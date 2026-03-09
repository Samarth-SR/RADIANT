package com.radiant.mesh.services;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.radiant.mesh.MainActivity;
import com.radiant.mesh.R;
import com.radiant.mesh.RadiantApp;
import com.radiant.mesh.crypto.KeyManager;
import com.radiant.mesh.mesh.MeshEngine;
import com.radiant.mesh.utils.ByteUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BLEService extends Service implements ScannerManager.ScannerCallback {

    private static final String TAG = "BLEService";
    private static final int NOTIFICATION_ID = 1;
    private final IBinder binder = new LocalBinder();

    private KeyManager keyManager;
    private AdvertiserManager advertiserManager;
    private ScannerManager scannerManager;
    private GATTServer gattServer;

    private boolean isMeshRunning = false;

    private final Map<String, Long> connectionCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    private final Map<String, String> lastDeviceMsgHashes = new HashMap<>();
    private final Map<String, Long> lastSuccessfulConnection = new HashMap<>();
    private static final long FORCE_SYNC_INTERVAL = 30000;

    private final Map<String, Long> discoveredPeers = new ConcurrentHashMap<>();
    private final Handler pruningHandler = new Handler(Looper.getMainLooper());
    private PeerCountListener peerCountListener;

    public interface PeerCountListener {
        void onPeerCountUpdated(int count);
    }

    public void setPeerCountListener(PeerCountListener listener) {
        this.peerCountListener = listener;
    }

    public class LocalBinder extends Binder {
        public BLEService getService() { return BLEService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        keyManager = new KeyManager(this);
        advertiserManager = new AdvertiserManager(this);
        advertiserManager.setDeviceHash(keyManager.getMyDeviceHashShort());
        scannerManager = new ScannerManager(this);
        gattServer = new GATTServer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundServiceNotification();
        return START_STICKY;
    }

    public void startMesh() {
        if (isMeshRunning) return;

        MeshEngine.getInstance().setAdvertiserManager(advertiserManager);

        gattServer.setOnDisconnectCallback(() -> {
            if (isMeshRunning && scannerManager != null) scannerManager.restartScanning();
        });
        gattServer.startServer();

        advertiserManager.startAdvertising();
        scannerManager.startScanning();
        startPeerPruning();
        isMeshRunning = true;
    }

    public void stopMesh() {
        if (advertiserManager != null) advertiserManager.stopAdvertising();
        if (scannerManager != null) scannerManager.stopScanning();
        if (gattServer != null) gattServer.stopServer();
        stopPeerPruning();
        discoveredPeers.clear();
        connectionCooldowns.clear();
        lastDeviceMsgHashes.clear();
        isMeshRunning = false;
        stopForeground(true);
    }

    @Override
    public void onDeviceFound(String address, byte[] remoteDeviceHash, byte[] remoteMsgHash) {
        boolean isNew = !discoveredPeers.containsKey(address);
        discoveredPeers.put(address, System.currentTimeMillis());
        if (isNew && peerCountListener != null) {
            peerCountListener.onPeerCountUpdated(discoveredPeers.size());
        }

        String remoteHashStr = ByteUtils.toHexString(remoteMsgHash);
        String lastSeenHashStr = lastDeviceMsgHashes.get(address);

        boolean hasNewData = !remoteHashStr.equals(lastSeenHashStr);
        boolean forceSync = false;

        if (lastSuccessfulConnection.containsKey(address)) {
            if (System.currentTimeMillis() - lastSuccessfulConnection.get(address) > FORCE_SYNC_INTERVAL) {
                forceSync = true;
            }
        } else {
            forceSync = true;
        }

        if (isOnCooldown(address) && !hasNewData) return;
        if (!hasNewData && !forceSync) return;

        lastDeviceMsgHashes.put(address, remoteHashStr);
        connectToPeer(address);

        connectionCooldowns.put(address, System.currentTimeMillis());
        lastSuccessfulConnection.put(address, System.currentTimeMillis());
    }

    private void connectToPeer(String address) {
        // STOP scanning before connecting. Critical for reliability.
        scannerManager.stopScanning();

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothDevice device = btManager.getAdapter().getRemoteDevice(address);

        GATTClient client = new GATTClient(this, MeshEngine.getInstance().getMessagesForPeer());

        client.setOnDisconnectCallback(() -> {
            // Restart scanning only after disconnection
            if (isMeshRunning && scannerManager != null) {
                scannerManager.restartScanning();
            }
        });

        // 150ms delay to let radio switch modes
        new Handler(Looper.getMainLooper()).postDelayed(() -> client.connect(device), 150);
    }

    private boolean isOnCooldown(String address) {
        if (!connectionCooldowns.containsKey(address)) return false;
        long lastTime = connectionCooldowns.get(address);
        if (System.currentTimeMillis() - lastTime > COOLDOWN_MS) {
            connectionCooldowns.remove(address);
            return false;
        }
        return true;
    }

    private final Runnable pruningRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isMeshRunning) return;
            long now = System.currentTimeMillis();
            boolean changed = false;
            Iterator<Map.Entry<String, Long>> it = discoveredPeers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                if (now - entry.getValue() > 15000) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed && peerCountListener != null) {
                peerCountListener.onPeerCountUpdated(discoveredPeers.size());
            }
            pruningHandler.postDelayed(this, 5000);
        }
    };

    private void startPeerPruning() { pruningHandler.post(pruningRunnable); }
    private void stopPeerPruning() { pruningHandler.removeCallbacks(pruningRunnable); }

    private void startForegroundServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, RadiantApp.CHANNEL_ID)
                .setContentTitle("RADIANT Mesh Active")
                .setContentText("Scanning and Advertising in background...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }
}