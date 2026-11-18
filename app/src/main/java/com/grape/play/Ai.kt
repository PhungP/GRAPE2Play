package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Lõi AI: lưu khóa Gemini (người dùng tự nhập) và gọi API generateContent. Mọi tính năng AI chỉ chạy khi người dùng chủ động bấm. */
object Ai {
    private lateinit var sp: SharedPreferences
    val hasKey = mutableStateOf(false)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)
        hasKey.value = key.isNotEmpty()
        AiMeta.init(ctx)
    }

    var key: String
        get() = if (::sp.isInitialized) sp.getString("key", "") ?: "" else ""
        set(v) { if (::sp.isInitialized) sp.edit().putString("key", v.trim()).apply(); hasKey.value = v.isNotBlank() }

    /** Tên model; "gemini-flash-latest" luôn trỏ tới bản Flash mới nhất nên không hỏng khi Google đổi phiên bản. */
    var model: String
        get() = if (::sp.isInitialized) sp.getString("model", "gemini-flash-latest") ?: "gemini-flash-latest" else "gemini-flash-latest"
        set(v) { if (::sp.isInitialized) sp.edit().putString("model", v.trim().ifEmpty { "gemini-flash-latest" }).apply() }

    private fun call(body: JSONObject): Pair<Int, String> {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { return it.code to (it.body?.string() ?: "") }
    }

    /** Gọi Gemini; json = true bắt buộc trả JSON. Tự thử lại khi quá tải (429/503). */
    suspend fun generate(prompt: String, system: String? = null, json: Boolean = false, temp: Double = 0.3): String = withContext(Dispatchers.IO) {
        if (key.isEmpty()) throw IOException("Chưa có khóa API Gemini")
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        if (system != null) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val gc = JSONObject().put("temperature", temp)
        if (json) gc.put("responseMimeType", "application/json")
        body.put("generationConfig", gc)
        var attempt = 0
        while (true) {
            val (code, text) = try { call(body) } catch (e: IOException) { if (attempt++ < 2) { delay(2000); continue } else throw IOException("Không kết nối được Gemini: ${e.message}") }
            if (code in 200..299) {
                val o = JSONObject(text)
                val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                if (sb.isNotBlank()) return@withContext sb.toString()
                val why = o.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                throw IOException("Gemini không trả kết quả" + if (why.isNotEmpty()) " ($why)" else "")
            }
            if ((code == 429 || code == 503) && attempt < 3) { attempt++; delay(4000L * attempt); continue }
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(160))
            throw IOException(when (code) { 400, 403 -> "Khóa API không hợp lệ hoặc chưa được bật: $msg"; 429 -> "Hết hạn mức Gemini, thử lại sau: $msg"; else -> "Gemini lỗi $code: $msg" })
        }
        @Suppress("UNREACHABLE_CODE") throw IllegalStateException()
    }

    /** Kiểm tra khóa bằng một yêu cầu rất nhỏ. */
    suspend fun test(): String = generate("Trả lời đúng một từ: OK", temp = 0.0).trim().take(20)

    /** Bóc JSON khỏi câu trả lời (bỏ hàng rào ```). */
    fun parseJson(s: String): Any {
        val t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
    }

    fun langHint(): String = if (Lang.en.value) "Answer in English." else "Trả lời bằng tiếng Việt."
}

/** Tên hiển thị gọn + thể loại do AI xác định cho từng thư mục (không đổi tên file thật). */
object AiMeta {
    class M(val title: String, val genre: String)
    val map = mutableStateMapOf<String, M>()
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("aimeta", Context.MODE_PRIVATE)
        runCatching {
            val o = JSONObject(sp.getString("v", "{}") ?: "{}")
            o.keys().forEach { k -> val a = o.getJSONArray(k); map[k] = M(a.getString(0), a.getString(1)) }
        }
    }
    @Synchronized fun putAll(items: Map<String, M>) { map.putAll(items); save() }
    @Synchronized fun clear() { map.clear(); save() }
    private fun save() {
        if (!::sp.isInitialized) return
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k, JSONArray().put(v.title).put(v.genre)) }
        sp.edit().putString("v", o.toString()).apply()
    }
}
