package com.radiant.mesh.mesh;

import com.radiant.mesh.model.MeshMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StoreForwardBuffer {

    private static final int MAX_BUFFER_SIZE = 100; // Increased for better burst handling
    private final ConcurrentLinkedQueue<MeshMessage> buffer = new ConcurrentLinkedQueue<>();

    public void add(MeshMessage msg) {
        if (msg == null) return;

        // Simple dedup check
        for (MeshMessage m : buffer) {
            if (m.msgId.equals(msg.msgId)) return;
        }

        buffer.add(msg);

        while (buffer.size() > MAX_BUFFER_SIZE) {
            buffer.poll();
        }
    }

    public void clear() {
        buffer.clear();
    }

    public List<MeshMessage> getMessagesToSend() {
        return new ArrayList<>(buffer);
    }
}