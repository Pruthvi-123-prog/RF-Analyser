package com.example.wallhack

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast

class MagnetometerHelper(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var isMonitoring = false
    private var onDetectionListener: ((Float, Float) -> Unit)? = null
    private val threshold = 70f // Adjust this value based on testing

    fun startMonitoring() {
        if (magnetometer == null) {
            throw Exception("Magnetometer sensor not available on this device")
        }

        if (!isMonitoring) {
            val success = sensorManager.registerListener(
                this,
                magnetometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (!success) {
                throw Exception("Failed to register magnetometer listener")
            }
            isMonitoring = true
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
        }
    }

    fun setOnDetectionListener(listener: (Float, Float) -> Unit) {
        onDetectionListener = listener
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                
                // Calculate magnetic field strength
                val strength = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                
                if (strength > threshold) {
                    // Calculate approximate position
                    val angle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()
                    val distance = (threshold / strength) * 100 // Normalized distance
                    
                    onDetectionListener?.invoke(angle, distance)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("MagnetometerHelper", "Sensor accuracy changed: $accuracy")
    }
}