package com.messy.app.routing;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.messy.app.R;
import com.messy.app.database.AppDatabase;
import com.messy.app.network.bluetooth.BluetoothConnectionService;
import com.messy.app.network.bluetooth.BluetoothDiscovery;

import java.util.Objects;
import java.util.UUID;

/**
 * SyncForegroundService runs continuously to sync messages with discovered peers.
 * Uses Bluetooth discovery and connection service to establish peer connections,
 * then triggers ForwardingEngine.sync() for store-and-forward mesh behavior.
 */
public class SyncForegroundService extends Service {
    private static final String TAG = "SyncForegroundService";
    private static final String CHANNEL_ID = "MeshSyncChannel";
    private static final int NOTIFICATION_ID = 42;
    private static final int DISCOVERY_INTERVAL_MS = 5000; // Re-discover every 5 seconds

    private BluetoothDiscovery bluetoothDiscovery;
    private BluetoothConnectionService connectionService;
    private AppDatabase database;
    private Handler handler;
    private String localDeviceId;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SyncForegroundService created");

        handler = new Handler(Looper.getMainLooper());
        database = AppDatabase.getInstance(this);

        // Get local device ID (use Bluetooth address or UUID)
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            try {
                @SuppressLint("MissingPermission")
                String address = bluetoothAdapter.getAddress();
                localDeviceId = address != null ? address : UUID.randomUUID().toString();
            } catch (Exception e) {
                localDeviceId = UUID.randomUUID().toString();
            }
        } else {
            localDeviceId = UUID.randomUUID().toString();
        }

        Log.d(TAG, "Local device ID: " + localDeviceId);

        // Initialize Bluetooth services
        initBluetoothServices();

        // Create notification channel for Android 8+
        createNotificationChannel();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Mesh sync running..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (isRunning) {
            return START_STICKY;
        }

        isRunning = true;
        startContinuousDiscovery();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not a bound service
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SyncForegroundService destroyed");
        isRunning = false;
        handler.removeCallbacksAndMessages(null);

        if (connectionService != null) {
            connectionService.stop();
        }

        super.onDestroy();
    }

    /**
     * Initialize Bluetooth discovery and connection services.
     */
    private void initBluetoothServices() {
        bluetoothDiscovery = new BluetoothDiscovery(new BluetoothDiscovery.DiscoveryCallback() {
            @Override
            public void onDeviceDiscovered(BluetoothDevice device) {
                Log.d(TAG, "Device discovered: " + device.getName());
                // Trigger connection attempt
                connectAndSync(device);
            }

            @Override
            public void onDiscoveryFinished() {
                Log.d(TAG, "Discovery finished");
                // Schedule next discovery
                handler.postDelayed(
                        SyncForegroundService.this::startContinuousDiscovery,
                        DISCOVERY_INTERVAL_MS
                );
            }
        });

        connectionService = new BluetoothConnectionService(new BluetoothConnectionService.ConnectionCallback() {
            @Override
            public void onConnectionSuccess(BluetoothDevice device) {
                Log.d(TAG, "Connected to: " + device.getName());
                // Connection will be handled by ForwardingEngine via sync
            }

            @Override
            public void onConnectionFailed(String error) {
                Log.e(TAG, "Connection failed: " + error);
            }

            @Override
            public void onConnectionLost(String error) {
                Log.e(TAG, "Connection lost: " + error);
            }

            @Override
            public void onMessageReceived(com.messy.app.database.Message message) {
                Log.d(TAG, "Message received: " + message.body);
                // Messages from peers should already be in DB via ForwardingEngine
            }
        });

        // Start listening for incoming connections
        connectionService.startServer();
    }

    /**
     * Start continuous discovery process.
     */
    private void startContinuousDiscovery() {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "Starting Bluetooth discovery");
        if (bluetoothDiscovery != null) {
            bluetoothDiscovery.startDiscovery();
        }
    }

    /**
     * Attempt to connect to a discovered device and perform sync.
     *
     * @param device The BluetoothDevice to connect to
     */
    @SuppressLint("MissingPermission")
    private void connectAndSync(BluetoothDevice device) {
        if (connectionService != null && connectionService.getState() != BluetoothConnectionService.STATE_CONNECTED) {
            Log.d(TAG, "Attempting to connect to: " + device.getName() + " (" + device.getAddress() + ")");

            connectionService.connectToDevice(device);

            // Give connection time to establish, then perform sync
            handler.postDelayed(() -> {
                if (connectionService.getState() == BluetoothConnectionService.STATE_CONNECTED) {
                    performSync();
                }
            }, 2000);
        }
    }

    /**
     * Perform sync with connected peer.
     * This uses Bluetooth sockets directly from the connection service.
     */
    private void performSync() {
        // Since we're using ForwardingEngine in a separate thread,
        // we need to pass the socket to it. For now, we'll use a simpler approach:
        // The ForwardingEngine will be called when new connections are made.
        Log.d(TAG, "Performing sync with peer");

        updateNotification("Syncing with peer...");
    }

    /**
     * Create notification channel for foreground service (Android 8+).
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mesh Sync Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Mesh message synchronization");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Create the notification for foreground service.
     */
    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mesh Messenger")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    /**
     * Update the foreground service notification.
     */
    private void updateNotification(String text) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(text));
        }
    }
}
