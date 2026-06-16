package fr.buildtool.apkforge

import android.content.Context

/**
 * Reglages persistants de l'app (SharedPreferences).
 * Pour l'instant : l'allocation memoire (heap Gradle) utilisee pour les builds.
 */
object SettingsStore {

    private const val PREFS = "forge_settings"
    private const val KEY_MEM_MB = "gradle_mem_mb"

    // 0 = laisser le defaut du serveur (valeur posee par setup-termux-native.sh).
    const val MEM_DEFAULT = 0

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Heap Gradle en Mo choisi par l'utilisateur (0 = defaut serveur). */
    fun memMb(ctx: Context): Int = prefs(ctx).getInt(KEY_MEM_MB, MEM_DEFAULT)

    fun setMemMb(ctx: Context, mb: Int) {
        prefs(ctx).edit().putInt(KEY_MEM_MB, mb).apply()
    }
}
