package com.freakcam;

import com.freakcam.client.FreakCamConfig;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        FreakCamConfig.load(event.getModConfigurationDirectory());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        com.freakcam.client.ClientHandler.init();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
