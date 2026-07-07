# Phase 3 Testing Guide: 3-Phone Mesh Test

## Quick Reference Card

### Device Setup
```
Phone A (Source):
├─ MAC: A_MAC (e.g., "AA:BB:CC:DD:EE:00")
├─ Role: Originator
└─ Test: Create message to C_MAC

Phone B (Relay):
├─ MAC: B_MAC (e.g., "AA:BB:CC:DD:EE:01")
├─ Role: Intermediate node
└─ Test: Forward message A→C

Phone C (Destination):
├─ MAC: C_MAC (e.g., "AA:BB:CC:DD:EE:02")
├─ Role: Final receiver
└─ Test: Receive and ACK message
```

---

## Pre-Test Checklist

### Device Requirements
- [ ] All 3 phones: Android 8+ (API 26+)
- [ ] All 3 phones: Bluetooth 4.0+ (BLE/Classic)
- [ ] All 3 phones: Messy app installed
- [ ] All 3 phones: Room to move ~20 meters

### Before Each Test Run
- [ ] Bluetooth enabled on all 3 phones
- [ ] Location services enabled (required for BT discovery)
- [ ] All apps force-closed before restart
- [ ] Clear app data: `adb shell pm clear com.messy.app`
- [ ] Logcat running: `adb logcat | grep "ForwardingEngine:"`

### Permissions to Grant
When app starts, accept:
- [ ] Bluetooth
- [ ] Fine Location
- [ ] Coarse Location
- [ ] Display over other apps (for notifications)

---

## Test Scenario 1: Direct Forwarding (A → B → C)

### Setup Phase (5 minutes)
```
Action                          Expected Result
─────────────────────────────────────────────────────
1. Open app on Phone A          UI loads, device A ready
2. Note MAC address             A_MAC = "AA:BB:CC:DD:EE:00"
3. Start Bluetooth discovery    A begins scanning
4. Open app on Phone B           UI loads, device B ready
5. Note MAC address             B_MAC = "AA:BB:CC:DD:EE:01"
6. Start Bluetooth discovery    B begins scanning
7. Open app on Phone C           UI loads, device C ready
8. Note MAC address             C_MAC = "AA:BB:CC:DD:EE:02"
9. Start Bluetooth discovery    C begins scanning
```

### Message Creation (Phone A)
```
Action                              Expected Result
───────────────────────────────────────────────────────
1. Click "Create Message"           Message composer opens
2. Enter receiver: C_MAC            Input shows "AA:BB:CC:DD:EE:02"
3. Enter body: "Test mesh hop"      Text shows in field
4. Tap "Send"                       Message inserted to A's DB
                                    Message appears in A's chat
                                    Status: "Pending" (not delivered)
```

**Logcat Output (Phone A):**
```
D ForwardingEngine: Creating message ID: [UUID]
D SyncManager: Created message: [UUID] to: C_MAC
D MessageDao: Inserted message with TTL=5
```

### Connection Phase (Phones A & B)
```
Timeline: 0-30 seconds

Action                              Expected Result
───────────────────────────────────────────────────────
Move A and B within 10 meters       Device discovery starts
                                    Names appear in each other's lists
                                    Both devices in range ✓
```

**Logcat Output (Phone A):**
```
D BluetoothDiscovery: Device discovered: [Phone B name]
D BluetoothConnectionService: Attempting connection...
D BluetoothConnectionService: STATE_CONNECTING
```

**Logcat Output (Phone B):**
```
D BluetoothDiscovery: Device discovered: [Phone A name]
D BluetoothConnectionService: Connected!
D BluetoothConnectionService: STATE_CONNECTED
```

