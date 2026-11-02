package com.freakcam.server;

import com.freakcam.network.VideoFrameMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHandler {
    private static final Map<Integer, VideoFrameMessage> playerLatestFrames = new ConcurrentHashMap<>();

    public static void onVideoFrameReceived(int entityId, VideoFrameMessage msg) {
        playerLatestFrames.put(entityId, msg);
    }
}
