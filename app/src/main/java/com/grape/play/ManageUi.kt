package com.grape.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Menu hành động cho một bài (giữ nút OK / nhấn giữ): yêu thích, phát tiếp theo, thêm vào hàng đợi/playlist; trong hàng đợi: chuyển lên/xuống, xoá một hoặc nhiều bài;
 * trong playlist: chuyển lên/xuống, xoá khỏi playlist. Logic chỉ số: [QueueOps]/[PlaylistOps] (có test).
 */
object TrackMenu {
    sealed class Ctx {
        object Library : Ctx()
        class Queue(val index: Int) : Ctx()
        /** [at] = mã playlist (thời điểm tạo), [index] = vị trí trong danh sách đường dẫn của playlist. */
        class InPlaylist(val at: Long, val index: Int) : Ctx()
    }
    class Target(val t: Track, val ctx: Ctx)
    val target = mutableStateOf<Target?>(null)
    val pickPlaylist = mutableStateOf<Track?>(null)
    fun open(t: Track, ctx: Ctx = Ctx.Library) { target.value = Target(t, ctx) }
}

@Composable
private fun MenuItem(label: String, requester: FocusRequester? = null, color: Color = C.Text, onClick: () -> Unit) =
    Focusable(onClick, Modifier.fillMaxWidth(), requester = requester) { T(label, 17, color, Modifier.padding(16.dp, 10.dp), lines = 2) }

@Composable
fun TrackMenuHost() {
    val tg by TrackMenu.target
    val pick by TrackMenu.pickPlaylist
    tg?.let { TrackMenuDialog(it) }
    pick?.let { PlaylistPickDialog(it) }
}

@Composable
private fun TrackMenuDialog(tg: TrackMenu.Target) {
    val t = tg.t
    val first = remember(tg) { FocusRequester() }
    LaunchedEffect(tg) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ hộp thoại dựng xong rồi mới đặt tiêu điểm D-pad
    val fav = t.path in Favorites.paths.value
    val queue by Playback.queue.collectAsState()
    val cur by Playback.index.collectAsState()
    fun close() { TrackMenu.target.value = null }
    Frame(onDismiss = ::close) {
        T(t.title, 20, serif = true, lines = 2)
        T(t.artist, 14, C.Muted, lines = 1)
        MenuItem(if (fav) "♥  Bỏ yêu thích" else "♡  Yêu thích", first) { Favorites.toggle(t); close() }
        when (val c = tg.ctx) {
            is TrackMenu.Ctx.Queue -> {
                val i = c.index; val n = queue.size
                if (i != cur && i != cur + 1 && i in 0 until n) MenuItem("⏭  Phát tiếp theo") { Playback.moveToNext(i); close() }
                if (i > 0) MenuItem("⬆  Chuyển lên") { Playback.moveInQueue(i, i - 1); close() }
                if (i < n - 1) MenuItem("⬇  Chuyển xuống") { Playback.moveInQueue(i, i + 1); close() }
                MenuItem("✕  Xoá bài này khỏi danh sách phát", color = C.Gold) { Playback.removeFromQueue(intArrayOf(i)); close() }
                if (i > 0) MenuItem("✕  Xoá các bài phía trên (${i} bài)", color = C.Gold) { Playback.removeFromQueue(QueueOps.above(i)); close() }
                if (i < n - 1) MenuItem("✕  Xoá các bài phía dưới (${n - 1 - i} bài)", color = C.Gold) { Playback.removeFromQueue(QueueOps.below(n, i)); close() }
                if (n > 1) MenuItem("✕  Xoá tất cả, trừ bài đang phát", color = C.Gold) { Playback.removeFromQueue(QueueOps.allExcept(n, cur)); close() }
            }
            else -> {
                MenuItem("⏭  Phát tiếp theo") { Playback.playNext(listOf(t)); close() }
                MenuItem("➕  Thêm vào cuối danh sách phát") { if (queue.isEmpty()) Playback.play(listOf(t), 0) else Playback.append(listOf(t)); close() }
                if (c is TrackMenu.Ctx.InPlaylist) {
                    val p = UserPlaylists.list.firstOrNull { it.at == c.at }
                    if (p != null) {
                        if (c.index > 0) MenuItem("⬆  Chuyển lên trong playlist") { UserPlaylists.moveTrack(p, c.index, c.index - 1); close() }
                        if (c.index < p.paths.size - 1) MenuItem("⬇  Chuyển xuống trong playlist") { UserPlaylists.moveTrack(p, c.index, c.index + 1); close() }
                        MenuItem("✕  Xoá khỏi playlist “${p.name}”", color = C.Gold) { UserPlaylists.removeAt(p, c.index); close() }
                    }
                }
            }
        }
        MenuItem("📂  Thêm vào playlist…") { close(); TrackMenu.pickPlaylist.value = t }
        MenuItem("Đóng", color = C.Muted) { close() }
    }
}

