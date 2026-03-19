package mesh.models;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class MeshMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String messageId;
    private final String senderId;
    private final String senderName;
    private final String targetId;
    private final String text;
    private final Instant timestamp;
    private int ttl;
    private final MessageType type;

    public MeshMessage(String senderId, String senderName, String targetId,
                       String text, MessageType type) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.text = text;
        this.timestamp = Instant.now();
        this.ttl = 5;
        this.type = type;
    }

    // Геттеры
    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getTargetId() { return targetId; }
    public String getText() { return text; }
    public Instant getTimestamp() { return timestamp; }
    public int getTtl() { return ttl; }
    public MessageType getType() { return type; }

    public void decrementTtl() { ttl--; }
    public boolean isExpired() { return ttl <= 0; }
}