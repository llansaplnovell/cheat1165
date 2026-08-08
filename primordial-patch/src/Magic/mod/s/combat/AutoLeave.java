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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class AutoLeave
extends Module
implements AutoRechargeParticipant, SkyPvPParticipant {
    public final NumberValue<Float> hp = new NumberValue<Float>("Health", this, Float.valueOf(5.0f), Float.valueOf(1.0f), Float.valueOf(20.0f), Float.valueOf(1.0f), "Health points: 20 HP = 10 hearts, 10 HP = 5 hearts.");
    public final BoolValue autodisable = new BoolValue("AutoDisable", (Module)this, false, "Disables after AutoLeave is triggered.");
    public final BoolValue autorecharge = new BoolValue("AutoRecharge", (Module)this, false, "Enables again after health recovers.");
    public final BoolValue skypvp = new BoolValue("SkyPvP", (Module)this, false, "Instead of coming back in the lobby: uses the compass, clicks the bow in the menu and enables AutoLeave again inside SkyPvP. Needs AutoRecharge.", () -> this.autorecharge.getValue());
    private boolean waitingForRecharge;
    private boolean rechargeLatched;
    private boolean skyRejoinRunning;
    private final Listener<EventPreUpdate> onPre = new Listener<EventPreUpdate>(eventPreUpdate -> this.updateLeave());

    public AutoLeave() {
        super("AutoLeave", 0, Category.Combat, "Leaves automatically when your health is low.");
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
            this.rechargeLatched = this.hasPlayer() && this.currentHealth() <= this.threshold();
        } else {
            this.clearRechargeState();
        }
        super.onDisable();
    }

    private void updateLeave() {
        if (!this.hasPlayer() || this.mc.playerController == null) {
            return;
        }
        if (this.currentHealth() > this.threshold()) {
            return;
        }
        this.mc.playerController.attackEntity((EntityPlayer)this.mc.thePlayer, (Entity)this.mc.thePlayer);
        if (!this.autodisable.getValue().booleanValue()) {
            return;
        }
        if (this.autorecharge.getValue().booleanValue()) {
            this.waitingForRecharge = true;
            this.rechargeLatched = true;
        }
        this.disableModule();
    }

    @Override
    public void handleAutoRecharge() {
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
        float f = this.currentHealth();
        if (f <= this.threshold()) {
            this.rechargeLatched = true;
        }
        if (this.rechargeLatched && f > this.threshold()) {
            this.clearRechargeState();
            if (this.skypvp.getValue().booleanValue() && SkyPvPController.requestRejoin(this)) {
                this.skyRejoinRunning = true;
                return;
            }
            this.enableModule();
        }
    }

    @Override
    public boolean isSkyPvPRejoinActive() {
        return this.skyRejoinRunning && this.skypvp.getValue().booleanValue() && !this.isEnabled();
    }

    @Override
    public void onSkyPvPRejoinFinished(boolean joined) {
        this.skyRejoinRunning = false;
        if (this.isEnabled()) {
            this.clearRechargeState();
            return;
        }
        if (this.autorecharge.getValue().booleanValue()) {
            this.waitingForRecharge = true;
            this.rechargeLatched = true;
            return;
        }
        this.clearRechargeState();
        this.enableModule();
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

    private float threshold() {
        return ((Float)this.hp.getValue()).floatValue();
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
