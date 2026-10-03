package com.jpb.steptrackr.utils

import android.content.Context
import android.icu.text.MessageFormat
import androidx.annotation.StringRes

object IcuLocaleUtils {

    /**
     * Retrieves an ICU-formatted string resource outside of Jetpack Compose.
     */
    fun getIcuString(
        context: Context,
        @StringRes id: Int,
        args: Map<String, Any>
    ): String {
        val rawPattern = context.getString(id)
        // Obtains the current locale from context configuration
        val locale = context.resources.configuration.locales[0]
        val messageFormat = MessageFormat(rawPattern, locale)
        return messageFormat.format(args)
    }
}