@Composable
private fun PlaylistPickDialog(t: Track) {
    val first = remember(t) { FocusRequester() }
    LaunchedEffect(t) { delay(150); runCatching { first.requestFocus() } }
    var name by remember(t) { mutableStateOf("") }
    val lists = UserPlaylists.list
    fun close() { TrackMenu.pickPlaylist.value = null }
    Frame(onDismiss = ::close) {
        T("Thêm vào playlist", 20, serif = true)
        T(t.title, 14, C.Muted, lines = 1)
        lists.forEachIndexed { i, p ->
            MenuItem("${p.name}  ·  ${p.paths.size} bài", if (i == 0) first else null) { UserPlaylists.addTracks(p, listOf(t)); close() }
        }
        DevField("Playlist mới — tên", name, startOpen = lists.isEmpty()) { name = it }
        MenuItem("＋  Tạo playlist “${PlaylistOps.cleanName(name)}” và thêm bài", if (lists.isEmpty()) first else null, C.Gold) { UserPlaylists.create(name, listOf(t)); close() }
        MenuItem("Đóng", color = C.Muted) { close() }
    }
}

// ------------------------------------------------------------------ Mới thêm
/** Bài mới thêm: theo ngày sửa file (chỉ có với nguồn cục bộ/USB; nguồn từ xa không có ngày). 300 bài mới nhất; tính ở luồng nền. */
@Composable
fun RecentView(all: List<Track>, cur: Track?, spacing: Dp, onNav: (Screen) -> Unit) {
    val list by produceState(emptyList<Track>(), all) { value = withContext(Dispatchers.Default) { all.filter { it.mtime > 0 }.sortedByDescending { it.mtime }.take(300) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("Mới thêm vào thư viện (theo ngày sửa file; không áp dụng cho nguồn từ xa) · ${list.size} bài", 14, C.Muted, lines = 2)
        if (list.isEmpty()) T("Chưa có bài nào có ngày sửa file.", 16, C.Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing)) {
            itemsIndexed(list) { i, t -> TrackRow(i, t, t.path == cur?.path) { Playback.play(list, i); onNav(Screen.NOW) } }
        }
    }
}

// ------------------------------------------------------------------ Lịch sử
private fun dayLabel(at: Long): String {
    val now = java.util.Calendar.getInstance(); val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
    return if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR))
        "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    else "%02d/%02d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1)
}

