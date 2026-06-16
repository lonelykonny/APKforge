package fr.buildtool.apkforge

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Capture les exceptions non gerees (crashes) et les ecrit dans un fichier
 * lisible : filesDir/last_crash.txt. Permet de recuperer la stacktrace meme
 * quand logcat n'est pas accessible.
 */
object CrashLogger {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    append("=== APKforge crash ===\n")
                    append("time: ").append(ts).append('\n')
                    append("thread: ").append(thread.name).append('\n')
                    append("message: ").append(throwable.message ?: "(none)").append('\n')
                    append("\n").append(sw.toString())
                }
                File(appCtx.filesDir, FILE).writeText(text)
            }
            // Laisse le handler systeme finir le crash normalement.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Renvoie le dernier crash enregistre, ou null s'il n'y en a pas. */
    fun lastCrash(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        return if (f.exists() && f.length() > 0L) f.readText() else null
    }

    /** Efface le crash enregistre. */
    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
