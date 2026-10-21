package com.blepulse.android.core.decoder

import com.blepulse.android.core.model.AdvertisementRecord
import com.blepulse.android.core.model.BlePhy
import com.blepulse.android.core.model.EddystoneData
import com.blepulse.android.core.model.IBeaconData
import com.blepulse.android.core.model.ProximityZone
import com.blepulse.android.core.sig.BluetoothSigRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.pow

object BleAdvertisementParser {

    private const val AD_TYPE_FLAGS = 0x01
    private const val AD_TYPE_MORE_16_UUID = 0x02
    private const val AD_TYPE_COMPLETE_16_UUID = 0x03
    private const val AD_TYPE_MORE_32_UUID = 0x04
    private const val AD_TYPE_COMPLETE_32_UUID = 0x05
    private const val AD_TYPE_MORE_128_UUID = 0x06
    private const val AD_TYPE_COMPLETE_128_UUID = 0x07
    private const val AD_TYPE_SHORT_NAME = 0x08
    private const val AD_TYPE_COMPLETE_NAME = 0x09
    private const val AD_TYPE_TX_POWER = 0x0A
    private const val AD_TYPE_SERVICE_DATA_16 = 0x16
    private const val AD_TYPE_SERVICE_DATA_32 = 0x20
    private const val AD_TYPE_SERVICE_DATA_128 = 0x21
    private const val AD_TYPE_MANUFACTURER_DATA = 0xFF

    private val EDDYSTONE_SERVICE_UUID = UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    fun parse(rawBytes: ByteArray?, currentRssi: Int = -60): AdvertisementRecord {
        if (rawBytes == null || rawBytes.isEmpty()) {
            return AdvertisementRecord()
        }

        var flags: Int? = null
        val serviceUuids = mutableListOf<UUID>()
        val serviceData = mutableMapOf<UUID, ByteArray>()
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        var companyName: String? = null
        var iBeaconData: IBeaconData? = null
        var eddystoneData: EddystoneData? = null

        var offset = 0
        while (offset < rawBytes.size) {
            val length = rawBytes[offset].toInt() and 0xFF
            if (length == 0) break // No more structures
            if (offset + 1 + length > rawBytes.size) break // Corrupt record

            val type = rawBytes[offset + 1].toInt() and 0xFF
            val dataOffset = offset + 2
            val dataLength = length - 1

            if (dataLength > 0) {
                when (type) {
                    AD_TYPE_FLAGS -> {
                        flags = rawBytes[dataOffset].toInt() and 0xFF
                    }
                    AD_TYPE_MORE_16_UUID, AD_TYPE_COMPLETE_16_UUID -> {
                        var p = dataOffset
                        while (p + 2 <= dataOffset + dataLength) {
                            val shortUuid = (rawBytes[p].toInt() and 0xFF) or ((rawBytes[p + 1].toInt() and 0xFF) shl 8)
                            serviceUuids.add(BluetoothSigRegistry.uuidFromShort16("%04X".format(shortUuid)))
                            p += 2
                        }
                    }
                    AD_TYPE_MORE_128_UUID, AD_TYPE_COMPLETE_128_UUID -> {
                        var p = dataOffset
                        while (p + 16 <= dataOffset + dataLength) {
                            val bb = ByteBuffer.wrap(rawBytes, p, 16).order(ByteOrder.LITTLE_ENDIAN)
                            val leastSig = bb.long
                            val mostSig = bb.long
                            serviceUuids.add(UUID(mostSig, leastSig))
                            p += 16
                        }
                    }
                    AD_TYPE_SERVICE_DATA_16 -> {
                        if (dataLength >= 2) {
                            val shortUuid = (rawBytes[dataOffset].toInt() and 0xFF) or ((rawBytes[dataOffset + 1].toInt() and 0xFF) shl 8)
                            val uuid = BluetoothSigRegistry.uuidFromShort16("%04X".format(shortUuid))
                            val payload = rawBytes.copyOfRange(dataOffset + 2, dataOffset + dataLength)
                            serviceData[uuid] = payload

                            if (uuid == EDDYSTONE_SERVICE_UUID) {
                                eddystoneData = parseEddystone(payload)
                            }
                        }
                    }
                    AD_TYPE_MANUFACTURER_DATA -> {
                        if (dataLength >= 2) {
                            val companyId = (rawBytes[dataOffset].toInt() and 0xFF) or ((rawBytes[dataOffset + 1].toInt() and 0xFF) shl 8)
                            val payload = rawBytes.copyOfRange(dataOffset + 2, dataOffset + dataLength)
                            manufacturerData[companyId] = payload
                            companyName = BluetoothSigRegistry.resolveCompanyName(companyId)

                            // Apple iBeacon: Company ID 0x004C and payload starts with 0x02, 0x15
                            if (companyId == 0x004C && payload.size >= 23 && payload[0] == 0x02.toByte() && payload[1] == 0x15.toByte()) {
                                iBeaconData = parseIBeacon(payload, currentRssi)
                            }
                        }
                    }
                }
            }
            offset += 1 + length
        }

        return AdvertisementRecord(
            rawBytes = rawBytes,
            flags = flags,
            serviceUuids = serviceUuids,
            serviceData = serviceData,
            manufacturerData = manufacturerData,
            companyName = companyName,
            iBeaconData = iBeaconData,
            eddystoneData = eddystoneData
        )
    }

