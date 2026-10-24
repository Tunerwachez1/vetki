package dev.vetki

import android.app.Application
import android.content.res.Configuration
import dev.vetki.i18n.Strings

class VetkiApplication : Application() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Strings.initialize(this)
    }

    override fun onCreate() {
        super.onCreate()
        Strings.initialize(this)
    }
}
