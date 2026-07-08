package com.messy.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.messy.app.chat.ChatActivity;
import com.messy.app.chat.ConversationAdapter;
import com.messy.app.chat.ConversationSummary;
import com.messy.app.database.AppDatabase;
import com.messy.app.database.Message;
import com.messy.app.database.MessageDao;
import com.messy.app.network.bluetooth.BluetoothConnectionService;
import com.messy.app.network.bluetooth.BluetoothDiscovery;
import com.messy.app.network.wifi.WifiDirectManager;
import com.messy.app.utils.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 101;

    private final ConversationAdapter conversationAdapter = new ConversationAdapter(this::openConversation);
    private MessageDao messageDao;
    private View emptyStateLayout;
    private RecyclerView conversationsRecyclerView;

    // P2P Fields
    private TextView p2pStatusTextView;
    private View p2pStatusIndicatorDot;
    private LinearLayout p2pDevicesListContainer;
    private Button p2pSendHelloButton;

    private BluetoothDiscovery bluetoothDiscovery;
    private BluetoothConnectionService connectionService;
    private WifiDirectManager wifiDirectManager;

    private BluetoothDevice connectedDevice;
    private final Set<String> discoveredAddresses = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageDao = AppDatabase.getInstance(this).messageDao();

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Conversations
        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView);
        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        conversationsRecyclerView.setAdapter(conversationAdapter);

        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        // FAB
        ExtendedFloatingActionButton fab = findViewById(R.id.openSavedMessagesButton);
        applyClickAnimation(fab, this::openSavedMessages);

        // P2P HUD Setup
        p2pStatusTextView = findViewById(R.id.p2pStatusTextView);
        p2pStatusIndicatorDot = findViewById(R.id.p2pStatusIndicatorDot);
        p2pDevicesListContainer = findViewById(R.id.p2pDevicesListContainer);
        p2pSendHelloButton = findViewById(R.id.p2pSendHelloButton);

        applyClickAnimation(findViewById(R.id.p2pPermsButton), this::checkAndRequestPermissions);
        applyClickAnimation(findViewById(R.id.p2pStartBtButton), this::startBluetoothWork);
        applyClickAnimation(findViewById(R.id.p2pStartWifiButton), this::startWifiDirectWork);
        applyClickAnimation(p2pSendHelloButton, this::sendTestHello);

        // Start glowing LED pulse animation
        if (p2pStatusIndicatorDot != null) {
            android.view.animation.AlphaAnimation pulse = new android.view.animation.AlphaAnimation(0.4f, 1.0f);
            pulse.setDuration(1200);
            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
            p2pStatusIndicatorDot.startAnimation(pulse);
        }

        // Initialize Services
        initP2pServices();
    }

    private void initP2pServices() {
        // Bluetooth Connection Service
        connectionService = new BluetoothConnectionService(new BluetoothConnectionService.ConnectionCallback() {
            @Override
            public void onConnectionSuccess(BluetoothDevice device) {
                connectedDevice = device;
                AppExecutors.postToMain(() -> {
                    @SuppressLint("MissingPermission")
                    String deviceName = device.getName() != null ? device.getName() : device.getAddress();
                    updateP2pStatus("Connected to: " + deviceName);
                    p2pSendHelloButton.setEnabled(true);
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                AppExecutors.postToMain(() -> {
                    updateP2pStatus("Connection failed: " + error);
                    p2pSendHelloButton.setEnabled(false);
                    connectedDevice = null;
                });
            }

            @Override
            public void onConnectionLost(String error) {
                AppExecutors.postToMain(() -> {
                    updateP2pStatus("Connection lost: " + error + ". Listening...");
                    p2pSendHelloButton.setEnabled(false);
                    connectedDevice = null;
                });
            }

            @Override
            public void onMessageReceived(Message message) {
                Log.d("P2PVerify", "Phone B received message body: " + message.body);
                AppExecutors.postToMain(() -> {
                    Toast.makeText(MainActivity.this, "Received Message: " + message.body, Toast.LENGTH_LONG).show();
                });
                
                // Save message in local db so it appears dynamically in the UI chat thread
                AppExecutors.execute(() -> {
                    messageDao.insert(message);
                    AppExecutors.postToMain(MainActivity.this::loadConversations);
                });
            }
        });

        // Bluetooth Discovery Handler
        bluetoothDiscovery = new BluetoothDiscovery(this, new BluetoothDiscovery.DiscoveryCallback() {
            @Override
            public void onDeviceFound(BluetoothDevice device) {
                @SuppressLint("MissingPermission")
                String address = device.getAddress();
                if (discoveredAddresses.contains(address)) {
                    return;
                }
                discoveredAddresses.add(address);

                @SuppressLint("MissingPermission")
                String name = device.getName() != null ? device.getName() : "Unknown Device";
                final String displayName = name + "\n(" + address + ")";
                
                AppExecutors.postToMain(() -> {
                    Button btn = new Button(MainActivity.this);
                    btn.setText(displayName);
                    btn.setAllCaps(false);
                    btn.setOnClickListener(v -> {
                        updateP2pStatus("Connecting to " + name + "...");
                        connectionService.connectToDevice(device);
                    });
                    p2pDevicesListContainer.addView(btn);
                });
            }

            @Override
            public void onDiscoveryStarted() {
                AppExecutors.postToMain(() -> {
                    updateP2pStatus("Bluetooth Scanning & Listening...");
                    p2pDevicesListContainer.removeAllViews();
                    discoveredAddresses.clear();
                });
            }

            @Override
            public void onDiscoveryFinished() {
                AppExecutors.postToMain(() -> {
                    if (connectionService.getState() != BluetoothConnectionService.STATE_CONNECTED) {
                        updateP2pStatus("Bluetooth Scan finished. Listening...");
                    }
                });
            }
        });

        // Wi-Fi Direct Manager
        wifiDirectManager = new WifiDirectManager(this, new WifiDirectManager.WifiDirectCallback() {
            @Override
            public void onPeersDiscovered(List<WifiP2pDevice> peers) {
                AppExecutors.postToMain(() -> {
                    updateP2pStatus("Wi-Fi Direct scan complete. Found " + peers.size() + " peers.");
                });
            }

            @Override
            public void onP2pEnabled(boolean enabled) {
                Log.d(TAG, "Wi-Fi P2P Enabled: " + enabled);
            }
        });
    }

    private void applyClickAnimation(View view, Runnable action) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                if (action != null) {
                    action.run();
                }
            }).start();
        });
    }

    private void updateP2pStatus(String status) {
        if (p2pStatusTextView != null) {
            p2pStatusTextView.setText("Status: " + status);
        }
        if (p2pStatusIndicatorDot != null) {
            int color;
            String lower = status.toLowerCase();
            if (lower.contains("connected")) {
                color = android.graphics.Color.parseColor("#00F5D4"); // Neon cyan for active connection
            } else if (lower.contains("scanning") || lower.contains("scan") || lower.contains("listening")) {
                color = android.graphics.Color.parseColor("#8E2DE2"); // Neon violet for scanning/searching
            } else if (lower.contains("failed") || lower.contains("lost") || lower.contains("error")) {
                color = android.graphics.Color.parseColor("#FF007F"); // Hot pink for error states
            } else {
                color = android.graphics.Color.parseColor("#FF9F43"); // Orange for idle or initial
            }
            android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) p2pStatusIndicatorDot.getBackground();
            if (bg != null) {
                bg.setColor(color);
            }
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        List<String> needed = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODES());
        } else {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private int PERMISSION_REQUEST_CODES() {
        return PERMISSION_REQUEST_CODE;
    }

    private void startBluetoothWork() {
        Log.d(TAG, "Starting Bluetooth advertising (server) and discovery (client)");
        // 1. Start Server Socket listener
        connectionService.startServer();
        // 2. Start Discovery scan
        bluetoothDiscovery.startDiscovery();
        
        updateP2pStatus("Starting Bluetooth Server & Scanning...");
    }

    private void startWifiDirectWork() {
        Log.d(TAG, "Starting Wi-Fi Direct Peer Discovery");
        wifiDirectManager.discoverPeers();
        updateP2pStatus("Scanning for Wi-Fi Direct peers...");
    }

    private void sendTestHello() {
        if (connectionService.getState() != BluetoothConnectionService.STATE_CONNECTED) {
            Toast.makeText(this, "Not connected to any device!", Toast.LENGTH_SHORT).show();
            return;
        }

        @SuppressLint("MissingPermission")
        String receiverName = connectedDevice != null && connectedDevice.getName() != null 
                ? connectedDevice.getName() : "P2P Partner";

        Message message = new Message();
        message.id = UUID.randomUUID().toString();
        message.sender = ChatActivity.SELF_ID; // "me"
        message.receiver = receiverName;
        message.body = "hello";
        message.timestamp = System.currentTimeMillis();
        message.delivered = true;
        message.ttl = 0;

        boolean success = connectionService.write(message);
        if (success) {
            Toast.makeText(this, "Sent: hello", Toast.LENGTH_SHORT).show();
            // Store locally so it displays in chat summary/messages list
            AppExecutors.execute(() -> {
                messageDao.insert(message);
                AppExecutors.postToMain(this::loadConversations);
            });
        } else {
            Toast.makeText(this, "Failed to send message over Socket", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
        if (wifiDirectManager != null) {
            wifiDirectManager.registerReceiver();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wifiDirectManager != null) {
            wifiDirectManager.unregisterReceiver();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothDiscovery != null) {
            bluetoothDiscovery.cleanup();
        }
        if (connectionService != null) {
            connectionService.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODES()) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted! You can now start BT/Wi-Fi", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions are required for BT and Wi-Fi direct scan", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void loadConversations() {
        AppExecutors.execute(() -> {
            List<Message> messages = messageDao.getAllMessages();
            List<ConversationSummary> conversations = buildConversationSummaries(messages);
            AppExecutors.postToMain(() -> {
                conversationAdapter.setConversations(conversations);
                boolean empty = conversations.isEmpty();
                emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
                conversationsRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private List<ConversationSummary> buildConversationSummaries(List<Message> messages) {
        Map<String, ConversationSummaryBuilder> builders = new LinkedHashMap<>();
        for (Message message : messages) {
            String contactId = resolveContactId(message);
            String contactName = resolveContactName(message, contactId);
            ConversationSummaryBuilder builder = builders.get(contactId);
            if (builder == null) {
                builder = new ConversationSummaryBuilder(contactId, contactName);
                builders.put(contactId, builder);
            }
            builder.update(message.body, message.timestamp);
        }

        List<ConversationSummary> summaries = new ArrayList<>();
        for (ConversationSummaryBuilder builder : builders.values()) {
            summaries.add(builder.build());
        }
        summaries.sort((left, right) -> Long.compare(right.lastTimestamp, left.lastTimestamp));
        return summaries;
    }

    private String resolveContactId(Message message) {
        if (ChatActivity.SELF_ID.equals(message.sender) && ChatActivity.SELF_ID.equals(message.receiver)) {
            return ChatActivity.SELF_ID;
        }
        if (ChatActivity.SELF_ID.equals(message.sender)) {
            return message.receiver;
        }
        return message.sender;
    }

    private String resolveContactName(Message message, String contactId) {
        if (ChatActivity.SELF_ID.equals(contactId)) {
            return getString(R.string.saved_messages);
        }
        if (ChatActivity.SELF_ID.equals(message.sender)) {
            return message.receiver;
        }
        return message.sender;
    }

    private void openConversation(ConversationSummary conversation) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, conversation.contactId);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, conversation.contactName);
        startActivity(intent);
    }

    private void openSavedMessages() {
        openConversation(new ConversationSummary(
                ChatActivity.SELF_ID,
                getString(R.string.saved_messages),
                "",
                System.currentTimeMillis()
        ));
    }

    private static final class ConversationSummaryBuilder {
        private final String contactId;
        private final String contactName;
        private String lastMessage = "";
        private long lastTimestamp;

        private ConversationSummaryBuilder(String contactId, String contactName) {
            this.contactId = contactId;
            this.contactName = contactName;
        }

        private void update(String message, long timestamp) {
            if (timestamp >= lastTimestamp) {
                lastMessage = message;
                lastTimestamp = timestamp;
            }
        }

        private ConversationSummary build() {
            return new ConversationSummary(contactId, contactName, lastMessage, lastTimestamp);
        }
    }
}