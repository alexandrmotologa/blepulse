package com.blepulse.android.core.ble

import android.content.Context
import com.blepulse.android.core.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class HardwareMode {
    REAL,
    VIRTUAL
}

class BleRepository(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val realProvider = AndroidBleHardwareProvider(context)
    private val virtualProvider = VirtualBleHardwareProvider()

    private val _hardwareMode = MutableStateFlow(HardwareMode.VIRTUAL)
    val hardwareMode: StateFlow<HardwareMode> = _hardwareMode.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var currentConnection: BleConnection? = null
    private val _activeConnection = MutableStateFlow<BleConnection?>(null)
    val activeConnection: StateFlow<BleConnection?> = _activeConnection.asStateFlow()

    private val activeProvider: BleHardwareProvider
        get() = if (_hardwareMode.value == HardwareMode.REAL) realProvider else virtualProvider

    init {
        // Forward state from active provider
        observeProvider(virtualProvider)
    }

    private fun observeProvider(provider: BleHardwareProvider) {
        provider.isScanning.onEach { _isScanning.value = it }.launchIn(scope)
        provider.isAdvertising.onEach { _isAdvertising.value = it }.launchIn(scope)
        provider.discoveredDevices.onEach { _discoveredDevices.value = it }.launchIn(scope)
    }

    fun setHardwareMode(mode: HardwareMode) {
        if (_hardwareMode.value == mode) return
        stopScan()
        disconnect()
        _hardwareMode.value = mode
        observeProvider(activeProvider)
    }

    fun startScan(filter: ScanFilterConfig = ScanFilterConfig()) {
        activeProvider.startScan(filter)
    }

    fun stopScan() {
        activeProvider.stopScan()
    }

    fun connect(address: String): BleConnection {
        currentConnection?.disconnect()
        val conn = activeProvider.connect(address)
        currentConnection = conn
        _activeConnection.value = conn
        return conn
    }

    fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
        _activeConnection.value = null
    }

    fun startAdvertising(config: CustomAdvertiseConfig): Boolean {
        return activeProvider.startAdvertising(config)
    }

    fun stopAdvertising() {
        activeProvider.stopAdvertising()
    }
}
