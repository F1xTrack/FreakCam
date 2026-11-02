package com.freakcam.server;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;

import com.freakcam.network.ConfigSyncMessage;
import com.freakcam.network.NetworkHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class ServerHandler {

    public static void init() {
        ServerHandler handler = new ServerHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            UUID token = UUID.randomUUID();
            UDPServer.registerPlayerToken(player.getEntityId(), token, player);

            int udpPort = UDPServer.getPort();
            NetworkHandler.INSTANCE.sendTo(new ConfigSyncMessage(udpPort, token), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            UDPServer.removePlayer(event.player.getEntityId());
        }
    }
}
