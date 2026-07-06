package com.messy.app.network.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BluetoothDiscovery {
    private static final String TAG = "BluetoothDiscovery";

    public interface DiscoveryCallback {
        void onDeviceFound(BluetoothDevice device);
        void onDiscoveryStarted();
        void onDiscoveryFinished();
    }

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final DiscoveryCallback callback;
    private boolean isReceiverRegistered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    try {
                        @SuppressLint("MissingPermission")
                        String name = device.getName();
                        String address = device.getAddress();
                        Log.d(TAG, "Device found: " + name + " [" + address + "]");
                        if (callback != null) {
                            callback.onDeviceFound(device);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permissions missing for Bluetooth name/address access", e);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "Discovery started");
                if (callback != null) {
                    callback.onDiscoveryStarted();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Discovery finished");
                if (callback != null) {
                    callback.onDiscoveryFinished();
                }
            }
        }
    };

    public BluetoothDiscovery(Context context, DiscoveryCallback callback) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is disabled");
            return;
        }

        stopDiscovery(); // Cancel existing discovery if running

        // Register for discovery status events and found devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        
        context.registerReceiver(receiver, filter);
        isReceiverRegistered = true;

        try {
            boolean success = bluetoothAdapter.startDiscovery();
            Log.d(TAG, "startDiscovery triggered: " + success);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Cannot start discovery due to missing permissions", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (bluetoothAdapter != null) {
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                    Log.d(TAG, "Cancelled ongoing bluetooth discovery");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Exception cancelling discovery", e);
            }
        }
        cleanup();
    }

    public void cleanup() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
                Log.d(TAG, "Receiver unregistered successfully");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was already unregistered", e);
            }
            isReceiverRegistered = false;
        }
    }
}
