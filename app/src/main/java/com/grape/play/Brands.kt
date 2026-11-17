@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Giao diện theo phong cách thương hiệu âm thanh (Denon, Marantz, Tannoy, McIntosh, Luxman) — chỉ lấy cảm hứng về màu sắc, chất liệu và kiểu đồng hồ,
 * không dùng logo hay chữ hiệu của hãng. Kiểu giao diện 6..15. Mỗi bộ gồm: mặt máy (kim loại / gỗ), màn hình tối có viền, bảng màu màn hình,
 * kiểu đồng hồ (vòng cung số hoặc kim analog), núm xoay và 3 màu chữ màn hình để tuỳ biến.
 */
class BrandTheme(
    val id: String, val name: String, val sub: String, val base: Palette,
    val variants: List<Pair<String, Color>>,
    val face: List<Color>, val faceInk: Color, val faceInk2: Color, val bezel: Color,
    val knobHi: Color, val knobLo: Color,
    val meter: Int, val m1: Color, val m2: Color, val mInk: Color,
    val mono: Boolean, val serif: Boolean, val wood: Boolean, val radius: Int,
)

private fun P(id: String, n: String, bg: Long, panel: Long, side: Long, line: Long, dim: Long, sel: Long, text: Long, muted: Long) =
    Palette(id, n, Color(bg), Color(panel), Color(side), Color(line), Color(dim), Color(sel), Color.White, Color(text), Color(muted), Color.White, Color.White)

