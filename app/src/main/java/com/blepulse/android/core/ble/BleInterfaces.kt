package com.blepulse.android.core.ble

import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.BlePhy
import com.blepulse.android.core.model.ConnectionParameters
import com.blepulse.android.core.model.ConnectionState
import com.blepulse.android.core.model.GattOperationLog
import com.blepulse.android.core.model.GattServiceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class ScanFilterConfig(
    val nameQuery: String = "",
    val minRssi: Int = -100,
    val serviceUuid: UUID? = null,
    val manufacturerId: Int? = null,
    val onlyConnectable: Boolean = false,
    val onlyWithServices: Boolean = false
)

data class CustomAdvertiseConfig(
    val deviceName: String = "BLEPulse Peripheral",
    val serviceUuid: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
    val includeTxPower: Boolean = true,
    val connectable: Boolean = true,
    val manufacturerCompanyId: Int = 0x0059, // Nordic
    val manufacturerPayload: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
)

interface BleConnection {
    val address: String
    val connectionState: StateFlow<ConnectionState>
    val connectionParameters: StateFlow<ConnectionParameters>
    val services: StateFlow<List<GattServiceInfo>>
    val operationLogs: StateFlow<List<GattOperationLog>>

    suspend fun discoverServices(): Result<List<GattServiceInfo>>
    suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Result<ByteArray>
    suspend fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, data: ByteArray, withResponse: Boolean): Result<Unit>
    suspend fun setNotification(serviceUuid: UUID, charUuid: UUID, enable: Boolean, indicate: Boolean = false): Result<Unit>
    suspend fun requestMtu(mtu: Int): Result<Int>
    suspend fun requestPhy(txPhy: BlePhy, rxPhy: BlePhy): Result<Pair<BlePhy, BlePhy>>
    fun observeCharacteristic(charUuid: UUID): Flow<ByteArray>
    fun disconnect()
}

interface BleHardwareProvider {
    val isScanning: StateFlow<Boolean>
    val isAdvertising: StateFlow<Boolean>
    val discoveredDevices: StateFlow<List<BleDevice>>

    fun startScan(filter: ScanFilterConfig = ScanFilterConfig())
    fun stopScan()
    fun connect(address: String): BleConnection
    fun startAdvertising(config: CustomAdvertiseConfig): Boolean
    fun stopAdvertising()
}
