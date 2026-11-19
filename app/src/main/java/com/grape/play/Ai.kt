package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Một dịch vụ AI có gói miễn phí. [gemini] = true dùng API riêng của Google, còn lại theo chuẩn OpenAI (chat/completions). */
class Prov(val id: String, val name: String, val keyUrl: String, val keyHint: String, val base: String, val models: List<String>, val gemini: Boolean = false)

object AiProvs {
    val ALL = listOf(
        Prov("gemini", "Google Gemini", "https://aistudio.google.com/apikey", "AIza…", "https://generativelanguage.googleapis.com/v1beta",
            listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"), gemini = true),
        Prov("groq", "Groq", "https://console.groq.com/keys", "gsk_…", "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "llama-3.1-8b-instant")),
        Prov("openrouter", "OpenRouter", "https://openrouter.ai/keys", "sk-or-…", "https://openrouter.ai/api/v1",
            listOf("openai/gpt-oss-120b:free", "meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3n-e2b-it:free")),
        Prov("mistral", "Mistral", "https://console.mistral.ai/api-keys", "", "https://api.mistral.ai/v1",
            listOf("mistral-small-latest", "open-mistral-nemo")),
        Prov("cerebras", "Cerebras", "https://cloud.cerebras.ai/", "csk-…", "https://api.cerebras.ai/v1",
            listOf("llama3.3-70b", "gpt-oss-120b", "llama3.1-8b")),
    )
    fun get(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** Lõi AI: lưu khóa của nhiều dịch vụ (người dùng tự nhập), chọn AI chính, tự chuyển sang AI khác khi hết hạn mức hoặc không phản hồi. Mọi tính năng AI chỉ chạy khi người dùng chủ động bấm. */
object Ai {
    private lateinit var sp: SharedPreferences
    /** Có ít nhất một khóa. */
    val hasKey = mutableStateOf(false)
    /** Tăng mỗi khi khóa / AI chính thay đổi để giao diện vẽ lại. */
    val rev = mutableStateOf(0)
    /** Dịch vụ vừa trả lời + ghi chú khi phải chuyển AI (hiện ở chân hộp thoại AI). */
    val status = mutableStateOf("")
    /** Thời gian chờ mỗi dịch vụ trước khi chuyển sang dịch vụ kế tiếp. */
    const val SWITCH_MS = 25_000L

    /** HTTP/1.1 và ưu tiên IPv4: một số TV Android cũ treo kết nối khi dùng HTTP/2 hoặc IPv6 hỏng. */
    internal val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
        }).build()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)
        refresh()
        AiMeta.init(ctx)
    }

    private fun refresh() { hasKey.value = AiProvs.ALL.any { keyOf(it.id).isNotEmpty() }; rev.value++ }

    fun keyOf(id: String): String = if (::sp.isInitialized) sp.getString(if (id == "gemini") "key" else "key_$id", "") ?: "" else ""
    fun has(id: String) = keyOf(id).isNotEmpty()
    fun setKey(id: String, v: String) {
        if (::sp.isInitialized) sp.edit().putString(if (id == "gemini") "key" else "key_$id", v.trim()).apply()
        refresh()
    }

    /** AI chính: mặc định dịch vụ đầu tiên có khóa. */
    val primary: String get() {
        val p = if (::sp.isInitialized) sp.getString("primary", "") ?: "" else ""
        return if (p.isNotEmpty() && has(p)) p else AiProvs.ALL.firstOrNull { has(it.id) }?.id ?: "gemini"
    }
    fun setPrimary(id: String) { if (::sp.isInitialized) sp.edit().putString("primary", id).apply(); rev.value++ }

    /** Tự chuyển sang AI khác khi AI chính hết hạn mức hoặc không phản hồi. */
    var auto: Boolean
        get() = if (::sp.isInitialized) sp.getBoolean("auto", true) else true
        set(v) { if (::sp.isInitialized) sp.edit().putBoolean("auto", v).apply(); rev.value++ }

    @Volatile private var lastOk = HashMap<String, String>()

