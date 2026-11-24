@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Thông số xử lý âm thanh do người dùng chỉnh (EQ 10 băng, cân bằng trái/phải, preamp). Lưu vào SharedPreferences. */
object DspParams {
    val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val PRESETS: List<Pair<String, FloatArray>> = listOf(
        "flat" to FloatArray(10),
        "warm" to floatArrayOf(2f, 2f, 1.5f, 1f, 0f, 0f, -0.5f, -1f, -1.5f, -2f),
        "vocal" to floatArrayOf(-2f, -1.5f, -1f, 0f, 1.5f, 2.5f, 2.5f, 1.5f, 0f, -1f),
        "bass" to floatArrayOf(5f, 4.5f, 3.5f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
    )
    val on = MutableStateFlow(true)
    val bands = MutableStateFlow(List(10) { 0f })
    val balance = MutableStateFlow(0f)   // -1 = chỉ trái … +1 = chỉ phải
    val preamp = MutableStateFlow(0f)    // dB
    val version = AtomicInteger(0)
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("dsp", Context.MODE_PRIVATE)
        on.value = sp.getBoolean("on", true)
        bands.value = (sp.getString("bands", "") ?: "").split(',').mapNotNull { it.toFloatOrNull() }.let { if (it.size == 10) it else List(10) { 0f } }
        balance.value = sp.getFloat("bal", 0f); preamp.value = sp.getFloat("pre", 0f)
        version.incrementAndGet()
    }
    private fun save() {
        if (!::sp.isInitialized) return
        sp.edit().putBoolean("on", on.value).putString("bands", bands.value.joinToString(",")).putFloat("bal", balance.value).putFloat("pre", preamp.value).apply()
        version.incrementAndGet()
    }
    fun setOn(v: Boolean) { on.value = v; save() }
    fun setBand(i: Int, db: Float) { bands.value = bands.value.toMutableList().also { it[i] = db.coerceIn(-12f, 12f) }; save() }
    fun setBands(v: FloatArray) { bands.value = v.toList(); save() }
    fun setBalance(v: Float) { balance.value = ((v * 20).toInt() / 20f).coerceIn(-1f, 1f); save() }
    fun setPreamp(v: Float) { preamp.value = v.coerceIn(-12f, 6f); save() }
    fun reset() { bands.value = List(10) { 0f }; balance.value = 0f; preamp.value = 0f; save() }
    fun isFlat() = DspMath.isFlat(on.value, bands.value.toFloatArray(), balance.value, preamp.value)
}

/** EQ 10 băng (biquad peaking, Q≈1.4) + preamp + cân bằng L/R. Khi mọi thứ phẳng thì chép nguyên dữ liệu ở tầng DSP. Phần toán nằm trong DspMath.java (có test). */
class DspProcessor : BaseAudioProcessor() {
    private var kind = DspMath.PCM16
    private var ch = 2
    private var sr = 44100
    private var ver = -1
    private var plan: DspMath.Plan? = null
    private val st = DspMath.State()
    private var wasFlat = true
    /** Số mẫu bị cắt đỉnh (chỉ PCM số nguyên) kể từ lần cấu hình gần nhất — chẩn đoán. */
    @Volatile var clipped = 0L; private set
    /** Mức giảm tự động (dB, ≤ 0) để EQ/preamp nâng không vượt 0 dBFS trên đáp tuyến sin; KHÔNG bảo đảm tuyệt đối với mọi tín hiệu (xung/vuông có thể vẫn vượt). */
    @Volatile var headroomDb = 0.0; private set

    override fun onConfigure(f: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        ch = f.channelCount; sr = f.sampleRate; ver = -1; clipped = 0; st.reset(); wasFlat = true
        if (ch < 1 || ch > 8) return AudioProcessor.AudioFormat.NOT_SET   // quá 8 kênh: bỏ qua EQ thay vì xử lý sai khung
        kind = when (f.encoding) {
            C.ENCODING_PCM_16BIT -> DspMath.PCM16
            C.ENCODING_PCM_24BIT -> DspMath.PCM24
            C.ENCODING_PCM_32BIT -> DspMath.PCM32
            C.ENCODING_PCM_FLOAT -> DspMath.FLOAT
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        return f
    }

    /** Tua/đổi bài làm rỗng bộ đệm: xoá trạng thái lọc để không còn "đuôi" của đoạn cũ gây tiếng lách tách. */
    override fun onFlush() { st.reset() }

    private fun refresh() {
        val v = DspParams.version.get()
        if (v == ver) return
        ver = v
        val pl = DspMath.plan(DspParams.bands.value.toFloatArray(), DspParams.preamp.value, DspParams.balance.value, sr, true)
        plan = pl
        headroomDb = pl.headroomDb
        if (pl.headroomDb < -0.05) AppLog.i("DSP", "Tự giảm %.1f dB để tránh cắt đỉnh (EQ/preamp nâng tối đa +%.1f dB)".format(-pl.headroomDb, pl.peakDb))
    }

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        if (DspParams.isFlat()) { out.put(input); out.flip(); wasFlat = true; return }   // EQ phẳng / DSP tắt: chép nguyên byte
        if (wasFlat) { st.reset(); wasFlat = false }   // vào lại EQ sau đoạn phẳng: không dùng trạng thái cũ
        refresh()
        val pl = plan ?: run { out.put(input); out.flip(); return }
        clipped += DspMath.process(input, out, kind, ch, pl, st)
        out.flip()
    }
}
