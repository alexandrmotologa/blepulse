package com.blepulse.android.core.sig

import java.util.UUID

object BluetoothSigRegistry {

    private const val BLUETOOTH_BASE_UUID_POSTFIX = "-0000-1000-8000-00805f9b34fb"

    fun uuidFromShort16(short16Hex: String): UUID {
        val padded = short16Hex.padStart(4, '0')
        return UUID.fromString("0000$padded$BLUETOOTH_BASE_UUID_POSTFIX")
    }

    private val STANDARD_SERVICES = mapOf(
        uuidFromShort16("1800") to "Generic Access",
        uuidFromShort16("1801") to "Generic Attribute",
        uuidFromShort16("1802") to "Immediate Alert",
        uuidFromShort16("1803") to "Link Loss",
        uuidFromShort16("1804") to "Tx Power",
        uuidFromShort16("1805") to "Current Time",
        uuidFromShort16("1806") to "Reference Time Update",
        uuidFromShort16("1807") to "Next DST Change",
        uuidFromShort16("1808") to "Glucose",
        uuidFromShort16("1809") to "Health Thermometer",
        uuidFromShort16("180A") to "Device Information",
        uuidFromShort16("180D") to "Heart Rate",
        uuidFromShort16("180E") to "Phone Alert Status",
        uuidFromShort16("180F") to "Battery Service",
        uuidFromShort16("1810") to "Blood Pressure",
        uuidFromShort16("1811") to "Alert Notification",
        uuidFromShort16("1812") to "Human Interface Device",
        uuidFromShort16("1813") to "Scan Parameters",
        uuidFromShort16("1814") to "Running Speed and Cadence",
        uuidFromShort16("1815") to "Automation IO",
        uuidFromShort16("1816") to "Cycling Speed and Cadence",
        uuidFromShort16("1818") to "Cycling Power",
        uuidFromShort16("1819") to "Location and Navigation",
        uuidFromShort16("181A") to "Environmental Sensing",
        uuidFromShort16("181B") to "Body Composition",
        uuidFromShort16("181C") to "User Data",
        uuidFromShort16("181D") to "Weight Scale",
        uuidFromShort16("181E") to "Bond Management",
        uuidFromShort16("181F") to "Continuous Glucose Monitoring",
        uuidFromShort16("1820") to "Internet Protocol Support",
        uuidFromShort16("1821") to "Indoor Positioning",
        uuidFromShort16("1822") to "Pulse Oximeter",
        uuidFromShort16("1823") to "HTTP Proxy",
        uuidFromShort16("1824") to "Transport Discovery",
        uuidFromShort16("1825") to "Object Transfer",
        uuidFromShort16("1826") to "Fitness Machine",
        uuidFromShort16("1827") to "Mesh Provisioning",
        uuidFromShort16("1828") to "Mesh Proxy",
        // Popular Vendor Services
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e") to "Nordic UART Service (NUS)",
        UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb") to "Google Eddystone Beacon",
        UUID.fromString("0000fd6f-0000-1000-8000-00805f9b34fb") to "Exposure Notification",
        UUID.fromString("f000aa00-0451-4000-b000-000000000000") to "TI SensorTag IR Temp",
        UUID.fromString("f000aa10-0451-4000-b000-000000000000") to "TI SensorTag Accelerometer",
        UUID.fromString("f000aa20-0451-4000-b000-000000000000") to "TI SensorTag Humidity"
    )

