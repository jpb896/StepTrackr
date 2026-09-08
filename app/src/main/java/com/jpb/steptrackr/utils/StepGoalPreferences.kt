package com.jpb.steptrackr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class StepGoalPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("step_trackr_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_STEP_GOAL = "key_step_goal"
        const val DEFAULT_GOAL = 10000L
    }

    fun getStepGoal(): Long {
        return prefs.getLong(KEY_STEP_GOAL, DEFAULT_GOAL)
    }

    fun setStepGoal(goal: Long) {
        prefs.edit { putLong(KEY_STEP_GOAL, goal) }
    }
}