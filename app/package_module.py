#!/usr/bin/env python3
"""
Universal Module Packager for Uncle Ted
Generates an out-of-the-box systemless flashable ZIP compatible with:
- KernelSU / KernelSU-Next
- Magisk / Kitsune Mask
- APatch

Includes dual-install automation (installs both as a systemless priv-app and
into user space) to permanently eliminate KernelSU mount namespace isolation
crashes, missing resources, and missing launcher icon issues.
"""

import os
import sys
import shutil
import zipfile

MODULE_ID = "uncleted_privapp"
MODULE_NAME = "UncleTed System Priv-App & Hook"
MODULE_VERSION = "v3.0.1"
MODULE_VERSION_CODE = "3"
MODULE_AUTHOR = "Hamoon Soleimani"
MODULE_DESCRIPTION = (
    "Systemless priv-app with LSPosed native Keyguard hooks, anti-coercion defense, "
    "and out-of-the-box KernelSU, Magisk, and APatch dual-install support."
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(BASE_DIR)
OUTPUT_DIR = os.path.join(BASE_DIR, "build_output")
ZIP_OUTPUT_PATH = os.path.join(OUTPUT_DIR, f"UncleTed-PrivApp-{MODULE_VERSION}.zip")

PRIVAPP_PERMS_SOURCE = os.path.join(
    BASE_DIR, "distribution", "etc", "permissions", "privapp-permissions-uncleted.xml"
)

POSSIBLE_APK_PATHS = [
    os.path.join(BASE_DIR, "build", "outputs", "apk", "release", "app-release.apk"),
    os.path.join(BASE_DIR, "build", "outputs", "apk", "release", "app-release-unsigned.apk"),
    os.path.join(BASE_DIR, "build", "outputs", "apk", "debug", "app-debug.apk"),
]

CUSTOMIZE_SH_CONTENT = r'''#!/sbin/sh
##########################################################################################
# Uncle Ted Systemless Priv-App Installer
##########################################################################################

ui_print "***********************************************"
ui_print "       Uncle Ted System Defense Suite          "
ui_print "    Priv-App Overlay & Platform Integrator     "
ui_print "***********************************************"

# Set standard directory and file permissions
ui_print "- Applying SELinux contexts and POSIX permissions..."
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$MODPATH/system/priv-app/UncleTed" 0 0 0755
set_perm "$MODPATH/system/priv-app/UncleTed/UncleTed.apk" 0 0 0644
set_perm "$MODPATH/system/etc/permissions" 0 0 0755
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" 0 0 0644
chcon -R u:object_r:system_file:s0 "$MODPATH/system"

# Crucial Out-of-the-Box KernelSU / Magisk Fix:
# In KernelSU, apps are isolated and module mounts are stripped by default in user namespaces.
# Installing the APK directly ensures LoadedApk always resolves assets without relying on
# isolated /system/priv-app mount namespaces.
if [ "$BOOTMODE" = "true" ]; then
    ui_print "- Installing Uncle Ted into package database (Dual-Install)..."
    APK_SRC="$MODPATH/system/priv-app/UncleTed/UncleTed.apk"
    if [ -f "$APK_SRC" ]; then
        pm install -r -d -g "$APK_SRC" >/dev/null 2>&1 || pm install -r -d "$APK_SRC" >/dev/null 2>&1
    fi

    # Automatically configure KernelSU App Profile if ksud is available
    if command -v ksud >/dev/null 2>&1; then
        ui_print "- Configuring KernelSU superuser profile..."
        ksud profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
        ksud profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
    fi
fi

# Ensure credentials bridge directory exists with proper SELinux context
mkdir -p /data/system/uncleted
chmod 0755 /data/system/uncleted
chown 1000:1000 /data/system/uncleted
chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true

ui_print " "
ui_print "✓ Installation successful!"
ui_print "✓ Enable 'UncleTed' in LSPosed Manager and reboot to complete setup."
'''

SERVICE_SH_CONTENT = r'''#!/system/bin/sh
MODDIR=${0%/*}

# Wait until device fully completes boot
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 3
done
sleep 2

APK_PATH="$MODDIR/system/priv-app/UncleTed/UncleTed.apk"

# Ensure APK is indexed and available in user space
if [ -f "$APK_PATH" ]; then
    CURRENT_PATH=$(pm path com.hamoon.uncleted 2>/dev/null)
    if [ -z "$CURRENT_PATH" ] || echo "$CURRENT_PATH" | grep -q "/system/priv-app"; then
        pm install -r -d -g "$APK_PATH" >/dev/null 2>&1 || pm install -r -d "$APK_PATH" >/dev/null 2>&1
    fi
fi

# KernelSU / KernelSU-Next CLI Profile Enforcement
if command -v ksud >/dev/null 2>&1; then
    ksud profile set com.hamoon.uncleted --allow-su >/dev/null 2>&1 || true
    ksud profile set com.hamoon.uncleted allow.su true >/dev/null 2>&1 || true
fi

# Ensure platform credentials directory is armed
mkdir -p /data/system/uncleted
chmod 0755 /data/system/uncleted
chown 1000:1000 /data/system/uncleted
chcon u:object_r:system_data_file:s0 /data/system/uncleted 2>/dev/null || true
'''

UPDATE_BINARY_CONTENT = r'''#!/sbin/sh
##########################################################################################
# Universal Magisk / KernelSU / APatch update-binary
##########################################################################################
OUTFD=$2
ZIPFILE=$3

ui_print() {
  echo -e "ui_print $1\nui_print" >> /proc/self/fd/$OUTFD
}

# Source Magisk / KernelSU internal scripts if present
if [ -f /data/adb/magisk/util_functions.sh ]; then
  . /data/adb/magisk/util_functions.sh
elif [ -f /data/adb/ksu/bin/util_functions.sh ]; then
  . /data/adb/ksu/bin/util_functions.sh
elif [ -f /data/adb/ap/bin/util_functions.sh ]; then
  . /data/adb/ap/bin/util_functions.sh
fi

MODPATH="/data/adb/modules/uncleted_privapp"
mkdir -p "$MODPATH"
unzip -o "$ZIPFILE" -d "$MODPATH" >/dev/null 2>&1

if [ -f "$MODPATH/customize.sh" ]; then
  . "$MODPATH/customize.sh"
fi

exit 0
'''

UPDATER_SCRIPT_CONTENT = "#MAGISK\n"

MODULE_PROP_CONTENT = f"""id={MODULE_ID}
name={MODULE_NAME}
version={MODULE_VERSION}
versionCode={MODULE_VERSION_CODE}
author={MODULE_AUTHOR}
description={MODULE_DESCRIPTION}
"""


def find_apk():
    for path in POSSIBLE_APK_PATHS:
        if os.path.isfile(path):
            return path
    return None


def write_zip_entry(zip_file, archive_path, content, is_executable=False):
    info = zipfile.ZipInfo(archive_path)
    info.compress_type = zipfile.ZIP_DEFLATED
    # Standard POSIX permissions: 0755 for executables, 0644 for files
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
    print(" Uncle Ted Universal Flashable Module Packager   ")
    print("==================================================")

    apk_path = find_apk()
    if not apk_path:
        print("\n[ERROR] No compiled APK found. Build the project first:")
        print("        ./gradlew assembleRelease  (or assembleDebug)")
        sys.exit(1)

    print(f"[+] Found APK: {apk_path}")

    if not os.path.isfile(PRIVAPP_PERMS_SOURCE):
        print(f"\n[ERROR] Missing permission file: {PRIVAPP_PERMS_SOURCE}")
        sys.exit(1)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    if os.path.exists(ZIP_OUTPUT_PATH):
        os.remove(ZIP_OUTPUT_PATH)

    print(f"[+] Generating flashable package: {ZIP_OUTPUT_PATH}")

    with zipfile.ZipFile(ZIP_OUTPUT_PATH, "w", compression=zipfile.ZIP_DEFLATED) as z:
        # Module metadata
        write_zip_entry(z, "module.prop", MODULE_PROP_CONTENT, is_executable=False)

        # Installer scripts
        write_zip_entry(z, "META-INF/com/google/android/update-binary", UPDATE_BINARY_CONTENT, is_executable=True)
        write_zip_entry(z, "META-INF/com/google/android/updater-script", UPDATER_SCRIPT_CONTENT, is_executable=False)
        write_zip_entry(z, "customize.sh", CUSTOMIZE_SH_CONTENT, is_executable=True)
        write_zip_entry(z, "service.sh", SERVICE_SH_CONTENT, is_executable=True)

        # System payload
        write_zip_file_entry(
            z, "system/priv-app/UncleTed/UncleTed.apk", apk_path, is_executable=False
        )
        write_zip_file_entry(
            z,
            "system/etc/permissions/privapp-permissions-uncleted.xml",
            PRIVAPP_PERMS_SOURCE,
            is_executable=False,
        )

    print("\n[SUCCESS] Module packaged successfully!")
    print(f"Artifact location: {ZIP_OUTPUT_PATH}")
    print("\nFlash via Magisk, KernelSU-Next, or APatch without requiring manual workarounds.")


if __name__ == "__main__":
    main()