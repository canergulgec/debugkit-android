package dev.debugkit.internal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Reports a shake when acceleration crosses [THRESHOLD_G] at least [PEAKS_REQUIRED] separate
 * times within [WINDOW_MS]. Counting rising edges (not samples) keeps a single bump of the
 * phone, which yields several consecutive high samples, from opening the debugger.
 */
internal class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var aboveThreshold = false
    private var windowStartMs = 0L
    private var peaks = 0
    private var lastShakeMs = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val nowMs = event.timestamp / 1_000_000

        val wasAbove = aboveThreshold
        aboveThreshold = gForce > THRESHOLD_G
        if (!aboveThreshold || wasAbove) return
        if (nowMs - lastShakeMs < COOLDOWN_MS) return

        if (nowMs - windowStartMs > WINDOW_MS) {
            windowStartMs = nowMs
            peaks = 0
        }
        peaks++
        if (peaks >= PEAKS_REQUIRED) {
            peaks = 0
            lastShakeMs = nowMs
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD_G = 2.5f
        const val PEAKS_REQUIRED = 3
        const val WINDOW_MS = 1_000L
        const val COOLDOWN_MS = 1_500L
    }
}
