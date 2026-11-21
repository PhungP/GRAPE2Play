package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Trạng thái các hộp thoại toàn cục mở từ thanh bên trái (hiển thị ở MainActivity). */
object Overlay {
    val spectrum = mutableStateOf(false)
    val exit = mutableStateOf(false)
    val ai = mutableStateOf(false)
    /** Nút micro trên điều khiển TV: mở trợ lý AI và bắt đầu nghe. */
    val aiListen = mutableStateOf(false)
    /** Câu hỏi chuyển từ Tìm nhanh sang Hỏi AI. */
    var aiAsk = ""
    var aiStart = "menu"
}

/**
 * Thanh điều hướng mỏng bên trái: logo + 6 mục (Đang phát, Thư viện, Nguồn nhạc, Đầu ra, Phổ tần, Cài đặt), biểu tượng đường nét mảnh;
 * mục đang mở có nền nhấn. Góc dưới: huy hiệu, Thoát và Quét lại. Nguồn nhạc / Đầu ra là trang riêng (Screen.SOURCES / Screen.OUTPUT).
 * Hai tham số cuối giữ lại để tương thích, không còn dùng.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Rail(cur: Screen, onNav: (Screen) -> Unit, onSources: () -> Unit = {}, onOut: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scanning by Library.scanning.collectAsState()
    val count by Library.tracks.collectAsState()
    val remote by Playback.remote.collectAsState()
    val st = Themes.style.value
    val rm = when (st) {
        5 -> Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp).width(132.dp).fillMaxHeight().sfPanel()
        4 -> Modifier.width(132.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(C.Panel, C.Side))).drawBehind {
            drawLine(Brush.verticalGradient(listOf(Color.Transparent, C.Gold.copy(alpha = .5f), Color.Transparent)), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 2f)
        }
        GlassTheme.ID -> Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp).width(132.dp).fillMaxHeight().glassPanel(36.dp)
        else -> Modifier.width(132.dp).fillMaxHeight().background(C.Side)
    }
    Column(
        rm.padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        GrapeLogo(Modifier.size(56.dp))
        Wordmark(15, Modifier.padding(top = 4.dp))
        LimitedBadge()
        Spacer(Modifier.height(10.dp))
        RailItem(Icons.Outlined.MusicNote, "Đang phát", cur == Screen.NOW) { onNav(Screen.NOW) }
        RailItem(Icons.Outlined.GridView, "Thư viện", cur == Screen.LIB) { onNav(Screen.LIB) }
        RailItem(Icons.Outlined.CloudQueue, "Nguồn nhạc", cur == Screen.SOURCES) { onNav(Screen.SOURCES) }
        RailItem(Icons.Outlined.Cast, "Đầu ra", cur == Screen.OUTPUT, highlight = remote != null) { onNav(Screen.OUTPUT) }
        RailItem(Icons.Outlined.Equalizer, "Phổ tần", false) { Overlay.spectrum.value = true }
        RailItem(Icons.Outlined.AutoAwesome, "Trợ lý AI", false) { Overlay.aiStart = "menu"; Overlay.ai.value = true }
        RailItem(Icons.Outlined.Settings, "Cài đặt", cur == Screen.SOUND) { onNav(Screen.SOUND) }
        if (!scanning) RailItem(Icons.Outlined.Refresh, "Quét lại", false) { Library.rescanAllAsync(ctx.applicationContext) }
        Spacer(Modifier.weight(1f))
        if (scanning) T("Đang quét… ${count.size} bài", 12, C.Gold)
        RailItem(Icons.Outlined.PowerSettingsNew, "Thoát", false, tintOverride = Color(0xFFE5575A)) { Overlay.exit.value = true }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, highlight: Boolean = false, tintOverride: Color? = null, onClick: () -> Unit) =
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), selected = selected) {

        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, tint = tintOverride ?: if (selected || highlight) C.Gold else C.Muted, modifier = Modifier.size(30.dp))
            T(label, 13, if (selected) C.Gold else C.Muted, bold = selected)
        }
    }

/**
 * Huy hiệu "Limited Edition" (giữ nguyên tiếng Anh ở mọi ngôn ngữ): chữ serif nghiêng, vàng chuyển sắc, khung vàng mảnh.
 * [engraved] = khắc laser chìm trên nhôm: chữ và khung màu than, kèm vệt sáng mảnh lệch xuống dưới/phải như vết khắc lõm.
 */
@Composable
fun LimitedBadge(engraved: Boolean = false) =
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LimitedArt(104.dp, if (engraved) AluTheme.m.gold else C.Gold) }
