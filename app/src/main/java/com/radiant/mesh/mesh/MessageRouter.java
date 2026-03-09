package com.radiant.mesh.mesh;

import com.radiant.mesh.model.MeshMessage;
import java.util.Random;

/**
 * The Logic Gate of the Mesh.
 * Handles:
 * 1. Deduplication (Have I seen this?)
 * 2. TTL Checks (Is the message dead?)
 * 3. Jitter (Random delay calculation)
 */
public class MessageRouter {

    private final DedupTable dedupTable;
    private final Random random = new Random();

    public MessageRouter() {
        this.dedupTable = new DedupTable();
    }

    /**
     * Checks if a message should be processed or ignored.
     * @return true if new and valid, false if duplicate.
     */
    public boolean isNewMessage(String msgId) {
        if (dedupTable.isAlreadyProcessed(msgId)) {
            return false;
        }
        dedupTable.add(msgId);
        return true;
    }

    /**
     * Checks if a message is valid for relaying (TTL > 0).
     */
    public boolean shouldRelay(MeshMessage msg) {
        return msg.ttl > 0;
    }

    /**
     * Creates a copy of the message with decremented TTL.
     */
    public MeshMessage decrementTtl(MeshMessage msg) {
        return new MeshMessage(
                msg.msgId,
                msg.senderHash,
                msg.ttl - 1,
                msg.timestamp,
                msg.payload
        );
    }

    /**
     * Returns a random jitter delay (50ms - 150ms) to prevent broadcast collisions.
     */
    public long getJitterDelay() {
        return 50 + random.nextInt(100);
    }

    /**
     * Maintenance method to clean old dedup entries.
     */
    public void cleanup() {
        dedupTable.cleanup();
    }
}