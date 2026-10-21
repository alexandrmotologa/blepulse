<p align="center">
  <img src="docs/images/blepulse_logo.png" alt="BLEPulse Logo" width="128" height="128" />
</p>

<h1 align="center">BLEPulse</h1>

<p align="center">
  <strong>Native Android Bluetooth Low Energy 5.x Scanner, GATT Service Explorer, Real-Time Waveform Oscilloscope, and IoT Peripheral Fuzzer</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26--35)-blue.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.0-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-green.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/Bluetooth-BLE_5.x_Core-cyan.svg" alt="Bluetooth" />
  <img src="https://img.shields.io/badge/License-MIT-gray.svg" alt="License" />
</p>

<p align="center">
  <img src="docs/images/blepulse_demo.gif" alt="BLEPulse Animated Demo" width="760" />
</p>

---

## Overview

Engineers and embedded firmware developers working with microcontrollers (ESP32, nRF52, STM32WB, RP2040) and Bluetooth Low Energy sensors frequently rely on diagnostics tools to inspect advertising broadcasts, inspect GATT tables, and stream telemetry. Existing tools often carry ads, remain closed-source, or lack automated security testing features.

BLEPulse provides an open-source, native Android application built with Kotlin, Jetpack Compose, and Android Bluetooth LE Core. It combines real-time broadcast packet parsing, interactive GATT read/write operations with type decoding, an embedded 60fps waveform oscilloscope, a virtual GATT server simulator, and an IoT peripheral fuzzer into a single utility.

---

## Core Capabilities

### 1. Real-Time Advertising Scanner
- Decodes BLE 5.x primary and secondary physical layers (LE 1M, LE 2M, LE Coded).
- Protocol decoders:
  - **Apple iBeacon**: Proximity UUID, Major, Minor, Measured Power at 1 meter, and distance calculation using the log-distance path loss model.
  - **Google Eddystone**: UID (Namespace and Instance), URL (compressed prefix and top-level domain expansion), and TLM telemetry (battery millivolts, beacon temperature, PDU packet counter, uptime).
  - **Manufacturer Data**: Resolves 100+ Bluetooth SIG Company Identifiers (Apple, Nordic Semiconductor, Espressif, Microsoft, Google, Texas Instruments, STMicroelectronics).
- Signal tracking: Exponential Moving Average (EMA) smoothing for RSSI, historical sparklines, and proximity categorization.
- Scan filters: Name matching, minimum RSSI threshold slider, and connectable-only toggling.

### 2. Interactive GATT Explorer
- Built-in Bluetooth SIG Assigned Numbers database for standard services (Heart Rate, Battery, Environmental Sensing, Health Thermometer) and vendor services (Nordic UART Service NUS, TI SensorTag, ESP32 SPP).
- Sequential GATT command queue preventing dropped packets and Android status 133 connection errors.
- Multi-format data parser and editor: Hex, ASCII, UTF-8 strings, Binary bitfields, UInt8, UInt16 (LE/BE), UInt32 (LE/BE), Int8, Int16 (LE/BE), Int32 (LE/BE), Float32 (LE/BE), and IEEE 11073-20601 SFloat medical floats.
- Connection parameter negotiation: ATT MTU exchange (up to 517 bytes) and 2M PHY switching.
- Notification and indication subscription management via CCCD (0x2902) descriptors.
- Real-time GATT transaction log recording operation latency in milliseconds and byte sizes.

### 3. Live Waveform Oscilloscope
- Hardware-accelerated Canvas rendering engine operating at 60 frames per second.
- Multi-channel plotting (up to 4 simultaneous channels for 3-axis accelerometer X/Y/Z, temperature, or cardiac signals).
- Scope parameters: Configurable rolling time windows (5s, 10s, 30s), center voltage baselines, auto-ranging Y-axis, and grid lines.
- Channel metrics bar displaying real-time values, minima, maxima, and rolling averages.
- Telemetry export: Direct export of captured time-series measurements as CSV files.

### 4. Virtual Peripheral Simulator
- Emulates a physical BLE 5.x peripheral server broadcasting custom advertisement packets.
- Pre-configured profiles:
  - Heart Rate Monitor (0x180D) with synthetic cardiac rhythm and HRV pulse simulation.
  - Battery Service (0x180F) with progressive discharge simulation.
  - Nordic UART Service (NUS) loopback echo responding to incoming writes.
  - Environmental Sensing (0x181A) with temperature and humidity oscillation.
- Connected central client monitor displaying peer MAC addresses, negotiated MTU, and incoming read/write request logs.

### 5. IoT Peripheral Security Fuzzer
- Automated boundary value testing: Zero-length buffers, single bytes, MTU edge cases (20, 23, 247, 512, 517 bytes), and fragmented oversized frames (600+ bytes).
- Mutation engine: Pseudorandom byte corruption, bit flipping, all-zero, and 0xFF byte saturation.
- Injection payloads: Format string specifiers (`%s%n%x`), null-byte delimiters, and illegal UTF-8 continuation bytes.
- Stress testing: High-speed consecutive write bursts to test microcontroller queue depth and RTOS task starvation.
- Anomaly detector: Flags unexpected GATT status errors and link terminations with exportable Markdown audit reports.

---

## Screenshots

