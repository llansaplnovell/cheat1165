package Magic.mod.s.combat;

import Magic.ink.event.s.EventPostUpdate;
import Magic.ink.event.s.EventPreUpdate;
import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.math.Timer;
import Magic.utils.pathfind.astar.AStar;
import Magic.utils.player.ClientUtils;
import Magic.utils.player.RotationUtils;
import Magic.utils.render.RenderUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class TpAura extends Module {
    public EnumValue<TpMode> mode = new EnumValue<TpMode>("Mode", this, TpMode.class, "Teleport methods.");
    private BoolValue block = new BoolValue("Block", this, false, "Auto block.");
    private List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
    private EntityLivingBase curTar;
    private boolean hit;
    private boolean postHit;
    private Timer timer = new Timer();
    private List<Vec3> list = new ArrayList<Vec3>();
    private AStar aStar = new AStar();

    private Listener<EventPreUpdate> onPre = new Listener<EventPreUpdate>(event -> {
        this.setSuffix(this.mode.getValue().name());
        if (this.mc.currentScreen != null) {
            return;
        }
        this.targets = this.getTargets();
        if (this.targets.isEmpty()) {
            this.list.clear();
            this.postHit = false;
            return;
        }
        this.curTar = Collections.min(this.targets,
                Comparator.comparingDouble(entity -> this.mc.thePlayer.getDistanceToEntity(entity)));
        float[] rotations = RotationUtils.getRotations(this.curTar);
        event.setYaw(rotations[0]);
        event.setPitch(rotations[1]);
        float delay = 0.0f;
        switch (this.mode.getValue()) {
            case Vanilla: {
                delay = 600.0f;
                break;
            }
            case NCP: {
                delay = (float) ((double) this.mc.thePlayer.getDistanceToEntity(this.curTar)
                        / ClientUtils.getBaseMoveSpeed() * 50.0) * 2.0f;
                if (delay < 500.0f) {
                    delay = 500.0f;
                }
                event.setCancelPackets(!this.postHit);
                break;
            }
        }
        if (this.timer.delay(delay)) {
            this.hit = true;
            this.timer.reset();
        } else {
            this.hit = false;
        }
        this.postHit = false;
    });

    private Listener<EventPostUpdate> onPost = new Listener<EventPostUpdate>(event -> {
        if (this.hit) {
            this.postHit = true;
            if (this.mc.thePlayer.isBlocking()) {
                ClientUtils.packet(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN, EnumFacing.DOWN));
            }
            boolean sprinting = this.mc.thePlayer.isSprinting();
            if (sprinting) {
                ClientUtils.packet(new C0BPacketEntityAction(this.mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
            }
            List<Vec3> path = new ArrayList<Vec3>();
            this.mc.thePlayer.swingItem();
            if (this.mode.getValue() == TpMode.NCP) {
                ClientUtils.sendOffset(0.0, -0.2, 0.0, true);
                double target = this.mc.thePlayer.getDistance(this.curTar.posX, this.curTar.posY, this.curTar.posZ) - 1.0;
                for (double offset = 0.0; offset < target; offset += ClientUtils.getBaseMoveSpeed()) {
                    float yaw = RotationUtils.getRotations(this.curTar)[0];
                    double[] motion = ClientUtils.calculate2(offset, yaw, 1.0f);
                    path.add(new Vec3(this.mc.thePlayer.posX + motion[0], this.mc.thePlayer.posY,
                            this.mc.thePlayer.posZ + motion[1]));
                }
                for (Vec3 position : path) {
                    float[] rotations = RotationUtils.getRotationFromPosition(this.curTar.posX, this.curTar.posZ,
                            this.curTar.posY, position.xCoord, position.yCoord, position.zCoord);
                    ClientUtils.send(position.xCoord, position.yCoord, position.zCoord, rotations[0], rotations[1], true);
                }
            } else if (this.mode.getValue() == TpMode.Vanilla) {
                this.teleport();
            }
            ClientUtils.packet(new C02PacketUseEntity(this.curTar, C02PacketUseEntity.Action.ATTACK));
            if (sprinting) {
                ClientUtils.packet(new C0BPacketEntityAction(this.mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            }
            if (this.mode.getValue() == TpMode.Vanilla) {
                Collections.reverse(this.list);
                for (Vec3 position : this.list) {
                    ClientUtils.send(position.xCoord, position.yCoord, position.zCoord, true);
                }
            } else if (this.mode.getValue() == TpMode.NCP) {
                if (!path.isEmpty()) {
                    Vec3 last = path.get(path.size() - 1);
                    ClientUtils.send(last.xCoord, last.yCoord - 0.2, last.zCoord, true);
                }
                Collections.reverse(path);
                for (Vec3 position : path) {
                    ClientUtils.send(position.xCoord, position.yCoord, position.zCoord, true);
                }
                ClientUtils.sendOffset(0.0, -0.2, 0.0, true);
            }
        }
        if (this.block.getValue().booleanValue() && this.mc.thePlayer.getHeldItem() != null
                && this.mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            this.mc.playerController.sendUseItem(this.mc.thePlayer, this.mc.theWorld, this.mc.thePlayer.getHeldItem());
        }
    }).filter(event -> !this.targets.isEmpty() && this.mc.thePlayer != null && this.mc.thePlayer.ticksExisted > 100);

    private Listener<EventRender3D> onRender = new Listener<EventRender3D>(event -> {
        for (Vec3 position : this.list) {
            double x = position.xCoord - this.mc.getRenderManager().renderPosX;
            double y = position.yCoord - this.mc.getRenderManager().renderPosY;
            double z = position.zCoord - this.mc.getRenderManager().renderPosZ;
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GL11.glLineWidth(1.0f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            RenderUtils.drawOutlineBox(new AxisAlignedBB(x - 0.5, y, z, x + 0.5, y + 1.8, z + 1.0));
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    });

    public TpAura() {
        super("TpAura", 35, Category.Combat, "Teleport-hit to player.");
    }

    private void teleport() {
        this.list.clear();
        for (BlockPos position : this.aStar.findPath(
                new BlockPos(this.mc.thePlayer.posX, this.mc.thePlayer.posY, this.mc.thePlayer.posZ),
                this.curTar.getPosition(), 100)) {
            this.list.add(new Vec3((double) position.x + 0.5, position.y, position.z));
        }
        for (Vec3 position : this.list) {
            ClientUtils.send(position.xCoord, position.yCoord, position.zCoord, true);
        }
    }

    @Override
    public void onEnable() {
        this.timer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.targets.clear();
        this.list.clear();
        this.curTar = null;
        this.hit = false;
        this.postHit = false;
        super.onDisable();
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(this.mode.getValue().name());
        super.onSuffixChange();
    }

    private List<EntityLivingBase> getTargets() {
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        if (this.mc.thePlayer == null || this.mc.theWorld == null) {
            return targets;
        }
        for (Entity entity : this.mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && this.canHit((EntityLivingBase) entity)) {
                targets.add((EntityLivingBase) entity);
            }
        }
        return targets;
    }

    private boolean canHit(EntityLivingBase entity) {
        if (FriendManager.isFriend(entity.getName())) {
            return false;
        }
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        return entity != this.mc.thePlayer
                && this.mc.thePlayer.getDistanceToEntity(entity) <= 50.0f
                && this.mc.thePlayer.getHealth() > 0.0f
                && entity.getHealth() > 0.0f
                && !(entity instanceof EntityArmorStand);
    }

    public static enum TpMode {
        Vanilla,
        NCP;
    }
}
