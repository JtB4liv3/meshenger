package mesh.core;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

public class NodeInfo {
    private final String nodeId;
    private final InetAddress address;
    private final int port;
    private Instant lastSeen;

    public NodeInfo(String nodeId, InetAddress address, int port) {
        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.lastSeen = Instant.now();
    }

    public String getNodeId() { return nodeId; }
    public InetAddress getAddress() { return address; }
    public int getPort() { return port; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return Objects.equals(nodeId, nodeInfo.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}