<p align="center">
  <img src="docs/images/blepulse_scanner.png" width="48%" alt="Advertising Scanner" />
  <img src="docs/images/blepulse_explorer.png" width="48%" alt="GATT Explorer" />
</p>

<p align="center">
  <img src="docs/images/blepulse_oscilloscope.png" width="48%" alt="Live Waveform Oscilloscope" />
  <img src="docs/images/blepulse_fuzzer.png" width="48%" alt="IoT Peripheral Fuzzer" />
</p>

<p align="center">
  <img src="docs/images/blepulse_simulator.png" width="55%" alt="Virtual Peripheral Simulator" />
</p>

---

## Technical Architecture

```
                       +---------------------------------------+
                       |          Jetpack Compose UI           |
                       | (Scanner, Explorer, Scope, Simulator) |
                       +---------------------------------------+
                                           |
                       +---------------------------------------+
                       |           BleRepository               |
                       |       (Hardware Mode Switcher)        |
                       +---------------------------------------+
                                      /         \
                                     /           \
     +----------------------------------+     +----------------------------------+
     |    AndroidBleHardwareProvider    |     |    VirtualBleHardwareProvider    |
     |   - BluetoothLeScanner           |     |   - ESP32-S3 Waveform Node       |
     |   - GattOperationQueue           |     |   - Polar H10 Heart Rate         |
     |   - Android 12+ Permission Guard |     |   - RuuviTag Weather Beacon      |
     |   - MTU 517 / PHY 2M Manager     |     |   - Vulnerable Fuzz Target       |
     +----------------------------------+     +----------------------------------+
                       |                                       |
                       +-------------------+-------------------+
                                           |
                       +---------------------------------------+
                       |            Protocol Decoders          |
                       |  - Apple iBeacon (Path Loss Distance) |
                       |  - Google Eddystone (UID, URL, TLM)   |
                       |  - Bluetooth SIG Registry             |
                       |  - IEEE 754 & IEEE 11073-20601 SFloat |
                       +---------------------------------------+
```

---

## Project Structure

```
blepulse/
├── app/
│   ├── build.gradle.kts                # Android build script (Compose, Coroutines, Serialization)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml     # BLE 5.x permissions (API 26-35)
│   │   │   └── java/com/blepulse/android/
│   │   │       ├── MainActivity.kt     # Main activity with 5-tab navigation bar
│   │   │       ├── core/
│   │   │       │   ├── ble/            # BLE interfaces, queue engine, Android & Virtual providers
│   │   │       │   ├── decoder/        # iBeacon, Eddystone, IEEE-11073, and data format parsers
│   │   │       │   ├── fuzzer/         # IoT peripheral fuzzer engine and test case generator
│   │   │       │   ├── model/          # Device, service, characteristic, and logging models
│   │   │       │   ├── sig/            # Bluetooth SIG Assigned Numbers database
│   │   │       │   ├── simulator/      # Virtual GATT server and dynamic profile engine
│   │   │       │   └── waveform/       # Real-time oscilloscope telemetry engine
│   │   │       └── ui/
│   │   │           ├── common/         # Signal bars, sparklines, hex viewer, status badges
│   │   │           ├── explorer/       # GATT service explorer, characteristic write modal
│   │   │           ├── fuzzer/         # Fuzzing control dashboard and audit log table
│   │   │           ├── scanner/        # BLE scanner list, search input, filter dialog
│   │   │           ├── simulator/      # Virtual peripheral server manager
│   │   │           ├── theme/          # OLED slate dark theme, typography, color palette
│   │   │           └── waveform/       # Hardware-accelerated oscilloscope canvas
│   │   └── test/java/com/blepulse/android/
│   │       ├── decoder/                # Unit tests for advertisement and data type decoders
│   │       ├── fuzzer/                 # Unit tests for fuzzing engine and crash detection
│   │       └── waveform/               # Unit tests for oscilloscope channels and CSV export
├── docs/images/                        # High-resolution screenshots, logo, and animated demo GIF
├── build.gradle.kts                    # Top-level Gradle build configuration
├── generate_assets.py                  # Procedural visual mockup and GIF generator
├── MASTER_PLAN.md                      # Comprehensive engineering specifications
├── LICENSE                             # MIT License
└── README.md                           # Documentation
```

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2+) or IntelliJ IDEA with Android plugin.
- Android SDK with Platform 35 (`compileSdk = 35`, `minSdk = 26`).
- JDK 17 or JDK 21.

### Build and Run Unit Tests
```bash
# Run the complete test suite
./gradlew testDebugUnitTest

# Build the debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installation via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Offline & Simulator Mode

BLEPulse includes a toggleable hardware abstraction layer. If physical Bluetooth is unavailable or if you are running on an Android emulator without radio hardware, toggle the mode pill in the Scanner toolbar to **VIRTUAL**. 

The virtual engine exposes 5 synthesized devices:
1. `ESP32-S3 Waveform Node` (Nordic UART Service streaming 3-channel accelerometer telemetry).
2. `Polar H10 3BA244` (Heart Rate Monitor broadcasting dynamic cardiac pulses).
3. `iBeacon Asset Tag` (Apple proximity broadcast tag with calibrated TxPower).
4. `RuuviTag CA58` (Environmental weather station).
5. `IoT Fuzz Target` (Microcontroller with a 64-byte buffer threshold).

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
