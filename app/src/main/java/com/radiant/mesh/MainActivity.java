package com.radiant.mesh;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.radiant.mesh.services.BLEService;

import java.util.ArrayList;
import java.util.List;

/**
 * MVP Dashboard.
 * 1. Checks Critical Permissions (Bluetooth, Location).
 * 2. Binds to BLEService.
 * 3. Allows user to Start Mesh or Enter Chat.
 */
public class MainActivity extends AppCompatActivity implements BLEService.PeerCountListener {

    private static final int PERMISSION_REQUEST_CODE = 101;

    // UI Elements
    private TextView statusText;
    private TextView peersText;
    private Button btnStartMesh;
    private Button btnChat;

    // Service Binding
    private BLEService bleService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BLEService.LocalBinder binder = (BLEService.LocalBinder) service;
            bleService = binder.getService();
            isBound = true;

            // Link Peer Count Listener
            bleService.setPeerCountListener(MainActivity.this);

            updateStatus("Service Connected. Ready.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            updateStatus("Service Disconnected.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Minimal Layout Setup (Programmatic or Layout XML assumed)
        setContentView(R.layout.activity_main);

        // Bind UI Views (Assuming standard IDs)
        statusText = findViewById(R.id.tv_status);
        peersText = findViewById(R.id.tv_peers);
        btnStartMesh = findViewById(R.id.btn_start_mesh);
        btnChat = findViewById(R.id.btn_chat);

        // Check Permissions immediately on launch
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            bindMeshService();
        }

        btnStartMesh.setOnClickListener(v -> {
            if (isBound && bleService != null) {
                // Placeholder: Start logic
                bleService.startMesh();
                updateStatus("Mesh Started: Scanning & Advertising...");
                btnStartMesh.setEnabled(false);
            }
        });

        btnChat.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            startActivity(intent);
        });
    }

    // --- PeerCountListener Implementation ---
    @Override
    public void onPeerCountUpdated(int count) {
        runOnUiThread(() -> {
            if (peersText != null) {
                peersText.setText("Nearby Peers: " + count);
            }
        });
    }
    // ----------------------------------------

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindMeshService();
            } else {
                Toast.makeText(this, "Permissions required for Mesh", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void bindMeshService() {
        Intent intent = new Intent(this, BLEService.class);
        // Start Foreground Service first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        // Bind for interaction
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void updateStatus(String msg) {
        if (statusText != null) {
            statusText.setText("Status: " + msg);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            // Remove listener to prevent leaks
            if (bleService != null) {
                bleService.setPeerCountListener(null);
            }
            unbindService(connection);
            isBound = false;
        }
    }
}