object Brands {
    val LIST = listOf(
        BrandTheme("denon", "Denon", "HI-FI NETWORK AUDIO · D-1", P("b_denon", "Denon", 0xFF030608, 0xFF0A1A24, 0xFF060F15, 0xFF17506B, 0xFF1B3A4A, 0xFF0C3A52, 0xFFE8F9FF, 0xFF7FA8BC),
            listOf("Xanh băng" to Color(0xFF4CC4FF), "Trắng" to Color(0xFFF2F6F8), "Hổ phách" to Color(0xFFFFB347)),
            listOf(Color(0xFF26292D), Color(0xFF121316), Color(0xFF0A0A0C)), Color(0xFFDFE5EA), Color(0xFF7FB4D6), Color(0xFF4A5056), Color(0xFFE6E9EC), Color(0xFF7D848A),
            0, Color.Black, Color.Black, Color.White, true, false, false, 5),
        BrandTheme("marantz", "Marantz", "NETWORK AUDIO PLAYER · M-2", P("b_marantz", "Marantz", 0xFF02070D, 0xFF0A2236, 0xFF06131E, 0xFF1A4F7A, 0xFF12344F, 0xFF0F3F63, 0xFFEAF6FF, 0xFF8FB4CC),
            listOf("Xanh dương" to Color(0xFF5CC0FF), "Xanh ngọc" to Color(0xFF3FE0C0), "Trắng ngà" to Color(0xFFE8F0F8)),
            listOf(Color(0xFFE6D5AE), Color(0xFFC7AD78), Color(0xFFB39560)), Color(0xFF3A2C12), Color(0xFF6C562A), Color(0xFF8D7442), Color(0xFFF6EAD0), Color(0xFF9A7F4A),
            0, Color.Black, Color.Black, Color.White, false, true, false, 16),
        BrandTheme("tannoy", "Tannoy", "DUAL CONCENTRIC EDITION", P("b_tannoy", "Tannoy", 0xFF120B06, 0xFF24150B, 0xFF190F08, 0xFF6B4A1C, 0xFF4A3216, 0xFF4A2A0C, 0xFFF1E3C4, 0xFFB59F78),
            listOf("Đồng" to Color(0xFFD9AD58), "Kem sáng" to Color(0xFFF1E3C4), "Xanh đèn ống" to Color(0xFF9FE08A)),
            listOf(Color(0xFF3B1F0F), Color(0xFF47260F), Color(0xFF33190B)), Color(0xFFD9AD58), Color(0xFFA98A58), Color(0xFFB98A3C), Color(0xFFE8C06A), Color(0xFF7A5420),
            1, Color(0xFFF4E8CC), Color(0xFFE2D1A6), Color(0xFF2A1A0A), false, true, true, 8),
        BrandTheme("mcintosh", "McIntosh", "STEREO NETWORK PLAYER · MX-1", P("b_mcintosh", "McIntosh", 0xFF000000, 0xFF08120E, 0xFF040A07, 0xFF1D5A3E, 0xFF14382A, 0xFF0C3A26, 0xFFD6FFE8, 0xFF7FBFA0),
            listOf("Xanh lục" to Color(0xFF2CFF8F), "Xanh lam" to Color(0xFF4CC4FF), "Trắng" to Color(0xFFEAF4F0)),
            listOf(Color(0xFF101214), Color(0xFF000000), Color(0xFF050607)), Color(0xFF8DFFC4), Color(0xFF4FBF86), Color(0xFF777F85), Color(0xFFE6E9EC), Color(0xFF7D848A),
            1, Color(0xFF0E4A6B), Color(0xFF041A2A), Color(0xFFBFEAFF), false, true, false, 2),
        BrandTheme("luxman", "Luxman", "NETWORK PLAYER · L-9", P("b_luxman", "Luxman", 0xFF050402, 0xFF1A1209, 0xFF100B05, 0xFF6B4812, 0xFF4A300C, 0xFF4A2C06, 0xFFFFE2B0, 0xFFC09A60),
            listOf("Hổ phách" to Color(0xFFFFB347), "Cam" to Color(0xFFFF8A1F), "Lục nhạt" to Color(0xFFB8F07A)),
            listOf(Color(0xFFD9DCDE), Color(0xFFC5C8CB), Color(0xFFA9ACAF)), Color(0xFF2A2F33), Color(0xFF6A6F73), Color(0xFF5B5F63), Color(0xFFF4F5F6), Color(0xFF8A8E92),
            1, Color(0xFFFFCF6A), Color(0xFFE08A1A), Color(0xFF2A1A05), false, false, false, 4),
        BrandTheme("technics", "Technics", "NETWORK AUDIO · SL-1", P("b_technics", "Technics", 0xFF04070A, 0xFF0B1620, 0xFF070E15, 0xFF2A4A60, 0xFF1E3444, 0xFF12344A, 0xFFF0F6FA, 0xFF8AA3B4),
            listOf("Xanh trắng" to Color(0xFFA8E4FF), "Trắng" to Color(0xFFF4F6F8), "Cam" to Color(0xFFFF9A3C)),
            listOf(Color(0xFFCDD1D5), Color(0xFFA9AEB3), Color(0xFF8C9196)), Color(0xFF1B1D20), Color(0xFF4A4F55), Color(0xFF3A3E43), Color(0xFFF2F4F6), Color(0xFF7A8087),
            0, Color.Black, Color.Black, Color.White, true, false, false, 3),
        BrandTheme("accuphase", "Accuphase", "NETWORK PLAYER · E-1", P("b_accuphase", "Accuphase", 0xFF0A0603, 0xFF1C1209, 0xFF120B05, 0xFF6B4A1C, 0xFF4A3216, 0xFF4A2C08, 0xFFFFEBCB, 0xFFC4A878),
            listOf("Cam hổ phách" to Color(0xFFFF9A2E), "Trắng ấm" to Color(0xFFFFE9C4), "Lục" to Color(0xFF7FE8A0)),
            listOf(Color(0xFFE4D3B0), Color(0xFFCDB98A), Color(0xFFB69F6B)), Color(0xFF2A1F08), Color(0xFF6B5A30), Color(0xFF8D7442), Color(0xFFF6EAD0), Color(0xFF9A7F4A),
            1, Color(0xFFFFF2CC), Color(0xFFE8D090), Color(0xFF2A1F08), false, true, false, 6),
        BrandTheme("yamaha", "Yamaha", "NATURAL SOUND · A-S", P("b_yamaha", "Yamaha", 0xFF0C0806, 0xFF1E1510, 0xFF140E0A, 0xFF5A3C22, 0xFF3E2A18, 0xFF4A2E18, 0xFFF6EBD3, 0xFFB8A488),
            listOf("Trắng kem" to Color(0xFFF6EBD3), "Cam" to Color(0xFFFF9A3C), "Xanh nhạt" to Color(0xFF8FD8FF)),
            listOf(Color(0xFF7A5230), Color(0xFF8B6038), Color(0xFF6B4426)), Color(0xFFF0E2C4), Color(0xFFC9B48C), Color(0xFF3A2A1A), Color(0xFFE9E4DA), Color(0xFF8A8478),
            0, Color.Black, Color.Black, Color.White, false, false, true, 10),
        BrandTheme("pioneer", "Pioneer", "STEREO NETWORK · SX-1", P("b_pioneer", "Pioneer", 0xFF050807, 0xFF0C1A14, 0xFF07100C, 0xFF1D5A44, 0xFF143A2C, 0xFF0C3A2A, 0xFFE4FFF0, 0xFF80B8A0),
            listOf("Lục ngọc" to Color(0xFF3DFFB0), "Xanh biển" to Color(0xFF4CC4FF), "Cam" to Color(0xFFFF9A3C)),
            listOf(Color(0xFF3A3D40), Color(0xFF1E2022), Color(0xFF101112)), Color(0xFFD9DEE2), Color(0xFF8A9096), Color(0xFF8D949A), Color(0xFFE6E9EC), Color(0xFF7D848A),
            1, Color(0xFF1C6B4A), Color(0xFF07261A), Color(0xFFD8FFE8), false, false, false, 4),
        BrandTheme("jbl", "JBL", "STUDIO MONITOR · L-1", P("b_jbl", "JBL", 0xFF050403, 0xFF1A130C, 0xFF0F0B07, 0xFF7A3A08, 0xFF4A2608, 0xFF4A2608, 0xFFFFF1E0, 0xFFC09A70),
            listOf("Cam" to Color(0xFFFF7A1A), "Trắng" to Color(0xFFF4F4F4), "Vàng" to Color(0xFFFFC933)),
            listOf(Color(0xFF16130F), Color(0xFF0C0B09), Color(0xFF060605)), Color(0xFFFF7A1A), Color(0xFFB85A14), Color(0xFFFF7A00), Color(0xFF3A3A3A), Color(0xFF111111),
            0, Color.Black, Color.Black, Color.White, false, false, false, 8),
    )
    private val vars = mutableStateMapOf<String, Int>()
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("brands", Context.MODE_PRIVATE)
        LIST.forEach { vars[it.id] = sp.getInt("v_" + it.id, 0).coerceIn(0, it.variants.lastIndex) }
    }
    fun variant(b: BrandTheme) = vars[b.id] ?: 0
    fun setVariant(b: BrandTheme, i: Int) { vars[b.id] = i; if (::sp.isInitialized) sp.edit().putInt("v_" + b.id, i).apply() }
    /** Thương hiệu đang chọn (kiểu 6..10) hoặc null. */
    val cur: BrandTheme? get() = Themes.style.value.let { if (it in 6..15) LIST[it - 6] else null }
    val palette: Palette get() {
        val b = cur ?: LIST[0]; val a = b.variants[variant(b)].second; val p = b.base
        return Palette(p.id, p.name, p.bg, p.panel, p.side, p.line, p.dim, p.sel, a, p.text, p.muted, a, a)
    }
}

