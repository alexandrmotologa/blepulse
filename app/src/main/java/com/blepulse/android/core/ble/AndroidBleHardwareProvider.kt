package com.blepulse.android.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.blepulse.android.core.decoder.BleAdvertisementParser
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
import com.blepulse.android.core.model.RssiSample
import com.blepulse.android.core.sig.BluetoothSigRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AndroidBleHardwareProvider(
    private val context: Context
) : BleHardwareProvider {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val leScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val leAdvertiser: BluetoothLeAdvertiser? get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BleDevice>()

    private var activeScanCallback: ScanCallback? = null
    private var activeAdvertiseCallback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    override fun startScan(filter: ScanFilterConfig) {
        if (_isScanning.value) return
        val scanner = leScanner ?: return

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }
            .build()

        val filters = mutableListOf<ScanFilter>()
        if (filter.serviceUuid != null) {
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(filter.serviceUuid)).build())
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processScanResult(result, filter)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { processScanResult(it, filter) }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
            }
        }

        activeScanCallback = callback
        try {
            scanner.startScan(if (filters.isEmpty()) null else filters, scanSettings, callback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            _isScanning.value = false
        }
    }

    private fun processScanResult(result: ScanResult, filter: ScanFilterConfig) {
        val device = result.device ?: return
        val address = device.address ?: return
        @SuppressLint("MissingPermission")
        val name = try { device.name ?: result.scanRecord?.deviceName } catch (_: SecurityException) { result.scanRecord?.deviceName }

        // Filter checks
        if (filter.nameQuery.isNotBlank()) {
            val matches = name?.contains(filter.nameQuery, ignoreCase = true) == true ||
                    address.contains(filter.nameQuery, ignoreCase = true)
            if (!matches) return
        }

        if (result.rssi < filter.minRssi) return

        val rawBytes = result.scanRecord?.bytes ?: byteArrayOf()
        val record = BleAdvertisementParser.parse(rawBytes, result.rssi)

        if (filter.manufacturerId != null && !record.manufacturerData.containsKey(filter.manufacturerId)) {
            return
        }

        val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable
        } else {
            true
        }
        if (filter.onlyConnectable && !isConnectable) return

        val primaryPhy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (result.primaryPhy) {
                BluetoothDevice.PHY_LE_1M -> BlePhy.PHY_LE_1M
                BluetoothDevice.PHY_LE_CODED -> BlePhy.PHY_LE_CODED
                else -> BlePhy.PHY_UNKNOWN
            }
        } else BlePhy.PHY_LE_1M

        val secondaryPhy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (result.secondaryPhy) {
                BluetoothDevice.PHY_LE_1M -> BlePhy.PHY_LE_1M
                BluetoothDevice.PHY_LE_2M -> BlePhy.PHY_LE_2M
                BluetoothDevice.PHY_LE_CODED -> BlePhy.PHY_LE_CODED
                else -> BlePhy.PHY_UNKNOWN
            }
        } else BlePhy.PHY_UNKNOWN

        val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && result.txPower != ScanResult.TX_POWER_NOT_PRESENT) {
            result.txPower
        } else {
            result.scanRecord?.txPowerLevel
        }

        val existing = deviceMap[address]
        val now = System.currentTimeMillis()
        val smoothed = if (existing != null) {
            BleAdvertisementParser.applyEmaFilter(existing.smoothedRssi, result.rssi)
        } else {
            result.rssi.toDouble()
        }

        val distance = if (record.iBeaconData != null) {
            record.iBeaconData.estimatedDistanceMeters
        } else if (txPower != null) {
            BleAdvertisementParser.calculateDistance(txPower, result.rssi)
        } else null

        val sample = RssiSample(now, result.rssi)
        val history = ((existing?.rssiHistory ?: emptyList()) + sample).takeLast(30)

        val updated = BleDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            smoothedRssi = smoothed,
            rssiHistory = history,
            txPower = txPower,
            distanceMeters = distance,
            isConnectable = isConnectable,
            primaryPhy = primaryPhy,
            secondaryPhy = secondaryPhy,
            advertisingSid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.advertisingSid else null,
            periodicIntervalMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.periodicAdvertisingInterval * 1.25.toLong() else null,
            advertisementRecord = record,
            firstSeenTimestamp = existing?.firstSeenTimestamp ?: now,
            lastSeenTimestamp = now,
            isPinned = existing?.isPinned ?: false
        )

        deviceMap[address] = updated
        _discoveredDevices.value = deviceMap.values.sortedByDescending { it.smoothedRssi }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        val scanner = leScanner
        val callback = activeScanCallback
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) { }
        }
        activeScanCallback = null
        _isScanning.value = false
    }

    override fun connect(address: String): BleConnection {
        return AndroidBleConnection(context, bluetoothAdapter, address)
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising(config: CustomAdvertiseConfig): Boolean {
        val advertiser = leAdvertiser ?: return false

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(config.connectable)
            .setTimeout(0)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(config.includeTxPower)
            .addServiceUuid(ParcelUuid(config.serviceUuid))

        if (config.manufacturerPayload.isNotEmpty()) {
            dataBuilder.addManufacturerData(config.manufacturerCompanyId, config.manufacturerPayload)
        }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
            }

            override fun onStartFailure(errorCode: Int) {
                _isAdvertising.value = false
            }
        }

        activeAdvertiseCallback = callback
        return try {
            advertiser.startAdvertising(settings, dataBuilder.build(), callback)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        val advertiser = leAdvertiser
        val callback = activeAdvertiseCallback
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: SecurityException) {}
        }
        activeAdvertiseCallback = null
        _isAdvertising.value = false
    }
}

