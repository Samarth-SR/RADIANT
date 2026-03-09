package com.radiant.mesh.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.radiant.mesh.utils.ByteUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Manages the persistent storage of the user's Identity Keys.
 * In a production app, these should be stored in the Android Keystore System.
 * For MVP, we use SharedPreferences with Base64 encoding.
 */
public class KeyManager {

    private static final String TAG = "KeyManager";
    private static final String PREF_NAME = "RadiantKeys";
    private static final String KEY_PUB = "local_public";
    private static final String KEY_PRIV = "local_private";

    private final CryptoEngine cryptoEngine;
    private final SharedPreferences prefs;

    private KeyPair myKeyPair;
    private byte[] myDeviceHash;

    public KeyManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.cryptoEngine = new CryptoEngine();
        loadOrGenerateKeys();
    }

    private void loadOrGenerateKeys() {
        String pubStr = prefs.getString(KEY_PUB, null);
        String privStr = prefs.getString(KEY_PRIV, null);

        if (pubStr != null && privStr != null) {
            try {
                // Reconstruct keys from storage
                byte[] pubBytes = Base64.decode(pubStr, Base64.DEFAULT);
                byte[] privBytes = Base64.decode(privStr, Base64.DEFAULT);

                KeyFactory kf = KeyFactory.getInstance("EC");
                PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                
                this.myKeyPair = new KeyPair(pub, priv);
            } catch (Exception e) {
                Log.e(TAG, "Error loading keys, regenerating...", e);
                generateNewKeys();
            }
        } else {
            generateNewKeys();
        }
        
        // Compute Hash
        if (myKeyPair != null) {
            String pubKeyString = Base64.encodeToString(myKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
            this.myDeviceHash = ByteUtils.hash256(pubKeyString);
        }
    }

    private void generateNewKeys() {
        this.myKeyPair = cryptoEngine.generateKeyPair();
        if (this.myKeyPair != null) {
            String pubBase64 = Base64.encodeToString(this.myKeyPair.getPublic().getEncoded(), Base64.DEFAULT);
            String privBase64 = Base64.encodeToString(this.myKeyPair.getPrivate().getEncoded(), Base64.DEFAULT);

            prefs.edit()
                    .putString(KEY_PUB, pubBase64)
                    .putString(KEY_PRIV, privBase64)
                    .apply();
        }
    }

    public KeyPair getMyKeyPair() {
        return myKeyPair;
    }

    /**
     * Returns the 8-byte simplified identity hash for BLE Advertising.
     */
    public byte[] getMyDeviceHashShort() {
        if (myDeviceHash == null) return new byte[8];
        byte[] shortHash = new byte[8];
        System.arraycopy(myDeviceHash, 0, shortHash, 0, 8);
        return shortHash;
    }
}