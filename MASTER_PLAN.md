# BLEPulse Master Plan

## Architectural Overview

BLEPulse is a native Android Bluetooth Low Energy (BLE 5.x) diagnostics suite, GATT explorer, waveform oscilloscope, and IoT peripheral security fuzzer. It is built in Kotlin with Jetpack Compose and Material 3, adhering to clean architecture principles and a unidirectional data flow (UDF).

```
                      +---------------------------------------+
                      |         Jetpack Compose UI            |
                      |  (Scanner, Explorer, Scope, Simulator)|
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

## System Components

### 1. Core BLE Abstraction & Queue Engine
- **Hardware Abstraction**: `BleHardwareProvider` interface decouples the UI and business logic from the physical Android Bluetooth stack.
- **Sequential GATT Queue**: Android BLE operations must be executed serially. `GattOperationQueue` enqueues read, write, descriptor, MTU, and PHY requests with timeout handling and error recovery.
- **Virtual BLE Engine**: Provides realistic simulated devices (Nordic UART sensor, Heart Rate monitor, Weather station, Malformed IoT peripheral) enabling complete offline testing, unit test execution, and CI verification.

### 2. Advertising Scanner & Protocol Decoders
- **BLE 5.x Extended Advertising**: Captures primary PHY, secondary PHY, advertising SID, Tx power, and periodic interval.
- **Protocol Decoders**:
  - Apple iBeacon: Proximity UUID, Major, Minor, Measured Power @ 1m, and estimated distance using the log-distance path loss model.
  - Google Eddystone: UID (10-byte Namespace, 6-byte Instance), URL (RFC-compliant compressed schemes and TLDs), TLM (battery voltage, beacon temperature, packet count, uptime).
  - Manufacturer Data: Embedded registry with 100+ Bluetooth SIG Company Identifiers (Apple, Nordic Semiconductor, Espressif, Microsoft, Google, Texas Instruments, STMicroelectronics).
- **Signal Tracking**: Exponential moving average (EMA) smoothing for RSSI, historical sparklines, and signal strength categorization.
- **Exporting**: Export scan dumps as structured JSON or Wireshark-compatible packet hex logs.

### 3. Interactive GATT Explorer
- **Service & Characteristic Resolution**: Built-in Bluetooth SIG Assigned Numbers database for standard services (0x180D Heart Rate, 0x180F Battery, 0x181A Environmental, 0x180A Device Info, etc.) and vendor-specific services (Nordic UART NUS, ESP32 SPP, TI SensorTag).
- **Multi-Format Type Parsing**:
  - Hexadecimal, ASCII, UTF-8 strings, Binary bitfield.
  - Signed and unsigned 8-bit, 16-bit, and 32-bit integers in Little-Endian and Big-Endian formats.
  - IEEE-754 32-bit float, IEEE 11073-20601 SFloat (16-bit medical float) and Float (32-bit medical float).
- **GATT Operations**:
  - Characteristic Read, Write Request (with response), Write Command (without response).
  - Descriptor Read and Write (CCCD notification/indication subscription).
  - Connection parameter updates: MTU negotiation (23 to 517 bytes), PHY request (1M, 2M, Coded LE).
- **Transaction History**: Real-time event log recording operation type, timestamp, payload byte size, hex dump, and round-trip latency in milliseconds.

### 4. Real-Time Waveform Oscilloscope
- **Hardware-Accelerated Rendering**: Compose Canvas rendering with 60 frames per second throughput.
- **Multi-Channel Streams**: Plots up to 4 concurrent telemetry channels (such as 3-axis accelerometer X/Y/Z or temperature and humidity).
- **Scope Controls**: Configurable rolling time window (5s, 10s, 30s, 60s), auto-scaling or locked Y-axis, threshold lines, and cursor inspection.
- **Telemetry Export**: Download captured time-series measurements as CSV files.

### 5. Virtual Peripheral Simulator
- **BLE Advertising Engine**: Broadcasts custom advertisement packets with configurable local name, service UUIDs, and manufacturer payload.
- **GATT Server Implementation**:
  - Standard Heart Rate profile with synthetic cardiac rhythm generator (HRV simulation).
  - Battery service with progressive drain simulation.
  - Nordic UART Service loopback echo.
  - Custom profile creator supporting dynamic addition of services and characteristics with configurable permissions.
- **Connection Monitor**: Displays connected central peer addresses, MTU state, and real-time read/write request logs.

### 6. IoT Peripheral Security Fuzzer
- **Boundary Testing**: Payload sizes at zero bytes, single byte, MTU boundaries (23, 247, 512, 517 bytes), and oversized fragmented buffers (600+ bytes).
- **Mutation & Injection Engine**: Pseudorandom byte corruption, bit flipping, format string sequences, null-byte injections, and malformed UTF-8 byte sequences.
- **Stress & Flooding**: High-speed burst writes (with-response vs without-response) to assess peripheral buffer overrun, RTOS task starvation, and stack depth limits.
- **State Machine Stress**: Rapid connect, MTU exchange, and disconnect cycling to discover peripheral memory leaks.
- **Crash & Anomaly Detector**: Monitors GATT status codes, link terminations, and timeout behaviors with exportable audit logs.

---

## Detailed Task Breakdown

- [x] Phase 0: Project Inception & Portfolio Registry (mtlg-site / mtlglabs-space)
- [ ] Task 1: Repository Foundation, Gradle Build Configuration & CI Setup
  - Subtask 1.1: Root build.gradle.kts, settings.gradle.kts, app/build.gradle.kts with Compose & Room
  - Subtask 1.2: AndroidManifest.xml with modern BLE permissions (API 31-35)
  - Subtask 1.3: GitHub Actions CI workflow for test compilation and linting
- [ ] Task 2: Core Domain Models & Bluetooth SIG Database
  - Subtask 2.1: BLE device, advertisement record, service, characteristic, descriptor models
  - Subtask 2.2: Bluetooth SIG Assigned Numbers registry (Services, Characteristics, Company IDs)
  - Subtask 2.3: Data type converters and bitfield parsers (Hex, UTF-8, IEEE-754, IEEE-11073, Int, UInt)
- [ ] Task 3: Advertisement Decoders (iBeacon, Eddystone, Manufacturer Data)
  - Subtask 3.1: Raw advertisement byte stream parser
  - Subtask 3.2: Apple iBeacon decoder with distance estimation
  - Subtask 3.3: Google Eddystone decoder (UID, URL, TLM)
  - Subtask 3.4: RSSI tracker with EMA smoothing and historical sparkline
- [ ] Task 4: BLE Transport Abstraction & Sequential GATT Queue
  - Subtask 4.1: BleHardwareProvider and BleConnection interfaces
  - Subtask 4.2: Sequential GattOperationQueue with timeout and error mapping
  - Subtask 4.3: Real Android implementation (BluetoothLeScanner, BluetoothGatt)
  - Subtask 4.4: High-fidelity Virtual BLE Engine for determinism and simulator testing
- [ ] Task 5: Interactive GATT Explorer & Service Browser
  - Subtask 5.1: Connection management, MTU exchange, PHY switching
  - Subtask 5.2: Service and characteristic tree navigation
  - Subtask 5.3: Characteristic read/write modal with auto-type selector
  - Subtask 5.4: Notification/indication listener with live transaction logging
- [ ] Task 6: Live Waveform Oscilloscope
  - Subtask 6.1: High-performance Canvas oscilloscope engine
  - Subtask 6.2: Multi-channel data routing from Notify streams
  - Subtask 6.3: Timebase, scale, pause, cursor readout, and CSV exporter
- [ ] Task 7: Virtual Peripheral Simulator
  - Subtask 7.1: BLE GATT Server and Advertiser manager
  - Subtask 7.2: Built-in Heart Rate, Battery, and Nordic UART loopback profiles
  - Subtask 7.3: Custom profile editor with dynamic characteristic definition
  - Subtask 7.4: Connected peer monitor and request inspector
- [ ] Task 8: IoT Peripheral Security Fuzzer
  - Subtask 8.1: Fuzzer payload generators (Boundary, Mutation, Injection, Oversize)
  - Subtask 8.2: Stress test runners (High-speed write bursts, Connection cycle stress)
  - Subtask 8.3: Anomaly detection, GATT error tracking, and audit report generator
- [ ] Task 9: User Interface Assembly with Jetpack Compose & Material 3
  - Subtask 9.1: Dark theme design system, typography, and status indicators
  - Subtask 9.2: Navigation bar and tabbed workflow (Scanner, Explorer, Scope, Simulator, Fuzzer)
  - Subtask 9.3: Settings, filter dialogs, and hardware mode switcher (Real / Simulator)
- [ ] Task 10: Comprehensive Unit Test Suite
  - Subtask 10.1: Unit tests for advertising decoders (iBeacon, Eddystone UID/URL/TLM, Manufacturer)
  - Subtask 10.2: Unit tests for data encoders/decoders (IEEE 754, IEEE 11073, Integers, Strings)
  - Subtask 10.3: Unit tests for GATT queue, fuzzer payload generation, and virtual simulator
- [ ] Task 11: Visual Assets, Humanized Documentation & Final Portfolio Sync
  - Subtask 11.1: Python asset generation script for realistic UI screenshots and animated demo GIF
  - Subtask 11.2: Comprehensive humanized README.md with technical architecture diagrams
  - Subtask 11.3: GitHub repository setup (About description + 12 topics) and Phase 2 portfolio sync
