package com.example.examplemod.client.hook;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.event.EventBus;
import com.example.examplemod.client.event.AttackEvent;
import com.example.examplemod.client.event.MotionEvent;
import com.example.examplemod.client.event.Render2DEvent;
import com.example.examplemod.client.event.Render3DEvent;
import com.example.examplemod.client.event.TickEvent;
import com.example.examplemod.client.event.UpdateEvent;
import com.example.examplemod.client.event.WorldChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The only class that talks to the real Forge/FML event buses. Every other
 * part of the client (modules, gui, hud) only ever sees the translated
 * events from {@link EventBus}, so none of it depends on Forge directly.
 */
public final class ForgeEventBridge {

    private final NetworkHook networkHook = new NetworkHook();
    private ClientWorld lastWorld;

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        EventBus.getInstance().post(new TickEvent(event.phase == Phase.START ? TickEvent.Phase.START : TickEvent.Phase.END));
        if (event.phase != Phase.START) {
            return;
        }

        ClientWorld currentWorld = Minecraft.getInstance().level;
        if (currentWorld != lastWorld) {
            EventBus.getInstance().post(new WorldChangeEvent(lastWorld, currentWorld));
            lastWorld = currentWorld;
            if (currentWorld != null && Minecraft.getInstance().getConnection() != null) {
                networkHook.install(Minecraft.getInstance().getConnection().getConnection());
            } else if (currentWorld == null) {
                Client.getInstance().getConfigManager().save();
            }
        }

        if (currentWorld != null && Minecraft.getInstance().player != null) {
            EventBus.getInstance().post(new UpdateEvent());
        }
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        MovementInput input = event.getMovementInput();
        ClientPlayerEntity player = Minecraft.getInstance().player;
        boolean currentlySprinting = player != null && player.isSprinting();

        MotionEvent motion = EventBus.getInstance().post(new MotionEvent(
                input.forwardImpulse, input.leftImpulse, input.jumping, input.shiftKeyDown, currentlySprinting));

        input.forwardImpulse = motion.getForward();
        input.leftImpulse = motion.getStrafe();
        input.jumping = motion.isJumping();
        input.shiftKeyDown = motion.isSneaking();
        if (player != null && motion.isSprinting() != currentlySprinting) {
            player.setSprinting(motion.isSprinting());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        EventBus.getInstance().post(new Render2DEvent(event.getMatrixStack(), event.getPartialTicks()));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        IRenderTypeBuffer.Impl buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        EventBus.getInstance().post(new Render3DEvent(event.getMatrixStack(), buffer, event.getPartialTicks()));
        buffer.endBatch();
    }

    @SubscribeEvent
    public void onChatSend(ClientChatEvent event) {
        if (Client.getInstance().getCommandManager().handle(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        AttackEvent internal = EventBus.getInstance().post(new AttackEvent(event.getTarget()));
        if (internal.isCancelled()) {
            event.setCanceled(true);
        }
    }
}
