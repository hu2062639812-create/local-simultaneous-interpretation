package com.hearopilot.app

import android.app.Application
import android.content.Context
import com.hearopilot.app.ui.locale.AppLocale
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Libellula MVP.
 *
 * Initializes Hilt dependency injection.
 */
@HiltAndroidApp
class HearoPilotApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }
}
