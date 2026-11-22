@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Nhớ nơi người dùng đang đứng trong Thư viện (tab, nguồn, thư mục, thư mục DLNA) để quay lại đúng chỗ từ màn Đang phát. */
object LibNav {
    val tab = mutableStateOf(Tab.FOLDER)
    val src = mutableStateOf<String?>(null)
    val live = mutableStateOf(false)
    val crumbs = mutableStateOf<List<String>>(emptyList())
    val dlna = HashMap<String, MutableState<List<Pair<String, String>>>>()
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("libnav", Context.MODE_PRIVATE)
        tab.value = runCatching { Tab.valueOf(sp.getString("tab", "FOLDER") ?: "FOLDER") }.getOrDefault(Tab.FOLDER)
        src.value = sp.getString("src", null)
        live.value = sp.getBoolean("live", false)
        val a = runCatching { JSONArray(sp.getString("crumbs", "[]") ?: "[]") }.getOrDefault(JSONArray())
        crumbs.value = (0 until a.length()).map { a.getString(it) }
    }
    fun persist() {
        if (!::sp.isInitialized) return
        val a = JSONArray(); crumbs.value.forEach { a.put(it) }
        sp.edit().putString("tab", tab.value.name).putString("src", src.value).putBoolean("live", live.value).putString("crumbs", a.toString()).apply()
    }
    fun pickSource(id: String?) { src.value = id; crumbs.value = emptyList(); live.value = id?.let { SourceStore.get(it)?.type == "dlna" } == true }
    fun openFolder(c: List<String>) { tab.value = Tab.FOLDER; src.value = null; live.value = false; crumbs.value = c }
}

/** Thứ tự ưu tiên hiển thị các nguồn (chip ở Thư viện, thư mục gốc). Người dùng đổi bằng nút ▲ ▼ trong hộp thoại "Nguồn hiển thị". */
object SourceOrder {
    val order = mutableStateOf<List<String>>(emptyList())
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("srcorder", Context.MODE_PRIVATE)
        val a = runCatching { JSONArray(sp.getString("v", "[]") ?: "[]") }.getOrDefault(JSONArray())
        order.value = (0 until a.length()).map { a.getString(it) }
    }
    fun ordered(ids: List<String>): List<String> { val o = order.value; return ids.sortedBy { o.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } } }
    fun move(id: String, up: Boolean, allIds: List<String>) {
        val l = ordered(allIds).toMutableList(); val i = l.indexOf(id); val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        order.value = l
        if (::sp.isInitialized) { val a = JSONArray(); l.forEach { a.put(it) }; sp.edit().putString("v", a.toString()).apply() }
    }
    fun rankOfName(name: String): Int {
        val id = SourceStore.list.value.firstOrNull { it.name == name }?.id ?: "local"
        return order.value.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
    }
    /** Các nguồn đang hiện (id, nhãn) theo thứ tự ưu tiên. */
    fun chips(sources: List<Source>, hidden: Set<String>): List<Pair<String, String>> {
        val rows = (listOf("local" to (if (Device.tv) "Bộ nhớ TV & USB" else "Bộ nhớ máy & USB")) + sources.map { it.id to it.name }).filter { it.first !in hidden }
        val ids = ordered(rows.map { it.first })
        return ids.map { id -> rows.first { it.first == id } }
    }
}

/** Thống kê thói quen nghe: số lần phát từng bài, lần nghe gần nhất, thời gian nghe theo ngày. */
object UsageStats {
    class Rec(var plays: Int, var last: Long)
    val tracks = HashMap<String, Rec>()
    val days = HashMap<String, Long>()
    val rev = mutableStateOf(0)
    private lateinit var sp: SharedPreferences
    private var lastPath = ""; private var lastAt = 0L
    private val fmtDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var ticks = 0