class AndroidBleConnection(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    override val address: String
) : BleConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionParameters = MutableStateFlow(ConnectionParameters())
    override val connectionParameters: StateFlow<ConnectionParameters> = _connectionParameters.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    override val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private val queue = GattOperationQueue(gattProvider = { bluetoothGatt })
    override val operationLogs: StateFlow<List<GattOperationLog>> = queue.logs

    private val characteristicStreams = ConcurrentHashMap<UUID, MutableSharedFlow<ByteArray>>()

    init {
        connectInternal()
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal() {
        _connectionState.value = ConnectionState.CONNECTING
        queue.log(GattOpType.CONNECT, address, GattStatus.SUCCESS, null, 0, "Initiating connection to $address")

        val device = try { adapter?.getRemoteDevice(address) } catch (_: Exception) { null }
        if (device == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            queue.log(GattOpType.CONNECT, address, GattStatus.FAILURE, null, 0, "Device not found")
            return
        }

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            queue.log(GattOpType.CONNECT, address, GattStatus.FAILURE, null, 0, "SecurityException: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    queue.log(GattOpType.CONNECT, address, GattStatus.SUCCESS, null, 0, "Connected (status=$status)")
                    // Automatically request high MTU (517) upon connection
                    gatt.requestMtu(517)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    queue.log(GattOpType.DISCONNECT, address, GattStatus.DEVICE_DISCONNECTED, null, 0, "Disconnected (status=$status)")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val list = gatt.services.map { service ->
                    GattServiceInfo(
                        uuid = service.uuid,
                        name = BluetoothSigRegistry.resolveServiceName(service.uuid),
                        isStandard = BluetoothSigRegistry.isStandardService(service.uuid),
                        type = service.type,
                        characteristics = service.characteristics.map { char ->
                            GattCharacteristicInfo(
                                uuid = char.uuid,
                                name = BluetoothSigRegistry.resolveCharacteristicName(char.uuid),
                                properties = char.properties,
                                permissions = char.permissions,
                                value = char.value,
                                descriptors = char.descriptors.map { desc ->
                                    GattDescriptorInfo(
                                        uuid = desc.uuid,
                                        name = BluetoothSigRegistry.resolveDescriptorName(desc.uuid),
                                        value = desc.value,
                                        isCccd = desc.uuid == BluetoothSigRegistry.CCCD_UUID
                                    )
                                }
                            )
                        }
                    )
                }
                _services.value = list
                queue.log(GattOpType.DISCOVER_SERVICES, address, GattStatus.SUCCESS, null, 0, "Discovered ${list.size} services")
            } else {
                queue.log(GattOpType.DISCOVER_SERVICES, address, GattStatus.FAILURE, null, 0, "Service discovery failed: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            queue.onCharacteristicReadResult(characteristic, characteristic.value ?: byteArrayOf(), status)
            updateCharacteristicValue(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            queue.onCharacteristicReadResult(characteristic, value, status)
            updateCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            queue.onCharacteristicWriteResult(characteristic, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            queue.onDescriptorWriteResult(descriptor, status)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            queue.onMtuChangedResult(mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionParameters.update { it.copy(mtu = mtu) }
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            queue.onPhyUpdateResult(txPhy, rxPhy, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionParameters.update {
                    it.copy(
                        txPhy = when (txPhy) {
                            BluetoothDevice.PHY_LE_2M -> BlePhy.PHY_LE_2M
                            BluetoothDevice.PHY_LE_CODED -> BlePhy.PHY_LE_CODED
                            else -> BlePhy.PHY_LE_1M
                        },
                        rxPhy = when (rxPhy) {
                            BluetoothDevice.PHY_LE_2M -> BlePhy.PHY_LE_2M
                            BluetoothDevice.PHY_LE_CODED -> BlePhy.PHY_LE_CODED
                            else -> BlePhy.PHY_LE_1M
                        }
                    )
                }
            }
        }
    }

    private fun handleNotification(charUuid: UUID, value: ByteArray) {
        updateCharacteristicValue(charUuid, value)
        characteristicStreams[charUuid]?.tryEmit(value)
        queue.log(GattOpType.NOTIFY, charUuid.toString(), GattStatus.SUCCESS, value, 0, "Notification: ${value.size} bytes")
    }

    private fun updateCharacteristicValue(charUuid: UUID, value: ByteArray?) {
        _services.update { list ->
            list.map { service ->
                service.copy(
                    characteristics = service.characteristics.map { char ->
                        if (char.uuid == charUuid) {
                            char.copy(value = value, lastUpdatedTimestamp = System.currentTimeMillis())
                        } else char
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverServices(): Result<List<GattServiceInfo>> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Not connected"))
        gatt.discoverServices()
        return Result.success(_services.value)
    }

    override suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Result<ByteArray> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Not connected"))
        val service = gatt.getService(serviceUuid) ?: return Result.failure(IllegalArgumentException("Service not found"))
        val char = service.getCharacteristic(charUuid) ?: return Result.failure(IllegalArgumentException("Characteristic not found"))
        return queue.executeRead(char)
    }

    override suspend fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        withResponse: Boolean
    ): Result<Unit> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Not connected"))
        val service = gatt.getService(serviceUuid) ?: return Result.failure(IllegalArgumentException("Service not found"))
        val char = service.getCharacteristic(charUuid) ?: return Result.failure(IllegalArgumentException("Characteristic not found"))
        val writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        return queue.executeWrite(char, data, writeType)
    }

    @SuppressLint("MissingPermission")
    override suspend fun setNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        enable: Boolean,
        indicate: Boolean
    ): Result<Unit> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Not connected"))
        val service = gatt.getService(serviceUuid) ?: return Result.failure(IllegalArgumentException("Service not found"))
        val char = service.getCharacteristic(charUuid) ?: return Result.failure(IllegalArgumentException("Characteristic not found"))

        gatt.setCharacteristicNotification(char, enable)
        val cccd = char.getDescriptor(BluetoothSigRegistry.CCCD_UUID)
            ?: return Result.failure(IllegalStateException("CCCD descriptor (0x2902) not found on characteristic"))

        val payload = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            indicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        return queue.executeDescriptorWrite(cccd, payload)
    }

    override suspend fun requestMtu(mtu: Int): Result<Int> {
        return queue.executeRequestMtu(mtu)
    }

    override suspend fun requestPhy(txPhy: BlePhy, rxPhy: BlePhy): Result<Pair<BlePhy, BlePhy>> {
        val txNative = when (txPhy) {
            BlePhy.PHY_LE_2M -> BluetoothDevice.PHY_LE_2M
            BlePhy.PHY_LE_CODED -> BluetoothDevice.PHY_LE_CODED
            else -> BluetoothDevice.PHY_LE_1M
        }
        val rxNative = when (rxPhy) {
            BlePhy.PHY_LE_2M -> BluetoothDevice.PHY_LE_2M
            BlePhy.PHY_LE_CODED -> BluetoothDevice.PHY_LE_CODED
            else -> BluetoothDevice.PHY_LE_1M
        }
        return queue.executeRequestPhy(txNative, rxNative).map { (tx, rx) ->
            Pair(
                if (tx == BluetoothDevice.PHY_LE_2M) BlePhy.PHY_LE_2M else BlePhy.PHY_LE_1M,
                if (rx == BluetoothDevice.PHY_LE_2M) BlePhy.PHY_LE_2M else BlePhy.PHY_LE_1M
            )
        }
    }

    override fun observeCharacteristic(charUuid: UUID): Flow<ByteArray> {
        return characteristicStreams.getOrPut(charUuid) {
            MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }.asSharedFlow()
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: SecurityException) {}
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
