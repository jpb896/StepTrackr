package com.jpb.steptrackr

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.jpb.steptrackr.services.HealthSyncWorker
import java.util.concurrent.TimeUnit

class StepApplication : Application() {
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate() {
        super.onCreate()
        setupGlobalSyncPipeline(this)
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun setupGlobalSyncPipeline(context: Context) {
        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        if (gmsAvailable) {
            // Subscribe the device background process to the low power recording engine
            FitnessLocal.getLocalRecordingClient(context).subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
        }

        // Enqueue synchronization work requests every 3 hours
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HealthConnectStepSyncPipeline",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}