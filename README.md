# Messy: Local P2P Messaging App

Messy is a modern, native Android local chat application built to explore local data persistence using Room and offline peer-to-peer raw data exchange over Bluetooth and Wi-Fi Direct.

---

## 🌟 Key Features

* **Material 3 UI**: Clean, responsive layout utilizing customized toolbars, round initials-only user avatars, bubble layouts for incoming/outgoing chats, and standard Material details.
* **Offline Persistence**: Fully decoupled conversation history powered by a Room database architecture (`AppDatabase`, `Message` entity, and `MessageDao`).
* **Bluetooth Connectivity**:
  * Broadcast-receiver-driven device discovery via `BluetoothDiscovery`.
  * Multi-threaded Server (`AcceptThread`) and Client (`ConnectThread`) P2P sockets in `BluetoothConnectionService`.
  * Bidirectional stream handler using `ObjectOutputStream`/`ObjectInputStream` to send and receive serialized `Message` objects.
* **Wi-Fi Direct Discovery**: Parallel/backup local discovery channel powered by `WifiP2pManager` to detect nearby peers.

---

## 📁 Project Architecture & Components

```
app/src/main/java/com/messy/app/
├── chat/
│   ├── ChatActivity.java          # Handles thread view, sending and rendering chats
│   ├── ChatAdapter.java           # Adapts Message items to asymmetric bubble layouts
│   ├── ConversationAdapter.java   # Adapts conversations to list items
│   └── ConversationSummary.java   # Model representing active conversations
├── database/
│   ├── AppDatabase.java           # Room database singleton
│   ├── Message.java               # Persisted & Serializable Message model
│   └── MessageDao.java            # Room DAO for transactions
├── network/
│   ├── bluetooth/
│   │   ├── BluetoothConnectionService.java  # Server & Client rfcomm sockets
│   │   └── BluetoothDiscovery.java         # Bluetooth scan/discovery broadcasts
│   └── wifi/
│       └── WifiDirectManager.java           # Wi-Fi Direct peer discovery manager
├── utils/
│   └── AppExecutors.java          # Multi-threaded runtime utilities
└── MainActivity.java              # Entry point and P2P developer test panel coordinator
```

---

## 🚀 How to Run & Test

Because Android emulators do not support physical Bluetooth chips, testing raw P2P data exchange requires two physical Android devices:

### 1. Prerequisite Pairing
- Ensure physical **Phone A** and **Phone B** are paired inside native operating system Bluetooth settings.

### 2. Build & Install
- Connect your device and compile the debug APK:
  ```powershell
  .\gradlew.bat installDebug
  ```

### 3. Verification Steps
1. Open the app on both devices.
2. Tap **Grant Perms** to accept the location and Bluetooth permission requests.
3. Tap **Start BT** on **Phone B** to spin up the server socket. Logs show `AcceptThread: waiting for connection...`.
4. Tap **Start BT** on **Phone A** to begin scanning for physical peers. The paired device will show up under `Discovered Devices`.
5. Tap **Phone B**'s button on **Phone A**'s screen.
6. Once connected, tap **Send 'hello'** on **Phone A**.
7. Confirm that **Phone B** receives the serialized raw data object (visible through the Logcat filter `/P2PVerify`, via Android Toast, and inside the local conversation list).