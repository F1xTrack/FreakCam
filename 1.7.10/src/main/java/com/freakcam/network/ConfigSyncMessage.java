package com.freakcam.network;

import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ConfigSyncMessage implements IMessage {

    public int udpPort;
    public UUID token;

    public ConfigSyncMessage() {}

    public ConfigSyncMessage(int udpPort, UUID token) {
        this.udpPort = udpPort;
        this.token = token;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.udpPort = buf.readInt();
        long mostSigBits = buf.readLong();
        long leastSigBits = buf.readLong();
        this.token = new UUID(mostSigBits, leastSigBits);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.udpPort);
        buf.writeLong(this.token.getMostSignificantBits());
        buf.writeLong(this.token.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<ConfigSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(ConfigSyncMessage message, MessageContext ctx) {
            // Handle on client thread
            com.freakcam.client.ClientHandler.onConfigSync(message.udpPort, message.token);
            return null;
        }
    }
}
