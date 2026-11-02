package com.freakcam.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

import com.freakcam.FreakCam;

public class UDPServer {

    private static DatagramSocket socket;
    private static Thread serverThread;
    private static boolean running = false;
    private static int port = 25566;

    private static final Map<Integer, UUID> expectedTokens = new ConcurrentHashMap<>();
    private static final Map<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
    private static final Map<SocketAddress, Integer> activeConnections = new ConcurrentHashMap<>();
    private static final Map<Integer, SocketAddress> activePlayers = new ConcurrentHashMap<>();

    public static void startServer() {
        if (running) return;
        try {
            socket = new DatagramSocket(port);
            running = true;
            serverThread = new Thread(UDPServer::runLoop, "FreakCam-UDPServer");
            serverThread.setDaemon(true);
            serverThread.start();
            FreakCam.LOG.info("Started FreakCam UDP Server on port " + port);
        } catch (Exception e) {
            FreakCam.LOG.error("Failed to start UDP server on port " + port, e);
        }
    }

    public static void stopServer() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static int getPort() {
        return port;
    }

    public static void registerPlayerToken(int entityId, UUID token, ServerPlayer player) {
        expectedTokens.put(entityId, token);
        players.put(entityId, player);
    }

    public static void removePlayer(int entityId) {
        expectedTokens.remove(entityId);
        players.remove(entityId);
        SocketAddress addr = activePlayers.remove(entityId);
        if (addr != null) {
            activeConnections.remove(addr);
        }
    }

    private static void runLoop() {
        byte[] buffer = new byte[65507];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            } catch (IOException e) {
                if (running) {
                    FreakCam.LOG.error("UDP Server receive error", e);
                }
            }
        }
    }

    private static void handlePacket(DatagramPacket packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        if (buf.remaining() < 1) return;
        byte packetId = buf.get();

        if (packetId == 0) { // Auth Packet
            if (buf.remaining() < 20) return;
            long most = buf.getLong();
            long least = buf.getLong();
            UUID token = new UUID(most, least);
            int entityId = buf.getInt();

            if (expectedTokens.containsKey(entityId) && expectedTokens.get(entityId).equals(token)) {
                activeConnections.put(packet.getSocketAddress(), entityId);
                activePlayers.put(entityId, packet.getSocketAddress());
                FreakCam.LOG.info("UDP Client authenticated for entity " + entityId);
            }
        } else if (packetId == 1 || packetId == 2) {
            SocketAddress senderAddr = packet.getSocketAddress();
            Integer senderId = activeConnections.get(senderAddr);
            if (senderId == null) return;

            byte[] forwardData = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), forwardData, 0, packet.getLength());

            ByteBuffer fBuf = ByteBuffer.wrap(forwardData);
            fBuf.get(); // skip packetId
            fBuf.putInt(senderId);

            ServerPlayer sender = players.get(senderId);
            if (sender == null) return;

            for (Map.Entry<Integer, ServerPlayer> entry : players.entrySet()) {
                if (entry.getKey().equals(senderId)) continue;
                SocketAddress targetAddr = activePlayers.get(entry.getKey());
                if (targetAddr == null) continue;
                ServerPlayer target = entry.getValue();
                if (target.level() == sender.level() && target.distanceToSqr(sender) < 256 * 256) {
                    try {
                        DatagramPacket sendPacket = new DatagramPacket(forwardData, forwardData.length, targetAddr);
                        socket.send(sendPacket);
                    } catch (IOException e) {}
                }
            }
        }
    }
}