    @Synchronized fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)
        runCatching {
            val o = JSONObject(sp.getString("v", "{}") ?: "{}")
            o.optJSONObject("t")?.let { t -> t.keys().forEach { k -> val a = t.getJSONArray(k); tracks[k] = Rec(a.getInt(0), a.getLong(1)) } }
            o.optJSONObject("d")?.let { d -> d.keys().forEach { k -> days[k] = d.getLong(k) } }
        }
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(5000)
                if (Playback.playing.value) { addTime(5000); ticks++; if (ticks % 12 == 0) save() }
            }
        }
    }
    fun dayKey(ms: Long = System.currentTimeMillis()) = fmtDay.format(java.util.Date(ms))
    @Synchronized private fun addTime(ms: Long) { val k = dayKey(); days[k] = (days[k] ?: 0L) + ms; rev.value = rev.value + 1 }
    @Synchronized fun played(path: String) {
        val now = System.currentTimeMillis()
        if (path == lastPath && now - lastAt < 8000) return
        lastPath = path; lastAt = now
        val r = tracks.getOrPut(path) { Rec(0, 0) }; r.plays++; r.last = now
        rev.value = rev.value + 1; save()
    }
    @Synchronized fun save() {
        if (!::sp.isInitialized) return
        val t = JSONObject(); tracks.forEach { (k, v) -> t.put(k, JSONArray().put(v.plays).put(v.last)) }
        val d = JSONObject(); days.entries.sortedByDescending { it.key }.take(120).forEach { d.put(it.key, it.value) }
        sp.edit().putString("v", JSONObject().put("t", t).put("d", d).toString()).apply()
    }
    fun plays(path: String) = tracks[path]?.plays ?: 0
    fun today() = days[dayKey()] ?: 0L
    /** Thời gian nghe của [n] ngày gần nhất (cũ → mới): (nhãn ngày, mili giây). */
    fun lastDays(n: Int): List<Pair<String, Long>> {
        val c = Calendar.getInstance(); val lab = SimpleDateFormat("dd/MM", Locale.US)
        return (n - 1 downTo 0).map { k ->
            val cc = c.clone() as Calendar; cc.add(Calendar.DAY_OF_YEAR, -k)
            lab.format(cc.time) to (days[dayKey(cc.timeInMillis)] ?: 0L)
        }
    }
}

fun fmtHm(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60} giờ ${m % 60} phút" else "$m phút" }