### Sync Phase (A → B)
```
Timeline: 30-35 seconds

Logcat Output (Phone A - Sender):
D ForwardingEngine: Starting sync with peer
D ForwardingEngine: Sent our ID list: 1 messages
  └→ Message UUID created earlier

D ForwardingEngine: Received peer's ID list: 0 messages
  └→ B has no messages yet

D ForwardingEngine: Sent message: UUID, TTL now: 4
  └→ A decrements TTL from 5 to 4 before sending

D ForwardingEngine: Sent END marker
D ForwardingEngine: Sync completed successfully

Logcat Output (Phone B - Receiver):
D ForwardingEngine: Starting sync with peer
D ForwardingEngine: Received our ID list: 1 messages
D ForwardingEngine: Sent our ID list: 0 messages

D ForwardingEngine: Received message: UUID
D SyncManager: Processing received message: UUID
D ForwardingEngine: Forwarding message UUID, TTL now: 4
  └→ B checks: receiver != B_MAC, TTL > 0 ✓

D MessageDao: Inserted message with TTL=4
D ForwardingEngine: Sync completed successfully
```

**Database State After A↔B Sync:**

Phone A:
```
Messages in DB:
├─ UUID: sender=A_MAC, receiver=C_MAC, body="Test mesh hop"
│  ├─ ttl: 5 (original)
│  ├─ delivered: false
│  └─ timestamp: T0

Phone B:
├─ UUID: sender=A_MAC, receiver=C_MAC, body="Test mesh hop"
│  ├─ ttl: 4 (decremented during forward)
│  ├─ delivered: false
│  └─ timestamp: T0 (copied)
```

### Intermediate Movement (Phone B toward C)
```
Timeline: 35-60 seconds

Action                              Expected Result
───────────────────────────────────────────────────────
Move B away from A                  A and B lose signal
                                    Connection drops
                                    Both return to discovery
Move B toward C                     B scans for nearby devices
                                    C device appears in B's list
                                    Both in range ✓
```

**Logcat Output (All Phones):**
```
D BluetoothConnectionService: onConnectionLost
D BluetoothConnectionService: Attempting connection...
D BluetoothConnectionService: Connected!
D BluetoothConnectionService: STATE_CONNECTED
```

### Final Sync (B → C)
```
Timeline: 60-65 seconds

Logcat Output (Phone B):
D ForwardingEngine: Starting sync with peer
D ForwardingEngine: Sent our ID list: 1 messages
  └→ UUID (the message from A)

D ForwardingEngine: Received peer's ID list: 0 messages
D ForwardingEngine: Sent message: UUID, TTL now: 3
  └→ B decrements TTL from 4 to 3

Logcat Output (Phone C):
D ForwardingEngine: Starting sync with peer
D ForwardingEngine: Received our ID list: 1 messages
  └→ UUID

D ForwardingEngine: Sent our ID list: 0 messages

D ForwardingEngine: Received message: UUID
D SyncManager: Processing received message: UUID
D SyncManager: Message UUID reached destination! receiver == myId
D MessageDao: Inserted message with delivered=true
D ForwardingEngine: Created ACK: ACK_UUID_[timestamp]
  └→ Special ACK message for flooding back

D ForwardingEngine: Sync completed successfully
```

### Verification (Phone C)
```
Timeline: 65 seconds

UI Display on Phone C:
├─ Open chat with A_MAC
├─ Message appears: "Test mesh hop"
├─ Status badge: "✓ Delivered" (not "✓✓ Read")
├─ Timestamp: T0 (original creation time)
└─ Body: Exactly "Test mesh hop"

Phone Database (C):
├─ Message UUID: sender=A_MAC, receiver=C_MAC
│  ├─ ttl: 3 (decremented again)
│  ├─ delivered: true ✓
│  └─ timestamp: T0
```

### ACK Flooding (C → B → A)
```
Timeline: 65-75 seconds

Logcat Output (Phone C):
D ForwardingEngine: Created ACK: ACK_UUID_[timestamp]
D MessageDao: Inserted ACK_UUID_[timestamp]
D SyncManager: onAckReceived callback triggered

Logcat Output (Phone B - Receives ACK):
D ForwardingEngine: Received message: ACK_UUID_[timestamp]
D SyncManager: Processing received message: ACK_UUID_[timestamp]
D SyncManager: Message ACK_UUID_[timestamp] not ours, forwarding
D MessageDao: Inserted ACK with TTL=4

Logcat Output (Phone A - Receives ACK):
D ForwardingEngine: Received message: ACK_UUID_[timestamp]
D SyncManager: Processing received message: ACK_UUID_[timestamp]
D SyncManager: Received ACK for message: UUID
D SyncManager: Message UUID marked as delivered ✓
D SyncManager: onAckReceived callback triggered
```

