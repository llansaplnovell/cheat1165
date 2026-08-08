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
 * right click the compass, wait for the menu the server opens, click the bow inside it and wait
 * until the server moves the player into the SkyPvP world. Once that is done the participant is
 * handed control back and re-enables itself through its own AutoRecharge path.
 *
 * <p>Everything below runs on the Minecraft client thread only (EventTick is fired from
 * Minecraft#runTick, module toggles and gui clicks happen on the same thread), so no locking is
 * needed apart from the one-time registration.
 */
public final class SkyPvPController {

    private static final SkyPvPController INSTANCE = new SkyPvPController();
    private static boolean registered;

    /** Ticks to wait after the recharge fired, so the lobby has time to hand out the hotbar. */
    private static final int SETTLE_TICKS = 20;
    /** How long the compass may stay missing before the sequence gives up. */
    private static final int COMPASS_SEARCH_TICKS = 100;
    /** How long the server gets to open the compass menu after a right click. */
    private static final int MENU_WAIT_TICKS = 60;
    /** How long the bow may stay missing inside an opened menu. */
    private static final int BOW_WAIT_TICKS = 60;
    /** How long to wait for the world change after the bow was clicked. */
    private static final int JOIN_WAIT_TICKS = 100;
    /** Extra ticks after the world changed, so the module comes back inside the arena. */
    private static final int JOIN_SETTLE_TICKS = 20;
    /** Ticks after the bow click before a still open menu gets closed by us. */
    private static final int MENU_CLOSE_TICKS = 10;
    /** Right clicks on the compass before the sequence gives up. */
    private static final int MAX_COMPASS_ATTEMPTS = 3;

    private final Listener<EventTick> onTick = new Listener<EventTick>(eventTick -> this.tick());

    private SkyPvPParticipant participant;
    private Stage stage = Stage.IDLE;
    private int stageTicks;
    private int compassAttempts;
    private World startWorld;
    private boolean worldChanged;
    private int joinSettleTicks;

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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            this.finish(false);
            return;
        }
        boolean active;
        try {
            active = current.isSkyPvPRejoinActive();
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Participant check failed: " + throwable);
            active = false;
        }
        if (!active) {
            this.finish(false);
            return;
        }
        if (this.startWorld != null && mc.theWorld != this.startWorld) {
            this.worldChanged = true;
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
                default: {
                    this.finish(false);
                }
            }
        } catch (Throwable throwable) {
            System.err.println("[SkyPvP] Rejoin sequence failed: " + throwable);
            this.finish(false);
        }
    }

    private void tickSettle(Minecraft mc) {
        if (mc.thePlayer.getHealth() <= 0.0f) {
            this.stageTicks = 0;
            return;
        }
        if (mc.currentScreen instanceof GuiChest) {
            this.setStage(Stage.CLICK_BOW);
            return;
        }
        if (this.stageTicks < SETTLE_TICKS) {
            return;
        }
        this.setStage(Stage.USE_COMPASS);
    }

    private void tickUseCompass(Minecraft mc) {
        if (mc.currentScreen instanceof GuiChest) {
            this.setStage(Stage.CLICK_BOW);
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
                ClientUtils.debug("SkyPvP: no compass in the inventory, AutoLeave comes back as usual.");
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
        ClientUtils.debug("SkyPvP: the compass menu did not open, AutoLeave comes back as usual.");
        this.finish(false);
    }

    private void tickClickBow(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest)) {
            if (this.compassAttempts < MAX_COMPASS_ATTEMPTS) {
                this.setStage(Stage.USE_COMPASS);
                return;
            }
            this.finish(false);
            return;
        }
        Container container = ((GuiContainer) mc.currentScreen).inventorySlots;
        if (container == null) {
            return;
        }
        Slot bow = findBowSlot(container, mc.thePlayer);
        if (bow == null) {
            if (this.stageTicks >= BOW_WAIT_TICKS) {
                ClientUtils.debug("SkyPvP: no bow inside the compass menu, AutoLeave comes back as usual.");
                mc.displayGuiScreen(null);
                this.finish(false);
            }
            return;
        }
        mc.playerController.windowClick(container.windowId, bow.slotNumber, 0, 0, mc.thePlayer);
        this.setStage(Stage.WAIT_JOIN);
    }

    private void tickWaitJoin(Minecraft mc) {
        if (this.stageTicks >= MENU_CLOSE_TICKS && mc.currentScreen instanceof GuiChest) {
            mc.displayGuiScreen(null);
        }
        if (this.worldChanged) {
            if (++this.joinSettleTicks < JOIN_SETTLE_TICKS) {
                return;
            }
            this.finish(true);
            return;
        }
        if (this.stageTicks >= JOIN_WAIT_TICKS) {
            this.finish(false);
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
    }

    private void reset() {
        this.participant = null;
        this.stage = Stage.IDLE;
        this.stageTicks = 0;
        this.compassAttempts = 0;
        this.startWorld = null;
        this.worldChanged = false;
        this.joinSettleTicks = 0;
    }

    private static enum Stage {
        IDLE,
        SETTLE,
        USE_COMPASS,
        WAIT_MENU,
        CLICK_BOW,
        WAIT_JOIN;
    }
}
