package com.example.wallhack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.*
import kotlin.random.Random
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay

// Data class for signal information
data class SignalData(
    val category: String,
    val identifier: String,
    val frequency: String,
    val signalStrength: Int,
    val estimatedRange: Float,
    val bearing: Float,
    val protocol: String,
    val bandwidth: String,
    val encryption: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var wifiManager: WifiManager
    private lateinit var telephonyManager: TelephonyManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var sensorManager: SensorManager? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval = 1500L
    
    // Real-time sensor data
    private val detectedSignals = mutableStateListOf<SignalData>()
    private var magneticField = FloatArray(3) { 0f }
    private var previousMagneticField = FloatArray(3) { 0f }
    private var gyroscopeData = FloatArray(3) { 0f }
    private var accelerometerData = FloatArray(3) { 0f }
    private var lightLevel = 0f
    private var proximityValue = 0f
    private var isScanning = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION && isScanning) {
                Log.d("WiFi", "WiFi scan results received")
                handleWifiScanResults()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isScanning) return
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let { handleBluetoothDevice(it, rssi) }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isScanning) {
                handleBleDevice(result)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            if (isScanning) {
                results.forEach { handleBleDevice(it) }
            }
        }
    }

    private val cellInfoListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in API level 31")
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            if (isScanning) {
                cellInfo?.let { handleCellularData(it) }
            }
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                performRealTimeScan()
                handler.postDelayed(this, scanInterval)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Log.d("Permissions", "All permissions granted")
            initializeHardware()
        } else {
            Log.w("Permissions", "Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for full black screen
        enableEdgeToEdge()
        
        setupHardwareComponents()
        registerAllReceivers()

        setContent {
            AdvancedSignalHunterTheme {
                var showSplash by remember { mutableStateOf(true) }
                
                if (showSplash) {
                    SplashScreenAnimation(
                        onAnimationComplete = { showSplash = false }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black // Ensure black background
                    ) {
                        RealTimeSignalInterface(
                            signals = detectedSignals.toList(),
                            onToggleScanning = { active ->
                                if (active) startRealTimeScanning()
                                else stopRealTimeScanning()
                            }
                        )
                    }
                }
            }
        }
        
        requestPermissionsAndInitialize()
    }

    private fun enableEdgeToEdge() {
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    private fun setupHardwareComponents() {
        try {
            wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            
            Log.d("Hardware", "All components initialized successfully")
            Log.d("WiFi", "WiFi enabled: ${wifiManager.isWifiEnabled}")
        } catch (e: Exception) {
            Log.e("Hardware", "Critical hardware initialization failure", e)
        }
    }

    private fun registerAllReceivers() {
        try {
            val wifiFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            registerReceiver(wifiScanReceiver, wifiFilter)
            
            val bluetoothFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(bluetoothReceiver, bluetoothFilter)
            
            Log.d("Receivers", "All broadcast receivers registered")
        } catch (e: Exception) {
            Log.e("Receivers", "Failed to register receivers", e)
        }
    }

    private fun requestPermissionsAndInitialize() {
        if (hasAllPermissions()) {
            initializeHardware()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun initializeHardware() {
        try {
            // Register cellular listener
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                telephonyManager.listen(cellInfoListener, PhoneStateListener.LISTEN_CELL_INFO)
            }
            
            // Start all sensors
            magnetometer?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("Sensors", "Magnetometer registered")
            }
            gyroscope?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("Sensors", "Gyroscope registered")
            }
            accelerometer?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("Sensors", "Accelerometer registered")
            }
            lightSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("Sensors", "Light sensor registered")
            }
            proximitySensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("Sensors", "Proximity sensor registered")
            }
            
        } catch (e: Exception) {
            Log.e("Hardware", "Error initializing hardware", e)
        }
    }

    private fun startRealTimeScanning() {
        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
            return
        }

        isScanning = true
        detectedSignals.clear()
        
        handler.removeCallbacks(scanRunnable)
        handler.post(scanRunnable)
        
        Log.d("Scan", "Real-time scanning started")
    }
    
    private fun stopRealTimeScanning() {
        isScanning = false
        handler.removeCallbacks(scanRunnable)
        
        try {
            bluetoothAdapter?.cancelDiscovery()
            bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            Log.e("Scan", "Error stopping scans", e)
        }
        
        Log.d("Scan", "Real-time scanning stopped")
    }

    private fun performRealTimeScan() {
        if (!isScanning) return
        
        try {
            val currentTime = System.currentTimeMillis()
            // Remove signals older than 8 seconds
            detectedSignals.removeAll { currentTime - it.timestamp > 8000 }
            
            // Real WiFi scan
            Log.d("Scan", "Starting WiFi scan...")
            if (wifiManager.isWifiEnabled) {
                val scanStarted = wifiManager.startScan()
                Log.d("WiFi", "WiFi scan started: $scanStarted")
            }
            
            // Real Bluetooth scan
            bluetoothAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    if (!adapter.isDiscovering) {
                        adapter.startDiscovery()
                        Log.d("Bluetooth", "Started Bluetooth discovery")
                    }
                    bluetoothLeScanner?.startScan(bleScanCallback)
                }
            }
            
            // Real cellular info
            getCellularInfo()
            
            // Real RF/EMF detection with lower thresholds
            generateRFSignals()
            
            // Only show EMF if actually detected
            analyzeRealTimeEMF()
            
            // Real GPS detection
            detectGPSSignals()
            
        } catch (e: SecurityException) {
            Log.e("Scan", "Security exception during scan", e)
        } catch (e: Exception) {
            Log.e("Scan", "Error during scan", e)
        }
    }

    private fun handleWifiScanResults() {
        try {
            if (!wifiManager.isWifiEnabled) {
                Log.w("WiFi", "WiFi is disabled")
                return
            }
            
            val results = wifiManager.scanResults
            Log.d("WiFi", "WiFi scan found ${results.size} networks")
            
            // Remove old WiFi signals
            detectedSignals.removeAll { it.category == "WiFi Network" }
            
            results.forEach { result: WifiScanResult ->
                if (result.SSID.isNotEmpty()) {
                    // Enhanced WiFi analysis with more details
                    val frequency = when {
                        result.frequency < 2500 -> "2.4GHz"
                        result.frequency < 6000 -> "5GHz"
                        else -> "6GHz"
                    }
                    
                    val channel = when {
                        result.frequency in 2412..2484 -> (result.frequency - 2412) / 5 + 1
                        result.frequency in 5170..5825 -> (result.frequency - 5000) / 5
                        result.frequency in 5955..7115 -> (result.frequency - 5950) / 5
                        else -> 0
                    }
                    
                    val security = when {
                        result.capabilities.contains("WPA3") -> "WPA3"
                        result.capabilities.contains("WPA2") -> "WPA2"
                        result.capabilities.contains("WPA") -> "WPA"
                        result.capabilities.contains("WEP") -> "WEP"
                        else -> "Open"
                    }
                    
                    val bandwidth = when {
                        result.frequency > 5000 -> "80MHz"
                        else -> "40MHz"
                    }
                    
                    val signal = SignalData(
                        category = "WiFi Network",
                        identifier = if (result.SSID.isNotBlank()) result.SSID else "Hidden Network",
                        frequency = "$frequency (Ch.$channel)",
                        signalStrength = result.level,
                        estimatedRange = calculateDistance(result.level, result.frequency),
                        bearing = calculateRealTimeBearing(),
                        protocol = "802.11${if (result.frequency > 5000) "ac" else "n"}",
                        bandwidth = bandwidth,
                        encryption = security,
                        timestamp = System.currentTimeMillis()
                    )
                    detectedSignals.add(signal)
                    Log.d("WiFi", "WiFi network detected: ${signal.identifier} at ${signal.signalStrength}dBm")
                }
            }
            
        } catch (e: Exception) {
            Log.e("WiFi", "Error processing WiFi results", e)
        }
    }

    private fun handleBluetoothDevice(device: BluetoothDevice, rssi: Int) {
        try {
            val deviceName = device.name ?: "Unknown Bluetooth Device"
            
            if (!detectedSignals.any { it.identifier == deviceName && it.category == "Bluetooth Classic" }) {
                val signal = SignalData(
                    category = "Bluetooth Classic",
                    identifier = deviceName,
                    frequency = "2.4 GHz",
                    signalStrength = rssi,
                    estimatedRange = calculateDistance(rssi, 2400),
                    bearing = calculateRealTimeBearing(),
                    protocol = "Bluetooth",
                    bandwidth = "1-3 Mbps",
                    encryption = "Unknown",
                    timestamp = System.currentTimeMillis()
                )
                detectedSignals.add(signal)
                Log.d("Bluetooth", "Real Bluetooth device: $deviceName")
            }
        } catch (e: SecurityException) {
            Log.e("Bluetooth", "Permission denied", e)
        }
    }

    private fun handleBleDevice(result: ScanResult) {
        try {
            val deviceName = result.device.name ?: "Unknown BLE Device"
            
            if (!detectedSignals.any { it.identifier == deviceName && it.category == "Bluetooth LE" }) {
                val signal = SignalData(
                    category = "Bluetooth LE",
                    identifier = deviceName,
                    frequency = "2.4 GHz", 
                    signalStrength = result.rssi,
                    estimatedRange = calculateDistance(result.rssi, 2400),
                    bearing = calculateRealTimeBearing(),
                    protocol = "BLE",
                    bandwidth = "1 Mbps",
                    encryption = "Unknown",
                    timestamp = System.currentTimeMillis()
                )
                detectedSignals.add(signal)
                Log.d("BLE", "Real BLE device: $deviceName")
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "Permission denied", e)
        }
    }

    private fun getCellularInfo() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            
            val cellInfos = telephonyManager.allCellInfo
            handleCellularData(cellInfos ?: emptyList())
            
        } catch (e: SecurityException) {
            Log.e("Cellular", "Permission denied accessing cellular info", e)
        } catch (e: Exception) {
            Log.e("Cellular", "Error getting cellular info", e)
        }
    }

    private fun handleCellularData(cellInfos: List<CellInfo>) {
        try {
            detectedSignals.removeAll { it.category == "Cellular Tower" }
            
            cellInfos.forEach { cellInfo ->
                when (cellInfo) {
                    is CellInfoLte -> {
                        val identity = cellInfo.cellIdentity as CellIdentityLte
                        val strength = cellInfo.cellSignalStrength as CellSignalStrengthLte
                        
                        val operatorName = identity.operatorAlphaLong?.toString() ?: "LTE Tower"
                        val cellId = identity.ci
                        
                        val signal = SignalData(
                            category = "Cellular Tower",
                            identifier = "$operatorName (Cell: $cellId)",
                            frequency = "${identity.earfcn} MHz",
                            signalStrength = strength.dbm,
                            estimatedRange = calculateCellularDistance(strength.dbm),
                            bearing = calculateRealTimeBearing(),
                            protocol = "LTE",
                            bandwidth = "20 MHz",
                            encryption = "AES",
                            timestamp = System.currentTimeMillis()
                        )
                        detectedSignals.add(signal)
                    }
                    
                    is CellInfoGsm -> {
                        val identity = cellInfo.cellIdentity as CellIdentityGsm
                        val strength = cellInfo.cellSignalStrength as CellSignalStrengthGsm
                        
                        val operatorName = identity.operatorAlphaLong?.toString() ?: "GSM Tower"
                        val cellId = identity.cid
                        
                        val signal = SignalData(
                            category = "Cellular Tower",
                            identifier = "$operatorName (Cell: $cellId)",
                            frequency = "${identity.arfcn} MHz",
                            signalStrength = strength.dbm,
                            estimatedRange = calculateCellularDistance(strength.dbm),
                            bearing = calculateRealTimeBearing(),
                            protocol = "GSM",
                            bandwidth = "200 kHz",
                            encryption = "A5",
                            timestamp = System.currentTimeMillis()
                        )
                        detectedSignals.add(signal)
                    }
                    
                    is CellInfoWcdma -> {
                        val identity = cellInfo.cellIdentity as CellIdentityWcdma
                        val strength = cellInfo.cellSignalStrength as CellSignalStrengthWcdma
                        
                        val operatorName = identity.operatorAlphaLong?.toString() ?: "3G Tower"
                        val cellId = identity.cid
                        
                        val signal = SignalData(
                            category = "Cellular Tower",
                            identifier = "$operatorName (Cell: $cellId)",
                            frequency = "${identity.uarfcn} MHz",
                            signalStrength = strength.dbm,
                            estimatedRange = calculateCellularDistance(strength.dbm),
                            bearing = calculateRealTimeBearing(),
                            protocol = "WCDMA",
                            bandwidth = "5 MHz",
                            encryption = "KASUMI",
                            timestamp = System.currentTimeMillis()
                        )
                        detectedSignals.add(signal)
                    }
                }
            }
            
            Log.d("Cellular", "Added ${cellInfos.size} cellular towers")
            
        } catch (e: Exception) {
            Log.e("Cellular", "Error processing cellular data", e)
        }
    }

    private fun generateRFSignals() {
        try {
            // Remove old RF signals
            detectedSignals.removeAll { it.category == "RF Signal" }
            
            val magneticMagnitude = sqrt(
                magneticField[0] * magneticField[0] +
                magneticField[1] * magneticField[1] +
                magneticField[2] * magneticField[2]
            )
            
            // LOWERED threshold from 60 to 45 for better RF detection
            if (magneticMagnitude > 45) { // More sensitive detection
                
                val fieldStrength = calculateFieldStrength(magneticMagnitude)
                val strengthCategory = when {
                    magneticMagnitude > 80 -> "Strong"  // Lowered from 100
                    magneticMagnitude > 60 -> "Average" // Lowered from 80
                    else -> "Weak"
                }
                
                val signal = SignalData(
                    category = "RF Signal",
                    identifier = "RF Activity ($strengthCategory)",
                    frequency = "Unknown",
                    signalStrength = fieldStrength,
                    estimatedRange = (magneticMagnitude * 0.3f).coerceIn(1f, 100f),
                    bearing = calculateRealTimeBearing(),
                    protocol = "Unknown",
                    bandwidth = "Unknown",
                    encryption = "Unknown",
                    timestamp = System.currentTimeMillis()
                )
                detectedSignals.add(signal)
                Log.d("RF", "Real RF activity detected: ${strengthCategory} at ${magneticMagnitude}µT")
            }
            
        } catch (e: Exception) {
            Log.e("RF", "Error detecting RF signals", e)
        }
    }

    private fun analyzeRealTimeEMF() {
        try {
            val magnitude = sqrt(
                magneticField[0] * magneticField[0] +
                magneticField[1] * magneticField[1] + 
                magneticField[2] * magneticField[2]
            )
            
            // Remove old EMF signals
            detectedSignals.removeAll { it.category == "EMF Source" }
            
            // LOWERED threshold from 70 to 55 for better EMF detection
            if (magnitude > 55) {
                
                val fieldVariation = calculateEMFVariation()
                
                // LOWERED variation threshold from 3.0f to 2.0f for more sensitivity
                if (fieldVariation > 2.0f) { 
                    val signal = SignalData(
                        category = "EMF Source", 
                        identifier = "EMF Activity (${String.format("%.1f", magnitude)}µT)",
                        frequency = "Unknown",
                        signalStrength = convertMagneticToDBm(magnitude),
                        estimatedRange = (magnitude * 0.5f).coerceIn(1f, 50f),
                        bearing = calculateRealTimeBearing(),
                        protocol = "EMF",
                        bandwidth = "Unknown",
                        encryption = "None",
                        timestamp = System.currentTimeMillis()
                    )
                    detectedSignals.add(signal)
                    Log.d("EMF", "Real EMF activity detected: ${magnitude}µT, variation: ${fieldVariation}")
                }
            }
            
        } catch (e: Exception) {
            Log.e("EMF", "Error analyzing EMF", e)
        }
    }

    private fun detectGPSSignals() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                
                if (gpsEnabled) {
                    // Only show GPS if actually receiving signals
                    val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    
                    if (lastKnownLocation != null) {
                        detectedSignals.removeAll { it.category == "GPS" }
                        
                        val signal = SignalData(
                            category = "GPS",
                            identifier = "GPS Satellite Signal",
                            frequency = "1.575 GHz",
                            signalStrength = -130, // GPS signals are very weak
                            estimatedRange = 20200000f, // Average GPS satellite distance
                            bearing = calculateRealTimeBearing(),
                            protocol = "GPS L1",
                            bandwidth = "2.046 MHz",
                            encryption = "C/A Code",
                            timestamp = System.currentTimeMillis()
                        )
                        detectedSignals.add(signal)
                        Log.d("GPS", "Real GPS signal detected")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GPS", "Error detecting GPS", e)
        }
    }

    private fun calculateEMFVariation(): Float {
        val currentVariation = sqrt(
            (magneticField[0] - previousMagneticField[0]).pow(2) +
            (magneticField[1] - previousMagneticField[1]).pow(2) +
            (magneticField[2] - previousMagneticField[2]).pow(2)
        )
        previousMagneticField = magneticField.clone()
        return currentVariation
    }

    private fun convertMagneticToDBm(magneticMicroTesla: Float): Int {
        // Convert magnetic field strength to equivalent RF power
        val normalizedField = (magneticMicroTesla - 50) / 50.0
        return (-90 + (normalizedField * 40)).toInt().coerceIn(-120, -20)
    }

    private fun calculateFieldStrength(magneticMagnitude: Float): Int {
        // Convert magnetic field to approximate RF signal strength
        val normalizedField = (magneticMagnitude - 50) / 100.0
        return (-100 + (normalizedField * 50)).toInt().coerceIn(-120, -30)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticField = event.values.clone()
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeData = event.values.clone()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerData = event.values.clone()
            }
            Sensor.TYPE_LIGHT -> {
                lightLevel = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityValue = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateRealTimeBearing(): Float {
        return try {
            if (gyroscopeData.any { it != 0f }) {
                val azimuth = atan2(gyroscopeData[1].toDouble(), gyroscopeData[0].toDouble()) * 180.0 / PI
                (azimuth.toFloat() + 360f) % 360f
            } else {
                Random.nextFloat() * 360f
            }
        } catch (e: Exception) {
            Random.nextFloat() * 360f
        }
    }

    private fun calculateDistance(rssi: Int, frequency: Int): Float {
        return try {
            val freqMHz = frequency / 1000000.0
            val distance = 10.0.pow((-rssi - 20 * log10(freqMHz) + 27.55) / 20.0).toFloat()
            distance.coerceIn(1f, 1000f)
        } catch (e: Exception) {
            50f
        }
    }
    
    private fun calculateCellularDistance(rssi: Int): Float {
        return try {
            val distance = 10.0.pow((-rssi + 100) / 20.0).toFloat() * 100f
            distance.coerceIn(100f, 50000f)
        } catch (e: Exception) {
            5000f
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopRealTimeScanning()
            sensorManager?.unregisterListener(this)
            telephonyManager.listen(cellInfoListener, PhoneStateListener.LISTEN_NONE)
            unregisterReceiver(wifiScanReceiver)
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e("Cleanup", "Error during cleanup", e)
        }
    }
}

