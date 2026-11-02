package com.freakcam.network;

import com.freakcam.FreakCam;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(FreakCam.MODID);
    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(ConfigSyncMessage.Handler.class, ConfigSyncMessage.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(VideoFrameMessage.Handler.class, VideoFrameMessage.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            VideoFrameClientMessage.Handler.class,
            VideoFrameClientMessage.class,
            packetId++,
            Side.CLIENT);
        INSTANCE
            .registerMessage(VideoToggleMessage.ServerHandler.class, VideoToggleMessage.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            VideoToggleMessage.ClientHandlerMsg.class,
            VideoToggleMessage.class,
            packetId++,
            Side.CLIENT);
    }
}
