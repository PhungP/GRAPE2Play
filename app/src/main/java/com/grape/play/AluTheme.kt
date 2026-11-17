package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Tuỳ chọn cho giao diện "Nhôm phay xước":
 *  - [AluGlass]: màu kính hiển thị (nền các màn hình có đèn hắt) kèm bộ màu chữ/vạch đi theo.
 *  - [AluMetal]: chất liệu mặt máy (nhôm phay xước, bóng, nhám, đen, titan, xám).
 */
class AluGlass(
    val name: String, val g1: Color, val g2: Color, val g3: Color, val glow: Color,
    val ink: Color, val muted: Color, val accent: Color, val selBg: Color, val lit: Color, val litSub: Color,
    val fill: Color, val waveBase: Color, val divider: Color, val warn: Color, val zone: Color, val light: Boolean,
)

class AluMetal(
    val name: String, val stops: List<Color>, val style: Int,   // style: 0 = phay xước, 1 = bóng, 2 = nhám
    val ink: Color, val lip: Color, val gold: Color,
    val btn1: Color, val btn2: Color, val btn3: Color, val btnIcon: Color, val bevelTop: Color, val bevelBot: Color,
    val streakLight: Float, val streakDark: Float, val dark: Boolean = false,
)

object AluTheme {
    /** Bảng màu toàn app khi chọn giao diện Nhôm phay xước: màn hình kính tối pha màu kính đang chọn, chữ sáng màu đèn của kính. */
    val palette: Palette get() {
        val k = g; val z = Color.Black
        fun d(f: Float) = androidx.compose.ui.graphics.lerp(k.g1, z, f)
        return Palette("alu", k.name, d(.84f), d(.74f), d(.88f), d(.5f), d(.35f), d(.6f), k.lit, k.lit.copy(alpha = 1f), k.litSub, k.g2, k.lit)
    }

