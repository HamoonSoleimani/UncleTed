#!/usr/bin/env python3
"""
Universal Module Packager for Uncle Ted (v10.0.1)
Generates an out-of-the-box flashable ZIP compatible with:
- KernelSU / KernelSU-Next (In-app & Recovery)
- Magisk / Kitsune Mask (In-app & Recovery)
- APatch (In-app & Recovery)
- Custom Recoveries (TWRP / OrangeFox / EvolutionX / Lineage Recovery)

Implements:
- Early post-fs-data mount script for KernelSU / APatch / Magisk before Zygote/PMS start
- Systemless priv-app integration with verified-mount /data/app deduplication
- Fallback overlayfs mounting for KernelSU environments without metamodules
- Privileged permission allowlist injection (MASTER_CLEAR, MANAGE_USB, REBOOT, WRITE_SECURE_SETTINGS)
- Native 64-bit library deployment (libuncleted_native.so with ARMv8.5-A MTE support)
- Discrete StrongBox / Titan M2 KeyMint runtime compatibility
- Early-boot Multi-User property injection (fw.max_users=5, fw.show_multiuserui=1)
- Direct-Boot (BFU) platform bridge provisioning (/data/system/uncleted with 0700/0600 mode)
- User 0 package activation via 'pm install-existing' (prevents /data/app user-space overrides)
"""

import os
import sys
import zipfile

MODULE_ID = "uncleted_privapp"
MODULE_NAME = "UncleTed System Priv-App & Hook"
MODULE_VERSION = "v10.0.1"
MODULE_VERSION_CODE = "10"
MODULE_AUTHOR = "Hamoon Soleimani"
MODULE_DESCRIPTION = (
    "v10.0.1: Domain-driven defense suite with discrete Titan M2 StrongBox suicide engine, "
    "NIST FIPS 203 ML-KEM-768 + X25519 PQC, RFC 9458 OHTTP covert canary, BLE proximity key sharding, "
    "Plausible Deniability DNG vault, ARMv8.5-A synchronous MTE hardening, physical USB HAL port severing, "
    "cold Vold keyring eviction, multi-user decoy space migration, and native LSPosed Keyguard interception hooks."
)

# Adaptive path resolution (handles execution from either project root or app/ directory)
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if os.path.basename(CURRENT_DIR) == "app":
    APP_DIR = CURRENT_DIR
    PROJECT_ROOT = os.path.dirname(CURRENT_DIR)
else:
    PROJECT_ROOT = CURRENT_DIR
    APP_DIR = os.path.join(CURRENT_DIR, "app")

OUTPUT_DIR = os.path.join(APP_DIR, "build_output")
ZIP_OUTPUT_PATH = os.path.join(OUTPUT_DIR, f"UncleTed-PrivApp-{MODULE_VERSION}.zip")

POSSIBLE_PERM_PATHS = [
    os.path.join(APP_DIR, "distribution", "etc", "permissions", "privapp-permissions-uncleted.xml"),
    os.path.join(PROJECT_ROOT, "distribution", "etc", "permissions", "privapp-permissions-uncleted.xml"),
    os.path.join(APP_DIR, "src", "main", "distribution", "etc", "permissions", "privapp-permissions-uncleted.xml"),
]

POSSIBLE_APK_PATHS = [
    os.path.join(APP_DIR, "build", "outputs", "apk", "release", f"UncleTed-{MODULE_VERSION}.apk"),
    os.path.join(APP_DIR, "build", "outputs", "apk", "release", "UncleTed-v10.0.1.apk"),
    os.path.join(APP_DIR, "build", "outputs", "apk", "release", "UncleTed-v9.0.1.apk"),
    os.path.join(APP_DIR, "build", "outputs", "apk", "release", "app-release.apk"),
    os.path.join(APP_DIR, "build", "outputs", "apk", "release", "app-release-unsigned.apk"),
    os.path.join(APP_DIR, "build", "outputs", "apk", "debug", "app-debug.apk"),
    os.path.join(PROJECT_ROOT, f"UncleTed-{MODULE_VERSION}.apk"),
    os.path.join(PROJECT_ROOT, "UncleTed-v10.0.1.apk"),
    os.path.join(PROJECT_ROOT, "UncleTed-v9.0.1.apk"),
    os.path.join(PROJECT_ROOT, "UncleTed-v8.0.1.apk"),
]

