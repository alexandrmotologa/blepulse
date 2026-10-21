# BLEPulse Master Plan

## Architectural Overview

BLEPulse is a native Android Bluetooth Low Energy (BLE 5.x) diagnostics suite, GATT explorer, waveform oscilloscope, and IoT peripheral security fuzzer. It is built in Kotlin with Jetpack Compose and Material 3, adhering to clean architecture principles and a unidirectional data flow (UDF).

```
                      +---------------------------------------+
                      |         Jetpack Compose UI            |
                      |  Scanner, Explorer, Scope, Tools, Sim |
                      +---------------------------------------+
                                          |
                      +---------------------------------------+
                      |             ViewControllers           |
                      |        (StateFlow / SharedFlow)       |
                      +---------------------------------------+
                                          |
                      +---------------------------------------+
                      |           Domain & Decoders           |
                      |   (iBeacon, Eddystone, SIG, Fuzzer)   |
                      +---------------------------------------+
                                          |
                      +---------------------------------------+
                      |     BleHardwareProvider Interface     |
                      +---------------------------------------+
                                     /         \
                                    /           \
    +---------------------------------+       +---------------------------------+
    |   AndroidBleHardwareProvider    |       |   VirtualBleHardwareProvider    |
    |  (android.bluetooth.* APIs)     |       | (Deterministic Simulator Engine)|
    +---------------------------------+       +---------------------------------+
```

---

## Detailed Task Breakdown

- [x] Phase 0: Project Inception & Portfolio Registry (mtlg-site / mtlglabs-space)
- [x] Task 1: Repository Foundation, Gradle Build Configuration & CI Setup
  - [x] Subtask 1.1: Root build.gradle.kts, settings.gradle.kts, app/build.gradle.kts with Compose & Kotlin 2.0
  - [x] Subtask 1.2: AndroidManifest.xml with modern BLE permissions (API 31-35)
  - [x] Subtask 1.3: GitHub Actions CI workflow for test compilation and linting
- [x] Task 2: Core Domain Models & Bluetooth SIG Database
  - [x] Subtask 2.1: BLE device, advertisement record, service, characteristic, descriptor models
  - [x] Subtask 2.2: Bluetooth SIG Assigned Numbers registry (Services, Characteristics, Company IDs)
  - [x] Subtask 2.3: Data type converters and bitfield parsers (Hex, UTF-8, IEEE-754, IEEE-11073, Int, UInt)
- [x] Task 3: Advertisement Decoders (iBeacon, Eddystone, Manufacturer Data)
  - [x] Subtask 3.1: Raw advertisement byte stream parser
  - [x] Subtask 3.2: Apple iBeacon decoder with distance estimation
  - [x] Subtask 3.3: Google Eddystone decoder (UID, URL, TLM)
  - [x] Subtask 3.4: Exponential Moving Average (EMA) RSSI smoother and sparkline buffer
- [x] Task 4: BLE Core Abstraction & Sequential Queue Engine
  - [x] Subtask 4.1: BleConnection & BleHardwareProvider contracts
  - [x] Subtask 4.2: Serial GattOperationQueue with deferred coroutine dispatch
  - [x] Subtask 4.3: Android physical hardware provider implementation
  - [x] Subtask 4.4: Virtual hardware provider with 5 realistic simulated peripheral nodes
- [x] Task 5: Interactive GATT Explorer & Type Inspection
  - [x] Subtask 5.1: Multi-service and characteristic hierarchy viewer
  - [x] Subtask 5.2: Type-aware write dialog (Hex, ASCII, Int, Float)
  - [x] Subtask 5.3: CCCD notification / indication toggle
  - [x] Subtask 5.4: MTU exchange and 2M PHY switching
  - [x] Subtask 5.5: Real-time operation log with millisecond latency metrics
- [x] Task 6: Hardware-Accelerated Waveform Oscilloscope & Real-Time FFT
  - [x] Subtask 6.1: High-performance 60fps Canvas oscilloscope renderer
  - [x] Subtask 6.2: Multi-channel telemetry plotting (accelerometer, ECG, temperature)
  - [x] Subtask 6.3: Radix-2 Cooley-Tukey Fast Fourier Transform (FFT) frequency spectrum analyzer
  - [x] Subtask 6.4: Selectable windowing (Hanning, Hamming, Blackman, Rectangular) & peak tracking
  - [x] Subtask 6.5: Direct CSV export of captured time-series measurements
- [x] Task 7: IoT Peripheral Boundary & Anomaly Fuzzer
  - [x] Subtask 7.1: Boundary value mutations (zero bytes, 1 byte, MTU edge cases, 600+ bytes)
  - [x] Subtask 7.2: Random bit-flip, byte corruption, and format string injection
  - [x] Subtask 7.3: High-speed burst writes for MCU buffer saturation
  - [x] Subtask 7.4: Anomaly detector capturing GATT disconnects, timeouts, and crash reports
- [x] Task 8: Virtual Peripheral Simulator
  - [x] Subtask 8.1: Emulated GATT server with heart rate, battery, NUS, and environmental profiles
  - [x] Subtask 8.2: Connected central client telemetry monitor
- [x] Task 9: Wireshark PCAP Capture Exporter
  - [x] Subtask 9.1: Libpcap 2.4 binary generator with LINKTYPE_BLUETOOTH_LE_LL (251)
  - [x] Subtask 9.2: BLE Link Layer pseudo-header, Access Address, and PDU framing
- [x] Task 10: Bidirectional BLE Serial UART Terminal
  - [x] Subtask 10.1: Nordic NUS, ESP32 SPP, and custom serial channel auto-detection
  - [x] Subtask 10.2: ASCII and Hex send/receive with CRLF, LF, CR, and None line endings
  - [x] Subtask 10.3: Command history buffer and macro presets
- [x] Task 11: Audio & Haptic Geiger Counter Proximity Tracker
  - [x] Subtask 11.1: RSSI-to-frequency mapping (0.6 Hz to 25 Hz)
  - [x] Subtask 11.2: Synthetic 1.5kHz PCM sine wave clicks via Android AudioTrack
  - [x] Subtask 11.3: Synchronized haptic pulses via Android Vibrator
  - [x] Subtask 11.4: Analog needle meter UI with proximity zones
- [x] Task 12: Chipset Fingerprinting & Automated Security Auditor
  - [x] Subtask 12.1: Heuristic identification of ESP32, nRF52/53, TI CC26xx, Apple Silicon, Ruuvi
  - [x] Subtask 12.2: Vulnerability checks for unencrypted serial and write-without-response risks
  - [x] Subtask 12.3: Automated 0-100% security score calculation with remediation recommendations
- [x] Task 13: GATT Macro Automation & Sequence Test Runner
  - [x] Subtask 13.1: Programmable sequence engine with assertions, delays, MTU, and writes
  - [x] Subtask 13.2: Built-in commissioning and echo test suites with pass/fail statistics
- [x] Task 14: Nordic Secure DFU & ESP32 BLE OTA Flasher
  - [x] Subtask 14.1: ESP32 BLE OTA and Nordic Secure DFU protocol state machines
  - [x] Subtask 14.2: Firmware image inspector (CRC32, SHA-256, magic byte detection)
  - [x] Subtask 14.3: Real-time throughput metrics (kbps), packet slicing, and progress UI
- [x] Task 15: Verification, Unit Testing & APK Assembly
  - [x] Subtask 15.1: 32 unit tests passing 100% across all modules
  - [x] Subtask 15.2: Production signed debug APK assembled (16.4 MB)
  - [x] Subtask 15.3: Visual screenshots & animated demo GIF generated
