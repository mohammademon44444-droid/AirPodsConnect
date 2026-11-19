# AirPods Connect

A lightweight Android Bluetooth Low Energy battery checker prototype.

## Features
- Scan nearby BLE devices.
- Connect to a selected device.
- Read the standard Bluetooth Battery Service (0x180F / 0x2A19), when the earbuds expose it.
- Build a debug APK with GitHub Actions.

## Build
Run the **Build AirPods Connect APK** workflow from the Actions tab, or push a commit to main. Download the `AirPodsConnect-debug-apk` artifact from the completed workflow.

## Important compatibility note
Many AirPods models and clones do not expose battery information through the standard BLE Battery Service. Separate left/right/case battery and case charging status require device-specific protocol support and cannot be guaranteed by this generic prototype.
