package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.CancellableEvent;
import net.minecraft.network.IPacket;

/** Fired before an outgoing packet reaches the network channel. Cancelling drops it. */
public class PacketSendEvent extends CancellableEvent {

    private IPacket<?> packet;

    public PacketSendEvent(IPacket<?> packet) {
        this.packet = packet;
    }

    public IPacket<?> getPacket() {
        return packet;
    }

    public void setPacket(IPacket<?> packet) {
        this.packet = packet;
    }
}
