package dev.vetki

import android.app.Application
import dev.vetki.i18n.Strings

class VetkiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.initialize(this)
    }
}
