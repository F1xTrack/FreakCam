package com.freakcam;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.freakcam.client.ClientHandler;
import com.freakcam.client.FreakCamConfig;
import com.freakcam.network.PacketHandler;

import java.io.File;

@Mod("freakcam")
public class FreakCam {
    public static final String MODID = "freakcam";
    public static final Logger LOG = LogManager.getLogger("FreakCam");

    public FreakCam() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }

    private void setup(final FMLCommonSetupEvent event) {
        File configDir = new File("config");
        if (!configDir.exists()) configDir.mkdirs();
        FreakCamConfig.load(configDir);
        PacketHandler.init();
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        ClientHandler.init();
    }
}
