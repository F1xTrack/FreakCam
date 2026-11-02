package com.freakcam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class AudioFrameClientMessage implements IMessage {

    public AudioFrameClientMessage() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<AudioFrameClientMessage, IMessage> {

        @Override
        public IMessage onMessage(AudioFrameClientMessage message, MessageContext ctx) {
            return null;
        }
    }
}
