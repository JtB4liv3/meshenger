package mesh.core;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    private final String localNodeId;
    private final Map<String, NodeInfo> neighbors;
    private final Map<String, Long> lastSeen;

    public RoutingTable(String localNodeId) {
        this.localNodeId = localNodeId;
        this.neighbors = new ConcurrentHashMap<>();
        this.lastSeen = new ConcurrentHashMap<>();
    }

    public void updateNeighbor(NodeInfo neighbor) {
        boolean isNew = !neighbors.containsKey(neighbor.getNodeId());
        neighbors.put(neighbor.getNodeId(), neighbor);
        lastSeen.put(neighbor.getNodeId(), System.currentTimeMillis());
    }

    public void cleanupStaleNeighbors(long timeoutMs) {
        long now = System.currentTimeMillis();
        neighbors.entrySet().removeIf(entry -> {
            Long last = lastSeen.get(entry.getKey());
            return last != null && (now - last) > timeoutMs;
        });
    }

    public Collection<NodeInfo> getAllNeighbors() {
        return neighbors.values();
    }

    public NodeInfo getNeighborInfo(String nodeId) {
        return neighbors.get(nodeId);
    }

    public int getNeighborCount() {
        return neighbors.size();
    }
}