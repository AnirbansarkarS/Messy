package com.messy.app.network.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.messy.app.database.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "BTConnectionService";
    private static final String APP_NAME = "MessyApp";
    
    // A standard UUID for SPP (Serial Port Profile) or P2P data exchange
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public interface ConnectionCallback {
        void onConnectionSuccess(BluetoothDevice device);
        void onConnectionFailed(String error);
        void onConnectionLost(String error);
        void onMessageReceived(Message message);
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final ConnectionCallback callback;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private int state;
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothConnectionService(ConnectionCallback callback) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
        this.state = STATE_NONE;
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.state + " -> " + state);
        this.state = state;
    }

    public synchronized int getState() {
        return state;
    }

    /**
     * Start the connection service. Specifically start AcceptThread to begin a session
     * in listening (server) mode.
     */
    public synchronized void startServer() {
        Log.d(TAG, "startServer");

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect to
     */
    public synchronized void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "connectToDevice: " + device);

        // Cancel any thread trying to connect
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection.
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);

        if (callback != null) {
            callback.onConnectionSuccess(device);
        }
    }

    /**
     * Stop all threads.
     */
    public synchronized void stop() {
        Log.d(TAG, "stop service");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner.
     *
     * @param message The Message object to send
     * @return boolean Representing if the write was successful
     */
    public boolean write(Message message) {
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread to prevent race condition
        synchronized (this) {
            if (state != STATE_CONNECTED) return false;
            r = connectedThread;
        }
        // Perform the write
        return r.write(message);
    }

    private void connectionFailed(String error) {
        Log.e(TAG, "Connection failed: " + error);
        if (callback != null) {
            callback.onConnectionFailed(error);
        }
        // Restart server mode / listen to allow reconnecting
        BluetoothConnectionService.this.startServer();
    }

    private void connectionLost(String error) {
        Log.e(TAG, "Connection lost: " + error);
        if (callback != null) {
            callback.onConnectionLost(error);
        }
        // Restart server mode / listen to allow reconnecting
        BluetoothConnectionService.this.startServer();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket listenUsingRfcommWithServiceRecord failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "AcceptThread run");
            setName("AcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (state != STATE_CONNECTED) {
                if (serverSocket == null) {
                    Log.e(TAG, "serverSocket is null, exiting AcceptThread");
                    break;
                }
                try {
                    Log.d(TAG, "AcceptThread: waiting for connection...");
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothConnectionService.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.d(TAG, "AcceptThread ending");
        }

        public void cancel() {
            Log.d(TAG, "AcceptThread cancel");
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "serverSocket close() failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            socket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            Log.i(TAG, "ConnectThread started");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(TAG, "ConnectThread: connecting to socket...");
                socket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    socket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed(e.getMessage());
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnectionService.this) {
                connectThread = null;
            }

            // Start the connected thread
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "socket close() failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private ObjectOutputStream objectOutputStream;
        private ObjectInputStream objectInputStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread creating");
            this.socket = socket;

            try {
                // IMPORTANT: Construct ObjectOutputStream first and flush to resolve deadlock
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectOutputStream.flush();
                objectInputStream = new ObjectInputStream(socket.getInputStream());
                Log.d(TAG, "Streams created successfully");
            } catch (IOException e) {
                Log.e(TAG, "Streams creation failed", e);
            }
        }

        public void run() {
            Log.i(TAG, "ConnectedThread running");
            setName("ConnectedThread");

            if (objectInputStream == null) {
                connectionLost("Input stream is null");
                return;
            }

            // Keep listening to the InputStream while connected
            while (state == STATE_CONNECTED) {
                try {
                    // Read the serialized Message object
                    Message message = (Message) objectInputStream.readObject();
                    Log.d(TAG, "Received message: " + message.body + " from " + message.sender);
                    if (callback != null) {
                        callback.onMessageReceived(message);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Connection read error, connection lost", e);
                    connectionLost(e.getMessage());
                    break;
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "Class not found during deserialization", e);
                }
            }
            Log.d(TAG, "ConnectedThread ending");
        }

        /**
         * Write message to the remote device stream.
         *
         * @param message The Message object to write
         * @return boolean representing operation status
         */
        public boolean write(Message message) {
            if (objectOutputStream == null) {
                Log.e(TAG, "Output stream is null, cannot write");
                return false;
            }
            try {
                objectOutputStream.writeObject(message);
                objectOutputStream.flush();
                Log.d(TAG, "Wrote message successfully: " + message.body);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
                return false;
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "socket close() failed", e);
            }
        }
    }
}
