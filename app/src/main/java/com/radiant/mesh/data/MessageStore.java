package com.radiant.mesh.data;

import com.radiant.mesh.model.MeshMessage;
import com.radiant.mesh.utils.ByteUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MessageStore {

    // Thread-safe list
    private final List<MeshMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private byte[] lastMsgHash = new byte[8];

    public void addMessage(MeshMessage msg) {
        synchronized (messages) {
            messages.add(msg);
            // Sort only if needed (optimization)
            if (messages.size() > 1) {
                Collections.sort(messages, (o1, o2) -> Long.compare(o1.timestamp, o2.timestamp));
            }
            updateLatestHash();
        }
    }

    public void clear() {
        synchronized (messages) {
            messages.clear();
            this.lastMsgHash = new byte[8]; // Reset to zeroes
        }
    }

    public List<MeshMessage> getAllMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public byte[] getLatestMessageHash() {
        return lastMsgHash;
    }

    private void updateLatestHash() {
        if (!messages.isEmpty()) {
            MeshMessage last = messages.get(messages.size() - 1);
            long hashLong = ByteUtils.getDeviceHash(last.msgId);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putLong(hashLong);
            this.lastMsgHash = buffer.array();
        } else {
            this.lastMsgHash = new byte[8];
        }
    }
}