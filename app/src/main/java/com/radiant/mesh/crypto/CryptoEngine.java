package com.radiant.mesh.crypto;

import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles all Cryptographic operations:
 * 1. Key Pair Generation (EC NIST P-256)
 * 2. Shared Secret Derivation (ECDH)
 * 3. Message Encryption/Decryption (AES-256-GCM)
 */
public class CryptoEngine {

    private static final String TAG = "CryptoEngine";
    private static final String KEY_ALGO = "EC";
    private static final String CURVE_NAME = "secp256r1"; // NIST P-256
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Generates a new Elliptic Curve KeyPair.
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGO);
            keyPairGenerator.initialize(new ECGenParameterSpec(CURVE_NAME));
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate KeyPair", e);
            return null;
        }
    }

    /**
     * Derives a Shared Secret using Elliptic Curve Diffie-Hellman (ECDH).
     * @param myPrivateKey Local Private Key
     * @param otherPublicKey Remote Public Key
     * @return 32-byte AES Key derived from the shared secret.
     */
    public SecretKey computeSharedSecret(PrivateKey myPrivateKey, PublicKey otherPublicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(myPrivateKey);
            keyAgreement.doPhase(otherPublicKey, true);
            
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Hash the raw secret to get a clean 256-bit AES key
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(sharedSecret);
            
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "ECDH Failed", e);
            return null;
        }
    }

    /**
     * Encrypts plain text using AES-GCM.
     * Output format: [IV (12 bytes)] + [Ciphertext]
     */
    public byte[] encryptMessage(String plainText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and CipherText
            byte[] output = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
            
            return output;
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypts AES-GCM data.
     * Input format: [IV (12 bytes)] + [Ciphertext]
     */
    public String decryptMessage(byte[] encryptedData, SecretKey key) {
        try {
            if (encryptedData.length < GCM_IV_LENGTH) return null;

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);

            // Extract CipherText
            int cipherSize = encryptedData.length - GCM_IV_LENGTH;
            byte[] cipherText = new byte[cipherSize];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherSize);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }
}