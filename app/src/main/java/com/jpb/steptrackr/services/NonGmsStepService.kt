package com.jpb.steptrackr.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jpb.steptrackr.R
import com.jpb.steptrackr.utils.SensorMetadata
import com.jpb.steptrackr.utils.StepDelta
import com.jpb.steptrackr.utils.StepDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class NonGmsStepService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var lastSavedSteps = 0L
    private lateinit var database: StepDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d("StepService", "Service lifecycle hook: onCreate initiated")
        database = StepDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StepService", "Service running: onStartCommand executed")

        // Strict guard condition prevents the service from spinning up illegally if permissions are missing
        val hasActivityPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasActivityPermission) {
            Log.e("StepService", "Service started without ACTIVITY_RECOGNITION permission. Aborting execution safely.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Instantly construct the mandatory notification to satisfy the OS watchdog 10-second limit
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Steps")
            .setContentText("Pedometer context active.")
            .setSmallIcon(R.drawable.ic_walk)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        if (!isInitialized) {
            serviceScope.launch {
                try {
                    val historicalBaseline = database.stepDao().getLastSensorValue()
                    if (historicalBaseline != null) {
                        lastSavedSteps = historicalBaseline
                        Log.d("StepService", "Database baseline restored successfully: $lastSavedSteps steps")
                    }

                    launch(Dispatchers.Main) {
                        stepSensor?.let {
                            sensorManager.registerListener(this@NonGmsStepService, it, SensorManager.SENSOR_DELAY_UI)
                            Log.d("StepService", "Hardware SensorEventListener successfully attached.")
                        }
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e("StepService", "Critical failure during background baseline loading", e)
                }
            }
        }

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toLong()
            Log.d("StepService", "Hardware event captured: Raw count since boot = $totalStepsSinceBoot")

            // FIX: If lastSavedSteps is higher than what the physical device says, it's a mismatch (reboot/cache drift).
            // Reset our tracking variable baseline immediately to prevent data locking.
            if (lastSavedSteps == 0L || totalStepsSinceBoot < lastSavedSteps) {
                Log.d("StepService", "Baseline anomaly or device reboot. Resetting baseline to current sensor count: $totalStepsSinceBoot")
                lastSavedSteps = totalStepsSinceBoot
                serviceScope.launch {
                    database.stepDao().updateSensorValue(SensorMetadata(lastSensorValue = totalStepsSinceBoot))
                }
                return
            }

            if (totalStepsSinceBoot > lastSavedSteps) {
                val delta = totalStepsSinceBoot - lastSavedSteps
                Log.d("StepService", "Step progression verified. Calculated delta window: +$delta steps")
                saveStepsToLocalDatabase(delta, Clock.System.now())
                lastSavedSteps = totalStepsSinceBoot
            }
        }
    }

    private fun saveStepsToLocalDatabase(delta: Long, timestamp: Instant) {
        serviceScope.launch {
            try {
                database.stepDao().insertDelta(
                    StepDelta(
                        delta = delta,
                        timestamp = timestamp.toEpochMilliseconds(),
                        isSynced = false
                    )
                )
                database.stepDao().updateSensorValue(
                    SensorMetadata(lastSensorValue = lastSavedSteps)
                )
                Log.d("StepService", "Room persistence execution complete. Saved delta: $delta")
            } catch (e: Exception) {
                Log.e("StepService", "Room transaction write crash", e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("StepService", "Service lifecycle hook: onDestroy executed")
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Activity Tracking", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "non_gms_activity_tracking_channel"
    }
}