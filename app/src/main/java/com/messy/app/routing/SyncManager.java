package com.messy.app.routing;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.util.Log;

import com.messy.app.database.AppDatabase;
import com.messy.app.database.Message;
import com.messy.app.database.MessageDao;
import com.messy.app.network.bluetooth.BluetoothConnectionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SyncManager coordinates mesh message synchronization across connected peers.
 * 
 * It integrates with BluetoothConnectionService to:
 * - Track connected peers
 * - Exchange message IDs with peers
 * - Forward messages through the mesh
 * - Handle TTL decrement and ACK messages
 * 
 * This manager should be instantiated by MainActivity or the SyncForegroundService.
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final int DEFAULT_TTL = 5;

    private final Context context;
    private final AppDatabase database;
    private final MessageDao messageDao;
    private final String localDeviceId;
    private final Set<String> pendingAcks;
    private SyncCallback callback;

    public interface SyncCallback {
        void onSyncStarted(String peerId);
        void onSyncCompleted(String peerId);
        void onMessageForwarded(Message message);
        void onAckReceived(String messageId);
    }

    /**
     * Initialize the SyncManager.
     *
     * @param context Android context
     * @param database AppDatabase instance
     * @param callback Callback for sync events
     */
    public SyncManager(Context context, AppDatabase database, SyncCallback callback) {
        this.context = context;
        this.database = database;
        this.messageDao = database.messageDao();
        this.callback = callback;
        this.pendingAcks = new HashSet<>();
        
        // Get local device ID
        String id;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            try {
                @SuppressLint("MissingPermission")
                String address = bluetoothAdapter.getAddress();
                id = address != null ? address : UUID.randomUUID().toString();
            } catch (Exception e) {
                id = UUID.randomUUID().toString();
            }
        } else {
            id = UUID.randomUUID().toString();
        }
        this.localDeviceId = id;

        Log.d(TAG, "SyncManager initialized with local ID: " + this.localDeviceId);
    }

    /**
     * Get the local device ID.
     */
    public String getLocalDeviceId() {
        return localDeviceId;
    }

    /**
     * Create a new message to be sent through the mesh.
     *
     * @param receiver The intended receiver's device ID
     * @param body The message content
     * @return Message object ready to be sent
     */
    public Message createMessage(String receiver, String body) {
        Message message = new Message();
        message.id = UUID.randomUUID().toString();
        message.sender = localDeviceId;
        message.receiver = receiver;
        message.body = body;
        message.timestamp = System.currentTimeMillis();
        message.delivered = false;
        message.ttl = DEFAULT_TTL;

        messageDao.insert(message);
        Log.d(TAG, "Created message: " + message.id + " to: " + receiver);
        return message;
    }

    /**
     * Process a received message for routing.
     * Handles: dedup, delivery confirmation, ACK creation, TTL checking.
     *
     * @param message The received message
     * @return true if message was processed, false if dropped/deduped
     */
    public boolean processReceivedMessage(Message message) {
        Log.d(TAG, "Processing received message: " + message.id);

        // Dedup check
        Message existing = messageDao.findById(message.id);
        if (existing != null) {
            Log.d(TAG, "Message " + message.id + " already in DB (dedup), skipping");
            return false;
        }

        // Check if it's an ACK
        if (message.body != null && message.body.startsWith("ACK:")) {
            handleAckMessage(message);
            return true;
        }

        // Check if message is for us
        if (message.receiver != null && message.receiver.equals(localDeviceId)) {
            Log.d(TAG, "Message " + message.id + " reached destination!");
            message.delivered = true;
            messageDao.insert(message);

            if (callback != null) {
                callback.onMessageForwarded(message);
            }

            // Create ACK
            createAndQueueAck(message.id);
            return true;
        }

        // Check TTL before forwarding
        if (message.ttl <= 0) {
            Log.d(TAG, "Message " + message.id + " TTL expired, dropping");
            return false;
        }

        // Forward: decrement TTL and store
        message.ttl--;
        messageDao.insert(message);
        Log.d(TAG, "Forwarding message " + message.id + ", TTL now: " + message.ttl);

        if (callback != null) {
            callback.onMessageForwarded(message);
        }

        return true;
    }

    /**
     * Handle an ACK message.
     *
     * @param ackMessage The ACK message received
     */
    private void handleAckMessage(Message ackMessage) {
        // Extract original message ID from ACK
        String[] parts = ackMessage.body.split(":");
        if (parts.length >= 2) {
            String originalMsgId = parts[1];
            Log.d(TAG, "Received ACK for message: " + originalMsgId);

            // Mark as delivered if we created it
            Message original = messageDao.findById(originalMsgId);
            if (original != null && original.sender.equals(localDeviceId)) {
                original.delivered = true;
                messageDao.update(original);
                Log.d(TAG, "Message " + originalMsgId + " marked as delivered");

                if (callback != null) {
                    callback.onAckReceived(originalMsgId);
                }
            } else {
                // Not ours, but forward it anyway with TTL
                if (ackMessage.ttl > 0) {
                    ackMessage.ttl--;
                    messageDao.insert(ackMessage);
                    Log.d(TAG, "Forwarding ACK, TTL now: " + ackMessage.ttl);
                }
            }
        }
    }

    /**
     * Create an ACK message for a received message.
     *
     * @param originalMessageId The ID of the message being acknowledged
     */
    private void createAndQueueAck(String originalMessageId) {
        if (pendingAcks.contains(originalMessageId)) {
            return; // Already created ACK for this message
        }

        Message ackMessage = new Message();
        ackMessage.id = "ACK_" + originalMessageId + "_" + System.currentTimeMillis();
        ackMessage.sender = localDeviceId;
        ackMessage.receiver = null; // Floods
        ackMessage.body = "ACK:" + originalMessageId;
        ackMessage.timestamp = System.currentTimeMillis();
        ackMessage.delivered = false;
        ackMessage.ttl = DEFAULT_TTL;

        messageDao.insert(ackMessage);
        pendingAcks.add(originalMessageId);
        Log.d(TAG, "Created ACK: " + ackMessage.id);
    }

    /**
     * Get all messages we should forward to a peer.
     * Returns messages the peer doesn't have.
     *
     * @param peerMessageIds Set of message IDs the peer has
     * @return List of messages to forward
     */
    public List<Message> getMessagesToForward(Set<String> peerMessageIds) {
        List<Message> messagesToForward = new ArrayList<>();
        List<Message> allMessages = messageDao.getAllMessages();

        for (Message msg : allMessages) {
            if (!peerMessageIds.contains(msg.id) && msg.ttl > 0) {
                messagesToForward.add(msg);
            }
        }

        return messagesToForward;
    }

    /**
     * Get list of all message IDs in local database.
     *
     * @return List of message IDs
     */
    public List<String> getLocalMessageIds() {
        List<Message> messages = messageDao.getAllMessages();
        List<String> ids = new ArrayList<>();
        for (Message msg : messages) {
            ids.add(msg.id);
        }
        return ids;
    }
}
