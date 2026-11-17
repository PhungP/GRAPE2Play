@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.grape.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Giao diện "Nhôm phay xước" (màn Đang phát, TV): mặt nhôm vân xước bao trọn màn hình; các khối nội dung là màn hình đen LÕM vào nhôm
 * (viền kim loại + bóng đổ trong); nút điều khiển là nút nhôm tròn NỔI khối; logo và huy hiệu khắc laser chìm trên mặt nhôm.
 */

internal val AluYellow = Color(0xFFFFD91F)
internal val AluBlack = Color(0xFF0A0A0B)
/** Kích thước chung (dp) của bố cục TV: mọi khe giữa các màn hình bằng nhau, mép trên/dưới thẳng hàng. */
internal val AluGap = 16.dp
internal val AluRailW = 192.dp
internal val AluStripH = 28.dp
internal val AluCoverH = 420.dp
internal val AluBoxesH = 184.dp
internal val AluLine = 1.5.dp
internal val AluMergedH = AluCoverH + AluLine + AluBoxesH
internal val AluWaveH = 80.dp
internal val AluScreensH = AluMergedH + AluGap + AluWaveH
internal val AluInk = Color(0xFF1A1A1A)
/** Mực nâu đậm in trên kính hổ phách. */
internal val AluAmberInk: Color get() = AluTheme.g.ink
internal val AluLit: Color get() = AluTheme.g.lit

/**
 * Mặt máy theo chất liệu đang chọn ([AluTheme.m]): phay xước (hàng nghìn vệt ngang dài ngắn không đều + dải phản chiếu),
 * bóng (dải phản chiếu chéo mượt, không vân), nhám (hạt mịn). Có mép vát sáng trên - tối dưới. Dùng hạt giống cố định nên không nhấp nháy.
 */
internal fun Modifier.brushedAluminum(): Modifier = drawBehind {
    val m = AluTheme.m
    drawRect(Brush.verticalGradient(m.stops))
    val rnd = java.util.Random(11)
    when (m.style) {
        0 -> {
            drawRect(Brush.horizontalGradient(0f to Color(0x00FFFFFF), 0.16f to Color(0x38FFFFFF), 0.34f to Color(0x00FFFFFF), 0.60f to Color(0x00FFFFFF), 0.82f to Color(0x30FFFFFF), 1f to Color(0x00FFFFFF)))
            val st = 0.8.dp.toPx().coerceAtLeast(1f)
            var y = 0f
            while (y < size.height) {
                for (k in 0..1) {
                    val a = rnd.nextFloat()
                    val len = size.width * (0.2f + 0.8f * rnd.nextFloat())
                    val x0 = (size.width - len) * rnd.nextFloat()
                    val col = if (a > 0.45f) Color.White.copy(alpha = 0.03f + m.streakLight * a) else Color.Black.copy(alpha = 0.02f + m.streakDark * (1f - a))
                    drawLine(col, Offset(x0, y + k * st * 0.5f), Offset(x0 + len, y + k * st * 0.5f), 1f)
                }
                y += st
            }
        }
        1 -> {
            drawRect(Brush.linearGradient(0f to Color.Transparent, 0.22f to Color(0x66FFFFFF), 0.30f to Color(0x00FFFFFF), 0.58f to Color(0x00FFFFFF), 0.66f to Color(0x55FFFFFF), 0.80f to Color(0x00FFFFFF), 1f to Color(0x22000000), start = Offset(0f, 0f), end = Offset(size.width, size.height * 0.7f)))
            drawRect(Brush.verticalGradient(0f to Color(0x44FFFFFF), 0.12f to Color.Transparent, 0.88f to Color.Transparent, 1f to Color(0x30000000)))
        }
        else -> {
            val n = (size.width * size.height / 260f).toInt().coerceIn(2000, 14000)
            val lt = ArrayList<Offset>(n); val dk = ArrayList<Offset>(n)
            repeat(n) { val o = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height); if (rnd.nextBoolean()) lt.add(o) else dk.add(o) }
            drawPoints(lt, androidx.compose.ui.graphics.PointMode.Points, Color.White.copy(alpha = 0.22f), 1.6f)
            drawPoints(dk, androidx.compose.ui.graphics.PointMode.Points, Color.Black.copy(alpha = 0.10f), 1.6f)
        }
    }
    drawRect(m.bevelTop.copy(alpha = 0.6f), Offset.Zero, Size(size.width, 2.dp.toPx()))
    drawRect(Color(0x33000000), Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
}

