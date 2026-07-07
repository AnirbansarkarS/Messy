# Phase 3 Code Examples
## Copy-Paste Ready Integration Code

---

## 1. Initialize SyncForegroundService (in MainActivity.onCreate)

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    messageDao = AppDatabase.getInstance(this).messageDao();

    // ... existing code ...

    // START MESH SYNC SERVICE
    startMeshSync();
}

private void startMeshSync() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent syncIntent = new Intent(this, SyncForegroundService.class);
        startForegroundService(syncIntent);
    } else {
        startService(new Intent(this, SyncForegroundService.class));
    }
    Log.d(TAG, "Mesh sync service started");
}

@Override
protected void onDestroy() {
    super.onDestroy();
    // Optional: Stop mesh service when leaving app
    // stopService(new Intent(this, SyncForegroundService.class));
}
```

---

## 2. Initialize SyncManager (in MainActivity)

```java
public class MainActivity extends AppCompatActivity 
    implements SyncManager.SyncCallback {
    
    private SyncManager syncManager;
    private AppDatabase database;
    private MessageDao messageDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getInstance(this);
        messageDao = database.messageDao();

        // Initialize SyncManager
        syncManager = new SyncManager(this, database, this);

        // ... rest of onCreate ...
    }

    // Implement SyncCallback interface
    @Override
    public void onSyncStarted(String peerId) {
        Log.d(TAG, "Sync started with: " + peerId);
        // Show UI indicator
        updateSyncStatus("Syncing with " + peerId);
    }

    @Override
    public void onSyncCompleted(String peerId) {
        Log.d(TAG, "Sync completed with: " + peerId);
        // Refresh conversations
        loadAndDisplayConversations();
        updateSyncStatus("Ready");
    }

    @Override
    public void onMessageForwarded(Message message) {
        Log.d(TAG, "Message forwarded: " + message.id);
        // Update UI: show message hopping through network
        refreshConversationUI(message.receiver);
    }

    @Override
    public void onAckReceived(String messageId) {
        Log.d(TAG, "Delivery confirmed for: " + messageId);
        // Update message status to "Delivered"
        refreshConversationUI(messageId);
    }

    private void updateSyncStatus(String status) {
        // Update status bar/TextView
        TextView statusView = findViewById(R.id.syncStatusTextView);
        if (statusView != null) {
            statusView.setText("Mesh: " + status);
        }
    }
}
```

---

## 3. Send Message Through Mesh

```java
// In ChatActivity or where you send messages

private void sendMessageThroughMesh(String receiverId, String content) {
    AppExecutors.getInstance().diskIO().execute(() -> {
        Message message = syncManager.createMessage(receiverId, content);
        
        runOnUiThread(() -> {
            // Add to chat UI immediately (optimistic)
            conversationAdapter.addMessage(message);
            conversationRecyclerView.scrollToPosition(conversationAdapter.getItemCount() - 1);
            
            // Clear input
            messageEditText.setText("");
            
            Log.d(TAG, "Message sent: " + message.id + " to: " + receiverId);
            Toast.makeText(this, "Message on mesh...", Toast.LENGTH_SHORT).show();
        });
    });
}

// In onCreate or onViewCreated
sendButton.setOnClickListener(v -> {
    String content = messageEditText.getText().toString().trim();
    if (!content.isEmpty() && receiverId != null) {
        sendMessageThroughMesh(receiverId, content);
    }
});
```

---

## 4. Receive Messages from Peers

```java
// Update BluetoothConnectionService callback in MainActivity

