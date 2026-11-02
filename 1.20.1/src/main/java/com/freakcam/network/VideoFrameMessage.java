package com.freakcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import com.freakcam.server.ServerHandler;

import java.util.function.Supplier;

public class VideoFrameMessage {
    public byte[] jpegData;
    public int frameWidth;
    public int frameHeight;
    public int frameShape;
    public boolean borderEnabled;
    public float borderWidth;
    public int borderMode;
    public int borderColor;
    public boolean chromaEnabled;
    public int chromaColor;
    public float chromaTolerance;
    public float webcamScale;
    public float heightOffset;

    public VideoFrameMessage() {}

    public VideoFrameMessage(byte[] jpegData, int frameWidth, int frameHeight, int frameShape,
                             boolean borderEnabled, float borderWidth, int borderMode, int borderColor,
                             boolean chromaEnabled, int chromaColor, float chromaTolerance,
                             float webcamScale, float heightOffset) {
        this.jpegData = jpegData;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameShape = frameShape;
        this.borderEnabled = borderEnabled;
        this.borderWidth = borderWidth;
        this.borderMode = borderMode;
        this.borderColor = borderColor;
        this.chromaEnabled = chromaEnabled;
        this.chromaColor = chromaColor;
        this.chromaTolerance = chromaTolerance;
        this.webcamScale = webcamScale;
        this.heightOffset = heightOffset;
    }

    public static void encode(VideoFrameMessage msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.jpegData != null ? msg.jpegData : new byte[0]);
        buf.writeVarInt(msg.frameWidth);
        buf.writeVarInt(msg.frameHeight);
        buf.writeVarInt(msg.frameShape);
        buf.writeBoolean(msg.borderEnabled);
        buf.writeFloat(msg.borderWidth);
        buf.writeVarInt(msg.borderMode);
        buf.writeInt(msg.borderColor);
        buf.writeBoolean(msg.chromaEnabled);
        buf.writeInt(msg.chromaColor);
        buf.writeFloat(msg.chromaTolerance);
        buf.writeFloat(msg.webcamScale);
        buf.writeFloat(msg.heightOffset);
    }

    public static VideoFrameMessage decode(FriendlyByteBuf buf) {
        VideoFrameMessage msg = new VideoFrameMessage();
        msg.jpegData = buf.readByteArray();
        msg.frameWidth = buf.readVarInt();
        msg.frameHeight = buf.readVarInt();
        msg.frameShape = buf.readVarInt();
        msg.borderEnabled = buf.readBoolean();
        msg.borderWidth = buf.readFloat();
        msg.borderMode = buf.readVarInt();
        msg.borderColor = buf.readInt();
        msg.chromaEnabled = buf.readBoolean();
        msg.chromaColor = buf.readInt();
        msg.chromaTolerance = buf.readFloat();
        msg.webcamScale = buf.readFloat();
        msg.heightOffset = buf.readFloat();
        return msg;
    }

    public static void handle(VideoFrameMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender != null) {
                ServerHandler.onVideoFrameReceived(sender.getId(), msg);
                VideoFrameClientMessage clientMsg = new VideoFrameClientMessage(
                    sender.getId(), msg.jpegData, msg.frameWidth, msg.frameHeight,
                    msg.frameShape, msg.borderEnabled, msg.borderWidth, msg.borderMode,
                    msg.borderColor, msg.chromaEnabled, msg.chromaColor, msg.chromaTolerance,
                    msg.webcamScale, msg.heightOffset
                );
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), clientMsg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