/** Màn hình kính hổ phách có đèn hắt từ dưới, lõm trong khối nhôm: vệt sáng mép dưới, hai vòng viền kim loại, kính sáng + bóng đổ trong + vệt phản chiếu. */
internal fun Modifier.aluScreen(radius: Dp = 4.dp, dark: Boolean = false): Modifier = drawBehind {
    val r = radius.toPx(); val b1 = 2.dp.toPx(); val b2 = 3.dp.toPx()
    drawRoundRect(AluTheme.m.lip.copy(alpha = 0.7f), Offset(-b2, -b2 + 1.5.dp.toPx()), Size(size.width + 2 * b2, size.height + 2 * b2), CornerRadius(r + b2))
    drawRoundRect(Color(0xFF4A4E53), Offset(-b2, -b2), Size(size.width + 2 * b2, size.height + 2 * b2), CornerRadius(r + b2))
    drawRoundRect(Color(0xFF8E949A), Offset(-b1, -b1), Size(size.width + 2 * b1, size.height + 2 * b1), CornerRadius(r + b1))
    if (dark) drawRoundRect(AluBlack, Offset.Zero, size, CornerRadius(r)) else {
    drawRoundRect(Brush.verticalGradient(listOf(AluTheme.g.g1, AluTheme.g.g2, AluTheme.g.g3)), Offset.Zero, size, CornerRadius(r))
    drawRoundRect(Brush.radialGradient(listOf(AluTheme.g.glow, Color.Transparent), center = Offset(size.width / 2f, size.height * 1.05f), radius = size.width * 0.6f), Offset.Zero, size, CornerRadius(r)) }
    drawRoundRect(Brush.verticalGradient(listOf(Color(0x80000000), Color.Transparent), endY = 12.dp.toPx()), Offset.Zero, size, CornerRadius(r))
    drawRoundRect(Brush.linearGradient(listOf(Color(0x47FFFFFF), Color.Transparent), Offset.Zero, Offset(size.width * .45f, size.height * .6f)), Offset.Zero, size, CornerRadius(r))
}

/**
 * Nút nhôm cơ nổi khối: đế bóng đổ gọn, viền vát (sáng trên / tối dưới), mặt nhôm lõm nhẹ có vân đồng tâm, vệt phản chiếu.
 * Không có quầng sáng: nút bật = chấm đèn vàng, nút tắt = chấm đen.
 */
private fun Modifier.aluButtonFace(active: Boolean): Modifier = drawBehind {
    val m = AluTheme.m
    val r = size.minDimension / 2f
    val c = center
    for (i in 3 downTo 1) drawCircle(Color(0x1A000000), r + i * 1.2.dp.toPx(), Offset(c.x, c.y + 2.5.dp.toPx()))
    drawCircle(m.lip, r + 2.4.dp.toPx(), Offset(c.x, c.y + 1.dp.toPx()))
    drawCircle(Brush.verticalGradient(listOf(m.bevelTop, m.bevelBot), startY = c.y - r, endY = c.y + r), r + 1.8.dp.toPx(), c)
    drawCircle(Color(0xFF3F4347), r + 0.2.dp.toPx(), c)
    val face = r - 1.2.dp.toPx()
    drawCircle(Brush.linearGradient(listOf(m.btn1, m.btn2, m.btn3), Offset(c.x - face * .6f, c.y - face), Offset(c.x + face * .6f, c.y + face)), face, c)
    for (k in 1..9) drawCircle(if (k % 2 == 0) Color(0x1AFFFFFF) else Color(0x12000000), face * k / 10f, c, style = Stroke(1f))
    drawCircle(Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color.Transparent), startY = c.y - face, endY = c.y), face * 0.92f, Offset(c.x, c.y - face * 0.04f), style = Stroke(1.2.dp.toPx()))
    val led = Offset(c.x, c.y + face * 0.7f)
    if (active) {
        drawCircle(Color(0xFF8A6A00), 4.2.dp.toPx(), led)
        drawCircle(Color(0xFFFFC21A), 3.4.dp.toPx(), led)
        drawCircle(Color(0xFFFFF3B0), 1.5.dp.toPx(), Offset(led.x - .6.dp.toPx(), led.y - .6.dp.toPx()))
    } else {
        drawCircle(Color(0x66FFFFFF), 4.2.dp.toPx(), Offset(led.x, led.y + .8.dp.toPx()))
        drawCircle(Color(0xFF1B1D20), 3.4.dp.toPx(), led)
    }
}