### Final Verification (Phone A)
```
Timeline: 75 seconds

UI Display on Phone A:
├─ Open chat with C_MAC
├─ Original message: "Test mesh hop"
├─ Status badge: "✓✓ Delivered" (now shows ACK received!)
└─ Timestamp: T0

Phone A Database:
├─ Message UUID: sender=A_MAC, receiver=C_MAC
│  ├─ delivered: true ✓✓ (updated by ACK)
│  └─ Status shown in UI
```

---

## Test Scenario 2: TTL Expiration Test

### Goal
Verify messages don't loop infinitely; TTL limits forwarding.

### Setup
```
Devices positioned in triangle:
  A ↔ B ↔ C (and B ↔ A!)
  
B can reach both A and C (they can't reach each other).
Message crosses A → B → C → B (again) ...
TTL should prevent infinite loop.
```

### Test Steps
```
1. Send message A → C (with TTL=3)
2. A ↔ B sync: TTL decrements to 2
3. B ↔ C sync: TTL decrements to 1
4. C ↔ B sync: B receives it back, TTL decrements to 0
5. B stores ACK
6. Message never loops back to A
7. Database check: Message TTL=0 in B, but delivered=true in C
```

**Expected Logcat (Phone B, second sync with C):**
```
D ForwardingEngine: Received message: UUID
D ForwardingEngine: TTL=0, dropping message
D ForwardingEngine: Message UUID already processed (cached)
  └→ Prevents infinite re-processing
```

---

## Test Scenario 3: Deduplication Test

### Goal
Verify same message coming from multiple peers isn't duplicated.

### Setup
```
A connects to B
A → B: Message UUID-1 (TTL=5)
A disconnects

B connects to C
B → C: Message UUID-1 (TTL=4)

C connects to B again (B refreshes)
B → C: Message UUID-1 (already has it!)
```

### Expected Behavior
```
Phone C, first sync with B:
D ForwardingEngine: Message UUID-1 inserted (first time)

Phone C, second sync with B:
D ForwardingEngine: Received message: UUID-1
D ForwardingEngine: Message UUID-1 already in DB (dedup), skipping
  └→ findById() found existing → return without insert
```

### Verification
```
Phone C database:
├─ Message UUID-1: count = 1 (not 2!)
└─ Dedup successful ✓
```

---

## Test Report Template

Use this to document your test run:

```
=== PHASE 3 MESH TEST REPORT ===

Test Date: _____________
Tester: ________________
Android Version: A___, B___, C___
BT Chipsets: A___, B___, C___

DEVICE IDs:
  A_MAC: ______________________
  B_MAC: ______________________
  C_MAC: ______________________

TEST SCENARIO 1: Direct Forwarding (A→B→C)
├─ Message created on A: [ ] Pass [ ] Fail
│  Details: ___________________________________
├─ Detected on B after sync: [ ] Pass [ ] Fail
│  Time to receive: _____ seconds
│  TTL on B: Expected 4, Got: _____
├─ Delivered on C: [ ] Pass [ ] Fail
│  Appeared in chat: [ ] Yes [ ] No
│  Status shows "Delivered": [ ] Yes [ ] No
├─ ACK received on A: [ ] Pass [ ] Fail
│  Status shows "ACK": [ ] Yes [ ] No
│  Time for round-trip: _____ seconds
└─ Overall: [ ] PASS [ ] FAIL

TEST SCENARIO 2: TTL Expiration
├─ Message created with TTL=3: [ ] Pass [ ] Fail
├─ Forwarded with TTL decrement: [ ] Pass [ ] Fail
├─ Stopped at TTL=0: [ ] Pass [ ] Fail
└─ Overall: [ ] PASS [ ] FAIL

TEST SCENARIO 3: Deduplication
├─ Message received once: [ ] Pass [ ] Fail
├─ No duplicates on second sync: [ ] Pass [ ] Fail
└─ Overall: [ ] PASS [ ] FAIL

ISSUES ENCOUNTERED:
1. ___________________________________
   Resolution: _______________________
2. ___________________________________
   Resolution: _______________________

NOTES:
_________________________________________
_________________________________________

NEXT STEPS:
_________________________________________
```