    /** Chờ phản hồi HTTP; huỷ coroutine (hết 25 giây) thì huỷ luôn kết nối. */
    private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { c ->
        c.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try { val r = response.use { it.code to (it.body?.string() ?: "") }; if (c.isActive) c.resume(r) }
                catch (e: IOException) { if (c.isActive) c.resumeWithException(e) }
            }
        })
    }

    private fun errMsg(text: String) = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(140))

    /** Gọi AI; json = true yêu cầu trả JSON. Thử AI chính trước; nếu bật tự chuyển thì lần lượt thử các AI còn lại (mỗi AI tối đa 25 giây). */
    suspend fun generate(prompt: String, system: String? = null, json: Boolean = false, temp: Double = 0.3, quick: Boolean = false): String {
        val ready = AiProvs.ALL.filter { has(it.id) }
        if (ready.isEmpty()) throw IOException("Chưa có khóa API nào — vào “Dịch vụ AI & khóa API” để thêm")
        val first = ready.firstOrNull { it.id == primary } ?: ready.first()
        val order = if (auto) listOf(first) + ready.filter { it !== first } else listOf(first)
        val budget = if (order.size > 1) SWITCH_MS else 60_000L
        val sys = if (json) ((system ?: "") + " Chỉ trả JSON hợp lệ, không thêm chữ nào khác, không dùng hàng rào ```.").trim() else system
        val fails = ArrayList<String>()
        for (p in order) {
            try {
                val out = withTimeout(budget) { if (p.gemini) gemini(p, prompt, sys, json, temp) else openai(p, prompt, sys, temp) }
                status.value = if (fails.isEmpty()) "AI: ${p.name}" else "↪ ${p.name} đang trả lời — ${fails.joinToString("; ")}"
                if (fails.isNotEmpty()) AppLog.i("AI", status.value)
                return out
            } catch (e: TimeoutCancellationException) {
                fails += "${p.name}: không phản hồi sau ${budget / 1000} giây"
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                fails += "${p.name}: ${e.message ?: e.javaClass.simpleName}"
            }
            AppLog.i("AI", "Bỏ qua ${p.name}: ${fails.last()}")
        }
        status.value = "✗ " + fails.joinToString("; ")
        throw IOException(fails.joinToString("\n"))
    }

    // ---------------------------------------------------------------- Gemini
    private suspend fun gemini(p: Prov, prompt: String, system: String?, json: Boolean, temp: Double): String = withContext(Dispatchers.IO) {
        val key = keyOf(p.id)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        if (system != null) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val gc = JSONObject().put("temperature", temp)
        if (json) gc.put("responseMimeType", "application/json")
        body.put("generationConfig", gc)
        val prefModel = if (::sp.isInitialized) sp.getString("model", "") ?: "" else ""
        val models = (listOf(lastOk["gemini"] ?: "", prefModel) + p.models).filter { it.isNotBlank() }.distinct()
        var lastErr = ""
        for (m in models) {
            var attempt = 0
            while (true) {
                val req = Request.Builder().url("${p.base}/models/$m:generateContent").header("x-goog-api-key", key)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                val (code, text) = try { http.newCall(req).await() } catch (e: IOException) {
                    AppLog.i("AI", "Gemini lỗi mạng ($m): ${e.javaClass.simpleName} ${e.message}")
                    if (attempt++ < 1) { delay(1500); continue } else throw IOException("lỗi mạng (${e.javaClass.simpleName}: ${e.message ?: "không rõ"})")
                }
                if (code in 200..299) {
                    val o = JSONObject(text)
                    val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                    if (sb.isNotBlank()) { lastOk["gemini"] = m; return@withContext sb.toString() }
                    val why = o.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                    throw IOException("không trả kết quả" + if (why.isNotEmpty()) " ($why)" else "")
                }
                val msg = errMsg(text)
                AppLog.i("AI", "Gemini $m trả $code: ${msg.take(120)}")
                if (code == 503) { if (attempt++ < 1) { delay(1500); continue }; lastErr = "quá tải"; break }
                if (code == 429) { lastErr = "hết hạn mức"; break }
                if (code == 404) { lastErr = "không có model"; break }
                throw IOException(if (code == 400 || code == 403) "khóa không hợp lệ hoặc chưa được bật" else "lỗi $code: ${msg.take(80)}")
            }
        }
        throw IOException(lastErr.ifEmpty { "không phản hồi" })
    }

    // ---------------------------------------------------------------- Groq / OpenRouter / Mistral / Cerebras (chuẩn OpenAI)
    private suspend fun openai(p: Prov, prompt: String, system: String?, temp: Double): String = withContext(Dispatchers.IO) {
        val key = keyOf(p.id)
        val msgs = JSONArray()
        if (system != null) msgs.put(JSONObject().put("role", "system").put("content", system))
        msgs.put(JSONObject().put("role", "user").put("content", prompt))
        val models = (listOf(lastOk[p.id] ?: "") + p.models).filter { it.isNotBlank() }.distinct()
        var lastErr = ""
        for (m in models) {
            val body = JSONObject().put("model", m).put("messages", msgs).put("temperature", temp)
            val rb = Request.Builder().url(p.base + "/chat/completions").header("Authorization", "Bearer $key")
            if (p.id == "openrouter") rb.header("HTTP-Referer", "https://github.com/grape2play").header("X-Title", "GRAPE2Play")
            val (code, text) = try { http.newCall(rb.post(body.toString().toRequestBody("application/json".toMediaType())).build()).await() }
                catch (e: IOException) { throw IOException("lỗi mạng (${e.javaClass.simpleName}: ${e.message ?: "không rõ"})") }
            if (code in 200..299) {
                val c = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
                val out = c.replace(Regex("(?s)<think>.*?</think>"), "").trim()
                if (out.isNotEmpty()) { lastOk[p.id] = m; return@withContext out }
                lastErr = "không trả kết quả"; continue
            }
            val msg = errMsg(text)
            AppLog.i("AI", "${p.name} $m trả $code: ${msg.take(120)}")
            when (code) {
                401, 403 -> throw IOException("khóa không hợp lệ")
                429 -> lastErr = "hết hạn mức"
                402 -> lastErr = "hết tín dụng"
                404, 400 -> lastErr = "model không dùng được"
                else -> lastErr = "lỗi $code"
            }
        }
        throw IOException(lastErr.ifEmpty { "không phản hồi" })
    }

    private fun hostOf(p: Prov) = java.net.URI(p.base).host

    /** Đo thời gian từng bước kết nối tới dịch vụ [p] (DNS, TCP, Internet, khóa, tạo nội dung) để biết TV kẹt ở đâu. */
    suspend fun diagnose(p: Prov): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        suspend fun step(name: String, f: suspend () -> String) {
            val t0 = System.currentTimeMillis()
            val r = try { f() } catch (e: Exception) { "LỖI ${e.javaClass.simpleName}: ${e.message}" }
            sb.append("• $name: $r (${System.currentTimeMillis() - t0} ms)\n")
        }
        var addrs: List<java.net.InetAddress> = emptyList()
        step("DNS ${hostOf(p)}") { addrs = java.net.InetAddress.getAllByName(hostOf(p)).toList(); addrs.joinToString { it.hostAddress ?: "?" } }
        step("TCP :443") {
            val a = addrs.firstOrNull { it is java.net.Inet4Address } ?: addrs.first()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(a, 443), 8000) }; "OK " + a.hostAddress
        }
        val c = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
        step("Internet (google.com)") { c.newCall(Request.Builder().url("https://www.google.com/generate_204").build()).execute().use { "HTTP ${it.code}" } }
        step("Khóa ${p.name}") { test(p.id) }
        step("Tạo nội dung") { withTimeout(25_000) { if (p.gemini) gemini(p, "OK", null, false, 0.0) else openai(p, "OK", null, 0.0) }.take(20) }
        sb.toString().trim()
    }

    /** Kiểm tra khóa của dịch vụ [id] bằng một lệnh nhẹ (không phụ thuộc tải của model). */
    suspend fun test(id: String): String = withContext(Dispatchers.IO) {
        val p = AiProvs.get(id); val key = keyOf(id)
        if (key.isEmpty()) throw IOException("Chưa có khóa ${p.name}")
        val c = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val req = when {
            p.gemini -> Request.Builder().url("${p.base}/models?pageSize=1").header("x-goog-api-key", key).build()
            p.id == "openrouter" -> Request.Builder().url("${p.base}/key").header("Authorization", "Bearer $key").build()
            else -> Request.Builder().url("${p.base}/models").header("Authorization", "Bearer $key").build()
        }
        val (code, text) = try { c.newCall(req).execute().use { it.code to (it.body?.string() ?: "") } }
            catch (e: IOException) { throw IOException("Không kết nối được ${p.name} (${e.javaClass.simpleName}: ${e.message ?: "không rõ"})") }
        if (code in 200..299) return@withContext "OK"
        throw IOException(if (code == 400 || code == 401 || code == 403) "Khóa ${p.name} không hợp lệ hoặc chưa được bật: ${errMsg(text)}" else "${p.name} lỗi $code: ${errMsg(text)}")
    }

    /** Bóc JSON khỏi câu trả lời (bỏ hàng rào ``` và chữ thừa trước/sau). */
    fun parseJson(s: String): Any {
        var t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val a = t.indexOfFirst { it == '{' || it == '[' }
        if (a > 0) t = t.substring(a)
        val b = t.indexOfLast { it == '}' || it == ']' }
        if (b in 0 until t.length - 1) t = t.substring(0, b + 1)
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
