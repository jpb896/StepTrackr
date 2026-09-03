package com.jpb.steptrackr.utils

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object SystemEnvironment {
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return availability == ConnectionResult.SUCCESS
    }
}