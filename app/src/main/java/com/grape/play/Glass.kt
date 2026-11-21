package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Giao diện "Liquid Glass" (kiểu 20): nền cực quang nhiều màu, các khối là kính mờ trắng bo tròn lớn
 * có viền sáng chéo (vệt phản chiếu), chữ trắng. Màu bảng bên dưới là màu bán trong suốt nên mọi
 * khung dùng C.Panel / C.Side tự thành kính.
 */
object GlassTheme {
    const val ID = 20
    private val light = Palette("glass", "Liquid Glass",
        Color(0xFF0E0D22), Color(0x33FFFFFF), Color(0x24FFFFFF), Color(0x59FFFFFF), Color(0x66FFFFFF), Color(0x59FFFFFF),
        Color(0xFFFFF1DA), Color(0xFFFFFFFF), Color(0xB8FFFFFF), Color(0xFF7A5CFF), Color(0xFFB9A8FF))
    private val darkP = Palette("glassdark", "Liquid Glass tối",
        Color(0xFF05050D), Color(0x1FFFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF), Color(0x4DFFFFFF), Color(0x3DFFFFFF),
        Color(0xFFF3DDB8), Color(0xFFF2F2F7), Color(0xA6FFFFFF), Color(0xFF6A4FE0), Color(0xFFA796F0))
    /** Chế độ tối: nền gần đen, cực quang mờ (hợp OLED), kính trắng nhạt hơn. */
    val dark = androidx.compose.runtime.mutableStateOf(false)
    private var sp: android.content.SharedPreferences? = null
    fun init(ctx: android.content.Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences("glass", android.content.Context.MODE_PRIVATE)
        dark.value = sp!!.getBoolean("dark", false)
    }
    fun setDark(v: Boolean) { dark.value = v; sp?.edit()?.putBoolean("dark", v)?.apply() }
    val palette: Palette get() = if (dark.value) darkP else light
    val base: Color get() = if (dark.value) Color(0xFF05050D) else Color(0xFF0E0D22)
    val on: Boolean get() = Themes.style.value == ID
}

/** Nền cực quang tĩnh (không chuyển động để không in bóng OLED). */
fun Modifier.glassBackdrop(): Modifier = this.drawBehind {
    val w = size.width; val h = size.height; val r = maxOf(w, h)
    drawRect(GlassTheme.base)
    fun blob(cx: Float, cy: Float, rad: Float, c: Color) =
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx * w, cy * h), rad * r), rad * r, Offset(cx * w, cy * h))
    val k = if (GlassTheme.dark.value) 0.32f else 1f
    blob(.15f, .22f, .55f, Color(0xFFFF8A5C).copy(alpha = .8f * k))
    blob(.82f, .18f, .55f, Color(0xFF7A5CFF).copy(alpha = .8f * k))
    blob(.62f, .88f, .55f, Color(0xFF19C3B3).copy(alpha = .73f * k))
    blob(.08f, .92f, .45f, Color(0xFFC2417C).copy(alpha = .7f * k))
}

/** Khối kính: nền trắng mờ chuyển sắc + viền sáng chéo. */
fun Modifier.glassPanel(r: Dp = 24.dp): Modifier {
    val sh = RoundedCornerShape(r)
    return this.clip(sh)
        .background(Brush.linearGradient(if (GlassTheme.dark.value) listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF)) else listOf(Color(0x3DFFFFFF), Color(0x14FFFFFF))))
        .border(1.dp, Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x4DFFFFFF))), sh)
}