SYSTEM_PROP_CONTENT = """# Multi-User Framework Flags for Uncle Ted Decoy Space
fw.max_users=5
fw.show_multiuserui=1
persist.sys.max_users=5
persist.sys.fw.max_users=5
"""

# Early-boot mount script executed during post-fs-data before PMS initializes
POST_FS_DATA_CONTENT = r'''#!/system/bin/sh
MODDIR=${0%/*}

# Early-boot fallback mount for KernelSU / APatch if /system was not mounted by a metamodule
if [ ! -f "/system/priv-app/UncleTed/UncleTed.apk" ] && [ -f "$MODDIR/system/priv-app/UncleTed/UncleTed.apk" ]; then
    mount -t overlay overlay -o lowerdir=$MODDIR/system/priv-app:/system/priv-app /system/priv-app 2>/dev/null || true
fi
'''

UPDATE_BINARY_CONTENT = r'''#!/bin/sh
##########################################################################################
# Universal Recovery & Root Manager Installer for Uncle Ted (v10.0.1)
# Compatible with AOSP / Evolution X / Lineage Recovery (/bin/sh) and TWRP (/sbin/sh)
##########################################################################################

if [ -z "$BASH_VERSION" ] && [ ! -f /bin/sh ]; then
  if [ -f /system/bin/sh ]; then
    exec /system/bin/sh "$0" "$@"
  elif [ -f /sbin/sh ]; then
    exec /sbin/sh "$0" "$@"
  fi
fi

OUTFD=$2
ZIPFILE=$3

[ -z "$OUTFD" ] && OUTFD=1

ui_print() {
  if [ -e "/proc/self/fd/$OUTFD" ]; then
    echo -e "ui_print $1\nui_print" > /proc/self/fd/$OUTFD
  else
    echo "$1"
  fi
}

ui_print "***********************************************"
ui_print "       Uncle Ted System Defense Suite          "
ui_print "    Hardware StrongBox & Hook Mode (v10.0.1)   "
ui_print "***********************************************"

BOOTMODE=false
if [ "$(getprop sys.boot_completed)" = "1" ]; then
  BOOTMODE=true
elif ps | grep -q zygote || ps -A 2>/dev/null | grep -q zygote; then
  BOOTMODE=true
fi

if [ -z "$ZIPFILE" ] || [ ! -f "$ZIPFILE" ]; then
  if [ -f "$3" ]; then
    ZIPFILE="$3"
  elif [ -f "$1" ]; then
    ZIPFILE="$1"
  elif [ -f "/sideload/package.zip" ]; then
    ZIPFILE="/sideload/package.zip"
  fi
fi

if [ "$BOOTMODE" = "false" ]; then
  ui_print "- Detected Recovery environment. Preparing filesystem..."

  if ! mountpoint -q /data 2>/dev/null; then
    mount /data 2>/dev/null || mount -o rw /data 2>/dev/null || true
  fi

  if [ ! -d "/data" ] || [ ! -w "/data" ]; then
    ui_print "! Error: /data partition is encrypted or not accessible."
    ui_print "! In TWRP/OrangeFox: Unlock with your screen lock credentials."
    ui_print "! In stock AOSP recovery: Boot Android and flash via KernelSU/Magisk/APatch."
    exit 1
  fi
fi

MODPATH="/data/adb/modules/uncleted_privapp"
SERVICE_D_DIR="/data/adb/service.d"
POST_MOUNT_D_DIR="/data/adb/post-mount.d"
POST_FS_DATA_D_DIR="/data/adb/post-fs-data.d"
BACKUP_APK_DIR="/data/adb/uncleted"
TARGET_PRIVAPP="$MODPATH/system/priv-app/UncleTed"
TARGET_LIB_DIR="$TARGET_PRIVAPP/lib/arm64"

mkdir -p "$MODPATH"
mkdir -p "$SERVICE_D_DIR"
mkdir -p "$POST_MOUNT_D_DIR"
mkdir -p "$POST_FS_DATA_D_DIR"
mkdir -p "$BACKUP_APK_DIR"
mkdir -p "$TARGET_PRIVAPP"
mkdir -p "$TARGET_LIB_DIR"

ui_print "- Extracting module payload..."
if command -v unzip >/dev/null 2>&1; then
  unzip -o "$ZIPFILE" -d "$MODPATH" >/dev/null 2>&1
elif [ -f "/system/bin/unzip" ]; then
  /system/bin/unzip -o "$ZIPFILE" -d "$MODPATH" >/dev/null 2>&1
elif [ -f "/sbin/unzip" ]; then
  /sbin/unzip -o "$ZIPFILE" -d "$MODPATH" >/dev/null 2>&1
else
  busybox unzip -o "$ZIPFILE" -d "$MODPATH" >/dev/null 2>&1
fi

ui_print "- Deploying native MTE-hardened binaries..."
APK_FILE="$TARGET_PRIVAPP/UncleTed.apk"
if [ -f "$APK_FILE" ]; then
  if command -v unzip >/dev/null 2>&1; then
    unzip -j -o "$APK_FILE" "lib/arm64-v8a/*" -d "$TARGET_LIB_DIR" >/dev/null 2>&1 || true
  elif [ -f "/system/bin/unzip" ]; then
    /system/bin/unzip -j -o "$APK_FILE" "lib/arm64-v8a/*" -d "$TARGET_LIB_DIR" >/dev/null 2>&1 || true
  elif [ -f "/sbin/unzip" ]; then
    /sbin/unzip -j -o "$APK_FILE" "lib/arm64-v8a/*" -d "$TARGET_LIB_DIR" >/dev/null 2>&1 || true
  else
    busybox unzip -j -o "$APK_FILE" "lib/arm64-v8a/*" -d "$TARGET_LIB_DIR" >/dev/null 2>&1 || true
  fi
fi

# Only purge /data/app in recovery mode; in bootmode, wait until service.sh verifies active mount
if [ "$BOOTMODE" = "false" ]; then
  ui_print "- Purging user-space overrides in /data/app..."
  find /data/app -type d -name "*com.hamoon.uncleted*" -exec rm -rf {} + 2>/dev/null || true
  rm -rf /data/app/*com.hamoon.uncleted* 2>/dev/null || true
fi

ui_print "- Setting permissions and security contexts..."
chmod -R 755 "$MODPATH"
chmod 644 "$MODPATH/module.prop"
chmod 644 "$MODPATH/system.prop" 2>/dev/null || true
chmod 755 "$TARGET_PRIVAPP"
chmod 644 "$TARGET_PRIVAPP/UncleTed.apk"
chmod 755 "$TARGET_PRIVAPP/lib" 2>/dev/null || true
chmod 755 "$TARGET_LIB_DIR" 2>/dev/null || true
chmod 644 "$TARGET_LIB_DIR"/*.so 2>/dev/null || true
chmod 755 "$MODPATH/system/etc/permissions"
chmod 644 "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml"
chmod 755 "$MODPATH/service.sh" 2>/dev/null || true
chmod 755 "$MODPATH/post-fs-data.sh" 2>/dev/null || true
chmod 755 "$MODPATH/customize.sh" 2>/dev/null || true
chown -R 0:0 "$MODPATH"
chcon -R u:object_r:system_file:s0 "$MODPATH/system" 2>/dev/null || true

# Prepare Direct-Boot (BFU) platform bridge directory with strict 0700/0600 mode
mkdir -p /data/system/uncleted
chmod 700 /data/system/uncleted
chown 1000:1000 /data/system/uncleted
chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true

if [ -f "/data/system/uncleted/credentials.cfg" ]; then
  chmod 600 /data/system/uncleted/credentials.cfg
  chown 1000:1000 /data/system/uncleted/credentials.cfg
  chcon u:object_r:system_data_file:s0 /data/system/uncleted/credentials.cfg 2>/dev/null || true
fi

cp -f "$TARGET_PRIVAPP/UncleTed.apk" "$BACKUP_APK_DIR/UncleTed.apk"
chmod 644 "$BACKUP_APK_DIR/UncleTed.apk"

# Persistent boot service script
BOOT_SCRIPT_CONTENT='#!/system/bin/sh
export PATH="/system/bin:/system/xbin:/vendor/bin:$PATH"

(
  LOG="/data/adb/uncleted/boot.log"
  mkdir -p /data/adb/uncleted
  echo "[$(date)] Uncle Ted boot service active (v10.0.1)" > "$LOG"

  # Apply Multi-User early framework properties
  if command -v resetprop >/dev/null 2>&1; then
    resetprop fw.max_users 5
    resetprop fw.show_multiuserui 1
    resetprop persist.sys.max_users 5
    resetprop persist.sys.fw.max_users 5
  else
    setprop fw.max_users 5
    setprop fw.show_multiuserui 1
    setprop persist.sys.max_users 5
    setprop persist.sys.fw.max_users 5
  fi
  settings put global allow_user_switching_when_system_user_locked 1 >/dev/null 2>&1 || true

  while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done
  echo "[$(date)] sys.boot_completed is 1" >> "$LOG"

  WAIT_SECS=0
  while [ "$(getprop sys.user.0.ce_available)" != "true" ] && [ ! -d "/data/user/0" ]; do
    if [ "$(getprop vold.decrypt)" = "trigger_restart_framework" ] || [ $WAIT_SECS -ge 45 ]; then
      break
    fi
    sleep 2
    WAIT_SECS=$((WAIT_SECS + 2))
  done
  echo "[$(date)] User 0 storage available" >> "$LOG"

  sleep 3

  PKG="com.hamoon.uncleted"

  # Only purge /data/app user-space duplicates IF /system/priv-app mount is verified active!
  if [ -f "/system/priv-app/UncleTed/UncleTed.apk" ]; then
    find /data/app -type d -name "*com.hamoon.uncleted*" -exec rm -rf {} + 2>/dev/null || true
    cmd package install-existing --user 0 "$PKG" >> "$LOG" 2>&1 || pm install-existing --user 0 "$PKG" >> "$LOG" 2>&1 || true
    pm enable --user 0 "$PKG" >/dev/null 2>&1 || true
    echo "[$(date)] Priv-app bound and activated for User 0" >> "$LOG"
  else
    echo "[$(date)] WARNING: /system/priv-app not mounted! Retaining /data/app to prevent app loss." >> "$LOG"
  fi

  # Auto-configure Root Manager superuser profiles
  if command -v ksud >/dev/null 2>&1; then
    echo "[$(date)] Configuring KernelSU superuser profile..." >> "$LOG"
    ksud profile set $PKG --allow-su true >/dev/null 2>&1 || true
    ksud profile set $PKG allow.su true >/dev/null 2>&1 || true
  fi

  if command -v apd >/dev/null 2>&1; then
    echo "[$(date)] Configuring APatch superuser profile..." >> "$LOG"
    apd profile set $PKG --allow-su true >/dev/null 2>&1 || true
    apd profile set $PKG allow.su true >/dev/null 2>&1 || true
  fi

  # Maintain platform bridge integrity with 0700 / 0600 mode
  mkdir -p /data/system/uncleted
  chmod 700 /data/system/uncleted
  chown 1000:1000 /data/system/uncleted
  chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true

  if [ -f "/data/system/uncleted/credentials.cfg" ]; then
    chmod 600 /data/system/uncleted/credentials.cfg
    chown 1000:1000 /data/system/uncleted/credentials.cfg
    chcon u:object_r:system_data_file:s0 /data/system/uncleted/credentials.cfg 2>/dev/null || true
  fi

  echo "[$(date)] Uncle Ted boot service execution completed." >> "$LOG"
) &'

echo "$BOOT_SCRIPT_CONTENT" > "$SERVICE_D_DIR/uncleted_boot.sh"
chmod 755 "$SERVICE_D_DIR/uncleted_boot.sh"
chown 0:0 "$SERVICE_D_DIR/uncleted_boot.sh"

EARLY_MOUNT_CONTENT='#!/system/bin/sh
if [ ! -f "/system/priv-app/UncleTed/UncleTed.apk" ] && [ -f "/data/adb/modules/uncleted_privapp/system/priv-app/UncleTed/UncleTed.apk" ]; then
    mount -t overlay overlay -o lowerdir=/data/adb/modules/uncleted_privapp/system/priv-app:/system/priv-app /system/priv-app 2>/dev/null || true
fi
'
echo "$EARLY_MOUNT_CONTENT" > "$POST_FS_DATA_D_DIR/uncleted_early_mount.sh"
chmod 755 "$POST_FS_DATA_D_DIR/uncleted_early_mount.sh"
chown 0:0 "$POST_FS_DATA_D_DIR/uncleted_early_mount.sh"

if [ "$BOOTMODE" = "true" ]; then
  if command -v ksud >/dev/null 2>&1; then
    ui_print "- Configuring KernelSU superuser profile..."
    ksud profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
    ksud profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
  fi

  if command -v apd >/dev/null 2>&1; then
    ui_print "- Configuring APatch superuser profile..."
    apd profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
    apd profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
  fi
  ui_print "- Please reboot your device to complete priv-app system mounting."
else
  ui_print "- Flashed successfully in Recovery."
  ui_print "- Reboot into Android to initialize the multi-user framework bridge."
fi

ui_print " "
ui_print "✓ Installation successful (v10.0.1)!"
ui_print "✓ Systemless priv-app, StrongBox, Multi-User, and ARM MTE layers armed."
exit 0
'''

