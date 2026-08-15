package Magic.mod.s.combat;

import Magic.ink.event.s.EventPreUpdate;
import Magic.mod.AutoRechargeController;
import Magic.mod.AutoRechargeParticipant;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.SkyPvPController;
import Magic.mod.SkyPvPParticipant;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.NumberValue;
import Magic.utils.player.ClientUtils;
import net.minecraft.item.ItemStack;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class AutoLeave extends Module implements AutoRechargeParticipant, SkyPvPParticipant {
    public final NumberValue<Float> hp = new NumberValue<Float>("Health", this, 5.0f, 1.0f, 20.0f, 1.0f, "Health points: 20 HP = 10 hearts, 10 HP = 5 hearts.");
    public final BoolValue armorleave = new BoolValue("ArmorLeave", this, false, "Also leaves when the durability of your worn armor drops too low.");
    public final NumberValue<Integer> armorDurability = new NumberValue<Integer>("Durability", this, 20, 1, 100, 1, "Leaves when any equipped armor piece's remaining durability is at or below this value.", () -> this.armorleave.getValue() != false);
    public final BoolValue autodisable = new BoolValue("AutoDisable", this, false, "Disables after AutoLeave is triggered.");
    public final BoolValue autorecharge = new BoolValue("AutoRecharge", this, false, "Enables again after health and, if ArmorLeave is on, armor durability recover.");
    public final BoolValue skypvp = new BoolValue("SkyPvP", this, false, "Uses the compass and clicks the bow in the menu to get back into SkyPvP. With AutoRecharge it runs after the recharge and enables AutoLeave once you are in, without AutoRecharge it just walks you back in after AutoDisable.", () -> this.autorecharge.getValue() != false || this.autodisable.getValue() != false);
    private boolean waitingForRecharge;
    private boolean rechargeLatched;
    private boolean skyRejoinRunning;
    private final Listener<EventPreUpdate> onPre = new Listener<EventPreUpdate>(eventPreUpdate -> this.updateLeave());

    public AutoLeave() {
        super("AutoLeave", 0, Category.Combat, "Leaves automatically when your health or armor durability is low.");
        AutoRechargeController.registerController();
        SkyPvPController.registerController();
    }

    @Override
    public void onEnable() {
        SkyPvPController.cancel(this);
        this.skyRejoinRunning = false;
        this.waitingForRecharge = false;
        this.rechargeLatched = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.autorecharge.getValue().booleanValue()) {
            this.waitingForRecharge = true;
            this.rechargeLatched = this.hasPlayer() && !this.isSafeState();
        } else {
            this.clearRechargeState();
        }
        super.onDisable();
    }

    private void updateLeave() {
        if (!this.hasPlayer() || this.mc.playerController == null) {
            return;
        }
        if (!this.shouldLeave()) {
            return;
        }
        this.mc.playerController.attackEntity(this.mc.thePlayer, this.mc.thePlayer);
        if (!this.autodisable.getValue().booleanValue()) {
            return;
        }
        boolean bl = this.autorecharge.getValue();
        if (bl) {
            this.waitingForRecharge = true;
            this.rechargeLatched = true;
        }
        this.disableModule();
        if (!bl) {
            this.startSkyPvPRejoin();
        }
    }

    @Override
    public void handleAutoRecharge() {
        if (this.skyRejoinRunning && !SkyPvPController.isBusy(this)) {
            this.skyRejoinRunning = false;
            if (this.autorecharge.getValue().booleanValue() && !this.isEnabled()) {
                this.waitingForRecharge = true;
                this.rechargeLatched = true;
            }
        }
        if (!this.autorecharge.getValue().booleanValue()) {
            if (!this.skyRejoinRunning) {
                this.clearRechargeState();
            }
            return;
        }
        if (this.skyRejoinRunning) {
            return;
        }
        if (!this.waitingForRecharge || this.isEnabled() || !this.hasPlayer()) {
            return;
        }
        boolean safe = this.isSafeState();
        if (!safe) {
            this.rechargeLatched = true;
        }
        if (this.rechargeLatched && safe) {
            this.clearRechargeState();
            if (this.startSkyPvPRejoin()) {
                return;
            }
            this.enableModule();
        }
    }

    private boolean startSkyPvPRejoin() {
        if (this.skyRejoinRunning) {
            return true;
        }
        if (!this.skypvp.getValue().booleanValue() || !SkyPvPController.requestRejoin(this)) {
            return false;
        }
        this.skyRejoinRunning = true;
        return true;
    }

    @Override
    public boolean isSkyPvPRejoinActive() {
        return this.skyRejoinRunning && this.skypvp.getValue() != false && !this.isEnabled();
    }

    @Override
    public boolean isSkyPvPRejoinReady() {
        return this.hasPlayer() && this.currentHealth() > 0.0f && this.isSafeState();
    }

    @Override
    public void onSkyPvPRejoinFinished(boolean bl) {
        this.skyRejoinRunning = false;
        this.clearRechargeState();
        if (this.isEnabled()) {
            return;
        }
        if (this.autorecharge.getValue().booleanValue()) {
            this.enableModule();
            ClientUtils.debug((Object)"SkyPvP: back in, AutoLeave enabled.");
        }
    }

    public boolean isWaitingForRecharge() {
        return this.waitingForRecharge;
    }

    public boolean isRejoiningSkyPvP() {
        return this.skyRejoinRunning;
    }

    private boolean hasPlayer() {
        return this.mc != null && this.mc.thePlayer != null && this.mc.theWorld != null;
    }

    private float currentHealth() {
        return this.mc.thePlayer.getHealth();
    }

    private float healthThreshold() {
        return ((Float)this.hp.getValue()).floatValue();
    }

    private int durabilityThreshold() {
        return ((Integer)this.armorDurability.getValue()).intValue();
    }

    /**
     * True as soon as any tracked condition has fallen to or below its threshold: health, or,
     * when ArmorLeave is enabled, the remaining durability of any currently worn armor piece.
     */
    private boolean shouldLeave() {
        if (this.currentHealth() <= this.healthThreshold()) {
            return true;
        }
        return this.armorleave.getValue().booleanValue() && this.isArmorDurabilityLow();
    }

    /**
     * True once every condition that can trigger a leave has recovered: health is back above
     * its threshold and, if ArmorLeave is enabled, no worn armor piece is still at or under the
     * durability threshold. AutoDisable/AutoRecharge/SkyPvP all key off this instead of health
     * alone, so they wait out an armor-durability trigger the same way they wait out a health one.
     */
    private boolean isSafeState() {
        if (this.currentHealth() <= this.healthThreshold()) {
            return false;
        }
        return !this.armorleave.getValue().booleanValue() || !this.isArmorDurabilityLow();
    }

    private boolean isArmorDurabilityLow() {
        if (!this.hasPlayer()) {
            return false;
        }
        ItemStack[] armor = this.mc.thePlayer.inventory.armorInventory;
        int threshold = this.durabilityThreshold();
        for (ItemStack stack : armor) {
            if (stack == null || !stack.isItemStackDamageable()) {
                continue;
            }
            int remaining = stack.getMaxDamage() - stack.getItemDamage();
            if (remaining <= threshold) {
                return true;
            }
        }
        return false;
    }

    private void clearRechargeState() {
        this.waitingForRecharge = false;
        this.rechargeLatched = false;
    }

    private void disableModule() {
        if (this.isEnabled()) {
            this.toggle();
        }
    }

    private void enableModule() {
        if (!this.isEnabled()) {
            this.toggle();
        }
    }
}
