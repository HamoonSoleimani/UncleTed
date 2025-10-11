<p align="center">
  <img src="https://hamoon.net/wp-content/uploads/2025/09/logo-transparent.png" alt="Uncle Ted Logo" width="180">
</p>

<h1 align="center">Uncle Ted for Android</h1>

<p align="center">
  <strong>A cabin in the digital woods.</strong><br>
  An advanced, privacy-focused anti-theft and personal security application for Android.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-blueviolet.svg" alt="Made with Kotlin">
</p>

---

### **Table of Contents**
- [**Warning**](#warning-legal--ethical-disclaimer)
- [**Screenshots**](#-screenshots)
- [**Core Features**](#-core-features)
  - [Security & Panic Triggers](#️-security--panic-triggers)
  - [Evidence Collection](#-evidence-collection)
  - [Stealth & Anti-Tampering](#-stealth--anti-tampering)
  - [Remote Control](#-remote-control)
  - [Automated & Advanced Security](#-automated--advanced-security)
  - [Root-Exclusive Features](#️-root-exclusive-features-extreme-caution)
- [**Technology Stack**](#-technology-stack)
- [**Setup & Installation**](#-setup--installation)
- [**How It Works**](#-how-it-works)
- [**Contributing**](#-contributing)
- [**License**](#-license)

---

> [!WARNING]
> ### **Warning: Legal & Ethical Disclaimer**
> This application is designed for **educational and defensive security purposes ONLY**. Features like stealth operations, remote data access, and data destruction carry significant ethical and legal responsibilities. **Never install or use this application on a device without the owner's explicit and informed consent.** Unauthorized use is strictly prohibited and may lead to severe legal consequences. The developer assumes no liability for any misuse of this software.

## 🖼️ Screenshots
<img width="200" height="800" alt="image" src="https://github.com/user-attachments/assets/16a8dbc3-e880-4db6-aee0-c48a7c31ae11" />
<img width="208" height="800" alt="image" src="https://github.com/user-attachments/assets/b762f43c-9760-4ac1-943a-bd7e94f54d08" />



## ✨ Core Features

Uncle Ted is a powerful security tool designed to give you ultimate control over your device's security, with a strong emphasis on privacy, remote control, and evidence collection in emergency situations.

### 🛡️ Security & Panic Triggers
- **Multi-Level PINs:** Set a normal PIN, a **Duress PIN** (unlocks the device but silently triggers a HIGH alert), and a **Wipe PIN** (unlocks but triggers a CRITICAL alert and initiates a data wipe).
- **Shake to Panic:** Vigorously shake the device to silently trigger a HIGH alert.
- **Device Admin Protection:** Prevents unauthorized uninstallation of the app.
- **Biometric Lock:** Secure the app's settings panel with your fingerprint or face.

### 📸 Evidence Collection
- **Intruder Selfie:** Automatically captures a photo from the front camera after 3+ failed PIN attempts.
- **Remote Evidence Gathering:** On HIGH or CRITICAL alerts, the app can capture photos (front & back), record video (front & back), and record ambient audio.
- **GPS Location:** All alerts include precise GPS coordinates.
- **Stealth Screenshot (Root):** Remotely capture a screenshot of the current screen.
- **Keylogger (Root):** Captures keystrokes to understand unauthorized activity.

### 👻 Stealth & Anti-Tampering
- **Fake Shutdown:** When an unauthorized user tries to power off the device, a fake shutdown animation is shown while the device remains on, sending alerts in the background.
- **Stealth Mode:** Hide the app's icon from the launcher. The app can then only be opened by dialing a secret code (e.g., `*#*#1234#*#*`).
- **Stealth Media Capture (Root):** Attempts to suppress the camera/mic privacy indicators (the "green dot") during evidence collection.

### 📡 Remote Control
- **SMS Commands:** Control the app remotely by sending a password-protected SMS.
  | Command | Description |
  | :--- | :--- |
  | `UNCLETED WIPE [password]` | Triggers a CRITICAL alert and wipes the device. |
  | `UNCLETED SIREN [password]` | Triggers a HIGH alert and activates a loud siren. |
  | `UNCLETED LOCK [password]` | Immediately locks the device. |
- **Email Alerts:** Receive detailed alerts with media attachments (photos, videos, audio) and device diagnostics.

### 🤖 Automated & Advanced Security
- **SIM Change Alert:** Sends an alert if the SIM card is removed or replaced.
- **Geofence "Safe Zone":** Triggers an alert if the device leaves a predefined geographical area.
- **Watchdog Mode:** Periodically sends a status update (location, battery) to your emergency contact.
- **Data Destruction Tripwire:** If the device fails to connect to the internet for a configurable period (e.g., 48 hours), it will automatically wipe all data.
- **AI-Powered Threat Engine:** A sophisticated engine that analyzes device behavior, app installations, and network activity to detect complex threats and adapt the security level in real-time.
- **Quantum-Inspired Security Layer:** An experimental layer that uses principles of quantum mechanics (superposition, uncertainty) for advanced intrusion detection.

### ☢️ Root-Exclusive Features (Extreme Caution)
> [!DANGER]
> These features require a rooted device and can be dangerous. Use them at your own risk. Incorrect use, especially of the flashing features, **can permanently brick your device.**

- **GPS Spoofing:** Provide a fake GPS location when a duress alert is triggered.
- **Silent App Installer:** Remotely install an APK from a URL via a special SMS command.
- **Firewall Tripwire:** If the Data Destruction Tripwire is activated, this will first block all network traffic using `iptables` before wiping.
- **Secure Wipe+:** Performs an anti-forensic wipe by physically overwriting the data partition with zeros.
- **System App Conversion:** Make the app a system app, rendering it immune to uninstallation without root access.
- **Ultimate Persistence:**
  - **Unkillable Service:** Installs a root-level script to ensure the monitoring service is always running.
  - **Hide Process:** Uses MagiskHide to make the app's process invisible to task managers.
  - **Survive Factory Reset:** (EXTREMELY DANGEROUS) Attempts to flash a loader to the recovery partition to automatically reinstall the app after a factory reset.

## 🛠️ Technology Stack

- **Language:** 100% [Kotlin](https://kotlinlang.org/)
- **Architecture:** MVVM (Model-View-ViewModel) with Fragments
- **Core Components:**
  - [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for asynchronous operations.
  - [Jetpack WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for robust background tasks (Watchdog, Tripwire).
  - [Jetpack CameraX](https://developer.android.com/training/camerax) for modern, reliable camera operations.
  - [Jetpack Security (EncryptedSharedPreferences)](https://developer.android.com/topic/security/data) for securely storing all sensitive settings.
  - [Jetpack Biometric](https://developer.android.com/training/sign-in/biometric-auth) for secure app access.
- **UI:** Material Design 3 Components
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [Gson](https://github.com/google/gson)
- **Email:** [JavaMail for Android](https://javaee.github.io/javamail/)

## 🚀 Setup & Installation

### Prerequisites
- Android Studio (latest stable version)
- Android SDK targeting API 34
- An Android device or emulator running API 29+

### Steps
1.  Clone the repository: `git clone https://github.com/HamoonSoleimani/UncleTed.git`
2.  Open the project in Android Studio.
3.  Let Gradle sync and download all the required dependencies.
4.  Build and run the application on your target device.
5.  **Important:** Upon first launch, navigate to the **Core Services** screen and grant all required permissions. These are essential for the app to function.
6.  Configure your desired settings in the **Authentication**, **Remote Control**, and **Features** screens.

## ⚙️ How It Works

The application's resilience and power come from its deep integration with the Android OS:

-   A persistent **Foreground Service** (`MonitoringService`) handles real-time tasks like shake detection.
-   **Broadcast Receivers** listen for critical system events like `BOOT_COMPLETED`, `SMS_RECEIVED`, and SIM state changes, allowing the app to react instantly.
-   The **Device Admin API** grants the app privileges to lock the screen, wipe data, and prevent uninstallation.
-   An **Accessibility Service** is cleverly used to detect when the system power menu is opened, enabling the "Fake Shutdown" feature.
-   **WorkManager** ensures that long-running, deferrable tasks like the Watchdog and Tripwire are executed reliably, even if the app is closed or the device reboots.

## 🙌 Contributing

Contributions are welcome! If you'd like to help improve Uncle Ted, please follow these steps:

1.  **Fork** the repository.
2.  Create a new **feature branch** (`git checkout -b feature/AmazingFeature`).
3.  **Commit** your changes (`git commit -m 'Add some AmazingFeature'`).
4.  **Push** to the branch (`git push origin feature/AmazingFeature`).
5.  Open a **Pull Request**.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