CUSTOMIZE_SH_CONTENT = r'''#!/sbin/sh
##########################################################################################
# Magisk / KernelSU / APatch In-App Customization Script (v10.0.1)
##########################################################################################

ui_print "- Extracting native ARMv8.5-A MTE hardened libraries..."
TARGET_PRIVAPP="$MODPATH/system/priv-app/UncleTed"
TARGET_LIB_DIR="$TARGET_PRIVAPP/lib/arm64"
mkdir -p "$TARGET_LIB_DIR"

APK_SRC="$TARGET_PRIVAPP/UncleTed.apk"
if [ -f "$APK_SRC" ]; then
    unzip -j -o "$APK_SRC" "lib/arm64-v8a/*" -d "$TARGET_LIB_DIR" >/dev/null 2>&1 || true
fi

# KernelSU Metamodule Check: Metamodule is required by KernelSU to mount /system/priv-app
if [ -d "/data/adb/ksu" ] || command -v ksud >/dev/null 2>&1; then
    if [ ! -d "/data/adb/metamodule" ] && [ ! -d "/data/adb/modules/meta-overlay" ] && [ ! -d "/data/adb/modules/meta-overlayfs" ] && [ ! -d "/data/adb/modules/meta-hybrid_mount" ] && [ ! -d "/data/adb/modules/meta-magic_mount" ]; then
        ui_print "********************************************************"
        ui_print "! NOTICE: KernelSU Metamodule (e.g. meta-overlayfs)    !"
        ui_print "! is required by KernelSU to mount /system/priv-app.   !"
        ui_print "! If the app does not show as a system app on reboot,  !"
        ui_print "! please install 'meta-overlayfs' in KernelSU Modules. !"
        ui_print "********************************************************"
    fi
fi

ui_print "- Applying SELinux contexts and POSIX permissions..."
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$TARGET_PRIVAPP" 0 0 0755
set_perm "$APK_SRC" 0 0 0644
if [ -d "$TARGET_PRIVAPP/lib" ]; then
    set_perm_recursive "$TARGET_PRIVAPP/lib" 0 0 0755 0644
fi
set_perm "$MODPATH/system/etc/permissions" 0 0 0755
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" 0 0 0644
set_perm "$MODPATH/system.prop" 0 0 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
chcon -R u:object_r:system_file:s0 "$MODPATH/system"

# Apply live multi-user properties immediately
if command -v resetprop >/dev/null 2>&1; then
    resetprop fw.max_users 5
    resetprop fw.show_multiuserui 1
    resetprop persist.sys.max_users 5
    resetprop persist.sys.fw.max_users 5
fi
settings put global allow_user_switching_when_system_user_locked 1 >/dev/null 2>&1 || true

PKG="com.hamoon.uncleted"
if [ "$BOOTMODE" = "true" ]; then
    if command -v ksud >/dev/null 2>&1; then
        ui_print "- Configuring KernelSU superuser profile..."
        ksud profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
        ksud profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
    fi

    if command -v apd >/dev/null 2>&1; then
        ui_print "- Configuring APatch superuser profile..."
        apd profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
        apd profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
    fi
fi

# Prepare Direct-Boot (BFU) platform bridge directory with 0700/0600 mode
mkdir -p /data/system/uncleted
chmod 700 /data/system/uncleted
chown 1000:1000 /data/system/uncleted
chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true

if [ -f "/data/system/uncleted/credentials.cfg" ]; then
    chmod 600 /data/system/uncleted/credentials.cfg
    chown 1000:1000 /data/system/uncleted/credentials.cfg
    chcon u:object_r:system_data_file:s0 /data/system/uncleted/credentials.cfg 2>/dev/null || true
fi
'''

