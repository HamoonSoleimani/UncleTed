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
  <img src="https://img.shields.io/badge/Version-v7.0.1-brightgreen.svg" alt="Version">
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg" alt="Target SDK">
  <img src="https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Hardware-Titan%20M2%20%2F%20StrongBox-blueviolet.svg" alt="Titan M2 StrongBox">
  <img src="https://img.shields.io/badge/PQC-NIST%20FIPS%20203%20(ML--KEM--768)-darkgreen.svg" alt="NIST ML-KEM-768">
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
  - [1. Hardware Security Module & Anti-Rollback Suicide Key (Titan M2 / StrongBox)](#1-hardware-security-module--anti-rollback-suicide-key-titan-m2--strongbox)
  - [2. NIST FIPS 203 Post-Quantum Cryptographic Hybrid Engine (ML-KEM-768 + X25519)](#2-nist-fips-203-post-quantum-cryptographic-hybrid-engine-ml-kem-768--x25519)
  - [3. JEDEC Silicon-Level Hardware Storage Sanitization (BLKSECDISCARD IOCTL)](#3-jedec-silicon-level-hardware-storage-sanitization-blksecdiscard-ioctl)
  - [4. Exploit Mitigation, Page Pinning & Memory Hardening (ARMv8.5-A MTE & mlock)](#4-exploit-mitigation-page-pinning--memory-hardening-armv85-a-mte--mlock)
  - [5. Sub-Second Spectral Blackout & 180-Minute Faraday Sentinels](#5-sub-second-spectral-blackout--180-minute-faraday-sentinels)
  - [6. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire](#6-pmic-battery-micro-telemetry--anti-disassembly-tripwire)
  - [7. Advanced Baseband & IMSI-Catcher / Stingray Sentinel (Modem 2G Masking & Timing Advance)](#7-advanced-baseband--imsi-catcher--stingray-sentinel-modem-2g-masking--timing-advance)
  - [8. Volatile Memory Scrubbing, ZRAM Re-Keying & Plausible Deniability Vault](#8-volatile-memory-scrubbing-zram-re-keying--plausible-deniability-vault)
  - [9. BLE/UWB Proximity Key Sharding Engine (Shamir 2-of-2 Hardware Separation)](#9-bleuwb-proximity-key-sharding-engine-shamir-2-of-2-hardware-separation)
  - [10. Zero-Knowledge Covert Canary Signaling via Oblivious HTTP (OHTTP / RFC 9458)](#10-zero-knowledge-covert-canary-signaling-via-oblivious-http-ohttp--rfc-9458)
  - [11. Lockscreen Authentication & Anti-Coercion Engine](#11-lockscreen-authentication--anti-coercion-engine)
  - [12. Storage Destruction Pipeline & Cryptographic Header Erasure](#12-storage-destruction-pipeline--cryptographic-header-erasure)
  - [13. Physical Bus & Peripheral Defense (USB HAL v1.3+ & Radio Isolation)](#13-physical-bus--peripheral-defense-usb-hal-v13--radio-isolation)
  - [14. Multi-User RAM Anti-Forensics & Cold Vold Eviction](#14-multi-user-ram-anti-forensics--cold-vold-eviction)
  - [15. Autonomous Environmental & Dead-Man Tripwires](#15-autonomous-environmental--dead-man-tripwires)
  - [16. Carrier-Blind Remote Command & Control (Ed25519 Wire, OTC, SMS)](#16-carrier-blind-remote-command--control-ed25519-wire-otc-sms)
  - [17. Covert Surveillance & Multi-Modal Evidence Gathering](#17-covert-surveillance--multi-modal-evidence-gathering)
- [📡 Remote Signaling & SMS Command Reference](#-remote-signaling--sms-command-reference)
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
> Features such as discrete hardware Secure Element suicide key erasure, JEDEC silicon-level block discarding, low-level FBE metadata zeroing, partition invalidation, and Vold key eviction carry permanent, irreversible consequences. Erasing cryptographic key material renders underlying data mathematically and physically unrecoverable.
> **Never install or deploy this software on any hardware without explicit, informed authorization from the device owner.** The developer and contributors assume no liability for data loss, hardware damage, or legal consequences resulting from the deployment or operation of this codebase.

---

## 📊 Architectural Comparison: Deployment Profiles

On modern Android (Android 9 through 14+), the platform enforces strict security boundaries between unprivileged apps, system services, and hardware execution environments. Uncle Ted operates across two privileged architectural routes: **Route A (Enterprise Device Owner with Locked Bootloader & Enforcing AVB 2.0)** and **Route B (Systemless Priv-App with native LSPosed hooks in `system_server`)**.

| Security Vector / Capability | Standalone APK (Stock OS / Sideloaded) | Route A: Device Owner via ADB (Locked Bootloader & AVB) | Route B: Privileged Root + LSPosed (Unlocked Bootloader) | Technical Root Cause / Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Bootloader & AVB State** | 🟢 Locked (AVB Enforcing) | 🟢 **100% Locked (AVB Enforcing)** | 🔴 Unlocked (dm-verity unverified) | Route A retains the hardware root of trust; Route B requires an unlocked bootloader for Zygisk, Magisk/KernelSU, and custom kernels. |
| **Hardware KeyMint Isolation** | 🟡 Emulated / TEE | 🟢 **Titan M2 / StrongBox HSM** | 🟡 TEE Degraded / Compromised | Route A uses `setIsStrongBoxBacked(true)` to isolate keys within discrete silicon (Titan M2); Route B hardware attestation fails due to unlocked state. |
| **Anti-NAND Mirroring Defense** | 🔴 Non-Existent | 🟢 **Hardware Monotonic Counter Bound** | 🟡 Software Monotonic Anchor | Route A binds keys to Titan M2 / RPMB rollback resistance; Route B detects counter desync and forces immediate suicide. |
| **Post-Quantum Cryptography** | 🔴 Classical Only | 🟢 **ML-KEM-768 + X25519 (FIPS 203)** | 🟢 **ML-KEM-768 + X25519 (FIPS 203)** | Hybrid Post-Quantum KEM protects data and covert canaries against "Harvest Now, Decrypt Later" quantum cryptanalysis. |
| **Silicon-Level Storage Purge** | 🔴 Inoperable | 🟡 Hardware SE Wipe via DPM | 🟢 **JEDEC BLKSECDISCARD IOCTL** | Route B issues hardware `BLKSECDISCARD` / `BLKDISCARD` ioctls directly to UFS/eMMC FTL controllers; Route A revokes FBE root keys. |
| **ARMv8.5-A MTE Hardening** | 🔴 Non-Enforced | 🟢 **Synchronous Mode (`sync`)** | 🟢 **Synchronous Mode (`sync`)** | Native layer sets `PR_MTE_TCF_SYNC` via `prctl()`, aborting spatial/temporal memory corruptions immediately via `SIGSEGV`. |
| **Volatile Memory Sanitization** | 🔴 None (OS Swaps Cleanly) | 🟡 Process `mlock()` & Barriers | 🟢 **Kernel `drop_caches` & ZRAM Re-Key** | Route B executes kernel-level cache dropping, page compaction, and ZRAM swap reset on `ACTION_SCREEN_OFF`. |
| **Spectral Faraday Seizure Trap** | 🔴 Slow Timeout | 🟢 **Sub-4s Multi-Carrier Collapse** | 🟢 **Sub-4s Multi-Carrier Collapse** | Monitors real-time Cellular RSRP, Wi-Fi BSSID scan extinction, and micro-motion; triggers instant AFU $\rightarrow$ BFU eviction. |
| **PMIC Battery Disassembly Guard** | 🔴 Unsupported | 🟡 Thermal Gradient Tracking | 🟢 **BMS $R_{int}$ & Thermal Micro-Telemetry** | Detects external DC bench supply micro-clamp attachment ($\Delta R > 35\text{ m}\Omega$) and rear-chassis unsealing. |
| **Baseband / Stingray Defense** | 🔴 Vulnerable to 2G Force | 🟢 **Modem-Level 2G Masking (API 34)** | 🟢 **RIL Power Cut & Timing Advance Trap** | Strips 2G from modem firmware bitmasks; detects impossible RF topologies (high RSRP with high Timing Advance) and cuts RIL power. |
| **Proximity Key Sharding** | 🔴 Single-Device Keys | 🟢 **BLE/UWB Shamir 2-of-2 Sharding** | 🟢 **BLE/UWB Shamir 2-of-2 Sharding** | Master secrets split across StrongBox (Shard A) and an external BLE wearable (Shard B); key evaporates if separated $> 2\text{ m}$. |
| **Covert Canary Signaling** | 🔴 Cleartext HTTP / Webhooks | 🟢 **RFC 9458 Oblivious HTTP (OHTTP)** | 🟢 **RFC 9458 Oblivious HTTP (OHTTP)** | Dispatches HPKE-encrypted distress blobs disguised as standard Android telemetry to CDN relays, hiding client IP and content. |
| **Lockscreen Interception** | 🔴 Non-Functional | 🟡 **Fail Callback / In-App Guard** | 🟢 **100% Native Hook** | Route B intercepts `LockSettingsService` directly inside `system_server`. Route A relies on Gatekeeper failure callbacks and hardware wipe limits. |
| **Physical USB Extraction Defense** | 🔴 Impossible | 🟢 **Physical USB HAL Port Severing** | 🟢 **Kernel UDC Monitor & Kill** | Route A calls `dpm.setUsbDataSignalingEnabled(false)` via USB HAL v1.3+ to sever D+/D- lines. Route B disables Linux UDC gadget drivers via SysFS/ConfigFS. |
| **Multi-User RAM Anti-Forensics** | 🔴 Impossible | 🟡 Work Profile Segregation | 🟢 **Cold Vold Eviction (`lockuser 0`)** | Route B drops User 0 Credential-Encrypted (CE) keys from the Linux kernel keyring using `vdc cryptfs lockuser 0`, reverting User 0 to BFU before loading Decoy Space. |
| **Overall Defense Posture** | **3.0 / 10** | **9.9 / 10** | **9.8 / 10** | Route A delivers maximum hardware security, AVB chain of trust, and discrete HSM isolation; Route B delivers native OS-level lockscreen control and low-level kernel bus manipulation. |

---

## 🏗️ System Architecture (Dual-Profile Engine)

Uncle Ted v7.0.1 features a decoupled, strategy-based architecture coordinated by `DefenseCoordinator`. The platform dynamically analyzes execution privileges, hardware security module availability, and bootloader status at startup, binding the runtime to the optimal defensive strategy:

```
                                  ┌───────────────────────────────┐
                                  │   OPERATIONAL THREAT SIGNAL   │
                                  │ (Keyguard, Spectral, BLE, RF) │
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
 │ - Titan M2 StrongBox KeyMint & Anti-Rollback    │   │ - JEDEC BLKSECDISCARD / BLKDISCARD IOCTL        │
 │ - NIST FIPS 203 ML-KEM-768 + X25519 PQC Engine  │   │ - Cold Vold Session Eviction (lockuser 0)       │
 │ - Native setUsbDataSignalingEnabled(false) HAL  │   │ - 16KB FBE Metadata Partition Cryptographic Zero│
 │ - Weaver Exponential Brute-Force Rate Limiting  │   │ - Linux Kernel UDC Gadget Controller Severing   │
 │ - Native dpm.reboot() Cold BFU Key Eviction     │   │ - Ephemeral ZRAM Swap Eviction & drop_caches    │
 │ - Spectral RF Collapse (4-Second Faraday Trap)  │   │ - PMIC BMS Resistance & Thermal Shock Tripwire  │
 │ - Modem-Level Hardware 2G Frequency Masking     │   │ - Raw Modem RIL Power Cut via Shell             │
 │ - RFC 9458 Oblivious HTTP (OHTTP) Covert Canary │   │ - RFC 9458 Oblivious HTTP (OHTTP) Covert Canary │
 │ - BLE/UWB Shamir 2-of-2 Proximity Sharding      │   │ - BLE/UWB Shamir 2-of-2 Proximity Sharding      │
 │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │   │ - ARMv8.5-A Synchronous Memory Tagging (MTE)    │
 └─────────────────────────────────────────────────┘   └─────────────────────────────────────────────────┘
```

---

## ✨ Core Capabilities

### 1. Hardware Security Module & Anti-Rollback Suicide Key (Titan M2 / StrongBox)
- **Discrete Silicon KeyMint (`StrongBoxSecurityManager`):** Protects application databases, sensitive preferences, and credential hashes using an AES-256-GCM master key provisioned inside discrete hardware silicon (`setIsStrongBoxBacked(true)`), isolated from the primary application processor.
- **Hardware Monotonic Anti-Rollback Binding (`AntiRollbackManager`):** Binds master cryptographic keys to non-volatile hardware monotonic counters and RPMB (Replay Protected Memory Block) sequence anchors with `setRollbackResistant(true)`. If an adversary desolders the UFS flash chip to perform **NAND Mirroring** (attempting PIN dictionaries and rewinding state), the hardware detects counter desynchronization and executes permanent silicon suicide.
- **Sub-10ms Cryptographic Suicide:** In duress or catastrophic compromise scenarios, Uncle Ted calls `KeyStore.deleteEntry(MASTER_SUICIDE_KEY_ALIAS)`. Deleting this silicon-level register takes under 10 milliseconds and makes all encrypted databases, credentials, and offline caches permanently unrecoverable, rendering physical flash dump analysis mathematically futile.
- **Weaver Hardware Rate-Limiting Weaponization:** In Route A, sets `dpm.setMaximumFailedPasswordsForWipe(admin, 3)`. The Titan M / Weaver chip enforces exponential backoffs and autonomously commands KeyMint to revoke root Key Encryption Keys (KEKs) upon 3 consecutive authentication failures, executing independently of userspace runtime health or battery state.

---

### 2. NIST FIPS 203 Post-Quantum Cryptographic Hybrid Engine (ML-KEM-768 + X25519)
- **Quantum-Resistant KEM Architecture (`PostQuantumEngine`):** Integrates the finalized NIST FIPS 203 post-quantum standard **ML-KEM-768** (CRYSTALS-Kyber) operating in tandem with classical **Curve25519 (X25519)**.
- **Immunity to "Harvest Now, Decrypt Later" (HNDL):** Adversaries capturing distress signals or encrypted storage archives cannot retroactively decrypt evidentiary dossiers or alert metadata using Shor's algorithm on future Cryptanalytically Relevant Quantum Computers (CRQCs).
- **HKDF-SHA512 Combiner:** Derives master 256-bit symmetric operational keys via HMAC-SHA512 combining classical ECDH shared secrets with lattice-based KEM decapsulation secrets over strict domain-separated salt anchors.

---

### 3. JEDEC Silicon-Level Hardware Storage Sanitization (BLKSECDISCARD IOCTL)
- **Hardware Controller Purge (`NativeSecurityBridge`):** Bypasses naive userspace `dd` overwrites (which are rendered ineffective by UFS/eMMC Flash Translation Layer wear-leveling and overprovisioned spare blocks).
- **Direct Kernel IOCTL Dispatch:** Issues `BLKSECDISCARD` (JEDEC JESD220 / JESD84-B51 standard) directly to `/dev/block/by-name/metadata`, `/dev/block/by-name/userdata`, and physical block devices via native C++ IOCTLs.
- **Physical Cell Invalidation:** Forces the hardware controller to raise physical NAND cell voltages to erase levels across the target Logical Block Address (LBA) range and discard internal Media Encryption Keys (MEKs) in under 80 milliseconds.

---

### 4. Exploit Mitigation, Page Pinning & Memory Hardening (ARMv8.5-A MTE & mlock)
- **Synchronous Hardware Memory Tagging:** Enforces `android:memtagMode="sync"` at the application level and injects `-march=armv8.5-a+memtag -fsanitize=memtag` into Clang native compilation. Memory allocations on ARMv8.5-A+ silicon (Tensor G3/G4, Snapdragon 8 Gen 3+) are assigned 4-bit metadata tags; any spatial overflow or use-after-free pointer dereference triggers an instant hardware `SIGSEGV` (`SEGV_MTESERR`), stopping memory corruption exploits.
- **Process Memory Locking (`mlock`):** Pins critical plaintext byte buffers, PIN arrays, and intermediate cryptographic secrets in physical LPDDR5 RAM via `mlock()`, preventing the Android OS from swapping sensitive memory pages into unencrypted storage or dirty swap.
- **Native Process Sandboxing (`NativeSecurityBridge`):** Calls `prctl(PR_SET_DUMPABLE, 0)` via JNI during initialization, blocking `/proc/$PID/mem` extraction, local memory inspection, and unauthorized debugger attachments (`ptrace`/`lldb`).
- **Compiler Dead-Store Protected Zeroing:** Employs volatile C++ pointer zeroing loops with memory barriers (`secureZeroMemory`) to purge sensitive plaintexts, cryptographic keys, and intermediate PIN buffers from heap memory, preventing compiler optimization passes from stripping cleanup operations.

---

### 5. Sub-Second Spectral Blackout & 180-Minute Faraday Sentinels
- **4-Second Spectral Collapse Sentinel (`SpectralSentinel`):** Standard forensic seizure begins with immediate placement of the active phone into an RF-shielded Faraday bag. Uncle Ted continuously monitors RF link parameters. A sudden, simultaneous collapse across Cellular (RSRP < -135 dBm), Wi-Fi (0 visible BSSIDs), and GNSS carrier loss—occurring while the device is in physical motion without user Airplane Mode activation—triggers an instant AFU $\rightarrow$ BFU eviction within 4 seconds.
- **180-Minute Autonomous Faraday Sentinel (`FaradayBlackoutSentinel`):** Operates via `AlarmManager.setExactAndAllowWhileIdle()` backed by hardware `ELAPSED_REALTIME_WAKEUP`. If the phone is isolated inside an evidence locker or Faraday container past the configured threshold, the CPU wakes from deep doze and executes an autonomous BFU key eviction.
- **Native DO BFU Reversion:** In Route A (Device Owner), calls `dpm.reboot()`, immediately purging all Credential-Encrypted (CE) keys from volatile RAM and leaving the device in cold Before First Unlock state where keys exist only as sealed silicon registers inside Titan M2.

---

### 6. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire
- **Hardware Power Management IC Interrogation (`PmicTamperSentinel`):** Forensic laboratories bypass timeout watchdogs by opening the device chassis, cutting the battery lead, and splicing an external DC bench power supply directly across the battery terminals (VBAT) to indefinitely sustain AFU state.
- **Internal Impedance ($R_{int}$) Step-Jump Detection:** Samples Battery Management System (BMS) SysFS nodes (`/sys/class/power_supply/bms/resistance`). Connecting external power supply clamps produces an abrupt electrochemical impedance shift exceeding $\Delta R > 35\text{ m}\Omega$, triggering instant cryptographic suicide.
- **Thermal Gradient Shock ($dT/dt$):** Monitors the battery pack's NTC thermistor resting against the rear enclosure. Heating and prying off the rear glass causes a rapid thermal drop ($\Delta T > 12.0^\circ\text{C}$), detecting enclosure unsealing before physical probes or JTAG taps can stabilize.

---

### 7. Advanced Baseband & IMSI-Catcher / Stingray Sentinel (Modem 2G Masking & Timing Advance)
- **Modem-Level Hardware 2G Stripping (`AdvancedBasebandSentinel`):** Uses Android 12+/14+ `TelephonyManager.setAllowedNetworkTypesForReason()` to permanently strip 2G network bitmasks (`GSM`, `GPRS`, `EDGE`, `CDMA`, `1xRTT`) at the baseband modem firmware layer, preventing cellular interceptors from forcing unauthenticated, unencrypted 2G downgrades.
- **Impossible RF Topology / Timing Advance Trap:** Cell-site simulators (Stingrays) transmit high RF power to override legitimate towers while introducing artificial propagation delays. Uncle Ted monitors serving cell telemetry; detecting high-power signals ($\text{RSRP} > -65\text{ dBm}$) paired with extreme Timing Advance ($\text{TA} > 30$, representing $> 2.3\text{ km}$) identifies a rogue transceiver, triggering an immediate radio cutoff.
- **Hardware RIL Power Cut:** In Route B, issues low-level Telephony IPC service commands (`service call phone 83 i32 0`) to disconnect power from the cellular baseband bus entirely. In Route A, enforces instant global airplane mode isolation.

---

### 8. Volatile Memory Scrubbing, ZRAM Re-Keying & Plausible Deniability Vault
- **Volatile RAM Hardening Engine (`MemoryHardeningEngine`):** Dynamically bound to `Intent.ACTION_SCREEN_OFF`. Commands the Linux kernel to drop pagecaches, dentries, and unpinned inodes (`echo 3 > /proc/sys/vm/drop_caches`) and compact memory (`compact_memory`) to eliminate unallocated plaintext fragments.
- **Ephemeral ZRAM Swap Flushing:** Flushes dirty anonymous pages (`swapoff /dev/block/zram0`), resets the swap device block allocator, and re-initializes ZRAM swap with fresh random cryptographic keys upon screen lock.
- **Compress-Encrypt-Shape (CES) Polyglot Vault (`PlausibleDeniabilityVault`):** Compresses sensitive data with Zstandard/Deflate (stripping plaintext statistical redundancy), encrypts it via native ChaCha20-Poly1305, and shapes its Shannon entropy ($H \approx 7.2\text{--}7.5\text{ bits/byte}$) with deterministic chaff padding into a structurally valid Adobe DNG RAW camera image container stored in the public `Pictures/Camera` directory. To forensic carvers (`bulk_extractor`, Autopsy), the vault is statistically indistinguishable from an ordinary camera file.

---

### 9. BLE/UWB Proximity Key Sharding Engine (Shamir 2-of-2 Hardware Separation)
- **Information-Theoretic Key Separation (`ProximityShardingEngine`):** Splits master operational secrets into two Shamir 2-of-2 additive secret shares ($S = S_A \oplus S_B$). Shard A is sealed inside the Titan M2 discrete StrongBox Keystore. Shard B is transmitted to a paired hardware token (smartwatch, fitness band, or ring) and kept strictly in pinned volatile RAM on the phone.
- **Authenticated BLE GATT Heartbeat (`BleProximitySentinel`):** Dispatches bidirectional authenticated GATT heartbeats every 1,500 ms. If the operator is tackled, ambushed, or separated from their phone by $> 2\text{ meters}$ (or remote RSSI drops below $-85\text{ dBm}$ for 3 consecutive cycles), Shard B is instantly wiped with native memory barriers (`secureZeroMemory`) and Vold drops CE keys to BFU state. The phone in the adversary's hands holds only an incomplete cryptographic fragment.

---

### 10. Zero-Knowledge Covert Canary Signaling via Oblivious HTTP (OHTTP / RFC 9458)
- **Carrier-Blind Network Signaling (`CovertCanarySender`):** Coerced users subjected to active network sniffing or Wi-Fi hardware taps cannot safely dispatch distress alerts to obvious IPs or mail servers. Uncle Ted implements **RFC 9458 Oblivious HTTP (OHTTP)** paired with **RFC 9180 Hybrid Public Key Encryption (HPKE)**.
- **Traffic Masquerading:** Formulates distress packets (GPS coordinates, battery state, trigger reason) matching standard Google Firebase / Google Play Services analytics JSON schemas, compressed with Brotli/gzip.
- **Oblivious Relay Routing:** Transmits the opaque binary payload to an oblivious CDN relay (Cloudflare / Fastly). The relay sees the user's IP but cannot read the encrypted payload; the destination gateway decrypts the payload with its private key but learns zero knowledge of the client's true IP address. Local network observers see only an ordinary Google analytics request.

---

### 11. Lockscreen Authentication & Anti-Coercion Engine
- **Route B (Native LSPosed Hook):** Hooks `com.android.server.locksettings.LockSettingsService` directly inside `system_server`:
  - **Normal PIN:** Validates authentication, clears failed attempt counters, and unlocks the primary profile.
  - **Duress PIN (Silent Canary Trap):** Halts authentication at the framework level, renders an authentic "Wrong PIN" feedback state on Keyguard, and silently triggers covert front/back camera photo, audio, and GPS dispatch with zero screen flickering.
  - **Wipe PIN:** Terminates Keyguard authentication, destroys the discrete StrongBox suicide key, zeros File-Based Encryption metadata headers via JEDEC discard, and stages an autonomous recovery wipe.
  - **Honeypot PIN:** Authenticates the operator into an isolated, authentic secondary Android user space (`UserHandle(10)`) while purging primary user keys from volatile RAM.
  - **BFU (Before First Unlock) Persistence:** PIN definitions synchronize to `/data/system/uncleted/credentials.cfg` (SELinux context `u:object_r:system_data_file:s0`) for pre-unlock interception.
- **Route A (Device Owner / Gatekeeper Integration):**
  - Enforces failed passcode limits directly on the hardware Gatekeeper/Weaver chip.
  - Disables biometric authenticators (fingerprint/face) automatically upon 3 consecutive failures, locking Keyguard down to complex passphrases and preventing forced biometric unlock.

---

### 12. Storage Destruction Pipeline & Cryptographic Header Erasure
Uncle Ted abandons naive raw NAND flash zeroing (`dd /dev/block/sda`), which fails due to wear-leveling and overprovisioning on UFS 3.1/4.0 and NVMe storage controllers. Instead, it utilizes **sub-millisecond cryptographic erasure and JEDEC hardware discard**:
- **16KB FBE Metadata Partition Zapping & BLKSECDISCARD:** Issues JEDEC `BLKSECDISCARD` IOCTLs directly to `/dev/block/by-name/metadata`. Overwriting and discarding the cryptographic metadata partition with immediate sync renders all underlying File-Based Encryption data partitions mathematically unrecoverable in under 1 millisecond.
- **Vold Key Eviction:** Destroys Vold user key directories (`/data/misc/vold/user_keys/`, `/metadata/vold/user_keys/`), synthetic password blobs (`/data/system_de/0/spblob/`), and credential databases.
- **Bootloader Control Block (BCB) Staging:** Writes `--wipe_data\n--reason=UncleTed_Emergency_Sanitize` directly into `/cache/recovery/command`, ensuring that even if userspace execution halts mid-wipe, the device boots into recovery and formats userdata on the subsequent boot cycle.
- **Three Destruction Tiers:**
  1. *Level 1 - Standard Factory Reset:* Platform `MASTER_CLEAR` wipe via `RecoverySystem.rebootWipeUserData()` or BCB command staging.
  2. *Level 2 - Fast Cryptographic Shred:* Destroys StrongBox master keys, evicts Vold keys, executes JEDEC silicon discard on metadata headers, and commands BCB factory reset before rebooting into recovery.
  3. *Level 3 - OS Suicide (Soft Brick):* Executes Level 2 cryptographic shred, then zeroes core boot and ramdisk partitions (`boot`, `vendor_boot`, `init_boot`), rendering the device unbootable without complete firmware reflashing.

---

### 13. Physical Bus & Peripheral Defense (USB HAL v1.3+ & Radio Isolation)
- **Hardware USB Port Severing (Route A):** Calls `dpm.setUsbDataSignalingEnabled(false)` via Android 12+ USB HAL v1.3+. Physically severs the D+/D- and high-speed data signaling lines whenever the screen is locked, preventing forensic workstations (Cellebrite, GrayKey) from establishing host enumeration while permitting charging. Reinforced with `DISALLOW_USB_FILE_TRANSFER` and `DISALLOW_MOUNT_PHYSICAL_MEDIA`.
- **Kernel UDC Bus Sentinel (Route B):** Continuously inspects the Linux USB Device Controller state (`/sys/class/udc/*/state`), modern ConfigFS gadget bindings (`/config/usb_gadget/g1/`), and upstream power supply port types (SDP/CDP vs. DCP/USB-PD). If an active data host connection (`configured` state) negotiates while locked, immediate key eviction executes.
- **Instant Radio Killswitch (`RadioIsolationManager`):** Flushes and sets default `DROP` policies across all kernel `iptables` and `ip6tables` chains (`INPUT`, `OUTPUT`, `FORWARD`) in milliseconds, paired with immediate airplane mode and interface shutdowns (Wi-Fi, Bluetooth, NFC, Cellular) to emulate an active Faraday shield.

---

### 14. Multi-User RAM Anti-Forensics & Cold Vold Eviction
Standard multi-user switching (`am switch-user`) leaves the primary owner's (User 0) Credential-Encrypted (CE) keys resident in the Linux kernel keyring, exposing them to cold-boot RAM acquisition. Uncle Ted implements **true RAM anti-forensics**:
- **Cold Vold Key Eviction:** When the Honeypot PIN is entered, Uncle Ted calls in-process `StorageManagerService.lockUserKey(0)` inside `system_server` or invokes `vdc cryptfs lockuser 0` and `sm lock-user-key 0`.
- **Reversion to BFU State:** Purges User 0's CE keys from volatile RAM and drops filesystem caches (`drop_caches`), placing User 0 back into a secure **Before First Unlock (BFU)** state before the decoy session loads.
- **Authentic Multi-User Decoy:** Moves the active OS session to a genuine secondary Android user profile (`UserHandle(10)`) named `"Personal"` backed by its own `/data/user/10` directory, separate encryption keys, distinct launcher, and decoy apps (`FakeBankingActivity`, `FakeNotesActivity`, `FakeGalleryActivity`).

---

### 15. Autonomous Environmental & Dead-Man Tripwires
- **Autonomous BFU Dead-Man Sentinel (`TripwireManager`):** Operates exclusively within Device-Protected (DE) storage using `AlarmManager.setExactAndAllowWhileIdle()` configured for hardware RTC wakeup. Evaluates elapsed time directly upon `LOCKED_BOOT_COMPLETED`; if the device was seized, powered down, or isolated in a Faraday bag past the threshold, an immediate cryptographic wipe executes in BFU state.
- **Hardware Volume Sequence Wipe:** Intercepts hardware keys via `PowerButtonService`. Entering the rapid sequence `[VOL UP] -> [VOL DOWN] -> [VOL UP] -> [VOL DOWN]` bypasses confirmation dialogs and triggers emergency erasure.
- **Multi-Zone Geographic Suicide (`PolygonUtils` & `ZoneWipeService`):** High-accuracy GPS sentinel operating with a Ray-Casting Point-in-Polygon algorithm using half-open latitude intervals to eliminate boundary errors:
  - *Pre-Configured Boundary:* Built-in perimeter covering Evin Prison.
  - *Custom Wipe Zones:* User-defined circular radius boundaries or polygon perimeters configured via UI or current GPS fix.
  - *Safety Guardrails:* Rejects coordinates with an uncertainty radius $> 30\text{ m}$ and requires 3 consecutive breach samples to eliminate false positives from multipath drift.
- **SIM Hardware Sentinel:** Detects changes in the hardware identity of the SIM card across Android 9 through 14+ without throwing `SecurityException`, instantly locking the device and dispatching alert telemetry.

---

### 16. Carrier-Blind Remote Command & Control (Ed25519 Wire, OTC, SMS)
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

### 17. Covert Surveillance & Multi-Modal Evidence Gathering
- **Sequential Dual-Camera Capture:** Uses `Jetpack CameraX` with a headless `FakeLifecycleOwner` running in `RESUMED` state to capture high-resolution front- and back-camera photos, followed by video clips.
- **Android 14 BAL Compliance:** Employs a full-screen intent broker (`CameraPermissionBrokerActivity`) paired with system shell invocation (`am start`) to bypass Background Activity Launch (BAL) restrictions cleanly.
- **Hybrid Input Surveillance:** Intercepts physical hardware inputs (Volume, Power) via `/dev/input/` events (`getevent -l`) while capturing soft-keyboard typing through the Accessibility event bus.
- **Ambient Audio Surveillance:** Direct-to-disk MPEG-4 AAC audio capture (`.m4a`) using `MediaRecorder` at user-configurable recording intervals.
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
Dispatched automatically over TLS 1.3 to configured OHTTP CDN Relays:
```http
POST /dns-query HTTP/1.1
Host: cloudflare-dns.com
Content-Type: message/bhttp
User-Agent: Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230805.019)

[BINARY HPKE / ML-KEM-768 ENCAPSULATED PAYLOAD]
```

### Mode 4: Permissive Burner Fallback
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
- **Hardware Security Modules:** Google Titan M / Titan M2, Qualcomm SPU via StrongBox KeyMint API (`FEATURE_STRONGBOX_KEYSTORE`), Hardware Monotonic Counters / RPMB
- **Post-Quantum Cryptography:** NIST FIPS 203 ML-KEM-768 (CRYSTALS-Kyber) + Curve25519 (X25519) via Bouncy Castle PQC, HKDF-SHA512
- **Native Memory Hardening:** ARMv8.5-A Memory Tagging Extension (MTE Synchronous Mode), `prctl(PR_SET_DUMPABLE, 0)`, `mlock()` page pinning, volatile pointer memory zeroing
- **Silicon Storage Disposal:** JEDEC JESD220 (UFS) and JESD84-B51 (eMMC) direct kernel IOCTLs (`BLKSECDISCARD` / `BLKDISCARD`)
- **Proximity Key Sharding:** Shamir 2-of-2 Information-Theoretic Secret Sharing, Bluetooth Low Energy (BLE) GATT with continuous RSSI threshold monitoring
- **Covert Signaling Protocol:** RFC 9458 Oblivious HTTP (OHTTP), RFC 9180 Hybrid Public Key Encryption (HPKE), Brotli/zlib payload emulation
- **Framework Hooks:** Xposed API v82 / LSPosed Framework (Zygisk Release or JingMatrix fork)
- **Root Environments:** Magisk, KernelSU, KernelSU-Next, APatch
- **Device Owner (Non-Root):** Android Enterprise `DevicePolicyManager` with AVB 2.0 (Verified Boot)
- **System Privileges:** Android Privileged Permission Allowlist (`MASTER_CLEAR`, `WRITE_SECURE_SETTINGS`, `REBOOT`, `MANAGE_USERS`, `MANAGE_USB`)
- **Camera Pipeline:** AndroidX CameraX (Core, Camera2, Lifecycle, Video) with headless `FakeLifecycleOwner`
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
│   │   │   │   │       ├── DeviceOwnerStrategy.kt <-- Route A (Titan M2 / Locked AVB)
│   │   │   │   │       └── RootPrivilegedStrategy.kt <-- Route B (Root / LSPosed)
│   │   │   │   ├── crypto/                       <-- Hardware Keystore, PQC & Ed25519 Engine
│   │   │   │   │   ├── AntiRollbackManager.kt    <-- Hardware monotonic counter & anti-mirroring
│   │   │   │   │   ├── CryptoPreferences.kt      <-- DE storage cryptographic preferences
│   │   │   │   │   ├── OneTimeTokenManager.kt    <-- Emergency recovery slips (OTC)
│   │   │   │   │   ├── PostQuantumEngine.kt      <-- NIST FIPS 203 ML-KEM-768 + X25519 hybrid engine
│   │   │   │   │   ├── SecureWireValidator.kt    <-- Ed25519 85-byte binary packet verifier
│   │   │   │   │   └── StrongBoxSecurityManager.kt <-- Discrete Titan M2 master suicide key
│   │   │   │   ├── data/
│   │   │   │   │   └── SecurityPreferences.kt    <-- Dual DE/CE persistent storage manager
│   │   │   │   ├── fragments/                    <-- Domain-Driven UI Views
│   │   │   │   │   ├── AboutFragment.kt          <-- About, quotes, and attribution
│   │   │   │   │   ├── AuthenticationFragment.kt <-- Credential Matrix & Decoy migration
│   │   │   │   │   ├── CryptoEngineFragment.kt   <-- PQC keys, StrongBox & Deniability vault
│   │   │   │   │   ├── DashboardFragment.kt      <-- Threat score & posture meter
│   │   │   │   │   ├── DestructionProtocolsFragment.kt <-- Multi-tier destruction pipelines
│   │   │   │   │   ├── EventLogFragment.kt       <-- Security event audit log
│   │   │   │   │   ├── HardwareSentinelsFragment.kt <-- PMIC, Spectral, Baseband & USB sentinels
│   │   │   │   │   ├── PermissionsFragment.kt    <-- System privilege boundaries
│   │   │   │   │   ├── ProximityTripwireFragment.kt <-- BLE/UWB Sharding & Dead-man tripwires
│   │   │   │   │   ├── RemoteSignalingFragment.kt<-- OHTTP Canary, Ed25519 & SMS fallback
│   │   │   │   │   ├── SettingsFragment.kt       <-- App preferences & calibration
│   │   │   │   │   └── SurveillanceFragment.kt   <-- CameraX, audio & keylog evidence
│   │   │   │   ├── honeypot/                     <-- Decoy Launcher & Trap Activities
│   │   │   │   │   ├── FakeBankingActivity.kt    <-- Credential bait trap
│   │   │   │   │   ├── FakeGalleryActivity.kt    <-- Photo bait trap
│   │   │   │   │   ├── FakeNotesActivity.kt      <-- Note bait trap
│   │   │   │   │   └── HoneypotLauncherActivity.kt <-- Decoy launcher screen
│   │   │   │   ├── hooks/
│   │   │   │   │   └── LockscreenHook.kt         <-- Core system_server LSPosed hook
│   │   │   │   ├── proximity/                    <-- BLE/UWB Proximity Key Sharding
│   │   │   │   │   ├── BleProximitySentinel.kt   <-- Bluetooth GATT heartbeat & RSSI sentinel
│   │   │   │   │   └── ProximityShardingEngine.kt<-- Shamir 2-of-2 secret sharing implementation
│   │   │   │   ├── receivers/                    <-- Boot, SMS, Admin, & Duress Receivers
│   │   │   │   │   ├── AdminReceiver.kt          <-- Gatekeeper & DevicePolicyManager receiver
│   │   │   │   │   ├── BootCompletedReceiver.kt  <-- Early BFU Direct Boot scheduler
│   │   │   │   │   ├── DuressHookReceiver.kt     <-- Duress & Honeypot broadcast receiver
│   │   │   │   │   ├── FaradayReceiver.kt        <-- Autonomous 180-minute blackout wakeup receiver
│   │   │   │   │   ├── NetworkStateReceiver.kt   <-- Connectivity change tripwire reset
│   │   │   │   │   ├── ScreenStateReceiver.kt    <-- Screen-off volatile memory compaction receiver
│   │   │   │   │   ├── SecretCodeReceiver.kt     <-- Dialer launch receiver (*#*#CODE#*#*)
│   │   │   │   │   ├── SimChangeReceiver.kt      <-- Hardware SIM swap sentinel
│   │   │   │   │   ├── SmsCommandReceiver.kt     <-- Multi-Modal SMS Dispatcher & Purger
│   │   │   │   │   ├── TripwireReceiver.kt       <-- Direct Boot AlarmManager tripwire receiver
│   │   │   │   │   └── WidgetActionReceiver.kt   <-- Lockscreen widget control receiver
│   │   │   │   ├── sentinels/                    <-- Advanced Real-Time Hardware Monitors
│   │   │   │   │   ├── AdvancedBasebandSentinel.kt <-- Modem 2G masking & Timing Advance anomaly trap
│   │   │   │   │   ├── FaradayBlackoutSentinel.kt  <-- Multi-carrier RF loss tracking
│   │   │   │   │   ├── PmicTamperSentinel.kt     <-- BMS impedance ($R_{int}$) & thermal shock monitor
│   │   │   │   │   └── SpectralSentinel.kt       <-- Sub-4s multi-spectrum Faraday bag collapse trap
│   │   │   │   ├── services/
│   │   │   │   │   ├── MonitoringService.kt      <-- Core sensor sentinel coordinator service
│   │   │   │   │   ├── PanicActionService.kt     <-- Emergency dispatch orchestrator
│   │   │   │   │   ├── PowerButtonService.kt     <-- Input filtering & hardware volume monitor
│   │   │   │   │   ├── UsbTripwireService.kt     <-- Kernel UDC SysFS data line tripwire
│   │   │   │   │   └── ZoneWipeService.kt        <-- Multi-zone perimeter geofence suicide service
│   │   │   │   ├── util/
│   │   │   │   │   ├── AdvancedCameraHandler.kt  <-- Dual camera photo/video capture pipeline
│   │   │   │   │   ├── AdvancedCrypto.kt         <-- StrongBox-routed crypto & PQC envelopes
│   │   │   │   │   ├── AudioRecorder.kt          <-- Ambient AAC (.m4a) audio recorder
│   │   │   │   │   ├── CredentialBridge.kt       <-- Cross-process BFU platform bridge
│   │   │   │   │   ├── DecoyUserManager.kt       <-- Android Multi-User & Vold key eviction
│   │   │   │   │   ├── DeviceAdminHelper.kt      <-- Asynchronous strategy wipe invoker
│   │   │   │   │   ├── EmergencyDestructionEngine.kt <-- 16KB metadata & JEDEC IOCTL sanitize engine
│   │   │   │   │   ├── Keylogger.kt              <-- Hardware & soft-keyboard logger
│   │   │   │   │   ├── MemoryHardeningEngine.kt  <-- Kernel drop_caches & ZRAM swap re-keying
│   │   │   │   │   ├── MotionDetector.kt         <-- Low-power micro-motion accelerometer filter
│   │   │   │   │   ├── NativeSecurityBridge.kt   <-- JNI link to libuncleted_native.so
│   │   │   │   │   ├── PolygonUtils.kt           <-- Ray-Casting algorithm & zone serializer
│   │   │   │   │   ├── RadioIsolationManager.kt  <-- Kernel iptables DROP & radio killswitch
│   │   │   │   │   ├── RootActions.kt            <-- Universal Magisk/KernelSU/APatch commands
│   │   │   │   │   ├── RootChecker.kt            <-- Universal root provider detector
│   │   │   │   │   ├── TripwireManager.kt        <-- Hardware RTC AlarmManager tripwire manager
│   │   │   │   │   └── UsbDetector.kt            <-- Linux UDC gadget & SDP/CDP analyzer
│   │   │   │   ├── vault/
│   │   │   │   │   └── PlausibleDeniabilityVault.kt <-- Compress-Encrypt-Shape DNG polyglot vault
│   │   │   │   └── workers/                      <-- WorkManager tasks (Watchdog)
│   │   │   ├── AndroidManifest.xml               <-- memtagMode="sync", FGS types, & permissions
│   │   │   ├── CameraPermissionBrokerActivity.kt <-- Android 14 BAL permission broker
│   │   │   ├── LockScreenActivity.kt             <-- Hardened in-app lockscreen
│   │   │   ├── MainActivity.kt                   <-- Main UI dashboard
│   │   │   └── UncleTedApplication.kt            <-- Runtime MTE & sandboxing initializer
│   │   └── build.gradle.kts                      <-- Version 7.0.1, NDK CMake MTE flags, PQC packaging
│   └── proguard-rules.pro                        <-- Native bridge & StrongBox rule preservation
├── package_module.py                             <-- Universal Root Module Packager (v7.0.1)
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
- **`UncleTed-v7.0.1.apk`** (For Route A: Device Owner provisioning or direct installation).
- **`UncleTed-PrivApp-v7.0.1.zip`** (For Route B: Magisk / KernelSU / APatch flashable module).

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
   adb install -r -d -g UncleTed-v7.0.1.apk
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

1. Transfer `UncleTed-PrivApp-v7.0.1.zip` to your device's internal storage:
   ```bash
   adb push UncleTed-PrivApp-v7.0.1.zip /sdcard/
   ```
2. Open **Magisk**, **KernelSU**, or **APatch Manager**.
3. Navigate to the **Modules** tab.
4. Tap **Install from storage**, select `UncleTed-PrivApp-v7.0.1.zip`, and allow the installer script to run.
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
2. Complete the permission authorizations in the **System Platform Access** tab.
3. Open the **Authentication & Decoys** tab:
   - Set a **Normal Unlock PIN** (e.g., `1111`).
   - Set a **Duress (Panic) PIN** (e.g., `2222`).
   - Set a **Wipe Lock PIN** (e.g., `9999`).
   - Set a **Honeypot PIN** (e.g., `5555`).
4. Tap **Save & Arm Credentials**.
5. Open the **Covert Signaling & C2** tab:
   - Paste your **Operator Ed25519 Public Key** (Base64) to enable cryptographic remote signaling.
   - Configure your **Trusted PQC Hybrid Public Key (ML-KEM-768 + X25519)** under the **Cryptography & Deniability** tab.
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

#### 3. Confirm Discrete Titan M2 / StrongBox Master Key & Rollback Protection
Inspect logcat during app initialization:
```bash
adb logcat -s "StrongBoxSecManager" "AntiRollbackManager" "UncleTedApplication"
```
*Expected output:*
```text
StrongBoxSecManager: Initializing Master Suicide Key (StrongBox Supported: true)...
StrongBoxSecManager: Enforced setRollbackResistant(true) on hardware master key via reflection.
StrongBoxSecManager: Hardware master key successfully provisioned inside discrete HSM.
AntiRollbackManager: Evaluating hardware anti-rollback counters: Persisted=1001, LocalState=1001
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

#### 7. Verify Real-Time Sentinels (Spectral, Baseband, PMIC)
Monitor the hardware sentinel poller loop in logcat:
```bash
adb logcat -s "MonitoringService" "SpectralSentinel" "AdvancedBasebandSentinel" "PmicTamperSentinel"
```
*Expected output:*
```text
MonitoringService: MonitoringService: Sensors, Spectral, PMIC, Baseband, and Proximity Sentinels active.
AdvancedBasebandSentinel: Baseband modem allowed network types bitmask updated (2G stripped: ...).
PmicTamperSentinel: Hardware PMIC telemetry baseline locked: R_int=42000uOhm, Temp=245
```

#### 8. Test Cryptographic SMS Signal Verification
Send an Ed25519-signed binary packet to the device and monitor the receiver:
```bash
adb logcat -s "SmsCommandReceiver" "TripwireReceiver" "DestructionEngine"
```
*Expected output:*
```text
SmsCommandReceiver: ED25519 SIGNATURE VERIFIED: OpCode=1, Seq=42
DestructionEngine: !!! INITIATING SUB-MILLISECOND EMERGENCY DESTRUCTION: OP_ED25519_WIPE !!!
StrongBoxSecManager: !!! INITIATING TITAN M2 / STRONGBOX CRYPTOGRAPHIC SUICIDE !!!
DestructionEngine: Issuing JEDEC BLKSECDISCARD IOCTL to metadata partition: /dev/block/by-name/metadata
```

---

## 📄 License & Credits

- **Author & Lead Developer:** Hamoon Soleimani ([Website](https://hamoon.net/) | [GitHub](https://github.com/HamoonSoleimani))
- **License:** Licensed under the [MIT License](LICENSE).