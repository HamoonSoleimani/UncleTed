<p align="center">
  <img src="https://hamoon.net/wp-content/uploads/2025/09/logo-transparent.png" alt="Uncle Ted Logo" width="180">
</p>

<h1 align="center">Uncle Ted for Android</h1>

<p align="center">
  <strong>A cabin in the digital woods.</strong><br>
  An advanced, system-level personal security, anti-coercion, and anti-forensic defense suite for Android.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Version-v4.0.1-brightgreen.svg" alt="Version">
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg" alt="Target SDK">
  <img src="https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Framework-LSPosed%20%2F%20Xposed-red.svg" alt="LSPosed">
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
  - [1. Lockscreen Authentication & Anti-Coercion](#1-lockscreen-authentication--anti-coercion)
  - [2. Storage Architecture & Emergency Destruction Pipeline](#2-storage-architecture--emergency-destruction-pipeline)
  - [3. Hardware & Environmental Tripwires](#3-hardware--environmental-tripwires)
  - [4. Native Multi-User Honeypot Decoy Space](#4-native-multi-user-honeypot-decoy-space)
  - [5. Covert Surveillance & Multi-Modal Evidence Gathering](#5-covert-surveillance--multi-modal-evidence-gathering)
  - [6. Multi-Modal Remote Command & Control (Ed25519, OTC, SMS)](#6-multi-modal-remote-command--control-ed25519-otc-sms)
  - [7. Hardware-Backed Direct Boot & Platform Integrity](#7-hardware-backed-direct-boot--platform-integrity)
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
> **Uncle Ted is engineered strictly for educational, defensive security, personal privacy, and human rights protection in high-risk scenarios.** 
> Features such as cryptographic key eviction, hardware Secure Element key revocation, block storage zeroing, and partition destruction carry permanent, irreversible consequences. Overwriting low-level block storage can permanently render hardware inoperable (hard brick).
> **Never install or deploy this software on any device without explicit authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the use or misuse of this codebase.

---

## 📊 Architectural Comparison: Deployment Profiles

On modern Android (Android 9 through 14+), Google's security model strictly restricts standard userspace applications. Uncle Ted operates across two privileged architectural routes: **Route A (Non-Root Enterprise Device Owner with Locked Bootloader & AVB 2.0)** and **Route B (Systemless Priv-App with native LSPosed hooks in `system_server`)**.

| Security Vector / Feature | Standalone APK (Stock OS / Sideloaded) | Non-Root + Device Owner via ADB (Route A: AVB Locked) | Flashed Systemless ZIP + LSPosed (Route B: Privileged Root) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Bootloader & AVB State** | 🟢 Locked (AVB Enforcing) | 🟢 **100% Locked (AVB Enforcing)** | 🔴 Unlocked (dm-verity unverified) | Route A retains hardware chain of trust; Route B requires unlocked bootloader for kernel root and Zygisk. |
| **Lockscreen PIN Interception** | 🔴 Non-Functional | 🟡 **Fail Callback / In-App Guard** | 🟢 **100% Native Hook** | Userspace apps cannot read Keyguard keystrokes. Route B hooks `LockSettingsService` directly inside `system_server`. Route A uses Gatekeeper callbacks. |
| **Silent Emergency Wipe Authority** | 🔴 Prompts / Deprecated | 🟢 **Sub-Second Hardware SE Wipe** | 🟢 **Vold Key Shredding & BCB Wipe** | Route A commands Weaver/KeyMint/Titan M2 to zero root key blobs via `dpm.wipeData()`. Route B evicts `/data/misc/vold/user_keys/` and metadata headers. |
| **Hardware Brute-Force Wipe Enforcement** | 🔴 Non-Functional (App counter) | 🟢 **Hardware-Enforced Gatekeeper** | 🟢 **system_server Debounced Hook** | Route A configures `dpm.setMaximumFailedPasswordsForWipe()`, triggering kernel/SE wipe on failure. Route B debounces attempts in `system_server`. |
| **Physical USB Extraction Defense** | 🔴 Impossible | 🟢 **Hardware Port Disconnection** | 🟢 **Kernel UDC Monitor & Kill** | Route A invokes `dpm.setUsbDataSignalingEnabled(false)` to physically sever D+/D- lines. Route B disconnects Linux UDC gadget controllers via SysFS. |
| **Before First Unlock (BFU) Execution** | 🔴 Disabled (Crashes) | 🟢 **Fully Operational (Direct Boot)** | 🟢 **Fully Operational (Direct Boot)** | CE storage is unreadable at boot. Both routes utilize `DeviceProtectedStorageContext` (DE) storage paired with Direct Boot aware receivers. |
| **Autonomous Dead-Man Tripwire** | 🔴 Broken (CE WorkManager) | 🟢 **Hardware RTC Alarm in BFU** | 🟢 **Hardware RTC Alarm in BFU** | Employs `AlarmManager.setExactAndAllowWhileIdle()` backed by DE storage; triggers wipe even if seized into a Faraday bag during cold boot. |
| **Remote Signaling Authentication** | 🔴 Cleartext SMS (Telco leaks) | 🟢 **Ed25519 Binary Wire + OTC** | 🟢 **Ed25519 Binary Wire + OTC** | Verifies 85-byte asymmetric Ed25519 signatures with strict timestamp windows and monotonic counters; supports single-use emergency wallet tokens. |
| **Bypass via Safe Mode** | 🔴 Trivially Bypassed | 🟢 **Immune** | 🟢 **Immune** | Safe Mode disables standard third-party apps. Device Owner policies and privileged apps mounted under `/system/priv-app/` remain active. |
| **Bypass via Task Killer / ADB** | 🔴 Vulnerable (`am force-stop`) | 🟢 **Protected** | 🟢 **Protected** | Device Owner cannot be uninstalled or force-stopped without a factory reset. Route B native lockscreen hook executes inside unkillable `system_server`. |
| **Covert Honeypot Decoy Space** | 🟡 In-App Activity Trap | 🟡 Isolated Work Profile (DO) | 🟢 **Native Multi-User (`UserHandle(10)`)** | Route B dynamically switches active OS sessions via `am.switchUser(10)`. Route A manages isolated, cryptographically segregated managed profiles. |
| **Overall Defense Capability** | **3.5 / 10** | **9.2 / 10** | **9.8 / 10** | Sideloaded APKs provide a false sense of security. Route A provides maximum physical anti-forensics with locked AVB; Route B provides maximum OS control. |

---

## 🏗️ System Architecture (Dual-Profile Engine)

Uncle Ted utilizes a centralized dynamic routing coordinator (`DefenseCoordinator`) that resolves hardware capabilities at startup, dispatching actions to the appropriate platform defense strategy:

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
 │ ROUTE A: DEVICE OWNER (AVB LOCKED)              │   │ ROUTE B: PRIVILEGED ROOT / LSPOSED (UNLOCKED)   │
 │ - 100% Locked Bootloader (dm-verity Enforcing)  │   │ - Unlocked Bootloader (Magisk / KernelSU / AP)  │
 │ - Hardware Weaver / Titan M2 Key Revocation     │   │ - Hooks LockSettingsService in system_server    │
 │ - Native setUsbDataSignalingEnabled(false) HAL  │   │ - Low-level Vold User Key Shredding             │
 │ - Gatekeeper setMaximumFailedPasswordsForWipe   │   │ - Native Multi-User Honeypot Switch (User 10)   │
 │ - Direct Boot DE Storage Inactivity Dead-Man    │   │ - Linux UDC Gadget Controller Nullification     │
 │ - Ed25519 Cryptographic Binary Envelope Verifier│   │ - Emergency Level 4 Master GPT / Block Erasure  │
 └─────────────────────────────────────────────────┘   └─────────────────────────────────────────────────┘
```

---

## ✨ Core Capabilities

### 1. Lockscreen Authentication & Anti-Coercion
- **Route B (Native LSPosed Hook):** Hooks directly into AOSP's core `com.android.server.locksettings.LockSettingsService` inside `system_server`:
  - **Normal PIN:** Standard device unlock; resets failed attempt counters.
  - **Duress PIN (Silent Canary Trap):** Aborts authentication at the OS level, displays a realistic "Wrong PIN" feedback on Keyguard, and silently triggers covert front/back camera photo, audio, and GPS capture with zero UI flickers.
  - **Wipe PIN:** Halts Keyguard authentication, evicts cryptographic keys from RAM, clears metadata headers, and triggers immediate factory data destruction.
  - **Honeypot PIN:** Unlocks the device by dynamically migrating the active OS session to an authentic, isolated secondary Android user profile (`UserHandle(10)`).
  - **BFU (Before First Unlock) Persistence:** PINs synchronize to `/data/system/uncleted/credentials.cfg` (SELinux context `u:object_r:system_data_file:s0`) for pre-unlock interception.
- **Route A (Device Owner / Gatekeeper Integration):**
  - Enforces failed passcode limits directly on the hardware Gatekeeper/Weaver chip via `dpm.setMaximumFailedPasswordsForWipe(admin, 5)`.
  - Automatically disables biometric authenticators (fingerprint/face) upon 3 consecutive failures, requiring a complex passphrase and preventing forced biometric unlock.

---

### 2. Storage Architecture & Emergency Destruction Pipeline
Uncle Ted integrates a unified destruction engine (`EmergencyDestructionEngine`) that separates platform cryptographic wipes from low-level block zeroing:
- **Instant Radio Killswitch:** Flushes and sets default drop policies across all `iptables` and `ip6tables` chains (`INPUT`, `OUTPUT`, `FORWARD`) within milliseconds to stop remote aborts or forensic sniffing.
- **Hardware-Backed Cryptographic Erasure (Route A):** Invokes `dpm.wipeData()` with `WIPE_SILENTLY`, commanding the Secure Element to permanently revoke root Key Encryption Keys (KEKs). All File-Based Encryption partitions become unrecoverable in sub-second time.
- **Cryptographic Key Eviction (Route B):** Destroys Vold user keys (`/data/misc/vold/user_keys/` and `/metadata/vold/`), synthetic password security blobs (`/data/system_de/0/spblob/`), and metadata headers.
- **Four Destruction Tiers:**
  1. *Level 1 - Standard Factory Reset:* Platform `MASTER_CLEAR` wipe via `RecoverySystem.rebootWipeUserData()` or Bootloader Control Block (BCB) `--wipe_data` staging under `/cache/recovery/command`.
  2. *Level 2 - Secure Data Shred:* Zeros cryptographic metadata headers, clears Vold user keys, and stages BCB reformatting before cleanly rebooting into recovery.
  3. *Level 3 - OS Suicide (Soft Brick):* Overwrites kernel and ramdisk partitions (`boot`, `vendor_boot`, `init_boot`) with zeros, rendering the device unbootable without firmware reflashing.
  4. *Level 4 - Nuclear Winter (Hard Brick):* Identifies physical disk controllers (`/dev/block/sda` on UFS, `/dev/block/mmcblk0` on eMMC) and zeroes master GUID Partition Tables (GPT) before triggering an immediate kernel panic (`sysrq-trigger`).

---

### 3. Hardware & Environmental Tripwires
- **Hardware USB Port Disconnection (Route A):** Leverages Android 12+ USB HAL v1.3+ via `dpm.setUsbDataSignalingEnabled(false)`. Physically severs the D+/D- data lines whenever the screen is locked, preventing forensic workstations (Cellebrite, GrayKey) from establishing data communication while allowing charging.
- **Kernel-Level USB Tripwire (Route B):** Continuously monitors the Linux USB Device Controller state (`/sys/class/udc/*/state`). If an active USB data host connection (`configured` state) is negotiated while locked, immediate key eviction is executed.
- **Autonomous BFU Dead-Man Tripwire:** Managed via `AlarmManager.setExactAndAllowWhileIdle()` operating exclusively in Device-Protected (DE) storage. Evaluates time limits directly upon `LOCKED_BOOT_COMPLETED` even if the device was seized, powered down, or isolated in a Faraday bag past the expiration deadline.
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` triggers emergency erasure.
- **Multi-Zone Geographic Suicide:** Monitors coordinates using a Ray-Casting Polygon and Haversine distance algorithm in `PolygonUtils`:
  - *Evin Prison Perimeter:* Built-in perimeter boundary covering the facility.
  - *User-Defined Wipe Zones:* Add custom circular radius zones or polygon perimeters directly from the UI or current GPS fix.
  - *Safety Guardrails:* Enforces a maximum uncertainty radius of $\le 30\text{ m}$ and requires 3 consecutive breach samples to eliminate false positives from GPS drift.
- **SIM Card Swap Sentinel:** Detects changes in the hardware identity of the SIM card across Android 9 through 14+ without throwing `SecurityException`, instantly locking the device and dispatching alert telemetry.

---

### 4. Native Multi-User Honeypot Decoy Space
Rather than relying on fake in-app launchers that can be bypassed with gesture navigation, Uncle Ted leverages **Android's native Multi-User subsystem (`UserManager`)**:
- **Genuine Secondary Profile:** Creates an authentic, isolated secondary Android user space (`UserHandle(10)`) named `"Personal"` backed by its own independent `/data/user/10` directory.
- **Separate Encryption Keys & Launcher:** The decoy space uses its own launcher, settings, accounts, and clean app drawer.
- **Instant Keyguard Transition:** Entering the **Honeypot PIN** on the primary lockscreen intercepts `LockSettingsService`, halts authentication on User 0 (leaving real data encrypted), and immediately invokes `ActivityManager.switchUser(10)` to unlock directly into the decoy environment.
- **In-App Honeypot Trap Suite:** Includes decoys (`FakeBankingActivity`, `FakeNotesActivity`, `FakeGalleryActivity`) that log intruder interactions and dispatch alerts.

---

### 5. Covert Surveillance & Multi-Modal Evidence Gathering
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` running in `RESUMED` state to capture high-resolution front- and back-camera photos, followed by video clips.
- **Android 14 Background Launch Compliance:** Employs a full-screen intent broker (`CameraPermissionBrokerActivity`) paired with system shell invocation (`am start`) to bypass Background Activity Launch (BAL) restrictions cleanly.
- **Hybrid Input Surveillance:** Intercepts physical hardware inputs (Volume, Power) via `/dev/input/` events (`getevent -l`) while capturing soft-keyboard typing through the Accessibility event bus.
- **Ambient Audio Surveillance:** Direct-to-disk MPEG-4 AAC audio capture (`.m4a`) using `MediaRecorder` at user-configurable recording intervals.
- **Stealth Screenshot (Root):** Directly reads surface buffers via `/system/bin/screencap` without generating UI flashes or notification badges.

---

### 6. Multi-Modal Remote Command & Control (Ed25519, OTC, SMS)
Uncle Ted features a multi-tiered remote signaling engine designed to operate in Before First Unlock (BFU) state without leaking operational intent to cellular carriers:
- **Mode 1: Ed25519 Cryptographic Envelope (`!UT:<Base64>`):** Compact 85-byte binary packet signed by the operator's offline asymmetric private key. Enforces a 120-second timestamp drift window and strict monotonic sequence counters to eliminate replay attacks. Carrier logs capture only high-entropy noise.
- **Mode 2: Single-Use Emergency Recovery Tokens (OTC):** Generates a batch of 5 high-entropy emergency recovery tokens (e.g., `!UT:OTC-W1-XXXX-XXXX-XXXX`) stored in Device-Protected storage. Texting a token from any basic phone immediately destroys all keys and permanently burns the token.
- **Mode 3: Permissive Burner Fallback (`UNCLETED [CMD] [PASSWORD]`):** Legacy command format for use with basic/analog phones when cryptographic tools are unavailable, guarded by a toggle switch in settings.
- **Inbox Cleansing:** Automatically purges incoming command SMS messages from the telephony content provider (`content://sms`) using elevated shell privileges.

---

### 7. Hardware-Backed Direct Boot & Platform Integrity
- **Dual-Profile Deployment:** Dynamically routes between **Route A** (Device Owner mode on locked bootloader with AVB) and **Route B** (Root/LSPosed privileged hook mode).
- **Direct Boot (BFU) Operation:** Stores critical operational flags, tripwire states, Ed25519 public keys, and platform bridge files in Device-Protected (DE) storage (`createDeviceProtectedStorageContext()`), allowing defense services to execute before the initial PIN unlock.
- **SELinux Compliance:** Ensures platform bridge files and systemless priv-app directories enforce strict SELinux contexts (`u:object_r:system_file:s0` and `u:object_r:system_data_file:s0`) to prevent bootloops on modern Android builds.

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

- **Language:** 100% Modern Kotlin (Coroutines, Flow, StateFlow, Mutex)
- **Target OS:** Android 14 (API 34) | **Minimum OS:** Android 9 (API 28)
- **Framework Hooks:** Xposed API v82 / LSPosed Framework (Zygisk Release or JingMatrix fork)
- **Root Environments:** Magisk, KernelSU, KernelSU-Next, APatch
- **Device Owner (Non-Root):** Android Enterprise `DevicePolicyManager` with AVB 2.0
- **Cryptography:** Bouncy Castle Ed25519 (`Ed25519Signer`), AndroidX Security Crypto (MasterKey AES-256-GCM, `DeviceProtectedStorageContext`)
- **System Privileges:** Android Privileged Permission Allowlist (`android.permission.MASTER_CLEAR`, `WRITE_SECURE_SETTINGS`, `REBOOT`, `MANAGE_USERS`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video)
- **Background Architecture:** AndroidX WorkManager, Direct Boot `AlarmManager`, Foreground Services
- **UI & Layout:** Google Material Design 3 Components with ViewBinding

---

## 📂 Project Directory Structure

```text
UncleTed-main/
├── app/
│   ├── distribution/
│   │   └── etc/permissions/
│   │       └── privapp-permissions-uncleted.xml  <-- System priv-app allowlist (MASTER_CLEAR)
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   └── xposed_init                   <-- LSPosed module entrypoint declaration
│   │   │   ├── java/com/hamoon/uncleted/
│   │   │   │   ├── core/                         <-- Dual-Profile Routing Architecture
│   │   │   │   │   ├── DefenseCoordinator.kt     <-- Runtime capability resolver
│   │   │   │   │   ├── DefenseStrategy.kt        <-- Abstract strategy interface
│   │   │   │   │   └── strategies/
│   │   │   │   │       ├── DeviceOwnerStrategy.kt <-- Route A (Locked bootloader/AVB)
│   │   │   │   │       └── RootPrivilegedStrategy.kt <-- Route B (Root/LSPosed)
│   │   │   │   ├── crypto/                       <-- Ed25519 & Single-Use Token Engine
│   │   │   │   │   ├── CryptoPreferences.kt      <-- DE storage cryptographic keys
│   │   │   │   │   ├── OneTimeTokenManager.kt    <-- Emergency recovery slips (OTC)
│   │   │   │   │   └── SecureWireValidator.kt    <-- Ed25519 85-byte binary packet verifier
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
│   │   │   │   │   ├── SmsCommandReceiver.kt     <-- Multi-Modal SMS Dispatcher
│   │   │   │   │   └── TripwireReceiver.kt       <-- Direct Boot AlarmManager tripwire receiver
│   │   │   │   ├── services/
│   │   │   │   │   ├── MonitoringService.kt      <-- Core sentinel service
│   │   │   │   │   ├── PanicActionService.kt     <-- Emergency dispatch orchestrator
│   │   │   │   │   ├── PowerButtonService.kt     <-- Input filtering & sequence monitor
│   │   │   │   │   ├── UsbTripwireService.kt     <-- Kernel UDC SysFS data line tripwire
│   │   │   │   │   └── ZoneWipeService.kt        <-- Multi-zone perimeter geofence suicide service
│   │   │   │   ├── util/
│   │   │   │   │   ├── AdvancedCameraHandler.kt  <-- Dual camera photo/video capture pipeline
│   │   │   │   │   ├── AudioRecorder.kt          <-- Ambient AAC (.m4a) audio recorder
│   │   │   │   │   ├── CredentialBridge.kt       <-- Cross-process BFU platform bridge
│   │   │   │   │   ├── DecoyUserManager.kt       <-- Native Android Multi-User manager
│   │   │   │   │   ├── DeviceAdminHelper.kt      <-- Direct strategy wipe invoker
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- Low-level key eviction & BCB engine
│   │   │   │   │   ├── Keylogger.kt              <-- Hardware & soft-keyboard logger
│   │   │   │   │   ├── PolygonUtils.kt           <-- Ray-Casting algorithm & zone serializer
│   │   │   │   │   ├── RootActions.kt            <-- Universal Magisk/KernelSU/APatch commands
│   │   │   │   │   ├── RootChecker.kt            <-- Universal root provider detector
│   │   │   │   │   ├── TripwireManager.kt        <-- Hardware RTC AlarmManager tripwire manager
│   │   │   │   │   └── UsbDetector.kt            <-- Linux UDC gadget state analyzer
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog)
│   │   │   ├── AndroidManifest.xml
│   │   │   └── CameraPermissionBrokerActivity.kt <-- Android 14 BAL permission broker
│   │   └── build.gradle.kts
│   └── proguard-rules.pro
└── settings.gradle.kts
```

---

## 🚀 Deployment & Installation Guide

### Prerequisites
- **For Route A (Device Owner Mode):** An Android device running Android 9 through 14+ with a locked bootloader, freshly factory reset (or containing zero user accounts).
- **For Route B (Privileged Root & Hook Mode):** A rooted Android device running Android 9 through 14+ (rooted via **Magisk**, **KernelSU**, **KernelSU-Next**, or **APatch**) with **LSPosed** installed and verified operational.

---

### Phase 1: Acquire the Release Artifacts
Download the pre-built release artifacts directly from the **[GitHub Releases](https://github.com/HamoonSoleimani/UncleTed/releases)** page:
- **`UncleTed-v4.0.1.apk`** (For Route A: Device Owner provisioning or direct installation).
- **`UncleTed-PrivApp-v4.0.1.zip`** (For Route B: Magisk / KernelSU / APatch flashable module).

---

### Phase 2: Deployment Selection

#### Route A: Provision as Device Owner (Locked Bootloader / AVB Enforced)
*Recommended for high-risk defense against physical seizure and forensic workstations.*

1. Factory reset your device. Boot to the welcome screen, select your language, but **do not connect to Wi-Fi and do not add any accounts**.
2. Tap `Build Number` 7 times in `Settings -> About Phone` to enable Developer Options.
3. Enable **USB Debugging** in `Settings -> System -> Developer Options`.
4. Connect the phone to your computer via USB.
5. Install the APK and assign Device Owner status via ADB:
   ```bash
   adb install -r -d -g UncleTed-v4.0.1.apk
   adb shell dpm set-device-owner com.hamoon.uncleted/.receivers.AdminReceiver
   ```
6. Revoke USB Debugging and disable Developer Options in Settings:
   ```bash
   adb shell settings put global adb_enabled 0
   adb shell settings put global development_settings_enabled 0
   ```
7. Disconnect the USB cable. UncleTed now exercises exclusive hardware policy authority over the device with AVB 2.0 fully enforcing.

#### Route B: Flashing the Systemless Module (.zip) for Root & LSPosed
*Recommended for full lockscreen PIN interception, native honeypot user switching, and covert surveillance.*

1. Transfer `UncleTed-PrivApp-v4.0.1.zip` to your device's internal storage:
   ```bash
   adb push UncleTed-PrivApp-v4.0.1.zip /sdcard/
   ```
2. Open **Magisk**, **KernelSU**, or **APatch Manager**.
3. Navigate to the **Modules** tab.
4. Tap **Install from storage**, select `UncleTed-PrivApp-v4.0.1.zip`, and allow the installer script to run.
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

#### 2. Confirm Privileged System-App Placement (Route B)
```bash
adb shell pm path com.hamoon.uncleted
```
*Expected output (Route B):*
```text
package:/system/priv-app/UncleTed/UncleTed.apk
```

#### 3. Verify Hardware Gatekeeper Brute-Force Limits (Route A)
```bash
adb shell dumpsys device_policy | grep -i "failedPasswordAttempts"
```
*Expected output:* Shows maximum failed passwords before wipe set to `5`.

#### 4. Verify Direct-Boot Credential Synchronization
Confirm that the platform bridge file is present in Device-Encrypted space and populated:
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

#### 5. Monitor Live Lockscreen Hook Interception (Route B)
Lock the device screen. Open an active logcat monitor:
```bash
adb logcat -s "UncleTed-LockHook" "PanicActionService"
```
Enter your **Duress PIN** on the native Keyguard keypad:
```text
UncleTed-LockHook: Credential verification intercepted: [length=4]
UncleTed-LockHook: DURESS PIN matched at OS level. Rejecting unlock & dispatching silent duress broadcast.
PanicActionService: Service executing protocol for: DURESS_PIN_LOCKSCREEN (Severity: HIGH)
```

#### 6. Test Cryptographic SMS Signal Verification
Send an Ed25519-signed packet to the device and monitor the receiver:
```bash
adb logcat -s "SmsCommandReceiver" "TripwireReceiver"
```
*Expected output:*
```text
SmsCommandReceiver: ED25519 SIGNATURE VERIFIED: OpCode=1, Seq=42
DestructionEngine: !!! INITIATING EMERGENCY DESTRUCTION SEQUENCE: OP_ED25519_WIPE !!!
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).