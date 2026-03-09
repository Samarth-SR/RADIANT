package com.radiant.mesh.services;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.radiant.mesh.mesh.MeshEngine;
import com.radiant.mesh.model.MeshMessage;
import com.radiant.mesh.utils.BinaryPacker;

import java.util.UUID;

/**
 * Acts as the receiver.
 * Opens a GATT Server and listens for incoming "Write" requests.
 * When data is received, it unpacks it and sends it to the MeshEngine.
 */
public class GATTServer {

    private static final String TAG = "GATTServer";

    // UUID for the Characteristic where messages are written
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000D00D-0000-1000-8000-00805F9B34FC");

    private BluetoothManager bluetoothManager;
    private BluetoothGattServer gattServer;
    private Context context;

    // Callback when a device disconnects from server
    private Runnable onDisconnectCallback;

    public GATTServer(Context context) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    public void startServer() {
        if (bluetoothManager == null) return;

        // Open the server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Unable to create GATT Server");
            return;
        }

        // Create the Service
        BluetoothGattService service = new BluetoothGattService(
                AdvertiserManager.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // Create the Write Characteristic
        BluetoothGattCharacteristic writeChar = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        service.addCharacteristic(writeChar);

        // Add Service to Server
        gattServer.addService(service);
        Log.d(TAG, "GATT Server Started. Ready to receive messages.");
    }

    public void stopServer() {
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
    }

    /**
     * Callback handling incoming events.
     */
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Device Connected to Server: " + device.getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Device Disconnected from Server: " + device.getAddress());
                if (onDisconnectCallback != null) {
                    onDisconnectCallback.run();
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            Log.d(TAG, "Data received from " + device.getAddress() + ", size: " + value.length);

            // 1. Acknowledge the write if response needed
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            // 2. Process Data
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                try {
                    MeshMessage msg = BinaryPacker.unpackMessage(value);
                    if (msg != null) {
                        Log.d(TAG, "Valid MeshMessage received: " + msg.msgId);
                        // Forward to Mesh Logic
                        MeshEngine.getInstance().processIncomingMessage(msg);
                    } else {
                        Log.w(TAG, "Received malformed or incomplete packet");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error unpacking message", e);
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "MTU changed to: " + mtu);
            // We accept whatever MTU the client requests
        }
    };
}