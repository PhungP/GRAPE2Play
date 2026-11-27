@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/*
 * Bố cục "Robot / Sci-Fi" (HUD): nền lưới + vân quét, khung góc vát có ngoặc góc phát sáng, chữ đơn cách, đồng hồ VU dạng vòng cung chia vạch,
 * nút lục giác có đèn, nút Play có vòng tiến độ. Có bản TV (màn Đang phát) và bản điện thoại; các màn khác của app đổi theo bảng màu của biến thể.
 * Năm biến thể màu: Cyan Tron, Cam Alien, Đỏ HAL, Tím neon, Lục Matrix — đổi toàn bộ màu của app (C.*) nên không sót chi tiết nào.
 */
class SfVariant(val name: String, val pal: Palette)

object SciFi {
    val VARIANTS = listOf(
        SfVariant("Cyan Tron", Palette("sf_cyan", "Cyan Tron", Color(0xFF04090D), Color(0xFF0A1D26), Color(0xFF071218), Color(0xFF135060), Color(0xFF1B4452), Color(0xFF0F3340),
            Color(0xFF19E3FF), Color(0xFFE8FDFF), Color(0xFF7FB8C4), Color(0xFF19B8D0), Color(0xFF7FEFFF))),
        SfVariant("Cam Alien", Palette("sf_orange", "Cam Alien", Color(0xFF0B0703), Color(0xFF1F1206), Color(0xFF150C04), Color(0xFF5A3410), Color(0xFF4A2A0C), Color(0xFF3A2008),
            Color(0xFFFF8A1F), Color(0xFFFFF1E0), Color(0xFFC09A70), Color(0xFFE06A00), Color(0xFFFFB366))),
        SfVariant("Đỏ HAL", Palette("sf_red", "Đỏ HAL", Color(0xFF0A0304), Color(0xFF1F080B), Color(0xFF150507), Color(0xFF5C1218), Color(0xFF4A1015), Color(0xFF3A0A10),
            Color(0xFFFF2E3F), Color(0xFFFFE9EA), Color(0xFFC48A8E), Color(0xFFD01F2E), Color(0xFFFF7A85))),
        SfVariant("Tím neon", Palette("sf_purple", "Tím neon", Color(0xFF07040E), Color(0xFF150A26), Color(0xFF0E0719), Color(0xFF4A2480), Color(0xFF3A1C66), Color(0xFF2C1556),
            Color(0xFFC65BFF), Color(0xFFF5EBFF), Color(0xFFA98FCB), Color(0xFF9A3BE0), Color(0xFFE0A8FF))),
        SfVariant("Lục Matrix", Palette("sf_green", "Lục Matrix", Color(0xFF020A04), Color(0xFF08210E), Color(0xFF04150A), Color(0xFF14602A), Color(0xFF124A22), Color(0xFF0C3A18),
            Color(0xFF2BFF6A), Color(0xFFE4FFEC), Color(0xFF7FBF96), Color(0xFF1FCB50), Color(0xFF8FFFB0))),
    )
    val variant = mutableStateOf(0)
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("scifi", Context.MODE_PRIVATE)
        variant.value = sp.getInt("variant", 0).coerceIn(0, VARIANTS.lastIndex)
    }
    fun set(i: Int) { variant.value = i; if (::sp.isInitialized) sp.edit().putInt("variant", i).apply() }
    val palette: Palette get() = VARIANTS[variant.value.coerceIn(0, VARIANTS.lastIndex)].pal
}

// ---------------------------------------------------------------- hình khối & nền
internal class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Generic(Path().apply {
            moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c); lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
        })
    }
}

internal object HexShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(Path().apply {
        moveTo(size.width / 2, 0f); lineTo(size.width, size.height * .25f); lineTo(size.width, size.height * .75f)
        lineTo(size.width / 2, size.height); lineTo(0f, size.height * .75f); lineTo(0f, size.height * .25f); close()
    })
}

/** Nền HUD: màu nền, quầng sáng phía trên, lưới 60dp, vân quét ngang. */
internal fun Modifier.sfBackdrop(): Modifier = drawBehind {
    drawRect(C.Bg)
    drawRect(Brush.radialGradient(listOf(C.Gold.copy(alpha = .14f), Color.Transparent), center = Offset(size.width / 2f, 0f), radius = size.width * 0.6f))
    val g = 60.dp.toPx()
    var x = 0f
    while (x < size.width) { drawLine(C.Gold.copy(alpha = .05f), Offset(x, 0f), Offset(x, size.height), 1f); x += g }
    var y = 0f
    while (y < size.height) { drawLine(C.Gold.copy(alpha = .05f), Offset(0f, y), Offset(size.width, y), 1f); y += g }
    val s = 4.dp.toPx()
    y = 0f
    while (y < size.height) { drawLine(C.Gold.copy(alpha = .035f), Offset(0f, y), Offset(size.width, y), 1f); y += s }
}

