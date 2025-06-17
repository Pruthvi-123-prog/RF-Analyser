package com.example.wallhack

import android.content.Context
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.telephony.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

class MultiSensorDetector(private val context: Context) {
    enum class DetectionType {
        LIVING_BEING,
        ERROR
    }

    data class DetectionResult(
        val type: DetectionType,
        val angle: Float,
        val distance: Float,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var isMonitoring = false
    private var onDetectionListener: ((DetectionResult) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun setOnDetectionListener(listener: (DetectionResult) -> Unit) {
        onDetectionListener = listener
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            startSignalMonitoring()
        }
    }

    private fun startSignalMonitoring() {
        scope.launch {
            while (isMonitoring) {
                val wifiRssi = wifiManager.connectionInfo.rssi
                val bluetoothRssi = getBluetoothRssi()
                val rfStrength = getRFStrength()

                processSignal(wifiRssi, bluetoothRssi, rfStrength)
                delay(1000)
            }
        }
    }

    private fun getBluetoothRssi(): Int {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        var rssi = -1
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                rssi = result.rssi
            }
        }
        scanner?.startScan(scanCallback)
        return rssi
    }

    private fun getRFStrength(): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            val strength = signalStrength.getCellSignalStrengths()
                                .maxOfOrNull { it.dbm } ?: return
                            processSignal(strength, -1, -1)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("RF", "Error monitoring RF: ${e.message}")
            }
        }
        return -1
    }

    private fun processSignal(wifiRssi: Int, bluetoothRssi: Int, rfStrength: Int) {
        val normalizedStrength = abs(wifiRssi + bluetoothRssi + rfStrength).toFloat()
        val angle = (System.currentTimeMillis() % 360).toFloat()
        val distance = (normalizedStrength / SIGNAL_THRESHOLD * 50f).coerceIn(0f, 100f)
        val confidence = (normalizedStrength / 100f).coerceIn(0f, 1f)

        if (confidence > 0.7) { // Threshold for living beings
            onDetectionListener?.invoke(
                DetectionResult(
                    type = DetectionType.LIVING_BEING,
                    angle = angle,
                    distance = distance,
                    confidence = confidence
                )
            )
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        scope.cancel()
    }

    companion object {
        private const val SIGNAL_THRESHOLD = 70
    }
}