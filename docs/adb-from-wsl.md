# ADB from WSL for Multi-Device Debugging

## Overview

When debugging multi-device sync scenarios, you can use ADB from WSL to access Android devices connected to Windows. This allows you to monitor logs, install APKs, and interact with devices without switching contexts.

## Prerequisites

1. **Windows**: Android SDK installed with platform-tools (includes adb.exe)
   - Default location: `C:\Users\<username>\AppData\Local\Android\sdk\platform-tools\`
   
2. **WSL**: Access to Windows filesystem via `/mnt/c/`

3. **Devices**: Android devices with USB debugging enabled

## Setup

### 1. Connect Devices via USB

Connect both Android devices to your Windows machine via USB. Ensure USB debugging is enabled on each device:
- Settings → Developer options → USB debugging (enable)
- Authorize the computer when prompted on the device

### 2. Verify Windows ADB Can See Devices

Open PowerShell or Command Prompt on Windows:

```powershell
cd C:\Users\<username>\AppData\Local\Android\sdk\platform-tools
.\adb.exe devices
```

You should see both devices listed:
```
List of devices attached
<device1-serial>    device
<device2-serial>    device
```

### 3. Access ADB from WSL

From WSL, use the Windows adb.exe directly:

```bash
/mnt/c/Users/<username>/AppData/Local/Android/sdk/platform-tools/adb.exe devices
```

You should see the same device list.

### 4. Identify Devices

To identify which serial belongs to which device:

```bash
# Get device model
adb.exe -s <serial> shell getprop ro.product.model

# Get device brand
adb.exe -s <serial> shell getprop ro.product.brand
```

Example:
```bash
adb.exe -s abc123 shell getprop ro.product.model
# Output: Pixel 7

adb.exe -s def456 shell getprop ro.product.model
# Output: SM-G991U
```

## Common Commands

### Install APK on Specific Device

```bash
# Install on device with serial abc123
adb.exe -s abc123 install -r apps/todo/android/build/outputs/apk/debug/todo-debug.apk

# Uninstall first if signature mismatch
adb.exe -s abc123 uninstall com.msuite.todo
adb.exe -s abc123 install apps/todo/android/build/outputs/apk/debug/todo-debug.apk
```

### Monitor Logs from Specific Device

```bash
# Clear logs
adb.exe -s abc123 logcat -c

# Monitor specific tags
adb.exe -s abc123 logcat -s SafFileStore:* FolderPlug:* LiveSync:*

# Monitor with grep
adb.exe -s abc123 logcat | grep -E "(write|rename|duplicate)"
```

### Monitor Logs from Multiple Devices Simultaneously

Open multiple terminal panes or use background processes:

```bash
# Terminal 1: Monitor Device 1
adb.exe -s abc123 logcat -s SafFileStore:* LiveSync:*

# Terminal 2: Monitor Device 2
adb.exe -s def456 logcat -s SafFileStore:* LiveSync:*
```

Or run both in background and save to files:

```bash
adb.exe -s abc123 logcat > device1.log &
adb.exe -s def456 logcat > device2.log &
```

### Clear App Data

```bash
adb.exe -s abc123 shell pm clear com.msuite.todo
```

### Check App Version

```bash
adb.exe -s abc123 shell dumpsys package com.msuite.todo | grep versionName
```

## Wireless Debugging (Alternative)

If USB debugging is unreliable, you can use wireless ADB:

### 1. Enable Wireless Debugging on Device

On the Android device:
- Settings → Developer options → Wireless debugging (enable)
- Tap "Pair device with pairing code"
- Note the IP address, port, and pairing code

### 2. Pair from WSL

```bash
# Connect to Windows ADB server first
/mnt/c/Users/<username>/AppData/Local/Android/sdk/platform-tools/adb.exe connect <ip>:<port>

# Pair with code
/mnt/c/Users/<username>/AppData/Local/Android/sdk/platform-tools/adb.exe pair <ip>:<pairing-port> <code>
```

### 3. Connect

```bash
adb.exe connect <ip>:<port>
```

The device will now appear in `adb devices` output.

## Troubleshooting

### ADB Server Conflicts

If WSL and Windows ADB servers conflict:

```bash
# Kill Windows ADB server
/mnt/c/Users/<username>/AppData/Local/Android/sdk/platform-tools/adb.exe kill-server

# Start it again
/mnt/c/Users/<username>/AppData/Local/Android/sdk/platform-tools/adb.exe start-server

# Verify devices
adb.exe devices
```

### Device Not Authorized

If device shows as "unauthorized":
1. Check the device screen for authorization prompt
2. Tap "Allow" and optionally "Always allow from this computer"
3. Run `adb.exe devices` again

### Signature Mismatch on Install

If you get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`:

```bash
# Uninstall old version
adb.exe -s <serial> uninstall com.msuite.todo

# Install new version
adb.exe -s <serial> install apps/todo/android/build/outputs/apk/debug/todo-debug.apk
```

## Example: Testing Multi-Device Sync

Here's a complete workflow for testing sync between two devices:

```bash
# Device serials (from earlier identification)
DEVICE1="abc123"  # OnePlus Nord
DEVICE2="def456"  # Motorola Edge 70 pro

# Clear logs on both devices
adb.exe -s $DEVICE1 logcat -c
adb.exe -s $DEVICE2 logcat -c

# Install latest APK on both devices
adb.exe -s $DEVICE1 install -r apps/todo/android/build/outputs/apk/debug/todo-debug.apk
adb.exe -s $DEVICE2 install -r apps/todo/android/build/outputs/apk/debug/todo-debug.apk

# Start monitoring logs in separate terminals
# Terminal 1:
adb.exe -s $DEVICE1 logcat -s SafFileStore:* LiveSync:* FolderPlug:*

# Terminal 2:
adb.exe -s $DEVICE2 logcat -s SafFileStore:* LiveSync:* FolderPlug:*

# Now manually:
# 1. Create a todo on Device 1
# 2. Wait ~15 seconds
# 3. Check Device 2 logs for "ingesting key" messages
# 4. Verify todo appears on Device 2
```

## References

- [Android Debug Bridge (ADB)](https://developer.android.com/tools/adb)
- [WSL File System Access](https://docs.microsoft.com/en-us/windows/wsl/filesystems)
