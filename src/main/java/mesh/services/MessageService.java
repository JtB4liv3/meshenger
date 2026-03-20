package mesh.services;

import mesh.core.MeshNode;
import mesh.core.NodeInfo;
import mesh.core.RoutingTable;
import mesh.models.MeshMessage;
import mesh.models.MessageType;
import mesh.utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageService {
    private static final int MESSAGE_PORT = 8889;
    private static final int DISCOVERY_PORT = 8888; // Для совместимости
    private static final int MAX_RETRIES = 2;
    private static final int ACK_TIMEOUT = 2000;

    private final MeshNode meshNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private boolean running;

    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private final Set<String> awaitingAck = ConcurrentHashMap.newKeySet();

    public interface DeliveryCallback {
        void onSuccess();
        void onFailure();
    }

    public MessageService(MeshNode meshNode, RoutingTable routingTable) {
        this.meshNode = meshNode;
        this.routingTable = routingTable;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(MESSAGE_PORT);
        socket.setReuseAddress(true);
        running = true;

        new Thread(this::listenForMessages).start();
    }

    private void listenForMessages() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                MeshMessage message = (MeshMessage) ois.readObject();

                handleIncomingMessage(message, packet.getAddress());

            } catch (SocketTimeoutException e) {
                // Таймаут - нормально
            } catch (Exception e) {
                if (running) {
                    System.err.println("Ошибка приема: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncomingMessage(MeshMessage message, InetAddress fromAddress) {
        // Дедупликация - предотвращаем зацикливание
        if (processedMessages.contains(message.getMessageId())) {
            return;
        }
        processedMessages.add(message.getMessageId());

        // Если это подтверждение
        if (message.getType() == MessageType.ACK) {
            awaitingAck.remove(message.getText()); // В тексте ID исходного сообщения
            return;
        }

        // Уменьшаем TTL
        message.decrementTtl();

        if (message.isExpired()) {
            return;
        }

        // ВАЖНО: Всегда добавляем информацию об отправителе в таблицу маршрутизации
        // Это нужно, чтобы узлы узнавали друг о друге через пересылаемые сообщения
        NodeInfo senderNode = new NodeInfo(
                message.getSenderId(),
                fromAddress,
                DISCOVERY_PORT
        );

        // Уведомляем meshNode о новом узле (если его еще нет)
        meshNode.onNewNeighbor(senderNode);

        // Проверяем, нам ли сообщение
        boolean isForUs = message.getTargetId().equals(meshNode.getNodeId()) ||
                message.getType() == MessageType.BROADCAST;

        if (isForUs) {
            meshNode.onMessageReceived(
                    message.getSenderId(),
                    message.getSenderName(),
                    message.getText()
            );
            sendAck(message.getMessageId(), fromAddress);
        }

        // ВАЖНО: Пересылаем ВСЕГДА, кроме случая, когда это ACK
        // Даже если сообщение для нас, другие узлы тоже должны его получить
        if (message.getType() != MessageType.ACK) {
            forwardMessage(message, fromAddress);
        }
    }

    private void sendAck(String messageId, InetAddress toAddress) {
        try {
            MeshMessage ackMsg = new MeshMessage(
                    meshNode.getNodeId(),
                    meshNode.getNodeName(),
                    "ACK",
                    messageId,
                    MessageType.ACK
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(ackMsg);

            DatagramPacket packet = new DatagramPacket(
                    baos.toByteArray(), baos.size(),
                    toAddress, MESSAGE_PORT
            );
            socket.send(packet);

        } catch (Exception e) {
            // Игнорируем
        }
    }

    private void forwardMessage(MeshMessage message, InetAddress fromAddress) {
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                // Не отправляем обратно отправителю
                if (neighbor.getAddress().equals(fromAddress)) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);

                DatagramPacket packet = new DatagramPacket(
                        baos.toByteArray(), baos.size(),
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(packet);

            } catch (Exception e) {
                // Игнорируем ошибки пересылки
            }
        }
    }

    public void sendMessage(String targetId, String text, DeliveryCallback callback) {
        MeshMessage message = new MeshMessage(
                meshNode.getNodeId(),
                meshNode.getNodeName(),
                targetId,
                text,
                MessageType.USER_MESSAGE
        );

        processedMessages.add(message.getMessageId());
        awaitingAck.add(message.getMessageId());

        // Отправка с подтверждением
        new Thread(() -> {
            boolean delivered = sendWithAck(message);
            if (callback != null) {
                if (delivered) {
                    callback.onSuccess();
                } else {
                    callback.onFailure();
                }
            }
        }).start();
    }

    private boolean sendWithAck(MeshMessage message) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Отправляем всем соседям
            for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(message);

                    DatagramPacket packet = new DatagramPacket(
                            baos.toByteArray(), baos.size(),
                            neighbor.getAddress(), MESSAGE_PORT
                    );
                    socket.send(packet);

                } catch (Exception e) {
                    // Игнорируем
                }
            }

            // Ждем подтверждение
            try {
                Thread.sleep(ACK_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!awaitingAck.contains(message.getMessageId())) {
                return true;
            }
        }

        awaitingAck.remove(message.getMessageId());
        return false;
    }

    public void broadcastMessage(String text) {
        MeshMessage message = new MeshMessage(
                meshNode.getNodeId(),
                meshNode.getNodeName(),
                "ALL",
                text,
                MessageType.BROADCAST
        );

        processedMessages.add(message.getMessageId());

        // Для broadcast не ждем подтверждения, просто рассылаем
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);

                DatagramPacket packet = new DatagramPacket(
                        baos.toByteArray(), baos.size(),
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(packet);

            } catch (Exception e) {
                // Игнорируем
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}