    private fun parseIBeacon(payload: ByteArray, currentRssi: Int): IBeaconData? {
        if (payload.size < 23) return null

        val bb = ByteBuffer.wrap(payload, 2, 16).order(ByteOrder.BIG_ENDIAN)
        val uuid = UUID(bb.long, bb.long)

        val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)
        val measuredPower = payload[22].toInt()

        val distance = calculateDistance(measuredPower, currentRssi)
        val zone = when {
            distance < 0.5 -> ProximityZone.IMMEDIATE
            distance < 3.0 -> ProximityZone.NEAR
            else -> ProximityZone.FAR
        }

        return IBeaconData(
            uuid = uuid,
            major = major,
            minor = minor,
            measuredPower = measuredPower,
            estimatedDistanceMeters = distance,
            proximity = zone
        )
    }

    private fun parseEddystone(payload: ByteArray): EddystoneData {
        if (payload.isEmpty()) return EddystoneData.Unknown(0, payload)

        val frameType = payload[0].toInt() and 0xFF
        return when (frameType) {
            0x00 -> { // UID
                if (payload.size >= 18) {
                    val txPower = payload[1].toInt()
                    val namespace = payload.copyOfRange(2, 12).joinToString("") { "%02X".format(it) }
                    val instance = payload.copyOfRange(12, 18).joinToString("") { "%02X".format(it) }
                    EddystoneData.Uid(namespace, instance, txPower)
                } else {
                    EddystoneData.Unknown(frameType, payload)
                }
            }
            0x10 -> { // URL
                if (payload.size >= 3) {
                    val txPower = payload[1].toInt()
                    val schemeIndex = payload[2].toInt() and 0xFF
                    val scheme = when (schemeIndex) {
                        0x00 -> "http://www."
                        0x01 -> "https://www."
                        0x02 -> "http://"
                        0x03 -> "https://"
                        else -> ""
                    }

                    val sb = StringBuilder(scheme)
                    for (i in 3 until payload.size) {
                        val b = payload[i].toInt() and 0xFF
                        val suffix = when (b) {
                            0x00 -> ".com/"
                            0x01 -> ".org/"
                            0x02 -> ".edu/"
                            0x03 -> ".net/"
                            0x04 -> ".info/"
                            0x05 -> ".biz/"
                            0x06 -> ".gov/"
                            0x07 -> ".com"
                            0x08 -> ".org"
                            0x09 -> ".edu"
                            0x0a -> ".net"
                            0x0b -> ".info"
                            0x0c -> ".biz"
                            0x0d -> ".gov"
                            else -> b.toChar().toString()
                        }
                        sb.append(suffix)
                    }
                    EddystoneData.Url(sb.toString(), txPower)
                } else {
                    EddystoneData.Unknown(frameType, payload)
                }
            }
            0x20 -> { // TLM
                if (payload.size >= 14) {
                    val version = payload[1].toInt() and 0xFF
                    val voltageMv = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                    val tempInt = payload[4].toInt()
                    val tempFrac = payload[5].toInt() and 0xFF
                    val tempCelsius = tempInt.toFloat() + (tempFrac.toFloat() / 256.0f)
                    val pduCount = ByteBuffer.wrap(payload, 6, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val uptimeDeciseconds = ByteBuffer.wrap(payload, 10, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val uptimeSeconds = uptimeDeciseconds / 10L

                    EddystoneData.Tlm(version, voltageMv, tempCelsius, pduCount, uptimeSeconds)
                } else {
                    EddystoneData.Unknown(frameType, payload)
                }
            }
            else -> EddystoneData.Unknown(frameType, payload)
        }
    }

    /**
     * Calculates beacon distance using the standard log-distance path loss formula:
     * distance = 10 ^ ((measuredPower - rssi) / (10 * n))
     * with an empirical polynomial correction.
     */
    fun calculateDistance(txPower: Int, rssi: Int, pathLossExponent: Double = 2.4): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi.toDouble() / txPower.toDouble()
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            (0.89976) * ratio.pow(7.7095) + 0.111
        }.coerceAtLeast(0.05)
    }

    fun applyEmaFilter(currentEma: Double, newRssi: Int, alpha: Double = 0.25): Double {
        return (alpha * newRssi) + ((1.0 - alpha) * currentEma)
    }
}
