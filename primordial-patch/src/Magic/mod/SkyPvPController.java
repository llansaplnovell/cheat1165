package Magic.mod;

import Magic.ink.event.EventManager;
import Magic.ink.event.s.EventTick;
import Magic.utils.player.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import pisi.unitedmeows.eventapi.event.listener.Listener;

/**
 * Runs the "get back into SkyPvP" sequence for a {@link SkyPvPParticipant}.
 *
 * <p>The sequence is what a player does by hand after AutoLeave dropped them into the lobby:
 * wait out the respawn, right click the compass, wait for the menu the server opens, click the
 * bow inside it and wait until the player is loaded in on the other side. Only then the
 * participant is told the sequence is over, so it can switch itself back on where it belongs.
 *
 * <p>The wait after the bow click deliberately survives the gap where the client has no world and
 * no player at all - that is exactly what a BungeeCord style server switch looks like from here.
 * Nothing waits forever: every stage has its own timeout and the whole sequence has a hard
 * deadline on top of that.
 *
 * <p>Everything below runs on the Minecraft client thread only (EventTick is fired from
 * Minecraft#runTick, module toggles and gui clicks happen on the same thread), so no locking is
 * needed apart from the one-time registration.
 */
public final class SkyPvPController {

    private static final SkyPvPController INSTANCE = new SkyPvPController();
    private static boolean registered;

    /** How long the player may stay dead / hurt / worldless before the compass step is dropped. */
    private static final int READY_WAIT_TICKS = 600;
    /** Ticks to wait after that, so the lobby has time to hand out the hotbar. */
    private static final int SETTLE_TICKS = 20;
    /** How long the compass may stay missing before the sequence gives up. */
    private static final int COMPASS_SEARCH_TICKS = 100;
    /** How long the server gets to open the compass menu after a right click. */
    private static final int MENU_WAIT_TICKS = 60;
    /** How long the bow may stay missing inside an opened menu. */
    private static final int BOW_WAIT_TICKS = 60;
    /** How long to wait for a world change after the bow was clicked (warps keep the world). */
    private static final int JOIN_WAIT_TICKS = 120;
    /** How long to wait for the player to be alive and healthy again after the join. */
    private static final int RETURN_WAIT_TICKS = 400;
    /** Extra ticks once that is the case, so the module comes back inside a loaded world. */
    private static final int RETURN_SETTLE_TICKS = 20;
    /** Ticks after the bow click before a still open menu gets closed by us. */
    private static final int MENU_CLOSE_TICKS = 10;
    /** Hard deadline for the whole sequence. */
    private static final int SEQUENCE_DEADLINE_TICKS = 1600;
    /** Right clicks on the compass before the sequence gives up. */
    private static final int MAX_COMPASS_ATTEMPTS = 3;

    private final Listener<EventTick> onTick = new Listener<EventTick>(eventTick -> this.tick());

    private SkyPvPParticipant participant;
    private Stage stage = Stage.IDLE;
    private int stageTicks;
    private int totalTicks;
    private int settleTicks;
    private int compassAttempts;
    private World startWorld;
    private boolean worldChanged;

    private SkyPvPController() {
    }

    public static synchronized void registerController() {
        if (registered) {
            return;
        }
        try {
            EventManager.eventSystem.subscribe(INSTANCE, INSTANCE.onTick);
            registered = true;
        } catch (Throwable throwable) {
            registered = false;
            System.err.println("[SkyPvP] Controller registration failed: " + throwable);
        }
    }

    /**
     * Starts the sequence for the given participant.
     *
     * @return true when the participant owns the sequence from now on, false when it has to
     *         fall back to its normal behaviour.
     */
    public static boolean requestRejoin(SkyPvPParticipant participant) {
        if (participant == null || !registered) {
            return false;
        }
        if (INSTANCE.participant != null) {
            return INSTANCE.participant == participant;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            return false;
        }
        INSTANCE.reset();
        INSTANCE.participant = participant;
        INSTANCE.stage = Stage.SETTLE;
        INSTANCE.startWorld = mc.theWorld;
        return true;
    }

