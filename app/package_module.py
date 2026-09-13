#!/usr/bin/env python3
import os
import sys
import shutil
import zipfile
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent

if (CURRENT_DIR / "src").exists() or (CURRENT_DIR / "build").exists():
    APP_DIR = CURRENT_DIR
    PROJECT_ROOT = CURRENT_DIR.parent
else:
    APP_DIR = CURRENT_DIR / "app"
    PROJECT_ROOT = CURRENT_DIR

APK_CANDIDATES = [
    APP_DIR / "build" / "outputs" / "apk" / "release" / "app-release.apk",
    APP_DIR / "build" / "outputs" / "apk" / "release" / "UncleTed.apk",
    APP_DIR / "build" / "outputs" / "apk" / "debug" / "app-debug.apk",
    APP_DIR / "build" / "outputs" / "apk" / "debug" / "UncleTed.apk",
    APP_DIR / "UncleTed.apk",
    PROJECT_ROOT / "UncleTed.apk",
]

PERMS_CANDIDATES = [
    APP_DIR / "distribution" / "etc" / "permissions" / "privapp-permissions-uncleted.xml",
    PROJECT_ROOT / "distribution" / "etc" / "permissions" / "privapp-permissions-uncleted.xml",
    APP_DIR / "src" / "main" / "distribution" / "etc" / "permissions" / "privapp-permissions-uncleted.xml"
]

OUTPUT_DIR = APP_DIR / "build_output"
STAGING_DIR = OUTPUT_DIR / "UncleTed-Module"
OUTPUT_ZIP = OUTPUT_DIR / "UncleTed-PrivApp-v2.0.zip"

MODULE_PROP = """id=uncleted_privapp
name=UncleTed System Priv-App & Hook
version=v2.0-SYSTEM
versionCode=2
author=UncleTed Security Project
description=Systemless integration of UncleTed into /system/priv-app with platform permissions (MASTER_CLEAR) and native lockscreen hook support.
"""

CUSTOMIZE_SH = """SKIPUNZIP=0
ui_print "***********************************************"
ui_print "   UncleTed Priv-App & Hook Installer          "
ui_print "***********************************************"

if [ ! -f "$MODPATH/system/priv-app/UncleTed/UncleTed.apk" ]; then
    abort "! Error: UncleTed.apk missing from package."
fi

if [ ! -f "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" ]; then
    abort "! Error: privapp-permissions-uncleted.xml missing from package."
fi

ui_print "- Applying permissions..."
set_perm_recursive "$MODPATH/system/priv-app/UncleTed" 0 0 0755 0644
set_perm "$MODPATH/system/priv-app/UncleTed/UncleTed.apk" 0 0 0644
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-uncleted.xml" 0 0 0644
ui_print "- Configured successfully."
"""

UPDATER_SCRIPT = """#MAGISK
"""

