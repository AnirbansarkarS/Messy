package com.messy.app.routing;

import android.util.Log;

import com.messy.app.database.AppDatabase;
import com.messy.app.database.Message;
import com.messy.app.database.MessageDao;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ForwardingEngine handles store-and-forward logic for mesh networking.
 * On connection: exchange message IDs, then send full messages peer doesn't have.
 * TTL decrement on each hop; drop if TTL <= 0.
 * ACK creation when receiver == myId.
 */
public class ForwardingEngine {
    private static final String TAG = "ForwardingEngine";
    private static final int DEFAULT_TTL = 5;
    private static final int MIN_TTL = 1;

    /**
     * Sync wrapper for protocol messages. Allows us to send different types of data.
     */
    public static class SyncMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public String type; // "ID_LIST", "MESSAGE", "ACK_REQUEST"
        public List<String> messageIds; // For ID_LIST
        public Message message; // For MESSAGE
        public String ackFor; // For ACK acknowledgment

        public SyncMessage(String type) {
            this.type = type;
        }
    }

    /**
     * Perform bidirectional sync with a peer:
     * 1. Exchange message ID lists
     * 2. Send full messages peer doesn't have (with TTL check)
     * 3. Receive messages from peer and insert if new
     *
     * @param socket The connected socket to peer
     * @param myId The local device ID
     * @param database The local message database
     */
    public static void sync(Socket socket, String myId, AppDatabase database) {
        Log.d(TAG, "Starting sync with peer");

        ObjectOutputStream out = null;
        ObjectInputStream in = null;

        try {
            // Initialize streams - output first to avoid deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            MessageDao messageDao = database.messageDao();

            // Step 1: Get our message IDs and send them
            List<Message> ourMessages = messageDao.getAllMessages();
            List<String> ourIds = new ArrayList<>();
            for (Message msg : ourMessages) {
                ourIds.add(msg.id);
            }

            SyncMessage ourIdList = new SyncMessage("ID_LIST");
            ourIdList.messageIds = ourIds;
            out.writeObject(ourIdList);
            out.flush();
            Log.d(TAG, "Sent our ID list: " + ourIds.size() + " messages");

            // Step 2: Receive peer's message IDs
            SyncMessage peerIdListMsg = (SyncMessage) in.readObject();
            if (!"ID_LIST".equals(peerIdListMsg.type)) {
                Log.e(TAG, "Expected ID_LIST, got: " + peerIdListMsg.type);
                return;
            }

            List<String> peerIds = peerIdListMsg.messageIds;
            Log.d(TAG, "Received peer's ID list: " + peerIds.size() + " messages");

            // Step 3: Send messages peer doesn't have
            Set<String> peerIdSet = java.util.Collections.unmodifiableSet(
                    new java.util.HashSet<>(peerIds)
            );

            for (Message msg : ourMessages) {
                if (!peerIdSet.contains(msg.id)) {
                    // Peer doesn't have this message - send it
                    if (msg.ttl > 0) {
                        // Decrement TTL before forwarding
                        msg.ttl--;
                        if (msg.ttl <= 0) {
                            Log.d(TAG, "Message " + msg.id + " TTL expired, not forwarding");
                            continue;
                        }

                        SyncMessage messageToSend = new SyncMessage("MESSAGE");
                        messageToSend.message = msg;
                        out.writeObject(messageToSend);
                        out.flush();
                        Log.d(TAG, "Sent message: " + msg.id + ", TTL now: " + msg.ttl);
                    } else {
                        Log.d(TAG, "Message " + msg.id + " has no TTL, not forwarding");
                    }
                }
            }

            // Send end-of-messages marker
            SyncMessage endMarker = new SyncMessage("END");
            out.writeObject(endMarker);
            out.flush();
            Log.d(TAG, "Sent END marker");

            // Step 4: Receive messages from peer
            while (true) {
                SyncMessage incomingMsg = (SyncMessage) in.readObject();

                if ("END".equals(incomingMsg.type)) {
                    Log.d(TAG, "Received END marker");
                    break;
                }

                if ("MESSAGE".equals(incomingMsg.type)) {
                    Message receivedMsg = incomingMsg.message;

                    // Dedup: Check if we already have this message
                    Message existing = messageDao.findById(receivedMsg.id);
                    if (existing != null) {
                        Log.d(TAG, "Message " + receivedMsg.id + " already in DB, skipping");
                        continue;
                    }

                    // Check if message is for us (destination reached)
                    if (receivedMsg.receiver.equals(myId)) {
                        Log.d(TAG, "Message " + receivedMsg.id + " reached destination!");
                        receivedMsg.delivered = true;
                        messageDao.insert(receivedMsg);

                        // Create and queue ACK message
                        createAndQueueAck(receivedMsg.id, myId, messageDao);
                    } else if (receivedMsg.ttl > 0) {
                        // Forward to next hop: decrement TTL and insert
                        receivedMsg.ttl--;
                        Log.d(TAG, "Forwarding message " + receivedMsg.id + ", TTL now: " + receivedMsg.ttl);
                        messageDao.insert(receivedMsg);
                    } else {
                        Log.d(TAG, "Message " + receivedMsg.id + " TTL expired, dropping");
                    }
                }
            }

            Log.d(TAG, "Sync completed successfully");

        } catch (IOException e) {
            Log.e(TAG, "IO error during sync: " + e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Create a special ACK message and insert it into the database.
     * ACK messages will flood back across the mesh.
     *
     * @param originalMessageId The ID of the message being acknowledged
     * @param myId The local device ID (ACK sender)
     * @param messageDao DAO for database access
     */
    private static void createAndQueueAck(String originalMessageId, String myId, MessageDao messageDao) {
        Message ackMessage = new Message();
        ackMessage.id = "ACK_" + originalMessageId + "_" + System.currentTimeMillis();
        ackMessage.sender = myId;
        ackMessage.receiver = null; // ACK floods to all
        ackMessage.body = "ACK:" + originalMessageId;
        ackMessage.timestamp = System.currentTimeMillis();
        ackMessage.delivered = false;
        ackMessage.ttl = DEFAULT_TTL;

        messageDao.insert(ackMessage);
        Log.d(TAG, "Created ACK: " + ackMessage.id + " for original message: " + originalMessageId);
    }
}
