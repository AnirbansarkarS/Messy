package com.messy.app.network.wifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WifiDirectManager {
    private static final String TAG = "WifiDirectManager";

    public interface WifiDirectCallback {
        void onPeersDiscovered(List<WifiP2pDevice> peers);
        void onP2pEnabled(boolean enabled);
    }

    private final Context context;
    private final WifiP2pManager wifiP2pManager;
    private final WifiP2pManager.Channel channel;
    private final WifiDirectCallback callback;
    private final List<WifiP2pDevice> peerList = new ArrayList<>();
    
    private boolean isReceiverRegistered = false;

    private final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            Collection<WifiP2pDevice> refreshedPeers = peers.getDeviceList();
            if (!refreshedPeers.equals(peerList)) {
                peerList.clear();
                peerList.addAll(refreshedPeers);
                
                Log.d(TAG, "Discovered Wi-Fi Direct Peers count: " + peerList.size());
                for (WifiP2pDevice device : peerList) {
                    Log.d(TAG, "Wi-Fi Direct Peer: " + device.deviceName + " [" + device.deviceAddress + "]");
                }
                
                if (callback != null) {
                    callback.onPeersDiscovered(new ArrayList<>(peerList));
                }
            }
        }
    };

    private final BroadcastReceiver p2pBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                boolean isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                Log.d(TAG, "Wi-Fi P2P State Changed: Enabled = " + isEnabled);
                if (callback != null) {
                    callback.onP2pEnabled(isEnabled);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                Log.d(TAG, "Wi-Fi P2P Peers Changed");
                if (wifiP2pManager != null) {
                    try {
                        wifiP2pManager.requestPeers(channel, peerListListener);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException requesting peers", e);
                    }
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (wifiP2pManager == null) return;
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null && networkInfo.isConnected()) {
                    Log.d(TAG, "Wi-Fi P2P Devices Connected");
                } else {
                    Log.d(TAG, "Wi-Fi P2P Devices Disconnected");
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (device != null) {
                    Log.d(TAG, "This device changed: " + device.deviceName + " [" + device.deviceAddress + "]");
                }
            }
        }
    };

    public WifiDirectManager(Context context, WifiDirectCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.wifiP2pManager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager != null) {
            this.channel = wifiP2pManager.initialize(this.context, this.context.getMainLooper(), null);
        } else {
            this.channel = null;
        }
    }

    public void registerReceiver() {
        if (isReceiverRegistered) return;
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        context.registerReceiver(p2pBroadcastReceiver, intentFilter);
        isReceiverRegistered = true;
        Log.d(TAG, "P2P broadcast receiver registered");
    }

    public void unregisterReceiver() {
        if (!isReceiverRegistered) return;
        try {
            context.unregisterReceiver(p2pBroadcastReceiver);
            isReceiverRegistered = false;
            Log.d(TAG, "P2P broadcast receiver unregistered");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver was already unregistered", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void discoverPeers() {
        if (wifiP2pManager == null || channel == null) {
            Log.e(TAG, "Wi-Fi Direct not supported or initialized");
            return;
        }

        registerReceiver();

        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "discoverPeers started successfully");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "discoverPeers failed with reason code: " + reason);
            }
        });
    }
}