    /** Drops a running sequence without notifying the participant. */
    public static void cancel(SkyPvPParticipant participant) {
        if (participant != null && INSTANCE.participant == participant) {
            INSTANCE.reset();
        }
    }

    public static boolean isBusy(SkyPvPParticipant participant) {
        return participant != null && INSTANCE.participant == participant;
    }

    private void tick() {
        if (this.stage == Stage.IDLE) {
            return;
        }
        SkyPvPParticipant current = this.participant;
        if (current == null) {
            this.reset();
            return;
        }
        if (!this.callActive(current)) {
            this.finish(false);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && this.startWorld != null && mc.theWorld != this.startWorld) {
            this.worldChanged = true;
        }
        if (++this.totalTicks >= SEQUENCE_DEADLINE_TICKS) {
            ClientUtils.debug("SkyPvP: rejoin took too long, giving up.");
            this.finish(this.worldChanged);
            return;
        }
        ++this.stageTicks;
        try {
            switch (this.stage) {
                case SETTLE: {
                    this.tickSettle(mc);
                    break;
                }
                case USE_COMPASS: {
                    this.tickUseCompass(mc);
                    break;
                }
                case WAIT_MENU: {
                    this.tickWaitMenu(mc);
                    break;
                }
                case CLICK_BOW: {
                    this.tickClickBow(mc);
                    break;
                }
                case WAIT_JOIN: {
                    this.tickWaitJoin(mc);
                    break;
                }
                case WAIT_RETURN: {
                    this.tickWaitReturn(mc);
                    break;
                }
                default: {
                    this.finish(false);
                }
            }
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Rejoin sequence failed: " + throwable);
            this.finish(this.worldChanged);
        }
    }

    private void tickSettle(Minecraft mc) {
        if (!this.isReady(mc)) {
            this.settleTicks = 0;
            if (this.stageTicks >= READY_WAIT_TICKS) {
                ClientUtils.debug("SkyPvP: no healthy respawn to start from, giving up.");
                this.finish(false);
            }
            return;
        }
        if (mc.currentScreen instanceof GuiChest) {
            this.setStage(Stage.CLICK_BOW);
            return;
        }
        if (++this.settleTicks < SETTLE_TICKS) {
            return;
        }
        this.setStage(Stage.USE_COMPASS);
    }