# Universal Dual-Mode Installer (Supports Magisk Manager, KernelSU, and standalone TWRP SAR)
UPDATE_BINARY = """#!/sbin/sh
OUTFD=$2
ZIPFILE=$3

ui_print() {
  echo -e "ui_print $1\\nui_print" > /proc/self/fd/$OUTFD
}

ui_print "***********************************************"
ui_print "   UncleTed Priv-App Installer (HTC 10 / A9)   "
ui_print "***********************************************"

# Mount /data to check for Magisk
mount /data 2>/dev/null
mount /dev/block/bootdevice/by-name/userdata /data 2>/dev/null

# 1. If running under Magisk/KernelSU environment:
if [ -f /data/adb/magisk/util_functions.sh ]; then
    ui_print "- Magisk environment detected. Installing module..."
    . /data/adb/magisk/util_functions.sh
    install_module
    exit 0
elif [ -f /data/adb/ksu/bin/busybox ]; then
    ui_print "- KernelSU environment detected. Installing module..."
    exec /data/adb/ksu/bin/busybox sh -c ". /data/adb/ksu/scripts/util_functions.sh; install_module"
    exit 0
fi

# 2. Standalone Recovery Mode (Direct System / SAR Flash)
ui_print "- Installing directly to System partition..."

# Mount System (Handle both SAR /system_root and classic /system)
mount -o rw /system 2>/dev/null
mount -o rw /system_root 2>/dev/null
mount -o rw /dev/block/bootdevice/by-name/system /system_root 2>/dev/null
mount -o rw /dev/block/bootdevice/by-name/system /system 2>/dev/null

SYS_TARGET=""
if [ -d "/system_root/system" ]; then
    SYS_TARGET="/system_root/system"
elif [ -d "/system/system" ]; then
    SYS_TARGET="/system/system"
elif [ -d "/system" ]; then
    SYS_TARGET="/system"
else
    ui_print "! ERROR: Could not mount system partition."
    exit 1
fi

ui_print "- Target system directory: $SYS_TARGET"

# Create directories
mkdir -p "$SYS_TARGET/priv-app/UncleTed"
mkdir -p "$SYS_TARGET/etc/permissions"

# Extract files directly from ZIP using unzip
ui_print "- Extracting UncleTed.apk..."
unzip -o "$ZIPFILE" "system/priv-app/UncleTed/UncleTed.apk" -d /tmp/
cp -f /tmp/system/priv-app/UncleTed/UncleTed.apk "$SYS_TARGET/priv-app/UncleTed/UncleTed.apk"

ui_print "- Extracting privapp-permissions-uncleted.xml..."
unzip -o "$ZIPFILE" "system/etc/permissions/privapp-permissions-uncleted.xml" -d /tmp/
cp -f /tmp/system/etc/permissions/privapp-permissions-uncleted.xml "$SYS_TARGET/etc/permissions/privapp-permissions-uncleted.xml"

# Set file permissions & ownership
chmod 755 "$SYS_TARGET/priv-app/UncleTed"
chmod 644 "$SYS_TARGET/priv-app/UncleTed/UncleTed.apk"
chmod 644 "$SYS_TARGET/etc/permissions/privapp-permissions-uncleted.xml"

chown 0:0 "$SYS_TARGET/priv-app/UncleTed"
chown 0:0 "$SYS_TARGET/priv-app/UncleTed/UncleTed.apk"
chown 0:0 "$SYS_TARGET/etc/permissions/privapp-permissions-uncleted.xml"

# Clean temporary files
rm -rf /tmp/system

ui_print "- Permissions set: root:root (0:0) and 0644."
ui_print "- Installation complete! Rebooting..."
exit 0
"""

def find_apk() -> Path:
    for candidate in APK_CANDIDATES:
        if candidate.is_file():
            return candidate
    print("[!] ERROR: Could not locate built APK.")
    sys.exit(1)

def find_permissions_xml() -> Path:
    for candidate in PERMS_CANDIDATES:
        if candidate.is_file():
            return candidate
    print("[!] ERROR: Could not locate 'privapp-permissions-uncleted.xml'.")
    sys.exit(1)

def write_unix_file(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="\n", encoding="utf-8") as f:
        f.write(content)

def main():
    print("=== UncleTed Systemless Module Packager (Universal TWRP Edition) ===")
    apk_path = find_apk()
    perms_path = find_permissions_xml()

    print(f"[+] Located APK: {apk_path}")
    print(f"[+] Located Permissions XML: {perms_path}")

    if STAGING_DIR.exists():
        shutil.rmtree(STAGING_DIR)
    STAGING_DIR.mkdir(parents=True, exist_ok=True)

    write_unix_file(STAGING_DIR / "module.prop", MODULE_PROP)
    write_unix_file(STAGING_DIR / "customize.sh", CUSTOMIZE_SH)
    write_unix_file(STAGING_DIR / "META-INF" / "com" / "google" / "android" / "updater-script", UPDATER_SCRIPT)
    write_unix_file(STAGING_DIR / "META-INF" / "com" / "google" / "android" / "update-binary", UPDATE_BINARY)

    target_apk_dir = STAGING_DIR / "system" / "priv-app" / "UncleTed"
    target_perms_dir = STAGING_DIR / "system" / "etc" / "permissions"

    target_apk_dir.mkdir(parents=True, exist_ok=True)
    target_perms_dir.mkdir(parents=True, exist_ok=True)

    shutil.copy2(apk_path, target_apk_dir / "UncleTed.apk")
    shutil.copy2(perms_path, target_perms_dir / "privapp-permissions-uncleted.xml")

    OUTPUT_ZIP.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT_ZIP.exists():
        OUTPUT_ZIP.unlink()

    with zipfile.ZipFile(OUTPUT_ZIP, "w", zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(STAGING_DIR):
            for file in files:
                file_path = Path(root) / file
                archive_name = file_path.relative_to(STAGING_DIR)
                zipf.write(file_path, arcname=archive_name)

    print(f"\n[✓] DONE! Universal ZIP generated at:\n    {OUTPUT_ZIP}\n")

if __name__ == "__main__":
    main()