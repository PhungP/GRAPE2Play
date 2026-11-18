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
    /** HTTP/1.1 và ưu tiên IPv4: một số TV Android cũ treo kết nối khi dùng HTTP/2 hoặc IPv6 hỏng. */
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
        }).build()

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

    /** Model dự phòng: khi model chính quá tải (503) hoặc hết hạn mức (429) thì thử model kế tiếp. */
    private val FALLBACKS = listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite")
    @Volatile private var lastOk = ""

    private fun call(body: JSONObject, quick: Boolean, m: String = model): Pair<Int, String> {
        val client = if (quick) http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() else http
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
            .header("x-goog-api-key", key).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { return it.code to (it.body?.string() ?: "") }
    }

    /** Gọi Gemini; json = true bắt buộc trả JSON. Tự thử lại và chuyển sang model dự phòng khi quá tải. */
    suspend fun generate(prompt: String, system: String? = null, json: Boolean = false, temp: Double = 0.3, quick: Boolean = false): String = withContext(Dispatchers.IO) {
        if (key.isEmpty()) throw IOException("Chưa có khóa API Gemini")
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        if (system != null) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val gc = JSONObject().put("temperature", temp)
        if (json) gc.put("responseMimeType", "application/json")
        body.put("generationConfig", gc)
        val models = (listOf(lastOk, model) + FALLBACKS).filter { it.isNotBlank() }.distinct()
        var lastErr = ""
        for (m in models) {
            var attempt = 0
            while (true) {
                val (code, text) = try { call(body, quick, m) } catch (e: IOException) {
                    AppLog.i("AI", "Gemini lỗi mạng ($m): ${e.javaClass.simpleName} ${e.message}")
                    if (!quick && attempt++ < 1) { delay(2000); continue } else throw IOException("Không kết nối được Gemini (${e.javaClass.simpleName}: ${e.message ?: "không rõ"})")
                }
                if (code in 200..299) {
                    val o = JSONObject(text)
                    val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                    if (sb.isNotBlank()) { lastOk = m; return@withContext sb.toString() }
                    val why = o.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                    throw IOException("Gemini không trả kết quả" + if (why.isNotEmpty()) " ($why)" else "")
                }
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(160))
                AppLog.i("AI", "Gemini $m trả $code: ${msg.take(120)}")
                if (code == 429 || code == 503) { if (attempt++ < 1) { delay(2500); continue }; lastErr = "$m: $code"; break }
                if (code == 404) { lastErr = "$m: không có model này"; break }
                throw IOException(if (code == 400 || code == 403) "Khóa API không hợp lệ hoặc chưa được bật: $msg" else "Gemini lỗi $code: $msg")
            }
        }
        throw IOException("Các model Gemini đều đang quá tải hoặc hết hạn mức — thử lại sau ít phút ($lastErr)")
    }

    private const val HOST = "generativelanguage.googleapis.com"

    /** Đo thời gian từng bước kết nối tới Gemini (DNS, TCP, Internet, API, tạo nội dung) để biết TV kẹt ở đâu. */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun step(name: String, f: () -> String) {
            val t0 = System.currentTimeMillis()
            val r = try { f() } catch (e: Exception) { "LỖI ${e.javaClass.simpleName}: ${e.message}" }
            sb.append("• $name: $r (${System.currentTimeMillis() - t0} ms)\n")
        }
        var addrs: List<java.net.InetAddress> = emptyList()
        step("DNS") { addrs = java.net.InetAddress.getAllByName(HOST).toList(); addrs.joinToString { it.hostAddress ?: "?" } }
        step("TCP :443") {
            val a = addrs.firstOrNull { it is java.net.Inet4Address } ?: addrs.first()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(a, 443), 8000) }; "OK " + a.hostAddress
        }
        val c = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
        step("Internet (google.com)") { c.newCall(Request.Builder().url("https://www.google.com/generate_204").build()).execute().use { "HTTP ${it.code}" } }
        step("Gemini API + khóa (danh sách model)") { c.newCall(Request.Builder().url("https://$HOST/v1beta/models?pageSize=1").header("x-goog-api-key", key).build()).execute().use { "HTTP ${it.code}" } }
        step("Tạo nội dung (model $model)") {
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "OK")))))
            val (code, _) = call(body, false); "HTTP $code"
        }
        sb.toString().trim()
    }

    /** Kiểm tra khóa bằng việc liệt kê model (nhẹ, không phụ thuộc tải của model tạo nội dung). */
    suspend fun test(): String = withContext(Dispatchers.IO) {
        if (key.isEmpty()) throw IOException("Chưa có khóa API Gemini")
        val c = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("https://$HOST/v1beta/models?pageSize=1").header("x-goog-api-key", key).build()
        val (code, text) = try { c.newCall(req).execute().use { it.code to (it.body?.string() ?: "") } }
            catch (e: IOException) { throw IOException("Không kết nối được Gemini (${e.javaClass.simpleName}: ${e.message ?: "không rõ"})") }
        if (code in 200..299) return@withContext "OK"
        val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(160))
        throw IOException(if (code == 400 || code == 403) "Khóa API không hợp lệ hoặc chưa được bật: $msg" else "Gemini lỗi $code: $msg")
    }

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
