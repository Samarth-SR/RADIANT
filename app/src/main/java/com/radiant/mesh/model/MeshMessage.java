package com.radiant.mesh.model;

import java.util.UUID;

/**
 * Core Data Model representing a message in the Mesh.
 * Holds metadata (ID, TTL, Timestamp) and the encrypted payload.
 */
public class MeshMessage {

    // Unique Identifier (UUID)
    public String msgId;
    
    // Hash of the sender's public key (for routing logic, not identification)
    public String senderHash;
    
    // Time To Live: Decrements on every hop. 0 = Drop.
    public int ttl;
    
    // Unix Timestamp (Seconds)
    public long timestamp;
    
    // Encrypted Content (AES-GCM Ciphertext)
    public byte[] payload;

    public MeshMessage(String msgId, String senderHash, int ttl, long timestamp, byte[] payload) {
        this.msgId = msgId;
        this.senderHash = senderHash;
        this.ttl = ttl;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    /**
     * Helper to create a new fresh message.
     */
    public static MeshMessage create(String senderHash, byte[] encryptedPayload) {
        return new MeshMessage(
                UUID.randomUUID().toString(),
                senderHash,
                4, // Default MVP TTL
                System.currentTimeMillis() / 1000, // Seconds
                encryptedPayload
        );
    }
}