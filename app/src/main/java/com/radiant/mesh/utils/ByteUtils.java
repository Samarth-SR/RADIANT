package com.radiant.mesh.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for Hex conversion, Hashing, and Byte manipulation.
 * Essential for generating Device IDs and handling raw BLE data.
 */
public class ByteUtils {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Converts a byte array to a hex string (e.g., [0x0A, 0xFF] -> "0AFF").
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Converts a hex string back to a byte array.
     */
    public static byte[] fromHexString(String s) {
        if (s == null || s.length() == 0) return new byte[0];
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Generates a SHA-256 hash of the input string.
     * Used for creating Device Hashes from Public Keys.
     */
    public static byte[] hash256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported on this device", e);
        }
    }

    /**
     * Returns the first 8 bytes of a SHA-256 hash.
     * Used for efficient BLE advertising (Short Device ID).
     */
    public static long getDeviceHash(String uniqueId) {
        byte[] hash = hash256(uniqueId);
        long result = 0;
        // Take first 8 bytes
        for (int i = 0; i < 8; i++) {
            result = (result << 8) + (hash[i] & 0xff);
        }
        return result;
    }
    public static byte[] longToBytes(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES); // Long.BYTES is 8
        buffer.putLong(value);
        return buffer.array();
    }
}