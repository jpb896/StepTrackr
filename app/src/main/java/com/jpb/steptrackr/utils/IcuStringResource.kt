package com.jpb.steptrackr.utils

import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun icuStringResource(@StringRes id: Int, args: Map<String, Any>): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val rawPattern = stringResource(id)

    return remember(id, args, configuration.locales) {
        val locale = configuration.locales[0]
        val messageFormat = MessageFormat(rawPattern, locale)
        messageFormat.format(args)
    }
}