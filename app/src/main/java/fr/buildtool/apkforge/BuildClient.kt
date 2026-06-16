package fr.buildtool.apkforge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Etat de la chaine de build cote serveur. */
data class ChainStatus(
    val chainReady: Boolean,
    val builderPresent: Boolean,
    val sdkPresent: Boolean,
    val nativeReady: Boolean = false,
    val prootReady: Boolean = false,
)

/** Resultat d'un poll de logs. */
data class LogChunk(
    val from: Int,
    val next: Int,
    val status: String,   // running | success | failed
    val lines: List<String>,
)

/**
 * Client du serveur de build local (buildserver.py).
 * Toutes les requetes visent 127.0.0.1 ; rien ne sort du telephone.
 */
class BuildClient(
    private val baseUrl: String = "http://127.0.0.1:8765",
    private val token: String = "",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    private fun req(path: String): Request.Builder {
        val b = Request.Builder().url("$baseUrl$path")
        if (token.isNotEmpty()) b.header("X-Build-Token", token)
        // Langue de l'UI (choix manuel via AppCompat, sinon langue systeme).
        // Le serveur s'en sert pour localiser les logs de build (fr/en).
        b.header("X-Forge-Lang", uiLang())
        return b
    }

    /** Renvoie le code langue effectif de l'app ("fr" ou "en", defaut "en"). */
    private fun uiLang(): String = LocaleManager.effective()

    /** Verifie si le serveur repond et renvoie l'etat de la chaine. */
    suspend fun status(): ChainStatus? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(req("/status").get().build()).execute().use { r ->
                val o = JSONObject(r.body?.string() ?: "{}")
                ChainStatus(
                    chainReady = o.optBoolean("chain_ready"),
                    builderPresent = o.optBoolean("builder_present"),
                    sdkPresent = o.optBoolean("sdk_present"),
                    nativeReady = o.optBoolean("native_ready"),
                    prootReady = o.optBoolean("proot_ready"),
                )
            }
        }.getOrNull()
    }

    /** Lance un build, renvoie le job_id ou null. */
    suspend fun startBuild(
        url: String, branch: String = "", subdir: String = "",
        task: String = "assembleDebug", memMb: Int = 0,
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("url", url)
            if (branch.isNotEmpty()) put("branch", branch)
            if (subdir.isNotEmpty()) put("subdir", subdir)
            put("task", task)
            // Heap Gradle en Mo (0 = laisser le defaut du serveur).
            if (memMb > 0) put("mem", memMb)
        }.toString().toRequestBody(json)
        runCatching {
            http.newCall(req("/build").post(body).build()).execute().use { r ->
                JSONObject(r.body?.string() ?: "{}").optString("job_id").ifEmpty { null }
            }
        }.getOrNull()
    }

    /** Lance le setup de la chaine, renvoie le job_id. */
    suspend fun startSetup(): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(req("/setup").post(ByteArray(0).toRequestBody()).build())
                .execute().use { r ->
                    JSONObject(r.body?.string() ?: "{}").optString("job_id").ifEmpty { null }
                }
        }.getOrNull()
    }

    /** Recupere les logs a partir de l'index [from]. */
    suspend fun logs(jobId: String, from: Int): LogChunk? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(req("/logs/$jobId?from=$from").get().build()).execute().use { r ->
                val o = JSONObject(r.body?.string() ?: "{}")
                val arr = o.optJSONArray("lines")
                val list = buildList { if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i)) }
                LogChunk(
                    from = o.optInt("from"),
                    next = o.optInt("next"),
                    status = o.optString("status", "running"),
                    lines = list,
                )
            }
        }.getOrNull()
    }

    /** URL de telechargement de l'APK pour un job termine. */
    fun apkUrl(jobId: String) = "$baseUrl/apk/$jobId"
}