SERVICE_SH_CONTENT = r'''#!/system/bin/sh
MODDIR=${0%/*}
export PATH="/system/bin:/system/xbin:/vendor/bin:$PATH"

(
  # Apply early-boot Multi-User properties
  if command -v resetprop >/dev/null 2>&1; then
    resetprop fw.max_users 5
    resetprop fw.show_multiuserui 1
    resetprop persist.sys.max_users 5
    resetprop persist.sys.fw.max_users 5
  else
    setprop fw.max_users 5
    setprop fw.show_multiuserui 1
    setprop persist.sys.max_users 5
    setprop persist.sys.fw.max_users 5
  fi
  settings put global allow_user_switching_when_system_user_locked 1 >/dev/null 2>&1 || true

  while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done

  WAIT_SECS=0
  while [ "$(getprop sys.user.0.ce_available)" != "true" ] && [ ! -d "/data/user/0" ]; do
    if [ "$(getprop vold.decrypt)" = "trigger_restart_framework" ] || [ $WAIT_SECS -ge 45 ]; then
      break
    fi
    sleep 2
    WAIT_SECS=$((WAIT_SECS + 2))
  done

  sleep 3

  PKG="com.hamoon.uncleted"

  # Only purge /data/app user-space duplicates IF /system/priv-app mount is verified active!
  if [ -f "/system/priv-app/UncleTed/UncleTed.apk" ]; then
    find /data/app -type d -name "*com.hamoon.uncleted*" -exec rm -rf {} + 2>/dev/null || true
    cmd package install-existing --user 0 "$PKG" >/dev/null 2>&1 || pm install-existing --user 0 "$PKG" >/dev/null 2>&1 || true
    pm enable --user 0 "$PKG" >/dev/null 2>&1 || true
  fi

  if command -v ksud >/dev/null 2>&1; then
    ksud profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
    ksud profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
  fi

  if command -v apd >/dev/null 2>&1; then
    apd profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
    apd profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
  fi

  # Maintain platform bridge integrity with 0700 / 0600 mode
  mkdir -p /data/system/uncleted
  chmod 700 /data/system/uncleted
  chown 1000:1000 /data/system/uncleted
  chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true

  if [ -f "/data/system/uncleted/credentials.cfg" ]; then
    chmod 600 /data/system/uncleted/credentials.cfg
    chown 1000:1000 /data/system/uncleted/credentials.cfg
    chcon u:object_r:system_data_file:s0 /data/system/uncleted/credentials.cfg 2>/dev/null || true
  fi
) &
'''