@Composable
fun HistoryView(all: List<Track>, cur: Track?, spacing: Dp, onNav: (Screen) -> Unit) {
    val h by PlayHistory.entries.collectAsState()
    val rows by produceState(emptyList<Pair<Track, Long>>(), all, h) { value = withContext(Dispatchers.Default) { PlayHistory.resolve(all).take(500) } }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Lịch sử nghe · ${h.size} lần (hiện ${rows.size} gần nhất còn trong thư viện)", 14, C.Muted, Modifier.weight(1f), lines = 2)
            if (h.isNotEmpty()) Focusable({ if (confirm) { PlayHistory.clear(); confirm = false } else confirm = true }) {
                T(if (confirm) "Bấm lần nữa để xoá lịch sử" else "Xoá lịch sử", 14, C.Gold, Modifier.padding(12.dp, 6.dp))
            }
        }
        if (rows.isEmpty()) T("Chưa có lịch sử nghe.", 16, C.Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing)) {
            itemsIndexed(rows) { i, (t, at) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(dayLabel(at), 12, C.Muted, Modifier.width(52.dp))
                    Box(Modifier.weight(1f)) { TrackRow(i, t, t.path == cur?.path, compact = true) { Playback.play(rows.map { it.first }, i); onNav(Screen.NOW) } }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Playlist
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlaylistsView(all: List<Track>, cur: Track?, spacing: Dp, onNav: (Screen) -> Unit) {
    val pls = UserPlaylists.list
    var selAt by remember { mutableStateOf<Long?>(null) }
    val sel = selAt?.let { a -> pls.firstOrNull { it.at == a } }
    BackHandler(enabled = sel != null) { selAt = null }
    val byPath = remember(all) { HashMap<String, Track>(all.size * 2).also { m -> for (t in all) m.putIfAbsent(t.path, t) } }
    if (sel == null) {
        var name by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DevField("Tạo playlist mới — tên", name) { name = it }
            Focusable({ UserPlaylists.create(name); name = "" }) { T("＋ Tạo playlist rỗng", 16, C.Gold, Modifier.padding(14.dp, 8.dp), bold = true) }
            if (pls.isEmpty()) T("Chưa có playlist. Tạo ở đây, hoặc giữ nút OK trên một bài → “Thêm vào playlist…”.", 15, C.Muted, lines = 3)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing)) {
                itemsIndexed(pls.toList()) { _, p ->
                    Focusable({ selAt = p.at }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp, 10.dp)) { T(p.name, 20, lines = 1); T("${p.paths.size} bài", 14, C.Muted) }
                    }
                }
            }
        }
    } else {
        var rename by remember(sel.at) { mutableStateOf(false) }
        var newName by remember(sel.at) { mutableStateOf(sel.name) }
        var confirmDel by remember(sel.at) { mutableStateOf(false) }
        val rows = remember(sel, byPath) { sel.paths.mapIndexedNotNull { i, p -> byPath[p]?.let { i to it } } }
        val missing = sel.paths.size - rows.size
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(sel.name, 24, serif = true, lines = 1)
            T("${sel.paths.size} bài" + (if (missing > 0) " · $missing bài hiện không có trong thư viện (được giữ lại)" else ""), 14, C.Muted, lines = 2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Focusable({ selAt = null }) { T("◀ Danh sách", 15, C.Gold, Modifier.padding(12.dp, 6.dp)) }
                if (rows.isNotEmpty()) {
                    Focusable({ Playback.play(rows.map { it.second }, 0, false); onNav(Screen.NOW) }) { T("▶ Phát", 15, C.Gold, Modifier.padding(12.dp, 6.dp)) }
                    Focusable({ Playback.playMixed(rows.map { it.second }); onNav(Screen.NOW) }) { T("⤮ Trộn", 15, C.Gold, Modifier.padding(12.dp, 6.dp)) }
                    Focusable({ val q = Playback.queue.value; val ts = rows.map { it.second }; if (q.isEmpty()) Playback.play(ts, 0, false) else Playback.append(ts) }) { T("➕ Vào hàng đợi", 15, C.Gold, Modifier.padding(12.dp, 6.dp)) }
                }
                Focusable({ rename = !rename }) { T("Đổi tên", 15, C.Gold, Modifier.padding(12.dp, 6.dp)) }
                Focusable({ if (confirmDel) { UserPlaylists.remove(sel); selAt = null } else confirmDel = true }) {
                    T(if (confirmDel) "Bấm lần nữa để xoá" else "Xoá playlist", 15, C.Gold, Modifier.padding(12.dp, 6.dp))
                }
            }
            if (rename) {
                DevField("Tên playlist", newName, startOpen = true) { newName = it }
                Focusable({ UserPlaylists.rename(sel, newName); rename = false }) { T("Lưu tên", 15, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
            }
            if (rows.isEmpty()) T("Playlist đang trống. Giữ nút OK trên một bài → “Thêm vào playlist…”.", 15, C.Muted, lines = 3)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing)) {
                itemsIndexed(rows) { n, (pi, t) ->
                    TrackRow(n, t, t.path == cur?.path, menu = TrackMenu.Ctx.InPlaylist(sel.at, pi)) { Playback.play(rows.map { it.second }, n, false); onNav(Screen.NOW) }
                }
            }
        }
    }
}
