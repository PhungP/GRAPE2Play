package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grape.play.BitPerfect.DevLevel
import com.grape.play.BitPerfect.Route

private fun s(vi: String, en: String) = if (Lang.en.value) en else vi

fun connLabel(r: Route): String = when (r) {
    Route.USB -> "USB Audio"
    Route.HDMI -> "HDMI"
    Route.SPDIF -> s("S/PDIF (quang hoặc đồng trục — Android không phân biệt)", "S/PDIF (optical or coaxial — Android cannot tell)")
    Route.BLUETOOTH -> "Bluetooth"
    Route.UPNP -> "UPnP/DLNA"
    Route.CAST -> "Google Cast"
    Route.SPEAKER -> s("Loa tích hợp", "Built-in speaker")
    Route.WIRED_ANALOG -> s("Analog có dây", "Wired analog")
    Route.ROUTE_UNKNOWN -> "System Default – Route Unknown"
    else -> s("Khác", "Other")
}

fun devLevelText(l: DevLevel): String = when (l) {
    DevLevel.NONE -> s("Chưa có hồ sơ xác minh", "No verification profile")
    DevLevel.USER_DECLARED -> s("Bạn tự khai báo — chưa được xác minh", "Declared by you — not verified")
    DevLevel.REGISTRY_UNVERIFIED -> s("Có trong danh mục nhưng chưa có căn cứ Bit-perfect cho phương thức này", "In the registry, but no Bit-perfect basis for this connection")
    DevLevel.REGISTRY_CONFIG -> s("Verified – Configuration (theo thông số kỹ thuật, không phải phép đo toàn tuyến)", "Verified – Configuration (from technical specs, not an end-to-end measurement)")
    DevLevel.REGISTRY_E2E -> "Verified – End-to-end"
}

@Composable
internal fun DevField(label: String, value: String, onChange: (String) -> Unit) {
    var f by remember { mutableStateOf(false) }
    T(label, 12, C.Muted, bold = true)
    Box(Modifier.fillMaxWidth().border(if (f) 2.dp else 1.dp, if (f) C.Gold else C.Dim, RoundedCornerShape(10.dp)).background(C.Inset, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = C.Text, fontSize = 17.sp), cursorBrush = SolidColor(C.Gold),
            modifier = Modifier.fillMaxWidth().onFocusChanged { f = it.isFocused },
        )
    }
}

@Composable
internal fun Frame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val compact = LocalCompact.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            (if (compact) Modifier.dlgW(560) else Modifier.dlgFrac(0.5f)).clip(RoundedCornerShape(14.dp)).background(C.Panel)
                .border(1.dp, C.Line, RoundedCornerShape(14.dp)).dlgPad(24, 16).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