/** Khung HUD: nền chuyển sắc, viền mảnh, góc vát, ngoặc góc phát sáng ở góc trên-trái và dưới-phải. */
internal fun Modifier.sfPanel(cut: Dp = 14.dp): Modifier {
    val br = Brands.cur
    if (br != null) { val rs = RoundedCornerShape(br.radius.dp.coerceAtLeast(4.dp)); return this.clip(rs).background(Brush.verticalGradient(listOf(C.Panel, C.Side))).border(2.dp, br.bezel, rs) }
    val sh = ChamferShape(cut)
    return this.clip(sh)
        .background(Brush.verticalGradient(listOf(C.Panel.copy(alpha = .9f), C.Side.copy(alpha = .95f))))
        .border(1.dp, C.Gold.copy(alpha = .35f), sh)
        .drawBehind {
            val c = cut.toPx(); val l = 34.dp.toPx(); val w = 2.5.dp.toPx(); val col = C.Gold
            val p1 = Path().apply { moveTo(0f, l); lineTo(0f, c); lineTo(c, 0f); lineTo(l, 0f) }
            val p2 = Path().apply { moveTo(size.width, size.height - l); lineTo(size.width, size.height - c); lineTo(size.width - c, size.height); lineTo(size.width - l, size.height) }
            drawPath(p1, col, style = Stroke(w)); drawPath(p2, col, style = Stroke(w))
        }
}

/** Chữ HUD: đơn cách, có thể viết hoa và giãn chữ; co theo [LocalTextScale] như T(). */
@Composable
internal fun S(s: String, size: Int, color: Color = C.Text, modifier: Modifier = Modifier, bold: Boolean = false, lines: Int = 1,
               spaced: Boolean = false, caps: Boolean = false, mono: Boolean = Themes.isMono, align: TextAlign? = null) {
    val ts = LocalTextScale.current
    val px = if (ts == 1f) size.toFloat() else (size * ts).coerceAtLeast(9f)
    val txt = Lang.tr(s).let { if (caps) it.uppercase() else it }
    Text(txt, modifier = modifier, color = color, fontSize = px.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = if (spaced) 2.sp else 0.sp,
        maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

/** Dòng chữ thông tin (tối đa 2 dòng) trong Robot/SciFi. */
@Composable
internal fun Marq(s: String, size: Int, color: Color, bold: Boolean = false) =
    S(s, size, color, Modifier.fillMaxWidth(), bold = bold, lines = 2)

// ---------------------------------------------------------------- nút
/** Nút lục giác có đèn: bật = viền sáng + chấm xanh lá, tắt = viền mờ + chấm tối. */
@Composable
internal fun SfHex(w: Dp, active: Boolean, icon: ImageVector, desc: String, requester: FocusRequester? = null, mod: Modifier = Modifier, onClick: () -> Unit) {
    val br = Brands.cur
    val h = if (br != null) w else w * 1.12f
    Focusable(onClick, mod.size(w + 10.dp, h + 10.dp), if (br != null) CircleShape else HexShape, ring = C.Text, requester = requester, selColor = Color.Transparent) {
        Box(Modifier.align(Alignment.Center).size(w, h).drawBehind {
            if (br != null) {
                drawCircle(Brush.verticalGradient(if (active) listOf(br.knobHi, br.knobLo) else listOf(br.knobLo, Color.Black)), size.minDimension / 2f)
                if (active) drawCircle(C.Gold.copy(alpha = .35f), size.minDimension / 2f + 2.dp.toPx(), style = Stroke(4.dp.toPx()))
                drawCircle(if (active) C.Gold else br.bezel, size.minDimension / 2f, style = Stroke(2.dp.toPx()))
                drawCircle(if (active) Grade.GREEN.color else Color.Black.copy(alpha = .6f), 2.8.dp.toPx(), Offset(size.width / 2, size.height - 8.dp.toPx()))
                return@drawBehind
            }
            val path = Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height * .25f); lineTo(size.width, size.height * .75f); lineTo(size.width / 2, size.height); lineTo(0f, size.height * .75f); lineTo(0f, size.height * .25f); close() }
            drawPath(path, Brush.verticalGradient(listOf(if (active) C.Sel else C.Panel, C.Bg)))
            if (active) drawPath(path, C.Gold.copy(alpha = .25f), style = Stroke(6.dp.toPx()))
            drawPath(path, C.Gold.copy(alpha = if (active) 1f else .5f), style = Stroke(2.dp.toPx()))
            drawCircle(if (active) Grade.GREEN.color else C.Dim, 2.8.dp.toPx(), Offset(size.width / 2, size.height - 9.dp.toPx()))
        }, contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (br != null) (if (active) Color(0xFF111111) else Color(0xFF222222)) else if (active) C.Text else C.Gold, modifier = Modifier.size(w * 0.36f).offset(y = (-2).dp))
        }
    }
}

