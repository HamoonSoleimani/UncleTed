<p align="center">
  <img src="https://hamoon.net/wp-content/uploads/2025/09/logo-transparent.png" alt="Uncle Ted Logo" width="180">
</p>

<h1 align="center">Uncle Ted for Android</h1>

<p align="center">
  <strong>A cabin in the digital woods.</strong><br>
  An advanced, hardware-backed personal security, anti-coercion, post-quantum, and anti-forensic defense suite for Android.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Version-v10.0.1-brightgreen.svg" alt="Version">
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg" alt="Target SDK">
  <img src="https://img.shields.io/badge/Compatibility-Android%209%20to%2017%20%2F%20GrapheneOS-darkgreen.svg" alt="Compatibility">
  <img src="https://img.shields.io/badge/Hardware-Titan%20M2%20%2F%20StrongBox-blueviolet.svg" alt="Titan M2 StrongBox">
  <img src="https://img.shields.io/badge/PQC-NIST%20FIPS%20203%20(ML--KEM--768)-darkgreen.svg" alt="NIST ML-KEM-768">
  <img src="https://img.shields.io/badge/Exploit%20Defense-ARM%20MTE%20(Sync)-red.svg" alt="ARM MTE">
  <img src="https://img.shields.io/badge/Platform-Device%20Owner%20%2F%20AVB%20Locked-blue.svg" alt="Device Owner">
  <img src="https://img.shields.io/badge/Root-Magisk%20%2F%20KernelSU%20%2F%20APatch-purple.svg" alt="Root">
</p>

<p align="center">
  <img width="829" height="601" alt="Uncle Ted Architecture Overview" src="https://github.com/user-attachments/assets/cef2fb8b-4fdd-40f5-9778-c89e2f4a9825" />
</p>
<img width="1361" height="1079" alt="image" src="https://github.com/user-attachments/assets/b8048b25-e480-4da0-b902-01b80d12fb4c" />

---

