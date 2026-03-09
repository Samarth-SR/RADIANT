package com.radiant.mesh.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.UUID;

public class AdvertiserManager {

    private static final String TAG = "AdvertiserManager";
    public static final UUID SERVICE_UUID = UUID.fromString("0000D00D-0000-1000-8000-00805F9B34FB");

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private byte[] myDeviceHash = new byte[8];
    private byte[] latestMsgHash = new byte[8];

    public AdvertiserManager(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            this.advertiser = adapter.getBluetoothLeAdvertiser();
        }
    }

    public void startAdvertising() {
        if (advertiser == null || isAdvertising) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // High Frequency
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = buildAdvertiseData();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                isAdvertising = true;
            }
            @Override
            public void onStartFailure(int errorCode) {
                isAdvertising = false;
                Log.e(TAG, "Advertising failed: " + errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    public void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null && isAdvertising) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception e) { Log.e(TAG, "Error stopping adv", e); }
            isAdvertising = false;
        }
    }

    public void updateContext(byte[] newLatestMsgHash) {
        if (newLatestMsgHash != null && newLatestMsgHash.length == 8) {
            this.latestMsgHash = newLatestMsgHash;
            if (isAdvertising) {
                stopAdvertising();
                // *** SPEED FIX: Reduced restart delay to 50ms ***
                handler.postDelayed(this::startAdvertising, 50);
            }
        }
    }

    public void setDeviceHash(byte[] hash) {
        if (hash != null && hash.length == 8) this.myDeviceHash = hash;
    }

    private AdvertiseData buildAdvertiseData() {
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.put((byte) 1);
        buffer.put(myDeviceHash);
        buffer.put(latestMsgHash);
        buffer.put((byte) 0);

        return new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addManufacturerData(0xFFFF, buffer.array())
                .build();
    }
}