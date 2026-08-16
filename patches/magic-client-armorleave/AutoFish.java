package Magic.mod.s.player;

import Magic.ink.event.s.EventClearItemUse;
import Magic.ink.event.s.EventPacketReceive;
import Magic.ink.event.s.EventPreUpdate;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.NumberValue;
import Magic.utils.math.Timer;
import Magic.utils.player.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class AutoFish extends Module {
    private BoolValue afk = new BoolValue("Afk", (Module)this, false, "");
    private BoolValue leave = new BoolValue("Leave", (Module)this, false, "Leaves the moment you take any damage while AutoFish is running, even damage the rod itself caused. If a hit doesn't show up as health loss, a drop in worn armor durability is used as a fallback detector. Turns itself back off after firing.");
    private BoolValue guard = new BoolValue("Guard", (Module)this, false, "Holds off reeling in while another player is standing right next to your bobber, so they can't steal the catch. Reels in automatically the moment they back off.");
    private NumberValue<Float> guardRange = new NumberValue<Float>("Range", this, 1.0f, 0.5f, 5.0f, 0.5f, "How close another player has to be to your bobber to count as contesting it.", () -> this.guard.getValue() != false);
    private Timer timer = new Timer();
    private long lastVelTime;
    private int lastAfkTick;
    private double prevY;
    private final int[] lastArmorDamage = new int[4];
    private boolean armorBaselineSet;
    private boolean pendingReel;
    private double pendingReelExtra;
    public Listener<EventPreUpdate> updateEvent = new Listener<EventPreUpdate>(event -> {
        boolean canAfk;
        this.updateLeave();
        this.updatePendingReel();
        if (!this.isHoldingFishingRod()) {
            this.getOtherRods();
            return;
        }
        this.fixRod();
        if (!this.afk.getValue().booleanValue() && this.mc.thePlayer.fishEntity == null && this.timer.delay(1000.0f)) {
            this.mc.rightClickMouse();
            this.lastVelTime = System.currentTimeMillis();
            this.timer.reset();
        }
        boolean bl = canAfk = Minecraft.getRunTick() - this.lastAfkTick > 210;
        if (this.afk.getValue().booleanValue() && this.mc.thePlayer.fishEntity == null && canAfk) {
            ClientUtils.debug((Object)"Start fishing...");
            this.mc.rightClickMouse();
            this.mc.thePlayer.sendChatMessage("/afk");
            this.lastVelTime = System.currentTimeMillis();
            this.lastAfkTick = Minecraft.getRunTick();
        }
    });
    private Listener<EventPacketReceive> onReceive = new Listener<EventPacketReceive>(event -> {
        if (event.getState() != EventPacketReceive.PacketState.PRE) {
            return;
        }
        if (this.mc.thePlayer == null || !this.isHoldingFishingRod() || this.mc.thePlayer.fishEntity == null) {
            return;
        }
        EntityFishHook fish = this.mc.thePlayer.fishEntity;
        Packet<? extends INetHandler> packet = event.getPacket();
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet2 = (S12PacketEntityVelocity)packet;
            if (packet2.getEntityID() == fish.getEntityId()) {
                if (System.currentTimeMillis() - this.lastVelTime >= 2000L) {
                    this.pullBack((double)packet2.getMotionY() / 8000.0);
                }
                this.lastVelTime = System.currentTimeMillis();
            }
        } else {
            Packet<? extends INetHandler> packet3 = event.getPacket();
            if (packet3 instanceof S29PacketSoundEffect) {
                S29PacketSoundEffect packet4 = (S29PacketSoundEffect)packet3;
                double dX = packet4.getX() - fish.posX;
                double dY = packet4.getY() - fish.posY;
                double dZ = packet4.getZ() - fish.posZ;
                double dist = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                if (!this.afk.getValue().booleanValue() && packet4.getSoundName().equals("random.splash") && dist <= 0.1) {
                    this.pullBack(Math.hypot(dX, dZ));
                }
            }
        }
    });
    private Listener<EventClearItemUse> onClear = new Listener<EventClearItemUse>(event -> event.setCancelled(true));

    public AutoFish() {
        super("AutoFish", 0, Category.Player, "Catch fish automatically.");
    }

    @Override
    public void onDisable() {
        this.armorBaselineSet = false;
        this.pendingReel = false;
        super.onDisable();
    }

    /**
     * Watches for any damage taken while AutoFish is running and leaves the moment it sees one.
     * The primary detector is vanilla's own hurtTime (set to > 0 the instant the client
     * registers a hit, same signal AntiKnockBack/PlayerESP already key off) - it doesn't care
     * what caused the hit, so it also covers damage dealt by the fishing rod itself, and it
     * doesn't miss hits that get absorbed before visibly changing health. As a fallback, in
     * case a hit doesn't set hurtTime, a tick-over-tick drop in any worn armor piece's
     * durability is treated as a hit too. Fires at most once per arm: once it leaves, it turns
     * the Leave toggle back off itself instead of relying on a separate AutoDisable setting.
     */
    private void updateLeave() {
        if (this.mc.thePlayer == null) {
            return;
        }
        if (!this.leave.getValue().booleanValue()) {
            this.armorBaselineSet = false;
            return;
        }
        boolean hit = this.mc.thePlayer.hurtTime > 0;
        ItemStack[] armor = this.mc.thePlayer.inventory.armorInventory;
        if (!hit && this.armorBaselineSet) {
            for (int i = 0; i < armor.length; ++i) {
                ItemStack stack = armor[i];
                int damage = stack == null ? 0 : stack.getItemDamage();
                if (damage > this.lastArmorDamage[i]) {
                    hit = true;
                    break;
                }
            }
        }
        for (int i = 0; i < armor.length; ++i) {
            ItemStack stack = armor[i];
            this.lastArmorDamage[i] = stack == null ? 0 : stack.getItemDamage();
        }
        this.armorBaselineSet = true;
        if (!hit || this.mc.playerController == null) {
            return;
        }
        this.mc.playerController.attackEntity(this.mc.thePlayer, this.mc.thePlayer);
        ClientUtils.debug((Object)"AutoFish: took damage while fishing, leaving.");
        this.leave.setValue(false);
    }

    /**
     * Retries a reel that pullBack() deferred because the bobber was contested. Runs every tick
     * so the catch is grabbed the instant the area clears, instead of only re-checking on the
     * next bite packet (which might not come again for that same fish).
     */
    private void updatePendingReel() {
        if (!this.pendingReel || this.mc.thePlayer == null) {
            return;
        }
        if (this.mc.thePlayer.fishEntity == null) {
            this.pendingReel = false;
            return;
        }
        if (this.isBobberContested()) {
            return;
        }
        this.pendingReel = false;
        this.reelIn(this.pendingReelExtra);
    }

    /**
     * True while Guard is on, a bobber is actually out, and another player is standing within
     * guardRange of it - i.e. close enough to also be able to right-click the loot the instant
     * it lands. Your own player never counts against yourself.
     */
    private boolean isBobberContested() {
        if (!this.guard.getValue().booleanValue()) {
            return false;
        }
        EntityFishHook hook = this.mc.thePlayer.fishEntity;
        if (hook == null) {
            return false;
        }
        float range = ((Float)this.guardRange.getValue()).floatValue();
        for (EntityPlayer player : ClientUtils.getPlayers()) {
            if (player == this.mc.thePlayer || player.isDead) {
                continue;
            }
            if (player.getDistanceToEntity(hook) < range) {
                return true;
            }
        }
        return false;
    }

    private void pullBack(double additionalValue) {
        if (this.isBobberContested()) {
            this.pendingReel = true;
            this.pendingReelExtra = additionalValue;
            ClientUtils.debug((Object)"AutoFish: someone's next to the bobber, holding the reel.");
            return;
        }
        this.reelIn(additionalValue);
    }

    private void reelIn(double additionalValue) {
        if (this.afk.getValue().booleanValue()) {
            boolean canAfk;
            boolean bl = canAfk = Minecraft.getRunTick() - this.lastAfkTick > 210;
            if (canAfk) {
                this.mc.rightClickMouse();
                this.mc.rightClickMouse();
                this.mc.thePlayer.sendChatMessage("/afk");
                this.lastAfkTick = Minecraft.getRunTick();
            } else {
                ClientUtils.debug((Object)("Skipping because can't afk (" + (Minecraft.getRunTick() - this.lastAfkTick) + ")"));
            }
        } else {
            this.mc.rightClickMouse();
            this.mc.rightClickMouse();
        }
        long diff = System.currentTimeMillis() - this.lastVelTime;
        ClientUtils.debug((Object)("Fish Caught (" + diff / 1000L + "s, " + additionalValue + ")"));
    }

    private boolean isHoldingFishingRod() {
        ItemStack heldItem = this.mc.thePlayer.getCurrentEquippedItem();
        return heldItem != null && heldItem.getItem() instanceof ItemFishingRod && heldItem.getMaxDamage() - heldItem.getItemDamage() > 1;
    }

    private void fixRod() {
        if (this.mc.thePlayer.fishEntity == null) {
            for (Entity entity : this.mc.theWorld.loadedEntityList) {
                if (!(entity instanceof EntityFishHook)) continue;
                EntityFishHook fishHook = (EntityFishHook)entity;
                if (fishHook.angler != this.mc.thePlayer) continue;
                this.mc.thePlayer.fishEntity = fishHook;
            }
        } else if (this.mc.thePlayer.fishEntity.isDead || !this.mc.thePlayer.fishEntity.isEntityAlive()) {
            this.mc.thePlayer.fishEntity = null;
        }
    }

    private void getOtherRods() {
        int i = 0;
        while (i < 45) {
            Slot slot = this.mc.thePlayer.inventoryContainer.getSlot(i);
            if (slot.getHasStack() && slot.getStack().getItem() instanceof ItemFishingRod && slot.getStack().getMaxDamage() - slot.getStack().getItemDamage() > 1) {
                ClientUtils.swapItem(i, this.mc.thePlayer.inventory.currentItem);
                return;
            }
            ++i;
        }
    }
}