### **Table of Contents**
- [⚠️ Legal & Ethical Disclaimer](#️-legal--ethical-disclaimer)
- [📊 Architectural Comparison: Deployment Profiles](#-architectural-comparison-deployment-profiles)
- [🏗️ System Architecture (Dual-Profile & Sub-OS Engine)](#️-system-architecture-dual-profile--sub-os-engine)
- [✨ Core Capabilities](#-core-capabilities)
  - [1. Hardware Security Module & Anti-Rollback Suicide Key (Titan M2 / StrongBox)](#1-hardware-security-module--anti-rollback-suicide-key-titan-m2--strongbox)
  - [2. Inverted Dead-Man Architecture: "Fail-Closed" Ephemeral Keys](#2-inverted-dead-man-architecture-fail-closed-ephemeral-keys)
  - [3. Out-of-Band Key Decoupling via OPRF (RFC 9497 / secp256r1)](#3-out-of-band-key-decoupling-via-oprf-rfc-9497--secp256r1)
  - [4. Zero-Latency Hardware USB PHY Annihilation (The Trapdoor Port)](#4-zero-latency-hardware-usb-phy-annihilation-the-trapdoor-port)
  - [5. True FBE 4KB Key Block Crypto-Shredding, Standard Wipe & JEDEC Discard](#5-true-fbe-4kb-key-block-crypto-shredding-standard-wipe--jedec-discard)
  - [6. NIST FIPS 203 Post-Quantum Cryptographic Hybrid Engine (ML-KEM-768 + X25519)](#6-nist-fips-203-post-quantum-cryptographic-hybrid-engine-ml-kem-768--x25519)
  - [7. Exploit Mitigation, Page Pinning & Memory Hardening (ARMv8.5-A MTE, mlock & Dynamic BFU)](#7-exploit-mitigation-page-pinning--memory-hardening-armv85-a-mte-mlock--dynamic-bfu)
  - [8. Sub-Second Spectral Blackout & 180-Minute Faraday Sentinels](#8-sub-second-spectral-blackout--180-minute-faraday-sentinels)
  - [9. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire (GrapheneOS / SELinux Resilient)](#9-pmic-battery-micro-telemetry--anti-disassembly-tripwire-grapheneos--selinux-resilient)
  - [10. Advanced Baseband & IMSI-Catcher / Stingray Sentinel (Modem 2G Masking & Timing Advance)](#10-advanced-baseband--imsi-catcher--stingray-sentinel-modem-2g-masking--timing-advance)
  - [11. Volatile Memory Scrubbing, ZRAM Re-Keying & Plausible Deniability Vault](#11-volatile-memory-scrubbing-zram-re-keying--plausible-deniability-vault)
  - [12. BLE/UWB Proximity Key Sharding Engine (Shamir 2-of-2 Hardware Separation)](#12-bleuwb-proximity-key-sharding-engine-shamir-2-of-2-hardware-separation)
  - [13. Zero-Knowledge Covert Canary Signaling via Oblivious HTTP (OHTTP / RFC 9458)](#13-zero-knowledge-covert-canary-signaling-via-oblivious-http-ohttp--rfc-9458)
  - [14. Decoy App Launcher Tripwires & Honeypot Traps (Wasted Integration)](#14-decoy-app-launcher-tripwires--honeypot-traps-wasted-integration)
  - [15. Decoy Quick Settings Airplane Tile & Direct Action Execution](#15-decoy-quick-settings-airplane-tile--direct-action-execution)
  - [16. Lockscreen Authentication & Anti-Coercion Engine (Salted Platform Bridge)](#16-lockscreen-authentication--anti-coercion-engine-salted-platform-bridge)
  - [17. Multi-User RAM Anti-Forensics & Seamless Decoy Space Migration](#17-multi-user-ram-anti-forensics--seamless-decoy-space-migration)
  - [18. Pre-OS Early Boot Staging, Fastboot & AVB 2.0 Hardening](#18-pre-os-early-boot-staging-fastboot--avb-20-hardening)
  - [19. Autonomous Environmental & Dead-Man Tripwires](#19-autonomous-environmental--dead-man-tripwires)
  - [20. Carrier-Blind Remote Command & Control (Ed25519 Wire, Whitelisted SMS, OTC)](#20-carrier-blind-remote-command--control-ed25519-wire-whitelisted-sms-otc)
  - [21. Covert Surveillance, Evidence Locker & Optical Recording Controls](#21-covert-surveillance-evidence-locker--optical-recording-controls)
- [📡 Remote Signaling & SMS Command Reference](#-remote-signaling--sms-command-reference)
- [🛠️ Technology Stack](#️-technology-stack)
- [📂 Project Directory Structure](#-project-directory-structure)
- [🚀 Deployment & Installation Guide](#-deployment--installation-guide)
  - [Prerequisites](#prerequisites)
  - [Phase 1: Build Release Artifacts](#phase-1-build-release-artifacts)
  - [Phase 2: Deployment Selection](#phase-2-deployment-selection)
    - [Route A: Provision as Device Owner (Locked Bootloader / AVB Enforced)](#route-a-provision-as-device-owner-locked-bootloader--avb-enforced)
    - [Route B: Flashing the Systemless Module (.zip) for Root & LSPosed](#route-b-flashing-the-systemless-module-zip-for-root--lsposed)
  - [Phase 3: Activating the LSPosed Hook (Route B Only)](#phase-3-activating-the-lsposed-hook-route-b-only)
  - [Phase 4: Configuring & Arming Credentials & Sub-OS Engines](#phase-4-configuring--arming-credentials--sub-os-engines)
- [📄 Verification & Diagnostic Checklist](#-verification--diagnostic-checklist)
- [📄 License & Credits](#-license--credits)

---

> [!WARNING]
> ### ⚠️ Legal & Ethical Disclaimer
> **Uncle Ted is engineered strictly for educational research, authorized defensive security auditing, high-risk operational privacy, and human rights protection against physical coercion and forensic seizure.**
> Features such as discrete hardware Secure Element suicide key erasure, JEDEC silicon-level block discarding, low-level FBE metadata zeroing, partition invalidation, kernel keyring eviction, USB PHY differential line severing, and Vold key eviction carry permanent, irreversible consequences. Erasing cryptographic key material renders underlying data mathematically and physically unrecoverable.
> **Never install or deploy this software on any hardware without explicit, informed authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the deployment or operation of this codebase.

---

## 📊 Architectural Comparison: Deployment Profiles

On modern Android (Android 9 through 17 / GrapheneOS), the platform enforces strict security boundaries between unprivileged apps, system services, kernel memory, and hardware execution environments. Uncle Ted operates across two privileged architectural routes: **Route A (Enterprise Device Owner with Locked Bootloader & Enforcing AVB 2.0)** and **Route B (Systemless Priv-App with native LSPosed hooks in `system_server` and early init hooks)**.

| Security Vector / Capability | Standalone APK (Stock OS / Sideloaded) | Route A: Device Owner via ADB (Locked Bootloader & AVB) | Route B: Privileged Root + LSPosed (Unlocked Bootloader) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Bootloader & AVB State** | 🟢 Locked (AVB Enforcing) | 🟢 **100% Locked (AVB Enforcing)** | 🔴 Unlocked (dm-verity unverified) | Route A retains the hardware root of trust; Route B requires an unlocked bootloader for Zygisk, Magisk/KernelSU, and custom kernels. |
| **Hardware KeyMint Isolation** | 🟡 Emulated / TEE | 🟢 **Titan M2 / StrongBox HSM** | 🟡 TEE Degraded / Compromised | Route A uses `setIsStrongBoxBacked(true)` to isolate keys within discrete silicon (Titan M2); Route B hardware attestation fails due to unlocked state. |
| **Fail-Closed Key Decay** | 🔴 Non-Existent | 🟡 Process `mlock()` & Decay Loop | 🟢 **Vold CE Eviction & Keyring Lock** | Route B binds the ephemeral rolling decay cycle directly to `vdc cryptfs lockuser 0` and `sm lock-user-key 0`, evicting keys from Linux kernel RAM. |
| **OPRF Key Decoupling** | 🔴 Inoperable | 🟢 **RFC 9497 EC-OPRF (Fail-Closed)** | 🟢 **RFC 9497 EC-OPRF (Fail-Closed)** | Master storage secret is mathematically decoupled: $K_{master} = K_{local} \oplus \text{OPRF}(PIN, K_{remote})$. Chip-off flash dumps cannot be brute-forced offline. |
| **Silicon-Level Storage Purge** | 🔴 Inoperable | 🟡 Hardware SE Wipe via DPM | 🟢 **True FBE Key Block Crypto-Shred** | Route B shreds actual Vold key directories, destroys 64KB metadata wrappers, and issues `BLKSECDISCARD` / `BLKDISCARD` ioctls directly to UFS/eMMC FTL controllers. |
| **Zero-Latency USB Severing** | 🔴 Impossible | 🟢 **USB HAL v1.3+ Port Severing** | 🟢 **DWC3 / UDC PHY Cut + SysRq Trap** | Route A commands `dpm.setUsbDataSignalingEnabled(false)`; Route B unbinds DWC3 registers instantly upon screen-off and panics on unauthorized protocol queries. |
| **Anti-NAND Mirroring Defense** | 🔴 Non-Existent | 🟢 **Hardware Monotonic Counter Bound** | 🟢 **Atomic Dual-Anchor State Machine** | Route A binds keys to Titan M2 / RPMB rollback resistance; Route B detects counter desync and forces defensive lockdown. |
| **Post-Quantum Cryptography** | 🔴 Classical Only | 🟢 **ML-KEM-768 + X25519 (FIPS 203)** | 🟢 **ML-KEM-768 + X25519 (FIPS 203)** | Hybrid Post-Quantum KEM protects data and covert canaries against "Harvest Now, Decrypt Later" quantum cryptanalysis. |
| **ARMv8.5-A MTE Hardening** | 🔴 Non-Enforced | 🟢 **Synchronous Mode (`sync`)** | 🟢 **Synchronous Mode (`sync`)** | Native layer sets `PR_MTE_TCF_SYNC` via `prctl()`, aborting spatial/temporal memory corruptions immediately via `SIGSEGV`. |
| **Volatile Memory Sanitization** | 🔴 None (OS Swaps Cleanly) | 🟡 Process `mlock()` & Barriers | 🟢 **Kernel `drop_caches` & ZRAM Re-Key** | Route B executes kernel-level cache dropping, page compaction, and ZRAM swap reset on `ACTION_SCREEN_OFF`. |
| **Spectral Faraday Seizure Trap** | 🔴 Slow Timeout | 🟢 **Multi-Link Hysteresis Sentinel** | 🟢 **Multi-Link Hysteresis Sentinel** | Monitors real-time Cellular RSRP, Wi-Fi connectivity, and micro-motion; triggers instant AFU $\rightarrow$ BFU eviction without elevator false alarms. |
| **PMIC Battery Disassembly Guard** | 🔴 Unsupported | 🟡 Thermal Gradient Tracking | 🟢 **BMS $R_{int}$ & Thermal Micro-Telemetry** | Detects external DC bench supply micro-clamp attachment ($\Delta R > 35\text{ m}\Omega$) with automatic SELinux failure-latching on hardened kernels (GrapheneOS). |
| **Baseband / Stingray Defense** | 🔴 Vulnerable to 2G Force | 🟢 **Modem-Level 2G Masking (API 34)** | 🟢 **RIL Power Cut & Timing Advance Trap** | Strips 2G from modem firmware bitmasks; detects impossible RF topologies (high RSRP with high Timing Advance on registered cells) and cuts RIL power. |
| **Proximity Key Sharding** | 🔴 Single-Device Keys | 🟢 **BLE/UWB Shamir 2-of-2 Sharding** | 🟢 **BLE/UWB Shamir 2-of-2 Sharding** | Master secrets split across StrongBox (Shard A) and an external BLE wearable (Shard B); key evaporates if separated $> 2\text{ m}$. |
| **Covert Canary Signaling** | 🔴 Cleartext HTTP / Webhooks | 🟢 **RFC 9458 OHTTP / Masquerade** | 🟢 **RFC 9458 OHTTP / Masquerade** | Dispatches HPKE-encrypted distress blobs disguised as standard Android telemetry to CDN relays or gateways, hiding client IP and content without self-inflicted radio cutoff. |
| **Lockscreen Interception** | 🔴 Non-Functional | 🟡 **Fail Callback / In-App Guard** | 🟢 **100% Native Hook (Salted Hash)** | Route B intercepts `LockSettingsService` directly inside `system_server` using salted hashes (Format v2). Route A relies on Gatekeeper failure callbacks and hardware wipe limits. |
| **Multi-User RAM Anti-Forensics** | 🔴 Impossible | 🟡 Work Profile Segregation | 🟢 **In-Process Decoy User Migration** | Route B transitions session instantly via `IActivityManager` and evicts User 0 caches, switching to Decoy Space without leaving the primary user exposed. |
| **Overall Defense Posture** | **3.0 / 10** | **9.9 / 10** | **9.8 / 10** | Route A delivers maximum hardware security, AVB chain of trust, and discrete HSM isolation; Route B delivers native OS-level lockscreen control, kernel bus manipulation, and sub-OS key shredding. |

---

## 🏗️ System Architecture (Dual-Profile & Sub-OS Engine)

Uncle Ted v10.0.1 features a decoupled, strategy-based architecture coordinated by `DefenseCoordinator`. The platform dynamically analyzes execution privileges, hardware security module availability, and bootloader status at startup, binding the runtime to the optimal defensive strategy:

```
                                  ┌───────────────────────────────┐
                                  │   OPERATIONAL THREAT SIGNAL   │
                                  │ (Keyguard, Decoy, BLE, PMIC)  │
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
 │ - Titan M2 StrongBox KeyMint & Anti-Rollback    │   │ - In-Process Atomic Decoy User Space Migration  │
 │ - Out-of-Band OPRF Key Decoupling (RFC 9497)    │   │ - Out-of-Band OPRF Key Decoupling (RFC 9497)    │
 │ - Fail-Closed Ephemeral Rolling Key Buffer      │   │ - Fail-Closed Ephemeral Rolling Key + Vold Lock │
 │ - NIST FIPS 203 ML-KEM-768 + X25519 PQC Engine  │   │ - JEDEC BLKSECDISCARD / BLKDISCARD IOCTL        │
 │ - Native setUsbDataSignalingEnabled(false) HAL  │   │ - True FBE 4KB Key Block Shredding (/metadata)  │
 │ - Decoy Messenger Launcher Tripwires (Wasted)   │   │ - Zero-Latency USB PHY Cut + SysRq Panic Trap   │
 │ - Direct & Immediate Decoy Airplane Execution   │   │ - Stage-2 Post-Mount USB Kill (/data/adb/)      │
 │ - Standard Platform Wipe vs Silicon Shred       │   │ - Linux Kernel UDC Gadget Controller Severing   │
 │ - Unified dpm.wipeDevice() (Android 14+ / DO)   │   │ - Ephemeral ZRAM Swap Eviction & drop_caches    │
 │ - Dynamic Direct Boot (BFU) Lifecycle Discovery │   │ - Dynamic Direct Boot (BFU) Lifecycle Discovery │
 │ - Native DISALLOW_CELLULAR_2G Restriction       │   │ - Raw Modem RIL Power Cut via Shell             │
 │ - RFC 9458 Oblivious HTTP (OHTTP) Covert Canary │   │ - RFC 9458 Oblivious HTTP (OHTTP) Covert Canary │
 │ - BLE/UWB Shamir 2-of-2 Proximity Sharding      │   │ - BLE/UWB Shamir 2-of-2 Proximity Sharding      │
 │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │   │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │
 └─────────────────────────────────────────────────┘   └─────────────────────────────────────────────────┘
```

---

## ✨ Core Capabilities

### 1. Hardware Security Module & Anti-Rollback Suicide Key (Titan M2 / StrongBox)
- **Discrete Silicon KeyMint (`StrongBoxSecurityManager`):** Protects application databases, sensitive preferences, and credential hashes using an AES-256-GCM master key provisioned inside discrete hardware silicon (`setIsStrongBoxBacked(true)`), isolated from the primary application processor.
- **Hardware Monotonic Anti-Rollback Binding (`AntiRollbackManager`):** Protects cryptographic integrity using atomic state files (`AtomicFile`) and monotonic sequence verification with tolerance thresholds. Detects state rewinds indicative of **NAND Mirroring** (desoldering the UFS flash chip to perform PIN dictionaries and rewinding state) and executes defensive lockdown.
- **Sub-10ms Cryptographic Suicide:** In duress or catastrophic compromise scenarios, Uncle Ted calls `KeyStore.deleteEntry(MASTER_SUICIDE_KEY_ALIAS)`. Deleting this silicon-level register takes under 10 milliseconds and makes all encrypted databases, credentials, and offline caches permanently unrecoverable, rendering physical flash dump analysis mathematically futile.
- **Weaver Hardware Rate-Limiting Weaponization:** In Route A, sets `dpm.setMaximumFailedPasswordsForWipe(admin, 3)`. The Titan M / Weaver chip enforces exponential backoffs and autonomously commands KeyMint to revoke root Key Encryption Keys (KEKs) upon 3 consecutive authentication failures, executing independently of userspace runtime health or battery state.

---

### 2. Inverted Dead-Man Architecture: "Fail-Closed" Ephemeral Keys
- **The "Fail-Closed" Paradigm (`EphemeralKeyDecayEngine`):** Solves the 4-second race condition where active wipe scripts fail to complete if power is abruptly severed. Sensitive encryption keys are never stored statically in memory; they reside in a volatile, rolling memory buffer pinned via `mlock()`.
- **Entropy-Driven Rolling Heartbeat:** Requires a continuous 1,500ms heartbeat pulse fed by dynamic, real-time physical entropy: accelerometer sensor jitter ($x, y, z$ floating-point variations), modem telephony network state shifts, high-resolution monotonic clock drift (`elapsedRealtimeNanos`), and wall-clock offsets.
- **Natural Volatile Evaporation:** If the device is isolated in a Faraday enclosure, frozen, or power is cut, the heartbeat decay counter expires (default: 3 missed cycles / 4,500ms). The keys evaporate naturally via volatile pointer loops (`burnMemory`).
- **Direct Linux Kernel FBE Key Eviction:** Unlike superficial userspace variables, key decay triggers system-level storage key revocation:
  ```bash
  vdc cryptfs lockuser 0
  sm lock-user-key 0
  sync
  echo 3 > /proc/sys/vm/drop_caches
  input keyevent 26
  ```
  Credential-Encrypted (CE) keys are immediately evicted from the Linux kernel keyring (`fscrypt`), dropping all filesystem caches and locking the device into cold BFU state.

---

### 3. Out-of-Band Key Decoupling via OPRF (RFC 9497 / secp256r1)
- **Eliminating Offline Dictionary Attacks (`OprfClientEngine` & `OprfPreferences`):** Commercial forensic extraction boxes (Cellebrite, GrayKey) dump raw flash memory via EDL 9008 mode and brute-force 4-to-6 digit PINs offline using GPU arrays. Uncle Ted mathematically decouples the master storage secret so **the phone never possesses the full key on disk**:
  $$K_{master} = \text{HKDF-SHA512}(K_{local} \oplus \text{OPRF}(PIN, K_{remote}))$$
- **RFC 9497 Compliant Hash-to-Curve Math:** Implements mathematically sound Elliptic Curve Oblivious Pseudorandom Functions over the prime-order curve `secp256r1` (NIST P-256):
  1. *Client Blinding:* Hashes PIN into a non-zero scalar $s = \text{SHA256}(PIN) \pmod N$, maps to point $M = G \cdot s$, generates random blinding scalar $r \in [1, N-1]$, and computes blinded point $B = M \cdot r$.
  2. *Server Evaluation:* The remote rate-limiting sentinel evaluates $E = B \cdot k_{remote}$ and returns $E$.
  3. *Client Unblinding:* Computes modular scalar inversion $r^{-1} \pmod N$ via `BigInteger.modInverse(groupOrderN)` and unblinds $S = E \cdot r^{-1} = M \cdot k_{remote}$.
  4. *Key Combination:* Combines unblinded PRF output with local secret share $K_{local}$ to derive the master symmetric key.
- **Strict Fail-Closed Enforcement:** Removes all offline fallback backdoors. If the device cannot reach the remote OPRF evaluation endpoint (e.g., inside a Faraday bag), the derivation pipeline strictly fails closed, returning `null` and locking the vault.

---

### 4. Zero-Latency Hardware USB PHY Annihilation (The Trapdoor Port)
- **Proactive Differential Line Severing (`UsbTrapdoorController`):** Solves the race condition where asynchronous pollers are outpaced by microsecond USB exploits. Rather than waiting for a connection to establish, Uncle Ted **proactively severs the physical USB data lines the millisecond the screen turns off**:
  - *Route A (Device Owner):* Calls `dpm.setUsbDataSignalingEnabled(false)` via Android 12+ USB HAL v1.3+, physically powering off D+/D- and SuperSpeed RX/TX differential signaling lines at the hardware layer.
  - *Route B (Root / Kernel):* Directly unbinds Qualcomm `dwc3` and Linux USB Device Controller (UDC) register endpoints upon `ACTION_SCREEN_OFF`:
    ```bash
    setprop sys.usb.config none
    setprop sys.usb.state none
    echo '' > /config/usb_gadget/g1/UDC
    for udc in /sys/class/udc/*; do echo '' > "$udc/state"; done
    for mode in /sys/devices/platform/soc/*.dwc3/mode; do echo 'none' > "$mode"; done
    ```
- **Charge-Only Hardware Isolation:** Transforms the USB-C port into a dedicated charge-only interface while locked.
- **100ms Fast-Path Intrusion Trap:** A high-frequency interrupt monitor inspects UDC controller binding states and upstream power supply negotiation (`/sys/class/power_supply/usb/type`). If an unauthorized forensic box attempts host protocol enumeration (SDP, CDP, ADB, or Fastboot), the system triggers an immediate **unconditional hardware kernel panic** (`echo c > /proc/sysrq-trigger`) and PMIC hard reset, forcing the SoC into cold BFU in milliseconds.

---

### 5. True FBE 4KB Key Block Crypto-Shredding, Standard Wipe & JEDEC Discard
- **Bypassing Flash Wear-Leveling (`FastCryptoShredEngine`):** Overwriting raw flash blocks via `dd` or `shred` fails because the Flash Translation Layer (FTL) remaps logical blocks across overprovisioned spare NAND cells. Furthermore, overwriting block 0 of `userdata` merely corrupts the filesystem superblock, leaving underlying FBE file extents intact.
- **Direct File-Based Encryption Key Shredding:** Targets and destroys the actual wrapped Key Encryption Keys (KEKs) and synthetic password blobs:
  ```bash
  rm -rf /data/misc/vold/user_keys/*
  rm -rf /metadata/vold/user_keys/*
  rm -rf /data/system_de/0/spblob/*
  rm -rf /data/system/gatekeeper.*.key
  rm -rf /data/system/users/0/*.key
  rm -rf /data/system/locksettings.db*
  ```
- **Unified Android 14+ Whole-Device Wipe (`DeviceOwnerStrategy`):** On Android 14 (API 34+) and GrapheneOS, calls `dpm.wipeDevice()` with `WIPE_EXTERNAL_STORAGE` and `WIPE_SILENTLY`, preventing the fatal `IllegalStateException: User 0 is a system user and cannot be removed` caused by deprecated `wipeData()` calls on modern platforms.
- **Distinct Separation between Standard Wipe & Silicon Shred:** Uncle Ted introduces distinct separation between standard platform factory resets (`dpm.wipeDevice(0)` / standard BCB staging) and lethal silicon discard across all UI dialogs, triggers, and tiles.
- **64KB Metadata Partition Zero-Fill:** Zero-fills the primary File-Based Encryption metadata partition (`/dev/block/by-name/metadata`) using `RandomAccessFile` in synchronous `rws` mode and root shell `conv=fsync`, obliterating wrapped keys and directory encryption tables.
- **JEDEC Silicon Discard Dispatch:** Issues hardware `BLKSECDISCARD` and `BLKDISCARD` IOCTLs directly to `/dev/block/by-name/metadata` via native C++ and root shell execution. Forces the UFS/eMMC memory controller to raise NAND cell voltages to physical erase levels, rendering 256GB of underlying storage mathematically indistinguishable from random noise in under 15 milliseconds.
- **Bootloader Control Block (BCB) Formatting Marker:** Writes `--wipe_data\n--reason=UncleTed_FBE_CryptoShred` directly into `/cache/recovery/command`, ensuring that even if userspace execution halts mid-wipe, the recovery partition formats userdata on the subsequent boot cycle.

---

### 6. NIST FIPS 203 Post-Quantum Cryptographic Hybrid Engine (ML-KEM-768 + X25519)
- **Quantum-Resistant KEM Architecture (`PostQuantumEngine`):** Integrates the finalized NIST FIPS 203 post-quantum standard **ML-KEM-768** (CRYSTALS-Kyber) operating in tandem with classical **Curve25519 (X25519)**.
- **Immunity to "Harvest Now, Decrypt Later" (HNDL):** Adversaries capturing distress signals or encrypted storage archives cannot retroactively decrypt evidentiary dossiers or alert metadata using Shor's algorithm on future Cryptanalytically Relevant Quantum Computers (CRQCs).
- **HKDF-SHA512 Combiner:** Derives master 256-bit symmetric operational keys via HMAC-SHA512 combining classical ECDH shared secrets with lattice-based KEM decapsulation secrets over strict domain-separated salt anchors.
- **Hardware-Sealed Private Keys:** Local post-quantum private keys are encrypted at rest with the discrete StrongBox master key inside `CryptoPreferences`, preventing plaintext extraction from flash dumps.

---

### 7. Exploit Mitigation, Page Pinning & Memory Hardening (ARMv8.5-A MTE, mlock & Dynamic BFU)
- **Synchronous Hardware Memory Tagging:** Enforces `android:memtagMode="sync"` at the application level and injects `-march=armv8.5-a+memtag -fsanitize=memtag` into Clang native compilation. Memory allocations on ARMv8.5-A+ silicon (Tensor G3/G4/G5, Snapdragon 8 Gen 3+) are assigned 4-bit metadata tags; any spatial overflow or use-after-free pointer dereference triggers an instant hardware `SIGSEGV` (`SEGV_MTESERR`), stopping memory corruption exploits.
- **Dynamic Direct Boot Lifecycle Guarding:** Dynamically queries `SecurityPreferences.isUserUnlocked(activity)` per-activity in `onActivityPreCreated()`, eliminating stale BFU closure retention when the OS starts in locked state and transitions to unlocked state later.
- **Process Memory Locking (`mlock`):** Pins critical plaintext byte buffers, PIN arrays, and intermediate cryptographic secrets in physical LPDDR5 RAM via `mlock()`, preventing the Android OS from swapping sensitive memory pages into unencrypted storage or dirty swap.
- **Native Process Sandboxing (`NativeSecurityBridge`):** Calls `prctl(PR_SET_DUMPABLE, 0)` via JNI during initialization, blocking `/proc/$PID/mem` extraction, local memory inspection, and unauthorized debugger attachments (`ptrace`/`lldb`).
- **Compiler Dead-Store Protected Zeroing:** Employs volatile C++ pointer zeroing loops with memory barriers (`burnMemory` / `secureZeroMemory`) and `std::atomic_thread_fence` to purge sensitive plaintexts, cryptographic keys, and intermediate PIN buffers from heap memory.

---

### 8. Sub-Second Spectral Blackout & 180-Minute Faraday Sentinels
- **Multi-Link Spectral Collapse Sentinel (`SpectralSentinel`):** Monitors sudden RF link parameter collapses across registered Cellular (RSRP < -135 dBm) and active Wi-Fi connections while the device is in physical motion without Airplane Mode activation. Employs a 15-second quarantine hysteresis window to eliminate false positives in elevators, subways, and basements before triggering AFU $\rightarrow$ BFU eviction.
- **180-Minute Autonomous Faraday Sentinel (`FaradayBlackoutSentinel`):** Operates via `AlarmManager.setExactAndAllowWhileIdle()` backed by hardware `ELAPSED_REALTIME_WAKEUP`. If the phone is isolated inside an evidence locker or Faraday container past the configured threshold, the CPU wakes from deep doze and executes an autonomous BFU key eviction.
- **Native DO BFU Reversion:** In Route A (Device Owner), calls `dpm.reboot()`, immediately purging all Credential-Encrypted (CE) keys from volatile RAM and leaving the device in cold Before First Unlock state where keys exist only as sealed silicon registers inside Titan M2.

---

### 9. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire (GrapheneOS / SELinux Resilient)
- **Hardware Power Management IC Interrogation (`PmicTamperSentinel`):** Forensic laboratories bypass timeout watchdogs by opening the device chassis, cutting the battery lead, and splicing an external DC bench power supply directly across the battery terminals (VBAT) to indefinitely sustain AFU state.
- **GrapheneOS & Hardened AOSP SELinux Failure-Latch:** On hardened operating systems where battery SysFS nodes under `/sys/class/power_supply/battery` are blocked from third-party app domains, the sentinel detects the denial once, latches `isSupported = false` persistently in Device-Protected storage, and ceases all subsequent filesystem probing. This completely stops continuous SELinux audit log spam (`avc: denied`) and eliminates UI thread frame drops.
- **Internal Impedance ($R_{int}$) Step-Jump Detection:** Samples Battery Management System (BMS) SysFS nodes with adaptive moving-average baselines when supported on IO threads. Connecting external power supply clamps produces an abrupt electrochemical impedance shift exceeding $\Delta R > 35\text{ m}\Omega$, triggering cryptographic suicide.
- **Thermal Gradient Shock ($dT/dt$):** Monitors the battery pack's NTC thermistor resting against the rear enclosure. Heating and prying off the rear glass causes a rapid thermal drop ($\Delta T > 12.0^\circ\text{C}$), detecting enclosure unsealing with calm-down verification to prevent false alarms from cold weather.

---

### 10. Advanced Baseband & IMSI-Catcher / Stingray Sentinel (Modem 2G Masking & Timing Advance)
- **Zero-Trust 2G Stripping via `UserManager.DISALLOW_CELLULAR_2G`:** In Route A on Android 14+ (API 34+), enforces 2G cellular blocking at the platform policy layer via `dpm.addUserRestriction(admin, UserManager.DISALLOW_CELLULAR_2G)`. Eliminates previous `SecurityException` carrier privilege denials on unlocked retail handsets.
- **Modem-Level Firmware Bitmask Stripping:** Concurrently applies Android 12+/14+ `TelephonyManager.setAllowedNetworkTypesForReason()` on IO threads to strip 2G bitmasks (`GSM`, `GPRS`, `EDGE`, `CDMA`, `1xRTT`) where carrier privileges are available.
- **Impossible RF Topology / Timing Advance Trap:** Cell-site simulators (Stingrays) transmit high RF power to override legitimate towers while introducing artificial propagation delays. Uncle Ted monitors registered serving cell telemetry; detecting high-power signals ($\text{RSRP} > -65\text{ dBm}$) paired with extreme Timing Advance ($\text{TA} > 30$, representing $> 2.3\text{ km}$) identifies a rogue transceiver, triggering an immediate radio cutoff.
- **Hardware RIL Power Cut:** In Route B, issues low-level Telephony IPC service commands (`service call phone 83 i32 0`) to disconnect power from the cellular baseband bus entirely. In Route A, enforces instant global airplane mode isolation.

---

### 11. Volatile Memory Scrubbing, ZRAM Re-Keying & Plausible Deniability Vault
- **Volatile RAM Hardening Engine (`MemoryHardeningEngine`):** Dynamically bound to `Intent.ACTION_SCREEN_OFF`. Commands the Linux kernel to drop pagecaches, dentries, and unpinned inodes (`echo 3 > /proc/sys/vm/drop_caches`) and compact memory to eliminate unallocated plaintext fragments without blocking broadcast threads.
- **Ephemeral ZRAM Swap Flushing:** Flushes dirty anonymous pages (`swapoff /dev/block/zram0`), resets the swap device block allocator, and re-initializes ZRAM swap with fresh random cryptographic keys upon screen lock.
- **Deterministic Polyglot Vault (`PlausibleDeniabilityVault`):** Compresses sensitive data with Deflate, derives deterministic symmetric keys using StrongBox Master Seeds with per-container salts via **HKDF-SHA256**, encrypts via ChaCha20-Poly1305, and shapes entropy ($H \approx 7.2\text{--}7.5\text{ bits/byte}$) into a structurally valid Adobe DNG RAW camera image container stored in `Pictures/Camera`. Fully decryptable on demand in the Evidence Locker, yet statistically indistinguishable from an ordinary camera file to forensic carvers (`bulk_extractor`, Autopsy).

---

### 12. BLE/UWB Proximity Key Sharding Engine (Shamir 2-of-2 Hardware Separation)
- **Information-Theoretic Key Separation (`ProximityShardingEngine`):** Splits master operational secrets into two Shamir 2-of-2 additive secret shares ($S = S_A \oplus S_B$). Shard A is sealed inside the Titan M2 discrete StrongBox Keystore. Shard B is transmitted to a paired hardware token (smartwatch, fitness band, or ring) and kept strictly in pinned volatile RAM on the phone.
- **Live Decryption Binding (`AdvancedCrypto`):** Sensitive data and encrypted stores dynamically reconstruct the symmetric key from RAM Shard B. If the operator is separated from their phone by $> 2\text{ meters}$ (or remote RSSI drops below $-85\text{ dBm}$ for 3 consecutive cycles), Shard B evaporates via native memory barriers (`burnMemory`) and all data becomes cryptographically un-decryptable until the wearable reconnects.
- **Authenticated BLE GATT Heartbeat (`BleProximitySentinel`):** Dispatches bidirectional authenticated GATT heartbeats every 1,500 ms with automatic reconnection and distance tracking.

---

### 13. Zero-Knowledge Covert Canary Signaling via Oblivious HTTP (OHTTP / RFC 9458)
- **Carrier-Blind Network Signaling (`CovertCanarySender`):** Coerced users subjected to active network sniffing or Wi-Fi hardware taps cannot safely dispatch distress alerts to obvious IPs or mail servers. Uncle Ted implements **RFC 9458 Oblivious HTTP (OHTTP)** paired with **RFC 9180 Hybrid Public Key Encryption (HPKE)**.
- **Dual-Mode Dispatch Engine:**
  - *Mode A (True RFC 9458 OHTTP):* Formats compliant binary HTTP requests (`message/bhttp`) routed to oblivious CDN relays (Cloudflare/Fastly). The relay sees the user's IP but cannot read the payload; the destination gateway decrypts the payload with its private key but learns zero knowledge of the client's true IP.
  - *Mode B (Google Telemetry Masquerade):* Shapes HPKE/ML-KEM-768 encrypted distress payloads into genuine Google Play Services / Firebase Analytics JSON diagnostic schemas over TLS 1.3. Local network observers see only an ordinary Google analytics request.
- **Non-Severing Radio Sequencing:** During silent duress triggers, Uncle Ted retains network and cellular radio interfaces until distress blobs, emails, and SMS alerts are transmitted, preventing self-inflicted communications dropouts.

---

### 14. Decoy App Launcher Tripwires & Honeypot Traps (Wasted Integration)
- **Zero-Bitmap Native Vector Launcher Traps (`DecoyAppActivity` & `DecoyAppManager`):** Integrates and expands the deception matrix inspired by *Wasted*. Deploys authentic fake messenger icons (**WhatsApp**, **Signal**, **Telegram**, **Threema**, **Session**) directly to the launcher home screen.
- **100% Vector Adaptive Icons:** Implements resolution-independent XML VectorDrawables and Adaptive Icons (`<adaptive-icon>`) for all decoy apps, eliminating external raster PNG dependencies and rendering sharp icons on all display scales.
- **Dynamic Alias Management:** Toggles component states dynamically via `PackageManager.setComponentEnabledSetting()` using non-restarting flags (`DONT_KILL_APP`).
- **User-Configurable Defensive Action Pipelines:** Opening any enabled decoy app immediately executes the configured defensive strategy:
  1. *Immediate Silicon Wipe (Lethal):* Destroys discrete StrongBox suicide keys and triggers kernel block zeroing.
  2. *Standard Platform Wipe:* Invokes standard Android factory reset via Device Owner.
  3. *Silent Duress Canary & Capture:* Silently captures dual-camera photos, room audio, and GPS coordinates while firing an OHTTP distress frame.
  4. *Immediate Lock & BFU Reversion:* Evicts Vold CE keys from memory and locks the device.
  5. *Migrate to Isolated Decoy Space:* Seamlessly switches OS session to secondary user profile (`UserHandle(10)` or `11`).

---

### 15. Decoy Quick Settings Airplane Tile & Direct Action Execution
- **Direct Decoy Execution (`FakeAirplaneTileService`):** Provides an authentic "Airplane mode" Quick Settings tile that can be activated from the notification shade or lockscreen.
- **Configurable Confirmation Barrier:** If the optional PIN challenge is disabled, tapping the tile immediately executes the configured action (e.g. silent duress, standard wipe, or BFU lock) without displaying an intrusive confirmation dialog.
- **Safety Bypass for Device Owner:** If the PIN challenge is enabled, the true owner can enter their normal PIN to dismiss the tile safely without triggering countermeasures.

---

### 16. Lockscreen Authentication & Anti-Coercion Engine (Salted Platform Bridge)
- **Route B (Native LSPosed Hook with Format v2 Bridge):** Hooks `com.android.server.locksettings.LockSettingsService` directly inside `system_server`:
  - **Zero-Plaintext Salted Storage:** Credentials in `/data/system/uncleted/credentials.cfg` are stored strictly as salted SHA-256 hashes (`format_version=2`) with `0600` permissions. Plaintext PINs are completely eliminated from disk.
  - **Constant-Time Verification:** Compares entered credentials using constant-time hash comparisons (`MessageDigest.isEqual`), eliminating side-channel timing attacks.
  - **Normal PIN:** Validates authentication, clears failed attempt counters, and unlocks the primary profile.
  - **Duress PIN (Silent Canary Trap):** Halts authentication at the framework level, renders an authentic "Wrong PIN" feedback state on Keyguard, and silently triggers covert camera, audio, and GPS dispatch with zero screen flickering.
  - **Wipe PIN:** Terminates Keyguard authentication, destroys the discrete StrongBox suicide key, zeros File-Based Encryption metadata headers via JEDEC discard, and stages an autonomous recovery wipe.
  - **Honeypot PIN (Instant Migration):** Authenticates the operator, dismisses the Keyguard, and atomically transitions the active session to the secondary Decoy Space without exposing User 0 storage.
- **Route A (Device Owner / Gatekeeper Integration):**
  - Enforces failed passcode limits directly on the hardware Gatekeeper/Weaver chip.
  - Disables biometric authenticators (fingerprint/face) automatically upon consecutive failures, locking Keyguard down to complex passphrases and preventing forced biometric unlock.
  - Safely bypasses non-root `/data/system` write attempts to eliminate false sync warnings.

---

### 17. Multi-User RAM Anti-Forensics & Seamless Decoy Space Migration
Standard multi-user switching leaves the primary owner's (User 0) Credential-Encrypted (CE) keys resident in the Linux kernel keyring, exposing them to cold-boot RAM acquisition. Uncle Ted implements **true RAM anti-forensics and atomic profile switching**:
- **Atomic In-Process Session Switch:** When the Honeypot PIN is entered on Keyguard, `LockscreenHook` directly invokes `IActivityManager.switchUser(decoyUserId)` inside `system_server`. The bouncer dismisses and the screen transitions immediately into Decoy Space.
- **Non-Destructive Package Disabling:** `DecoyUserManager` utilizes `pm disable --user` and `pm hide` instead of destructive uninstallation (`pm uninstall -k`), preventing `system_server` crashes in `HealthConnect`, `NotificationManagerService`, and `MidiService`.
- **Multi-User Early Boot Injection:** Module packager injects `system.prop` (`fw.max_users=5`, `fw.show_multiuserui=1`) directly into the `init` environment, ensuring `system_server` boots with multi-user capabilities active on Android 9 through 17.
- **Authentic Multi-User Decoy:** Moves the active OS session to a genuine secondary Android user profile (`UserHandle(10)` or `11`) named `"Personal"` backed by its own `/data/user/10` directory, separate encryption keys, distinct launcher, and decoy apps (`FakeBankingActivity`, `FakeNotesActivity`, `FakeGalleryActivity`).

---

### 18. Pre-OS Early Boot Staging, Fastboot & AVB 2.0 Hardening
- **Init Stage-2 Post-Mount Hardware Cutoff (`BootloaderHardeningHelper`):** Deploys executable root init scripts directly into `/data/adb/post-mount.d/00_uncleted_early_usb_kill.sh` with `0755` permissions. Ensures Qualcomm DWC3 registers and USB gadget state are severed during Stage-2 initialization before Zygote, system daemons, or `adbd` can spin up.
- **Recovery Booby-Trap Staging:** Writes direct BCB format directives to `/cache/recovery/command` (`--wipe_data\n--reason=UncleTed_Recovery_BoobyTrap`), neutralizing unauthorized recovery boots intended to mount `/data`.
- **Authentic AVB 2.0 Hardware Root of Trust:** Provides verified cryptographic workflows to re-lock custom firmware under user-owned cryptographic keys (`fastboot flashing set-installed-pkg-key pkmd.bin` and `fastboot flashing lock`), enforcing green verified boot state and blocking physical kernel RAM injection (`fastboot boot`).

---

### 19. Autonomous Environmental & Dead-Man Tripwires
- **Autonomous BFU Dead-Man Sentinel (`TripwireManager`):** Operates exclusively within Device-Protected (DE) storage using `AlarmManager.setExactAndAllowWhileIdle()` configured for hardware RTC wakeup. Evaluates elapsed time directly upon `LOCKED_BOOT_COMPLETED`; if the device was seized, powered down, or isolated in a Faraday bag past the threshold, an immediate cryptographic wipe executes in BFU state.
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` bypasses confirmation dialogs and triggers emergency erasure.
- **Multi-Zone Geographic Suicide (`PolygonUtils` & `ZoneWipeService`):** High-accuracy GPS sentinel operating with a Ray-Casting Point-in-Polygon algorithm using half-open latitude intervals to eliminate boundary errors:
  - *Pre-Configured Boundary:* Built-in perimeter covering Evin Prison.
  - *Custom Wipe Zones:* User-defined circular radius boundaries or polygon perimeters configured via UI or current GPS fix.
  - *Safety Guardrails:* Rejects coordinates with an uncertainty radius $> 30\text{ m}$ and requires 3 consecutive breach samples to eliminate false positives from multipath drift.
- **SIM Hardware Sentinel:** Detects changes in the hardware identity of the SIM card across Android 9 through 17 without throwing `SecurityException`, instantly locking the device and dispatching alert telemetry.

---

### 20. Carrier-Blind Remote Command & Control (Ed25519 Wire, Whitelisted SMS, OTC)
Uncle Ted implements a multi-tier remote signaling engine designed to operate in BFU state without leaking operational intent to cellular carriers:
- **Mode 1: Ed25519 Cryptographic Envelope (`!UT:<Base64>`):** Compact 85-byte binary packet signed by the operator's offline asymmetric private key. Enforces a 120-second timestamp drift window and strict monotonic sequence counters to eliminate replay attacks. Carrier logs capture only high-entropy random noise:
  - `0x01` : Emergency Cryptographic Wipe & BCB Staging (`OP_EMERGENCY_WIPE`)
  - `0x02` : Sever Hardware USB Port & Lock Keyguard (`OP_SEVER_USB_AND_LOCK`)
  - `0x03` : Evict Keys to BFU State & Drop Kernel Keyring (`OP_EVICT_KEYS_TO_BFU`)
  - `0x04` : Trigger Covert Evidence Capture & Telemetry (`OP_CAPTURE_EVIDENCE`)
- **Mode 2: Single-Use Emergency Recovery Tokens (OTC):** Generates a batch of 5 high-entropy emergency recovery tokens (e.g., `!UT:OTC-W1-XXXX-XXXX-XXXX`) stored in Device-Protected storage. Texting a token from any basic phone immediately destroys all keys and permanently burns the token.
- **Mode 3: Notification Listener C2 (`NotificationCommandListener`):** Intercepts push notifications containing cryptographic binary frames or OTC tokens. Allows devices on data-only eSIM plans (without SMS support) to receive secure remote wipes.
- **Mode 4: Whitelisted Permissive Burner Fallback (`UNCLETED [CMD] [PASSWORD]`):** Legacy command format for use with basic/analog phones. Strictly validates the incoming sender phone number against the configured `Emergency Contact` to prevent unauthorized wipes from third parties.
- **Automated Inbox Cleansing:** Purges incoming command SMS records from `content://sms` using elevated shell commands and ContentResolver queries to eliminate forensic traces of signaling.

---

### 21. Covert Surveillance, Evidence Locker & Optical Recording Controls
- **Integrated Evidence Locker & Media Vault (`EvidenceGalleryActivity`):** Complete in-app media gallery and viewer activity allowing operators to preview captured intruder photos (`.jpg`), surface screenshots (`.png`), high-definition video recordings (`.mp4`), and ambient audio (`.m4a`). Includes on-demand ChaCha20-Poly1305 decoding for DNG polyglot vaults, category filtering chips, and JEDEC silicon-level file shredding.
- **Granular Recording Length & Lens Controls:** User-configurable recording duration menus in `SurveillanceFragment` for video (5s to 60s) and audio (15s to 300s), with independent toggles for Front Camera and Rear Camera capture to prevent unnecessary sensor cycling.
- **Hardware CameraX Mutex & Deadlock Elimination:** Enforces strict asynchronous mutex serialization (`hardwareCameraLock`) across photo and video pipelines, active observer synchronization on `CameraState.Type.OPEN`, and a 400ms HAL cooldown buffer between closing the front lens and opening the rear lens to eliminate Qualcomm HAL concurrency deadlocks.
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` running in `RESUMED` state to capture high-resolution front- and back-camera photos, followed by video clips.
- **Android 14 BAL & FGS Hardening:** Employs an active completion broker (`CameraPermissionBrokerActivity`) paired with request-ID matching broadcasts (`ACTION_MEDIA_CAPTURE_COMPLETED`), shell extra forwarding in `GodMode`, and an extended 60-second watchdog to prevent background camera captures from being killed prematurely.
- **Hybrid Input Surveillance:** Intercepts physical hardware inputs (Volume, Power) via `/dev/input/` events (`getevent -l`) while capturing soft-keyboard typing through the Accessibility event bus.
- **Ambient Audio Surveillance:** Direct-to-disk MPEG-4 AAC audio capture (`.m4a`) using `MediaRecorder` at user-configured recording intervals.
- **Stealth Screenshot (Root):** Directly reads surface buffers via `/system/bin/screencap` without generating UI flashes or notification badges.

---

## 📡 Remote Signaling & SMS Command Reference

### Mode 1: Cryptographic Envelope (Ed25519)
```text
!UT:[85_BYTE_BASE64_PAYLOAD]
```

### Mode 2: Single-Use Emergency Recovery Token (OTC)
```text
!UT:OTC-W1-[XXXX-XXXX-XXXX]
```

### Mode 3: Oblivious HTTP Covert Canary (RFC 9458)
Dispatched automatically over TLS 1.3 to configured OHTTP CDN Relays or Google Telemetry Masquerade endpoints:
```http
POST /dns-query HTTP/1.1
Host: cloudflare-dns.com
Content-Type: message/bhttp
User-Agent: Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230805.019)

[BINARY HPKE / ML-KEM-768 ENCAPSULATED PAYLOAD]
```

### Mode 4: Permissive Burner Fallback (Whitelisted Senders Only)
```text
UNCLETED [COMMAND] [SMS_MASTER_PASSWORD] [OPTIONAL_ARGS]
```

| Command | Arguments | Severity | Description |
| :--- | :--- | :---: | :--- |
| `WIPE` | *None* | `CRITICAL` | Bypasses evidence collection and triggers immediate platform wipe & key eviction. |
| `EVIDENCE`| *None* | `HIGH` | Gathers front/back photos, video, audio, and emails the complete dossier. |
| `SIREN` | *None* | `HIGH` | Maximizes alarm streams and loops a loud emergency siren while vibrating. |
| `LOCK` | *None* | `LOW` | Immediately closes running tasks and launches the secure lockscreen activity. |
| `LOCATE` | *None* | `LOW` | Requests high-accuracy GPS coordinates and replies via SMS and email. |
| `AUDIO` | `[seconds]` | `HIGH` | Records ambient room audio for specified seconds and emails the file. |
| `SPEAK` | `[text]` | `MEDIUM` | Maximizes volume and reads text aloud using the device's Text-to-Speech engine. |
| `REBOOT` | *None* | `HIGH` | (Root Only) Forces an immediate hardware reboot (`/system/bin/reboot`). |
| `SCREENSHOT`| *None* | `HIGH` | (Root Only) Takes a silent screenshot of the active screen and emails it. |
| `GETLOGS`| *None* | `LOW` | (Root Only) Dumps and emails captured keylog buffers, then flushes storage. |
| `EXFIL` | `[pkg] [file]` | `HIGH` | (Root Only) Copies a file from `/data/data/[pkg]/` and attaches it via email. |

---

## 🛠️ Technology Stack

- **Languages:** 100% Modern Kotlin (Coroutines, StateFlow, Mutex) & Modern C++17 (Native NDK)
- **Target OS:** Android 14 (API 34) | **Minimum OS:** Android 9 (API 28) | **Compatibility:** Android 9 to 17 / GrapheneOS
- **NDK Toolchain:** Clang with `-march=armv8.5-a+memtag -fsanitize=memtag -fstack-protector-strong -D_FORTIFY_SOURCE=2 -O3` (NDK `30.0.16248370`)
- **Hardware Security Modules:** Google Titan M / Titan M2, Qualcomm SPU via StrongBox KeyMint API (`FEATURE_STRONGBOX_KEYSTORE`), Hardware Monotonic Counters / RPMB
- **Post-Quantum Cryptography:** NIST FIPS 203 ML-KEM-768 (CRYSTALS-Kyber) + Curve25519 (X25519) via Bouncy Castle PQC, HKDF-SHA512
- **Key Decoupling Architecture:** RFC 9497 Elliptic Curve Oblivious Pseudorandom Function (EC-OPRF) over `secp256r1` with blind client scalars and modular inversion
- **Native Memory Hardening:** ARMv8.5-A Memory Tagging Extension (MTE Synchronous Mode), `prctl(PR_SET_DUMPABLE, 0)`, `mlock()` page pinning, volatile pointer zeroing barriers
- **Storage Sanitization:** JEDEC JESD220 (UFS) and JESD84-B51 (eMMC) direct kernel IOCTLs (`BLKSECDISCARD` / `BLKDISCARD`), 64KB FBE metadata block zeroing
- **Peripheral Bus Control:** USB HAL v1.3+, Qualcomm DWC3 driver register unbind, Linux UDC controller manipulation, SysRq hardware panic triggers
- **Proximity Key Sharding:** Shamir 2-of-2 Information-Theoretic Secret Sharing, Bluetooth Low Energy (BLE) GATT with continuous RSSI threshold monitoring
- **Covert Signaling Protocol:** RFC 9458 Oblivious HTTP (OHTTP), RFC 9180 Hybrid Public Key Encryption (HPKE), Brotli/zlib payload emulation
- **Framework Hooks:** Xposed API v82 / LSPosed Framework (Zygisk Release or JingMatrix fork)
- **Root Environments:** Magisk, KernelSU, KernelSU-Next, APatch
- **Device Owner (Non-Root):** Android Enterprise `DevicePolicyManager` with AVB 2.0 (Verified Boot) and `dpm.wipeDevice()` (API 34+)
- **System Privileges:** Android Privileged Permission Allowlist (`MASTER_CLEAR`, `WRITE_SECURE_SETTINGS`, `REBOOT`, `MANAGE_USERS`, `MANAGE_USB`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video) with headless `FakeLifecycleOwner` and asynchronous mutual exclusion
- **Background Architecture:** AndroidX WorkManager, Direct Boot `AlarmManager` (`RTC_WAKEUP` / `ELAPSED_REALTIME_WAKEUP`), Native Foreground Services with BAL brokers
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
│   │   │   ├── cpp/                              <-- Native NDK Memory Hardening, IOCTL & MTE
│   │   │   │   ├── CMakeLists.txt                <-- Clang -march=armv8.5-a+memtag & zlib config
│   │   │   │   └── native-security.cpp           <-- MTE, BLKSECDISCARD, mlock, ChaCha20-Poly1305
│   │   │   ├── java/com/hamoon/uncleted/
│   │   │   │   ├── canary/                       <-- Covert Zero-Knowledge Signaling
│   │   │   │   │   └── CovertCanarySender.kt     <-- RFC 9458 OHTTP & RFC 9180 HPKE dispatcher
│   │   │   │   ├── core/                         <-- Dual-Profile Routing Architecture
│   │   │   │   │   ├── DefenseCoordinator.kt     <-- Runtime capability resolver
│   │   │   │   │   ├── DefenseStrategy.kt        <-- Abstract defense strategy interface
│   │   │   │   │   └── strategies/
│   │   │   │   │       ├── DeviceOwnerStrategy.kt <-- Route A (Titan M2 / Locked AVB / wipeDevice)
│   │   │   │   │       └── RootPrivilegedStrategy.kt <-- Route B (Root / LSPosed)
│   │   │   │   ├── crypto/                       <-- Hardware Keystore, PQC, OPRF & Ephemeral Engine
│   │   │   │   │   ├── AntiRollbackManager.kt    <-- Atomic dual-anchor monotonic counter
│   │   │   │   │   ├── CryptoPreferences.kt      <-- Hardware-encrypted DE storage preferences
│   │   │   │   │   ├── EphemeralKeyDecayEngine.kt<-- Fail-Closed entropy-driven key rolling & Vold lock
│   │   │   │   │   ├── FastCryptoShredEngine.kt  <-- True FBE 4KB key block & metadata shredder
│   │   │   │   │   ├── OneTimeTokenManager.kt    <-- Emergency recovery slips (OTC)
│   │   │   │   │   ├── OprfClientEngine.kt       <-- RFC 9497 EC-OPRF client (secp256r1 math)
│   │   │   │   │   ├── OprfPreferences.kt        <-- OPRF configuration & key share storage
│   │   │   │   │   ├── PostQuantumEngine.kt      <-- NIST FIPS 203 ML-KEM-768 + X25519 hybrid engine
│   │   │   │   │   ├── SecureWireValidator.kt    <-- Ed25519 85-byte binary packet verifier
│   │   │   │   │   └── StrongBoxSecurityManager.kt <-- Discrete Titan M2 master suicide key
│   │   │   │   ├── data/
│   │   │   │   │   └── SecurityPreferences.kt    <-- Dual DE/CE persistent storage manager (incl. Decoy Apps)
│   │   │   │   ├── fragments/                    <-- Domain-Driven UI Views
│   │   │   │   │   ├── AboutFragment.kt          <-- About, quotes, and attribution
│   │   │   │   │   ├── AntiForensicsFragment.kt  <-- Ephemeral keys, OPRF, USB trapdoor & AVB UI
│   │   │   │   │   ├── AuthenticationFragment.kt <-- Credential Matrix, Decoy Apps & Decoy migration
│   │   │   │   │   ├── CryptoEngineFragment.kt   <-- PQC keys, StrongBox & Deniability vault
│   │   │   │   │   ├── DashboardFragment.kt      <-- Threat score & posture meter
│   │   │   │   │   ├── DestructionProtocolsFragment.kt <-- Multi-tier destruction pipelines
│   │   │   │   │   ├── DiagnosticsFragment.kt    <-- Live system bug reporting, log recording & export
│   │   │   │   │   ├── EventLogFragment.kt       <-- Security event audit log
│   │   │   │   │   ├── HardwareSentinelsFragment.kt <-- PMIC, Spectral, Baseband & USB sentinels
│   │   │   │   │   ├── PermissionsFragment.kt    <-- System privilege boundaries
│   │   │   │   │   ├── ProximityTripwireFragment.kt <-- BLE/UWB Sharding & Dead-man tripwires
│   │   │   │   │   ├── RemoteSignalingFragment.kt<-- OHTTP Canary, Ed25519 & SMS fallback
│   │   │   │   │   ├── SettingsFragment.kt       <-- App preferences & calibration
│   │   │   │   │   └── SurveillanceFragment.kt   <-- CameraX, audio, durations & keylog evidence
│   │   │   │   ├── honeypot/                     <-- Decoy Launcher & Trap Activities
│   │   │   │   │   ├── DecoyAppActivity.kt       <-- Fake Launcher App Dispatcher & Trap Trigger
│   │   │   │   │   ├── DecoyAppManager.kt        <-- Dynamic Activity-Alias Visibility Manager
│   │   │   │   │   ├── FakeBankingActivity.kt    <-- Credential bait trap
│   │   │   │   │   ├── FakeGalleryActivity.kt    <-- Photo bait trap
│   │   │   │   │   ├── FakeNotesActivity.kt      <-- Note bait trap
│   │   │   │   │   ├── HoneypotAppAdapter.kt     <-- Decoy launcher app grid adapter
│   │   │   │   │   └── HoneypotLauncherActivity.kt <-- Decoy launcher screen
│   │   │   │   ├── hooks/
│   │   │   │   │   └── LockscreenHook.kt         <-- Core system_server LSPosed hook (Salted Hash v2)
│   │   │   │   ├── proximity/                    <-- BLE/UWB Proximity Key Sharding
│   │   │   │   │   ├── BleProximitySentinel.kt   <-- Bluetooth GATT heartbeat & RSSI sentinel
│   │   │   │   │   └── ProximityShardingEngine.kt<-- Shamir 2-of-2 secret sharing implementation
│   │   │   │   ├── receivers/                    <-- Boot, SMS, Admin, & Duress Receivers
│   │   │   │   │   ├── AdminReceiver.kt          <-- Gatekeeper & DevicePolicyManager receiver
│   │   │   │   │   ├── BootCompletedReceiver.kt  <-- Early BFU Direct Boot scheduler & Decoy sync
│   │   │   │   │   ├── DuressHookReceiver.kt     <-- Duress & Honeypot broadcast receiver
│   │   │   │   │   ├── FaradayReceiver.kt        <-- Autonomous 180-minute blackout wakeup receiver
│   │   │   │   │   ├── NetworkStateReceiver.kt   <-- Connectivity change tripwire reset
│   │   │   │   │   ├── ScreenStateReceiver.kt    <-- Screen-off USB severing & memory compaction
│   │   │   │   │   ├── SecretCodeReceiver.kt     <-- Dialer launch receiver (*#*#CODE#*#*)
│   │   │   │   │   ├── SimChangeReceiver.kt      <-- Hardware SIM swap sentinel
│   │   │   │   │   ├── SmsCommandReceiver.kt     <-- Multi-Modal SMS Dispatcher & Purger
│   │   │   │   │   ├── TripwireReceiver.kt       <-- Direct Boot AlarmManager tripwire receiver
│   │   │   │   │   └── WidgetActionReceiver.kt   <-- Lockscreen widget control receiver
│   │   │   │   ├── sentinels/                    <-- Advanced Real-Time Hardware Monitors
│   │   │   │   │   ├── AdvancedBasebandSentinel.kt <-- Modem 2G masking & Timing Advance anomaly trap
│   │   │   │   │   ├── FaradayBlackoutSentinel.kt  <-- Multi-carrier RF loss tracking
│   │   │   │   │   ├── PmicTamperSentinel.kt     <-- BMS impedance ($R_{int}$) & thermal shock monitor
│   │   │   │   │   ├── SpectralSentinel.kt       <-- Multi-link hysteresis Faraday bag trap
│   │   │   │   │   └── UsbTrapdoorController.kt  <-- Zero-latency PHY severing & SysRq panic trap
│   │   │   │   ├── services/
│   │   │   │   │   ├── FakeAirplaneConfirmActivity.kt <-- Decoy Airplane Mode confirmation barrier
│   │   │   │   │   ├── FakeAirplaneTileService.kt <-- Decoy Airplane Mode Quick Settings tile
│   │   │   │   │   ├── MonitoringService.kt      <-- Core sensor sentinel coordinator service
│   │   │   │   │   ├── NotificationCommandListener.kt <-- Push notification listener C2 for data-only eSIM
│   │   │   │   │   ├── PanicActionService.kt     <-- Emergency dispatch & destruction orchestrator
│   │   │   │   │   ├── PowerButtonService.kt     <-- Input filtering & hardware volume monitor
│   │   │   │   │   ├── UsbTripwireService.kt     <-- Kernel UDC SysFS data line tripwire
│   │   │   │   │   └── ZoneWipeService.kt        <-- Multi-zone perimeter geofence suicide service
│   │   │   │   ├── util/
│   │   │   │   │   ├── AdvancedCameraHandler.kt  <-- Dual camera photo/video capture pipeline & cooldown
│   │   │   │   │   ├── AdvancedCrypto.kt         <-- StrongBox & Proximity-bound encryption
│   │   │   │   │   ├── AudioRecorder.kt          <-- Ambient AAC (.m4a) audio recorder
│   │   │   │   │   ├── BootloaderHardeningHelper.kt <-- Pre-OS init stage-2 scripts & AVB guide
│   │   │   │   │   ├── CameraHandler.kt          <-- CameraX mutex serialization & state monitoring
│   │   │   │   │   ├── CredentialBridge.kt       <-- Salted hash BFU platform bridge (Format v2)
│   │   │   │   │   ├── DecoyUserManager.kt       <-- Android Multi-User provisioning & non-destructive hiding
│   │   │   │   │   ├── DeviceAdminHelper.kt      <-- Asynchronous strategy wipe invoker
│   │   │   │   │   ├── DiagnosticLogCollector.kt <-- Live log recording and bug packaging
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- 16KB metadata & root block discard engine
│   │   │   │   │   ├── Keylogger.kt              <-- Hardware & soft-keyboard logger
│   │   │   │   │   ├── MemoryHardeningEngine.kt  <-- Kernel drop_caches & ZRAM swap re-keying
│   │   │   │   │   ├── MotionDetector.kt         <-- Low-power micro-motion accelerometer filter
│   │   │   │   │   ├── NativeSecurityBridge.kt   <-- JNI link to libuncleted_native.so
│   │   │   │   │   ├── NotificationHelper.kt     <-- Material 3 status center notification manager
│   │   │   │   │   ├── PolygonUtils.kt           <-- Ray-Casting algorithm & zone serializer
│   │   │   │   │   ├── RadioIsolationManager.kt  <-- Kernel iptables DROP & radio killswitch
│   │   │   │   │   ├── RootActions.kt            <-- Universal Magisk/KernelSU/APatch commands
│   │   │   │   │   ├── RootChecker.kt            <-- Universal root provider detector
│   │   │   │   │   ├── TripwireManager.kt        <-- Hardware RTC AlarmManager tripwire manager
│   │   │   │   │   └── UsbDetector.kt            <-- Linux UDC gadget & SDP/CDP analyzer
│   │   │   │   ├── vault/
│   │   │   │   │   └── PlausibleDeniabilityVault.kt <-- Deterministic HKDF DNG polyglot vault
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog, Tripwire)
│   │   │   ├── res/
│   │   │   │   ├── drawable/                     <-- Vector drawables & adaptive icons
│   │   │   │   ├── layout/                       <-- Material 3 layouts (incl. evidence locker)
│   │   │   │   ├── menu/                         <-- Navigation drawer layout
│   │   │   │   ├── values/                       <-- Strings, themes, arrays, and styling
│   │   │   │   ├── values-fa/                    <-- Complete Persian (فارسی) localization
│   │   │   │   ├── AndroidManifest.xml           <-- System privileges & component definitions
│   │   │   │   ├── CameraPermissionBrokerActivity.kt <-- Android 14 BAL broker with completion listener
│   │   │   │   ├── EvidenceGalleryActivity.kt    <-- Evidence Locker, media viewer & shredder activity
│   │   │   │   ├── LockScreenActivity.kt         <-- Hardened in-app lockscreen
│   │   │   │   ├── MainActivity.kt               <-- Main UI dashboard & navigation coordinator
│   │   │   │   └── UncleTedApplication.kt        <-- Dynamic Direct-Boot runtime initializer
│   │   │   └── build.gradle.kts                  <-- NDK CMake MTE flags, PQC packaging, Target SDK 34
│   │   └── proguard-rules.pro                    <-- Native bridge & StrongBox rule preservation
├── package_module.py                             <-- Universal Root Module Packager with system.prop
└── settings.gradle.kts
```

---

## 🚀 Deployment & Installation Guide

### Prerequisites
- **For Route A (Device Owner Mode):** An Android device running Android 9 through 17 (Stock AOSP or GrapheneOS) with a locked bootloader, freshly factory reset (containing zero Google or user accounts).
- **For Route B (Privileged Root & Hook Mode):** A rooted Android device running Android 9 through 17 (rooted via **Magisk**, **KernelSU**, **KernelSU-Next**, or **APatch**) with **LSPosed** installed and operational.

---

### Phase 1: Build Release Artifacts
Clone the repository and build the production APK and universal flashable root module:
```bash
git clone https://github.com/HamoonSoleimani/UncleTed.git
cd UncleTed

# Build production release APK (v10.0.1)
./gradlew assembleRelease

# Package universal flashable root ZIP (v10.0.1)
python3 package_module.py
```
Output artifacts are generated in `app/build_output/`:
- **`UncleTed-v10.0.1.apk`** (For Route A: Device Owner provisioning or standalone use).
- **`UncleTed-PrivApp-v10.0.1.zip`** (For Route B: Magisk / KernelSU / APatch module).

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
   adb install -r -d -g app/build_output/UncleTed-v10.0.1.apk
   adb shell dpm set-device-owner com.hamoon.uncleted/.receivers.AdminReceiver
   ```
6. Revoke USB Debugging and disable Developer Options in Settings:
   ```bash
   adb shell settings put global adb_enabled 0
   adb shell settings put global development_settings_enabled 0
   ```
7. Disconnect the USB cable. Uncle Ted now exercises exclusive hardware policy authority over the device with AVB 2.0 fully enforcing.

#### Route B: Flashing the Systemless Module (.zip) for Root & LSPosed
*Recommended for native lockscreen PIN interception, instant Decoy Space switching, kernel-level USB PHY manipulation, and early-boot script integration.*

1. Transfer `UncleTed-PrivApp-v10.0.1.zip` to your device:
   ```bash
   adb push app/build_output/UncleTed-PrivApp-v10.0.1.zip /sdcard/
   ```
2. Open **Magisk**, **KernelSU**, or **APatch Manager**.
3. Navigate to the **Modules** tab.
4. Tap **Install from storage**, select `UncleTed-PrivApp-v10.0.1.zip`, and allow the installer script to execute.
5. **Reboot your device**.

---

### Phase 3: Activating the LSPosed Hook (Route B Only)
1. Once the phone reboots, open the **LSPosed Manager** app.
2. Navigate to the **Modules** tab.
3. Tap **UncleTed System Priv-App & Hook**.
4. Toggle **Enable Module** to **ON**.
5. Ensure the hook scope includes:
   - `System Framework` (`android`)
   - `System UI` (`com.android.systemui`)
6. **Hard reboot your device once more** so `system_server` loads the native Keyguard hook during early initialization:
   ```bash
   adb reboot
   ```

---

### Phase 4: Configuring & Arming Credentials & Sub-OS Engines
1. Open **Uncle Ted** on your device.
2. Complete the permission authorizations in the **System Platform Access** tab.
3. Open the **Authentication & Decoys** tab:
   - Set a **Normal Unlock PIN** (e.g., `1111`).
   - Set a **Duress (Panic) PIN** (e.g., `2222`).
   - Set a **Wipe Lock PIN** (e.g., `9999`).
   - Set a **Honeypot PIN** (e.g., `8888`).
   - Tap **Provision Decoy User** to establish the isolated `Personal` secondary profile (`UserHandle(10)` or `11`).
   - Configure **Decoy App Launcher Tripwires** (select action: *Immediate Silicon Wipe*, *Standard Platform Wipe*, *Silent Duress*, *Lock to BFU*, or *Decoy Space*, and enable WhatsApp, Signal, Telegram, Threema, or Session).
   - Configure **Decoy Airplane Mode Tile Settings** (toggle optional PIN challenge on or off, and select trigger action: *Lock*, *Standard Platform Wipe*, *Immediate Silicon Wipe*, or *Silent Duress*).
   - Tap **Save & Arm Credentials** (writes salted hashes in Format v2 to `/data/system/uncleted/credentials.cfg` on rooted systems, or stores securely in DE space).
4. Open the **Anti-Forensics & Pre-OS** tab:
   - Tap **Arm Ephemeral Keys** to initialize the fail-closed rolling entropy buffer.
   - Configure **OPRF Evaluation Server URL** and toggle **Enforce OPRF Remote Key Splitting**.
   - Enable **USB PHY Trapdoor Severing** (ports sever differential signaling lines immediately upon screen-off).
   - If rooted, tap **Install Init Stage-2 USB Kill Script** to write `00_uncleted_early_usb_kill.sh` into `/data/adb/post-mount.d/`.
   - Tap **Export AVB 2.0 & Recovery Hardening Artifacts** to generate authenticated bootloader locking instructions.
5. Open the **Covert Signaling & C2** tab:
   - Paste your **Operator Ed25519 Public Key** (Base64) to enable cryptographic remote signaling.
   - Configure your **Trusted PQC Hybrid Public Key (ML-KEM-768 + X25519)** under the **Cryptography & Deniability** tab.
   - Tap **Generate Emergency Wallet Sheet (5 OTC)** and record the single-use recovery tokens.
   - Configure your **Emergency Contact** (Phone Number for SMS whitelisting or Email for SMTP dispatches).
6. Open the **Surveillance & Evidence** tab:
   - Set preferred **Video Recording Duration** (5s to 60s) and **Ambient Audio Duration** (15s to 300s).
   - Select optical lenses (Front and/or Rear cameras).
   - Inspect recorded evidentiary assets via the **Evidence Locker & Media Vault**.

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

#### 3. Confirm Zero-Latency USB PHY Severing on Screen Lock
Lock the phone and monitor the kernel USB log stream:
```bash
adb logcat -s "UsbTrapdoorController" "ScreenStateReceiver"
```
*Expected output upon pressing power button to turn off screen:*
```text
ScreenStateReceiver: Screen-off event detected. Triggering zero-latency USB severing and memory purge...
UsbTrapdoorController: Zero-Latency USB PHY data line severing engaged.
UsbTrapdoorController: DPM setUsbDataSignalingEnabled(false) executed.
UsbTrapdoorController: Kernel DWC3/UDC physical data registers zeroed.
```

#### 4. Verify Fail-Closed OPRF Evaluation in Faraday Environment
Disconnect network or activate an external RF shield, then test the blinded OPRF derivation in the app:
```bash
adb logcat -s "OprfClientEngine"
```
*Expected output (network unavailable):*
```text
OprfClientEngine: OPRF evaluation failed. Server returned HTTP error or timed out. Failing closed.
OprfClientEngine: OPRF handshake failed or was rejected. Refusing key delivery (Fail-Closed).
```
*Confirm that no local master secret is derived or decrypted.*

#### 5. Verify Early-Boot Post-Mount Init Deployment (Route B)
Confirm that the Stage-2 USB kill script is present with root execution authority:
```bash
adb shell su -c "ls -la /data/adb/post-mount.d/00_uncleted_early_usb_kill.sh"
```
*Expected output:*
```text
-rwxr-xr-x 1 root root ... /data/adb/post-mount.d/00_uncleted_early_usb_kill.sh
```

#### 6. Confirm Discrete Titan M2 / StrongBox Master Key & Rollback Protection
Inspect logcat during app initialization:
```bash
adb logcat -s "StrongBoxSecManager" "AntiRollbackManager" "UncleTedApplication"
```
*Expected output:*
```text
StrongBoxSecManager: Initializing Master Suicide Key (StrongBox Supported: true)...
StrongBoxSecManager: Enforced setRollbackResistant(true) on hardware master key via reflection.
StrongBoxSecManager: Hardware master key successfully provisioned inside discrete HSM.
AntiRollbackManager: Anti-rollback evaluation: Persisted=1001, LocalFile=1001
UncleTedApplication: Native runtime memory defenses armed (Success: true).
```

#### 7. Confirm Decoy Launcher App Tripwires (Wasted Integration)
Verify that the configured decoy messenger aliases are active on the launcher:
```bash
adb shell pm list activities | grep com.hamoon.uncleted.honeypot
```
*Expected output:* Shows enabled decoy aliases (e.g., `com.hamoon.uncleted.honeypot.WhatsAppActivity`, `com.hamoon.uncleted.honeypot.SignalActivity`).

#### 8. Verify Salted Platform Bridge Synchronization (Route B - Format v2)
Confirm that the bridge file is present in Device-Protected space and populated with salted hashes without plaintext credentials:
```bash
adb shell su -c "cat /data/system/uncleted/credentials.cfg"
```
*Expected output (Route B):*
```text
format_version=2
wipe_pin_hash=[SALTED_BASE64_HASH]
wipe_pin_salt=[SALT_BASE64]
duress_pin_hash=[SALTED_BASE64_HASH]
duress_pin_salt=[SALT_BASE64]
honeypot_pin_hash=[SALTED_BASE64_HASH]
honeypot_pin_salt=[SALT_BASE64]
decoy_user_id=11
updated_at=[TIMESTAMP]
```

#### 9. Monitor Honeypot Decoy Space Migration (Route B)
Lock the device screen with the hardware power button. Open an active logcat monitor:
```bash
adb logcat -s "UncleTed-LockHook" "DecoyUserManager"
```
Enter your **Honeypot PIN** (`8888`) on the native Keyguard keypad:
```text
UncleTed-LockHook: Credential verification intercepted: [length=4]
UncleTed-LockHook: HONEYPOT PIN matched at OS level! Transitioning to Decoy User 11...
UncleTed-LockHook: Invoking in-process switchUser(11) via IActivityManager...
UncleTed-LockHook: IActivityManager.switchUser(11) returned: true
DecoyUserManager: User 0 session evicted. Decoy Space active.
```

#### 10. Verify Real-Time Sentinels (Spectral, Baseband, PMIC)
Monitor the hardware sentinel poller loop in logcat:
```bash
adb logcat -s "MonitoringService" "SpectralSentinel" "AdvancedBasebandSentinel" "PmicTamperSentinel"
```
*Expected output:*
```text
MonitoringService: MonitoringService: Sensors, Spectral, PMIC, Baseband, and Proximity Sentinels active.
AdvancedBasebandSentinel: Baseband modem allowed network types bitmask updated (2G stripped).
PmicTamperSentinel: Hardware PMIC baseline locked: R_int=42000uOhm, Temp=245
```

#### 11. Test Cryptographic SMS Signal Verification
Send an Ed25519-signed binary packet to the device and monitor the receiver:
```bash
adb logcat -s "SmsCommandReceiver" "TripwireReceiver" "DestructionEngine" "FastCryptoShred"
```
*Expected output:*
```text
SmsCommandReceiver: ED25519 SIGNATURE VERIFIED: OpCode=1, Seq=42
DestructionEngine: !!! INITIATING EMERGENCY DESTRUCTION: OP_ED25519_WIPE !!!
StrongBoxSecManager: !!! INITIATING TITAN M2 / STRONGBOX CRYPTOGRAPHIC SUICIDE !!!
FastCryptoShred: !!! INITIATING TRUE FBE CRYPTO-SHREDDING PIPELINE !!!
FastCryptoShred: 64KB metadata key block zeroed on: /dev/block/by-name/metadata
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).
