package com.freakcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import com.freakcam.client.ClientHandler;

import java.util.function.Supplier;

public class VideoFrameClientMessage {
    public int entityId;
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

    public VideoFrameClientMessage() {}

    public VideoFrameClientMessage(int entityId, byte[] jpegData, int frameWidth, int frameHeight,
                                   int frameShape, boolean borderEnabled, float borderWidth, int borderMode,
                                   int borderColor, boolean chromaEnabled, int chromaColor, float chromaTolerance,
                                   float webcamScale, float heightOffset) {
        this.entityId = entityId;
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

    public static void encode(VideoFrameClientMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
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

    public static VideoFrameClientMessage decode(FriendlyByteBuf buf) {
        VideoFrameClientMessage msg = new VideoFrameClientMessage();
        msg.entityId = buf.readVarInt();
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

    public static void handle(VideoFrameClientMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientHandler.onRemoteVideoFrameReceived(msg);
        });
        ctx.get().setPacketHandled(true);
    }
}
