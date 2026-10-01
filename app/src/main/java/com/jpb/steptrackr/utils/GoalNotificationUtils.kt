package com.jpb.steptrackr.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jpb.steptrackr.R

object GoalNotificationHelper {
    private const val CHANNEL_ID = "step_goal_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Goal Reached",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when you reach your daily step goal"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showGoalReachedNotification(context: Context, totalSteps: Long) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app icon
            .setContentTitle("Goal achieved! 🎉")
            .setContentText("Congratulations! You've reached your goal of $totalSteps steps today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }
}