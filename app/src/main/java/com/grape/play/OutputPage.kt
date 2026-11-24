@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.grape.play

import android.media.AudioDeviceInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Một lựa chọn đầu ra: thiết bị âm thanh của TV/máy (tv = true) hoặc thiết bị stream không dây. */
private class OutItem(val key: String, val icon: ImageVector, val title: String, val sub: String, val tv: Boolean, val type: Int = 0, val r: Renderer? = null, val devKey: String = "")

private fun iconFor(type: Int): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> Icons.Outlined.SettingsInputHdmi
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Outlined.Usb
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> Icons.Outlined.SettingsInputComponent
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Outlined.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Outlined.Headphones
    else -> Icons.Outlined.Speaker
}

/** Trang "Đầu ra âm thanh": trái = đầu ra của máy + thiết bị stream; phải = chi tiết thiết bị đang chọn. */
@Composable
fun OutputPage(onNav: (Screen) -> Unit) {
    val compact = LocalCompact.current
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var hires by remember { mutableStateOf(AudioSettings.hires) }
    var dev by remember { mutableStateOf(AudioSettings.deviceKey) }
    var startup by remember { mutableStateOf(AudioSettings.startupKey) }
    var devices by remember { mutableStateOf(AudioSettings.sinks(ctx)) }
    // Danh sách tự cập nhật khi cắm/rút thiết bị (USB DAC, HDMI, Bluetooth…)
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { devices = AudioSettings.sinks(ctx) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { devices = AudioSettings.sinks(ctx) }
        }
        am.registerAudioDeviceCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    val fmt by Playback.level.fmt.collectAsState()
    val remote by Playback.remote.collectAsState()
    var found by remember { mutableStateOf<List<Renderer>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    BackHandler { onNav(Screen.LIB) }

    fun commit() { AudioSettings.hires = hires; AudioSettings.deviceKey = dev; Playback.applySettings() }
    fun scan() {
        if (scanning) return
        scanning = true
        scope.launch { found = runCatching { Renderers.discover(ctx) }.getOrDefault(emptyList()); scanning = false; scanned = true }
    }
    LaunchedEffect(Unit) { scan(); if (!compact) runCatching { first.requestFocus() } }

    // Danh sách lựa chọn
    val tvItems = buildList {
        add(OutItem("tv:default", Icons.Outlined.Tv, "Mặc định hệ thống", if (Device.tv) "Theo cài đặt âm thanh của TV" else "Theo cài đặt âm thanh của máy", true, 0, null, ""))
        devices.forEach { d ->
            val name = d.productName?.toString()?.takeIf { it.isNotBlank() }
            val rate = d.sampleRates.maxOrNull()?.takeIf { it > 0 }?.let { "PCM tối đa ${khz(it)}" }
            val k = AudioSettings.keyOf(d)
            add(OutItem("tv:$k", iconFor(d.type), AudioSettings.title(d, devices), listOfNotNull(name, rate).joinToString(" · ").ifEmpty { "Đầu ra do Android báo" }, true, d.type, null, k))
        }
        // Thiết bị đã chọn nhưng đang rút ra: vẫn hiện để biết app đang chờ nó (app tạm dùng mặc định hệ thống và tự chuyển khi cắm lại)
        if (dev.isNotEmpty() && devices.none { AudioSettings.keyOf(it) == dev } && AudioSettings.match(dev, devices) == null) {
            val t = AudioSettings.typeOf(dev)
            add(OutItem("tv:$dev", iconFor(t), AudioSettings.currentName(ctx), "Chưa kết nối — app tạm dùng mặc định hệ thống và tự chuyển lại khi cắm", true, t, null, dev))
        }
        // Bluetooth luôn có mặt trong danh sách (kể cả khi chưa kết nối): chọn trước, ghép đôi sau — app tự chuyển sang khi loa/tai nghe kết nối
        if (devices.none { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } && AudioSettings.typeOf(dev) != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            val bk = "${AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}||"
            add(OutItem("tv:$bk", Icons.Outlined.Bluetooth, "Bluetooth", "Chưa kết nối — ghép đôi loa/tai nghe trong Cài đặt Bluetooth của ${if (Device.tv) "TV" else "máy"}", true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null, bk))
        }
    }
    val renderers = (found + listOfNotNull(remote).filter { r -> found.none { it.id == r.id } })
    val rnItems = renderers.map { r -> OutItem("rn:${r.id}", if (r.kind == "cast") Icons.Outlined.Cast else Icons.Outlined.SettingsInputAntenna, r.name,
        (if (r.kind == "cast") "Google Cast" else "UPnP / OpenHome") + " · " + r.host, false, 0, r) }

    // Khoá thẻ đang dùng: thiết bị đã lưu có thể khớp một thiết bị cắm lại với địa chỉ khác → dùng khoá của thiết bị khớp
    val activeKey = remote?.let { "rn:${it.id}" } ?: "tv:" + (if (dev.isEmpty()) "default" else AudioSettings.match(dev, devices)?.let { AudioSettings.keyOf(it) } ?: dev)
    var selKey by remember { mutableStateOf(activeKey) }
    var pending by remember { mutableStateOf<OutItem?>(null) }
    val sel = (tvItems + rnItems).firstOrNull { it.key == selKey } ?: tvItems.first()

    @Composable
    fun OutCard(o: OutItem, req: FocusRequester?) {
        val chosen = o.key == selKey
        val active = o.key == activeKey
        OptionCard(o.icon, o.title, o.sub, chosen, tag = if (active) "ĐANG DÙNG" else if (chosen) "ĐANG CHỌN" else "", requester = req, onFocus = { selKey = o.key }) { selKey = o.key; if (!active) pending = o }
    }

    @Composable
    fun Left(m: Modifier) = Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(if (Device.tv) "TRÊN TV NÀY" else "TRÊN MÁY NÀY")
        tvItems.forEachIndexed { i, o -> OutCard(o, if (i == 0) first else null) }
        Spacer(Modifier.height(6.dp))
        SectionLabel("KHÔNG DÂY — NETWORK PLAYER")
        rnItems.forEach { OutCard(it, null) }
        if (scanning) T("Đang tìm thiết bị…", 15, C.Muted)
        else if (scanned && Renderers.report.isNotEmpty()) T(Renderers.report, 12, C.Muted, lines = 6)
        if (!scanning && scanned && rnItems.isEmpty()) T("Không thấy thiết bị nào. Kiểm tra thiết bị và ${if (Device.tv) "TV" else "máy"} cùng mạng Wi-Fi/LAN, rồi quét lại.", 14, C.Muted, lines = 3)
    }

    @Composable
    fun Right(m: Modifier) = DetailPanel(m, scroll = !compact) {
        SectionLabel("THÔNG TIN THIẾT BỊ")
        T(sel.title, if (compact) 28 else 38, serif = true, lines = 2)
        T(sel.sub, 16, C.Muted, lines = 2)
        val r = sel.r
        if (r != null) {
            val upnp = r.kind != "cast"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (upnp) "UPnP" else "Google Cast")
                if (upnp) Tag(if (AudioSettings.dsdRemote == AudioSettings.DSD_DOP) "DSD: DoP" else "DSD: file gốc", C.PlumSoft)
            }
            Column(Modifier.fillMaxWidth()) {
                SpecRow("Giao thức", if (upnp) "UPnP AV / OpenHome" else "Google Cast")
                SpecRow("Địa chỉ", r.host)
                SpecRow("DSD", if (!upnp) "Không hỗ trợ" else if (AudioSettings.dsdRemote == AudioSettings.DSD_DOP) "DSD64 qua DoP (24-bit/176,4 kHz)" else "Gửi file DSD gốc")
                SpecRow("Âm lượng", "Chỉnh trên thiết bị nhận")
            }
            NoteBox {
                T("Đường tín hiệu", 14, C.Muted, bold = true)
                T("File gốc được gửi nguyên bản (không chuyển mã) qua Wi-Fi/LAN; thiết bị nhận tự giải mã. Thanh âm lượng của ${if (Device.tv) "TV" else "máy"} không ảnh hưởng.", 16, C.Text, lines = 4)
            }
        } else {
            Tag(if (hires) "Yêu cầu đầu ra float 32-bit" else "PCM 16-bit")
            // Chế độ PCM
            SectionLabel("CHẾ ĐỘ PCM")
            OptionCard(Icons.Outlined.GraphicEq, "Tương thích (PCM 16-bit)", "Chế độ tương thích, ổn định nhất — mọi thiết bị đều ra tiếng. Dữ liệu 24-bit bị hạ xuống 16-bit.", !hires) { hires = false; commit() }
            OptionCard(Icons.Outlined.HighQuality, "Hi-res (PCM float 32-bit)", "Yêu cầu ExoPlayer dùng đầu ra float 32-bit, không hạ xuống 16-bit trong app. Android/thiết bị vẫn có thể đổi định dạng và KHÔNG bảo đảm độ sâu bit thật tại DAC. Nếu file hi-res im lặng thì quay lại Tương thích.", hires) { hires = true; commit() }
            // Mặc định khi mở app
            SectionLabel("KHI MỞ APP")
            OptionCard(Icons.Outlined.History, "Giữ lựa chọn lần dùng trước", "", startup.isEmpty()) { startup = ""; AudioSettings.startupKey = startup }
            OptionCard(Icons.Outlined.PushPin, "Luôn bắt đầu bằng ${sel.title}", "Nếu thiết bị chưa cắm, app tạm dùng mặc định hệ thống và tự chuyển khi cắm.", startup == (if (sel.devKey.isEmpty()) AudioSettings.DEFAULT else sel.devKey)) { startup = if (sel.devKey.isEmpty()) AudioSettings.DEFAULT else sel.devKey; AudioSettings.startupKey = startup }
            NoteBox {
                T("Đường tín hiệu", 14, C.Muted, bold = true)
                T("Đang đưa ra: ${fmt.ifEmpty { "chưa phát" }}", 17, C.Gold, lines = 2)
                if (Device.tv) T("Nếu TV nối ampli qua cổng quang/analogue, hãy chọn “Mặc định hệ thống”. Trên TV Sony, việc chuyển loa / cổng quang / tai nghe thường do Cài đặt ▸ Âm thanh ▸ Đầu ra âm thanh của TV quyết định.", 14, C.Muted, lines = 5)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            GhostButton(if (scanning) "Đang quét…" else "Quét lại thiết bị") { scan() }
        }
    }

    if (compact) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("Đầu ra âm thanh", "Phát trên ${if (Device.tv) "TV" else "máy"} hoặc stream tới Network Player")
        Left(Modifier.fillMaxWidth())
        Right(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxSize().pageBg()) {
        Rail(Screen.OUTPUT, onNav)
        Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 36.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeader("Đầu ra âm thanh", "Phát trên TV hoặc stream tới Network Player")
                Left(Modifier.fillMaxWidth())
                ScrollTail()
            }
            Right(Modifier.weight(1f).fillMaxHeight())
        }
    }
    pending?.let { p ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { pending = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            val ok = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { ok.requestFocus() } }
            val sm = !compact
            Column(
                (if (compact) Modifier.dlgW(520) else Modifier.dlgFrac(0.3f)).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(C.Panel)
                    .border(1.dp, C.Line, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).dlgPad(24, 16),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T("Chọn đầu ra này?", if (sm) 18 else 24, serif = true)
                T(p.title, if (sm) 15 else 20, C.Gold, bold = true, lines = 2)
                T(p.sub, if (sm) 12 else 15, C.Muted, lines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Focusable({ pending = null }) { T("Huỷ", if (sm) 14 else 18, C.Muted, Modifier.padding(if (sm) 18.dp else 24.dp, if (sm) 7.dp else 10.dp), bold = true) }
                    Focusable({
                        pending = null
                        if (p.tv) { dev = p.devKey; commit(); if (remote != null) Playback.setTarget(null) } else p.r?.let { Playback.setTarget(it) }
                    }, requester = ok) { T("Chọn", if (sm) 14 else 18, C.Gold, Modifier.padding(if (sm) 18.dp else 24.dp, if (sm) 7.dp else 10.dp), bold = true) }
                }
            }
        }
    }

}
