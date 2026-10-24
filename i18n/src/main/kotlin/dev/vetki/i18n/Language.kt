package dev.vetki.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Only UI locale preferences live here; user documents are never translated. */
object Language {
    const val SYSTEM = "system"
    private val supported = setOf(SYSTEM, "en", "ru")

    private fun preferences(context: Context) =
        context.getSharedPreferences("language", Context.MODE_PRIVATE)

    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) SYSTEM
            else locales[0].language.takeIf { it in supported } ?: SYSTEM
        }
        return preferences(context).getString("selected", SYSTEM)?.takeIf { it in supported }
            ?: SYSTEM
    }

    fun set(context: Context, language: String) {
        require(language in supported)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
        } else {
            preferences(context).edit().putString("selected", language).apply()
        }
    }

    fun locale(context: Context): Locale {
        val selection = selected(context)
        val language =
            if (selection == SYSTEM) Resources.getSystem().configuration.locales[0].language
            else selection
        return Locale.forLanguageTag(if (language == "ru") "ru" else "en")
    }

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale(context)))
        return context.createConfigurationContext(configuration)
    }
}

/** Resource access for UI callbacks, templates and background notifications alike. */
object Strings {
    private lateinit var application: Context
    @Volatile private var localized: Context? = null

    fun initialize(context: Context) {
        application = context.applicationContext
        localized = null
    }

    val locale: Locale
        get() = Language.locale(application)

    fun text(id: Int, vararg args: Any?): String {
        val language = locale.language
        var context = localized
        if (context == null || context.resources.configuration.locales[0].language != language) {
            context = Language.wrap(application)
            localized = context
        }
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
}

fun tr(id: Int, vararg args: Any?): String = Strings.text(id, *args)
