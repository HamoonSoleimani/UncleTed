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
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-blueviolet.svg" alt="Made with Kotlin">
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg" alt="Target SDK">
  <img src="https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Framework-LSPosed%20%2F%20Xposed-red.svg" alt="LSPosed">
  <img src="https://img.shields.io/badge/Root-Magisk%20%2F%20KernelSU%20%2F%20APatch-purple.svg" alt="Root">
</p>

<p align="center">
  <img width="829" height="601" alt="Uncle Ted Architecture Overview" src="https://github.com/user-attachments/assets/cef2fb8b-4fdd-40f5-9778-c89e2f4a9825" />
</p>

---

### **Table of Contents**
- [⚠️ Legal & Ethical Disclaimer](#️-legal--ethical-disclaimer)
- [📊 Architectural Comparison: Standalone APK vs. Flashed System Module](#-architectural-comparison-standalone-apk-vs-flashed-system-module)
- [🏗️ System Architecture (The 3-Tier Model)](#️-system-architecture-the-3-tier-model)
- [🖼️ Interface & Architecture Preview](#️-interface--architecture-preview)
- [✨ Core Capabilities](#-core-capabilities)
  - [1. Lockscreen Authentication & Anti-Coercion (LSPosed Native Hooks)](#1-lockscreen-authentication--anti-coercion-lsposed-native-hooks)
  - [2. Storage Architecture & Emergency Destruction Pipeline](#2-storage-architecture--emergency-destruction-pipeline)
  - [3. Hardware & Environmental Tripwires](#3-hardware--environmental-tripwires)
  - [4. Deception Matrix & Decoy Sandbox Environment](#4-deception-matrix--decoy-sandbox-environment)
  - [5. Surveillance & Multi-Modal Evidence Gathering](#5-surveillance--multi-modal-evidence-gathering)
  - [6. Remote Command & Control (SMS & SMTP Pipeline)](#6-remote-command--control-sms--smtp-pipeline)
  - [7. Deterministic Behavioral Analysis & Cryptographic Integrity](#7-deterministic-behavioral-analysis--cryptographic-integrity)
- [📡 Remote SMS Command Reference](#-remote-sms-command-reference)
- [🛠️ Technology Stack](#️-technology-stack)
- [📂 Project Directory Structure](#-project-directory-structure)
- [🚀 Deployment & Installation Guide](#-deployment--installation-guide)
  - [Prerequisites](#prerequisites)
  - [Phase 1: Build or Acquire the Release Artifacts](#phase-1-build-or-acquire-the-release-artifacts)
  - [Phase 2: Flashing the Systemless Module (.zip)](#phase-2-flashing-the-systemless-module-zip)
  - [Phase 3: Activating the LSPosed Hook](#phase-3-activating-the-lsposed-hook)
  - [Phase 4: Configuring & Arming Credentials](#phase-4-configuring--arming-credentials)
- [📄 Verification & Diagnostic Checklist](#-verification--diagnostic-checklist)
- [📄 License & Credits](#-license--credits)

---

> [!WARNING]
> ### ⚠️ Legal & Ethical Disclaimer
> **Uncle Ted is engineered strictly for educational, defensive security, personal privacy, and human rights protection in high-risk scenarios.** 
> Features such as cryptographic key eviction, block storage zeroing, and partition destruction carry permanent, irreversible consequences. Overwriting low-level block storage can permanently render hardware inoperable (hard brick).
> **Never install or deploy this software on any device without explicit authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the use or misuse of this codebase.

---

## 📊 Architectural Comparison: Standalone APK vs. Flashed System Module

On modern Android (Android 10 through 14+), Google's security sandbox imposes rigid boundaries on third-party userspace applications. Understanding the functional gulf between installing Uncle Ted as a standard sideloaded APK versus flashing it as a **Systemless Priv-App Module with LSPosed hooks** is critical for threat modeling.

| Security Vector / Feature | Standalone APK (Stock OS / Non-Root) | Flashed Systemless ZIP (`/system/priv-app/` + LSPosed) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :--- |
| **Lockscreen PIN Interception** | 🔴 **Non-Functional** | 🟢 **100% Native Hook** | Userspace apps cannot read Keyguard keystrokes. The module hooks `LockSettingsService` directly inside `system_server`. |
| **Before First Unlock (BFU) Execution** | 🔴 **Disabled (Crashes)** | 🟢 **Fully Operational** | Sideloaded app CE storage is unreadable at boot. Uncle Ted utilizes `DeviceProtectedStorageContext` (DE) storage paired with `/data/system/uncleted/` platform bridge. |
| **Silent Emergency Wipe Authority** | 🟡 **Prompts / Deprecated** | 🟢 **Zero-Delay Native Wipe** | Sideloaded apps rely on Device Admin APIs. Flashed priv-apps invoke `RecoverySystem.rebootWipeUserData()` backed by `android.permission.MASTER_CLEAR`. |
| **Bypass via Safe Mode** | 🔴 **Trivially Bypassed** | 🟢 **Immune** | Safe Mode boots Android with third-party apps disabled. Privileged apps mounted under `/system/priv-app/` remain active. |
| **Bypass via Task Killer / ADB** | 🔴 **Vulnerable** | 🟢 **Protected** | Userspace floating windows (`SYSTEM_ALERT_WINDOW`) can be killed via `am force-stop`. The native lockscreen hook executes inside the unkillable `system_server`. |
| **Background Media Capture** | 🟡 **Requires Fullscreen Intent** | 🟢 **Root / Foreground Service** | Android 14+ blocks background camera access. Priv-app status combined with Root shell dispatch (`am start`) bypasses BAL restrictions completely. |
| **Anti-Tamper & Anti-Uninstall** | 🔴 **Low** | 🟢 **System-Locked** | Standard apps can be uninstalled from Settings. Priv-apps cannot be removed without root manager or custom recovery access. |
| **Hardware Volume Key Sequence** | 🟡 **Restricted Accessibility** | 🟢 **Unconstrained Service** | Android 13+ restricts accessibility services for sideloaded APKs. System integration runs with unconstrained input filtering. |
| **Kernel USB Tripwire** | 🔴 **Impossible** | 🟢 **Kernel SysFS Monitor** | Reading `/sys/class/power_supply/` and `/sys/class/udc/*` data attributes requires root or system-level filesystem permissions. |
| **Overall Defense Capability** | **3.5 / 10** | **9.5 / 10** | Sideloaded APKs provide a false sense of security under physical coercion. Flashing the module guarantees operating system-level enforcement. |

---

## 🏗️ System Architecture (The 3-Tier Model)

Uncle Ted operates across three integrated levels of the Android operating system to guarantee persistence, privilege, and sub-second execution:

```
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ TIER 3: KERNEL & SYSTEM_SERVER HOOK (LSPosed / Root UID 0)                  │
 │ - Hooks LockSettingsService directly inside the system_server process       │
 │ - Intercepts native Keyguard inputs Before First Unlock (BFU)               │
 │ - Reads credentials from platform bridge: /data/system/uncleted/            │
 │ - Dispatches deduplicated foreground intents to Uncle Ted subsystems        │
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ IPC Broadcast / Bridge
 ┌──────────────────────────────────────▼──────────────────────────────────────┐
 │ TIER 2: SYSTEM PRIV-APP OVERLAY (Magisk / KernelSU / APatch / OverlayFS)    │
 │ - Privileged Allowlist: android.permission.MASTER_CLEAR, REBOOT, etc.       │
 │ - Invokes RecoverySystem.rebootWipeUserData directly with zero UI prompts  │
 │ - Whitelisted against App Standby, Doze mode, and aggressive OOM killing    │
 │ - DeviceProtectedStorageContext (DE) persistence active pre-unlock          │
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ Local IPC
 ┌──────────────────────────────────────▼──────────────────────────────────────┐
 │ TIER 1: USERSPACE DEFENSE APPS & SERVICES (com.hamoon.uncleted)             │
 │ - Foreground Sentinel Services (MonitoringService, UsbTripwireService)      │
 │ - Full-Screen Intent Broker & CameraX dual-camera capture pipeline          │
 │ - Deception Matrix (Honeypot Launcher, Fake Banking, Fake Notes)            │
 │ - Deterministic Bayesian Risk Engine & CSPRNG Hardware Keystore Auditing    │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🖼️ Interface & Architecture Preview

<p align="center">
  <img width="1049" height="721" alt="Uncle Ted Main Dashboard" src="https://github.com/user-attachments/assets/173c3b9d-5bd0-47a1-92f3-aeb342d44cef" />
  <img width="1063" height="629" alt="Uncle Ted System Configuration" src="https://github.com/user-attachments/assets/846b51eb-c9ba-4201-87f2-cbf70132839c" />
  <img width="1045" height="795" alt="Uncle Ted Security Controls" src="https://github.com/user-attachments/assets/2a1ebc67-19c3-475f-af07-6674d02fc4e6" />
</p>

---

## ✨ Core Capabilities

### 1. Lockscreen Authentication & Anti-Coercion (LSPosed Native Hooks)
Instead of relying on fragile overlay screens (`SYSTEM_ALERT_WINDOW`) that can be bypassed with system gesture glitches or task killers, Uncle Ted hooks directly into AOSP's core `com.android.server.locksettings.LockSettingsService`:
- **Normal PIN:** Standard device unlock; resets failed attempt counters.
- **Duress PIN:** Transparently unlocks the device so an adversary suspects nothing, while silently dispatching an urgent alert with front/back photos, audio recordings, and GPS coordinates to emergency contacts.
- **Wipe PIN:** Immediately halts Keyguard authentication, blocks access to the launcher, evicts cryptographic keys from RAM, and executes irreversible factory data destruction.
- **Honeypot PIN:** Unlocks the device into an isolated decoy sandbox environment populated with trap applications designed to harvest attacker credentials.
- **BFU (Before First Unlock) Persistence:** Credentials synchronize directly to `/data/system/uncleted/credentials.cfg` (SELinux context `u:object_r:system_data_file:s0`), allowing interception even when the phone has just rebooted and remains fully encrypted.
- **Deduplicated Event Bus:** Dedicated debouncing logic eliminates dual-counting between Android DevicePolicyManager callbacks and Xposed hook dispatches, ensuring intruder alerts fire on exact thresholds.

---

### 2. Storage Architecture & Emergency Destruction Pipeline
Uncle Ted features a calibrated, sequential destruction engine (`EmergencyDestructionEngine`) that dynamically recognizes modern storage architectures (UFS vs. eMMC vs. NVMe) and separates userspace platform wipes from low-level block zeroing to avoid self-corrupting recovery execution:
- **Instant Radio Killswitch:** Flushes and sets default drop policies across all `iptables` and `ip6tables` chains (`INPUT`, `OUTPUT`, `FORWARD`) within milliseconds to stop remote aborts or forensic network sniffing.
- **Cryptographic Key Eviction:** Clears Linux kernel keyrings (`keyctl clear @u`, `keyctl clear @s`) and zeros out Vold user keys (`/data/misc/vold/user_keys/` and `/metadata/vold/`).
- **Dynamic Partition Resolution:** Locates `/dev/block/by-name/` block targets across Qualcomm, MediaTek, Exynos, and Tensor SoC layouts without hardcoded paths.
- **Four Destruction Tiers:**
  1. *Level 1 - Standard Factory Reset:* Platform `MASTER_CLEAR` wipe via `RecoverySystem.rebootWipeUserData()` or Bootloader Control Block (BCB) `--wipe_data` staging under `/cache/recovery/command`.
  2. *Level 2 - Secure Data Shred:* Zeros cryptographic metadata headers (`/dev/block/by-name/metadata`) and overwrites the first 64MB of `userdata` storage before rebooting into recovery for hardware re-formatting.
  3. *Level 3 - OS Suicide (Soft Brick):* Overwrites kernel and initial ramdisk partitions (`boot`, `vendor_boot`, `init_boot`) with zeros, immediately rendering the device unbootable without firmware reflashing.
  4. *Level 4 - Nuclear Winter (Hard Brick):* Dynamically identifies physical disk controllers (`/dev/block/sda` on UFS devices, `/dev/block/mmcblk0` on eMMC devices) and zeroes master GUID Partition Tables (GPT) and bootloader headers before issuing a kernel crash trigger (`sysrq-trigger`).

---

### 3. Hardware & Environmental Tripwires
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` instantly triggers emergency data erasure even while the screen is off.
- **Kernel-Level USB Tripwire:** Continuously monitors kernel SysFS paths (`/sys/class/power_supply/usb/type`, `/sys/class/udc/*`) and ADB properties via `UsbTripwireService`. If a data-capable cable (SDP/CDP) or forensic extraction workstation (Cellebrite, GrayKey) is connected while the device is locked, an immediate wipe is executed.
- **Geographic Suicide (Evin Prison Boundary):** Leverages a numerically stable Ray-Casting Polygon Algorithm in `PolygonUtils` to monitor device coordinates. Entering the defined perimeter of high-risk facilities automatically arms a zero-delay wipe. Edge singularities and vertical-boundary division errors are mathematically eliminated.
- **Network Inactivity Tripwire:** Managed by `WorkManager`. If the device cannot establish a validated network check-in within a user-configured interval (e.g., 24 or 48 hours), the tripwire activates an offline emergency wipe.
- **SIM Card Swap Sentinel:** Detects changes in the ICC serial number of the SIM card, instantly locking the device and transmitting location packets via SMS fallback.
- **Power Menu Interception (Fake Shutdown):** Accessibility hooks suppress the system power menu, displaying an authentic "Powering off..." animation while keeping the device operational, silent, and tracking in the background.

---

### 4. Deception Matrix & Decoy Sandbox Environment
If forced to hand over an unlocked device, launching or unlocking via the Honeypot PIN presents a realistic decoy workspace:
- **Decoy Launcher:** Mimics an authentic Android desktop layout while blocking access to the true home screen and applications.
- **Fake Banking App:** Traps and records attacker login attempts, immediately transmitting entered credentials to the remote emergency email.
- **Fake Notes App:** Contains decoy documents ("Crypto Keys", "Passwords") that trigger high-severity silent alerts the moment they are tapped.
- **Decoy Gallery:** Presents dummy albums while silently triggering front-camera evidence collection.

---

### 5. Surveillance & Multi-Modal Evidence Gathering
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` to capture high-resolution front- and back-camera photos, followed by front- and back-camera video clips.
- **Android 14 Background Launch Compliance:** Employs a full-screen intent broker (`CameraPermissionBrokerActivity`) paired with system shell invocation (`am start`) to bypass Android 10–14 Background Activity Launch (BAL) restrictions cleanly.
- **Hybrid Input Surveillance:** Intercepts physical hardware inputs (Volume, Power) via `/dev/input/` events (`getevent -l`) while capturing software keyboard typing and text input dynamically through the Accessibility event bus.
- **Ambient Audio Surveillance:** Direct-to-disk MP3 audio capture using `MediaRecorder` at user-configurable recording intervals.
- **Anti-Green Dot Suppression (Root):** Suppresses Android 12+ privacy indicators during evidence capture by resetting and managing camera server instances.
- **Stealth Screenshot (Root):** Directly reads surface buffers via `/system/bin/screencap` without generating UI flashes or notification badges.

---

### 6. Remote Command & Control (SMS & SMTP Pipeline)
When mobile data or Wi-Fi is lost, Uncle Ted falls back to a broadcast-intercepting SMS engine requiring a master authentication password:
- **Inbox Cleansing:** On Android 4.4+, third-party apps cannot block SMS delivery via `abortBroadcast()`. Uncle Ted actively cleanses incoming command SMS messages directly from the telephony content provider (`content://sms`) using elevated shell privileges to prevent cleartext exposure of authentication secrets.
- **Encrypted Alert Payloads:** High-accuracy GPS links, captured media dossiers, and diagnostic metrics are transmitted using JavaMail over authenticated SSL/TLS SMTP channels.

---

### 7. Deterministic Behavioral Analysis & Cryptographic Integrity
- **Sliding-Window Behavioral Biometrics:** Evaluates typing cadence (dwell and flight times), device orientation stability, and walking step frequency using thread-safe, rolling time-window sensor buffers in `BehavioralAnalysisEngine`.
- **Hardware-Backed Keystore Auditing:** Replaces pseudo-random heuristics with active AndroidKeyStore validation, ensuring master keys reside inside secure hardware (TEE/StrongBox).
- **NIST SP 800-22 Entropy Testing:** Regularly tests the platform Cryptographically Secure Pseudo-Random Number Generator (CSPRNG) using Monobit frequency evaluations to detect entropy pool degradation.
- **Calibrated Bayesian Threat Orchestrator:** Evaluates threat assessments, biometric stability, and keystore state through an evidence-weighted heuristic model, completely avoiding uncalibrated lockouts or false positives.

---

## 📡 Remote SMS Command Reference

Send commands via SMS from any phone using the following syntax:
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

- **Language:** 100% Modern Kotlin (Coroutines, Flow, StateFlow)
- **Target OS:** Android 14 (API 34) | **Minimum OS:** Android 9 (API 28)
- **Framework Hooks:** Xposed API v82 / LSPosed Framework
- **System Privileges:** Android Privileged Permission Allowlist (`android.permission.MASTER_CLEAR`, `WRITE_SECURE_SETTINGS`, `REBOOT`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video)
- **Background Architecture:** AndroidX WorkManager & Android Foreground Services (compliant with Android 14 FGS types)
- **Security & Cryptography:** AndroidX Security Crypto (`EncryptedSharedPreferences`, MasterKey AES-256-GCM, DeviceProtectedStorageContext)
- **UI & Layout:** Google Material Design 3 Components with ViewBinding

---

## 📂 Project Directory Structure

```text
UncleTed-main/
├── app/
│   ├── build_output/
│   │   └── UncleTed-PrivApp-v2.0.zip              <-- Pre-packaged systemless flashable module
│   ├── distribution/
│   │   └── etc/permissions/
│   │       └── privapp-permissions-uncleted.xml  <-- System priv-app allowlist (MASTER_CLEAR)
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   └── xposed_init                   <-- LSPosed module entrypoint declaration
│   │   │   ├── java/com/hamoon/uncleted/
│   │   │   │   ├── data/
│   │   │   │   │   └── SecurityPreferences.kt    <-- Dual DE/CE persistent storage manager
│   │   │   │   ├── fragments/                    <-- UI Views (Dashboard, PINs, Features, etc.)
│   │   │   │   ├── honeypot/                     <-- Decoy Launcher & Bait Activities
│   │   │   │   ├── hooks/
│   │   │   │   │   └── LockscreenHook.kt         <-- Core system_server LSPosed hook
│   │   │   │   ├── receivers/                    <-- Boot, SMS, Admin, & Duress Receivers
│   │   │   │   ├── services/
│   │   │   │   │   ├── MonitoringService.kt      <-- Core sentinel service
│   │   │   │   │   ├── PanicActionService.kt     <-- Emergency dispatch orchestrator
│   │   │   │   │   ├── PowerButtonService.kt     <-- Accessibility key & input monitor
│   │   │   │   │   ├── UsbTripwireService.kt     <-- Kernel SysFS data line tripwire
│   │   │   │   │   └── ZoneWipeService.kt        <-- Perimeter geofence suicide service
│   │   │   │   ├── util/
│   │   │   │   │   ├── AISecurityOrchestrator.kt <-- Deterministic Bayesian risk engine
│   │   │   │   │   ├── BehavioralAnalysisEngine.kt <-- Sliding-window biometric analyzer
│   │   │   │   │   ├── CredentialBridge.kt       <-- Cross-process BFU platform bridge
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- UFS/eMMC block zeroing engine
│   │   │   │   │   ├── Keylogger.kt              <-- Hardware & soft-keyboard logger
│   │   │   │   │   ├── PolygonUtils.kt           <-- Numerically robust Ray-Casting algorithm
│   │   │   │   │   ├── QuantumSecurityLayer.kt   <-- CSPRNG & Keystore integrity auditor
│   │   │   │   │   └── RootActions.kt            <-- Kernel commands & Magisk operations
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog, Tripwire)
│   │   │   ├── AndroidManifest.xml
│   │   │   └── CameraPermissionBrokerActivity.kt <-- Android 14 BAL permission broker
│   │   └── build.gradle.kts
│   └── proguard-rules.pro
└── settings.gradle.kts
```

---

## 🚀 Deployment & Installation Guide

### Prerequisites
- A rooted Android device running Android 10 through 14 (rooted via **Magisk**, **KernelSU**, or **APatch**).
- **LSPosed (Zygisk release)** installed and verified operational in your root manager.
- A custom recovery (**TWRP** or **OrangeFox**) installed (optional, but recommended for recovery-based installs).

---

### Phase 1: Build or Acquire the Release Artifacts
You can either download the pre-packaged module `UncleTed-PrivApp-v2.0.zip` directly from the repository's `app/build_output/` folder or build the project yourself:
1. Open the project inside Android Studio.
2. Select **Build** -> **Select Build Variant...** -> Set to `release`.
3. Select **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
4. Package the flashable zip using `python app/package_module.py`.

---

### Phase 2: Flashing the Systemless Module (.zip)
The flashable module `UncleTed-PrivApp-v2.0.zip` installs UncleTed into `/system/priv-app/`, injects the `privapp-permissions-uncleted.xml` whitelist into `/system/etc/permissions/`, and sets all file permissions (`0644`) and ownership (`0:0 root:root`) systemlessly via `overlayfs`.

#### Option A: Flash via Magisk / KernelSU App (Recommended)
1. Transfer `app/build_output/UncleTed-PrivApp-v2.0.zip` to your device's internal storage:
   ```bash
   adb push app/build_output/UncleTed-PrivApp-v2.0.zip /sdcard/
   ```
2. Open **Magisk** or **KernelSU Manager**.
3. Navigate to the **Modules** tab.
4. Tap **Install from storage**, select `UncleTed-PrivApp-v2.0.zip`, and allow the installer script to run.
5. Tap **Reboot**.

#### Option B: Flash via Custom Recovery (TWRP / OrangeFox)
1. Boot into Recovery:
   ```bash
   adb reboot recovery
   ```
2. Tap **Install** -> Select `/sdcard/UncleTed-PrivApp-v2.0.zip`.
3. Swipe to confirm flash.
4. Tap **Reboot System**.

---

### Phase 3: Activating the LSPosed Hook
1. Once the phone reboots, open the **LSPosed Manager** app.
2. Navigate to the **Modules** tab (puzzle icon).
3. Tap **UncleTed System Priv-App & Hook**.
4. Toggle **Enable Module** to **ON**.
5. Ensure the hook scope includes:
   - `System Framework` (`android`)
   - `System UI` (`com.android.systemui`)
6. **Reboot your device once more** so `system_server` loads the native Keyguard hook during early initialization.

---

### Phase 4: Configuring & Arming Credentials
1. Open **UncleTed** on your device.
2. Complete the initial permission requests in the **Core Services** tab.
3. Open the **Authentication** tab:
   - Set a **Normal Unlock PIN** (e.g., `1111`).
   - Set a **Duress (Panic) PIN** (e.g., `2222`).
   - Set a **Wipe Lock PIN** (e.g., `9999`).
   - Set a **Honeypot PIN** (e.g., `5555`).
4. Tap **Save PINs**.
5. Confirm the platform bridge synchronization notification:
   ```text
   ✓ PINs saved & OS Hook Bridge Armed (/data/system)
   ```

---

## 📄 Verification & Diagnostic Checklist

Run these commands via ADB to confirm that all operational layers are properly armed:

#### 1. Confirm Privileged System-App Placement
```bash
adb shell pm path com.hamoon.uncleted
```
*Expected output:*
```text
package:/system/priv-app/UncleTed/UncleTed.apk
```
*(If it returns `/data/app/...`, the module overlay has not mounted properly).*

#### 2. Confirm `MASTER_CLEAR` Platform Permission
```bash
adb shell dumpsys package com.hamoon.uncleted | grep -E "MASTER_CLEAR|WRITE_SECURE_SETTINGS"
```
*Expected output:*
```text
android.permission.MASTER_CLEAR: granted=true
android.permission.WRITE_SECURE_SETTINGS: granted=true
```

#### 3. Verify Direct-Boot Credential Synchronization
Confirm that the platform bridge file is present in Device-Encrypted space and populated with configured PINs:
```bash
adb shell su -c "cat /data/system/uncleted/credentials.cfg"
```
*Expected output:*
```text
wipe_pin=[YOUR_WIPE_PIN]
duress_pin=[YOUR_DURESS_PIN]
updated_at=[TIMESTAMP]
```

#### 4. Monitor Live Lockscreen Hook Interception
Lock the device screen. Open an active logcat monitor on your workstation:
```bash
adb logcat -s "UncleTed-LockHook" "PanicActionService"
```
Enter your **Duress PIN** on the native Keyguard keypad. Observe:
```text
UncleTed-LockHook: Credential verification intercepted: [length=4]
UncleTed-LockHook: DURESS PIN matched at OS level. Dispatching duress broadcast.
PanicActionService: Service executing protocol for: DURESS_PIN (Severity: HIGH)
```

#### 5. Verify Storage Block Controller Discovery
Confirm that the destruction engine correctly identifies storage blocks on modern devices:
```bash
adb shell su -c "ls -d /sys/block/sd* /sys/block/mmcblk* 2>/dev/null"
```
*Expected output on UFS devices:*
```text
/sys/block/sda  /sys/block/sdb  /sys/block/sdc
```
*Expected output on eMMC devices:*
```text
/sys/block/mmcblk0
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).