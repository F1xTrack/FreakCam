package com.freakcam.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import com.freakcam.FreakCam;

public class UDPServer {

    private static DatagramSocket socket;
    private static Thread serverThread;
    private static boolean running = false;
    private static int port = 25566; // Configurable later

    private static final Map<Integer, UUID> expectedTokens = new ConcurrentHashMap<>();
    private static final Map<Integer, EntityPlayerMP> players = new ConcurrentHashMap<>();
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

    public static void registerPlayerToken(int entityId, UUID token, EntityPlayerMP player) {
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
        byte[] buffer = new byte[65507]; // Max UDP packet size
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
            if (buf.remaining() < 20) return; // 16 bytes UUID + 4 bytes EntityID
            long most = buf.getLong();
            long least = buf.getLong();
            UUID token = new UUID(most, least);
            int entityId = buf.getInt();

            if (expectedTokens.containsKey(entityId) && expectedTokens.get(entityId)
                .equals(token)) {
                activeConnections.put(packet.getSocketAddress(), entityId);
                activePlayers.put(entityId, packet.getSocketAddress());
                FreakCam.LOG.info("UDP Client authenticated for entity " + entityId);
            }
        } else if (packetId == 1 || packetId == 2) { // Video (1) or Audio (2) Packet
            Integer senderId = activeConnections.get(packet.getSocketAddress());
            if (senderId != null) {
                // Prepend senderId (4 bytes) to the payload
                byte[] originalData = packet.getData();
                int originalOffset = packet.getOffset();
                int originalLength = packet.getLength();

                byte[] newData = new byte[originalLength + 4];
                newData[0] = packetId;

                newData[1] = (byte) (senderId.intValue() >> 24);
                newData[2] = (byte) (senderId.intValue() >> 16);
                newData[3] = (byte) (senderId.intValue() >> 8);
                newData[4] = (byte) (senderId.intValue());

                System.arraycopy(originalData, originalOffset + 1, newData, 5, originalLength - 1);

                DatagramPacket broadcastPacket = new DatagramPacket(newData, newData.length);
                broadcastToNearby(senderId, broadcastPacket);
            }
        }
    }

    private static void broadcastToNearby(int senderId, DatagramPacket originalPacket) {
        EntityPlayerMP sender = players.get(senderId);
        if (sender == null) return;

        double maxDistSq = 64 * 64; // 64 blocks radius

        for (Map.Entry<Integer, EntityPlayerMP> entry : players.entrySet()) {
            int targetId = entry.getKey();
            if (targetId == senderId) continue;

            EntityPlayerMP target = entry.getValue();
            if (target.dimension == sender.dimension && target.getDistanceSqToEntity(sender) <= maxDistSq) {
                SocketAddress targetAddr = activePlayers.get(targetId);
                if (targetAddr != null) {
                    try {
                        originalPacket.setSocketAddress(targetAddr);
                        socket.send(originalPacket);
                    } catch (IOException e) {
                        FreakCam.LOG.error("Failed to send UDP packet to " + targetId, e);
                    }
                }
            }
        }
    }
}