---

## Troubleshooting During Test

### Symptom: Message stuck on A (never reaches B)

**Check:**
1. Are A and B in range?
   ```
   Logcat on A: "Attempting connection..."
   Logcat on B: "Connected!"
   ```

2. Is sync running?
   ```
   Logcat: "Starting sync with peer"
   ```

3. Is TTL initialized?
   ```
   DB query: SELECT ttl FROM messages WHERE id = 'UUID'
   Expected: ttl > 0
   ```

**Fix:**
- Move phones closer
- Grant location permission
- Check `Message.ttl` initialization in createMessage()

### Symptom: Message arrives on C, but status doesn't update on A

**Check:**
1. Did C create ACK?
   ```
   Logcat on C: "Created ACK: ACK_UUID_..."
   ```

2. Is ACK reaching B?
   ```
   Logcat on B: "Forwarding ACK, TTL now: ..."
   ```

3. Did A receive ACK?
   ```
   Logcat on A: "Received ACK for message: UUID"
   ```

**Fix:**
- Keep devices in range longer
- Increase ACK TTL in SyncManager (DEFAULT_TTL)
- Check ACK message format: `"ACK:{originalId}"`

### Symptom: Continuous duplicate messages

**Check:**
1. Is findById() working?
   ```
   Logcat: "Message UUID already in DB (dedup), skipping"
   ```

2. Are message IDs unique?
   ```
   DB: SELECT COUNT(DISTINCT id) FROM messages
       == COUNT(*) FROM messages (should be equal)
   ```

**Fix:**
- Verify `Message.id = UUID.randomUUID().toString()`
- Check MessageDao.insert() uses `OnConflictStrategy.IGNORE`
- Restart app and retry

---

## Performance Baseline

Expected timing for A → B → C:
```
Event                   Expected Time    Actual Time
─────────────────────────────────────────────────
A creates message       0.5 sec         ________
A detects B             5-10 sec        ________
A-B sync completes      3-5 sec         ________
B detects C             5-10 sec        ________
B-C sync completes      3-5 sec         ________
C creates ACK           0.5 sec         ________
ACK reaches A           3-5 sec         ________
────────────────────────────────────────────────
TOTAL                   20-40 sec       ________
```

If your times are significantly longer:
- Check Bluetooth interference
- Reduce DISCOVERY_INTERVAL_MS in SyncForegroundService
- Verify no other BT apps running

---

## Success Criteria

✅ **PASS** When:
1. Message sent from A arrives at C
2. C correctly identifies receiver == myId
3. Message marked delivered=true
4. ACK created and floods back
5. A receives ACK and updates status
6. No duplicates in any database
7. TTL decrements properly each hop
8. Message doesn't loop infinitely

❌ **FAIL** If:
- Any step above doesn't happen
- Duplicates appear in database
- Message loops infinitely  
- ACK never arrives
- Timestamps don't match

---

## Next Test: Multi-Device Network

After 3-phone test passes, try:
```
4+ phones in line: A ↔ B ↔ C ↔ D
└→ Test 3+ hop forwarding (TTL=6+)

Star topology: A ←→ B ←→ C,D,E
└→ Test flooding to multiple peers

Random walk: Phones move in Bluetooth range randomly
└→ Test dynamic network changes
```

---

Good luck with your mesh network testing! 🚀
