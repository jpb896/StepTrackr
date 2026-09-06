package com.jpb.steptrackr.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import com.jpb.steptrackr.R
import kotlin.time.Clock
import kotlin.time.Instant

class NonGmsStepService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lastSavedSteps = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Android requires a persistent notification for foreground services
        val notification = NotificationCompat.Builder(this, "step_channel")
            .setContentTitle("Tracking Steps")
            .setSmallIcon(R.drawable.ic_walk)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toLong()

            if (lastSavedSteps in 1..<totalStepsSinceBoot) {
                val delta = totalStepsSinceBoot - lastSavedSteps
                saveStepsToLocalDatabase(delta, Clock.System.now())
            }
            lastSavedSteps = totalStepsSinceBoot
        }
    }

    private fun saveStepsToLocalDatabase(delta: Long, timestamp: Instant) {
        //TODO:  Log this locally. Your WorkManager sync block will pick these entries up later.
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?) = null
}
