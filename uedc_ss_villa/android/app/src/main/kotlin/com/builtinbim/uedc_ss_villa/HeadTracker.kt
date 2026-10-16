package com.builtinbim.uedc_ss_villa

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.Position
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the phone's rotation-vector sensor into a head pose in SceneView's world
 * (Y up, -Z forward), for an activity locked to landscape with the top of the phone on the left.
 *
 * [onPose] receives the head orientation and the unit "right" vector, which the VR screen uses to
 * offset the left and right eye cameras.
 */
class HeadTracker(
    context: Context,
    private val onPose: (orientation: Quaternion, right: Position) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable get() = sensor != null

    private val deviceToWorld = FloatArray(9)
    private val screenToWorld = FloatArray(9)
    private var yawOffset: Float? = null

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    /** Makes the direction the user is currently facing the new "forward". */
    fun recenter() {
        yawOffset = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(deviceToWorld, event.values)
        // Landscape (ROTATION_90): screen X = device Y, screen Y = device -X.
        SensorManager.remapCoordinateSystem(
            deviceToWorld, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, screenToWorld
        )
        val r = screenToWorld
        // Sensor world is East-North-Up; SceneView is X right, Y up, Z towards the viewer.
        // Rows: x' = East, y' = Up, z' = -North.
        val m = floatArrayOf(
            r[0], r[1], r[2],
            r[6], r[7], r[8],
            -r[3], -r[4], -r[5]
        )

        // Camera looks down its local -Z, so forward is minus the third column.
        val forwardX = -m[2]
        val forwardZ = -m[8]
        val yaw = atan2(-forwardX, -forwardZ)
        val offset = yawOffset ?: yaw.also { yawOffset = it }

        // Rotate about world Y by -offset so the first pose (or the last recenter) faces -Z.
        val c = cos(-offset)
        val s = sin(-offset)
        val a = FloatArray(9)
        for (col in 0..2) {
            val x = m[col]
            val y = m[3 + col]
            val z = m[6 + col]
            a[col] = c * x + s * z
            a[3 + col] = y
            a[6 + col] = -s * x + c * z
        }

        onPose(toQuaternion(a), Position(a[0], a[3], a[6]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Row-major 3x3 rotation matrix to quaternion. */
    private fun toQuaternion(m: FloatArray): Quaternion {
        val m00 = m[0]; val m01 = m[1]; val m02 = m[2]
        val m10 = m[3]; val m11 = m[4]; val m12 = m[5]
        val m20 = m[6]; val m21 = m[7]; val m22 = m[8]
        val trace = m00 + m11 + m22
        return when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f
                Quaternion((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                Quaternion(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                Quaternion((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                Quaternion((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
            }
        }
    }
}
