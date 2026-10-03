package com.jpb.steptrackr.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

object AppLocaleManager {

    fun setLanguage(context: Context, languageCode: String) {
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
        localeManager.applicationLocales = LocaleList.forLanguageTags(languageCode)

        // Force activity recreation to immediately apply the new locale string resources
        (context as? Activity)?.recreate()
    }

    fun getCurrentLanguage(context: Context): String {
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
        val locales = localeManager?.applicationLocales
        return if (locales != null && !locales.isEmpty) locales.toLanguageTags() else "en"
    }
}