package com.freakcam.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class VideoToggleMessage implements IMessage {

    public int entityId;
    public boolean enabled;

    public VideoToggleMessage() {}

    public VideoToggleMessage(boolean enabled) {
        this.entityId = 0;
        this.enabled = enabled;
    }

    public VideoToggleMessage(int entityId, boolean enabled) {
        this.entityId = entityId;
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(enabled);
    }

    public static class ServerHandler implements IMessageHandler<VideoToggleMessage, IMessage> {

        @Override
        public IMessage onMessage(VideoToggleMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            if (sender != null) {
                NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(
                    sender.dimension,
                    sender.posX,
                    sender.posY,
                    sender.posZ,
                    64.0);
                NetworkHandler.INSTANCE
                    .sendToAllAround(new VideoToggleMessage(sender.getEntityId(), message.enabled), point);
            }
            return null;
        }
    }

    public static class ClientHandlerMsg implements IMessageHandler<VideoToggleMessage, IMessage> {

        @Override
        public IMessage onMessage(VideoToggleMessage message, MessageContext ctx) {
            com.freakcam.client.ClientHandler.onPlayerCamStatusChanged(message.entityId, message.enabled);
            return null;
        }
    }
}
