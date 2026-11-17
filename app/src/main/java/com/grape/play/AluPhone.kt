package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/*
 * Giao diện "Nhôm phay xước" cho điện thoại / máy tính bảng: cùng ngôn ngữ thiết kế với bản TV
 * (nhôm phay xước, kính hổ phách, nút nhôm cơ có đèn) nhưng bố cục dọc, chạm để dùng, thanh điều hướng đặt trên cùng.
 * Không hiện thông tin chỉ có trên TV (cổng quang, HDMI, "mặc định hệ thống"...).
 */

private class PNav(val s: Screen, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
private val P_NAV = listOf(
    PNav(Screen.NOW, Icons.Filled.PlayCircle, "Đang phát"),
    PNav(Screen.LIB, Icons.Filled.LibraryMusic, "Thư viện"),
    PNav(Screen.SOURCES, Icons.Filled.Cloud, "Nguồn"),
    PNav(Screen.OUTPUT, Icons.Filled.Speaker, "Đầu ra"),
    PNav(Screen.SOUND, Icons.Filled.Settings, "Cài đặt"),
)

/** Thanh điều hướng trên cùng: màn hình kính hổ phách có 5 mục, mục đang mở là thanh tối chữ vàng sáng. */
@Composable
internal fun AluTopNav(cur: Screen, onNav: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(3.dp).aluScreen().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.padding(start = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) { GrapeLogo(Modifier.size(30.dp), engraved = false); Wordmark(8, color = AluAmberInk); LimitedMini(7, AluAmberInk) }
        P_NAV.forEach { n ->
            val sel = n.s == cur
            Column(
                Modifier.weight(1f).background(if (sel) AluTheme.g.selBg else Color.Transparent, RoundedCornerShape(4.dp))
                    .pointerInput(n.s) { detectTapGestures { onNav(n.s) } }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(n.icon, null, tint = if (sel) AluLit else AluAmberInk, modifier = Modifier.size(20.dp))
                T(n.label, 10, if (sel) AluLit else AluAmberInk, bold = sel)
            }
        }
    }
}

@Composable
internal fun AluPhoneShell(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().brushedAluminum()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        AluScrew(Modifier.align(Alignment.TopStart).padding(5.dp)); AluScrew(Modifier.align(Alignment.TopEnd).padding(5.dp))
        AluScrew(Modifier.align(Alignment.BottomStart).padding(5.dp)); AluScrew(Modifier.align(Alignment.BottomEnd).padding(5.dp))
    }
}