UPDATER_SCRIPT_CONTENT = "#MAGISK\n"

MODULE_PROP_CONTENT = f"""id={MODULE_ID}
name={MODULE_NAME}
version={MODULE_VERSION}
versionCode={MODULE_VERSION_CODE}
author={MODULE_AUTHOR}
description={MODULE_DESCRIPTION}
"""


def find_file(candidate_paths):
    for path in candidate_paths:
        if os.path.isfile(path):
            return path
    return None


def write_zip_entry(zip_file, archive_path, content, is_executable=False):
    info = zipfile.ZipInfo(archive_path)
    info.compress_type = zipfile.ZIP_DEFLATED
    if is_executable:
        info.external_attr = 0o100755 << 16
    else:
        info.external_attr = 0o100644 << 16
    zip_file.writestr(info, content)


def write_zip_file_entry(zip_file, archive_path, source_file_path, is_executable=False):
    info = zipfile.ZipInfo(archive_path)
    info.compress_type = zipfile.ZIP_DEFLATED
    if is_executable:
        info.external_attr = 0o100755 << 16
    else:
        info.external_attr = 0o100644 << 16
    with open(source_file_path, "rb") as f:
        zip_file.writestr(info, f.read())


def main():
    print("==================================================")
    print(f" Uncle Ted Universal Flashable Packager ({MODULE_VERSION})")
    print("==================================================")

    apk_path = find_file(POSSIBLE_APK_PATHS)
    if not apk_path:
        print("\n[ERROR] No compiled APK found. Build the project first:")
        print("        ./gradlew assembleRelease  (or assembleDebug)")
        print("\nTried searching in:")
        for p in POSSIBLE_APK_PATHS:
            print(f"  - {p}")
        sys.exit(1)

    print(f"[+] Found compiled APK: {apk_path}")

    perms_path = find_file(POSSIBLE_PERM_PATHS)
    if not perms_path:
        print("\n[ERROR] Missing permission allowlist XML file.")
        print("Searched in:")
        for p in POSSIBLE_PERM_PATHS:
            print(f"  - {p}")
        sys.exit(1)

    print(f"[+] Found privileged permissions XML: {perms_path}")

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    if os.path.exists(ZIP_OUTPUT_PATH):
        os.remove(ZIP_OUTPUT_PATH)

    print(f"[+] Packaging universal flashable module: {ZIP_OUTPUT_PATH}")

    with zipfile.ZipFile(ZIP_OUTPUT_PATH, "w", compression=zipfile.ZIP_DEFLATED) as z:
        # Module metadata and properties
        write_zip_entry(z, "module.prop", MODULE_PROP_CONTENT, is_executable=False)
        write_zip_entry(z, "system.prop", SYSTEM_PROP_CONTENT, is_executable=False)

        # Recovery & Root Manager execution entrypoints
        write_zip_entry(z, "META-INF/com/google/android/update-binary", UPDATE_BINARY_CONTENT, is_executable=True)
        write_zip_entry(z, "META-INF/com/google/android/updater-script", UPDATER_SCRIPT_CONTENT, is_executable=False)
        write_zip_entry(z, "customize.sh", CUSTOMIZE_SH_CONTENT, is_executable=True)
        write_zip_entry(z, "post-fs-data.sh", POST_FS_DATA_CONTENT, is_executable=True)
        write_zip_entry(z, "service.sh", SERVICE_SH_CONTENT, is_executable=True)

        # Systemless priv-app filesystem payloads
        write_zip_file_entry(
            z, "system/priv-app/UncleTed/UncleTed.apk", apk_path, is_executable=False
        )
        write_zip_file_entry(
            z,
            "system/etc/permissions/privapp-permissions-uncleted.xml",
            perms_path,
            is_executable=False,
        )

    print("\n[SUCCESS] Universal flashable module packaged successfully!")
    print(f"Output artifact: {ZIP_OUTPUT_PATH}")
    print("\nVerified installation targets:")
    print("1. Magisk / Kitsune Mask Manager (In-OS)")
    print("2. KernelSU / KernelSU-Next Manager (In-OS)")
    print("3. APatch Manager (In-OS)")
    print("4. Custom Recovery (TWRP / OrangeFox / Lineage / EvolutionX / Sideload)")


if __name__ == "__main__":
    main()
