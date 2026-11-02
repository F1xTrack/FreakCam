package com.freakcam.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("freakcam", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void init() {
        int id = 0;
        INSTANCE.registerMessage(
            id++,
            VideoFrameMessage.class,
            VideoFrameMessage::encode,
            VideoFrameMessage::decode,
            VideoFrameMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        INSTANCE.registerMessage(
            id++,
            VideoFrameClientMessage.class,
            VideoFrameClientMessage::encode,
            VideoFrameClientMessage::decode,
            VideoFrameClientMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