/** Nút Play/Pause tròn có vòng tiến độ chạy quanh. */
@Composable
internal fun SfPlay(size: Dp, frac: Float, playing: Boolean, requester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(size + 10.dp), CircleShape, ring = C.Text, requester = requester, selColor = Color.Transparent) {
        Box(Modifier.align(Alignment.Center).size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 5.dp.toPx(); val r = (minOf(this.size.width, this.size.height) - sw) / 2f
                drawCircle(C.Dim, r, style = Stroke(sw))
                drawArc(C.Gold, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(sw / 2, sw / 2), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw, cap = StrokeCap.Butt))
                drawCircle(C.Gold.copy(alpha = .3f), r - 7.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 8f))))
            }
            Box(Modifier.size(size * 0.66f).clip(CircleShape).background(Brush.radialGradient(listOf(C.Sel, C.Bg))).border(2.dp, C.Gold, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Tạm dừng" else "Phát", tint = C.Text, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

/** Đồng hồ VU vòng cung 40 vạch: sáng theo mức đỉnh, 5 vạch cuối đỏ; số dBFS ở giữa. */
@Composable
internal fun SfGauge(label: String, level: Float, modifier: Modifier) {
    if (Brands.cur?.meter == 1) { BrandAnalog(label, level, modifier); return }
    val db = if (level <= 0.0005f) -60f else (20f * kotlin.math.log10(level)).coerceAtMost(6f)
    val frac by animateFloatAsState(((db + 48f) / 51f).coerceIn(0f, 1f), tween(140), label = "sfvu")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val n = 40; val lit = (frac * n).toInt()
            val r = minOf(size.width * 0.46f, size.height * 0.92f)
            val cx = size.width / 2f; val cy = size.height * 0.95f
            for (k in 0 until n) {
                val a = Math.toRadians((160.0 + (20.0 - 160.0) * k / (n - 1)))
                val x0 = cx + (r - 12.dp.toPx()) * Math.cos(a).toFloat(); val y0 = cy - (r - 12.dp.toPx()) * Math.sin(a).toFloat()
                val x1 = cx + r * Math.cos(a).toFloat(); val y1 = cy - r * Math.sin(a).toFloat()
                val col = if (k < lit) (if (k >= n - 5) Color(0xFFFF3B5C) else C.Gold) else C.Dim
                drawLine(col, Offset(x0, y0), Offset(x1, y1), 4.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(C.Gold.copy(alpha = .25f), r - 26.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 8f))))
        }
        S("CH-$label", 15, C.Gold, Modifier.align(Alignment.TopStart).padding(12.dp, 6.dp), bold = true)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            S(if (db <= -60f) "-∞" else "%.1f".format(db), 28, C.Text, bold = true)
            S("dBFS · PEAK", 10, C.Gold, spaced = true)
        }
    }
}

