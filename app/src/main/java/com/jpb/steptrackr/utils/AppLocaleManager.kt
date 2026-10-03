package com.jpb.steptrackr.utils

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

object AppLocaleManager {

    fun setLanguage(context: Context, languageCode: String) {
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
        localeManager.applicationLocales = LocaleList.forLanguageTags(languageCode)
    }

    fun getCurrentLanguage(context: Context): String {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
            return localeManager.applicationLocales.toLanguageTags().ifEmpty { "en" }
    }
}