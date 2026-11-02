package com.freakcam.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class VideoFrameMessage implements IMessage {

    public int frameId;
    public int chunkIndex;
    public int totalChunks;
    public byte[] chunkData;
    public float heightOffset;
    public float webcamScale;
    public int frameShape;
    public boolean borderEnabled;
    public float borderWidth;
    public int borderMode;
    public int borderColor;
    public boolean chromaEnabled;
    public int chromaColor;
    public float chromaTolerance;

    public VideoFrameMessage() {}

    public VideoFrameMessage(int frameId, int chunkIndex, int totalChunks, byte[] chunkData, float heightOffset,
        float webcamScale, int frameShape, boolean borderEnabled, float borderWidth, int borderMode, int borderColor,
        boolean chromaEnabled, int chromaColor, float chromaTolerance) {
        this.frameId = frameId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
        this.heightOffset = heightOffset;
        this.webcamScale = webcamScale;
        this.frameShape = frameShape;
        this.borderEnabled = borderEnabled;
        this.borderWidth = borderWidth;
        this.borderMode = borderMode;
        this.borderColor = borderColor;
        this.chromaEnabled = chromaEnabled;
        this.chromaColor = chromaColor;
        this.chromaTolerance = chromaTolerance;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.frameId = buf.readInt();
        this.chunkIndex = buf.readInt();
        this.totalChunks = buf.readInt();
        int len = buf.readInt();
        this.chunkData = new byte[len];
        buf.readBytes(this.chunkData);
        this.heightOffset = buf.readFloat();
        this.webcamScale = buf.readFloat();
        this.frameShape = buf.readInt();
        this.borderEnabled = buf.readBoolean();
        this.borderWidth = buf.readFloat();
        this.borderMode = buf.readInt();
        this.borderColor = buf.readInt();
        this.chromaEnabled = buf.readBoolean();
        this.chromaColor = buf.readInt();
        this.chromaTolerance = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(frameId);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(chunkData.length);
        buf.writeBytes(chunkData);
        buf.writeFloat(heightOffset);
        buf.writeFloat(webcamScale);
        buf.writeInt(frameShape);
        buf.writeBoolean(borderEnabled);
        buf.writeFloat(borderWidth);
        buf.writeInt(borderMode);
        buf.writeInt(borderColor);
        buf.writeBoolean(chromaEnabled);
        buf.writeInt(chromaColor);
        buf.writeFloat(chromaTolerance);
    }

    public static class Handler implements IMessageHandler<VideoFrameMessage, IMessage> {

        @Override
        public IMessage onMessage(VideoFrameMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            if (sender != null && message.chunkData != null) {
                NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(
                    sender.dimension,
                    sender.posX,
                    sender.posY,
                    sender.posZ,
                    64.0);
                NetworkHandler.INSTANCE.sendToAllAround(
                    new VideoFrameClientMessage(
                        sender.getEntityId(),
                        message.frameId,
                        message.chunkIndex,
                        message.totalChunks,
                        message.chunkData,
                        message.heightOffset,
                        message.webcamScale,
                        message.frameShape,
                        message.borderEnabled,
                        message.borderWidth,
                        message.borderMode,
                        message.borderColor,
                        message.chromaEnabled,
                        message.chromaColor,
                        message.chromaTolerance),
                    point);
            }
            return null;
        }
    }
}
