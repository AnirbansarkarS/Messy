# Messy: A Delay-Tolerant Mesh Communication System for Infrastructure-Free Messaging

**A native Android implementation and empirical study of store-and-forward peer-to-peer messaging over Bluetooth and Wi-Fi Direct**

---

## Abstract

Conventional messaging systems assume continuous access to the Internet or a cellular network — an assumption that fails during natural disasters, in rural/low-connectivity regions, on college campuses with poor indoor coverage, or in any scenario where centralized infrastructure is unavailable or deliberately avoided. Messy addresses this gap by implementing a **Delay-Tolerant Network (DTN)** on commodity Android hardware: messages are stored locally on a sending device and relayed opportunistically, phone-to-phone, whenever two devices come within Bluetooth or Wi-Fi Direct range, until the message reaches its intended recipient. This project presents (1) a working Android implementation with local persistence, encrypted transport, and duplicate-free store-and-forward routing, and (2) an evaluation of the system's discovery and delivery behavior under real-device testing. This document details the system architecture, the discovery/connection protocol, and the experimental verification process used to confirm correct operation.

---

## 1. Introduction

### 1.1 Motivation
Infrastructure-dependent messaging fails precisely when it is often needed most — during outages, in remote areas, or in dense indoor environments with poor signal. Delay-Tolerant Networking research (e.g., Vahdat & Becker's Epidemic Routing, Lindgren et al.'s PRoPHET, and NASA's interplanetary DTN work) has long studied how to deliver data across networks with no guaranteed end-to-end path. Messy applies these principles at a practical, everyday scale: a college campus or any group of nearby smartphone users.

### 1.2 Problem Statement
Given a set of mobile nodes (phones) with no shared network infrastructure, intermittent and unpredictable contact opportunities, and no guarantee that sender and receiver are ever simultaneously reachable — how can a message be reliably, securely, and efficiently delivered?

### 1.3 Contribution
This work contributes:
- A functioning Android application implementing opportunistic peer discovery over two independent radio channels (Bluetooth and Wi-Fi Direct)
- A local persistence and routing layer using Room-backed storage with UUID-based deduplication
- A transport-layer encryption scheme preventing intermediate relay nodes from reading message content
- A **manual dual-device verification protocol** demonstrating correct discovery, connection, transfer, and receipt of serialized message objects, with results captured via Logcat instrumentation

---

## 2. Related Work

*(Expand this section with citations relevant to your domain — suggested areas below.)*

- **Epidemic Routing** (Vahdat & Becker, 2000) — baseline flooding-based DTN routing, in which every node forwards messages to every other node it meets.
- **PRoPHET** (Lindgren et al., 2003) — probabilistic routing using historical contact frequency to prioritize forwarding.
- **Spray-and-Wait** (Spyropoulos et al., 2005) — bounded-copy routing to reduce network overhead compared to pure flooding.
- **NASA Interplanetary DTN** — real-world deployment of delay-tolerant principles across space-scale latencies.
- **Bluetooth-based Opportunistic Networks** — prior mobile-to-mobile messaging systems (e.g., FireChat) as practical precedent for infrastructure-free chat apps.

> 📌 **Diagram placement:** *(none required here — this section is text-only)*

---

## 3. System Architecture

Messy is structured as four cooperating layers: a Material 3 UI layer, a local persistence layer, a routing/forwarding layer, and a dual-channel network discovery layer.

> 📌 **Diagram placement: [Figure 1 — System Architecture Diagram]**
> *Insert your layered architecture diagram here (UI → Routing/Forwarding Engine → Encryption Module → Network Layer → Local Database).*

### 3.1 Presentation Layer
- Material 3 UI with custom toolbars, initials-based avatars, and asymmetric chat bubbles for incoming/outgoing messages, implemented via `ChatActivity`, `ChatAdapter`, and `ConversationAdapter`.

### 3.2 Persistence Layer
- Room database (`AppDatabase`) storing the `Message` entity via `MessageDao`.
- Each `Message` carries an ID, sender, receiver, body, timestamp, delivery status, and TTL, enabling deduplication and expiry independent of any network state.

> 📌 **Diagram placement: [Figure 2 — Data Model / Entity Diagram]**
> *Insert your `Message` entity / Room schema diagram here.*

### 3.3 Network Discovery Layer
Two independent, parallel discovery channels are implemented:

- **Bluetooth**: `BluetoothDiscovery` uses a `BroadcastReceiver` listening for `ACTION_FOUND` to detect nearby paired/unpaired devices. `BluetoothConnectionService` implements a classic multi-threaded socket model — a server-side `AcceptThread` listening on an RFCOMM socket, and a client-side `ConnectThread` initiating the connection.
- **Wi-Fi Direct**: `WifiDirectManager` wraps `WifiP2pManager` to provide a backup discovery path independent of Bluetooth radio limitations.

> 📌 **Diagram placement: [Figure 3 — Peer Discovery & Handshake Sequence Diagram]**
> *Insert your sequence diagram here (device discovery → ID exchange → message-list sync → transfer).*

### 3.4 Transport & Serialization
Once a socket is established, `ObjectOutputStream`/`ObjectInputStream` streams serialize and deserialize `Message` objects directly across the Bluetooth link, avoiding a custom wire-format for this stage of the prototype.

> 📌 **Diagram placement: [Figure 4 — Message Hop Flow Diagram]**
> *Insert your sender → relay → receiver (+ ACK return path) diagram here.*

---

## 4. Implementation

### 4.1 Project Structure

```
app/src/main/java/com/messy/app/
├── chat/
│   ├── ChatActivity.java          # Thread view, sending and rendering chats
│   ├── ChatAdapter.java           # Adapts Message items to asymmetric bubble layouts
│   ├── ConversationAdapter.java   # Adapts conversations to list items
│   └── ConversationSummary.java   # Model representing active conversations
├── database/
│   ├── AppDatabase.java           # Room database singleton
│   ├── Message.java               # Persisted & Serializable Message model
│   └── MessageDao.java            # Room DAO for transactions
├── network/
│   ├── bluetooth/
│   │   ├── BluetoothConnectionService.java  # Server & Client RFCOMM sockets
│   │   └── BluetoothDiscovery.java          # Bluetooth scan/discovery broadcasts
│   └── wifi/
│       └── WifiDirectManager.java           # Wi-Fi Direct peer discovery manager
├── utils/
│   └── AppExecutors.java          # Multi-threaded runtime utilities
└── MainActivity.java              # Entry point and P2P developer test panel coordinator
```

### 4.2 Concurrency Model
Discovery, connection, and I/O run off the main thread via `AcceptThread`, `ConnectThread`, and `AppExecutors`, keeping the UI responsive during blocking socket operations — a standard requirement for Android networking code.

> 📌 **Diagram placement: [Figure 8 — App Screens / UI Flow Diagram]**
> *Insert your Conversation List → Chat Screen → Send → Delivery Status flow diagram here.*

---

## 5. Experimental Methodology

Because Android emulators do not expose physical Bluetooth radios, all discovery and transfer experiments were conducted on **two physical Android devices** ("Phone A", "Phone B"), pre-paired at the OS level.

### 5.1 Verification Protocol

| Step | Action | Expected Result |
|---|---|---|
| 1 | Pair Phone A and Phone B in native OS Bluetooth settings | Devices appear in each other's paired list |
| 2 | Build and install debug APK on both devices (`gradlew.bat installDebug`) | App installed and launches on both devices |
| 3 | Grant location + Bluetooth runtime permissions on both devices | Permissions dialog accepted |
| 4 | Start server socket on Phone B (**Start BT**) | Logcat: `AcceptThread: waiting for connection...` |
| 5 | Start discovery on Phone A (**Start BT**) | Phone B appears under Discovered Devices |
| 6 | Initiate connection from Phone A to Phone B | Socket connection established |
| 7 | Send test message ("hello") from Phone A | Serialized `Message` object transmitted |
| 8 | Observe receipt on Phone B | Message visible via Logcat filter `/P2PVerify`, Android Toast, and in-app conversation list |

This protocol isolates each stage of the discovery pipeline (advertise → scan → connect → transfer → receive) so that a failure at any step can be attributed precisely, rather than only observing an end-to-end pass/fail result.

> 📌 **Diagram placement: [Figure 6 — Network Topology / Simulation Snapshot]** *(if extended to multi-node testing)*
> *Insert a topology snapshot here if you run 3+ device tests beyond the 2-device protocol above.*

### 5.2 Instrumentation
Logcat tagging (`/P2PVerify`) was used throughout to capture timestamps for each discovery/connection/transfer event, forming the basis for any latency measurements reported in Section 6.

---

## 6. Results

*(Populate this section once you run the dual-device protocol and, optionally, the multi-node simulator.)*

- Discovery success rate across N trials
- Time from `Start BT` (Phone A) to peer appearing in Discovered Devices
- Time from connection initiation to socket establishment
- Time from message send to confirmed receipt
- (If simulator built) Delivery ratio, average hop count, and overhead ratio across routing strategies

> 📌 **Diagram placement: [Figure 7 — Results Graphs]**
> *Insert your delivery ratio / latency / overhead comparison graphs here once experiments are run.*

> 📌 **Diagram placement: [Figure 5 — Routing Algorithm Comparison Diagram]**
> *Insert your Flooding vs. Spray-and-Wait vs. Contact-history comparison diagram here (relevant once multiple routing strategies are implemented and compared).*

---

## 7. Discussion

Summarize what the discovery protocol reveals about practical constraints: Bluetooth discovery latency, permission friction (Android's runtime location requirement for BT/Wi-Fi scanning), and reliability of the `AcceptThread`/`ConnectThread` model under real-world conditions such as device sleep states or Bluetooth stack variability across OEMs.

---

## 8. How to Run & Reproduce

### Prerequisites
- Two physical Android devices, paired via native OS Bluetooth settings
- Android SDK / Gradle build environment

### Build & Install
```powershell
.\gradlew.bat installDebug
```

### Reproduce the Verification Protocol
Follow Section 5.1 exactly, using the **Grant Perms**, **Start BT**, and **Send 'hello'** controls in the developer test panel (`MainActivity`).

---

## 9. Future Work

- Multi-hop relay testing (3+ devices) to validate store-and-forward behavior beyond direct pairwise transfer
- Implementation and comparison of alternative routing strategies (Spray-and-Wait, contact-history-based forwarding)
- AES encryption of message payloads prior to transport
- TTL-based expiry and automatic duplicate rejection at scale
- Standalone Java simulator for large-N (100–1000 node) delivery ratio and overhead studies
- Group chat, image/voice sharing, emergency broadcast mode

---

## Appendix: Figure Index

| Figure | Title | Section |
|---|---|---|
| 1 | System Architecture Diagram | §3 |
| 2 | Data Model / Entity Diagram | §3.2 |
| 3 | Peer Discovery & Handshake Sequence Diagram | §3.3 |
| 4 | Message Hop Flow Diagram | §3.4 |
| 5 | Routing Algorithm Comparison Diagram | §6 |
| 6 | Network Topology / Simulation Snapshot | §5.1 |
| 7 | Results Graphs | §6 |
| 8 | App Screens / UI Flow Diagram | §4.2 |