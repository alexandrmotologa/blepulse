package com.blepulse.android.core.ble

import com.blepulse.android.core.decoder.BleAdvertisementParser
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.AdvertisementRecord
import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.BlePhy
import com.blepulse.android.core.model.ConnectionParameters
import com.blepulse.android.core.model.ConnectionState
import com.blepulse.android.core.model.GattCharacteristicInfo
import com.blepulse.android.core.model.GattDescriptorInfo
import com.blepulse.android.core.model.GattOpType
import com.blepulse.android.core.model.GattOperationLog
import com.blepulse.android.core.model.GattServiceInfo
import com.blepulse.android.core.model.GattStatus
import com.blepulse.android.core.model.IBeaconData
import com.blepulse.android.core.model.ProximityZone
import com.blepulse.android.core.model.RssiSample
import com.blepulse.android.core.sig.BluetoothSigRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin
import kotlin.random.Random

class VirtualBleHardwareProvider : BleHardwareProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var scanJob: Job? = null
    private val connections = ConcurrentHashMap<String, VirtualBleConnection>()

    private val virtualDevicesList: List<BleDevice> by lazy {
        buildVirtualDevices()
    }

    override fun startScan(filter: ScanFilterConfig) {
        if (_isScanning.value) return
        _isScanning.value = true

        scanJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val updated = virtualDevicesList.map { dev ->
                    // Add slight RSSI jitter
                    val jitter = Random.nextInt(-3, 4)
                    val newRssi = (dev.rssi + jitter).coerceIn(-95, -35)
                    val sample = RssiSample(now, newRssi)
                    val history = (dev.rssiHistory + sample).takeLast(30)
                    val smoothed = BleAdvertisementParser.applyEmaFilter(dev.smoothedRssi, newRssi)
                    val dist = if (dev.txPower != null) {
                        BleAdvertisementParser.calculateDistance(dev.txPower, newRssi)
                    } else dev.distanceMeters

                    dev.copy(
                        rssi = newRssi,
                        smoothedRssi = smoothed,
                        rssiHistory = history,
                        distanceMeters = dist,
                        lastSeenTimestamp = now
                    )
                }.filter { dev ->
                    if (filter.nameQuery.isNotBlank()) {
                        val matches = dev.name?.contains(filter.nameQuery, ignoreCase = true) == true ||
                                dev.address.contains(filter.nameQuery, ignoreCase = true)
                        if (!matches) return@filter false
                    }
                    if (dev.rssi < filter.minRssi) return@filter false
                    if (filter.manufacturerId != null && !dev.advertisementRecord.manufacturerData.containsKey(filter.manufacturerId)) {
                        return@filter false
                    }
                    true
                }

                _discoveredDevices.value = updated.sortedByDescending { it.smoothedRssi }
                delay(1200)
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override fun connect(address: String): BleConnection {
        return connections.getOrPut(address) {
            VirtualBleConnection(address)
        }
    }

    override fun startAdvertising(config: CustomAdvertiseConfig): Boolean {
        _isAdvertising.value = true
        return true
    }

    override fun stopAdvertising() {
        _isAdvertising.value = false
    }

    private fun buildVirtualDevices(): List<BleDevice> {
        val now = System.currentTimeMillis()

        // 1. ESP32-S3 IoT Node (Nordic UART Service + Battery)
        val esp32Uuids = listOf(
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            BluetoothSigRegistry.uuidFromShort16("180F")
        )
        val esp32Mfr = mapOf(0x02E5 to byteArrayOf(0x45, 0x53, 0x50, 0x33, 0x32, 0x01))
        val esp32 = BleDevice(
            address = "78:21:84:45:9A:10",
            name = "ESP32-S3 Waveform Node",
            rssi = -52,
            smoothedRssi = -52.0,
            txPower = -59,
            distanceMeters = 0.7,
            isConnectable = true,
            primaryPhy = BlePhy.PHY_LE_1M,
            secondaryPhy = BlePhy.PHY_LE_2M,
            advertisementRecord = AdvertisementRecord(
                serviceUuids = esp32Uuids,
                manufacturerData = esp32Mfr,
                companyName = "Espressif Systems"
            ),
            firstSeenTimestamp = now,
            lastSeenTimestamp = now
        )

        // 2. Polar H10 Heart Rate Monitor
        val hrUuids = listOf(
            BluetoothSigRegistry.uuidFromShort16("180D"),
            BluetoothSigRegistry.uuidFromShort16("180F"),
            BluetoothSigRegistry.uuidFromShort16("180A")
        )
        val hr = BleDevice(
            address = "C8:FD:19:3B:A2:44",
            name = "Polar H10 3BA244",
            rssi = -61,
            smoothedRssi = -61.0,
            txPower = -62,
            distanceMeters = 1.1,
            isConnectable = true,
            primaryPhy = BlePhy.PHY_LE_1M,
            advertisementRecord = AdvertisementRecord(
                serviceUuids = hrUuids,
                companyName = "Polar Electro Oy"
            ),
            firstSeenTimestamp = now,
            lastSeenTimestamp = now
        )

        // 3. Apple iBeacon Proximity Tag
        val ibeaconData = IBeaconData(
            uuid = UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0"),
            major = 101,
            minor = 202,
            measuredPower = -59,
            estimatedDistanceMeters = 1.4,
            proximity = ProximityZone.NEAR
        )
        val ibeacon = BleDevice(
            address = "E2:C5:6D:B5:D4:F1",
            name = "iBeacon Asset Tag",
            rssi = -66,
            smoothedRssi = -66.0,
            txPower = -59,
            distanceMeters = 1.4,
            isConnectable = false,
            advertisementRecord = AdvertisementRecord(
                iBeaconData = ibeaconData,
                manufacturerData = mapOf(0x004C to byteArrayOf(0x02, 0x15)),
                companyName = "Apple, Inc."
            ),
            firstSeenTimestamp = now,
            lastSeenTimestamp = now
        )

        // 4. RuuviTag Environmental Weather Sensor
        val ruuviUuids = listOf(BluetoothSigRegistry.uuidFromShort16("181A"))
        val ruuvi = BleDevice(
            address = "D4:36:39:CA:58:12",
            name = "RuuviTag CA58",
            rssi = -74,
            smoothedRssi = -74.0,
            txPower = -64,
            distanceMeters = 2.6,
            isConnectable = true,
            advertisementRecord = AdvertisementRecord(
                serviceUuids = ruuviUuids,
                manufacturerData = mapOf(0x0499 to byteArrayOf(0x05, 0x12, 0x60, 0x53)),
                companyName = "Ruuvi Innovations Ltd."
            ),
            firstSeenTimestamp = now,
            lastSeenTimestamp = now
        )

        // 5. Vulnerable IoT Sensor (Fuzz Target)
        val fuzzerTargetUuids = listOf(UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb"))
        val fuzzTarget = BleDevice(
            address = "DE:AD:BE:EF:00:01",
            name = "IoT Fuzz Target",
            rssi = -48,
            smoothedRssi = -48.0,
            txPower = -55,
            distanceMeters = 0.5,
            isConnectable = true,
            advertisementRecord = AdvertisementRecord(
                serviceUuids = fuzzerTargetUuids,
                companyName = "Generic Microcontroller"
            ),
            firstSeenTimestamp = now,
            lastSeenTimestamp = now
        )

        return listOf(esp32, hr, ibeacon, ruuvi, fuzzTarget)
    }
}

class VirtualBleConnection(
    override val address: String
) : BleConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionParameters = MutableStateFlow(ConnectionParameters(mtu = 23))
    override val connectionParameters: StateFlow<ConnectionParameters> = _connectionParameters.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    override val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _operationLogs = MutableStateFlow<List<GattOperationLog>>(emptyList())
    override val operationLogs: StateFlow<List<GattOperationLog>> = _operationLogs.asStateFlow()

    private val characteristicStreams = ConcurrentHashMap<UUID, MutableSharedFlow<ByteArray>>()
    private val activeStreamsJobs = ConcurrentHashMap<UUID, Job>()

    init {
        connectInternal()
    }

    private fun connectInternal() {
        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            log(GattOpType.CONNECT, address, GattStatus.SUCCESS, null, 15, "Connecting to virtual peripheral $address")
            delay(150)
            _connectionState.value = ConnectionState.CONNECTED
            log(GattOpType.CONNECT, address, GattStatus.SUCCESS, null, 40, "Connected with MTU 23")
            discoverServices()
        }
    }

    override suspend fun discoverServices(): Result<List<GattServiceInfo>> {
        log(GattOpType.DISCOVER_SERVICES, address, GattStatus.SUCCESS, null, 0, "Discovering services...")
        delay(120)

        val serviceList = when (address) {
            "C8:FD:19:3B:A2:44" -> buildHeartRateServices()
            "D4:36:39:CA:58:12" -> buildEnvironmentalServices()
            "DE:AD:BE:EF:00:01" -> buildFuzzTargetServices()
            else -> buildEsp32UartServices()
        }

        _services.value = serviceList
        log(GattOpType.DISCOVER_SERVICES, address, GattStatus.SUCCESS, null, 120, "Discovered ${serviceList.size} services")
        return Result.success(serviceList)
    }

    override suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Result<ByteArray> {
        delay(40)
        val service = _services.value.find { it.uuid == serviceUuid }
            ?: return Result.failure(IllegalArgumentException("Service not found"))
        val char = service.characteristics.find { it.uuid == charUuid }
            ?: return Result.failure(IllegalArgumentException("Characteristic not found"))

        val data = char.value ?: byteArrayOf(0x00)
        log(GattOpType.READ_CHARACTERISTIC, charUuid.toString(), GattStatus.SUCCESS, data, 40, "Read: ${DataTypeParsers.toHexString(data)}")
        return Result.success(data)
    }

    override suspend fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        withResponse: Boolean
    ): Result<Unit> {
        delay(if (withResponse) 50 else 15)

        // Special handling for Fuzz Target
        if (address == "DE:AD:BE:EF:00:01") {
            if (data.size > 64) {
                log(GattOpType.WRITE_CHARACTERISTIC, charUuid.toString(), GattStatus.INVALID_ATTRIBUTE_LENGTH, data, 50, "Buffer overflow fault: payload size ${data.size} > 64")
                return Result.failure(RuntimeException("GATT_INVALID_ATTRIBUTE_LENGTH: buffer exceeded"))
            }
        }

        // Update local characteristic representation
        _services.update { list ->
            list.map { s ->
                if (s.uuid == serviceUuid) {
                    s.copy(
                        characteristics = s.characteristics.map { c ->
                            if (c.uuid == charUuid) c.copy(value = data, lastUpdatedTimestamp = System.currentTimeMillis()) else c
                        }
                    )
                } else s
            }
        }

        val opType = if (withResponse) GattOpType.WRITE_CHARACTERISTIC else GattOpType.WRITE_COMMAND
        log(opType, charUuid.toString(), GattStatus.SUCCESS, data, if (withResponse) 50 else 15, "Written ${data.size} bytes: ${DataTypeParsers.toHexString(data.take(8).toByteArray())}")
        return Result.success(Unit)
    }

    override suspend fun setNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        enable: Boolean,
        indicate: Boolean
    ): Result<Unit> {
        delay(30)
        val op = if (enable) GattOpType.ENABLE_NOTIFICATION else GattOpType.DISABLE_NOTIFICATION

        if (enable) {
            startSimulationStream(charUuid)
        } else {
            activeStreamsJobs[charUuid]?.cancel()
            activeStreamsJobs.remove(charUuid)
        }

        log(op, charUuid.toString(), GattStatus.SUCCESS, null, 30, "${if (enable) "Enabled" else "Disabled"} notifications for $charUuid")
        return Result.success(Unit)
    }

    override suspend fun requestMtu(mtu: Int): Result<Int> {
        delay(45)
        val granted = mtu.coerceIn(23, 517)
        _connectionParameters.update { it.copy(mtu = granted) }
        log(GattOpType.REQUEST_MTU, null, GattStatus.SUCCESS, null, 45, "MTU updated to $granted bytes")
        return Result.success(granted)
    }

    override suspend fun requestPhy(txPhy: BlePhy, rxPhy: BlePhy): Result<Pair<BlePhy, BlePhy>> {
        delay(35)
        _connectionParameters.update { it.copy(txPhy = txPhy, rxPhy = rxPhy) }
        log(GattOpType.REQUEST_PHY, null, GattStatus.SUCCESS, null, 35, "PHY updated: TX=${txPhy.label}, RX=${rxPhy.label}")
        return Result.success(Pair(txPhy, rxPhy))
    }

    override fun observeCharacteristic(charUuid: UUID): Flow<ByteArray> {
        return characteristicStreams.getOrPut(charUuid) {
            MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }.asSharedFlow()
    }

    override fun disconnect() {
        activeStreamsJobs.values.forEach { it.cancel() }
        activeStreamsJobs.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        log(GattOpType.DISCONNECT, address, GattStatus.DEVICE_DISCONNECTED, null, 10, "Virtual connection closed")
    }

    private fun startSimulationStream(charUuid: UUID) {
        val stream = characteristicStreams.getOrPut(charUuid) {
            MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

        activeStreamsJobs[charUuid]?.cancel()
        activeStreamsJobs[charUuid] = scope.launch {
            var step = 0
            while (isActive) {
                val payload = when (charUuid) {
                    // Heart Rate Measurement (0x2A37)
                    BluetoothSigRegistry.uuidFromShort16("2A37") -> {
                        val baseBpm = 72
                        val hrv = (sin(step * 0.15) * 6).toInt()
                        val bpm = baseBpm + hrv
                        byteArrayOf(0x00, bpm.toByte())
                    }
                    // Environmental Temperature (0x2A6E)
                    BluetoothSigRegistry.uuidFromShort16("2A6E") -> {
                        val baseTemp = 2350 // 23.50 C
                        val variation = (sin(step * 0.1) * 80).toInt()
                        val tempVal = (baseTemp + variation).toShort()
                        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(tempVal).array()
                    }
                    // Nordic UART TX (6E400003-...)
                    UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") -> {
                        // Synthesizing 3-channel wave: X = sin, Y = cos, Z = noise
                        val x = (sin(step * 0.2) * 100.0).toFloat()
                        val y = (kotlin.math.cos(step * 0.2) * 80.0).toFloat()
                        val z = (sin(step * 0.4) * 40.0).toFloat()
                        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                            .putFloat(x)
                            .putFloat(y)
                            .putFloat(z)
                            .array()
                    }
                    else -> {
                        byteArrayOf(step.toByte())
                    }
                }

                // Update services cache
                _services.update { list ->
                    list.map { s ->
                        s.copy(
                            characteristics = s.characteristics.map { c ->
                                if (c.uuid == charUuid) c.copy(value = payload, lastUpdatedTimestamp = System.currentTimeMillis()) else c
                            }
                        )
                    }
                }

                stream.emit(payload)
                step++
                delay(100) // 10 Hz telemetry stream
            }
        }
    }

    private fun log(opType: GattOpType, target: String?, status: GattStatus, payload: ByteArray?, latency: Long, msg: String) {
        val entry = GattOperationLog(
            opType = opType,
            targetUuid = target,
            status = status,
            rawPayload = payload,
            latencyMs = latency,
            message = msg
        )
        _operationLogs.update { (listOf(entry) + it).take(100) }
    }

    // Service builders for virtual peripherals
    private fun buildEsp32UartServices(): List<GattServiceInfo> {
        val nusUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val rxUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val txUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val nus = GattServiceInfo(
            uuid = nusUuid,
            name = "Nordic UART Service (NUS)",
            isStandard = false,
            type = 0,
            characteristics = listOf(
                GattCharacteristicInfo(
                    uuid = rxUuid,
                    name = "Nordic UART RX (Write)",
                    properties = 0x0C, // Write | WriteWithoutResponse
                    permissions = 0x11,
                    value = byteArrayOf()
                ),
                GattCharacteristicInfo(
                    uuid = txUuid,
                    name = "Nordic UART TX (Notify)",
                    properties = 0x10, // Notify
                    permissions = 0x01,
                    value = byteArrayOf(),
                    descriptors = listOf(
                        GattDescriptorInfo(BluetoothSigRegistry.CCCD_UUID, "CCCD", byteArrayOf(0x00, 0x00), true)
                    )
                )
            )
        )

        val batteryService = GattServiceInfo(
            uuid = BluetoothSigRegistry.uuidFromShort16("180F"),
            name = "Battery Service",
            isStandard = true,
            type = 0,
            characteristics = listOf(
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A19"),
                    name = "Battery Level",
                    properties = 0x12, // Read | Notify
                    permissions = 0x01,
                    value = byteArrayOf(94)
                )
            )
        )

        return listOf(nus, batteryService)
    }

    private fun buildHeartRateServices(): List<GattServiceInfo> {
        val hrService = GattServiceInfo(
            uuid = BluetoothSigRegistry.uuidFromShort16("180D"),
            name = "Heart Rate Service",
            isStandard = true,
            type = 0,
            characteristics = listOf(
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A37"),
                    name = "Heart Rate Measurement",
                    properties = 0x10, // Notify
                    permissions = 0x01,
                    value = byteArrayOf(0x00, 72),
                    descriptors = listOf(
                        GattDescriptorInfo(BluetoothSigRegistry.CCCD_UUID, "CCCD", byteArrayOf(0x00, 0x00), true)
                    )
                ),
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A38"),
                    name = "Body Sensor Location",
                    properties = 0x02, // Read
                    permissions = 0x01,
                    value = byteArrayOf(0x01) // Chest
                )
            )
        )

        val deviceInfo = GattServiceInfo(
            uuid = BluetoothSigRegistry.uuidFromShort16("180A"),
            name = "Device Information",
            isStandard = true,
            type = 0,
            characteristics = listOf(
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A29"),
                    name = "Manufacturer Name String",
                    properties = 0x02,
                    permissions = 0x01,
                    value = "Polar Electro Oy".toByteArray()
                ),
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A24"),
                    name = "Model Number String",
                    properties = 0x02,
                    permissions = 0x01,
                    value = "Polar H10".toByteArray()
                ),
                GattCharacteristicInfo(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A26"),
                    name = "Firmware Revision String",
                    properties = 0x02,
                    permissions = 0x01,
                    value = "v3.2.1-ble".toByteArray()
                )
            )
        )

        return listOf(hrService, deviceInfo)
    }

    private fun buildEnvironmentalServices(): List<GattServiceInfo> {
        return listOf(
            GattServiceInfo(
                uuid = BluetoothSigRegistry.uuidFromShort16("181A"),
                name = "Environmental Sensing",
                isStandard = true,
                type = 0,
                characteristics = listOf(
                    GattCharacteristicInfo(
                        uuid = BluetoothSigRegistry.uuidFromShort16("2A6E"),
                        name = "Temperature",
                        properties = 0x12, // Read | Notify
                        permissions = 0x01,
                        value = byteArrayOf(0x2E, 0x09) // 23.50 C
                    ),
                    GattCharacteristicInfo(
                        uuid = BluetoothSigRegistry.uuidFromShort16("2A6F"),
                        name = "Humidity",
                        properties = 0x02, // Read
                        permissions = 0x01,
                        value = byteArrayOf(0x4C, 0x11) // 44.28 %
                    )
                )
            )
        )
    }

    private fun buildFuzzTargetServices(): List<GattServiceInfo> {
        val targetUuid = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")
        val bufferChar = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val statusChar = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

        return listOf(
            GattServiceInfo(
                uuid = targetUuid,
                name = "Vulnerable Peripheral Service",
                isStandard = false,
                type = 0,
                characteristics = listOf(
                    GattCharacteristicInfo(
                        uuid = bufferChar,
                        name = "Buffer Input (Max 64 bytes)",
                        properties = 0x0A, // Read | Write
                        permissions = 0x11,
                        value = byteArrayOf(0x00)
                    ),
                    GattCharacteristicInfo(
                        uuid = statusChar,
                        name = "Device Health Register",
                        properties = 0x02, // Read
                        permissions = 0x01,
                        value = "OK: Healthy".toByteArray()
                    )
                )
            )
        )
    }
}
