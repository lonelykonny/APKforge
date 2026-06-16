package fr.buildtool.apkforge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telecharge l'APK produit (servi par le serveur local sur 127.0.0.1) puis lance
 * l'installeur systeme via un FileProvider.
 *
 * Pourquoi pas un simple ACTION_VIEW sur l'URL http://127.0.0.1/... :
 *  - Android n'installe pas un APK depuis une URL http (au mieux il ouvre un
 *    navigateur, au pire aucune appli ne gere -> ActivityNotFoundException/crash).
 *  - Il faut un fichier local + un content:// URI (FileProvider) + le type MIME
 *    application/vnd.android.package-archive pour declencher l'installeur.
 */
object ApkInstaller {

    /** Telecharge l'apk depuis [url] vers le cache. Renvoie le fichier ou null. */
    suspend fun download(ctx: Context, url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(ctx.cacheDir, "APKforge.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                requestMethod = "GET"
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            if (out.length() > 0L) out else null
        }.getOrNull()
    }

    /** Lance l'installeur systeme sur le fichier APK local. */
    fun install(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /**
     * Telecharge l'APK depuis [url] et l'ecrit dans [dest] (un URI choisi par
     * l'utilisateur via le selecteur systeme "Enregistrer sous"). Renvoie true
     * en cas de succes. Permet de SORTIR l'APK du sandbox de l'app, pour la
     * garder meme apres desinstallation d'APKforge.
     */
    suspend fun exportTo(ctx: Context, url: String, dest: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    requestMethod = "GET"
                }
                conn.inputStream.use { input ->
                    ctx.contentResolver.openOutputStream(dest)?.use { output ->
                        input.copyTo(output)
                    } ?: return@runCatching false
                }
                conn.disconnect()
                true
            }.getOrDefault(false)
        }
}