    val GLASS = listOf(
        AluGlass("Hổ phách", Color(0xFFF2A50C), Color(0xFFFFBD1C), Color(0xFFFFCF3A), Color(0x66FFF6C0),
            Color(0xFF241A05), Color(0xB3402E08), Color(0xFF6B3D00), Color(0xFF1C1406), Color(0xFFFFD24A), Color(0xFFD9B24A),
            Color(0x33FFF0AA), Color(0x47462800), Color(0x59502D00), Color(0xFF9E1C14), Color(0xFFC8250F), false),
        AluGlass("Đỏ hồng ngọc", Color(0xFF9E0F2B), Color(0xFFC81E3F), Color(0xFFE8506B), Color(0x66FFB0BD),
            Color(0xFFFFF4F5), Color(0xB3FFE4E8), Color(0xFFFFD1D8), Color(0xFF2A0610), Color(0xFFFFE9A0), Color(0xFFFFC9D2),
            Color(0x22000000), Color(0x40FFFFFF), Color(0x4DFFFFFF), Color(0xFFFFE9A0), Color(0xFFFFE9A0), true),
        AluGlass("Xanh olive", Color(0xFF5A6418), Color(0xFF7B8A22), Color(0xFFA2B445), Color(0x55F4FFB0),
            Color(0xFF141904), Color(0xB3262E08), Color(0xFF3B4A00), Color(0xFF12160A), Color(0xFFD9F06A), Color(0xFFB8CC55),
            Color(0x30FFFFFF), Color(0x47222800), Color(0x59222800), Color(0xFF8E1B12), Color(0xFFC8250F), false),
        AluGlass("Tím nho", Color(0xFF3F1760), Color(0xFF62298C), Color(0xFF8C4FB8), Color(0x55D9B5FF),
            Color(0xFFFBF3FF), Color(0xB3EAD6FF), Color(0xFFE6C8FF), Color(0xFF1A0828), Color(0xFFFFE08A), Color(0xFFD9B8F5),
            Color(0x22000000), Color(0x40FFFFFF), Color(0x4DFFFFFF), Color(0xFFFFE08A), Color(0xFFFFD24A), true),
        AluGlass("Xanh sapphire", Color(0xFF144E94), Color(0xFF2E78C4), Color(0xFF63A8E8), Color(0x55CFE8FF),
            Color(0xFF04162B), Color(0xB3081F3A), Color(0xFF062B55), Color(0xFF06101E), Color(0xFF9FD4FF), Color(0xFF7FB8E8),
            Color(0x30FFFFFF), Color(0x47061A33), Color(0x59061A33), Color(0xFF8E1B12), Color(0xFFC8250F), false),
        AluGlass("Lục bảo", Color(0xFF08503A), Color(0xFF0E7556), Color(0xFF2BA47C), Color(0x55B5FFE0),
            Color(0xFFF2FFF9), Color(0xB3D3F5E8), Color(0xFFC4F2DE), Color(0xFF04180F), Color(0xFFFFE08A), Color(0xFFA8E3C8),
            Color(0x22000000), Color(0x40FFFFFF), Color(0x4DFFFFFF), Color(0xFFFFE08A), Color(0xFFFFD24A), true),
        AluGlass("Trắng băng", Color(0xFFBFD6E3), Color(0xFFDCEAF2), Color(0xFFF4FAFD), Color(0x99FFFFFF),
            Color(0xFF0A1A24), Color(0xB30F2A38), Color(0xFF1D5878), Color(0xFF0C1A24), Color(0xFF9FD4FF), Color(0xFF7FA8C0),
            Color(0x33FFFFFF), Color(0x470A1A24), Color(0x590A1A24), Color(0xFF9E1C14), Color(0xFFC8250F), false),
        AluGlass("Đen chữ vàng", Color(0xFF08080A), Color(0xFF0F0F12), Color(0xFF1A1A1E), Color(0x22FFD24A),
            Color(0xFFFFD24A), Color(0xB3E6C05A), Color(0xFFFFE08A), Color(0xFF5A4308), Color(0xFFFFE9A0), Color(0xFFD9B24A),
            Color(0x14FFFFFF), Color(0x40FFD24A), Color(0x40FFD24A), Color(0xFFFF7A6B), Color(0xFFFF4A3A), true),
    )
    private val SILVER = Triple(Color(0xFFF6F7F8), Color(0xFFD2D6DA), Color(0xFFA9AFB5))
    val METAL = listOf(
        AluMetal("Nhôm phay xước", listOf(Color(0xFFE4E0D5), Color(0xFFD9D5CA), Color(0xFFD0CBBE), Color(0xFFD8D4C9), Color(0xFFC8C3B6)), 0,
            Color(0xFF1A1A1A), Color(0xCCFFFFFF), Color(0xFFB8860B), SILVER.first, SILVER.second, SILVER.third, Color(0xFF1A1A1A), Color(0xFFF8F9FA), Color(0xFF6D7379), 0.20f, 0.07f),
        AluMetal("Nhôm bóng", listOf(Color(0xFFF7F8F9), Color(0xFFDADDE0), Color(0xFFB9BDC2), Color(0xFFEEF0F2), Color(0xFFA9AEB3)), 1,
            Color(0xFF1A1A1A), Color(0xCCFFFFFF), Color(0xFFB8860B), SILVER.first, SILVER.second, SILVER.third, Color(0xFF1A1A1A), Color(0xFFF8F9FA), Color(0xFF6D7379), 0f, 0f),
        AluMetal("Nhôm nhám", listOf(Color(0xFFD9DADB), Color(0xFFCFD0D2), Color(0xFFC6C7C9), Color(0xFFCFD0D2), Color(0xFFBDBEC0)), 2,
            Color(0xFF1A1A1A), Color(0xB3FFFFFF), Color(0xFFB8860B), SILVER.first, SILVER.second, SILVER.third, Color(0xFF1A1A1A), Color(0xFFF8F9FA), Color(0xFF6D7379), 0.10f, 0.10f),
        AluMetal("Nhôm đen", listOf(Color(0xFF3A3B3E), Color(0xFF2D2E31), Color(0xFF242528), Color(0xFF2E2F32), Color(0xFF1C1D1F)), 0,
            Color(0xFFE6E6E6), Color(0x99000000), Color(0xFFE3B94F), Color(0xFF6A6C70), Color(0xFF3F4044), Color(0xFF2A2B2E), Color(0xFFEDEDED), Color(0xFF8A8C90), Color(0xFF111112), 0.07f, 0.12f, true),
        AluMetal("Titan", listOf(Color(0xFFB3AFA7), Color(0xFF9E9A92), Color(0xFF8F8B84), Color(0xFFA09C95), Color(0xFF827E78)), 0,
            Color(0xFF1E1D1A), Color(0x80000000), Color(0xFFFFE08A), Color(0xFFD8D4CC), Color(0xFFB0ACA4), Color(0xFF8A867F), Color(0xFF1E1D1A), Color(0xFFEDE9E1), Color(0xFF55524D), 0.18f, 0.12f),
        AluMetal("Nhôm xám", listOf(Color(0xFFA5A8AC), Color(0xFF96999D), Color(0xFF8C8F94), Color(0xFF989B9F), Color(0xFF7F8287)), 0,
            Color(0xFF17181A), Color(0x80000000), Color(0xFFFFD866), Color(0xFFDADCDF), Color(0xFFB3B6BA), Color(0xFF8D9095), Color(0xFF17181A), Color(0xFFEFF0F2), Color(0xFF4F5155), 0.14f, 0.10f),
    )

    val glass = mutableStateOf(0)
    val metal = mutableStateOf(0)
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("alutheme", Context.MODE_PRIVATE)
        glass.value = sp.getInt("glass", 0).coerceIn(0, GLASS.lastIndex)
        metal.value = sp.getInt("metal", 0).coerceIn(0, METAL.lastIndex)
    }
    fun setGlass(i: Int) { glass.value = i; if (::sp.isInitialized) sp.edit().putInt("glass", i).apply() }
    fun setMetal(i: Int) { metal.value = i; if (::sp.isInitialized) sp.edit().putInt("metal", i).apply() }
    val g: AluGlass get() = GLASS[glass.value.coerceIn(0, GLASS.lastIndex)]
    val m: AluMetal get() = METAL[metal.value.coerceIn(0, METAL.lastIndex)]
}
