package com.radiant.mesh.mesh;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stores recent Message IDs to prevent processing or rebroadcasting the same message twice.
 * This is critical for the "Epidemic Routing" protocol to avoid infinite loops.
 */
public class DedupTable {

    // Maximum number of IDs to keep in memory
    private static final int MAX_ENTRIES = 200;
    
    // Time before an ID is considered expired (5 minutes in milliseconds)
    private static final long EXPIRATION_MS = 5 * 60 * 1000;

    // LinkedHashMap with access-order allows us to implement a simple LRU cache
    private final Map<String, Long> seenMessages;

    public DedupTable() {
        // limit size to MAX_ENTRIES
        this.seenMessages = Collections.synchronizedMap(
                new LinkedHashMap<String, Long>(MAX_ENTRIES + 1, .75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                        return size() > MAX_ENTRIES;
                    }
                }
        );
    }

    /**
     * Checks if a message ID has been seen recently.
     * @return true if seen, false if new.
     */
    public boolean isAlreadyProcessed(String msgId) {
        if (seenMessages.containsKey(msgId)) {
            return true;
        }
        return false;
    }

    /**
     * Adds a message ID to the table with the current timestamp.
     */
    public void add(String msgId) {
        seenMessages.put(msgId, System.currentTimeMillis());
    }

    /**
     * Removes expired entries to keep the map clean.
     * Should be called periodically (e.g., during scanning cycles).
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        synchronized (seenMessages) {
            Iterator<Map.Entry<String, Long>> it = seenMessages.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                if (now - entry.getValue() > EXPIRATION_MS) {
                    it.remove();
                }
            }
        }
    }
}