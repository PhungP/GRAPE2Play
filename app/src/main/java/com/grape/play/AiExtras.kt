package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================ 8. Màn hình chờ theo bối cảnh

object AiSaver {
    val on = mutableStateOf(false)
    val minutes = mutableStateOf(5)
    val showing = mutableStateOf(false)
    val blurbs = HashMap<String, String>()
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("aisaver", Context.MODE_PRIVATE)
        on.value = sp.getBoolean("on", false); minutes.value = sp.getInt("min", 5)
    }
    fun setOn(v: Boolean) { on.value = v; if (::sp.isInitialized) sp.edit().putBoolean("on", v).apply(); if (!v) showing.value = false }
    fun setMinutes(m: Int) { minutes.value = m; if (::sp.isInitialized) sp.edit().putInt("min", m).apply() }
    fun hide() { showing.value = false; BurnIn.touch() }

    /** Phím điều khiển nhạc / âm lượng không làm tắt màn hình chờ. */
    fun passKey(code: Int) = code in setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )
}

object AiSaverTask {
    suspend fun blurb(t: Track): String {
        val sys = "Viết đúng 2 câu ngắn (tối đa 40 từ) thú vị về album/nghệ sĩ/bản thu được hỏi. Không bịa: nếu không chắc chỉ nói chung về phong cách. Không trích lời bài hát. ${Ai.langHint()}"
        return Ai.generate("Bài: ${t.title}\nNghệ sĩ: ${t.artist}\nAlbum: ${t.album}", sys, temp = 0.5).trim()
    }
}

private val SAVER_ALIGN = listOf(Alignment.Center, Alignment.TopStart, Alignment.BottomEnd, Alignment.TopEnd, Alignment.BottomStart)

/** Chạy nền: không thao tác quá [AiSaver.minutes] phút thì hiện màn hình chờ (nền đen cho OLED, nội dung dịch chỗ chậm). Chạm / bấm phím để thoát. */
@Composable
fun AiSaverHost() {
    val on by AiSaver.on
    val mins by AiSaver.minutes
    val showing by AiSaver.showing
    LaunchedEffect(on, mins) {
        while (on) {
            delay(2000)
            if (!AiSaver.showing.value && System.currentTimeMillis() - BurnIn.lastInput > mins * 60_000L) AiSaver.showing.value = true
        }
        AiSaver.showing.value = false
    }
    if (showing) SaverScreen()
}

@Composable
private fun SaverScreen() {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val t = queue.getOrNull(idx)
    var head by remember { mutableStateOf("") }
    var blurb by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LaunchedEffect(Unit) { head = try { AiContext.headline(AiContext.load()) } catch (e: CancellationException) { throw e } catch (e: Exception) { AiContext.headline(null) } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); step = (step + 1) % SAVER_ALIGN.size; now = System.currentTimeMillis() } }
    LaunchedEffect(t?.album, t?.artist) {
        blurb = ""
        if (t != null && Ai.hasKey.value && t.album.isNotBlank()) {
            val k = t.artist + "|" + t.album
            blurb = AiSaver.blurbs[k] ?: try { AiSaverTask.blurb(t).also { AiSaver.blurbs[k] = it } } catch (e: CancellationException) { throw e } catch (e: Exception) { "" }
        }
    }
    val soft = Color(0xFFD8D8D8).copy(alpha = 0.8f)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(step, animationSpec = tween(2500), label = "saver") { s ->
            Box(Modifier.fillMaxSize().padding(56.dp), contentAlignment = SAVER_ALIGN[s]) {
                Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    T(clock.format(Date(now)), 46, soft)
                    if (t != null) Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(t, Modifier.size(190.dp), 8)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            T(t.title, 28, soft, lines = 2, bold = true)
                            T(t.artist, 20, C.Gold.copy(alpha = 0.8f), lines = 1)
                            if (t.album.isNotBlank()) T(t.album, 15, soft.copy(alpha = 0.6f), lines = 1)
                            if (blurb.isNotEmpty()) T(blurb, 15, soft.copy(alpha = 0.7f), lines = 4)
                        }
                    }
                    if (head.isNotEmpty()) T(head, 14, soft.copy(alpha = 0.6f), lines = 2)
                }
            }
        }
    }
}

// ============================================================ 9. Buổi thử dàn

/** AI dựng buổi thử dàn từ chính thư viện: mỗi mục một album hợp nhóm (giọng hát, trầm, động lực, không gian…), kèm lời nhắc nghe gì. */
object AiTestSet {
    class Item(val category: String, val track: Track, val tip: String)

    suspend fun run(lib: List<Track>, focus: String): List<Item> = aiHeavy { runNow(lib, focus) }

    private fun hiRes(e: AiIndex.Alb) = e.value.any { it.bits > 16 || it.sr > 48000 || it.ext == "dsf" || it.ext == "dff" }

    private suspend fun runNow(lib: List<Track>, focus: String): List<Item> {
        val snap = AiIndex.get(lib)
        val pool = (snap.albums.filter { hiRes(it) }.shuffled().take(80) + snap.albums.shuffled().take(60)).distinctBy { it.key }
        if (pool.size < 3) throw java.io.IOException("Thư viện có quá ít album để dựng buổi thử dàn")
        val lines = pool.mapIndexed { i, e ->
            val t = e.value.first()
            val q = if (t.ext == "dsf" || t.ext == "dff") "DSD" else if (t.bits > 0) "${t.bits}bit/${t.sr / 1000}k" else ""
            "$i|${t.artist}|${t.album}|${t.ext} $q|${e.value.size} bài"
        }
        val sys = "Bạn là chuyên gia hi-fi dựng buổi thử dàn loa/tai nghe. Từ danh sách album (id|nghệ sĩ|album|định dạng/chất lượng|số bài) hãy chọn tối đa 8 mục, mỗi mục một album KHÁC nhau, ứng với các nhóm: " +
            "giọng nữ, giọng nam, guitar/piano acoustic, trầm sâu, động lực (giao hưởng/trống), không gian & sân khấu (live), chi tiết dải cao (violin/cymbal), nhạc đời thường. " +
            "Ưu tiên bản thu tốt, hi-res, nổi tiếng về chất âm có trong danh sách. Chỉ trả JSON {\"items\":[{\"category\":<nhóm>,\"id\":<id>,\"tip\":<tối đa 12 từ: nghe chú ý điều gì>}]}. Chỉ dùng id có trong danh sách. ${Ai.langHint()}"
        val o = Ai.parseJson(Ai.generate("Trọng tâm: $focus\nAlbum:\n${lines.joinToString("\n")}", sys, json = true, temp = 0.6)) as JSONObject
        val arr = o.optJSONArray("items") ?: return emptyList()
        val used = HashSet<Int>(); val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val id = it.optInt("id", -1)
            val alb = pool.getOrNull(id) ?: continue
            if (!used.add(id)) continue
            val ts = alb.value.sortedWith(compareBy({ x -> x.dir }, { x -> x.no }))
            val good = ts.filter { x -> x.durationMs in 120_000..420_000 }.ifEmpty { ts }
            out += Item(it.optString("category").ifBlank { "Thử âm" }, good[good.size / 3], it.optString("tip"))
        }
        return out
    }
}