    private void tickUseCompass(Minecraft mc) {
        if (mc.currentScreen instanceof GuiChest) {
            this.setStage(Stage.CLICK_BOW);
            return;
        }
        if (!this.hasPlayer(mc)) {
            return;
        }
        if (mc.currentScreen instanceof GuiContainer) {
            mc.displayGuiScreen(null);
            return;
        }
        int hotbarSlot = findHotbarCompass(mc);
        if (hotbarSlot < 0) {
            int inventorySlot = findInventoryCompass(mc);
            if (inventorySlot >= 0) {
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, inventorySlot,
                        mc.thePlayer.inventory.currentItem, 2, mc.thePlayer);
                return;
            }
            if (this.stageTicks >= COMPASS_SEARCH_TICKS) {
                ClientUtils.debug("SkyPvP: no compass in the inventory.");
                this.finish(false);
            }
            return;
        }
        if (mc.thePlayer.inventory.currentItem != hotbarSlot) {
            mc.thePlayer.inventory.currentItem = hotbarSlot;
            mc.playerController.syncCurrentPlayItem();
            return;
        }
        ItemStack held = mc.thePlayer.getCurrentEquippedItem();
        if (held == null || held.getItem() != Items.compass) {
            return;
        }
        ++this.compassAttempts;
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        this.setStage(Stage.WAIT_MENU);
    }

    private void tickWaitMenu(Minecraft mc) {
        if (mc.currentScreen instanceof GuiChest) {
            this.setStage(Stage.CLICK_BOW);
            return;
        }
        if (this.stageTicks < MENU_WAIT_TICKS) {
            return;
        }
        if (this.compassAttempts < MAX_COMPASS_ATTEMPTS) {
            this.setStage(Stage.USE_COMPASS);
            return;
        }
        ClientUtils.debug("SkyPvP: the compass menu did not open.");
        this.finish(false);
    }

    private void tickClickBow(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest) || !this.hasPlayer(mc)) {
            if (this.compassAttempts < MAX_COMPASS_ATTEMPTS && this.hasPlayer(mc)) {
                this.setStage(Stage.USE_COMPASS);
                return;
            }
            this.finish(this.worldChanged);
            return;
        }
        Container container = ((GuiContainer) mc.currentScreen).inventorySlots;
        if (container == null) {
            return;
        }
        Slot bow = findBowSlot(container, mc.thePlayer);
        if (bow == null) {
            if (this.stageTicks >= BOW_WAIT_TICKS) {
                ClientUtils.debug("SkyPvP: no bow inside the compass menu.");
                mc.displayGuiScreen(null);
                this.finish(false);
            }
            return;
        }
        mc.playerController.windowClick(container.windowId, bow.slotNumber, 0, 0, mc.thePlayer);
        this.setStage(Stage.WAIT_JOIN);
    }

    private void tickWaitJoin(Minecraft mc) {
        this.closeLeftoverMenu(mc);
        if (this.worldChanged) {
            this.setStage(Stage.WAIT_RETURN);
            return;
        }
        // a warp inside the same world never changes it, so stop guessing after a while
        if (this.stageTicks >= JOIN_WAIT_TICKS) {
            this.setStage(Stage.WAIT_RETURN);
        }
    }

    private void tickWaitReturn(Minecraft mc) {
        this.closeLeftoverMenu(mc);
        if (!this.isReady(mc)) {
            this.settleTicks = 0;
            if (this.stageTicks >= RETURN_WAIT_TICKS) {
                this.finish(this.worldChanged);
            }
            return;
        }
        if (++this.settleTicks < RETURN_SETTLE_TICKS) {
            return;
        }
        this.finish(this.worldChanged);
    }

    private void closeLeftoverMenu(Minecraft mc) {
        if (this.stageTicks >= MENU_CLOSE_TICKS && mc.currentScreen instanceof GuiChest) {
            mc.displayGuiScreen(null);
        }
    }

    private boolean hasPlayer(Minecraft mc) {
        return mc.thePlayer != null && mc.theWorld != null && mc.playerController != null;
    }

    private boolean isReady(Minecraft mc) {
        if (!this.hasPlayer(mc)) {
            return false;
        }
        SkyPvPParticipant current = this.participant;
        if (current == null) {
            return false;
        }
        try {
            return current.isSkyPvPRejoinReady();
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Participant state check failed: " + throwable);
            return false;
        }
    }

    private boolean callActive(SkyPvPParticipant current) {
        try {
            return current.isSkyPvPRejoinActive();
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Participant check failed: " + throwable);
            return false;
        }
    }

    private static int findHotbarCompass(Minecraft mc) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.compass) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Looks for a compass outside of the hotbar. The returned index is a slot number of the
     * player container, where the main inventory (9..35) keeps the inventory indices.
     */
    private static int findInventoryCompass(Minecraft mc) {
        for (int slot = 9; slot < 36; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.compass) {
                return slot;
            }
        }
        return -1;
    }

    private static Slot findBowSlot(Container container, EntityPlayer player) {
        for (Slot slot : container.inventorySlots) {
            if (slot == null || slot.inventory == player.inventory) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.getItem() != Items.bow) {
                continue;
            }
            return slot;
        }
        return null;
    }

    private void finish(boolean joined) {
        SkyPvPParticipant current = this.participant;
        this.reset();
        if (current == null) {
            return;
        }
        try {
            current.onSkyPvPRejoinFinished(joined);
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Participant callback failed: " + throwable);
        }
    }

    private void setStage(Stage next) {
        this.stage = next;
        this.stageTicks = 0;
        this.settleTicks = 0;
    }

    private void reset() {
        this.participant = null;
        this.stage = Stage.IDLE;
        this.stageTicks = 0;
        this.totalTicks = 0;
        this.settleTicks = 0;
        this.compassAttempts = 0;
        this.startWorld = null;
        this.worldChanged = false;
    }

    private static enum Stage {
        IDLE,
        SETTLE,
        USE_COMPASS,
        WAIT_MENU,
        CLICK_BOW,
        WAIT_JOIN,
        WAIT_RETURN;
    }
}
