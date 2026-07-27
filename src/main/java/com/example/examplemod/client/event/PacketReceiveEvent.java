package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.CancellableEvent;
import net.minecraft.network.IPacket;

/** Fired for every incoming packet before it is handled. Cancelling discards it. */
public class PacketReceiveEvent extends CancellableEvent {

    private final IPacket<?> packet;

    public PacketReceiveEvent(IPacket<?> packet) {
        this.packet = packet;
    }

    public IPacket<?> getPacket() {
        return packet;
    }
}