// ---------------------------------------------------------------- khối thông tin dùng chung (TV + điện thoại)
@Composable
private fun SfTag(text: String) =
    Box(Modifier.border(1.dp, C.Gold, ChamferShape(5.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) { S(text, 12, C.Gold, spaced = true) }

@Composable
private fun SfCell(label: String, value: String, modifier: Modifier) =
    Column(modifier.border(1.dp, C.Gold.copy(alpha = .3f)).background(C.Gold.copy(alpha = .05f)).padding(8.dp, 3.dp)) {
        S(label, 9, C.Gold.copy(alpha = .8f), spaced = true, caps = true)
        S(value, 14, C.Text, bold = true)
    }

/** Toàn bộ thông tin bài: nhãn chất lượng, tên, nghệ sĩ, album, 6 ô thông số, đường tín hiệu (kể cả lỗi / cảnh báo EQ / DoP). */
@Composable
internal fun SfInfo(t: Track?, modifier: Modifier, compact: Boolean) {
    val info by Playback.info.collectAsState()
    val remote by Playback.remote.collectAsState()
    val outName by Playback.outName.collectAsState()
    val error by Playback.error.collectAsState()
    val pcm by Playback.level.fmt.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (t == null) {
            S("Chưa có bài nào đang phát", if (compact) 18 else 24, C.Text, bold = true, lines = 2)
            S("Vào Thư viện để chọn nhạc", 14, C.Muted)
            return@Column
        }
        val i = info
        val isDsd = t.ext == "dsf" || t.ext == "dff"
        val isFav = t.path in Favorites.paths.value
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SfTag(quality(t, i))
                if (i != null && i.sampleRate > 0 && i.bits > 0) SfTag(if (isDsd) "${dsdLabel(i.sampleRate)} · 1-BIT" else "${i.bits}-BIT / ${khz(i.sampleRate)}")
            }
            if (!compact) Focusable({ Favorites.toggle(t) }, Modifier.size(38.dp), CircleShape, ring = C.Text) {
                Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Yêu thích", tint = if (isFav) C.Gold else C.Text, modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
        }
        TM(t.title, if (compact) 20 else 23, C.Text, Modifier.fillMaxWidth(), bold = true)
        S("▸ " + t.artist, if (compact) 14 else 17, Grade.GREEN.color, lines = 1)
        S("ALBUM // " + t.album, 12, C.Muted, lines = 1, caps = true)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfCell("Định dạng", if (isDsd) dsdLabel(i?.sampleRate ?: t.sr) else t.ext.uppercase(), Modifier.weight(1f))
            SfCell("Độ sâu bit", ((i?.bits ?: 0).takeIf { it > 0 } ?: t.bits).let { if (it > 0) "$it-bit" else "—" }, Modifier.weight(1f))
            SfCell("Tần số", if (isDsd) mhz(i?.sampleRate ?: t.sr) else khz(i?.sampleRate ?: 0), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfCell("Bitrate", if (i != null && i.bitrateKbps > 0) "%,d kbps".format(i.bitrateKbps) else "—", Modifier.weight(1f))
            SfCell("Kênh", channelName(i?.channels ?: 0), Modifier.weight(1f))
            SfCell("Nguồn", t.sourceName.substringBefore(" · "), Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.weight(1f)).border(1.dp, Grade.GREEN.color.copy(alpha = .4f)).background(Grade.GREEN.color.copy(alpha = .05f)).padding(10.dp, 4.dp)) {
            val src = if (isDsd) "${dsdLabel(i?.sampleRate ?: t.sr)} ${mhz(i?.sampleRate ?: t.sr)}"
                else if (i != null && i.sampleRate > 0) "${t.ext.uppercase()} ${i.bits.takeIf { it > 0 } ?: ""}/${khz(i.sampleRate)}" else t.ext.uppercase()
            val dspOn = !DspParams.isFlat() && remote == null
            val (grade, gl) = signalGrade(t, i, pcm, remote)
            S("// SIGNAL PATH", 10, C.Gold, spaced = true)
            Focusable({ BitPerfectUi.open.value = true }) { Marq((if (grade == Grade.GREEN) "✓ " else "") + gl + "  ⓘ", 14, grade.color, bold = true) }
            val route = if (remote != null) (if (dopActive(t, i, remote)) "Tín hiệu: $src → DoP 24-bit/176,4 kHz → $outName" else "Tín hiệu: $src → $outName (file gốc, thiết bị tự giải mã)")
                else "Tín hiệu: $src → ${pcm.ifEmpty { "PCM" }}${if (dspOn) " → EQ" else ""} → $outName"
            val err = error
            if (err != null) Marq(err.lines().first().take(200), 12, C.Gold)
            else {
                Marq(route, 12, grade.color)
                if (dspOn) Marq("⚠ EQ đang chỉnh — có thể méo nếu tăng cao", 11, Grade.YELLOW.color)
            }
        }
    }
}