/** Nút nhôm nổi khối focus được bằng remote (viền tối khi focus). [size] = đường kính mặt nút. */
@Composable
internal fun AluButton(size: Dp, active: Boolean, icon: ImageVector, desc: String, requester: FocusRequester? = null, onClick: () -> Unit) =
    Focusable(onClick, Modifier.size(size + 12.dp), CircleShape, ring = AluTheme.g.selBg, requester = requester, selColor = Color.Transparent) {
        Box(Modifier.align(Alignment.Center).size(size).aluButtonFace(active), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = AluTheme.m.btnIcon, modifier = Modifier.size(size * 0.42f).offset(y = (-size.value * 0.05f).dp))
        }
    }

@Composable
internal fun AluScrew(modifier: Modifier) = Canvas(modifier.size(11.dp)) {
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFAEB3B8), Color(0xFF565A5F), Color(0xFF222222)), center = Offset(size.width * .35f, size.height * .3f), radius = size.width * .9f))
    drawLine(Color(0xFF202326), Offset(size.width * .2f, size.height * .62f), Offset(size.width * .8f, size.height * .38f), 1.5.dp.toPx(), StrokeCap.Round)
}

/**
 * Cột trái giao diện Nhôm: logo (khắc chìm) trên mặt nhôm, màn hình đen chứa menu từ Đang phát đến Quét lại,
 * huy hiệu Limited Edition (khắc chìm), nút Bật/Tắt (thoát app) nổi khối. Đèn vàng trên nút = app đang chạy.
 */
@Composable
internal fun AluRail(cur: Screen, onNav: (Screen) -> Unit, remoteActive: Boolean) {
    val ctx = LocalContext.current
    val scanning by Library.scanning.collectAsState()
    val count by Library.tracks.collectAsState()
    Column(Modifier.width(AluRailW).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Màn hình menu cao đúng bằng màn hình Danh sách phát: logo + tên app + Limited Edition ở trên cùng, các mục menu xếp tiếp bên dưới
        Column(Modifier.fillMaxWidth().height(AluScreensH).aluScreen().padding(8.dp, 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GrapeLogo(Modifier.size(64.dp))
            Wordmark(17, Modifier.padding(top = 6.dp), AluAmberInk)
            AluBadge()
            Spacer(Modifier.height(12.dp))
            AluDivH()
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
                AluMenuItem(Icons.Outlined.MusicNote, "Đang phát", cur == Screen.NOW) { onNav(Screen.NOW) }
                AluMenuItem(Icons.Outlined.GridView, "Thư viện", cur == Screen.LIB) { onNav(Screen.LIB) }
                AluMenuItem(Icons.Outlined.CloudQueue, "Nguồn nhạc", cur == Screen.SOURCES) { onNav(Screen.SOURCES) }
                AluMenuItem(Icons.Outlined.Cast, "Đầu ra", cur == Screen.OUTPUT, highlight = remoteActive) { onNav(Screen.OUTPUT) }
                AluMenuItem(Icons.Outlined.Equalizer, "Phổ tần", false) { Overlay.spectrum.value = true }
                AluMenuItem(Icons.Outlined.Tune, "Cài đặt", cur == Screen.SOUND) { onNav(Screen.SOUND) }
                if (scanning) T("Đang quét… ${count.size} bài", 13, AluAmberInk, Modifier.padding(12.dp, 10.dp))
                else AluMenuItem(Icons.Outlined.Refresh, "Quét lại", false) { Library.rescanAllAsync(ctx.applicationContext) }
            }
        }
        // Hàng nút cứng: nút Bật/Tắt nằm giữa khoảng từ mép dưới màn hình tới mép ngoài dải nhôm (cùng đường ngang với hàng nút phát nhạc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AluButton(64.dp, true, Icons.Outlined.PowerSettingsNew, "Bật / Tắt") { Overlay.exit.value = true }
        }
    }
}

/** Huy hiệu Limited Edition in trên màn hình kính (chữ serif nghiêng cùng màu mực của kính, khung mảnh). */
@Composable
private fun AluBadge() = LimitedArt(112.dp, AluAmberInk)

