<p align="center">
  <img src="https://hamoon.net/wp-content/uploads/2025/09/logo-transparent.png" alt="Uncle Ted Logo" width="180">
</p>

<h1 align="center">Uncle Ted for Android</h1>

<p align="center">
  <strong>A cabin in the digital woods.</strong><br>
  An advanced, hardware-backed personal security, anti-coercion, and anti-forensic defense suite for Android.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Version-v5.0.1-brightgreen.svg" alt="Version">
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg" alt="Target SDK">
  <img src="https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Hardware-Titan%20M2%20%2F%20StrongBox-blueviolet.svg" alt="Titan M2 StrongBox">
  <img src="https://img.shields.io/badge/Exploit%20Defense-ARM%20MTE%20(Sync)-red.svg" alt="ARM MTE">
  <img src="https://img.shields.io/badge/Platform-Device%20Owner%20%2F%20AVB%20Locked-blue.svg" alt="Device Owner">
  <img src="https://img.shields.io/badge/Root-Magisk%20%2F%20KernelSU%20%2F%20APatch-purple.svg" alt="Root">
</p>

<p align="center">
  <img width="829" height="601" alt="Uncle Ted Architecture Overview" src="https://github.com/user-attachments/assets/cef2fb8b-4fdd-40f5-9778-c89e2f4a9825" />
</p>

---