// Signal classification function
fun getSignalColor(category: String): Color {
    return when (category) {
        "WiFi Network" -> Color(0xFF00E676)        // Bright Green
        "Bluetooth Classic" -> Color(0xFF2196F3)   // Blue
        "Bluetooth LE" -> Color(0xFF03DAC6)        // Cyan
        "EMF Source" -> Color(0xFFFF5722)          // Red-Orange
        "Cellular Tower" -> Color(0xFF9C27B0)      // Purple
        "RF Signal" -> Color(0xFFFFEB3B)           // Yellow
        "GPS" -> Color(0xFF00BCD4)                 // Light Blue
        else -> Color(0xFFFFFFFF)                  // White
    }
}

// UI Theme
@Composable
fun AdvancedSignalHunterTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF00E676),
        secondary = Color(0xFF1DE9B6),
        tertiary = Color(0xFF64FFDA),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF121212),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

// Main Interface
@Composable
fun RealTimeSignalInterface(
    signals: List<SignalData>,
    onToggleScanning: (Boolean) -> Unit
) {
    var activeScanning by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pure AMOLED black background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            RealTimeControlHeader(
                signalCount = signals.size,
                isScanning = activeScanning,
                onScanToggle = { 
                    activeScanning = !activeScanning
                    onToggleScanning(activeScanning)
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            RealTimeDisplay(
                signals = signals,
                selectedCategory = "ALL",
                isScanning = activeScanning,
                onCategoryChange = { },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Control Header
@Composable
fun RealTimeControlHeader(
    signalCount: Int,
    isScanning: Boolean,
    onScanToggle: () -> Unit
) {
    val glowAnimation by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isScanning) glowAnimation else 0.2f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = if (isScanning) glowAnimation else 0.2f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RF-ANALYSER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$signalCount SIGNALS DETECTED",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    )
                    
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
            
            IconButton(
                onClick = onScanToggle,
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = if (isScanning) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        },
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isScanning) "Stop Scan" else "Start Scan",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Real-Time Display
@Composable
fun RealTimeDisplay(
    signals: List<SignalData>,
    selectedCategory: String,
    isScanning: Boolean,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        PulseRadarDisplay(
            signals = signals.filter { selectedCategory == "ALL" || it.category == selectedCategory },
            isScanning = isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LiveSignalAnalysisView(
            signals = signals,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
        )
    }
}

// Pulse Radar Display with Green Theme
@Composable
fun PulseRadarDisplay(
    signals: List<SignalData>,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    
    // Radar sweep animation
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // Simple fade animation for all signals
    val fadeAnimation by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fade"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black // Complete OLED black
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f)) // Green border
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val maxRadius = size.minDimension / 2 - 10.dp.toPx()

                // Draw radar grid in GREEN
                for (i in 1..5) {
                    val radius = maxRadius * i / 5
                    val alpha = (6 - i) * 0.15f
                    drawCircle(
                        color = Color(0xFF00E676).copy(alpha = alpha), // Green radar grid
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        style = Stroke(width = (1.5f + i * 0.2f).dp.toPx())
                    )
                }
                
                // Draw direction lines in GREEN
                val directions = (0 until 360 step 45).map { it.toFloat() }
                directions.forEach { angleValue ->
                    val angleRad = Math.toRadians(angleValue.toDouble())
                    val endX = centerX + maxRadius * cos(angleRad).toFloat()
                    val endY = centerY + maxRadius * sin(angleRad).toFloat()
                    
                    val lineWidth = if (angleValue % 90f == 0f) 2.dp.toPx() else 1.dp.toPx()
                    val alpha = if (angleValue % 90f == 0f) 0.4f else 0.2f
                    
                    drawLine(
                        color = Color(0xFF00E676).copy(alpha = alpha), // Green direction lines
                        start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = lineWidth
                    )
                }
                
                if (isScanning) {
                    // Draw radar sweep in GREEN
                    val sweepPath = Path().apply {
                        moveTo(centerX, centerY)
                        arcTo(
                            rect = Rect(
                                left = centerX - maxRadius,
                                top = centerY - maxRadius,
                                right = centerX + maxRadius,
                                bottom = centerY + maxRadius
                            ),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = sweepAngle,
                            forceMoveTo = false
                        )
                        lineTo(centerX, centerY)
                    }
                    
                    drawPath(
                        path = sweepPath,
                        color = Color(0xFF00E676).copy(alpha = 0.2f), // Green sweep
                        style = Fill
                    )
                }
                
                // Draw signal points with classified colors and smooth fade animations
                signals.forEach { signalItem ->
                    val normalizedDistance = (signalItem.estimatedRange / 5000f).coerceIn(0.1f, 0.9f)
                    val radius = maxRadius * normalizedDistance
                    val angleRad = Math.toRadians(signalItem.bearing.toDouble())
                    val x = centerX + radius * cos(angleRad).toFloat()
                    val y = centerY + radius * sin(angleRad).toFloat()

                    // Calculate alpha based on sweep position
                    val signalAngle = (Math.toDegrees(angleRad) + 90).toFloat() % 360
                    val angleDiff = (sweepAngle - signalAngle + 360) % 360
                    val sweepAlpha = when {
                        angleDiff < 30 -> angleDiff / 30f
                        angleDiff > 330 -> (360 - angleDiff) / 30f
                        else -> 1f
                    }

                    // Combine sweep alpha with fade animation
                    val combinedAlpha = (sweepAlpha * fadeAnimation).coerceIn(0f, 1f)
                    
                    if (combinedAlpha > 0) {
                        val signalColor = getSignalColor(signalItem.category)
                        val strengthFactor = ((signalItem.signalStrength + 100) / 80f).coerceIn(0.5f, 1.5f)
                        
                        // Outer glow with classified signal color
                        drawCircle(
                            color = signalColor.copy(alpha = 0.4f * combinedAlpha),
                            radius = 15.dp.toPx() * strengthFactor,
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                        
                        // Main signal point with classified signal color
                        drawCircle(
                            color = signalColor.copy(alpha = combinedAlpha),
                            radius = 8.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                        
                        // Inner dot
                        drawCircle(
                            color = Color.Black.copy(alpha = combinedAlpha),
                            radius = 3.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

// Signal Analysis View
@Composable
fun LiveSignalAnalysisView(
    signals: List<SignalData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "CLASSIFIED SIGNAL ANALYSIS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (signals.isEmpty()) {
                Text(
                    text = "No signals detected",
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(signals) { signal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = getSignalColor(signal.category).copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, getSignalColor(signal.category).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Animated status dot
                                        val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "alpha"
                                        )
                                        
                                        Canvas(
                                            modifier = Modifier.size(8.dp)
                                        ) {
                                            drawCircle(
                                                color = getSignalColor(signal.category).copy(alpha = alpha),
                                                radius = size.width / 2f
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Text(
                                            text = signal.category.uppercase(),
                                            color = getSignalColor(signal.category),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Signal details including distance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = signal.identifier,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (signal.frequency.isNotEmpty()) {
                                                Text(
                                                    text = signal.frequency,
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            // Distance display
                                            Text(
                                                text = "${String.format("%.1f", signal.estimatedRange)}m",
                                                color = getSignalColor(signal.category).copy(alpha = 0.8f),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = "${signal.signalStrength}dBm",
                                        color = getSignalColor(signal.category),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Splash Screen Animation
@Composable
fun SplashScreenAnimation(
    onAnimationComplete: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    // Logo scale animation
    val logoScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200, easing = EaseOutBounce),
        label = "logoScale"
    )
    
    // Radar sweep animation
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )
    
    // Pulse animation for outer rings
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Text fade in animation
    var textVisible by remember { mutableStateOf(false) }
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(800),
        label = "textAlpha"
    )
    
    // Trigger text animation after logo animation
    LaunchedEffect(Unit) {
        delay(600)
        textVisible = true
        delay(2400) // Total animation duration
        onAnimationComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Pure black background
        contentAlignment = Alignment.Center
    ) {
        // Animated radar rings
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .scale(pulseScale)
        ) {
            val center = center
            val maxRadius = size.minDimension / 2
            
            // Draw multiple radar rings with different alphas
            for (i in 1..4) {
                val radius = maxRadius * i / 4
                val alpha = (5 - i) * 0.1f
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (3f + i).dp.toPx())
                )
            }
            
            // Draw radar sweep
            val sweepPath = Path().apply {
                moveTo(center.x, center.y)
                arcTo(
                    rect = Rect(
                        left = center.x - maxRadius,
                        top = center.y - maxRadius,
                        right = center.x + maxRadius,
                        bottom = center.y + maxRadius
                    ),
                    startAngleDegrees = radarRotation,
                    sweepAngleDegrees = 60f,
                    forceMoveTo = false
                )
                lineTo(center.x, center.y)
            }
            
            drawPath(
                path = sweepPath,
                color = Color(0xFF00E676).copy(alpha = 0.3f),
                style = Fill
            )
            
            // Draw crosshairs
            drawLine(
                color = Color(0xFF00E676).copy(alpha = 0.6f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF00E676).copy(alpha = 0.6f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 2.dp.toPx()
            )
        }
        
        // App logo in center
        Box(
            modifier = Modifier.scale(logoScale),
            contentAlignment = Alignment.Center
        ) {
            // Background circle for logo
            Canvas(modifier = Modifier.size(100.dp)) {
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                    radius = size.minDimension / 2,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.1f),
                    radius = size.minDimension / 2,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            
            // Logo icon
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = "RF-Analyser Logo",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(50.dp)
            )
        }
        
        // App name and tagline
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RF-ANALYSER",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF00E676).copy(alpha = textAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "SIGNAL DETECTION & ANALYSIS",
                style = MaterialTheme.typography.bodySmall.copy(
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White.copy(alpha = textAlpha * 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Loading indicator
            LinearProgressIndicator(
                modifier = Modifier
                    .width(200.dp)
                    .height(2.dp),
                color = Color(0xFF00E676),
                trackColor = Color(0xFF00E676).copy(alpha = 0.2f)
            )
        }
        
        // Scanning particles effect
        repeat(8) { index ->
            val particleAnimation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween((1500 + index * 200), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particle$index"
            )
            
            val radius = 120f + (index * 20f)
            val particleAngle = particleAnimation + (index * 45f)
            val particleX = radius * cos(Math.toRadians(particleAngle.toDouble())).toFloat()
            val particleY = radius * sin(Math.toRadians(particleAngle.toDouble())).toFloat()
            
            Canvas(
                modifier = Modifier
                    .offset(
                        x = particleX.dp,
                        y = particleY.dp
                    )
                    .size(4.dp)
            ) {
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.6f),
                    radius = size.width / 2
                )
            }
        }
    }
}