/** Chữ "Đầu ra · tên ▾" (mực đen, như bản gốc) ở góc trên bên phải cột chính; bấm để đổi thiết bị phát. */
@Composable
internal fun AluOutputLabel(outName: String, onOut: () -> Unit) =
    Focusable(onOut, shape = RoundedCornerShape(6.dp), ring = AluTheme.g.selBg, selColor = AluAmberInk.copy(alpha = 0.25f)) {
        androidx.compose.material3.Text(
            Lang.tr("Đầu ra") + " · " + outName + "  ▾", modifier = Modifier.padding(14.dp, 4.dp), maxLines = 1,
            style = TextStyle(color = AluTheme.m.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
    }

@Composable
private fun AluMenuItem(icon: ImageVector, label: String, selected: Boolean, highlight: Boolean = false, onClick: () -> Unit) =
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(3.dp), selected = selected, ring = AluLit, selColor = AluTheme.g.selBg) { f ->
        Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val col = if (selected || f) AluLit else if (highlight) AluTheme.g.warn else AluAmberInk
            Icon(icon, null, tint = col, modifier = Modifier.size(22.dp))
            T(label, 16, col, bold = selected || f || highlight)
        }
    }

/**
 * Cửa sổ VU liền khối kiểu máy thật: một tấm kính hổ phách chứa hai đồng hồ L/R, vạch chia giữa và chữ "VU".
 * Thang -40…+3 dB, vạch đỏ từ 0 dB; kim đỏ chạy mượt theo mức đỉnh thật của luồng PCM.
 */
@Composable
internal fun AluVuWindow(left: Float, right: Float, modifier: Modifier, framed: Boolean = true) {
    Box(modifier.then(if (framed) Modifier.aluScreen() else Modifier)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            AluMeter("L", left, Modifier.weight(1f).fillMaxHeight())
            AluMeter("R", right, Modifier.weight(1f).fillMaxHeight())
        }
        Box(Modifier.align(Alignment.Center).fillMaxHeight(0.8f).width(1.5.dp).background(Brush.verticalGradient(listOf(Color.Transparent, AluTheme.g.divider, Color.Transparent))))
        T("VU", 15, AluAmberInk, Modifier.align(Alignment.Center).offset(y = 28.dp), bold = true, spaced = true)
    }
}

@Composable
private fun AluMeter(label: String, level: Float, modifier: Modifier) {
    val db = if (level <= 0.0005f) -60f else (20f * kotlin.math.log10(level)).coerceAtMost(6f)
    val frac by animateFloatAsState(((db + 40f) / 43f).coerceIn(0f, 1f), tween(140), label = "vuA")
    val red = AluTheme.g.zone
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 1.02f
            val r = minOf(size.width * 0.42f, size.height * 0.95f)
            val half = 55f
            fun ang(f: Float) = Math.toRadians((-half + 2 * half * f).toDouble())
            val ink = AluAmberInk
            drawArc(ink, -90f - half, 2 * half, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(2.dp.toPx()))
            val r2 = r * 0.9f
            drawArc(ink, -90f - half, 2 * half, false, Offset(cx - r2, cy - r2), Size(2 * r2, 2 * r2), style = Stroke(1.dp.toPx()))
            val redF = 40f / 43f
            drawArc(red, -90f - half + 2 * half * redF, 2 * half * (1f - redF), false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(5.dp.toPx()))
            for (k in 0..43) {   // vạch nhỏ mỗi dB (kim 1/3 vạch lớn)
                val a = ang(k / 43f); val sn = Math.sin(a).toFloat(); val cs = Math.cos(a).toFloat()
                val d = k - 40; val big = d % 10 == 0 || d == 0 || d == 3
                val len = if (big) 0.88f else 0.95f
                drawLine(if (d > 0) red else ink, Offset(cx + sn * r * len, cy - cs * r * len), Offset(cx + sn * r, cy - cs * r), if (big) 2.dp.toPx() else 0.8.dp.toPx())
            }
            val a = ang(frac); val sn = Math.sin(a).toFloat(); val cs = Math.cos(a).toFloat()
            drawLine(Color(0x33000000), Offset(cx + 1.5.dp.toPx(), cy + 1.5.dp.toPx()), Offset(cx + sn * r * 0.96f + 1.5.dp.toPx(), cy - cs * r * 0.96f + 1.5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(AluTheme.g.zone, Offset(cx, cy), Offset(cx + sn * r * 0.96f, cy - cs * r * 0.96f), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(ink, 6.dp.toPx(), Offset(cx, cy))
        }
        T("dB", 13, AluAmberInk, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
        T("PEAK POWER LEVEL", 9, AluAmberInk, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), spaced = true)
        T(label, 16, AluAmberInk, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 4.dp), bold = true)
        T(if (db <= -60f) "-∞ dB" else "%.1f dB".format(db), 12, AluTheme.g.muted, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 2.dp))
    }
}