/** Hộp thoại xác nhận thiết bị: ngắn gọn; chỉ hiện khi DeviceProfiles.pending != null (xem DeviceRegistry.decide). */
@Composable
fun DeviceConfirmDialog() {
    val pend by DeviceProfiles.pending.collectAsState()
    val p = pend ?: return
    val first = remember(p) { FocusRequester() }
    LaunchedEffect(p) { runCatching { first.requestFocus() } }
    val det = p.det
    Frame(onDismiss = { DeviceProfiles.later(p) }) {
        if (p.declare) {
            var maker by remember(p) { mutableStateOf("") }
            var model by remember(p) { mutableStateOf(det.name) }
            var notes by remember(p) { mutableStateOf("") }
            T(s("Khai báo thiết bị", "Declare device"), 20, serif = true)
            T(s("Thiết bị phát hiện: ", "Detected device: ") + det.name + " · " + connLabel(det.conn), 14, C.Muted, lines = 3)
            DevField(s("Hãng", "Manufacturer"), maker) { maker = it }
            DevField(s("Model", "Model"), model) { model = it }
            DevField(s("Ghi chú kỹ thuật (tuỳ chọn)", "Technical notes (optional)"), notes) { notes = it }
            T(s("Thông tin bạn khai báo chỉ lưu trong hồ sơ cá nhân; KHÔNG tự đưa vào danh mục Verified và KHÔNG làm thiết bị được coi là Bit-perfect.",
                "What you declare is stored in your personal profile only; it is NOT added to the Verified registry and does NOT make the device count as Bit-perfect."), 12, C.Muted, lines = 5)
            Focusable({ DeviceProfiles.declare(det, maker, model, notes) }, requester = first) { T(s("Lưu hồ sơ", "Save profile"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
            Focusable({ DeviceProfiles.later(p) }) { T(s("Để sau", "Later"), 16, C.Muted, Modifier.padding(24.dp, 8.dp)) }
        } else if (p.ask == DeviceRegistry.Ask.REGISTRY_MATCH && p.match.entry != null) {
            val e = p.match.entry!!
            T(s("GRAPE2Play phát hiện thiết bị", "GRAPE2Play detected a device"), 20, serif = true)
            T(det.name.ifEmpty { e.label() }, 18, C.Gold, bold = true, lines = 2)
            T(s("Phương thức kết nối: ", "Connection: ") + connLabel(det.conn), 14, C.Text, lines = 3)
            T(s("Khớp bản ghi danh mục: ", "Matches registry entry: ") + e.label() + " · " + connLabel(e.conn), 13, C.Muted, lines = 3)
            T(devLevelText(e.level), 13, if (e.level == DevLevel.REGISTRY_CONFIG) C.Text else C.Muted, lines = 4)
            if (p.match.quality == DeviceRegistry.NAME_ONLY) T(s("Khớp theo tên (không đọc được mã phần cứng USB).", "Matched by name (the USB hardware ID could not be read)."), 12, C.Muted, lines = 3)
            if (!det.activeKnown) T(s("Lưu ý: Android không xác nhận thiết bị này đang thực sự nhận âm thanh.", "Note: Android does not confirm that this device is actually receiving the audio."), 12, C.Muted, lines = 3)
            T(s("Đây có đúng là thiết bị bạn đang sử dụng không? Xác nhận chỉ liên kết hồ sơ, không chứng minh dữ liệu được bảo toàn.", "Is this the device you are using? Confirming only links your profile; it does not prove the data is preserved."), 13, C.Text, lines = 5)
            Focusable({ DeviceProfiles.confirmYes(p) }, requester = first) { T(s("Đúng thiết bị", "Yes, this device"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
            Focusable({ DeviceProfiles.confirmNo(p) }) { T(s("Không đúng thiết bị", "Not this device"), 16, C.Muted, Modifier.padding(24.dp, 8.dp)) }
        } else {
            T(s("Phát hiện thiết bị mới", "New device detected"), 20, serif = true)
            T(det.name, 18, C.Gold, bold = true, lines = 2)
            T(s("Phương thức kết nối: ", "Connection: ") + connLabel(det.conn), 14, C.Text, lines = 3)
            T(s("Thiết bị này chưa có trong danh mục Bit-perfect tích hợp. Bạn có thể khai báo hãng/model để lưu vào hồ sơ cá nhân (không tự thành Verified).", "This device is not in the built-in Bit-perfect registry. You can declare its make/model for your personal profile (it will not become Verified)."), 13, C.Muted, lines = 5)
            Focusable({ DeviceProfiles.pending.value = DeviceProfiles.Pending(det, p.match, p.ask, declare = true) }, requester = first) { T(s("Khai báo thiết bị", "Declare device"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
            Focusable({ DeviceProfiles.later(p) }) { T(s("Để sau", "Later"), 16, C.Muted, Modifier.padding(24.dp, 8.dp)) }
        }
    }
}

/** Quản lý hồ sơ thiết bị: xem, sửa (chỉ phần người dùng khai báo), xoá; phân biệt dữ liệu app phát hiện / người dùng khai báo / danh mục tích hợp. */
@Composable
fun DeviceManagerDialog() {
    val ver by DeviceProfiles.version.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var editing by remember { mutableStateOf<String?>(null) }
    var maker by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val close = { DeviceProfiles.manage.value = false }
    val profiles = remember(ver) { DeviceProfiles.store.profiles.toList() }
    Frame(onDismiss = { close() }) {
        T(s("Thiết bị Bit-perfect đã xác nhận", "Confirmed Bit-perfect devices"), 20, serif = true)
        if (profiles.isEmpty()) T(s("Chưa có thiết bị nào. Thiết bị USB/HDMI/S/PDIF/Bluetooth sẽ được hỏi xác nhận khi phát hiện.", "No devices yet. USB/HDMI/S/PDIF/Bluetooth devices are confirmed when detected."), 13, C.Muted, lines = 4)
        for (p in profiles) {
            val entry = if (p.entryId.isEmpty()) null else DeviceRegistry.byId(DeviceProfiles.regs, p.entryId)
            SectionLabel(p.label().ifEmpty { p.name })
            T(s("Kết nối đã xác nhận: ", "Confirmed connection: ") + connLabel(p.conn), 13, C.Text, lines = 3)
            T(s("Nguồn dữ liệu: ", "Data source: ") + if (p.source == DeviceRegistry.SRC_USER) s("bạn khai báo", "declared by you") else s("app phát hiện, bạn xác nhận", "detected by the app, confirmed by you"), 12, C.Muted, lines = 2)
            if (p.vid >= 0) T("USB VID:PID " + Integer.toHexString(p.vid) + ":" + Integer.toHexString(p.pid), 12, C.Muted)
            if (entry != null) {
                T(devLevelText(entry.level), 12, C.Muted, lines = 4)
                if (entry.bits.isNotEmpty()) T(s("Danh mục: PCM ", "Registry: PCM ") + entry.bits.joinToString("/") + "-bit · " + s("tối đa ", "up to ") + khz(entry.rates.maxOrNull() ?: 0) + " · DSD: " + entry.dsd, 12, C.Muted, lines = 4)
                T(s("Nguồn xác minh: ", "Verification source: ") + entry.source + " (" + entry.updated + ")", 11, C.Dim, lines = 6)
            } else T(devLevelText(DevLevel.USER_DECLARED), 12, C.Muted, lines = 3)
            if (p.notes.isNotEmpty()) T(s("Ghi chú: ", "Notes: ") + p.notes, 12, C.Muted, lines = 4)
            if (editing == p.key) {
                DevField(s("Hãng", "Manufacturer"), maker) { maker = it }
                DevField("Model", model) { model = it }
                DevField(s("Ghi chú", "Notes"), notes) { notes = it }
                Focusable({ DeviceProfiles.edit(p.key, maker, model, notes); editing = null }) { T(s("Lưu", "Save"), 15, C.Gold, Modifier.padding(16.dp, 6.dp), bold = true) }
            } else {
                if (p.source == DeviceRegistry.SRC_USER) Focusable({ maker = p.manufacturer; model = p.model; notes = p.notes; editing = p.key }) { T(s("Sửa thông tin khai báo", "Edit declared info"), 14, C.Text, Modifier.padding(16.dp, 6.dp)) }
                Focusable({ DeviceProfiles.remove(p.key) }) { T(s("Xóa khỏi hồ sơ cá nhân", "Remove from my profile"), 14, C.Muted, Modifier.padding(16.dp, 6.dp)) }
            }
        }
        SectionLabel(s("DANH MỤC TÍCH HỢP (chỉ đọc)", "BUILT-IN REGISTRY (read-only)"))
        for (e in DeviceProfiles.regs) T("• " + e.label() + " · " + connLabel(e.conn) + " — " + devLevelText(e.level), 12, C.Muted, lines = 4)
        T(s("Danh mục còn nhỏ và chỉ dựa trên thông số nhà sản xuất; GRAPE2Play chưa tự kiểm thử thiết bị thật nào. Thông tin bạn khai báo không ghi đè danh mục.", "The registry is small and based on manufacturer specs only; GRAPE2Play has not tested any real device. What you declare never overrides the registry."), 11, C.Dim, lines = 6)
        Focusable({ close() }, requester = first) { T(s("Đóng", "Close"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
    }
}
