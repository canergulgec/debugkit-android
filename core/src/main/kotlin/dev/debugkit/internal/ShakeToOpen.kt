package dev.debugkit.internal

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.debugkit.DebugKit
import dev.debugkit.ui.DebugKitActivity

/** Listens for shakes only while the app is in the foreground, to avoid draining battery. */
internal object ShakeToOpen {

    fun install(application: Application) {
        val sensorManager = application.getSystemService(SensorManager::class.java) ?: return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val detector = ShakeDetector {
            if (!DebugKitActivity.isVisible) DebugKit.open(application)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }

            override fun onStop(owner: LifecycleOwner) {
                sensorManager.unregisterListener(detector)
            }
        })
    }
}
