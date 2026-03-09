package com.radiant.mesh.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ScannerManager {

    private static final String TAG = "ScannerManager";

    // CONTINUOUS SCANNING for maximum speed
    private BluetoothLeScanner scanner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private ScanCallback scanCallback;

    public interface ScannerCallback {
        void onDeviceFound(String address, byte[] deviceHash, byte[] msgHash);
    }

    private ScannerCallback callback;

    public ScannerManager(ScannerCallback callback) {
        this.callback = callback;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            this.scanner = adapter.getBluetoothLeScanner();
        }
    }

    public void startScanning() {
        if (scanner == null) return;
        if (isScanning) return; // Already scanning

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(AdvertiserManager.SERVICE_UUID))
                .build());

        // *** SPEED FIX: LOW_LATENCY (High Duty Cycle) ***
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // Immediate reporting
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                processScanResult(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult r : results) {
                    processScanResult(r);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed: " + errorCode);
            }
        };

        scanner.startScan(filters, settings, scanCallback);
        isScanning = true;
        Log.d(TAG, "High Speed Scanning started...");

        // No auto-restart cycle needed for LOW_LATENCY unless it fails,
        // effectively continuous.
    }

    public void stopScanning() {
        if (scanner != null && scanCallback != null && isScanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Stop scan error", e);
            }
            isScanning = false;
        }
    }

    public void restartScanning() {
        stopScanning();
        // Minimal delay to clear stack
        handler.postDelayed(this::startScanning, 50);
    }

    private void processScanResult(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record == null) return;

        byte[] data = record.getManufacturerSpecificData(0xFFFF);
        if (data != null && data.length >= 17) {
            byte[] deviceHash = new byte[8];
            System.arraycopy(data, 1, deviceHash, 0, 8);

            byte[] msgHash = new byte[8];
            System.arraycopy(data, 9, msgHash, 0, 8);

            if (callback != null) {
                callback.onDeviceFound(result.getDevice().getAddress(), deviceHash, msgHash);
            }
        }
    }
}