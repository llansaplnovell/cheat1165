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

public class ArmorLeave extends Module implements AutoRechargeParticipant, SkyPvPParticipant {
    public final NumberValue<Integer> durability = new NumberValue<Integer>("Durability", this, 20, 1, 100, 1, "Leaves when any equipped armor piece's remaining durability drops to or below this value.");
    public final BoolValue autodisable = new BoolValue("AutoDisable", this, false, "Disables after ArmorLeave is triggered.");
    public final BoolValue autorecharge = new BoolValue("AutoRecharge", this, false, "Enables again after armor durability recovers.");
    public final BoolValue skypvp = new BoolValue("SkyPvP", this, false, "Uses the compass and clicks the bow in the menu to get back into SkyPvP. With AutoRecharge it runs after the recharge and enables ArmorLeave once you are in, without AutoRecharge it just walks you back in after AutoDisable.", () -> this.autorecharge.getValue() != false || this.autodisable.getValue() != false);
    private boolean waitingForRecharge;
    private boolean rechargeLatched;
    private boolean skyRejoinRunning;
    private final Listener<EventPreUpdate> onPre = new Listener<EventPreUpdate>(eventPreUpdate -> this.updateLeave());

    public ArmorLeave() {
        super("ArmorLeave", 0, Category.Combat, "Leaves automatically when the durability of your worn armor is low.");
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
            this.rechargeLatched = this.hasPlayer() && this.isDurabilityLow();
        } else {
            this.clearRechargeState();
        }
        super.onDisable();
    }

    private void updateLeave() {
        if (!this.hasPlayer() || this.mc.playerController == null) {
            return;
        }
        if (!this.isDurabilityLow()) {
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
        boolean low = this.isDurabilityLow();
        if (low) {
            this.rechargeLatched = true;
        }
        if (this.rechargeLatched && !low) {
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
        return this.hasPlayer() && this.currentHealth() > 0.0f && !this.isDurabilityLow();
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
            ClientUtils.debug((Object)"SkyPvP: back in, ArmorLeave enabled.");
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

    private int durabilityThreshold() {
        return ((Integer)this.durability.getValue()).intValue();
    }

    private boolean isDurabilityLow() {
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
