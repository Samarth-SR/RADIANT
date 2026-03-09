package com.radiant.mesh.utils;

import com.radiant.mesh.model.MeshMessage;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Handles low-level serialization of MeshMessages into Byte Arrays.
 * This ensures data fits efficiently into BLE GATT Characteristics.
 * 
 * Format:
 * [UUID (16b)] [SenderHash (8b)] [TTL (4b)] [Time (8b)] [Payload Length (4b)] [Payload (N bytes)]
 */
public class BinaryPacker {

    // Fixed header size: UUID(16) + SenderHash(8) + TTL(4) + Time(8) + Length(4) = 40 bytes
    // Note: This is relatively large for BLE, but necessary for robust MVP routing.
    // Optimization: In a real scenario, we would compress UUIDs or use short IDs.
    private static final int HEADER_SIZE = 40; 

    /**
     * Packs a MeshMessage into a byte array.
     */
    public static byte[] packMessage(MeshMessage msg) {
        if (msg == null || msg.payload == null) return new byte[0];

        int totalSize = HEADER_SIZE + msg.payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        // 1. Pack UUID (128 bits / 16 bytes)
        UUID uuid = UUID.fromString(msg.msgId);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        // 2. Pack Sender Hash (stored as Hex String, converted to 8 bytes long)
        // If the string isn't exactly a long representation, we hash it to 8 bytes.
        long senderLong;
        try {
            // Simplified: We assume senderHash is a hex string of a long, or we generate a hash
            senderLong = ByteUtils.getDeviceHash(msg.senderHash);
        } catch (Exception e) {
            senderLong = 0;
        }
        buffer.putLong(senderLong);

        // 3. Pack TTL
        buffer.putInt(msg.ttl);

        // 4. Pack Timestamp
        buffer.putLong(msg.timestamp);

        // 5. Pack Payload Length
        buffer.putInt(msg.payload.length);

        // 6. Pack Payload
        buffer.put(msg.payload);

        return buffer.array();
    }

    /**
     * Unpacks a byte array into a MeshMessage.
     */
    public static MeshMessage unpackMessage(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) return null;

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Unpack UUID
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        UUID uuid = new UUID(mostSigBits, leastSigBits);
        String msgId = uuid.toString();

        // 2. Unpack Sender Hash
        long senderHashLong = buffer.getLong();
        // Convert back to Hex String for consistency
        String senderHash = Long.toHexString(senderHashLong);

        // 3. Unpack TTL
        int ttl = buffer.getInt();

        // 4. Unpack Timestamp
        long timestamp = buffer.getLong();

        // 5. Unpack Payload Length
        int length = buffer.getInt();

        // Safety check for malformed packets
        if (buffer.remaining() < length) return null;

        // 6. Unpack Payload
        byte[] payload = new byte[length];
        buffer.get(payload);

        return new MeshMessage(msgId, senderHash, ttl, timestamp, payload);
    }
}