/** Mặt máy: kim loại (gradient + vân phay mảnh) hoặc gỗ (vân dọc). */
internal fun Modifier.brandFace(): Modifier = drawBehind {
    val b = Brands.cur ?: return@drawBehind
    drawRect(Brush.verticalGradient(b.face))
    if (b.wood) {
        var x = 0f; var k = 0
        while (x < size.width) { drawRect(Color.Black.copy(alpha = if (k % 3 == 0) .22f else .08f), Offset(x, 0f), androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)); x += 7.dp.toPx(); k++ }
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .35f))))
    } else {
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = .03f), Offset(0f, y), Offset(size.width, y), 1f); y += 3.dp.toPx() }
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .25f))))
    }
}

/** Chữ viết tay "Limited Edition" (ảnh do bạn cung cấp, nét đen → đổi sang màu [tint] hợp với giao diện). */
@Composable
fun LimitedArt(width: Dp, tint: Color, modifier: Modifier = Modifier) {
    if (Themes.style.value == 5) { S("LIMITED EDITION", (width.value / 9.5f).toInt().coerceAtLeast(7), tint, modifier, spaced = width.value >= 90f); return }
    Image(painterResource(R.drawable.limited_edition), "Limited Edition", modifier.width(width), contentScale = ContentScale.FillWidth, colorFilter = ColorFilter.tint(tint))
}