### **Table of Contents**
- [⚠️ Legal & Ethical Disclaimer](#️-legal--ethical-disclaimer)
- [📊 Architectural Comparison: Deployment Profiles](#-architectural-comparison-deployment-profiles)
- [🏗️ System Architecture (Dual-Profile Engine)](#️-system-architecture-dual-profile-engine)
- [✨ Core Capabilities](#-core-capabilities)
  - [1. Hardware Security Module & Master Suicide Key (Titan M2 / StrongBox)](#1-hardware-security-module--master-suicide-key-titan-m2--strongbox)
  - [2. Exploit Mitigation & Native Memory Hardening (ARMv8.5-A MTE)](#2-exploit-mitigation--native-memory-hardening-armv85-a-mte)
  - [3. Lockscreen Authentication & Anti-Coercion Engine](#3-lockscreen-authentication--anti-coercion-engine)
  - [4. Storage Destruction Pipeline & Cryptographic Header Erasure](#4-storage-destruction-pipeline--cryptographic-header-erasure)
  - [5. Physical Bus & Peripheral Defense (USB HAL v1.3+ & Radio Isolation)](#5-physical-bus--peripheral-defense-usb-hal-v13--radio-isolation)
  - [6. Multi-User RAM Anti-Forensics & Cold Vold Eviction](#6-multi-user-ram-anti-forensics--cold-vold-eviction)
  - [7. Autonomous Environmental & Dead-Man Tripwires](#7-autonomous-environmental--dead-man-tripwires)
  - [8. Carrier-Blind Remote Command & Control (Ed25519 Wire, OTC, SMS)](#8-carrier-blind-remote-command--control-ed25519-wire-otc-sms)
  - [9. Covert Surveillance & Multi-Modal Evidence Gathering](#9-covert-surveillance--multi-modal-evidence-gathering)
- [📡 Remote SMS Command Reference](#-remote-sms-command-reference)
- [🛠️ Technology Stack](#️-technology-stack)
- [📂 Project Directory Structure](#-project-directory-structure)
- [🚀 Deployment & Installation Guide](#-deployment--installation-guide)
  - [Prerequisites](#prerequisites)
  - [Phase 1: Acquire the Release Artifacts](#phase-1-acquire-the-release-artifacts)
  - [Phase 2: Deployment Selection](#phase-2-deployment-selection)
    - [Route A: Provision as Device Owner (Locked Bootloader / AVB Enforced)](#route-a-provision-as-device-owner-locked-bootloader--avb-enforced)
    - [Route B: Flashing the Systemless Module (.zip) for Root & LSPosed](#route-b-flashing-the-systemless-module-zip-for-root--lsposed)
  - [Phase 3: Activating the LSPosed Hook (Route B Only)](#phase-3-activating-the-lsposed-hook-route-b-only)
  - [Phase 4: Configuring & Arming Credentials](#phase-4-configuring--arming-credentials)
- [📄 Verification & Diagnostic Checklist](#-verification--diagnostic-checklist)
- [📄 License & Credits](#-license--credits)

---

> [!WARNING]
> ### ⚠️ Legal & Ethical Disclaimer
> **Uncle Ted is engineered strictly for educational research, authorized defensive security auditing, high-risk operational privacy, and human rights protection against physical coercion and forensic seizure.**
> Features such as discrete hardware Secure Element suicide key erasure, low-level FBE metadata zeroing, partition invalidation, and Vold key eviction carry permanent, irreversible consequences. Erasing cryptographic key material renders underlying data mathematically and physically unrecoverable.
> **Never install or deploy this software on any hardware without explicit, informed authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the deployment or operation of this codebase.

---

## 📊 Architectural Comparison: Deployment Profiles

On modern Android (Android 9 through 14+), the platform enforces strict security boundaries between unprivileged apps, system services, and hardware execution environments. Uncle Ted operates across two privileged architectural routes: **Route A (Enterprise Device Owner with Locked Bootloader & Enforcing AVB 2.0)** and **Route B (Systemless Priv-App with native LSPosed hooks in `system_server`)**.

| Security Vector / Capability | Standalone APK (Stock OS / Sideloaded) | Route A: Device Owner via ADB (Locked Bootloader & AVB) | Route B: Privileged Root + LSPosed (Unlocked Bootloader) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Bootloader & AVB State** | 🟢 Locked (AVB Enforcing) | 🟢 **100% Locked (AVB Enforcing)** | 🔴 Unlocked (dm-verity unverified) | Route A retains the hardware root of trust; Route B requires an unlocked bootloader for Zygisk, Magisk/KernelSU, and custom kernels. |
| **Hardware KeyMint Isolation** | 🟡 Emulated / TEE | 🟢 **Titan M2 / StrongBox HSM** | 🟡 TEE Degraded / Compromised | Route A uses `setIsStrongBoxBacked(true)` to isolate keys within discrete silicon (Titan M2); Route B hardware attestation fails due to unlocked state. |
| **ARMv8.5-A MTE Hardening** | 🔴 Non-Enforced | 🟢 **Synchronous Mode (`sync`)** | 🟢 **Synchronous Mode (`sync`)** | Native layer sets `PR_MTE_TCF_SYNC` via `prctl()`, aborting spatial/temporal memory corruptions immediately via `SIGSEGV`. |
| **Lockscreen Interception** | 🔴 Non-Functional | 🟡 **Fail Callback / In-App Guard** | 🟢 **100% Native Hook** | Route B intercepts `LockSettingsService` directly inside `system_server`. Route A relies on Gatekeeper failure callbacks and hardware wipe limits. |
| **Sub-Millisecond Data Erasure** | 🔴 Prompts User / Deprecated | 🟢 **Instant Hardware SE Wipe** | 🟢 **16KB FBE Metadata & Vold Shred** | Route A commands Weaver/Titan M2 to revoke root Key Encryption Keys (KEKs) via `dpm.wipeData()`. Route B zeroes `/dev/block/by-name/metadata`. |
| **Hardware Brute-Force Rate Limiting** | 🔴 App-level (Bypassed) | 🟢 **Hardware-Enforced (Titan M)** | 🟢 **system_server Debounced Hook** | Route A sets `dpm.setMaximumFailedPasswordsForWipe(admin, 3)`, forcing the Weaver chip to purge FBE keys upon 3 invalid attempts. |
| **Physical USB Extraction Defense** | 🔴 Impossible | 🟢 **Physical USB HAL Port Severing** | 🟢 **Kernel UDC Monitor & Kill** | Route A calls `dpm.setUsbDataSignalingEnabled(false)` via USB HAL v1.3+ to sever D+/D- lines. Route B disables Linux UDC gadget drivers via SysFS/ConfigFS. |
| **Before First Unlock (BFU) Defense** | 🔴 Crashes (CE Storage Locked) | 🟢 **Fully Operational (Direct Boot)** | 🟢 **Fully Operational (Direct Boot)** | CE storage is inaccessible before initial unlock. Both routes execute within Device-Protected (DE) storage with Direct Boot receivers. |
| **Multi-User RAM Anti-Forensics** | 🔴 Impossible | 🟡 Work Profile Segregation | 🟢 **Cold Vold Eviction (`lockuser 0`)** | Route B drops User 0 Credential-Encrypted (CE) keys from the Linux kernel keyring using `vdc cryptfs lockuser 0`, reverting User 0 to BFU before loading Decoy Space. |
| **Autonomous Dead-Man Tripwire** | 🔴 Inoperable in BFU | 🟢 **Hardware RTC Alarm in BFU** | 🟢 **Hardware RTC Alarm in BFU** | Uses `AlarmManager.setExactAndAllowWhileIdle()` backed by DE storage; triggers autonomous wipe even if seized into a Faraday bag during cold boot. |
| **Remote Signaling Integrity** | 🔴 Cleartext SMS (Telco leaks) | 🟢 **Ed25519 Binary Wire + OTC** | 🟢 **Ed25519 Binary Wire + OTC** | Verifies 85-byte binary wire packets signed by an offline Ed25519 private key with strict 120s drift windows and monotonic sequence counters. |
| **Anti-Debugging / Memory Dumping** | 🔴 Default Debuggable Flags | 🟢 **`PR_SET_DUMPABLE = 0`** | 🟢 **`PR_SET_DUMPABLE = 0` + Volatile Zero** | Native security bridge enforces un-dumpable process flags and zeroes memory buffers using volatile C++ pointers to prevent compiler elimination. |
| **Overall Defense Posture** | **3.0 / 10** | **9.6 / 10** | **9.5 / 10** | Route A provides maximum hardware security, AVB chain of trust, and discrete HSM isolation; Route B provides native OS-level lockscreen control. |

---

## 🏗️ System Architecture (Dual-Profile Engine)

Uncle Ted v5.0.1 features a decoupled, strategy-based architecture coordinated by `DefenseCoordinator`. The platform dynamically analyzes execution privileges, hardware security module availability, and bootloader status at startup, binding the runtime to the optimal defensive strategy:

```
                                  ┌───────────────────────────────┐
                                  │   OPERATIONAL THREAT SIGNAL   │
                                  │   (Keyguard, Tripwire, SMS)   │
                                  └───────────────┬───────────────┘
                                                  │
                                  ┌───────────────▼───────────────┐
                                  │      DefenseCoordinator       │
                                  │ (Capability Runtime Resolver) │
                                  └───────────────┬───────────────┘
                                                  │
                  ┌───────────────────────────────┴───────────────────────────────┐
                  ▼                                                               ▼
 ┌─────────────────────────────────────────────────┐   ┌─────────────────────────────────────────────────┐
 │ ROUTE A: DEVICE OWNER (AVB 2.0 LOCKED)          │   │ ROUTE B: PRIVILEGED ROOT / LSPOSED (UNLOCKED)   │
 │ - 100% Enforcing AVB 2.0 Root of Trust          │   │ - Native system_server LockSettingsService Hook │
 │ - Titan M2 / StrongBox Hardware Master Suicide  │   │ - Cold Vold Session Eviction (lockuser 0)       │
 │ - Native setUsbDataSignalingEnabled(false) HAL  │   │ - 16KB FBE Metadata Partition Cryptographic Zero│
 │ - Weaver Exponential Brute-Force Rate Limiting  │   │ - Linux Kernel UDC Gadget Controller Severing   │
 │ - Direct Boot DE Hardware Dead-Man Tripwire     │   │ - Multi-User Decoy Profile Migration (User 10)  │
 │ - Ed25519 Cryptographic Envelope Verifier       │   │ - BCB Recovery Command Staging & SysRq Panic    │
 │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │   │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │
 └─────────────────────────────────────────────────┘   └─────────────────────────────────────────────────┘
```

---

## ✨ Core Capabilities

### 1. Hardware Security Module & Master Suicide Key (Titan M2 / StrongBox)
- **Discrete Silicon KeyMint (`StrongBoxSecurityManager`):** Protects application databases, sensitive preferences, and credential hashes using an AES-256-GCM master key provisioned inside discrete hardware silicon (`setIsStrongBoxBacked(true)`), isolated from the primary application processor.
- **Sub-10ms Cryptographic Suicide:** In duress or catastrophic compromise scenarios, Uncle Ted calls `KeyStore.deleteEntry(MASTER_SUICIDE_KEY_ALIAS)`. Deleting this silicon-level register takes under 10 milliseconds and makes all encrypted databases, credentials, and offline caches permanently unrecoverable, rendering physical flash dump analysis mathematically futile.
- **Weaver Hardware Rate-Limiting Weaponization:** In Route A, sets `dpm.setMaximumFailedPasswordsForWipe(admin, 3)`. The Titan M / Weaver chip enforces exponential backoffs and autonomously commands KeyMint to revoke root Key Encryption Keys (KEKs) upon 3 consecutive authentication failures, executing independently of userspace runtime health or battery state.

---

### 2. Exploit Mitigation & Native Memory Hardening (ARMv8.5-A MTE)
- **Synchronous Hardware Memory Tagging:** Enforces `android:memtagMode="sync"` at the application level and injects `-march=armv8.5-a+memtag -fsanitize=memtag` into Clang native compilation. Memory allocations on ARMv8.5-A+ silicon (Tensor G3/G4, Snapdragon 8 Gen 3+) are assigned 4-bit metadata tags; any spatial overflow or use-after-free pointer dereference triggers an instant hardware `SIGSEGV` (`SEGV_MTESERR`), stopping memory corruption exploits.
- **Native Process Sandboxing (`NativeSecurityBridge`):** Calls `prctl(PR_SET_DUMPABLE, 0)` via JNI during initialization, blocking `/proc/$PID/mem` extraction, local memory inspection, and unauthorized debugger attachments (`ptrace`/`lldb`).
- **Compiler Dead-Store Protected Zeroing:** Employs volatile C++ pointer zeroing loops (`secureZeroMemory`) to purge sensitive plaintexts, cryptographic keys, and intermediate PIN buffers from heap memory, preventing compiler optimization passes from stripping cleanup operations.

---

### 3. Lockscreen Authentication & Anti-Coercion Engine
- **Route B (Native LSPosed Hook):** Hooks `com.android.server.locksettings.LockSettingsService` directly inside `system_server`:
  - **Normal PIN:** Validates authentication, clears failed attempt counters, and unlocks the primary profile.
  - **Duress PIN (Silent Canary Trap):** Halts authentication at the framework level, renders an authentic "Wrong PIN" feedback state on Keyguard, and silently triggers covert front/back camera photo, audio, and GPS dispatch with zero screen flickering.
  - **Wipe PIN:** Terminates Keyguard authentication, destroys the discrete StrongBox suicide key, zeros File-Based Encryption metadata headers, and stages an autonomous recovery wipe.
  - **Honeypot PIN:** Authenticates the operator into an isolated, authentic secondary Android user space (`UserHandle(10)`) while purging primary user keys from volatile RAM.
  - **BFU (Before First Unlock) Persistence:** PIN definitions synchronize to `/data/system/uncleted/credentials.cfg` (SELinux context `u:object_r:system_data_file:s0`) for pre-unlock interception.
- **Route A (Device Owner / Gatekeeper Integration):**
  - Enforces failed passcode limits directly on the hardware Gatekeeper/Weaver chip.
  - Disables biometric authenticators (fingerprint/face) automatically upon 3 consecutive failures, locking Keyguard down to complex passphrases and preventing forced biometric unlock.

---

### 4. Storage Destruction Pipeline & Cryptographic Header Erasure
Uncle Ted abandons naive raw NAND flash zeroing (`dd /dev/block/sda`), which fails due to wear-leveling and overprovisioning on UFS 3.1/4.0 and NVMe storage controllers. Instead, it utilizes **sub-millisecond cryptographic erasure**:
- **16KB FBE Metadata Partition Zapping:** Overwrites the cryptographic metadata partition (`/dev/block/by-name/metadata`) with zeroes followed by immediate filesystem sync. Zeroing the master Key Encryption Key (KEK) wrappers renders all underlying File-Based Encryption data partitions mathematically unrecoverable in under 1 millisecond.
- **Vold Key Eviction:** Destroys Vold user key directories (`/data/misc/vold/user_keys/`, `/metadata/vold/user_keys/`), synthetic password blobs (`/data/system_de/0/spblob/`), and credential databases.
- **Bootloader Control Block (BCB) Staging:** Writes `--wipe_data\n--reason=UncleTed_Emergency_Sanitize` directly into `/cache/recovery/command`, ensuring that even if userspace execution halts mid-wipe, the device boots into recovery and formats userdata on the subsequent boot cycle.
- **Three Destruction Tiers:**
  1. *Level 1 - Standard Factory Reset:* Platform `MASTER_CLEAR` wipe via `RecoverySystem.rebootWipeUserData()` or BCB command staging.
  2. *Level 2 - Fast Cryptographic Shred:* Destroys StrongBox master keys, evicts Vold keys, zeroes FBE metadata headers, and commands BCB factory reset before rebooting into recovery.
  3. *Level 3 - OS Suicide (Soft Brick):* Executes Level 2 cryptographic shred, then zeroes core boot and ramdisk partitions (`boot`, `vendor_boot`, `init_boot`), rendering the device unbootable without complete firmware reflashing.

---

### 5. Physical Bus & Peripheral Defense (USB HAL v1.3+ & Radio Isolation)
- **Hardware USB Port Severing (Route A):** Calls `dpm.setUsbDataSignalingEnabled(false)` via Android 12+ USB HAL v1.3+. Physically severs the D+/D- and high-speed data signaling lines whenever the screen is locked, preventing forensic workstations (Cellebrite, GrayKey) from establishing host enumeration while permitting charging. Reinforced with `DISALLOW_USB_FILE_TRANSFER` and `DISALLOW_MOUNT_PHYSICAL_MEDIA`.
- **Kernel UDC Bus Sentinel (Route B):** Continuously inspects the Linux USB Device Controller state (`/sys/class/udc/*/state`), modern ConfigFS gadget bindings (`/config/usb_gadget/g1/`), and upstream power supply port types (SDP/CDP vs. DCP/USB-PD). If an active data host connection (`configured` state) negotiates while locked, immediate key eviction executes.
- **Instant Radio Killswitch (`RadioIsolationManager`):** Flushes and sets default `DROP` policies across all kernel `iptables` and `ip6tables` chains (`INPUT`, `OUTPUT`, `FORWARD`) in milliseconds, paired with immediate airplane mode and interface shutdowns (Wi-Fi, Bluetooth, NFC, Cellular) to emulate an active Faraday shield.

---

### 6. Multi-User RAM Anti-Forensics & Cold Vold Eviction
Standard multi-user switching (`am switch-user`) leaves the primary owner's (User 0) Credential-Encrypted (CE) keys resident in the Linux kernel keyring, exposing them to cold-boot RAM acquisition. Uncle Ted implements **true RAM anti-forensics**:
- **Cold Vold Key Eviction:** When the Honeypot PIN is entered, Uncle Ted calls in-process `StorageManagerService.lockUserKey(0)` inside `system_server` or invokes `vdc cryptfs lockuser 0` and `sm lock-user-key 0`.
- **Reversion to BFU State:** Purges User 0's CE keys from volatile RAM and drops filesystem caches (`drop_caches`), placing User 0 back into a secure **Before First Unlock (BFU)** state before the decoy session loads.
- **Authentic Multi-User Decoy:** Moves the active OS session to a genuine secondary Android user profile (`UserHandle(10)`) named `"Personal"` backed by its own `/data/user/10` directory, separate encryption keys, distinct launcher, and decoy apps (`FakeBankingActivity`, `FakeNotesActivity`, `FakeGalleryActivity`).

---

### 7. Autonomous Environmental & Dead-Man Tripwires
- **Autonomous BFU Dead-Man Sentinel (`TripwireManager`):** Operates exclusively within Device-Protected (DE) storage using `AlarmManager.setExactAndAllowWhileIdle()` configured for hardware RTC wakeup. Evaluates elapsed time directly upon `LOCKED_BOOT_COMPLETED`; if the device was seized, powered down, or isolated in a Faraday bag past the threshold, an immediate cryptographic wipe executes in BFU state.
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` bypasses confirmation dialogs and triggers emergency erasure.
- **Multi-Zone Geographic Suicide (`PolygonUtils` & `ZoneWipeService`):** High-accuracy GPS sentinel operating with a Ray-Casting Point-in-Polygon algorithm using half-open latitude intervals to eliminate boundary errors:
  - *Pre-Configured Boundary:* Built-in perimeter covering Evin Prison.
  - *Custom Wipe Zones:* User-defined circular radius boundaries or polygon perimeters configured via UI or current GPS fix.
  - *Safety Guardrails:* Rejects coordinates with an uncertainty radius $> 30\text{ m}$ and requires 3 consecutive breach samples to eliminate false positives from multipath drift.
- **SIM Hardware Sentinel:** Detects changes in the hardware identity of the SIM card across Android 9 through 14+ without throwing `SecurityException`, instantly locking the device and dispatching alert telemetry.

---

### 8. Carrier-Blind Remote Command & Control (Ed25519 Wire, OTC, SMS)
Uncle Ted implements a three-tier remote signaling engine designed to operate in BFU state without leaking operational intent to cellular carriers:
- **Mode 1: Ed25519 Cryptographic Envelope (`!UT:<Base64>`):** Compact 85-byte binary packet signed by the operator's offline asymmetric private key. Enforces a 120-second timestamp drift window and strict monotonic sequence counters to eliminate replay attacks. Carrier logs capture only high-entropy random noise:
  - `0x01` : Emergency Cryptographic Wipe & BCB Staging (`OP_EMERGENCY_WIPE`)
  - `0x02` : Sever Hardware USB Port & Lock Keyguard (`OP_SEVER_USB_AND_LOCK`)
  - `0x03` : Evict Keys to BFU State & Drop Kernel Keyring (`OP_EVICT_KEYS_TO_BFU`)
  - `0x04` : Trigger Covert Evidence Capture & Telemetry (`OP_CAPTURE_EVIDENCE`)
- **Mode 2: Single-Use Emergency Recovery Tokens (OTC):** Generates a batch of 5 high-entropy emergency recovery tokens (e.g., `!UT:OTC-W1-XXXX-XXXX-XXXX`) stored in Device-Protected storage. Texting a token from any basic phone immediately destroys all keys and permanently burns the token.
- **Mode 3: Permissive Burner Fallback (`UNCLETED [CMD] [PASSWORD]`):** Legacy command format for use with basic/analog phones when cryptographic tools are unavailable, guarded by a toggle switch in settings.
- **Automated Inbox Cleansing:** Purges incoming command SMS records from `content://sms` using elevated shell commands and ContentResolver queries to eliminate forensic traces of signaling.

---

### 9. Covert Surveillance & Multi-Modal Evidence Gathering
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` running in `RESUMED` state to capture high-resolution front- and back-camera photos, followed by video clips.
- **Android 14 BAL Compliance:** Employs a full-screen intent broker (`CameraPermissionBrokerActivity`) paired with system shell invocation (`am start`) to bypass Background Activity Launch (BAL) restrictions cleanly.
- **Hybrid Input Surveillance:** Intercepts physical hardware inputs (Volume, Power) via `/dev/input/` events (`getevent -l`) while capturing soft-keyboard typing through the Accessibility event bus.
- **Ambient Audio Surveillance:** Direct-to-disk MPEG-4 AAC audio capture (`.m4a`) using `MediaRecorder` at user-configurable recording intervals.
- **Stealth Screenshot (Root):** Directly reads surface buffers via `/system/bin/screencap` without generating UI flashes or notification badges.

---

## 📡 Remote SMS Command Reference

Send commands via SMS using one of the three supported modes:

### Mode 1: Cryptographic Envelope (Ed25519)
```text
!UT:[85_BYTE_BASE64_PAYLOAD]
```

### Mode 2: Single-Use Emergency Recovery Token (OTC)
```text
!UT:OTC-W1-[XXXX-XXXX-XXXX]
```

### Mode 3: Permissive Burner Fallback
```text
UNCLETED [COMMAND] [SMS_MASTER_PASSWORD] [OPTIONAL_ARGS]
```

| Command | Arguments | Severity | Description |
| :--- | :--- | :---: | :--- |
| `WIPE` | *None* | `CRITICAL` | Bypasses evidence collection and triggers immediate platform wipe & key eviction. |
| `EVIDENCE`| *None* | `HIGH` | Gathers front/back photos, 15s video, 30s audio, and emails the complete dossier. |
| `SIREN` | *None* | `HIGH` | Maximizes alarm streams and loops a loud emergency siren while vibrating. |
| `LOCK` | *None* | `LOW` | Immediately closes running tasks and launches the secure lockscreen activity. |
| `LOCATE` | *None* | `LOW` | Requests high-accuracy GPS coordinates and replies via SMS and email. |
| `AUDIO` | `[seconds]` | `HIGH` | Records ambient room audio for specified seconds (default 60s) and emails the file. |
| `SPEAK` | `[text]` | `MEDIUM` | Maximizes volume and reads text aloud using the device's Text-to-Speech engine. |
| `REBOOT` | *None* | `HIGH` | (Root Only) Forces an immediate hardware reboot (`/system/bin/reboot`). |
| `SCREENSHOT`| *None* | `HIGH` | (Root Only) Takes a silent screenshot of the active screen and emails it. |
| `GETLOGS`| *None* | `LOW` | (Root Only) Dumps and emails captured keylog buffers, then flushes storage. |
| `EXFIL` | `[pkg] [file]` | `HIGH` | (Root Only) Copies a file from `/data/data/[pkg]/` and attaches it via email. |

---

## 🛠️ Technology Stack

- **Languages:** 100% Modern Kotlin (Coroutines, StateFlow, Mutex) & Modern C++17 (Native NDK)
- **Target OS:** Android 14 (API 34) | **Minimum OS:** Android 9 (API 28)
- **Hardware Security Modules:** Google Titan M / Titan M2, Qualcomm SPU via StrongBox KeyMint API (`FEATURE_STRONGBOX_KEYSTORE`)
- **Native Hardening:** ARMv8.5-A Memory Tagging Extension (MTE Synchronous Mode), `prctl(PR_SET_DUMPABLE, 0)`
- **Framework Hooks:** Xposed API v82 / LSPosed Framework (Zygisk Release or JingMatrix fork)
- **Root Environments:** Magisk, KernelSU, KernelSU-Next, APatch
- **Device Owner (Non-Root):** Android Enterprise `DevicePolicyManager` with AVB 2.0 (Verified Boot)
- **Cryptography:** Bouncy Castle Ed25519 (`Ed25519Signer`), AndroidX Security Crypto (MasterKey AES-256-GCM, `DeviceProtectedStorageContext`)
- **System Privileges:** Android Privileged Permission Allowlist (`android.permission.MASTER_CLEAR`, `WRITE_SECURE_SETTINGS`, `REBOOT`, `MANAGE_USERS`, `MANAGE_USB`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video) with headless `FakeLifecycleOwner`
- **Background Architecture:** AndroidX WorkManager, Direct Boot `AlarmManager`, Native Foreground Services with BAL brokers
- **UI & Layout:** Material Design 3 Components with pure black AMOLED theme support

---

## 📂 Project Directory Structure

```text
UncleTed-main/
├── app/
│   ├── distribution/
│   │   └── etc/permissions/
│   │       └── privapp-permissions-uncleted.xml  <-- System priv-app allowlist (MASTER_CLEAR, MANAGE_USB)
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   └── xposed_init                   <-- LSPosed module entrypoint declaration
│   │   │   ├── cpp/                              <-- Native NDK Memory Hardening & MTE
│   │   │   │   ├── CMakeLists.txt                <-- Clang -march=armv8.5-a+memtag build config
│   │   │   │   └── native-security.cpp           <-- Synchronous MTE & PR_SET_DUMPABLE=0
│   │   │   ├── java/com/hamoon/uncleted/
│   │   │   │   ├── core/                         <-- Dual-Profile Routing Architecture
│   │   │   │   │   ├── DefenseCoordinator.kt     <-- Runtime capability resolver
│   │   │   │   │   ├── DefenseStrategy.kt        <-- Abstract defense strategy interface
│   │   │   │   │   └── strategies/
│   │   │   │   │       ├── DeviceOwnerStrategy.kt <-- Route A (Titan M2 / Locked AVB)
│   │   │   │   │       └── RootPrivilegedStrategy.kt <-- Route B (Root / LSPosed)
│   │   │   │   ├── crypto/                       <-- Hardware Keystore & Ed25519 Engine
│   │   │   │   │   ├── CryptoPreferences.kt      <-- DE storage cryptographic preferences
│   │   │   │   │   ├── OneTimeTokenManager.kt    <-- Emergency recovery slips (OTC)
│   │   │   │   │   ├── SecureWireValidator.kt    <-- Ed25519 85-byte binary packet verifier
│   │   │   │   │   └── StrongBoxSecurityManager.kt <-- Discrete Titan M2 master suicide key
│   │   │   │   ├── data/
│   │   │   │   │   └── SecurityPreferences.kt    <-- Dual DE/CE persistent storage manager
│   │   │   │   ├── fragments/                    <-- UI Views (Dashboard, PINs, Features, etc.)
│   │   │   │   ├── honeypot/                     <-- Decoy Launcher & Trap Activities
│   │   │   │   │   ├── FakeBankingActivity.kt    <-- Credential bait trap
│   │   │   │   │   ├── FakeGalleryActivity.kt    <-- Photo bait trap
│   │   │   │   │   ├── FakeNotesActivity.kt      <-- Note bait trap
│   │   │   │   │   └── HoneypotLauncherActivity.kt <-- Decoy launcher screen
│   │   │   │   ├── hooks/
│   │   │   │   │   └── LockscreenHook.kt         <-- Core system_server LSPosed hook
│   │   │   │   ├── receivers/                    <-- Boot, SMS, Admin, & Duress Receivers
│   │   │   │   │   ├── AdminReceiver.kt          <-- Gatekeeper & DevicePolicyManager receiver
│   │   │   │   │   ├── BootCompletedReceiver.kt  <-- Early BFU Direct Boot scheduler
│   │   │   │   │   ├── DuressHookReceiver.kt     <-- Duress & Honeypot broadcast receiver
│   │   │   │   │   ├── NetworkStateReceiver.kt   <-- Connectivity change tripwire reset
│   │   │   │   │   ├── SecretCodeReceiver.kt     <-- Dialer launch receiver (*#*#CODE#*#*)
│   │   │   │   │   ├── SimChangeReceiver.kt      <-- Hardware SIM swap sentinel
│   │   │   │   │   ├── SmsCommandReceiver.kt     <-- Multi-Modal SMS Dispatcher & Purger
│   │   │   │   │   ├── TripwireReceiver.kt       <-- Direct Boot AlarmManager tripwire receiver
│   │   │   │   │   └── WidgetActionReceiver.kt   <-- Lockscreen widget control receiver
│   │   │   │   ├── services/
│   │   │   │   │   ├── MonitoringService.kt      <-- Core sensor sentinel service
│   │   │   │   │   ├── PanicActionService.kt     <-- Emergency dispatch orchestrator
│   │   │   │   │   ├── PowerButtonService.kt     <-- Input filtering & hardware volume monitor
│   │   │   │   │   ├── UsbTripwireService.kt     <-- Kernel UDC SysFS data line tripwire
│   │   │   │   │   └── ZoneWipeService.kt        <-- Multi-zone perimeter geofence suicide service
│   │   │   │   ├── util/
│   │   │   │   │   ├── AdvancedCameraHandler.kt  <-- Dual camera photo/video capture pipeline
│   │   │   │   │   ├── AdvancedCrypto.kt         <-- StrongBox-routed crypto & salt hashing
│   │   │   │   │   ├── AudioRecorder.kt          <-- Ambient AAC (.m4a) audio recorder
│   │   │   │   │   ├── CredentialBridge.kt       <-- Cross-process BFU platform bridge
│   │   │   │   │   ├── DecoyUserManager.kt       <-- Android Multi-User & Vold key eviction
│   │   │   │   │   ├── DeviceAdminHelper.kt      <-- Asynchronous strategy wipe invoker
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- 16KB metadata zeroing & BCB engine
│   │   │   │   │   ├── Keylogger.kt              <-- Hardware & soft-keyboard logger
│   │   │   │   │   ├── NativeSecurityBridge.kt   <-- JNI link to libuncleted_native.so
│   │   │   │   │   ├── PolygonUtils.kt           <-- Ray-Casting algorithm & zone serializer
│   │   │   │   │   ├── RadioIsolationManager.kt  <-- Kernel iptables DROP & radio killswitch
│   │   │   │   │   ├── RootActions.kt            <-- Universal Magisk/KernelSU/APatch commands
│   │   │   │   │   ├── RootChecker.kt            <-- Universal root provider detector
│   │   │   │   │   ├── TripwireManager.kt        <-- Hardware RTC AlarmManager tripwire manager
│   │   │   │   │   └── UsbDetector.kt            <-- Linux UDC gadget & SDP/CDP analyzer
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog)
│   │   │   ├── AndroidManifest.xml               <-- memtagMode="sync" & permissions
│   │   │   ├── CameraPermissionBrokerActivity.kt <-- Android 14 BAL permission broker
│   │   │   ├── LockScreenActivity.kt             <-- Hardened in-app lockscreen
│   │   │   ├── MainActivity.kt                   <-- Main UI dashboard
│   │   │   └── UncleTedApplication.kt            <-- Runtime MTE & sandboxing initializer
│   │   └── build.gradle.kts                      <-- Version 5.0.1, NDK CMake MTE flags
│   └── proguard-rules.pro                        <-- Native bridge & StrongBox rule preservation
└── settings.gradle.kts
```

---

## 🚀 Deployment & Installation Guide

### Prerequisites
- **For Route A (Device Owner Mode):** An Android device running Android 9 through 14+ with a locked bootloader, freshly factory reset (containing zero Google or user accounts).
- **For Route B (Privileged Root & Hook Mode):** A rooted Android device running Android 9 through 14+ (rooted via **Magisk**, **KernelSU**, **KernelSU-Next**, or **APatch**) with **LSPosed** installed and operational.

---

### Phase 1: Acquire the Release Artifacts
Download the pre-built release artifacts directly from the **[GitHub Releases](https://github.com/HamoonSoleimani/UncleTed/releases)** page:
- **`UncleTed-v5.0.1.apk`** (For Route A: Device Owner provisioning or direct installation).
- **`UncleTed-PrivApp-v5.0.1.zip`** (For Route B: Magisk / KernelSU / APatch flashable module).

---

### Phase 2: Deployment Selection

#### Route A: Provision as Device Owner (Locked Bootloader / AVB Enforced)
*Recommended for defense against physical seizure, forensic workstations, and hardware-level exploitation.*

1. Factory reset your device. Boot to the welcome screen, select your language, but **do not connect to Wi-Fi and do not add any accounts**.
2. Tap `Build Number` 7 times in `Settings -> About Phone` to enable Developer Options.
3. Enable **USB Debugging** in `Settings -> System -> Developer Options`.
4. Connect the phone to your computer via USB.
5. Install the APK and assign Device Owner status via ADB:
   ```bash
   adb install -r -d -g UncleTed-v5.0.1.apk
   adb shell dpm set-device-owner com.hamoon.uncleted/.receivers.AdminReceiver
   ```
6. Revoke USB Debugging and disable Developer Options in Settings:
   ```bash
   adb shell settings put global adb_enabled 0
   adb shell settings put global development_settings_enabled 0
   ```
7. Disconnect the USB cable. UncleTed now exercises exclusive hardware policy authority over the device with AVB 2.0 fully enforcing.

#### Route B: Flashing the Systemless Module (.zip) for Root & LSPosed
*Recommended for native lockscreen PIN interception, cold Vold user key eviction, and covert surveillance.*

1. Transfer `UncleTed-PrivApp-v5.0.1.zip` to your device's internal storage:
   ```bash
   adb push UncleTed-PrivApp-v5.0.1.zip /sdcard/
   ```
2. Open **Magisk**, **KernelSU**, or **APatch Manager**.
3. Navigate to the **Modules** tab.
4. Tap **Install from storage**, select `UncleTed-PrivApp-v5.0.1.zip`, and allow the installer script to run.
5. Tap **Reboot**.

---

### Phase 3: Activating the LSPosed Hook (Route B Only)
1. Once the phone reboots, open the **LSPosed Manager** app.
2. Navigate to the **Modules** tab.
3. Tap **UncleTed System Priv-App & Hook**.
4. Toggle **Enable Module** to **ON**.
5. Ensure the hook scope includes:
   - `System Framework` (`android`)
   - `System UI` (`com.android.systemui`)
6. **Reboot your device once more** so `system_server` loads the native Keyguard hook during early initialization.

---

### Phase 4: Configuring & Arming Credentials
1. Open **UncleTed** on your device.
2. Complete the permission authorizations in the **Core Services** tab.
3. Open the **Authentication** tab:
   - Set a **Normal Unlock PIN** (e.g., `1111`).
   - Set a **Duress (Panic) PIN** (e.g., `2222`).
   - Set a **Wipe Lock PIN** (e.g., `9999`).
   - Set a **Honeypot PIN** (e.g., `5555`).
4. Tap **Save PINs**.
5. Open the **Remote Control** tab:
   - Paste your **Operator Ed25519 Public Key** (Base64) to enable cryptographic remote signaling.
   - Tap **Generate Emergency Wallet Sheet (5 OTC)** and write down or print the single-use recovery tokens.
   - Configure your **Emergency Contact** (Email or Phone Number).

---

## 📄 Verification & Diagnostic Checklist

Run these commands via ADB to confirm that operational layers are properly armed:

#### 1. Confirm Active Defense Strategy
```bash
adb shell dumpsys package com.hamoon.uncleted | grep -E "Device Owner|isDeviceOwner"
```
*Expected output (Route A):*
```text
Device Owner: admin=ComponentInfo{com.hamoon.uncleted/com.hamoon.uncleted.receivers.AdminReceiver}
```

#### 2. Verify Hardware Memory Tagging Extension (ARM MTE)
Verify that synchronous MTE memory tagging is active for Uncle Ted:
```bash
adb shell cat /proc/$(adb shell pidof com.hamoon.uncleted)/status | grep -i "mte"
```
*Expected output:* Shows tagged address control flags active (`PR_MTE_TCF_SYNC`).

#### 3. Confirm Discrete Titan M2 / StrongBox Master Key
Inspect logcat during app initialization:
```bash
adb logcat -s "StrongBoxSecManager" "UncleTedApplication"
```
*Expected output:*
```text
StrongBoxSecManager: Initializing Master Suicide Key (StrongBox Supported: true)...
StrongBoxSecManager: Hardware master key successfully provisioned inside discrete HSM.
UncleTedApplication: Native runtime memory defenses armed (Success: true).
```

#### 4. Confirm Hardware Gatekeeper Brute-Force Limits (Route A)
```bash
adb shell dumpsys device_policy | grep -i "failedPasswordAttempts"
```
*Expected output:* Shows maximum failed passwords before wipe set to `3`.

#### 5. Verify Direct-Boot Credential Synchronization
Confirm that the platform bridge file is present in Device-Protected space and populated:
```bash
adb shell su -c "cat /data/system/uncleted/credentials.cfg"
```
*Expected output (Route B):*
```text
wipe_pin=[YOUR_WIPE_PIN]
duress_pin=[YOUR_DURESS_PIN]
honeypot_pin=[YOUR_HONEYPOT_PIN]
decoy_user_id=10
updated_at=[TIMESTAMP]
```

#### 6. Monitor Honeypot RAM Keyring Eviction (Route B)
Lock the device screen. Open an active logcat monitor:
```bash
adb logcat -s "UncleTed-LockHook" "DecoyUserManager"
```
Enter your **Honeypot PIN** on the native Keyguard keypad:
```text
UncleTed-LockHook: HONEYPOT PIN matched at OS level! Initiating surveillance before session migration...
UncleTed-LockHook: lockUserKey(0) invoked successfully via in-process StorageManager.
DecoyUserManager: Primary user CE encryption keys evicted from kernel keyring. User 0 is now in BFU state.
```

#### 7. Test Cryptographic SMS Signal Verification
Send an Ed25519-signed binary packet to the device and monitor the receiver:
```bash
adb logcat -s "SmsCommandReceiver" "TripwireReceiver" "DestructionEngine"
```
*Expected output:*
```text
SmsCommandReceiver: ED25519 SIGNATURE VERIFIED: OpCode=1, Seq=42
DestructionEngine: !!! INITIATING SUB-MILLISECOND EMERGENCY DESTRUCTION: OP_ED25519_WIPE !!!
StrongBoxSecManager: !!! INITIATING TITAN M2 / STRONGBOX CRYPTOGRAPHIC SUICIDE !!!
DestructionEngine: Zeroing master FBE metadata partition header at: /dev/block/by-name/metadata
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).