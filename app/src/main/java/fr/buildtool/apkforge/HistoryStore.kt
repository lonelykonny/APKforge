package fr.buildtool.apkforge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historique des depots compiles, persiste dans SharedPreferences (JSON).
 * Garde les 20 entrees non epinglees les plus recentes ; les entrees epinglees
 * sont toujours conservees et affichees en tete.
 */
object HistoryStore {

    private const val PREFS = "forge_history"
    private const val KEY = "entries"
    private const val MAX_UNPINNED = 20

    data class Entry(
        val url: String,
        val timestamp: Long,
        val status: String,   // "success" | "failed"
        val pinned: Boolean = false,
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Liste complete : epinglees d'abord, puis par date decroissante. */
    fun load(ctx: Context): List<Entry> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    url = o.getString("url"),
                    timestamp = o.getLong("timestamp"),
                    status = o.optString("status", "success"),
                    pinned = o.optBoolean("pinned", false),
                )
            }
        }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.timestamp })
    }

    private fun save(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("url", e.url)
                put("timestamp", e.timestamp)
                put("status", e.status)
                put("pinned", e.pinned)
            })
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * Enregistre un build termine. Si l'URL existe deja, met a jour sa date et
     * son statut (en conservant l'etat epingle). Purge les non-epinglees au-dela
     * de la limite.
     */
    fun record(ctx: Context, url: String, status: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = load(ctx).toMutableList()
        val idx = existing.indexOfFirst { it.url == clean }
        if (idx >= 0) {
            val prev = existing[idx]
            existing[idx] = prev.copy(timestamp = now, status = status)
        } else {
            existing.add(Entry(clean, now, status))
        }
        // Garde toutes les epinglees + les MAX_UNPINNED non-epinglees recentes.
        val pinned = existing.filter { it.pinned }
        val unpinned = existing.filter { !it.pinned }
            .sortedByDescending { it.timestamp }
            .take(MAX_UNPINNED)
        save(ctx, pinned + unpinned)
    }

    fun togglePin(ctx: Context, url: String) {
        val entries = load(ctx).map {
            if (it.url == url) it.copy(pinned = !it.pinned) else it
        }
        save(ctx, entries)
    }

    fun remove(ctx: Context, url: String) {
        save(ctx, load(ctx).filterNot { it.url == url })
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }
}