/** Hàng 5 nút điều khiển: xáo trộn, lùi, Play (vòng tiến độ), tới, lặp. */
@Composable
private fun SfTransport(big: Dp, small: Dp, gap: Dp, playReq: FocusRequester?) {
    val playing by Playback.playing.collectAsState()
    val pos by Playback.position.collectAsState()
    val dur by Playback.duration.collectAsState()
    val shuffle by Playback.shuffle.collectAsState()
    val repeat by Playback.repeat.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        SfHex(small, shuffle, Icons.Filled.Shuffle, "Ngẫu nhiên") { Playback.toggleShuffle() }
        SfHex(small, false, Icons.Filled.SkipPrevious, "Bài trước") { Playback.prev() }
        SfPlay(big, if (dur > 0) pos.toFloat() / dur else 0f, playing, playReq) { Playback.toggle() }
        SfHex(small, false, Icons.Filled.SkipNext, "Bài sau") { Playback.next() }
        SfHex(small, repeat != Player.REPEAT_MODE_OFF, if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Lặp lại") { Playback.cycleRepeat() }
    }
}

/** Sóng nhạc + thời gian + chế độ phát. [touch] = chạm để tua (điện thoại); TV tua bằng phím trái/phải khi đang chọn. */
@Composable
private fun SfWave(t: Track?, height: Dp, touch: Boolean) {
    val pos by Playback.position.collectAsState()
    val dur by Playback.duration.collectAsState()
    val shuffle by Playback.shuffle.collectAsState()
    val repeat by Playback.repeat.collectAsState()
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }   // tua chỉ hoạt động sau khi bấm OK/chuột vào dải sóng
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(height)
                .then(if (touch) Modifier.pointerInput(dur) { detectTapGestures { o -> if (dur > 0) Playback.seekTo((o.x / size.width * dur).toLong().coerceIn(0L, dur)) } } else Modifier)
                .onFocusChanged { focused = it.isFocused; if (!it.isFocused) armed = false }
                .then(if (armed) Modifier.border(3.dp, C.Gold).background(C.Gold.copy(alpha = 0.12f)) else if (focused) Modifier.border(2.dp, C.Text) else Modifier)
                .onKeyEvent { e ->
                    if (!armed || e.type != KeyEventType.KeyDown) false
                    else if (e.key == Key.DirectionLeft) { Playback.seekBy(-10_000); true }
                    else if (e.key == Key.DirectionRight) { Playback.seekBy(10_000); true }
                    else if (e.key == Key.Back || e.key == Key.Escape) { armed = false; true }
                    else false
                }
                .clickable { armed = !armed },
        ) { if (armed) S("◀ ▶ tua · OK khóa", 10, C.Gold, Modifier.align(Alignment.TopEnd)); Waveform(t?.path.hashCode(), if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, Modifier.fillMaxSize().padding(horizontal = 4.dp), C.Gold, C.Dim) }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            S(fmt(pos), 12, C.Muted, spaced = true)
            S((if (shuffle) "Ngẫu nhiên" else "Tuần tự") + " · " + when (repeat) { Player.REPEAT_MODE_ONE -> "Lặp một bài"; Player.REPEAT_MODE_ALL -> "Lặp toàn bộ"; else -> "Không lặp" }, 12, Grade.GREEN.color, caps = true)
            S(fmt(dur), 12, C.Muted, spaced = true)
        }
    }
}