/** Màn "Thói quen": thời gian nghe hôm nay và 7 ngày, nhạc hay phát / ít phát / chưa nghe bao giờ (kèm thư mục chưa nghe để mở). */
@Composable
fun HabitView(all: List<Track>, onNav: (Screen) -> Unit) {
    val compact = LocalCompact.current
    val rev by UsageStats.rev
    val cur = Playback.queue.collectAsState().value.getOrNull(Playback.index.collectAsState().value)
    val hab = remember(all, rev / 4) {
        val top = all.filter { UsageStats.plays(it.path) >= 2 }.sortedByDescending { UsageStats.plays(it.path) }.take(100)
        val rare = all.filter { UsageStats.plays(it.path) == 1 }.sortedBy { UsageStats.tracks[it.path]?.last ?: 0L }.take(100)
        val never = all.filter { UsageStats.plays(it.path) == 0 }
        val playedFolders = all.filter { UsageStats.plays(it.path) > 0 }.map { it.folder }.toSet()
        val nf = never.groupBy { it.folder }.filterKeys { it !in playedFolders }.values.toList().sortedByDescending { it.size }
        Triple(top, rare, Triple(never, nf, 0))
    }
    val (top, rare, nv) = hab; val never = nv.first; val neverFolders = nv.second
    val days = remember(rev) { UsageStats.lastDays(7) }
    val maxMs = (days.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)

    @Composable fun Section(title: String, note: String, list: List<Track>, extra: @Composable RowScope.() -> Unit = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(title, if (compact) 20 else 26, C.Gold, serif = true)
            T(note, 14, C.Muted, lines = 2)
            if (list.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Focusable({ Playback.play(list, 0, false); onNav(Screen.NOW) }) { T("▶ Phát", 17, C.Gold, Modifier.padding(14.dp, 8.dp)) }
                Focusable({ Playback.playMixed(list); onNav(Screen.NOW) }) { T("⤮ Trộn & phát", 17, C.Gold, Modifier.padding(14.dp, 8.dp)) }
                Focusable({ Playback.playShuffled(list); onNav(Screen.NOW) }) { T("🎲 Ngẫu nhiên", 17, C.Gold, Modifier.padding(14.dp, 8.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { extra() }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("THỜI GIAN NGHE", 13, C.Muted, spaced = true)
                T("Hôm nay: ${fmtHm(UsageStats.today())}", if (compact) 22 else 30, C.Text, bold = true)
                Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { (lab, ms) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height((6 + 40f * ms / maxMs).dp).clip(RoundedCornerShape(3.dp)).background(if (ms > 0) C.Gold else C.Dim))
                            T(lab, 11, C.Muted)
                        }
                    }
                }
                T("7 ngày gần nhất: " + fmtHm(days.sumOf { it.second }) + " · " + days.joinToString("  ") { fmtHm(it.second).replace(" giờ ", "g").replace(" phút", "p") }, 13, C.Muted, lines = 2)
            }
        }
        item {
            Section("Nhạc hay phát", "Bài bạn nghe từ 2 lần trở lên, nhiều nhất ở trên. ${top.size} bài.", top) {
                if (top.isNotEmpty()) Focusable({ top.forEach { if (!Favorites.isFav(it.path)) Favorites.toggle(it) } }) { T("♥ Thêm tất cả vào Yêu thích", 17, C.Gold, Modifier.padding(14.dp, 8.dp)) }
            }
        }
        itemsIndexed(top.take(5)) { i, t -> TrackRow(i, t, t.path == cur?.path) { Playback.play(top, i, false); onNav(Screen.NOW) } }
        item { Section("Nhạc ít phát", "Bài bạn mới nghe đúng 1 lần, bài nghe lâu nhất ở trên. ${rare.size} bài.", rare) }
        itemsIndexed(rare.take(5)) { i, t -> TrackRow(i, t, t.path == cur?.path) { Playback.play(rare, i, false); onNav(Screen.NOW) } }
        item {
            Section("Chưa nghe bao giờ", "${never.size} bài · ${neverFolders.size} thư mục chưa phát lần nào. Chọn một thư mục để mở:", never.shuffled().take(30)) {}
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(neverFolders.take(20), key = { it.first().folder }) { g ->
                    val f = g.first()
                    Box(Modifier.width(if (compact) 130.dp else 170.dp)) {
                        FolderTile(segs(f).dropLast(1).lastOrNull() ?: f.album, g.size, f) { LibNav.openFolder(segs(f).dropLast(1)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Gợi ý một dòng ở thư mục gốc: nhắc có thư mục chưa nghe bao giờ. */
@Composable
fun UnplayedHint(all: List<Track>) {
    val rev by UsageStats.rev
    val pick = remember(all, rev / 20) {
        val played = all.filter { UsageStats.plays(it.path) > 0 }.map { it.folder }.toSet()
        if (played.isEmpty()) null else all.filter { UsageStats.plays(it.path) == 0 && it.folder !in played }.groupBy { it.folder }.values.toList().let { if (it.isEmpty()) null else it.random() }
    }
    val g = pick ?: return
    val f = g.first()
    Focusable({ LibNav.openFolder(segs(f).dropLast(1)) }, Modifier.fillMaxWidth()) {
        T("💡 Bạn chưa nghe thư mục “${segs(f).dropLast(1).lastOrNull() ?: f.album}” (${g.size} bài) bao giờ — bấm để mở", 17, C.Gold, Modifier.padding(14.dp, 8.dp), lines = 2)
    }
}
