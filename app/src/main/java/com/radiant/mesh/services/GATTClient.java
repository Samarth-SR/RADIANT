package com.radiant.mesh.services;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.radiant.mesh.model.MeshMessage;
import com.radiant.mesh.utils.BinaryPacker;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GATTClient {

    private static final String TAG = "GATTClient";
    private final Context context;
    private BluetoothGatt bluetoothGatt;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ConcurrentLinkedQueue<MeshMessage> messageQueue;
    private Runnable onDisconnectCallback;

    public GATTClient(Context context, List<MeshMessage> messages) {
        this.context = context;
        this.messageQueue = new ConcurrentLinkedQueue<>(messages);
    }

    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    public void connect(BluetoothDevice device) {
        if (device == null) return;
        Log.d(TAG, "Connecting to " + device.getAddress());

        // AutoConnect = false is mandatory for speed
        this.bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);

        // 8s timeout: if it hangs, kill it
        handler.postDelayed(this::disconnect, 8000);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        if (onDisconnectCallback != null) {
            handler.post(onDisconnectCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected. Boosting Priority...");

                // CRITICAL FOR SPEED: 11ms-15ms latency
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

                // Wait 150ms for priority to apply, then request MTU
                handler.postDelayed(() -> gatt.requestMtu(512), 150);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            gatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendNextMessage(gatt);
            } else {
                disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendNextMessage(gatt); // Send next immediately
            } else {
                disconnect();
            }
        }
    };

    private void sendNextMessage(BluetoothGatt gatt) {
        if (messageQueue.isEmpty()) {
            disconnect();
            return;
        }

        BluetoothGattService service = gatt.getService(AdvertiserManager.SERVICE_UUID);
        if (service == null) { disconnect(); return; }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(GATTServer.CHARACTERISTIC_UUID);
        if (characteristic == null) { disconnect(); return; }

        MeshMessage msg = messageQueue.poll();
        if (msg != null) {
            byte[] data = BinaryPacker.packMessage(msg);
            characteristic.setValue(data);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            if (!gatt.writeCharacteristic(characteristic)) {
                disconnect();
            }
        } else {
            disconnect();
        }
    }
}