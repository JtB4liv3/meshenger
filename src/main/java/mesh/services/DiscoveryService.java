package mesh.services;

import mesh.core.MeshNode;
import mesh.core.NodeInfo;
import mesh.core.RoutingTable;
import mesh.models.MeshMessage;
import mesh.models.MessageType;
import mesh.utils.NetworkUtils;

import java.io.*;
import java.net.*;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String BROADCAST_ADDR = "255.255.255.255";

    private final MeshNode meshNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private boolean running;

    public DiscoveryService(MeshNode meshNode, RoutingTable routingTable) {
        this.meshNode = meshNode;
        this.routingTable = routingTable;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(DISCOVERY_PORT);
        socket.setBroadcast(true);
        socket.setReuseAddress(true);

        running = true;

        // Поток для прослушивания
        new Thread(this::listenForDiscoveries).start();

        // Поток для рассылки объявлений
        new Thread(this::announcePresence).start();
    }

    private void listenForDiscoveries() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                // Игнорируем свои сообщения
                if (packet.getAddress().getHostAddress().equals(NetworkUtils.getLocalIpAddress())) {
                    continue;
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                MeshMessage message = (MeshMessage) ois.readObject();

                handleDiscoveryMessage(message, packet.getAddress());

            } catch (SocketException e) {
                if (running) break;
            } catch (Exception e) {
                // Игнорируем ошибки десериализации
            }
        }
    }

    private void handleDiscoveryMessage(MeshMessage message, InetAddress address) {
        switch (message.getType()) {
            case DISCOVERY:
                sendDiscoveryReply(address);
                break;

            case DISCOVERY_REPLY:
                NodeInfo newNode = new NodeInfo(
                        message.getSenderId(),
                        address,
                        DISCOVERY_PORT
                );
                meshNode.onNewNeighbor(newNode);
                break;
        }
    }

    private void announcePresence() {
        while (running) {
            try {
                MeshMessage discoveryMsg = new MeshMessage(
                        meshNode.getNodeId(),
                        meshNode.getNodeName(),
                        "ALL",
                        "DISCOVERY",
                        MessageType.DISCOVERY
                );

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(discoveryMsg);

                DatagramPacket packet = new DatagramPacket(
                        baos.toByteArray(), baos.size(),
                        InetAddress.getByName(BROADCAST_ADDR), DISCOVERY_PORT
                );
                socket.send(packet);

                Thread.sleep(5000); // Каждые 5 секунд

            } catch (Exception e) {
                if (running) break;
            }
        }
    }

    private void sendDiscoveryReply(InetAddress targetAddress) {
        try {
            MeshMessage replyMsg = new MeshMessage(
                    meshNode.getNodeId(),
                    meshNode.getNodeName(),
                    "ALL",
                    "DISCOVERY_REPLY",
                    MessageType.DISCOVERY_REPLY
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(replyMsg);

            DatagramPacket packet = new DatagramPacket(
                    baos.toByteArray(), baos.size(),
                    targetAddress, DISCOVERY_PORT
            );
            socket.send(packet);

        } catch (Exception e) {
            // Игнорируем
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}