private void initBluetoothServices() {
    bluetoothConnectionService = new BluetoothConnectionService(
        new BluetoothConnectionService.ConnectionCallback() {
            
            @Override
            public void onConnectionSuccess(BluetoothDevice device) {
                Log.d(TAG, "Connected to: " + device.getName());
                // Sync will happen automatically via ForwardingEngine
            }

            @Override
            public void onMessageReceived(Message message) {
                Log.d(TAG, "Received message: " + message.id);
                
                if (syncManager == null) {
                    Log.e(TAG, "SyncManager not initialized");
                    return;
                }
                
                // Process through mesh routing
                boolean processed = syncManager.processReceivedMessage(message);
                
                if (processed) {
                    // Message was routed, refresh UI
                    refres
hConversationUI();
                    
                    // If delivered to us, show notification
                    if (message.receiver.equals(syncManager.getLocalDeviceId())) {
                        showMessageNotification(message);
                    }
                }
            }

            @Override
            public void onConnectionFailed(String error) {
                Log.e(TAG, "Connection failed: " + error);
                Toast.makeText(MainActivity.this, 
                    "Connection failed: " + error, 
                    Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConnectionLost(String error) {
                Log.e(TAG, "Connection lost: " + error);
            }
        }
    );
    
    bluetoothConnectionService.startServer();
}

private void showMessageNotification(Message message) {
    NotificationManager notificationManager = 
        getSystemService(NotificationManager.class);
    
    NotificationCompat.Builder builder = 
        new NotificationCompat.Builder(this, "MeshMessagesChannel")
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle("New Message")
            .setContentText(message.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true);
    
    if (notificationManager != null) {
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
```

---

## 5. Display Mesh Status UI

```java
// Add to activity_main layout:
<TextView
    android:id="@+id/syncStatusTextView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="8dp"
    android:text="Mesh: Ready"
    android:textSize="12sp"
    android:textColor="@android:color/darker_gray"
    android:background="#F0F0F0" />

// In MainActivity to keep status updated:
private void loadAndDisplayConversations() {
    AppExecutors.getInstance().diskIO().execute(() -> {
        List<Message> messages = messageDao.getAllMessages();
        
        runOnUiThread(() -> {
            // Convert messages to conversation summaries
            Map<String, ConversationSummary> conversations = new LinkedHashMap<>();
            
            for (Message msg : messages) {
                String contactId = msg.sender.equals(syncManager.getLocalDeviceId()) 
                    ? msg.receiver 
                    : msg.sender;
                
                conversations.putIfAbsent(contactId, 
                    new ConversationSummary(contactId, msg.body, msg.timestamp, msg.delivered));
            }
            
            // Update adapter
            conversationAdapter.submitList(new ArrayList<>(conversations.values()));
            
            // Update empty state
            emptyStateLayout.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
        });
    });
}
```

---

## 6. Get Device ID for UI Display

```java
public class MainActivity extends AppCompatActivity {
    
    private String getMyDeviceId() {
        if (syncManager != null) {
            return syncManager.getLocalDeviceId();
        }
        
        // Fallback
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try {
                return adapter.getAddress();
            } catch (Exception e) {
                return UUID.randomUUID().toString();
            }
        }
        
        return "unknown";
    }
    
    // In onCreate:
    TextView deviceIdView = findViewById(R.id.deviceIdTextView);
    if (deviceIdView != null) {
        deviceIdView.setText("My ID: " + getMyDeviceId());
    }
}
```

---

## 7. Testing: Manually Trigger Sync

```java
// Add debug button in MainActivity for testing
Button debugSyncButton = findViewById(R.id.debugSyncButton);
debugSyncButton.setOnClickListener(v -> {
    testMeshForwarding();
});

private void testMeshForwarding() {
    AppExecutors.getInstance().diskIO().execute(() -> {
        String myId = syncManager.getLocalDeviceId();
        String testReceiverId = "AA:BB:CC:DD:EE:02"; // Phone C's MAC
        
        // Create test message
        Message testMsg = syncManager.createMessage(testReceiverId, 
            "Test mesh message at " + System.currentTimeMillis());
        
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, 
                "Test message created: " + testMsg.id, 
                Toast.LENGTH_LONG).show();
            
            Log.d(TAG, "Created test message:");
            Log.d(TAG, "  ID: " + testMsg.id);
            Log.d(TAG, "  From: " + testMsg.sender);
            Log.d(TAG, "  To: " + testMsg.receiver);
            Log.d(TAG, "  TTL: " + testMsg.ttl);
            Log.d(TAG, "  Timestamp: " + testMsg.timestamp);
        });
    });
}
```

---

## 8. Debug: View All Messages in Database

```java
// Add to MainActivity for debugging
Button debugDbButton = findViewById(R.id.debugDbButton);
debugDbButton.setOnClickListener(v -> {
    dumpDatabase();
});

