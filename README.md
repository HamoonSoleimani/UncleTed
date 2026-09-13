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
  <img src="https://img.shields.io/badge/Min%20SDK-29%20(Android%2010)-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Framework-LSPosed%20%2F%20Xposed-red.svg" alt="LSPosed">
  <img src="https://img.shields.io/badge/Root-Magisk%20%2F%20KernelSU%20%2F%20APatch-purple.svg" alt="Root">
</p>

---

### **Table of Contents**
- [⚠️ Legal & Ethical Disclaimer](#️-legal--ethical-disclaimer)
- [📊 Architectural Comparison: Standalone APK vs. Flashed System Module](#-architectural-comparison-standalone-apk-vs-flashed-system-module)
- [🏗️ System Architecture (The 3-Tier Model)](#️-system-architecture-the-3-tier-model)
- [🖼️ Interface & Architecture Preview](#️-interface--architecture-preview)
- [✨ Core Capabilities](#-core-capabilities)
  - [1. Lockscreen Authentication & Anti-Coercion (LSPosed Native Hooks)](#1-lockscreen-authentication--anti-coercion-lsposed-native-hooks)
  - [2. Emergency Destruction & Cryptographic Erasure](#2-emergency-destruction--cryptographic-erasure)
  - [3. Hardware & Environmental Tripwires](#3-hardware--environmental-tripwires)
  - [4. Deception Matrix & Honeypot Environment](#4-deception-matrix--honeypot-environment)
  - [5. Surveillance & Evidence Burst Gathering](#5-surveillance--evidence-burst-gathering)
  - [6. Remote Command & Control (SMS & SMTP)](#6-remote-command--control-sms--smtp)
  - [7. AI & Behavioral Analysis Engine](#7-ai--behavioral-analysis-engine)
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
> Features such as cryptographic key zeroing, hardware wipe routines, and partition destruction carry permanent, irreversible consequences. Flashing custom loaders or overwriting block storage can permanently render hardware inoperable (hard brick). 
> **Never install or deploy this software on any device without explicit authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the use or misuse of this codebase.

---

## 📊 Architectural Comparison: Standalone APK vs. Flashed System Module

On modern Android (Android 10 through 14+), Google's security sandbox imposes rigid boundaries on third-party userspace applications. Understanding the functional gulf between installing Uncle Ted as a standard sideloaded APK versus flashing it as a **Systemless Priv-App Module with LSPosed hooks** is critical for threat modeling.

| Security Vector / Feature | Standalone APK (Stock OS / Non-Root) | Flashed Systemless ZIP (`/system/priv-app/` + LSPosed) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :--- |
| **Lockscreen PIN Interception** | 🔴 **Non-Functional** | 🟢 **100% Native Hook** | Userspace apps cannot read Keyguard keystrokes. The module hooks `LockSettingsService` directly inside `system_server`. |
| **Before First Unlock (BFU) Execution** | 🔴 **Disabled (Dead)** | 🟢 **Fully Operational** | Sideloaded app code is encrypted inside Credential-Encrypted (CE) storage at boot. The module bridges configuration to `/data/system/uncleted/` using Device-Encrypted (DE) storage. |
| **Silent Emergency Wipe Authority** | 🔴 **Broken / Blocked** | 🟢 **Zero-Delay Native Wipe** | Sideloaded apps rely on deprecated Device Admin APIs that prompt the user or fail on Android 13+. The module grants `android.permission.MASTER_CLEAR`. |
| **Bypass via Safe Mode** | 🔴 **Trivially Bypassed** | 🟢 **Immune** | Safe Mode boots Android with third-party apps disabled. System priv-apps in `/system/priv-app/` remain active. |
| **Bypass via Task Killer / ADB** | 🔴 **Vulnerable** | 🟢 **Protected** | Userspace floating windows (`SYSTEM_ALERT_WINDOW`) can be killed via `am force-stop`. The native lockscreen hook executes inside the unkillable `system_server`. |
| **Background Media Capture** | 🔴 **Blocked by OS** | 🟢 **Fully Supported** | Android 14+ strictly blocks background apps from accessing the camera or microphone. Priv-app status combined with root bypasses foreground service restrictions. |
| **Anti-Tamper & Anti-Uninstall** | 🔴 **Low** | 🟢 **System-Locked** | Standard apps can be uninstalled from Settings. Priv-apps cannot be removed without root or recovery partition access. |
| **Hardware Volume Key Sequence** | 🟡 **Partial (Accessibility)** | 🟢 **Low-Level Service** | Android 13+ restricts accessibility services for sideloaded APKs. System integration runs with unconstrained input filtering. |
| **Kernel USB Tripwire** | 🔴 **Impossible** | 🟢 **Kernel SysFS Monitor** | Reading `/sys/class/power_supply/usb/type` and `/sys/class/udc/*` requires root or system-level filesystem permissions. |
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
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ IPC Broadcast / Bridge
 ┌──────────────────────────────────────▼──────────────────────────────────────┐
 │ TIER 2: SYSTEM PRIV-APP OVERLAY (Magisk / KernelSU / /system/priv-app)      │
 │ - Privileged Allowlist: android.permission.MASTER_CLEAR, REBOOT, etc.       │
 │ - Invokes RecoverySystem.rebootWipeUserData directly with zero UI prompts  │
 │ - Whitelisted against App Standby, Doze mode, and aggressive OOM killing    │
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ Local IPC
 ┌──────────────────────────────────────▼──────────────────────────────────────┐
 │ TIER 1: USERSPACE DEFENSE APPS & SERVICES (com.hamoon.uncleted)             │
 │ - Foreground Sentinel Services (MonitoringService, UsbTripwireService)      │
 │ - Jetpack CameraX headless capture & ambient audio recording pipeline       │
 │ - Deception Matrix (Honeypot Launcher, Fake Banking, Fake Notes)            │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🖼️ Interface & Architecture Preview

<p align="center">
  <img width="220" alt="Dashboard Status" src="https://github.com/user-attachments/assets/16a8dbc3-e880-4db6-aee0-c48a7c31ae11" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img width="220" alt="Core Configuration" src="https://github.com/user-attachments/assets/b762f43c-9760-4ac1-943a-bd7e94f54d08" />
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

---

### 2. Emergency Destruction & Cryptographic Erasure
Uncle Ted features a sequential, blocking destruction pipeline (`EmergencyDestructionEngine`) designed to prevent OS shutdowns or power cuts from terminating wipe threads:
- **Instant Radio Killswitch:** Flushes and sets default drop policies across all `iptables` and `ip6tables` chains (`INPUT`, `OUTPUT`, `FORWARD`) within milliseconds to stop remote aborts or forensic network sniffing.
- **Cryptographic Key Eviction:** Clears Linux kernel keyrings (`keyctl clear @u`, `keyctl clear @s`) and zeros out Vold user keys (`/data/misc/vold/user_keys/` and `/metadata/vold/`).
- **Four Destruction Tiers:**
  1. *Level 1 - Standard Factory Reset:* Platform `MASTER_CLEAR` wipe via `RecoverySystem.rebootWipeUserData()`.
  2. *Level 2 - Secure Data Shred:* Zeros the first 100MB of `/data` and `/metadata` block devices, destroying FBE master keys before rebooting to recovery.
  3. *Level 3 - OS Suicide (Soft Brick):* Mounts `/system` read-write, deletes `/system/bin`, `/system/framework`, and `/vendor`, preventing the phone from booting again without full firmware reflashing.
  4. *Level 4 - Nuclear Winter (Hard Brick Risk):* Overwrites the device's raw GUID Partition Table (GPT) and master boot records (`/dev/block/mmcblk0`), permanently corrupting low-level partition headers.

---

### 3. Hardware & Environmental Tripwires
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` instantly triggers emergency data erasure even while the screen is off.
- **Kernel-Level USB Tripwire:** Continuously monitors kernel SysFS paths (`/sys/class/power_supply/usb/type`, `/sys/class/udc/*`) and ADB properties. If a data-capable cable (SDP/CDP) or forensic extraction hardware (Cellebrite, GrayKey) is connected while the device is locked, the device triggers an immediate wipe.
- **Geographic Suicide (Evin Prison Boundary):** Leverages a Ray-Casting Polygon Algorithm to monitor device coordinates. Entering the defined perimeter of high-risk interrogation facilities automatically arms a zero-delay wipe.
- **Network Inactivity Tripwire:** Managed by `WorkManager`. If the device cannot establish a validated network check-in within a user-configured interval (e.g., 24 or 48 hours), the tripwire activates an offline emergency wipe.
- **SIM Card Swap Sentinel:** Detects changes in the ICC serial number of the SIM card, instantly locking the device and transmitting location packets via SMS fallback.
- **Power Menu Interception (Fake Shutdown):** Accessibility hooks suppress the system power menu, displaying an authentic "Powering off..." animation while keeping the device operational, silent, and tracking in the background.

---

### 4. Deception Matrix & Honeypot Environment
If forced to hand over an unlocked device, launching or unlocking via the Honeypot PIN presents a realistic decoy workspace:
- **Decoy Launcher:** Mimics an authentic Android desktop layout while blocking access to the true home screen and applications.
- **Fake Banking App:** Traps and records attacker login attempts, immediately transmitting entered credentials to the remote emergency email.
- **Fake Notes App:** Contains decoy documents ("Crypto Keys", "Passwords") that trigger high-severity silent alerts the moment they are tapped.
- **Decoy Gallery:** Presents dummy albums while silently triggering front-camera evidence collection.

---

### 5. Surveillance & Evidence Burst Gathering
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` to capture high-resolution front- and back-camera photos, followed by front- and back-camera video clips.
- **Ambient Audio Surveillance:** Direct-to-disk MP3 audio capture using `MediaRecorder` at user-configurable recording intervals.
- **Anti-Green Dot Suppression (Root):** Suppresses Android 12+ privacy indicators during evidence capture by resetting and managing camera server instances.
- **Stealth Screenshot (Root):** Directly reads surface buffers via `/system/bin/screencap` without generating UI flashes or notification badges.

---

### 6. Remote Command & Control (SMS & SMTP)
When mobile data or Wi-Fi is lost, Uncle Ted falls back to a broadcast-intercepting SMS engine requiring a master authentication password. Incoming commands are stripped from the inbox (`abortBroadcast()`).

Detailed alert payloads (location links, camera captures, diagnostic battery/network metrics, and audio files) are dispatched using JavaMail over authenticated TLS/SSL connections.

---

### 7. AI & Behavioral Analysis Engine
- **Keystroke & Holding Dynamics:** Observes dwell times, flight times, typing pressures, and rotational gyroscope stability to detect usage by an unauthorized individual.
- **Lifecycle-Aware Monitoring:** Automatically throttles analysis loops when the interface enters the background, preserving battery and preventing CPU thermal throttling.
- **Quantum-Inspired Security Layer:** An experimental heuristic model evaluating system entropy, device state uncertainty, and simulated entanglement states to adaptively elevate security postures.

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
| `GETLOGS`| *None* | `LOW` | (Root Only) Dumps and emails captured kernel keylogs, then flushes the buffer. |
| `EXFIL` | `[pkg] [file]` | `HIGH` | (Root Only) Copies a file from `/data/data/[pkg]/` and attaches it via email. |

---

## 🛠️ Technology Stack

- **Language:** 100% Modern Kotlin (Coroutines, Flow, StateFlow)
- **Target OS:** Android 14 (API 34) | **Minimum OS:** Android 10 (API 29)
- **Framework Hooks:** Xposed API v82 / LSPosed Framework
- **System Privileges:** Android Privileged Permission Allowlist (`android.permission.MASTER_CLEAR`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video)
- **Background Architecture:** AndroidX WorkManager & Android Foreground Services
- **Security & Cryptography:** AndroidX Security Crypto (`EncryptedSharedPreferences`, MasterKey AES-256-GCM)
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
│   │   │   │   ├── data/                         <-- Encrypted preferences & storage
│   │   │   │   ├── fragments/                    <-- UI Views (Dashboard, PINs, Remote, etc.)
│   │   │   │   ├── honeypot/                     <-- Decoy Launcher & Bait Activities
│   │   │   │   ├── hooks/
│   │   │   │   │   └── LockscreenHook.kt         <-- Core system_server LSPosed hook
│   │   │   │   ├── receivers/                    <-- Boot, SMS, Admin, & Duress Receivers
│   │   │   │   ├── services/                     <-- Monitoring, Panic, USB, & Zone Services
│   │   │   │   ├── util/
│   │   │   │   │   ├── CredentialBridge.kt       <-- Direct-boot cross-process sync
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- Cryptographic zeroing engine
│   │   │   │   │   ├── DeviceAdminHelper.kt      <-- Sequential wipe broker
│   │   │   │   │   └── RootActions.kt            <-- Kernel commands & Magisk operations
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog, Tripwire)
│   │   │   └── AndroidManifest.xml
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

---

### Phase 2: Flashing the Systemless Module (.zip)
The flashable module `UncleTed-PrivApp-v2.0.zip` automatically installs UncleTed into `/system/priv-app/`, injects the `privapp-permissions-uncleted.xml` whitelist into `/system/etc/permissions/`, and sets all file permissions (`0644`) and ownership (`0:0 root:root`) systemlessly via `overlayfs`.

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
6. **Reboot your device once more** so `system_server` can load the hook at boot.

---

### Phase 4: Configuring & Arming Credentials
1. Open **UncleTed** on your device.
2. Complete the initial permission requests in the **Core Services** tab.
3. Open the **Authentication** tab:
   - Set a **Normal Unlock PIN** (e.g., `1111`).
   - Set a **Duress (Panic) PIN** (e.g., `2222`).
   - Set a **Wipe Lock PIN** (e.g., `9999`).
4. Tap **Save PINs**.
5. You will see the confirmation message:
   ```text
   ✓ PINs saved & OS Hook Bridge Armed (/data/system)
   ```

---

## 📄 Verification & Diagnostic Checklist

Run these commands via ADB to confirm that all layers are properly armed:

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
Confirm that the platform bridge file is present and populated with your configured PINs:
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
adb logcat -s "[UncleTed-LockHook]" "PanicActionService"
```
Enter your **Duress PIN** on the native Keyguard keypad. You should observe:
```text
[UncleTed-LockHook] !!! ALERT: DURESS PIN INTERCEPTED AT OS LEVEL !!!
[UncleTed-LockHook] Duress broadcast dispatched to UncleTed.
PanicActionService: Service executing protocol for: DURESS_PIN (Severity: HIGH)
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).
