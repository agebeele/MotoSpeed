package com.example.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class SensorData(
    val leanAngleDegrees: Float = 0f,
    val maxLeftLean: Float = 0f,
    val maxRightLean: Float = 0f,
    val accelerationG: Float = 0f,
    val maxGForce: Float = 0f
)

class MotoSensors(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    private var maxLeft = 0f
    private var maxRight = 0f
    private var maxG = 0f

    fun start() {
        sensorManager?.let { sm ->
            accelerometer?.let { acc ->
                sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    fun resetPeaks() {
        maxLeft = 0f
        maxRight = 0f
        maxG = 0f
        _sensorData.value = _sensorData.value.copy(
            maxLeftLean = 0f,
            maxRightLean = 0f,
            maxGForce = 0f
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Calculate lean angle in degrees (device roll)
        val angleRad = atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))
        val angleDeg = Math.toDegrees(angleRad).toFloat()

        if (angleDeg < 0 && -angleDeg > maxLeft) {
            maxLeft = -angleDeg
        } else if (angleDeg > 0 && angleDeg > maxRight) {
            maxRight = angleDeg
        }

        val totalAcc = sqrt(ax * ax + ay * ay + az * az) / 9.80665f
        if (totalAcc > maxG) {
            maxG = totalAcc
        }

        _sensorData.value = SensorData(
            leanAngleDegrees = angleDeg,
            maxLeftLean = maxLeft,
            maxRightLean = maxRight,
            accelerationG = totalAcc,
            maxGForce = maxG
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