private void dumpDatabase() {
    AppExecutors.getInstance().diskIO().execute(() -> {
        List<Message> allMessages = messageDao.getAllMessages();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Database Dump ===\n");
        sb.append("Total messages: ").append(allMessages.size()).append("\n\n");
        
        for (Message msg : allMessages) {
            sb.append("ID: ").append(msg.id).append("\n");
            sb.append("  From: ").append(msg.sender).append("\n");
            sb.append("  To: ").append(msg.receiver).append("\n");
            sb.append("  Body: ").append(msg.body).append("\n");
            sb.append("  TTL: ").append(msg.ttl).append("\n");
            sb.append("  Delivered: ").append(msg.delivered).append("\n");
            sb.append("  Timestamp: ").append(msg.timestamp).append("\n");
            sb.append("\n");
        }
        
        runOnUiThread(() -> {
            // Show in dialog
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Database Contents")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
            
            Log.d(TAG, sb.toString());
        });
    });
}
```

---

## 9. Example: Custom Conversation Adapter

```java
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {
    
    public class VH extends RecyclerView.ViewHolder {
        TextView contactView, previewView, timeView, statusView;
        
        public VH(@NonNull View itemView) {
            super(itemView);
            contactView = itemView.findViewById(R.id.contactName);
            previewView = itemView.findViewById(R.id.messagePreview);
            timeView = itemView.findViewById(R.id.timestamp);
            statusView = itemView.findViewById(R.id.deliveryStatus);
        }
        
        public void bind(ConversationSummary conv) {
            contactView.setText("Chat: " + conv.getContactId());
            previewView.setText(conv.getLastMessage());
            timeView.setText(formatTime(conv.getTimestamp()));
            
            // Show delivery status
            if (conv.isDelivered()) {
                statusView.setText("✓✓ Delivered");
                statusView.setTextColor(Color.GREEN);
            } else {
                statusView.setText("• Pending");
                statusView.setTextColor(Color.GRAY);
            }
            
            itemView.setOnClickListener(v -> {
                if (onConversationClick != null) {
                    onConversationClick.accept(conv.getContactId());
                }
            });
        }
    }
    
    private String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(timestamp));
    }
}
```

---

## 10. AndroidManifest.xml: Internet & Foreground Service

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Messy">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".routing.SyncForegroundService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

    </application>

</manifest>
```

---

## Quick Integration Checklist

- [ ] Copy ForwardingEngine.java to app/routing/
- [ ] Copy SyncForegroundService.java to app/routing/
- [ ] Copy SyncManager.java to app/routing/
- [ ] Add FOREGROUND_SERVICE permission to AndroidManifest.xml
- [ ] Add SyncForegroundService declaration to AndroidManifest.xml
- [ ] Initialize SyncForegroundService in MainActivity.onCreate()
- [ ] Initialize SyncManager in MainActivity
- [ ] Implement SyncManager.SyncCallback in MainActivity
- [ ] Update BluetoothConnectionService callback to use syncManager.processReceivedMessage()
- [ ] Test with 3 phones

---

## Common Integration Mistakes

❌ **Wrong:** Not importing SyncManager/ForwardingEngine
```java
// Missing import - will cause compilation error
```

✅ **Right:** Import the correct classes
```java
import com.messy.app.routing.SyncManager;
import com.messy.app.routing.ForwardingEngine;
import com.messy.app.routing.SyncForegroundService;
```

---

❌ **Wrong:** Starting service without permissions
```java
// Will crash if FOREGROUND_SERVICE permission missing
startForegroundService(syncIntent);
```

✅ **Right:** Use Build.VERSION check
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    startForegroundService(syncIntent);  // API 26+
} else {
    startService(syncIntent);  // Older APIs
}
```

---

❌ **Wrong:** Never processing received messages
```java
@Override
public void onMessageReceived(Message message) {
    // Just log - message never gets routed!
    Log.d("Tag", "Got message");
}
```

✅ **Right:** Always call syncManager
```java
@Override
public void onMessageReceived(Message message) {
    if (syncManager.processReceivedMessage(message)) {
        updateUI();
    }
}
```

---

Now you're ready to integrate Phase 3! 🚀
