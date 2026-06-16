package fr.buildtool.apkforge

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Gestion de la langue de l'UI SANS recreation d'activite (donc sans flash noir).
 *
 * Le choix ("fr", "en", ou null = systeme) est expose via un State observe par
 * Compose : le modifier declenche une simple recomposition, pas un redemarrage
 * de l'activite. La valeur est persistee dans les SharedPreferences pour etre
 * restauree au lancement.
 */
object LocaleManager {
    private const val PREFS = "forge_prefs"
    private const val KEY_LANG = "ui_lang"

    // null = suivre le systeme ; sinon "fr" / "en".
    var current by mutableStateOf<String?>(null)
        private set

    /** A appeler une fois au demarrage (dans Application/Activity) pour restaurer. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = prefs.getString(KEY_LANG, null)
    }

    /** Change la langue et la persiste. Recompose l'UI, sans recreer l'activite. */
    fun set(context: Context, lang: String?) {
        current = lang
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().apply {
                if (lang == null) remove(KEY_LANG) else putString(KEY_LANG, lang)
            }.apply()
    }

    /** Code langue effectif ("fr" ou "en"), en resolvant le mode systeme. */
    fun effective(): String {
        val tag = current ?: Locale.getDefault().language
        return if (tag == "fr") "fr" else "en"
    }

    /** Renvoie un Context dont la configuration porte la locale choisie. */
    fun wrap(base: Context): Context {
        val lang = current ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
