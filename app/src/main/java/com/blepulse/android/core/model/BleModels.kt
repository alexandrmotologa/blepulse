package com.blepulse.android.core.model

import java.util.UUID

enum class BlePhy(val value: Int, val label: String) {
    PHY_LE_1M(1, "LE 1M"),
    PHY_LE_2M(2, "LE 2M"),
    PHY_LE_CODED(3, "LE Coded"),
    PHY_UNKNOWN(0, "Unknown")
}

data class RssiSample(
    val timestamp: Long,
    val rssi: Int
)

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val smoothedRssi: Double,
    val rssiHistory: List<RssiSample> = emptyList(),
    val txPower: Int? = null,
    val distanceMeters: Double? = null,
    val isConnectable: Boolean = true,
    val primaryPhy: BlePhy = BlePhy.PHY_LE_1M,
    val secondaryPhy: BlePhy = BlePhy.PHY_UNKNOWN,
    val advertisingSid: Int? = null,
    val periodicIntervalMs: Long? = null,
    val advertisementRecord: AdvertisementRecord = AdvertisementRecord(),
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Device"

    val proximityZone: ProximityZone
        get() = when {
            distanceMeters == null -> ProximityZone.UNKNOWN
            distanceMeters < 0.5 -> ProximityZone.IMMEDIATE
            distanceMeters < 3.0 -> ProximityZone.NEAR
            else -> ProximityZone.FAR
        }
}

enum class ProximityZone(val label: String) {
    IMMEDIATE("Immediate (<0.5m)"),
    NEAR("Near (<3m)"),
    FAR("Far (>3m)"),
    UNKNOWN("Unknown")
}

data class AdvertisementRecord(
    val rawBytes: ByteArray = byteArrayOf(),
    val flags: Int? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val companyName: String? = null,
    val iBeaconData: IBeaconData? = null,
    val eddystoneData: EddystoneData? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdvertisementRecord
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        return rawBytes.contentHashCode()
    }
}

data class IBeaconData(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val measuredPower: Int,
    val estimatedDistanceMeters: Double,
    val proximity: ProximityZone
)

sealed interface EddystoneData {
    data class Uid(
        val namespace: String,
        val instance: String,
        val calibratedTxPower: Int
    ) : EddystoneData

    data class Url(
        val url: String,
        val calibratedTxPower: Int
    ) : EddystoneData

    data class Tlm(
        val version: Int,
        val batteryVoltageMv: Int,
        val temperatureCelsius: Float,
        val advertisingPduCount: Long,
        val uptimeSeconds: Long
    ) : EddystoneData

    data class Unknown(val frameType: Int, val rawBytes: ByteArray) : EddystoneData {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Unknown
            return frameType == other.frameType && rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int {
            return 31 * frameType + rawBytes.contentHashCode()
        }
    }
}

data class GattServiceInfo(
    val uuid: UUID,
    val name: String,
    val isStandard: Boolean,
    val type: Int, // 0 = Primary, 1 = Secondary
    val characteristics: List<GattCharacteristicInfo> = emptyList()
)

data class GattCharacteristicInfo(
    val uuid: UUID,
    val name: String,
    val properties: Int,
    val permissions: Int,
    val value: ByteArray? = null,
    val lastUpdatedTimestamp: Long? = null,
    val descriptors: List<GattDescriptorInfo> = emptyList()
) {
    val isReadable: Boolean get() = (properties and 0x02) != 0
    val isWritableWithoutResponse: Boolean get() = (properties and 0x04) != 0
    val isWritable: Boolean get() = (properties and 0x08) != 0
    val isNotifiable: Boolean get() = (properties and 0x10) != 0
    val isIndicatable: Boolean get() = (properties and 0x20) != 0
    val isSignedWritable: Boolean get() = (properties and 0x40) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GattCharacteristicInfo
        if (uuid != other.uuid) return false
        if (properties != other.properties) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false
        return descriptors == other.descriptors
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + properties
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + descriptors.hashCode()
        return result
    }
}

data class GattDescriptorInfo(
    val uuid: UUID,
    val name: String,
    val value: ByteArray? = null,
    val isCccd: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GattDescriptorInfo
        if (uuid != other.uuid) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false
        return isCccd == other.isCccd
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + isCccd.hashCode()
        return result
    }
}

enum class GattOpType {
    CONNECT,
    DISCONNECT,
    DISCOVER_SERVICES,
    READ_CHARACTERISTIC,
    WRITE_CHARACTERISTIC,
    WRITE_COMMAND,
    READ_DESCRIPTOR,
    WRITE_DESCRIPTOR,
    ENABLE_NOTIFICATION,
    DISABLE_NOTIFICATION,
    ENABLE_INDICATION,
    DISABLE_INDICATION,
    NOTIFY,
    INDICATE,
    REQUEST_MTU,
    REQUEST_PHY
}

enum class GattStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    READ_NOT_PERMITTED,
    WRITE_NOT_PERMITTED,
    INSUFFICIENT_AUTHENTICATION,
    INSUFFICIENT_ENCRYPTION,
    INVALID_OFFSET,
    INVALID_ATTRIBUTE_LENGTH,
    CONNECTION_CONGESTED,
    DEVICE_DISCONNECTED
}

data class GattOperationLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val opType: GattOpType,
    val targetUuid: String? = null,
    val status: GattStatus,
    val rawPayload: ByteArray? = null,
    val latencyMs: Long = 0,
    val message: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GattOperationLog
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class ConnectionParameters(
    val mtu: Int = 23,
    val txPhy: BlePhy = BlePhy.PHY_LE_1M,
    val rxPhy: BlePhy = BlePhy.PHY_LE_1M
)