@Composable
private fun APill(text: String) =
    Box(Modifier.border(1.dp, AluAmberInk, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { T(text, 11, AluAmberInk, bold = true) }

/** Màn Đang phát (điện thoại, kiểu Nhôm): ảnh bìa, thông tin, VU, sóng nhạc (chạm để tua), 5 nút nhôm, 4 nút phụ. */
@Composable
internal fun AluPhoneNow(onOut: () -> Unit, onQueue: () -> Unit) {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val playing by Playback.playing.collectAsState()
    val pos by Playback.position.collectAsState()
    val dur by Playback.duration.collectAsState()
    val info by Playback.info.collectAsState()
    val shuffle by Playback.shuffle.collectAsState()
    val repeat by Playback.repeat.collectAsState()
    val remote by Playback.remote.collectAsState()
    val outName by Playback.outName.collectAsState()
    val err by Playback.error.collectAsState()
    val lv by Playback.level.levels.collectAsState()
    val h by RemoteStats.health.collectAsState()
    val t = queue.getOrNull(idx)
    val fav = t != null && t.path in Favorites.paths.value
    val land = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    if (t == null) {
        Box(Modifier.fillMaxSize().padding(3.dp).aluScreen().padding(24.dp), contentAlignment = Alignment.Center) {
            T("Chưa có bài nào đang phát. Mở Thư viện để chọn nhạc.", 18, AluAmberInk, lines = 3)
        }
        return
    }
    val dsd = t.ext == "dsf" || t.ext == "dff"
    val cover: @Composable (Modifier) -> Unit = { m -> CoverImage(t, m, 0) }
    val infoBox: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(12.dp, 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                APill(formatBadge(t)); APill(quality(t, info))
                val i = info
                if (i != null && !dsd && i.sampleRate > 0 && i.bits > 0) APill("${i.bits}-BIT / ${khz(i.sampleRate)}")
            }
            Spacer(Modifier.height(6.dp))
            T(t.title, 22, AluAmberInk, serif = true, lines = 2)
            T(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), 14, AluTheme.g.accent, lines = 2)
            if (remote != null) T("Đang phát tới: $outName", 13, AluAmberInk, Modifier.padding(top = 4.dp), bold = true, lines = 2)
            val e = err
            if (e != null) T(e.lines().first().take(160), 12, AluTheme.g.warn, Modifier.padding(top = 4.dp), lines = 3)
        }
    }
    val meters: @Composable () -> Unit = {
        if (remote == null) AluVuWindow(if (playing) lv.first else 0f, if (playing) lv.second else 0f, Modifier.fillMaxWidth().height(118.dp), framed = false)
        else Column(Modifier.fillMaxWidth().height(118.dp).padding(12.dp, 8.dp)) {
            T("THIẾT BỊ NHẬN", 10, AluTheme.g.muted, spaced = true)
            T(remote?.name ?: "", 18, AluAmberInk, bold = true)
            T((if (remote?.kind == "cast") "Google Cast" else "UPnP / OpenHome") + " · " + (if (playing) "Đang phát" else "Tạm dừng"), 12, AluTheme.g.accent)
            Spacer(Modifier.weight(1f))
            if (h.volume >= 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { T("Âm lượng loa", 12, AluTheme.g.muted); T("${h.volume}%", 12, AluAmberInk, bold = true) }
                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp)) {
                    drawRoundRect(AluAmberInk.copy(alpha = 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    drawRoundRect(AluAmberInk, size = Size(size.width * (h.volume / 100f).coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                }
            }
        }
    }
    val wave: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(10.dp, 6.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).pointerInput(dur) { detectTapGestures { o -> if (dur > 0) Playback.seekTo((o.x / size.width * dur).toLong().coerceIn(0L, dur)) } }) {
                Waveform(t.path.hashCode(), if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, Modifier.fillMaxSize(), AluAmberInk, AluTheme.g.waveBase)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T(fmt(pos), 13, AluTheme.g.muted); T(fmt(dur), 13, AluTheme.g.muted)
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                AluButton(48.dp, shuffle, Icons.Filled.Shuffle, "Ngẫu nhiên") { Playback.toggleShuffle() }
                AluButton(48.dp, false, Icons.Filled.SkipPrevious, "Bài trước") { Playback.prev() }
                AluButton(66.dp, playing, if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Phát / Dừng") { Playback.toggle() }
                AluButton(48.dp, false, Icons.Filled.SkipNext, "Bài sau") { Playback.next() }
                AluButton(48.dp, repeat != Player.REPEAT_MODE_OFF, if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Lặp lại") { Playback.cycleRepeat() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                AluButton(40.dp, fav, if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Yêu thích") { Favorites.toggle(t) }
                AluButton(40.dp, remote != null, Icons.Filled.Speaker, "Đầu ra") { onOut() }
                AluButton(40.dp, false, Icons.Filled.GraphicEq, "Phổ tần") { Overlay.spectrum.value = true }
                AluButton(40.dp, false, Icons.Filled.QueueMusic, "Danh sách") { onQueue() }
            }
        }
    }
    // Một màn hình kính duy nhất: ảnh bìa, thông số, VU/thiết bị nhận, trạng thái tua; ngăn bằng vạch mờ như bản TV
    if (land) Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(3.dp).aluScreen().height(IntrinsicSize.Min)) {
            cover(Modifier.weight(0.8f).aspectRatio(1f))
            AluDivV(Modifier.fillMaxHeight())
            Column(Modifier.weight(1.2f)) { infoBox(); AluDivH(); meters(); AluDivH(); wave() }
        }
        Spacer(Modifier.height(10.dp))
        controls()
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(3.dp).aluScreen()) {
            cover(Modifier.fillMaxWidth().aspectRatio(1.35f)); AluDivH(); infoBox(); AluDivH(); meters(); AluDivH(); wave()
        }
        controls()
    }
}
