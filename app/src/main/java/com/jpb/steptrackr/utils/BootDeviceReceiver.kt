package com.jpb.steptrackr.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.jpb.steptrackr.services.NonGmsStepService

class BootDeviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            if (!gmsAvailable) {
                // Direct call from BOOT_COMPLETED broadcast receiver is permitted by the OS
                val serviceIntent = Intent(context, NonGmsStepService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}