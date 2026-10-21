package com.blepulse.android.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.blepulse.android.core.model.GattOpType
import com.blepulse.android.core.model.GattOperationLog
import com.blepulse.android.core.model.GattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed interface GattCommand {
    val deferred: CompletableDeferred<Any>

    data class ReadChar(
        val characteristic: BluetoothGattCharacteristic,
        override val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : GattCommand

    data class WriteChar(
        val characteristic: BluetoothGattCharacteristic,
        val payload: ByteArray,
        val writeType: Int,
        override val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : GattCommand

    data class WriteDesc(
        val descriptor: BluetoothGattDescriptor,
        val payload: ByteArray,
        override val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : GattCommand

    data class RequestMtu(
        val mtu: Int,
        override val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : GattCommand

    data class RequestPhy(
        val txPhy: Int,
        val rxPhy: Int,
        override val deferred: CompletableDeferred<Any> = CompletableDeferred()
    ) : GattCommand
}

class GattOperationQueue(
    private val gattProvider: () -> BluetoothGatt?,
    private val defaultTimeoutMs: Long = 4000L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandChannel = Channel<GattCommand>(Channel.UNLIMITED)

    private val _logs = MutableStateFlow<List<GattOperationLog>>(emptyList())
    val logs: StateFlow<List<GattOperationLog>> = _logs.asStateFlow()

    private var currentCommand: GattCommand? = null

    init {
        scope.launch {
            for (command in commandChannel) {
                currentCommand = command
                val startTime = System.currentTimeMillis()
                var status = GattStatus.SUCCESS
                var returnPayload: ByteArray? = null
                var errorMsg = ""

                val gatt = gattProvider()
                if (gatt == null) {
                    command.deferred.completeExceptionally(IllegalStateException("BluetoothGatt is null or disconnected"))
                    log(GattOpType.READ_CHARACTERISTIC, null, GattStatus.DEVICE_DISCONNECTED, null, 0, "Gatt null")
                    continue
                }

                try {
                    val executeResult = when (command) {
                        is GattCommand.ReadChar -> {
                            gatt.readCharacteristic(command.characteristic)
                        }
                        is GattCommand.WriteChar -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val res = gatt.writeCharacteristic(command.characteristic, command.payload, command.writeType)
                                res == 0
                            } else {
                                @Suppress("DEPRECATION")
                                command.characteristic.writeType = command.writeType
                                @Suppress("DEPRECATION")
                                command.characteristic.value = command.payload
                                @Suppress("DEPRECATION")
                                gatt.writeCharacteristic(command.characteristic)
                            }
                        }
                        is GattCommand.WriteDesc -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val res = gatt.writeDescriptor(command.descriptor, command.payload)
                                res == 0
                            } else {
                                @Suppress("DEPRECATION")
                                command.descriptor.value = command.payload
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(command.descriptor)
                            }
                        }
                        is GattCommand.RequestMtu -> {
                            gatt.requestMtu(command.mtu)
                        }
                        is GattCommand.RequestPhy -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                gatt.setPreferredPhy(command.txPhy, command.rxPhy, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                                true
                            } else {
                                false
                            }
                        }
                    }

                    if (!executeResult) {
                        status = GattStatus.FAILURE
                        errorMsg = "Gatt invocation returned false"
                        command.deferred.completeExceptionally(RuntimeException(errorMsg))
                    } else {
                        val result = withTimeoutOrNull(defaultTimeoutMs) {
                            command.deferred.await()
                        }
                        if (result == null) {
                            status = GattStatus.TIMEOUT
                            errorMsg = "GATT operation timed out after ${defaultTimeoutMs}ms"
                            command.deferred.completeExceptionally(RuntimeException(errorMsg))
                        } else {
                            if (result is ByteArray) {
                                returnPayload = result
                            }
                        }
                    }
                } catch (e: Exception) {
                    status = GattStatus.FAILURE
                    errorMsg = e.message ?: "Unknown error"
                    command.deferred.completeExceptionally(e)
                } finally {
                    val latency = System.currentTimeMillis() - startTime
                    val opType = when (command) {
                        is GattCommand.ReadChar -> GattOpType.READ_CHARACTERISTIC
                        is GattCommand.WriteChar -> if (command.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) GattOpType.WRITE_COMMAND else GattOpType.WRITE_CHARACTERISTIC
                        is GattCommand.WriteDesc -> GattOpType.WRITE_DESCRIPTOR
                        is GattCommand.RequestMtu -> GattOpType.REQUEST_MTU
                        is GattCommand.RequestPhy -> GattOpType.REQUEST_PHY
                    }
                    val target = when (command) {
                        is GattCommand.ReadChar -> command.characteristic.uuid.toString()
                        is GattCommand.WriteChar -> command.characteristic.uuid.toString()
                        is GattCommand.WriteDesc -> command.descriptor.uuid.toString()
                        else -> null
                    }
                    val payload = when (command) {
                        is GattCommand.WriteChar -> command.payload
                        is GattCommand.WriteDesc -> command.payload
                        else -> returnPayload
                    }
                    log(opType, target, status, payload, latency, errorMsg)
                    currentCommand = null
                }
            }
        }
    }

    suspend fun executeRead(characteristic: BluetoothGattCharacteristic): Result<ByteArray> {
        val cmd = GattCommand.ReadChar(characteristic)
        commandChannel.send(cmd)
        return runCatching {
            cmd.deferred.await() as ByteArray
        }
    }

    suspend fun executeWrite(
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        writeType: Int
    ): Result<Unit> {
        val cmd = GattCommand.WriteChar(characteristic, payload, writeType)
        commandChannel.send(cmd)
        return runCatching {
            cmd.deferred.await()
            Unit
        }
    }

    suspend fun executeDescriptorWrite(
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray
    ): Result<Unit> {
        val cmd = GattCommand.WriteDesc(descriptor, payload)
        commandChannel.send(cmd)
        return runCatching {
            cmd.deferred.await()
            Unit
        }
    }

    suspend fun executeRequestMtu(mtu: Int): Result<Int> {
        val cmd = GattCommand.RequestMtu(mtu)
        commandChannel.send(cmd)
        return runCatching {
            cmd.deferred.await() as Int
        }
    }

    suspend fun executeRequestPhy(txPhy: Int, rxPhy: Int): Result<Pair<Int, Int>> {
        val cmd = GattCommand.RequestPhy(txPhy, rxPhy)
        commandChannel.send(cmd)
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            cmd.deferred.await() as Pair<Int, Int>
        }
    }

    fun onCharacteristicReadResult(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        val cmd = currentCommand as? GattCommand.ReadChar
        if (cmd != null && cmd.characteristic.uuid == characteristic.uuid) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cmd.deferred.complete(value)
            } else {
                cmd.deferred.completeExceptionally(RuntimeException("GATT Read failed with status $status"))
            }
        }
    }

    fun onCharacteristicWriteResult(characteristic: BluetoothGattCharacteristic, status: Int) {
        val cmd = currentCommand as? GattCommand.WriteChar
        if (cmd != null && cmd.characteristic.uuid == characteristic.uuid) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cmd.deferred.complete(Unit)
            } else {
                cmd.deferred.completeExceptionally(RuntimeException("GATT Write failed with status $status"))
            }
        }
    }

    fun onDescriptorWriteResult(descriptor: BluetoothGattDescriptor, status: Int) {
        val cmd = currentCommand as? GattCommand.WriteDesc
        if (cmd != null && cmd.descriptor.uuid == descriptor.uuid) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cmd.deferred.complete(Unit)
            } else {
                cmd.deferred.completeExceptionally(RuntimeException("Descriptor Write failed with status $status"))
            }
        }
    }

    fun onMtuChangedResult(mtu: Int, status: Int) {
        val cmd = currentCommand as? GattCommand.RequestMtu
        if (cmd != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cmd.deferred.complete(mtu)
            } else {
                cmd.deferred.completeExceptionally(RuntimeException("MTU Request failed with status $status"))
            }
        }
    }

    fun onPhyUpdateResult(txPhy: Int, rxPhy: Int, status: Int) {
        val cmd = currentCommand as? GattCommand.RequestPhy
        if (cmd != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cmd.deferred.complete(Pair(txPhy, rxPhy))
            } else {
                cmd.deferred.completeExceptionally(RuntimeException("PHY Update failed with status $status"))
            }
        }
    }

    fun log(opType: GattOpType, target: String?, status: GattStatus, payload: ByteArray?, latency: Long, msg: String) {
        val entry = GattOperationLog(
            opType = opType,
            targetUuid = target,
            status = status,
            rawPayload = payload,
            latencyMs = latency,
            message = msg
        )
        _logs.update { (listOf(entry) + it).take(100) }
    }
}
