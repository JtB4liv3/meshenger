package mesh.core;

import mesh.services.DiscoveryService;
import mesh.services.MessageService;
import mesh.utils.NetworkUtils;

import java.net.SocketException;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MeshNode {

    private final String nodeId;
    private final String nodeName;
    private final RoutingTable routingTable;
    private DiscoveryService discoveryService;
    private MessageService messageService;
    private ScheduledExecutorService scheduler;
    private boolean running;

    // Интерфейс для обновления UI
    public interface UIUpdater {
        void onNodeStarted(String nodeId);
        void onNewNeighbor(NodeInfo neighbor);
        void onNeighborLost(String nodeId);
        void onMessageReceived(String senderId, String senderName, String text);
        void onMessageDelivered(String targetId, boolean success);
    }

    private final UIUpdater uiUpdater;

    public MeshNode(UIUpdater uiUpdater) {
        this.nodeId = NetworkUtils.generateNodeId();
        this.nodeName = "Node-" + nodeId.substring(0, 4);
        this.routingTable = new RoutingTable(nodeId);
        this.uiUpdater = uiUpdater;
    }

    public void start() throws SocketException {
        discoveryService = new DiscoveryService(this, routingTable);
        messageService = new MessageService(this, routingTable);

        discoveryService.start();
        messageService.start();

        running = true;

        // Планировщик для обновления статистики
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupStaleNeighbors, 10, 10, TimeUnit.SECONDS);

        if (uiUpdater != null) {
            uiUpdater.onNodeStarted(nodeId);
        }
    }

    private void cleanupStaleNeighbors() {
        routingTable.cleanupStaleNeighbors(20000); // 20 секунд таймаут
    }

    public void sendMessage(String targetId, String text) {
        if (messageService != null) {
            messageService.sendMessage(targetId, text, new MessageService.DeliveryCallback() {
                @Override
                public void onSuccess() {
                    if (uiUpdater != null) {
                        uiUpdater.onMessageDelivered(targetId, true);
                    }
                }

                @Override
                public void onFailure() {
                    if (uiUpdater != null) {
                        uiUpdater.onMessageDelivered(targetId, false);
                    }
                }
            });
        }
    }

    public void broadcastMessage(String text) {
        if (messageService != null) {
            messageService.broadcastMessage(text);
        }
    }

    // Методы для обратного вызова из сервисов

    public void onNewNeighbor(NodeInfo neighbor) {
        routingTable.updateNeighbor(neighbor);
        if (uiUpdater != null) {
            uiUpdater.onNewNeighbor(neighbor);
        }
    }

    public void onNeighborLost(String nodeId) {
        if (uiUpdater != null) {
            uiUpdater.onNeighborLost(nodeId);
        }
    }

    public void onMessageReceived(String senderId, String senderName, String text) {
        if (uiUpdater != null) {
            uiUpdater.onMessageReceived(senderId, senderName, text);
        }
    }

    public void stop() {
        running = false;
        if (discoveryService != null) {
            discoveryService.stop();
        }
        if (messageService != null) {
            messageService.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    // Геттеры

    public String getNodeId() { return nodeId; }
    public String getNodeName() { return nodeName; }
    public Collection<NodeInfo> getNeighbors() { return routingTable.getAllNeighbors(); }
    public int getNeighborCount() { return routingTable.getNeighborCount(); }
}