/** "Limited Edition" cỡ nhỏ đặt ngay dưới logo con cáo ở mọi giao diện; màu mặc định theo màu nhấn của giao diện. */
@Composable
fun LimitedMini(sizeSp: Int = 10, color: Color? = null, modifier: Modifier = Modifier) = LimitedArt((sizeSp * 7.5f).dp, color ?: C.Gold, modifier)

/** Núm xoay kim loại (trang trí) với vạch chỉ. */
@Composable
internal fun BrandKnob(size: Dp, modifier: Modifier = Modifier) {
    val b = Brands.cur ?: return
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f; val c = center
        drawCircle(Color.Black.copy(alpha = .5f), r, c + Offset(0f, 4.dp.toPx()))
        drawCircle(Brush.sweepGradient(listOf(b.knobHi, b.knobLo, b.knobHi, b.knobLo, b.knobHi, b.knobLo, b.knobHi), c), r, c)
        drawCircle(Color.Black.copy(alpha = .35f), r, c, style = Stroke(2.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(b.knobHi.copy(alpha = .8f), b.knobLo), c, r * .6f), r * .62f, c)
        drawLine(Color(0xFF111111), c + Offset(0f, -r * .88f), c + Offset(0f, -r * .42f), 3.5.dp.toPx(), StrokeCap.Round)
    }
}

/** Đồng hồ kim analog (McIntosh / Luxman / Tannoy): mặt chuyển sắc, vạch chia, kim chạy theo mức đỉnh. */
@Composable
internal fun BrandAnalog(label: String, level: Float, modifier: Modifier) {
    val b = Brands.cur ?: return
    val db = if (level <= 0.0005f) -60f else (20f * kotlin.math.log10(level)).coerceAtMost(6f)
    val frac by animateFloatAsState(((db + 40f) / 43f).coerceIn(0f, 1f), tween(140), label = "bmeter")
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(b.m1, b.m2)))) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height * 0.94f
            val r = minOf(size.width * 0.46f, size.height * 0.82f)
            for (i in 0..20) {
                val a = Math.toRadians(150.0 - i * 6.0); val r0 = r - (if (i % 5 == 0) 14.dp.toPx() else 8.dp.toPx())
                val col = if (i >= 15) Color(0xFFD83A2A) else b.mInk
                drawLine(col, Offset(cx + r0 * Math.cos(a).toFloat(), cy - r0 * Math.sin(a).toFloat()), Offset(cx + r * Math.cos(a).toFloat(), cy - r * Math.sin(a).toFloat()), if (i % 5 == 0) 2.4.dp.toPx() else 1.2.dp.toPx())
            }
            val a = Math.toRadians(150.0 - 120.0 * frac)
            drawLine(Color(0xFF15100A), Offset(cx, cy), Offset(cx + (r + 4.dp.toPx()) * Math.cos(a).toFloat(), cy - (r + 4.dp.toPx()) * Math.sin(a).toFloat()), 2.5.dp.toPx(), StrokeCap.Round)
            drawCircle(Color(0xFF15100A), 7.dp.toPx(), Offset(cx, cy))
        }
        S("CH-$label", 14, b.mInk, Modifier.align(Alignment.TopStart).padding(10.dp, 5.dp), bold = true)
        S(if (db <= -60f) "-∞ dB" else "%.1f dB".format(db), 13, b.mInk, Modifier.align(Alignment.TopEnd).padding(10.dp, 5.dp), bold = true)
    }
}

/** Thẻ Cài đặt: tuỳ biến màu chữ / đèn màn hình của bộ giao diện thương hiệu đang chọn. */
@Composable
fun BrandStyleCard() {
    val b = Brands.cur ?: return
    val cur = Brands.variant(b)
    Card {
        T("${b.name.uppercase()} — MÀU MÀN HÌNH", 13, C.Muted, spaced = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.variants.forEachIndexed { i, v ->
                Focusable({ Brands.setVariant(b, i) }, shape = RoundedCornerShape(8.dp), selected = cur == i) {
                    Row(Modifier.padding(12.dp, 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(b.base.bg).border(3.dp, v.second, CircleShape))
                        T(v.first, 17, if (cur == i) C.Gold else C.Text)
                    }
                }
            }
        }
        T("Đổi màu chữ, đèn và viền màn hình trên toàn ứng dụng. Mặt máy và kiểu đồng hồ giữ theo phong cách của hãng.", 14, C.Muted, lines = 2)
    }
}