/** Danh sách phát: màn hình kính hổ phách trong khối nhôm; bài đang phát là thanh tối chữ vàng sáng. */
@Composable
internal fun AluQueue(queue: List<Track>, idx: Int, remoteActive: Boolean, onOut: () -> Unit) {
    val st = rememberLazyListState()
    LaunchedEffect(idx, queue.size) { if (queue.isNotEmpty()) st.animateScrollToItem(idx.coerceIn(0, queue.lastIndex)) }
    val total = remember(queue) { queue.sumOf { it.durationMs } }
    Column(Modifier.width(430.dp).fillMaxHeight()) {
    Column(Modifier.fillMaxWidth().height(AluScreensH).aluScreen().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            T("Danh sách phát", 28, AluAmberInk, serif = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Focusable({ Playback.shuffleQueue() }, ring = AluLit) { T("⤮ Trộn", 13, AluAmberInk, Modifier.padding(10.dp, 4.dp), bold = true) }
                T("${queue.size} bài · ${fmt(total)}", 13, AluTheme.g.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (queue.isEmpty()) T("Danh sách đang trống", 18, AluTheme.g.muted)
        LazyColumn(state = st, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(queue) { i, t ->
                val cur = i == idx
                Focusable({ Playback.jump(i) }, Modifier.fillMaxWidth(), RoundedCornerShape(4.dp), selected = cur, ring = AluLit, selColor = AluTheme.g.selBg) { f ->
                    val lit = cur || f
                    val main = if (lit) AluLit else AluAmberInk
                    val sub = if (lit) AluTheme.g.litSub else AluTheme.g.muted
                    Row(
                        Modifier.fillMaxWidth().padding(7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        T("${i + 1}", 13, sub, Modifier.width(24.dp))
                        CoverImage(t, Modifier.size(38.dp), 3)
                        Column(Modifier.weight(1f)) {
                            T(t.title, 16, main, bold = lit)
                            T("${t.artist} · ${t.ext.uppercase()}", 12, sub)
                        }
                        T(fmt(t.durationMs), 12, sub)
                    }
                }
            }
        }
    }
    // Nút Đầu ra (đèn sáng khi đang phát tới thiết bị mạng) — cùng hàng với các nút cứng khác
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        AluButton(56.dp, remoteActive, Icons.Outlined.Cast, "Đầu ra", onClick = onOut)
    }
    }
}

/** Vạch phân cách mờ dọc / ngang trong màn hình kính: đậm ở giữa, tan dần ở hai đầu (cùng phong cách mọi chỗ). */
@Composable
internal fun AluDivV(modifier: Modifier = Modifier) =
    Box(modifier.width(AluLine).background(Brush.verticalGradient(listOf(Color.Transparent, AluTheme.g.divider, Color.Transparent))))

@Composable
internal fun AluDivH(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(AluLine).background(Brush.horizontalGradient(listOf(Color.Transparent, AluTheme.g.divider, Color.Transparent))))

/** Thẻ Cài đặt: chọn màu kính hiển thị và chất liệu mặt máy cho giao diện Nhôm. */
@Composable
fun AluStyleCard() {
    val gi by AluTheme.glass
    val mi by AluTheme.metal
    Card {
        T("NHÔM PHAY XƯỚC — MÀU KÍNH HIỂN THỊ", 13, C.Muted, spaced = true)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AluTheme.GLASS.forEachIndexed { i, g ->
                Focusable({ AluTheme.setGlass(i) }, shape = RoundedCornerShape(8.dp), selected = gi == i) {
                    Row(Modifier.padding(12.dp, 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(g.g1, g.g3))).border(2.dp, if (gi == i) C.Gold else Color(0x66FFFFFF), CircleShape))
                        T(g.name, 17, if (gi == i) C.Gold else C.Text)
                    }
                }
            }
        }
        T("CHẤT LIỆU MẶT MÁY", 13, C.Muted, spaced = true)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AluTheme.METAL.forEachIndexed { i, m ->
                Focusable({ AluTheme.setMetal(i) }, shape = RoundedCornerShape(8.dp), selected = mi == i) {
                    Row(Modifier.padding(12.dp, 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.verticalGradient(m.stops)).border(2.dp, if (mi == i) C.Gold else Color(0x66FFFFFF), CircleShape))
                        T(m.name, 17, if (mi == i) C.Gold else C.Text)
                    }
                }
            }
        }
    }
}
