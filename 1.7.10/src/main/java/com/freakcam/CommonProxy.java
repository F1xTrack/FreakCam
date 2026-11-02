package com.freakcam;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        com.freakcam.network.NetworkHandler.init();
        com.freakcam.server.ServerHandler.init();
    }

    public void init(FMLInitializationEvent event) {
        // Register packets
    }

    public void postInit(FMLPostInitializationEvent event) {
        // Late init
    }

    public void serverStarting(FMLServerStartingEvent event) {
        // Start UDP Server
        com.freakcam.server.UDPServer.startServer();
    }
}
