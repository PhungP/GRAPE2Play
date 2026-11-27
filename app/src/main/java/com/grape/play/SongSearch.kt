package com.grape.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Tìm bài trong thư viện: chỉ mục dựng một lần cho mỗi danh sách (theo tham chiếu), truy vấn chạy ở luồng nền. Xem [SearchIndex]. */
object SongSearch {
    private var src: List<Track>? = null
    private var idx: SearchIndex? = null
    private const val MAX_RESULTS = 500

    @Synchronized fun search(all: List<Track>, q: String): List<Track> {
        if (src !== all || idx == null) {
            idx = SearchIndex(all.map { arrayOf(it.title, it.artist, it.album, it.albumArtist + " " + it.genre + " " + it.composer) })
            src = all
        }
        return idx!!.search(q, MAX_RESULTS).map { all[it] }
    }
}

/** Danh sách "Bài hát" kèm ô tìm kiếm (dùng cho cả giao diện TV và điện thoại). Ô trống = danh sách đầy đủ như trước. */
@Composable
fun SongList(all: List<Track>, cur: Track?, spacing: Dp, onNav: (Screen) -> Unit) {
    var q by remember { mutableStateOf("") }
    val base = remember(all) { all.sortedBy { it.title.lowercase() } }
    val shown by produceState(base, base, q) {
        if (q.isBlank()) value = base
        else { delay(200); value = withContext(Dispatchers.Default) { SongSearch.search(all, q) } }     // debounce + luồng nền
    }
    Column {
        DevField("Tìm bài, nghệ sĩ, album", q) { q = it }
        if (q.isNotBlank()) T("${shown.size} kết quả", 14, C.Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing)) {
            itemsIndexed(shown) { i, t -> TrackRow(i, t, t.path == cur?.path) { Playback.play(shown, i); onNav(Screen.NOW) } }
        }
    }
}