    private val STANDARD_CHARACTERISTICS = mapOf(
        uuidFromShort16("2A00") to "Device Name",
        uuidFromShort16("2A01") to "Appearance",
        uuidFromShort16("2A02") to "Peripheral Privacy Flag",
        uuidFromShort16("2A03") to "Reconnection Address",
        uuidFromShort16("2A04") to "Peripheral Preferred Connection Parameters",
        uuidFromShort16("2A05") to "Service Changed",
        uuidFromShort16("2A06") to "Alert Level",
        uuidFromShort16("2A07") to "Tx Power Level",
        uuidFromShort16("2A19") to "Battery Level",
        uuidFromShort16("2A1C") to "Temperature Measurement",
        uuidFromShort16("2A1D") to "Temperature Type",
        uuidFromShort16("2A1E") to "Intermediate Temperature",
        uuidFromShort16("2A21") to "Measurement Interval",
        uuidFromShort16("2A23") to "System ID",
        uuidFromShort16("2A24") to "Model Number String",
        uuidFromShort16("2A25") to "Serial Number String",
        uuidFromShort16("2A26") to "Firmware Revision String",
        uuidFromShort16("2A27") to "Hardware Revision String",
        uuidFromShort16("2A28") to "Software Revision String",
        uuidFromShort16("2A29") to "Manufacturer Name String",
        uuidFromShort16("2A2A") to "IEEE 11073-20601 Regulatory Cert",
        uuidFromShort16("2A37") to "Heart Rate Measurement",
        uuidFromShort16("2A38") to "Body Sensor Location",
        uuidFromShort16("2A39") to "Heart Rate Control Point",
        uuidFromShort16("2A56") to "Digital Input/Output",
        uuidFromShort16("2A58") to "Analog Input",
        uuidFromShort16("2A6E") to "Temperature (Environmental)",
        uuidFromShort16("2A6F") to "Humidity (Environmental)",
        uuidFromShort16("2A76") to "UV Index",
        uuidFromShort16("2A6D") to "Pressure (Environmental)",
        // Nordic UART Characteristics
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") to "Nordic UART RX (Write)",
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") to "Nordic UART TX (Notify)"
    )

    private val STANDARD_DESCRIPTORS = mapOf(
        uuidFromShort16("2900") to "Characteristic Extended Properties",
        uuidFromShort16("2901") to "Characteristic User Description",
        uuidFromShort16("2902") to "Client Characteristic Configuration (CCCD)",
        uuidFromShort16("2903") to "Server Characteristic Configuration",
        uuidFromShort16("2904") to "Characteristic Presentation Format",
        uuidFromShort16("2905") to "Characteristic Aggregate Format"
    )

    val CCCD_UUID: UUID = uuidFromShort16("2902")

    private val COMPANY_IDENTIFIERS = mapOf(
        0x0001 to "Nokia Mobile Phones",
        0x0002 to "Intel Corp.",
        0x0003 to "IBM Corp.",
        0x0004 to "Toshiba Corp.",
        0x0006 to "Microsoft",
        0x000A to "CSR (Qualcomm)",
        0x000D to "Texas Instruments Inc.",
        0x001D to "Qualcomm Technologies, Inc.",
        0x0030 to "STMicroelectronics",
        0x004C to "Apple, Inc.",
        0x0048 to "Cypress / Infineon Semiconductor",
        0x0059 to "Nordic Semiconductor ASA",
        0x0075 to "Samsung Electronics Co. Ltd.",
        0x0087 to "Garmin International, Inc.",
        0x00D2 to "Fitbit, Inc.",
        0x00E0 to "Google Inc.",
        0x012D to "Sony Mobile Communications Inc.",
        0x0157 to "Anki, Inc.",
        0x0171 to "Amazon.com Services LLC",
        0x01AB to "Bose Corporation",
        0x01D1 to "Tile, Inc.",
        0x02E5 to "Espressif Systems (Shanghai) Co., Ltd.",
        0x038F to "Xiaomi Inc.",
        0x0499 to "Ruuvi Innovations Ltd.",
        0x0822 to "Shenzhen Minew Technologies Co., Ltd."
    )

    fun resolveServiceName(uuid: UUID): String {
        return STANDARD_SERVICES[uuid] ?: "Custom Service (${uuid.toString().take(8)}...)"
    }

    fun isStandardService(uuid: UUID): Boolean {
        return STANDARD_SERVICES.containsKey(uuid)
    }

    fun resolveCharacteristicName(uuid: UUID): String {
        return STANDARD_CHARACTERISTICS[uuid] ?: "Custom Characteristic (${uuid.toString().take(8)}...)"
    }

    fun resolveDescriptorName(uuid: UUID): String {
        return STANDARD_DESCRIPTORS[uuid] ?: "Custom Descriptor (${uuid.toString().take(8)}...)"
    }

    fun resolveCompanyName(companyId: Int): String {
        return COMPANY_IDENTIFIERS[companyId] ?: "Company 0x${companyId.toString(16).padStart(4, '0').uppercase()}"
    }
}
