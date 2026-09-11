package com.jpb.steptrackr.utils

enum class HistoryTimeFrame {
    HOURLY, DAILY
}

data class StepDataPoint(
    val label: String, // e.g., "10 AM" for Hourly, "Mon" or "Sep 10" for Daily
    val steps: Int
)

data class StepHistoryState(
    val selectedTimeFrame: HistoryTimeFrame = HistoryTimeFrame.HOURLY,
    val hourlyData: List<StepDataPoint> = emptyList(),
    val dailyData: List<StepDataPoint> = emptyList()
)