package com.jpb.steptrackr.utils

import android.location.Location
import com.jpb.steptrackr.ui.RouteLocation

object RouteDistanceCalculator {
    /**
     * Calculates total distance in kilometers from a list of route points.
     */
    fun calculateTotalDistanceKm(points: List<RouteLocation>): Float {
        if (points.size < 2) return 0f
        var totalMeters = 0f
        val results = FloatArray(1)

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            Location.distanceBetween(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
                results
            )
            totalMeters += results[0]
        }
        return totalMeters / 1000f // Convert meters to kilometers
    }
}