/** Ô VU L/R hoặc (khi phát tới loa mạng) Thiết bị nhận | Kết nối & phát liên tục. */
@Composable
private fun SfMeters(modifier: Modifier) {
    val playing by Playback.playing.collectAsState()
    val lv by Playback.level.levels.collectAsState()
    val remote by Playback.remote.collectAsState()
    val info by Playback.info.collectAsState()
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val t = queue.getOrNull(idx)
    val r = remote
    Row(modifier) {
        if (r == null) {
            SfGauge("L", if (playing) lv.first else 0f, Modifier.weight(1f).fillMaxHeight())
            SfGauge("R", if (playing) lv.second else 0f, Modifier.weight(1f).fillMaxHeight())
        } else {
            val sk = remember { Skin(false, true) }
            ReceiverBox(sk, r, t, info, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.padding(vertical = 10.dp).fillMaxHeight().width(1.dp).background(Brush.verticalGradient(listOf(Color.Transparent, C.Gold.copy(alpha = .5f), Color.Transparent))))
            LinkBox(sk, r, t, playing, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ---------------------------------------------------------------- TV
@Composable
internal fun SciFiNow(onNav: (Screen) -> Unit, onOut: () -> Unit) {
    val ctx = LocalContext.current
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val info by Playback.info.collectAsState()
    val remote by Playback.remote.collectAsState()
    val outName by Playback.outName.collectAsState()
    val scanning by Library.scanning.collectAsState()
    val lib by Library.tracks.collectAsState()
    val t = queue.getOrNull(idx)
    val playReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playReq.requestFocus() } }
    // Phím lên/xuống đi thẳng giữa nút Power và mục menu cuối, bỏ qua khối thông tin // SYSTEM
    val powerReq = remember { FocusRequester() }
    val lastReq = remember { FocusRequester() }
    val lastMenu = Modifier.focusRequester(lastReq).focusProperties { down = powerReq }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val f = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US); while (true) { clock = f.format(java.util.Date()); delay(1000) } }

    val br = Brands.cur
    val hi = br?.faceInk ?: C.Text; val hi2 = br?.faceInk2 ?: C.Gold
    Box(Modifier.fillMaxSize().then(if (br != null) Modifier.brandFace() else Modifier.sfBackdrop())) {
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---- thanh trên
            Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GrapeLogo(Modifier.size(52.dp))
                    if (br != null) BrandKnob(52.dp)
                    Column {
                        S(if (br != null) "GRAPE2Play" else "GRAPE·2·PLAY", 24, hi, bold = true, spaced = true)
                        S("LIMITED EDITION", 11, hi2, spaced = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    S("LINK ● " + (remote?.name?.take(16) ?: "LOCAL"), 12, if (br != null) hi else Grade.GREEN.color, spaced = true)
                    S("OUT " + outName.take(22), 12, if (br != null) hi2 else C.Muted, spaced = true, caps = true)
                    S("CLOCK " + khz(info?.sampleRate ?: 0), 12, if (br != null) hi2 else C.Muted, spaced = true, caps = true)
                    S(clock, 12, if (br != null) hi2 else C.Muted, spaced = true)
                    if (br != null) BrandKnob(52.dp)
                }
            }
            // ---- giữa
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(230.dp).fillMaxHeight().sfPanel().padding(14.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    S("// NAVIGATION", 11, C.Gold, Modifier.padding(bottom = 6.dp), spaced = true)
                    SfMenuItem("Đang phát", true) { onNav(Screen.NOW) }
                    SfMenuItem("Thư viện", false) { onNav(Screen.LIB) }
                    SfMenuItem("Nguồn nhạc", false) { onNav(Screen.SOURCES) }
                    SfMenuItem("Đầu ra", false, remote != null) { onNav(Screen.OUTPUT) }
                    SfMenuItem("Phổ tần", false) { Overlay.spectrum.value = true }
                    SfMenuItem("Trợ lý AI", false) { Overlay.aiStart = "menu"; Overlay.ai.value = true }
                    SfMenuItem("Cài đặt", false, mod = lastMenu) { onNav(Screen.SOUND) }   // luôn gắn lastReq: nút Power trỏ "lên" tới đây, nếu chưa gắn thì Compose báo lỗi và app thoát
                    if (scanning) S("Đang quét… ${lib.size} bài", 12, C.Gold, Modifier.padding(14.dp, 10.dp), lines = 2)
                    Spacer(Modifier.weight(1f))
                    S("// SYSTEM", 11, C.Gold, spaced = true)
                    S("LIB   ${lib.size}", 12, C.Muted)
                    S("QUEUE ${queue.size}", 12, C.Muted)
                    S("MODE  " + (if (remote != null) "NETWORK" else "LOCAL"), 12, C.Muted)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f).sfPanel()) {
                        Box(Modifier.padding(14.dp).fillMaxHeight().aspectRatio(1f).border(1.dp, C.Gold.copy(alpha = .45f))) {
                            CoverImage(t, Modifier.fillMaxSize(), 0)
                        }
                        SfInfo(t, Modifier.weight(1f).fillMaxHeight().padding(top = 14.dp, end = 22.dp, bottom = 14.dp), compact = false)
                    }
                    SfMeters(Modifier.fillMaxWidth().height(160.dp).sfPanel().padding(horizontal = 10.dp, vertical = 6.dp))
                }
                // ---- danh sách phát
                Column(Modifier.width(420.dp).fillMaxHeight().sfPanel().padding(18.dp)) {
                    val st = rememberLazyListState()
                    LaunchedEffect(idx, queue.size) { if (queue.isNotEmpty()) st.animateScrollToItem(idx.coerceIn(0, queue.lastIndex)) }
                    S("Danh sách phát", 18, C.Text, bold = true, spaced = true, caps = true)
                    Row(Modifier.padding(top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        S("${queue.size} TRACKS // ${fmt(queue.sumOf { it.durationMs })}", 11, C.Muted, spaced = true)
                        Focusable({ Playback.shuffleQueue() }, shape = ChamferShape(5.dp)) { S("⤮ MIX", 11, C.Gold, Modifier.padding(10.dp, 3.dp), bold = true, spaced = true) }
                    }
                    if (queue.isEmpty()) S("Danh sách đang trống", 14, C.Muted)
                    LazyColumn(state = st) {
                        itemsIndexed(queue) { i, tr -> SfQueueRow(i, tr, i == idx) { Playback.jump(i) } }
                    }
                }
            }
            // ---- hàng dưới
            Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(230.dp).fillMaxHeight().sfPanel().padding(14.dp, 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    S("// POWER", 11, C.Gold, spaced = true)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SfHex(60.dp, true, Icons.Filled.PowerSettingsNew, "Bật / Tắt", powerReq, Modifier.focusProperties { up = lastReq }) { Overlay.exit.value = true } }
                    Spacer(Modifier.height(4.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight().sfPanel().padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    SfWave(t, 38.dp, touch = false)
                    SfTransport(94.dp, 62.dp, 24.dp, playReq)
                }
                Column(Modifier.width(420.dp).fillMaxHeight().sfPanel().padding(14.dp, 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    S("// OUTPUT", 11, C.Gold, spaced = true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SfHex(60.dp, remote != null, Icons.Filled.Speaker, "Đầu ra") { onOut() }
                        Column {
                            S(outName, 16, C.Text, bold = true, lines = 2)
                            S(if (remote != null) "NETWORK PLAYER" else "LOCAL OUTPUT", 11, C.Muted, spaced = true)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SfMenuItem(label: String, selected: Boolean, highlight: Boolean = false, mod: Modifier = Modifier, onClick: () -> Unit) =
    Focusable(onClick, mod.fillMaxWidth(), ChamferShape(6.dp), selected = selected, ring = C.Text, selColor = C.Sel) { f ->
        Row(
            Modifier.fillMaxWidth().then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(C.Gold.copy(alpha = .28f), Color.Transparent))) else Modifier).padding(12.dp, 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(8.dp).drawBehind { drawRect(if (selected || f || highlight) C.Gold else Color.Transparent); drawRect(C.Gold, style = Stroke(1.dp.toPx())) })
            S(label, 14, if (selected || f) C.Text else if (highlight) C.Gold else C.Muted, bold = selected || f, spaced = true, caps = true)
        }
    }

@Composable
private fun SfQueueRow(i: Int, t: Track, cur: Boolean, onClick: () -> Unit) =
    Focusable(onClick, Modifier.fillMaxWidth(), ChamferShape(6.dp), selected = cur, ring = C.Text, selColor = C.Sel, onLong = { TrackMenu.open(t, TrackMenu.Ctx.Queue(i)) }) { f ->
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(t, Modifier.size(40.dp).border(1.dp, C.Gold.copy(alpha = .35f)), 0)
            Column(Modifier.weight(1f)) {
                TM(t.title, 14, if (cur || f) C.Text else C.Text.copy(alpha = .85f), bold = cur, run = f)
                S("${t.artist} · ${t.ext.uppercase()}", 11, C.Muted, lines = 1)
            }
            if (t.path in Favorites.paths.value) Icon(Icons.Filled.Favorite, null, tint = C.Gold, modifier = Modifier.size(14.dp))
            S(fmt(t.durationMs), 12, if (cur) Grade.GREEN.color else C.Muted)
        }
    }

// ---------------------------------------------------------------- điện thoại
private class SfNav(val s: Screen, val icon: ImageVector, val label: String)
private val SF_NAV = listOf(
    SfNav(Screen.NOW, Icons.Filled.PlayCircle, "Đang phát"), SfNav(Screen.LIB, Icons.Filled.LibraryMusic, "Thư viện"),
    SfNav(Screen.SOURCES, Icons.Filled.Cloud, "Nguồn"), SfNav(Screen.OUTPUT, Icons.Filled.Speaker, "Đầu ra"), SfNav(Screen.SOUND, Icons.Filled.Settings, "Cài đặt"),
)

/** Khung điện thoại: nền HUD + thanh điều hướng trên cùng (Đang phát, Thư viện, Nguồn, Đầu ra, Cài đặt). */
@Composable
internal fun SciFiPhoneShell(cur: Screen, onNav: (Screen) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val br = Brands.cur
    Column(Modifier.fillMaxSize().then(if (br != null) Modifier.brandFace() else Modifier.sfBackdrop())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).sfPanel(10.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(start = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { GrapeLogo(Modifier.size(28.dp)); Wordmark(8); LimitedMini(7) }
            val active = if (cur == Screen.SOURCES || cur == Screen.OUTPUT) cur else cur
            SF_NAV.forEach { n ->
                val sel = n.s == active
                Column(Modifier.weight(1f).clickable { onNav(n.s) }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(n.icon, null, tint = if (sel) C.Gold else C.Muted, modifier = Modifier.size(20.dp))
                    S(n.label, 9, if (sel) C.Text else C.Muted, bold = sel, caps = true)
                    Box(Modifier.padding(top = 2.dp).width(22.dp).height(2.dp).background(if (sel) C.Gold else Color.Transparent))
                }
            }
        }
        if (br != null) Column(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(C.Bg).border(2.dp, br.bezel, RoundedCornerShape(10.dp)), content = content)
        else content()
    }
}

/** Màn Đang phát trên điện thoại: ảnh bìa + thông tin, VU / thiết bị nhận, sóng nhạc (chạm để tua) + 5 nút, 4 nút phụ. Không có thông tin riêng của TV. */
@Composable
internal fun SciFiPhoneNow(onOut: () -> Unit, onQueue: () -> Unit) {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val remote by Playback.remote.collectAsState()
    val t = queue.getOrNull(idx)
    val land = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    if (t == null) {
        Box(Modifier.fillMaxSize().padding(16.dp).sfPanel().padding(24.dp), contentAlignment = Alignment.Center) { S("Chưa có bài nào đang phát. Mở Thư viện để chọn nhạc.", 16, C.Muted, lines = 3, mono = false) }
        return
    }
    val fav = t.path in Favorites.paths.value
    val cover: @Composable (Modifier) -> Unit = { m ->
        Box(m.border(1.dp, C.Gold.copy(alpha = .45f))) {
            CoverImage(t, Modifier.fillMaxSize(), 0)
        }
    }
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().sfPanel(10.dp).padding(12.dp, 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SfWave(t, 38.dp, touch = true)
            SfTransport(76.dp, 46.dp, 6.dp, null)
        }
    }
    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SfHex(44.dp, fav, if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Yêu thích") { Favorites.toggle(t) }
            SfHex(44.dp, remote != null, Icons.Filled.Speaker, "Đầu ra") { onOut() }
            SfHex(44.dp, false, Icons.Filled.GraphicEq, "Phổ tần") { Overlay.spectrum.value = true }
            SfHex(44.dp, false, Icons.Filled.QueueMusic, "Danh sách") { onQueue() }
        }
    }
    if (land) Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cover(Modifier.fillMaxHeight().aspectRatio(1f))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SfInfo(t, Modifier.fillMaxWidth().sfPanel(10.dp).padding(12.dp), compact = true)
            SfMeters(Modifier.fillMaxWidth().height(130.dp).sfPanel(10.dp).padding(6.dp))
            controls(); actions()
        }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cover(Modifier.fillMaxWidth().aspectRatio(1.4f))
        SfInfo(t, Modifier.fillMaxWidth().sfPanel(10.dp).padding(12.dp), compact = true)
        SfMeters(Modifier.fillMaxWidth().height(130.dp).sfPanel(10.dp).padding(6.dp))
        controls(); actions()
    }
}

/** Thẻ Cài đặt: chọn màu cho giao diện Robot / Sci-Fi. */
@Composable
fun SciFiStyleCard() {
    val cur by SciFi.variant
    Card {
        T("ROBOT / SCI-FI — MÀU GIAO DIỆN", 13, C.Muted, spaced = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SciFi.VARIANTS.forEachIndexed { i, v ->
                Focusable({ SciFi.set(i) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), selected = cur == i) {
                    Row(Modifier.padding(12.dp, 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(v.pal.bg).border(3.dp, v.pal.accent, CircleShape))
                        T(v.name, 17, if (cur == i) C.Gold else C.Text)
                    }
                }
            }
        }
        T("Đổi màu toàn bộ ứng dụng (TV và điện thoại) của giao diện Robot / Sci-Fi.", 14, C.Muted, lines = 2)
    }
}
