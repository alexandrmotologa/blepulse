package com.blepulse.android.core.pcap

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates standard Libpcap 2.4 capture files with LINKTYPE_BLUETOOTH_LE_LL (251).
 * Wireshark natively parses this format to dissect BLE advertising channels,
 * scan responses, PDU types, and GATT transactions.
 */
object PcapExporter {

    private const val PCAP_MAGIC_NUMBER = 0xA1B2C3D4.toInt()
    private const val PCAP_VERSION_MAJOR: Short = 2
    private const val PCAP_VERSION_MINOR: Short = 4
    private const val PCAP_THISZONE = 0
    private const val PCAP_SIGFIGS = 0
    private const val PCAP_SNAPLEN = 65535
    // DLT_BLUETOOTH_LE_LL (Link-Layer Bluetooth LE packet)
    private const val LINKTYPE_BLUETOOTH_LE_LL = 251

    // BLE Access Address for Advertising channels (0x8E89BED6 in Little-Endian)
    private val ADV_ACCESS_ADDRESS = byteArrayOf(0xD6.toByte(), 0xBE.toByte(), 0x89.toByte(), 0x8E.toByte())

    /**
     * Converts a list of scanned BleDevices into Libpcap 2.4 format.
     */
    fun exportAdvertisements(devices: List<com.blepulse.android.core.model.BleDevice>): ByteArray {
        val packets = devices.map { dev ->
            CapturedBlePacket(
                timestampMs = dev.lastSeenTimestamp,
                pduType = if (dev.isConnectable) 0x00 else 0x02,
                isRandomAddress = true,
                macAddressBytes = CapturedBlePacket.parseMacToBytes(dev.address),
                rssi = dev.rssi,
                data = dev.advertisementRecord.rawBytes
            )
        }
        return buildPcapFile(packets)
    }

    /**
     * Builds a complete .pcap binary file from captured advertisement and GATT packets.
     */
    fun buildPcapFile(packets: List<CapturedBlePacket>): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Libpcap Global Header (24 bytes)
        dos.writeInt(PCAP_MAGIC_NUMBER)
        dos.writeShort(PCAP_VERSION_MAJOR.toInt())
        dos.writeShort(PCAP_VERSION_MINOR.toInt())
        dos.writeInt(PCAP_THISZONE)
        dos.writeInt(PCAP_SIGFIGS)
        dos.writeInt(PCAP_SNAPLEN)
        dos.writeInt(LINKTYPE_BLUETOOTH_LE_LL)

        // 2. Packet Records
        for (pkt in packets) {
            val payload = buildPacketPayload(pkt)
            val sec = (pkt.timestampMs / 1000).toInt()
            val usec = ((pkt.timestampMs % 1000) * 1000).toInt()
            val caplen = payload.size
            val origlen = payload.size

            // Packet Record Header (16 bytes)
            dos.writeInt(sec)
            dos.writeInt(usec)
            dos.writeInt(caplen)
            dos.writeInt(origlen)

            // Packet Data
            dos.write(payload)
        }

        dos.flush()
        return baos.toByteArray()
    }

    private fun buildPacketPayload(pkt: CapturedBlePacket): ByteArray {
        val baos = ByteArrayOutputStream()

        // Bluetooth LE Link Layer pseudo-header for Wireshark (BTLE LL):
        // Access Address (4 bytes)
        baos.write(ADV_ACCESS_ADDRESS)

        // PDU Header: PDU Type (4 bits), RFU (2 bits), ChSel (1 bit), TxAdd (1 bit) + Length (8 bits)
        val pduType = pkt.pduType and 0x0F
        val txAdd = if (pkt.isRandomAddress) 0x40 else 0x00
        val headerByte0 = (pduType or txAdd).toByte()
        val payloadLength = (pkt.macAddressBytes.size + pkt.data.size).toByte()

        baos.write(headerByte0.toInt())
        baos.write(payloadLength.toInt())

        // Advertiser Address (6 bytes LE)
        baos.write(pkt.macAddressBytes)

        // Payload data
        baos.write(pkt.data)

        // 3-byte CRC placeholder
        baos.write(byteArrayOf(0x00, 0x00, 0x00))

        return baos.toByteArray()
    }

    data class CapturedBlePacket(
        val timestampMs: Long = System.currentTimeMillis(),
        val pduType: Int = 0x00, // 0 = ADV_IND, 2 = ADV_NONCONN_IND, 4 = SCAN_RSP, 6 = ADV_SCAN_IND
        val isRandomAddress: Boolean = true,
        val macAddressBytes: ByteArray,
        val rssi: Int = -60,
        val data: ByteArray
    ) {
        companion object {
            fun parseMacToBytes(mac: String): ByteArray {
                val parts = mac.split(":")
                if (parts.size != 6) return ByteArray(6)
                // Bluetooth MAC in LE over the air is reversed
                return ByteArray(6) { i ->
                    parts[5 - i].toIntOrNull(16)?.toByte() ?: 0
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CapturedBlePacket
            return timestampMs == other.timestampMs &&
                    pduType == other.pduType &&
                    macAddressBytes.contentEquals(other.macAddressBytes) &&
                    data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = timestampMs.hashCode()
            result = 31 * result + pduType
            result = 31 * result + macAddressBytes.contentHashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
