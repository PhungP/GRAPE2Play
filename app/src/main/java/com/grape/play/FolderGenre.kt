package com.grape.play

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Tra thể loại của thư mục chưa nhận diện được qua internet (Deezer, không cần khoá), lưu cache để lần sau không tra lại. */
object FolderGenre {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    /** "" = đã tra nhưng không biết; null = chưa tra. */
    val genres = mutableStateMapOf<String, String>()
    private var loaded = false

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("foldergenre", Context.MODE_PRIVATE)

    fun genreOf(ctx: Context, name: String): String? {
        if (!loaded) { loaded = true; sp(ctx).all.forEach { (k, v) -> if (v is String) genres[k] = v } }
        return genres[name]
    }

    private fun clean(name: String): String = name
        .replace(Regex("(?i)\\b(dsd\\s?\\d*|dsf|dff|24\\s?bit|16\\s?bit|flac|hi-?res|sacd|cd\\d*)\\b"), " ")
        .replace(Regex("[\\[\\(].*?[\\]\\)]"), " ").replace(Regex("\\b(19|20)\\d\\d\\b"), " ")
        .replace(Regex("[_]+"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '.')

    private fun get(url: String): JSONObject? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) JSONObject(it.body?.string() ?: "") else null }
    } catch (_: Exception) { null }

    private fun query(q: String): String? {
        if (q.length < 2) return null
        val s = get("https://api.deezer.com/search?limit=3&q=" + URLEncoder.encode(q, "UTF-8")) ?: return null
        val arr = s.optJSONArray("data") ?: return null
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optJSONObject("album")?.optLong("id") ?: continue
            val a = get("https://api.deezer.com/album/$id") ?: continue
            val g = a.optJSONObject("genres")?.optJSONArray("data")
            if (g != null && g.length() > 0) return g.getJSONObject(0).optString("name")
        }
        return null
    }

    suspend fun fetch(ctx: Context, name: String) {
        if (genreOf(ctx, name) != null) return
        lock.withLock {
            if (genres[name] != null) return
            val g = withContext(Dispatchers.IO) {
                val c = clean(name)
                val parts = c.split(" - ", " – ", "-").map { it.trim() }.filter { it.isNotEmpty() }
                var r = query(c)
                if (r == null && parts.size > 1) { delay(120); r = query(parts[0]) }
                r
            }
            delay(150)
            val v = g ?: ""
            genres[name] = v
            sp(ctx).edit().putString(name, v).apply()
        }
    }
}
