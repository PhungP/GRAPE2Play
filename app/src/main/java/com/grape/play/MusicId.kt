package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nhận diện nhạc môi trường: thu ~10 giây từ MICRO, gửi tới AudD (api.audd.io) bằng khoá do NGƯỜI DÙNG tự cung cấp, hiện kết quả.
 * Không chạm Playback / Audio Engine / Bit-perfect: không xin audio focus, không đổi đầu ra, không đọc luồng phát nội bộ của app
 * (chỉ nghe được âm thanh qua micro). Không ghi đè metadata của file nhạc. Không có khoá hoặc không có micro (đa số TV) thì tính năng không khả dụng.
 */
object MusicId {
    sealed class State {
        object Idle : State()
        class Recording(val sec: Int) : State()
        object Sending : State()
        class Done(val r: MusicIdCodec.Result) : State()
        class Failed(val msg: String) : State()
    }

    const val SAMPLE_RATE = 16000
    const val SECONDS = 10
    private const val MIN_PEAK = 200        // dưới mức này coi như micro im lặng/bị tắt: không gửi đi

    val state = MutableStateFlow<State>(State.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)
    @Volatile private var job: Job? = null
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) { if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("musicid", Context.MODE_PRIVATE) }
    /**
     * Khoá AudD của người dùng, lưu MÃ HOÁ bằng Android Keystore ([SecretBox]); khoá cũ dạng plaintext được mã hoá lại ngay khi đọc.
     * Không bao giờ ghi log, chỉ gửi qua HTTPS tới api.audd.io. Nếu không giải mã được (khoá Keystore bị mất) thì coi như chưa có khoá và KHÔNG xoá dữ liệu đã lưu.
     */
    var apiKey: String
        get() {
            if (!::sp.isInitialized) return ""
            val stored = sp.getString("audd", "").orEmpty()
            if (stored.isEmpty()) return ""
            val plain = SecretBox.decrypt(stored) ?: return ""
            if (!SecretBox.isEncrypted(stored)) SecretBox.encrypt(plain)?.let { sp.edit().putString("audd", it).apply() }   // di trú khoá cũ
            return plain
        }
        set(v) {
            if (!::sp.isInitialized) return
            val t = v.trim()
            sp.edit().putString("audd", if (t.isEmpty()) "" else (SecretBox.encrypt(t) ?: t)).apply()      // Keystore lỗi: giữ bản rõ, không làm mất khoá
        }

    fun hasMic(ctx: Context) = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun fail(msg: String) { state.value = State.Failed(msg) }
    fun busy() = busy.get()

    /** Bắt đầu một lần nhận diện (bỏ qua nếu đang chạy). Gọi sau khi đã có quyền micro. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (!busy.compareAndSet(false, true)) return
        job = scope.launch { try { run(app) } finally { busy.set(false) } }
    }
    fun cancel() { job?.cancel() }

    private suspend fun run(ctx: Context) {
        val key = apiKey
        if (key.isEmpty()) { fail("Hãy nhập khoá API AudD trước."); return }
        if (!hasMic(ctx)) { fail("Thiết bị này không có micro."); return }
        if (!hasPermission(ctx)) { fail("Cần cấp quyền micro để nhận diện nhạc."); return }
        val pcm = try { record() } catch (e: CancellationException) { state.value = State.Idle; throw e }
            catch (e: Exception) { fail("Không thu được âm thanh: ${e.message ?: e.javaClass.simpleName}"); return }
        if (MusicIdCodec.peak(pcm, pcm.size) < MIN_PEAK) { fail("Micro không thu được âm thanh (quá nhỏ hoặc micro đang tắt). Đưa micro lại gần nguồn nhạc."); return }
        state.value = State.Sending
        try {
            val r = post(key, MusicIdCodec.wav(pcm, pcm.size, SAMPLE_RATE))
            state.value = if (r.status == MusicIdCodec.Status.ERROR) State.Failed(MusicIdCodec.describeError(r)) else State.Done(r)
        } catch (e: CancellationException) { state.value = State.Idle; throw e }
        catch (e: Exception) { fail("Không kết nối được dịch vụ nhận diện: ${e.message ?: e.javaClass.simpleName}") }
    }

    private suspend fun record(): ShortArray {
        val total = SAMPLE_RATE * SECONDS
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) throw IOException("thiết bị không hỗ trợ thu âm 16 kHz")
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192) * 2)
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) throw IOException("không mở được micro")
            val out = ShortArray(total); var n = 0
            state.value = State.Recording(0)
            rec.startRecording()
            val t0 = SystemClock.elapsedRealtime()
            while (n < total) {
                currentCoroutineContext().ensureActive()
                val r = rec.read(out, n, minOf(4096, total - n))
                if (r < 0) throw IOException("lỗi đọc micro ($r)")
                n += r
                state.value = State.Recording(n / SAMPLE_RATE)
                if (SystemClock.elapsedRealtime() - t0 > (SECONDS + 5) * 1000L) throw IOException("hết thời gian thu")
            }
            return out
        } finally { runCatching { rec.stop() }; rec.release() }
    }

    /** Gửi tối đa [MusicIdCodec.MAX_ATTEMPTS] lần (chỉ lỗi mạng/502/503/504 mới thử lại). Khoá và nội dung âm thanh không bao giờ được ghi log. */
    private suspend fun post(key: String, wav: ByteArray): MusicIdCodec.Result {
        val client = Http.client.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        var attempt = 0
        while (true) {
            attempt++
            AppLog.i("MusicId", "gửi ${wav.size / 1024} KB âm thanh tới AudD (lần $attempt)")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("api_token", key)
                .addFormDataPart("file", "clip.wav", wav.toRequestBody("audio/wav".toMediaType())).build()
            val rq = Request.Builder().url("https://api.audd.io/").post(body).build()
            val got: Pair<Int, String>? = try {
                client.newCall(rq).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                if (MusicIdCodec.shouldRetry(0, true, attempt)) null else throw e
            }
            if (got == null || MusicIdCodec.shouldRetry(got.first, false, attempt)) { delay(1000L * attempt); continue }
            return MusicIdCodec.parseAudd(got.second)
        }
    }
}
