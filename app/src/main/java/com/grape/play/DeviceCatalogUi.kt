package com.grape.play

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.grape.play.BitPerfect.Route
import com.grape.play.DeviceCatalog.Config
import com.grape.play.DeviceCatalog.Device
import com.grape.play.DeviceCatalog.Status
import com.grape.play.DeviceVerification.Confidence
import com.grape.play.DeviceVerification.Evidence
import com.grape.play.DeviceVerification.Source

/**
 * Bit-perfect Verified Devices (danh mục công khai trong app) + My Audio Devices. Chỉ ĐỌC dữ liệu; không đụng vào Playback/Audio Engine.
 * Danh mục là tài liệu tra cứu offline (assets/device_catalog.tsv). Mức xác minh luôn hiển thị theo TỪNG kết nối, không có huy hiệu "Verified" chung.
 */
object DeviceCatalogStore {
    val open = MutableStateFlow(false)
    val mine = MutableStateFlow(false)
    val detail = MutableStateFlow<String?>(null)
    @Volatile var catalog: DeviceCatalog.Catalog = DeviceCatalog.Catalog(); private set
    /** Báo cáo cộng đồng đi kèm app (hiện rỗng: không có báo cáo nào được phát hành; không bịa dữ liệu). */
    @Volatile var community: List<DeviceVerification.CommunityReport> = emptyList(); private set

    fun init(ctx: Context) {
        if (catalog.devices.isNotEmpty()) return
        catalog = runCatching { DeviceCatalog.parse(ctx.assets.open("device_catalog.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }) }.getOrDefault(DeviceCatalog.Catalog())
        community = runCatching { DeviceVerification.parseCommunity(ctx.assets.open("community_reports.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }) }.getOrDefault(emptyList())
        if (catalog.problems.isNotEmpty()) AppLog.i("Catalog", "Danh mục thiết bị: ${catalog.problems.size} cảnh báo dữ liệu (đã hạ mức/bỏ qua)")
    }
}

private fun s(vi: String, en: String) = if (Lang.en.value) en else vi

fun statusLabel(st: Status): String = when (st) {
    Status.CONFIG_VERIFIED -> s("Cấu hình Bit-perfect đã xác minh", "Bit-perfect capable configuration verified")
    Status.CAPABILITY_VERIFIED -> s("Đã xác minh khả năng định dạng/giao thức", "Format/protocol capability verified")
    Status.CANDIDATE -> s("Ứng viên — chưa xác minh đầy đủ", "Candidate — not fully verified")
    Status.NOT_BIT_PERFECT -> s("Không bit-perfect theo cấu hình đã ghi nhận", "Not bit-perfect under the documented configuration")
}
private fun statusColor(st: Status): Color = when (st) {
    Status.CONFIG_VERIFIED -> Grade.GREEN.color
    Status.CAPABILITY_VERIFIED -> C.Gold
    Status.CANDIDATE -> C.Muted
    Status.NOT_BIT_PERFECT -> Grade.RED.color
}
/** Cảnh báo dùng chung: danh mục chưa đầy đủ; vắng mặt ≠ không hỗ trợ. */
private fun notice() = s("Danh mục hiện CHƯA đầy đủ (chỉ gồm các thiết bị đã nghiên cứu được). Thiết bị không có trong danh mục KHÔNG có nghĩa là không hỗ trợ Bit-perfect — chỉ là chưa được nghiên cứu.",
    "The catalog is NOT complete (it only lists devices that have been researched). A device missing from it does NOT mean it lacks Bit-perfect support — it just has not been researched.")
private fun statusMark(st: Status) = when (st) { Status.CONFIG_VERIFIED -> "● "; Status.CAPABILITY_VERIFIED -> "◐ "; Status.CANDIDATE -> "○ "; Status.NOT_BIT_PERFECT -> "✕ " }

fun levelLabel(l: Int): String = when (l) {
    1 -> s("Mức 1 — đã nhận diện thiết bị", "Level 1 — device identified")
    2 -> s("Mức 2 — khả năng theo tài liệu/báo cáo (chưa phải bit-perfect)", "Level 2 — documented/reported capability (not bit-perfect)")
    3 -> s("Mức 3 — cấu hình bit-perfect đã xác minh", "Level 3 — bit-perfect capable configuration")
    else -> s("Mức 4 — bit-perfect đầu cuối (có phép đo)", "Level 4 — end-to-end (measured)")
}
private fun dsdLabel(c: Config): String = when (c.dsd) {
    DeviceCatalog.Dsd.NATIVE -> "Native DSD" + if (c.dsdMax.isNotEmpty()) " (${c.dsdMax})" else ""
    DeviceCatalog.Dsd.DOP -> "DoP" + if (c.dsdMax.isNotEmpty()) " (${c.dsdMax})" else ""
    DeviceCatalog.Dsd.DSD_TO_PCM -> s("DSD → PCM (chuyển đổi, không phải DSD gốc)", "DSD → PCM (converted, not native DSD)")
    DeviceCatalog.Dsd.NONE -> s("không hỗ trợ DSD", "no DSD")
    DeviceCatalog.Dsd.UNDOCUMENTED -> s("chưa có tài liệu", "undocumented")
}
private fun pcmLabel(c: Config): String {
    val b = c.maxBits(); val r = c.maxRate()
    return if (b > 0 && r > 0) "$b-bit / ${khz(r)}" else if (r > 0) khz(r) else if (c.maxPcm.isNotEmpty()) c.maxPcm else s("chưa có tài liệu", "undocumented")
}
private fun catLabel(c: String) = when (c.uppercase()) {
    "STREAMER" -> s("Network streamer / UPnP", "Network streamer / UPnP"); "USB_DAC" -> "USB DAC"; "AVR" -> s("AV receiver", "AV receiver")
    "AMP" -> s("Ampli", "Amplifier"); "DAC" -> "DAC"; "SPEAKER" -> s("Loa", "Speaker"); "TV" -> "TV"; "INTERFACE" -> s("Audio interface", "Audio interface"); else -> c
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) =
    Focusable(onClick, selected = on) { T((if (on) "● " else "○ ") + text, 13, if (on) C.Gold else C.Muted, Modifier.padding(10.dp, 5.dp), bold = on) }

// ---- Nguồn xác minh (Verification Source) ≠ Độ chắc chắn kỹ thuật (Technical Confidence): hai thuộc tính độc lập.
// Ba huy hiệu cùng kích thước/kiểu; màu chỉ để phân biệt nguồn (không phải thang đánh giá); thứ tự hiển thị cố định: Manufacturer, User, Community.
fun sourceName(x: Source) = when (x) { Source.MANUFACTURER -> "Manufacturer Verified"; Source.USER -> "User Verified"; Source.COMMUNITY -> "Community Verified" }
private fun sourceColor(x: Source) = when (x) { Source.MANUFACTURER -> Color(0xFF6CA6E8); Source.USER -> Color(0xFF5CC9B7); Source.COMMUNITY -> Color(0xFFB99AE8) }
private fun sourceExplain(x: Source) = when (x) {
    Source.MANUFACTURER -> s("Đối chiếu với tài liệu kỹ thuật chính thức của nhà sản xuất (thông số, hướng dẫn, tài liệu giao thức).", "Checked against the manufacturer's official technical documentation (specs, manuals, protocol documents).")
    Source.USER -> s("Người dùng trực tiếp kiểm tra thiết bị và ghi nhận kết quả thực tế (phạm vi, phương pháp, thời gian).", "A user tested the device directly and recorded the real result (scope, method, time).")
    Source.COMMUNITY -> s("Nhiều báo cáo độc lập, nhất quán từ cộng đồng, đạt tiêu chí của app (≥3, không mâu thuẫn).", "Several independent, consistent community reports that meet the app's criteria (≥3, no conflicts).")
}
fun confidenceName(c: Confidence) = when (c) { Confidence.CONFIRMED -> "Confirmed"; Confidence.SUPPORTED -> "Supported"; Confidence.INCONCLUSIVE -> "Inconclusive"; Confidence.UNVERIFIED -> "Unverified" }
private fun confidenceExplain(c: Confidence) = when (c) {
    Confidence.CONFIRMED -> s("Bằng chứng đủ mạnh cho đúng thiết bị, kết nối và cấu hình được đánh giá.", "Evidence is strong enough for exactly this device, connection and configuration.")
    Confidence.SUPPORTED -> s("Bằng chứng kỹ thuật phù hợp nhưng chưa đủ để xác nhận toàn bộ chuỗi xử lý.", "Relevant technical evidence, but not enough to confirm the whole processing chain.")
    Confidence.INCONCLUSIVE -> s("Bằng chứng chưa đầy đủ hoặc chưa thống nhất.", "Evidence is incomplete or inconsistent.")
    Confidence.UNVERIFIED -> s("Chưa có bằng chứng đáng tin cậy.", "No reliable evidence yet.")
}

@Composable
fun SourceBadge(x: Source) {
    val col = sourceColor(x)
    Box(Modifier.border(1.5.dp, col, RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { T(sourceName(x), 12, col, bold = true) }
}

/** Các nguồn có bằng chứng đáng kể (độ chắc chắn ≠ Unverified) của một cấu hình, theo thứ tự cố định. */
private fun sourcesOf(c: Config, extra: List<Evidence> = emptyList()): List<Source> =
    Source.values().filter { sx -> (c.evidence + extra).any { it.source == sx && it.confidence != Confidence.UNVERIFIED } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigSummary(c: Config, extra: List<Evidence> = emptyList()) {
    T(statusMark(c.status) + connLabel(c.conn) + " · " + c.protocol, 13, statusColor(c.status), bold = true, lines = 3)
    T("   " + statusLabel(c.status) + " · " + levelLabel(c.level), 12, C.Muted, lines = 3)
    val src = sourcesOf(c, extra)
    if (src.isEmpty()) T("   " + s("Chưa có nguồn xác minh (chỉ nhận diện thiết bị).", "No verification source yet (device identified only)."), 11, C.Dim, lines = 2)
    else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { for (x in src) SourceBadge(x) }
}

@Composable
private fun EvidenceRow(e: Evidence, yours: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(s("Độ chắc chắn kỹ thuật: ", "Technical confidence: ") + confidenceName(e.confidence) + if (yours) s("  (trên máy bạn)", "  (on your device)") else "", 13, C.Text, bold = true, lines = 2)
        T(confidenceExplain(e.confidence), 11, C.Muted, lines = 3)
        if (e.scope.isNotEmpty()) T(s("Phạm vi: ", "Scope: ") + e.scope, 12, C.Text, lines = 3)
        T(s("Firmware: ", "Firmware: ") + e.firmware.ifEmpty { s("không nêu", "not specified") } + (if (e.date.isNotEmpty()) " · " + s("thời gian ", "date ") + e.date else ""), 12, C.Muted, lines = 2)
        if (e.note.isNotEmpty()) T(e.note, 12, C.Text, lines = 8)
        if (e.reference.isNotEmpty()) T("↗ " + e.reference, 12, C.Gold, lines = 3)
    }
}

/** Danh mục chính: tìm kiếm + bộ lọc + danh sách; chọn một thiết bị để xem chi tiết. */
@Composable
fun DeviceCatalogDialog() {
    val det by DeviceCatalogStore.detail.collectAsState()
    val cat = DeviceCatalogStore.catalog
    val device = det?.let { cat.byId(it) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ hộp thoại dựng xong, nếu không tiêu điểm D-pad vẫn ở trang bên dưới
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var conn by remember { mutableStateOf<Route?>(null) }
    var pcm by remember { mutableStateOf(0) }
    var dsd by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<Status?>(null) }
    var filters by remember { mutableStateOf(false) }
    val q = DeviceCatalog.Query().also {
        it.text = text; it.category = category; it.conn = conn; it.minPcmRate = pcm; it.minLevel = level; it.status = status
        when (dsd) { "any" -> it.anyDsd = true; "NATIVE" -> it.dsd = DeviceCatalog.Dsd.NATIVE; "DOP" -> it.dsd = DeviceCatalog.Dsd.DOP; "DSD_TO_PCM" -> it.dsd = DeviceCatalog.Dsd.DSD_TO_PCM; "NONE" -> it.dsd = DeviceCatalog.Dsd.NONE; else -> {} }
    }
    val res = remember(text, category, conn, pcm, dsd, level, status, cat) { DeviceCatalog.search(cat.devices, q) }
    Frame(onDismiss = { DeviceCatalogStore.open.value = false }) {
        // Nút đầu trang nhận tiêu điểm đầu tiên: mọi nội dung bên dưới đều tới được bằng D-pad/cuộn
        Focusable({ DeviceCatalogStore.open.value = false }, requester = first) { T(s("← Đóng", "← Close"), 14, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
        T("Bit-perfect Verified Devices", 22, serif = true)
        T(s("Danh mục thiết bị đã nghiên cứu · cập nhật nghiên cứu ", "Researched devices · research date ") + cat.researched.ifEmpty { "—" } + s(" · dùng offline", " · works offline"), 12, C.Muted, lines = 2)
        T(s("Đây là tài liệu tra cứu, KHÔNG phải kết quả đo của GRAPE2Play. Mỗi kết nối (UPnP, USB, HDMI…) được đánh giá riêng; có tên trong danh mục không có nghĩa là đã bit-perfect.",
            "This is a reference, NOT measurements made by GRAPE2Play. Each connection (UPnP, USB, HDMI…) is assessed separately; being listed does not mean bit-perfect."), 12, C.Muted, lines = 5)
        T(notice(), 12, C.Gold, lines = 6)
        DevField(s("Tìm theo hãng hoặc model", "Search manufacturer or model"), text) { text = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Focusable({ filters = !filters }) { T((if (filters) "▾ " else "▸ ") + s("Bộ lọc", "Filters"), 14, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
            Focusable({ text = ""; category = ""; conn = null; pcm = 0; dsd = ""; level = 0; status = null }) { T(s("Xoá lọc", "Clear"), 14, C.Muted, Modifier.padding(14.dp, 6.dp)) }
        }
        if (filters) {
            SectionLabel(s("LOẠI THIẾT BỊ", "CATEGORY"))
            Chip(s("Tất cả", "All"), category.isEmpty()) { category = "" }
            for (c in DeviceCatalog.categories(cat.devices)) Chip(catLabel(c), category == c) { category = c }
            SectionLabel(s("KẾT NỐI", "CONNECTION"))
            Chip(s("Tất cả", "All"), conn == null) { conn = null }
            for (c in DeviceCatalog.connections(cat.devices)) Chip(connLabel(c), conn == c) { conn = c }
            SectionLabel("PCM")
            for ((v, l) in listOf(0 to s("Bất kỳ", "Any"), 96000 to "≥ 96 kHz", 192000 to "≥ 192 kHz", 384000 to "≥ 384 kHz")) Chip(l, pcm == v) { pcm = v }
            SectionLabel("DSD")
            for ((v, l) in listOf("" to s("Bất kỳ", "Any"), "any" to s("Có DSD", "Has DSD"), "NATIVE" to "Native DSD", "DOP" to "DoP", "DSD_TO_PCM" to "DSD → PCM", "NONE" to s("Không DSD", "No DSD"))) Chip(l, dsd == v) { dsd = v }
            SectionLabel(s("MỨC XÁC MINH", "VERIFICATION LEVEL"))
            for ((v, l) in listOf(0 to s("Bất kỳ", "Any"), 2 to s("≥ 2 (khả năng)", "≥ 2 (capability)"), 3 to s("≥ 3 (cấu hình)", "≥ 3 (configuration)"), 4 to s("4 (đầu cuối)", "4 (end-to-end)"))) Chip(l, level == v) { level = v }
            SectionLabel(s("TRẠNG THÁI", "STATUS"))
            Chip(s("Tất cả", "All"), status == null) { status = null }
            for (st in Status.values()) Chip(statusLabel(st), status == st) { status = st }
        }
        T(res.size.toString() + s(" / ", " / ") + cat.devices.size + s(" thiết bị", " devices"), 13, C.Muted)
        if (cat.devices.isEmpty()) T(s("Không đọc được danh mục thiết bị.", "The device catalog could not be loaded."), 14, Grade.RED.color, lines = 3)
        for (d in res) {
            Focusable({ DeviceCatalogStore.detail.value = d.id }, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(d.label(), 16, C.Text, bold = true, lines = 2)
                    T(catLabel(d.category) + " · " + s("xem lần cuối ", "reviewed ") + d.lastReviewed, 12, C.Muted, lines = 2)
                    for (c in d.configs.take(4)) ConfigSummary(c)
                    if (d.configs.size > 4) T("+" + (d.configs.size - 4) + s(" kết nối khác", " more connections"), 12, C.Dim)
                }
            }
        }
        Focusable({ DeviceCatalogStore.open.value = false; DeviceCatalogStore.mine.value = true }) { T(s("Thiết bị âm thanh của tôi", "My Audio Devices"), 15, C.Text, Modifier.padding(24.dp, 8.dp)) }
        Focusable({ DeviceCatalogStore.open.value = false }) { T(s("Đóng", "Close"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
    }
    // Chi tiết nằm trên danh sách (không huỷ trạng thái tìm kiếm/lọc khi mở rồi đóng)
    if (device != null) DeviceDetailDialog(device)
}

@Composable
private fun DeviceDetailDialog(d: Device) {
    val first = remember(d) { FocusRequester() }
    LaunchedEffect(d) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ hộp thoại dựng xong, nếu không tiêu điểm D-pad vẫn ở trang bên dưới
    val ver by DeviceProfiles.version.collectAsState()
    val mineAll = remember(ver, d) { DeviceProfiles.store.verifications.filter { it.catalogId == d.id } }
    Frame(onDismiss = { DeviceCatalogStore.detail.value = null }) {
        // Nút quay lại ở ĐẦU trang: tiêu điểm D-pad bắt đầu từ trên cùng, không bị đẩy xuống cuối nội dung
        Focusable({ DeviceCatalogStore.detail.value = null }, requester = first) { T(s("← Về danh mục", "← Back to catalog"), 15, C.Gold, Modifier.padding(16.dp, 6.dp), bold = true) }
        T(d.label(), 22, serif = true, lines = 2)
        T(catLabel(d.category) + (if (d.mfrProductId.isNotEmpty()) " · " + d.mfrProductId else ""), 13, C.Muted, lines = 2)
        if (d.description.isNotEmpty()) T(d.description, 13, C.Text, lines = 4)
        if (d.aliases.isNotEmpty()) T(s("Tên khác: ", "Also known as: ") + d.aliases.joinToString(", "), 12, C.Muted, lines = 3)
        T(s("Xem xét lần cuối: ", "Last reviewed: ") + d.lastReviewed + " · " + s("phiên bản hồ sơ ", "profile v") + d.profileVersion, 12, C.Muted)
        T(s("Mỗi kết nối bên dưới là một cấu hình riêng. Mức xác minh của kết nối này KHÔNG áp dụng cho kết nối khác.", "Each connection below is a separate configuration. The verification of one connection does not apply to another."), 12, C.Gold, lines = 4)
        T(s("Nguồn xác minh (Manufacturer / User / Community) cho biết bằng chứng đến từ đâu; Độ chắc chắn kỹ thuật (Confirmed / Supported / Inconclusive / Unverified) cho biết bằng chứng mạnh đến đâu. Hai thuộc tính này độc lập: nguồn không quyết định độ chắc chắn.",
            "The verification source (Manufacturer / User / Community) says where the evidence comes from; the technical confidence (Confirmed / Supported / Inconclusive / Unverified) says how strong it is. They are independent: the source does not decide the confidence."), 11, C.Muted, lines = 8)
        for (c in d.configs) {
            T(connLabel(c.conn).uppercase() + " · " + c.protocol, 13, C.Muted, bold = true, lines = 3)
            T(statusMark(c.status) + statusLabel(c.status), 14, statusColor(c.status), bold = true, lines = 3)
            T(levelLabel(c.level), 13, C.Text, lines = 3)
            @Composable fun row(l: String, v: String) { if (v.isNotEmpty()) { T(l, 11, C.Muted, bold = true); T(v, 13, C.Text, lines = 8) } }
            row(s("Định dạng", "Formats"), c.formats.joinToString(", "))
            row(s("PCM tối đa (theo tài liệu)", "Max documented PCM"), pcmLabel(c))
            if (c.rates.isNotEmpty()) row(s("Tần số PCM", "PCM rates"), c.rates.sorted().joinToString(", ") { khz(it) })
            row("DSD", dsdLabel(c))
            row(s("Native / DoP / DSD→PCM", "Native / DoP / DSD→PCM"),
                s("Native: ", "Native: ") + (if (c.nativeDsd()) s("có", "yes") else s("không xác nhận", "not confirmed")) + " · DoP: " + (if (c.dop()) s("có", "yes") else s("không xác nhận", "not confirmed")) +
                    " · DSD→PCM: " + (if (c.dsd == DeviceCatalog.Dsd.DSD_TO_PCM) s("có", "yes") else s("không xác nhận", "not confirmed")))
            row(s("Bỏ qua DSP", "DSP bypass"), c.dspBypass); row(s("Xử lý âm lượng", "Volume processing"), c.volume); row("Resampling", c.resampling)
            row(s("Điều kiện để bit-perfect", "Conditions for bit-perfect"), c.conditions)
            row(s("Giới hạn đã biết", "Known limitations"), c.limitations)
            if (c.userConfirmationRequired) T(s("Cần người dùng xác nhận thiết bị; xác nhận danh tính không phải bằng chứng khả năng phát.", "The user must confirm the device; confirming identity is not playback-capability evidence."), 11, C.Dim, lines = 3)
            if (c.downgraded.isNotEmpty()) T(s("Mức đã bị hạ bởi chính sách bằng chứng: ", "Downgraded by the evidence policy: ") + c.downgraded, 11, C.Dim, lines = 3)

            // Ba nguồn xác minh, thứ tự cố định, luôn hiển thị đủ (không ghi đè nguồn nhau)
            SectionLabel(s("NGUỒN XÁC MINH CHO KẾT NỐI NÀY", "VERIFICATION SOURCES FOR THIS CONNECTION"))
            val mine = mineAll.filter { it.conn == c.conn }
            for (sx in Source.values()) {
                SourceBadge(sx)
                T(sourceExplain(sx), 11, C.Muted, lines = 3)
                when (sx) {
                    Source.COMMUNITY -> {
                        val rep = DeviceCatalogStore.community.filter { it.deviceId == d.id && it.conn == c.conn }
                        if (rep.isEmpty()) T(s("Chưa có báo cáo cộng đồng. Cần ≥3 báo cáo độc lập, nhất quán và có bằng chứng; báo cáo chưa kiểm chứng không được đưa vào danh mục.", "No community reports yet. At least 3 independent, consistent, evidenced reports are required; unverified reports are not added to the catalog."), 12, C.Dim, lines = 4)
                        for ((_, grp) in DeviceVerification.groupByCombo(rep)) {
                            val ag = DeviceVerification.aggregate(grp)
                            T(s("Báo cáo độc lập: ", "Independent reports: ") + ag.independent + " · PASS ${ag.pass} / FAIL ${ag.fail}" + (if (ag.duplicates > 0) " · " + s("trùng lặp ", "duplicates ") + ag.duplicates else "") + (if (ag.conflict) " · " + s("MÂU THUẪN", "CONFLICT") else ""), 12, C.Text, lines = 3)
                            T(s("Độ chắc chắn kỹ thuật: ", "Technical confidence: ") + confidenceName(ag.confidence) + if (ag.communityVerified()) "" else s(" (chưa đạt tiêu chí Community Verified)", " (does not yet meet Community Verified criteria)"), 12, C.Text, bold = true, lines = 3)
                        }
                    }
                    else -> {
                        val items = if (sx == Source.USER) c.evidence.filter { it.source == sx }.map { it to false } + mine.map { DeviceVerification.toEvidence(it) to true } else c.evidence.filter { it.source == sx }.map { it to false }
                        if (items.isEmpty()) T(s("Chưa có bằng chứng từ nguồn này.", "No evidence from this source yet."), 12, C.Dim, lines = 2)
                        for ((e, yours) in items) EvidenceRow(e, yours)
                    }
                }
            }
        }
        T(notice(), 11, C.Gold, lines = 6)
        T(s("Cấu hình nào không có trong danh sách trên là CHƯA được nghiên cứu/xác minh, không phải không hoạt động.", "A configuration not listed above has not been researched or verified; it is not necessarily unsupported."), 11, C.Dim, lines = 4)
        Focusable({ DeviceCatalogStore.detail.value = null }) { T(s("← Về danh mục", "← Back to catalog"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) = FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }

/** Verify Playback Capability: người dùng ghi nhận kết quả thử một cấu hình phát (khác hẳn "Confirm Device Identity"). Lưu cục bộ ⇒ User Verified. */
@Composable
private fun VerifyPlaybackDialog(p: DeviceRegistry.Profile, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ hộp thoại dựng xong, nếu không tiêu điểm D-pad vẫn ở trang bên dưới
    var dsd by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(DeviceCatalog.Dsd.NATIVE) }
    var rate by remember { mutableStateOf(0) }
    var bits by remember { mutableStateOf(0) }
    var fw by remember { mutableStateOf(p.firmware) }
    var sw by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf("") }
    var dspOff by remember { mutableStateOf(false) }
    var volFixed by remember { mutableStateOf(false) }
    var rsOff by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(DeviceVerification.TestMethod.LISTENING) }
    var result by remember { mutableStateOf(DeviceVerification.Result.INCONCLUSIVE) }
    var notes by remember { mutableStateOf("") }
    val v = DeviceVerification.UserVerification().also {
        it.profileKey = p.key; it.catalogId = p.catalogId; it.manufacturer = p.manufacturer; it.model = p.model; it.firmware = fw.trim(); it.protocol = proto.trim(); it.software = sw.trim(); it.notes = notes.trim()
        it.conn = p.conn; it.dsd = dsd; it.dsdMode = if (dsd) mode else DeviceCatalog.Dsd.UNDOCUMENTED; it.rate = if (dsd) 0 else rate; it.bits = if (dsd) 0 else bits
        it.result = result; it.method = method; it.dspOff = dspOff; it.volumeFixed = volFixed; it.resamplingOff = rsOff; it.at = System.currentTimeMillis()
    }
    val conf = DeviceVerification.userConfidence(v)
    Frame(onDismiss = onClose) {
        Focusable(onClose, requester = first) { T(s("← Huỷ", "← Cancel"), 15, C.Gold, Modifier.padding(16.dp, 6.dp), bold = true) }
        T("Verify Playback Capability", 22, serif = true)
        T(p.label().ifEmpty { p.name } + " · " + connLabel(p.conn), 15, C.Gold, bold = true, lines = 2)
        T(s("Ghi lại kết quả bạn đã thử NGHIỆM THỰC TẾ cho đúng kết nối trên. Đây là xác minh của người dùng (User Verified), lưu trên máy này, không thay đổi danh mục tích hợp. Xác nhận danh tính thiết bị không thay thế bước này.",
            "Record what you actually TESTED for the connection above. This is a user verification (User Verified), stored on this device only; it does not change the built-in catalog. Confirming the device identity does not replace this step."), 12, C.Muted, lines = 6)
        SectionLabel(s("ĐỊNH DẠNG ĐÃ THỬ", "TESTED FORMAT"))
        Chips { Chip("PCM", !dsd) { dsd = false }; Chip("DSD", dsd) { dsd = true } }
        if (dsd) {
            SectionLabel(s("KIỂU DSD", "DSD MODE"))
            Chips { Chip("Native DSD", mode == DeviceCatalog.Dsd.NATIVE) { mode = DeviceCatalog.Dsd.NATIVE }; Chip("DoP", mode == DeviceCatalog.Dsd.DOP) { mode = DeviceCatalog.Dsd.DOP }; Chip("DSD → PCM", mode == DeviceCatalog.Dsd.DSD_TO_PCM) { mode = DeviceCatalog.Dsd.DSD_TO_PCM } }
        } else {
            SectionLabel(s("TẦN SỐ LẤY MẪU", "SAMPLE RATE"))
            Chips { for (r in listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)) Chip(khz(r), rate == r) { rate = r } }
            SectionLabel(s("ĐỘ SÂU BIT", "BIT DEPTH"))
            Chips { for (b in listOf(16, 24, 32)) Chip("$b-bit", bits == b) { bits = b } }
        }
        SectionLabel(s("ĐIỀU KIỆN ĐÃ ÁP DỤNG KHI THỬ", "CONDITIONS APPLIED DURING THE TEST"))
        Chips {
            Chip(s("DSP/EQ tắt", "DSP/EQ off"), dspOff) { dspOff = !dspOff }
            Chip(s("Âm lượng cố định/tối đa", "Volume fixed/max"), volFixed) { volFixed = !volFixed }
            Chip(s("Không resampling", "No resampling"), rsOff) { rsOff = !rsOff }
        }
        SectionLabel(s("PHƯƠNG PHÁP KIỂM TRA", "TEST METHOD"))
        Chips {
            Chip(s("Chỉ nghe", "Listening only"), method == DeviceVerification.TestMethod.LISTENING) { method = DeviceVerification.TestMethod.LISTENING }
            Chip(s("Đèn/màn hình báo trên thiết bị", "Device indicator"), method == DeviceVerification.TestMethod.DEVICE_INDICATOR) { method = DeviceVerification.TestMethod.DEVICE_INDICATOR }
            Chip(s("Bắt luồng số + so sánh bit", "Digital capture + bit compare"), method == DeviceVerification.TestMethod.DIGITAL_CAPTURE) { method = DeviceVerification.TestMethod.DIGITAL_CAPTURE }
        }
        SectionLabel(s("KẾT QUẢ", "RESULT"))
        Chips {
            Chip(s("Dữ liệu không đổi (như đã thử)", "Data unchanged (as tested)"), result == DeviceVerification.Result.PASS) { result = DeviceVerification.Result.PASS }
            Chip(s("Dữ liệu bị đổi", "Data altered"), result == DeviceVerification.Result.FAIL) { result = DeviceVerification.Result.FAIL }
            Chip(s("Chưa kết luận", "Inconclusive"), result == DeviceVerification.Result.INCONCLUSIVE) { result = DeviceVerification.Result.INCONCLUSIVE }
        }
        DevField(s("Firmware thiết bị (nếu biết)", "Device firmware (if known)"), fw) { fw = it }
        DevField(s("Giao thức/chi tiết kết nối (vd. UAC2, eARC)", "Protocol/connection detail (e.g. UAC2, eARC)"), proto) { proto = it }
        DevField(s("Phần mềm/ứng dụng dùng để thử", "Software/app used for the test"), sw) { sw = it }
        DevField(s("Ghi chú và phương pháp (cần ≥10 ký tự để đạt Confirmed)", "Notes and method (≥10 characters needed for Confirmed)"), notes) { notes = it }
        T(s("Độ chắc chắn kỹ thuật dự kiến: ", "Expected technical confidence: ") + confidenceName(conf), 14, C.Text, bold = true, lines = 2)
        T(confidenceExplain(conf) + " " + s("Nghe bằng tai không chứng minh dữ liệu được bảo toàn; Confirmed cần bắt luồng số, so sánh bit, đủ điều kiện và mô tả.", "Listening alone does not prove data is preserved; Confirmed needs a digital capture with bit comparison, all conditions, and a description."), 11, C.Muted, lines = 6)
        val ready = conf != Confidence.UNVERIFIED
        if (!ready) T(s("Hãy chọn đủ định dạng (tần số/độ sâu bit hoặc kiểu DSD) trước khi lưu.", "Choose the full format (rate/bit depth or DSD mode) before saving."), 12, Grade.RED.color, lines = 3)
        else Focusable({ DeviceProfiles.addVerification(v, fw.trim()); onClose() }) { T(s("Lưu kết quả (User Verified)", "Save result (User Verified)"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
        Focusable(onClose) { T(s("Huỷ", "Cancel"), 15, C.Muted, Modifier.padding(24.dp, 8.dp)) }
    }
}

/** Thiết bị của tôi: thiết bị đang phát, thiết bị UPnP đã thấy, hồ sơ cục bộ. Không lẫn với danh mục toàn cục. */
@Composable
fun MyAudioDevicesDialog() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver by DeviceProfiles.version.collectAsState()
    val cat = DeviceCatalogStore.catalog
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ hộp thoại dựng xong, nếu không tiêu điểm D-pad vẫn ở trang bên dưới
    var seen by remember { mutableStateOf(Renderers.loadKnown(ctx).filter { it.kind == "upnp" }) }
    var scanning by remember { mutableStateOf(false) }
    var fixing by remember { mutableStateOf<String?>(null) }        // khoá hồ sơ đang sửa danh tính
    var verifying by remember { mutableStateOf<String?>(null) }     // khoá hồ sơ đang ghi nhận xác minh khả năng phát
    var query by remember { mutableStateOf("") }
    val queue by Playback.queue.collectAsState(); val idx by Playback.index.collectAsState(); val info by Playback.info.collectAsState(); val remote by Playback.remote.collectAsState()
    val active = remember(ver) { runCatching { BitPerfectProbe.detected(ctx) }.getOrNull() }
    val status = remember(queue, idx, info, remote, ver) { runCatching { BitPerfect.evaluate(BitPerfectProbe.input(queue.getOrNull(idx), info, remote)) }.getOrNull() }
    val profiles = remember(ver) { DeviceProfiles.store.profiles.toList() }

    @Composable fun Identify(det: DeviceRegistry.Detected, x: DeviceCatalog.Discovered, udn: String) {
        val m = DeviceCatalog.match(x, cat.devices)
        val saved = DeviceProfiles.store.find(DeviceRegistry.keyOf(det))
        if (m.device != null) {
            val dev = m.device!!
            T(s("Khớp danh mục: ", "Catalog match: ") + dev.label() + " · " + (if (m.quality >= DeviceCatalog.MODEL) s("khớp chắc chắn", "exact match") else s("khớp theo tên/bí danh — cần bạn xác nhận", "matched by name/alias — please confirm")), 12, C.Text, lines = 3)
            val cfgs = if (det.conn == Route.ROUTE_UNKNOWN) dev.configs else dev.configsFor(det.conn)
            if (cfgs.isEmpty()) T(s("Kết nối hiện tại (", "The current connection (") + connLabel(det.conn) + s(") CHƯA có trong danh mục cho thiết bị này.", ") is not covered by the catalog for this device."), 12, C.Muted, lines = 3)
            for (c in cfgs) ConfigSummary(c)
            T(s("Các kết nối khác của model này được xác minh riêng, không áp dụng cho kết nối đang dùng.", "Other connections of this model are verified separately and do not apply to the one in use."), 11, C.Dim, lines = 3)
            if (saved == null || saved.catalogId != dev.id)
                Focusable({ DeviceRegistry.linkCatalog(DeviceProfiles.store, det, udn, dev, System.currentTimeMillis()); DeviceProfiles.persist() }) { T(s("Confirm Device Identity — đúng thiết bị này", "Confirm Device Identity — yes, this device"), 14, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
        } else if (m.ambiguous) T(s("Có nhiều model trùng khớp (", "Several models match (") + m.candidates.joinToString(", ") { it.label() } + s("). Hãy chọn thủ công.", "). Please choose manually."), 12, C.Muted, lines = 4)
        else T(s("Chưa có trong danh mục. Bạn có thể chọn model thủ công; việc này chỉ xác nhận danh tính, không cấp xác minh.", "Not in the catalog. You can pick a model manually; this only confirms identity and grants no verification."), 12, C.Muted, lines = 3)
        Focusable({ fixing = DeviceRegistry.keyOf(det); pendingDet = det; pendingUdn = udn; query = det.name }) { T(s("Chọn / sửa model", "Choose / correct model"), 13, C.Text, Modifier.padding(14.dp, 6.dp)) }
    }

    Frame(onDismiss = { DeviceCatalogStore.mine.value = false }) {
        // Hàng nút ở ĐẦU trang: tiêu điểm bắt đầu từ trên cùng
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Focusable({ DeviceCatalogStore.mine.value = false }, requester = first) { T(s("← Đóng", "← Close"), 14, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
            Focusable({ DeviceCatalogStore.mine.value = false; DeviceCatalogStore.open.value = true }) { T("Bit-perfect Verified Devices", 14, C.Text, Modifier.padding(14.dp, 6.dp)) }
        }
        T(s("Thiết bị âm thanh của tôi", "My Audio Devices"), 22, serif = true)
        T(s("Chỉ gồm thiết bị trên máy này. Danh mục toàn cục nằm ở “Bit-perfect Verified Devices”. Hai việc khác nhau: Confirm Device Identity (đúng hãng/model) và Verify Playback Capability (kết quả bạn đã thử). Xác nhận danh tính không phải bằng chứng bit-perfect.",
            "Only devices on this device. The global catalog is “Bit-perfect Verified Devices”. Two different actions: Confirm Device Identity (right make/model) and Verify Playback Capability (the result you tested). Confirming identity is not bit-perfect evidence."), 12, C.Muted, lines = 7)

        T(notice(), 11, C.Gold, lines = 6)
        SectionLabel(s("ĐẦU RA ĐANG DÙNG", "ACTIVE OUTPUT"))
        if (active == null) T(s("Không xác định được thiết bị đầu ra.", "The output device cannot be identified."), 13, C.Muted, lines = 2)
        else {
            T(active.name.ifEmpty { "—" } + " · " + connLabel(active.conn), 15, C.Gold, bold = true, lines = 2)
            if (!active.activeKnown) T(s("Android không xác nhận thiết bị này đang thực sự nhận âm thanh.", "Android does not confirm that this device is actually receiving the audio."), 11, C.Muted, lines = 3)
            val x = DeviceCatalog.Discovered().also { it.friendlyName = active.name; it.modelName = active.name; it.vid = active.vid; it.pid = active.pid; it.conn = active.conn
                it.manufacturer = active.manufacturer }
            Identify(active, x, "")
        }
        val st = status
        T(s("Trạng thái Bit-perfect của phiên phát hiện tại (tách biệt với khả năng thiết bị): ", "Bit-perfect status of the current playback session (independent of device capability): ") + (if (st == null) "—" else statusText(st.status, st.level)), 13, C.Text, lines = 4)
        Focusable({ DeviceCatalogStore.mine.value = false; BitPerfectUi.open.value = true }) { T(s("Xem chi tiết trạng thái phát", "View playback status details"), 13, C.Text, Modifier.padding(14.dp, 6.dp)) }

        SectionLabel(s("THIẾT BỊ UPnP ĐÃ THẤY", "UPnP DEVICES SEEN"))
        if (seen.isEmpty()) T(s("Chưa thấy thiết bị UPnP nào. Bấm “Quét” để tìm.", "No UPnP device seen yet. Tap “Scan”."), 13, C.Muted, lines = 2)
        for (r in seen) {
            T(r.name + " · UPnP/DLNA", 14, C.Text, bold = true, lines = 2)
            if (r.maker.isNotEmpty() || r.model.isNotEmpty()) T(s("Thiết bị tự khai: ", "Self-reported: ") + (r.maker + " " + r.model).trim(), 12, C.Muted, lines = 2)
            val det = DeviceRegistry.Detected(r.name, Route.UPNP, -1, -1, false)
            val x = DeviceCatalog.Discovered(r.maker, r.model, r.name, Route.UPNP).also { it.modelDescription = r.modelDesc; it.udn = r.id }
            Identify(det, x, r.id)
        }
        Focusable({ if (!scanning) { scanning = true; scope.launch { runCatching { Renderers.discover(ctx) }; seen = Renderers.loadKnown(ctx).filter { it.kind == "upnp" }; scanning = false } } }) {
            T(if (scanning) s("Đang quét…", "Scanning…") else s("Quét thiết bị UPnP", "Scan UPnP devices"), 14, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true)
        }

        SectionLabel(s("HỒ SƠ ĐÃ LƯU TRÊN MÁY", "PROFILES SAVED ON THIS DEVICE"))
        if (profiles.isEmpty()) T(s("Chưa có hồ sơ nào.", "No saved profiles."), 13, C.Muted)
        for (p in profiles) {
            val dev = if (p.catalogId.isEmpty()) null else cat.byId(p.catalogId)
            val mine = DeviceProfiles.store.verificationsFor(p.key)
            T(p.label().ifEmpty { p.name }, 14, C.Text, bold = true, lines = 2)
            T(s("Danh tính đã xác nhận · kết nối: ", "Identity confirmed · connection: ") + connLabel(p.conn) + " · " + (if (p.source == DeviceRegistry.SRC_USER) s("bạn khai báo", "declared by you") else s("bạn xác nhận", "confirmed by you")) +
                (if (p.firmware.isNotEmpty()) " · firmware " + p.firmware else ""), 12, C.Muted, lines = 3)
            if (dev != null) {
                val cfgs = dev.configsFor(p.conn)
                if (cfgs.isEmpty()) T(s("Danh mục chưa có cấu hình cho kết nối này.", "The catalog has no configuration for this connection."), 12, C.Muted, lines = 2)
                for (c in cfgs) ConfigSummary(c, mine.map { DeviceVerification.toEvidence(it) })
                Focusable({ DeviceCatalogStore.mine.value = false; DeviceCatalogStore.detail.value = dev.id; DeviceCatalogStore.open.value = true }) { T(s("Xem chi tiết và bằng chứng trong danh mục", "Open details and evidence in the catalog"), 13, C.Text, Modifier.padding(14.dp, 6.dp)) }
            } else {
                T(s("Chưa liên kết danh mục — không có bằng chứng từ nhà sản xuất.", "Not linked to the catalog — no manufacturer evidence."), 12, C.Muted, lines = 2)
                if (mine.isNotEmpty()) FlowRowOne { SourceBadge(Source.USER) }
            }
            for (v in mine) {
                val e = DeviceVerification.toEvidence(v)
                EvidenceRow(e, true)
                Focusable({ DeviceProfiles.removeVerification(v) }) { T(s("Xoá kết quả này", "Remove this result"), 12, C.Muted, Modifier.padding(14.dp, 4.dp)) }
            }
            Focusable({ verifying = p.key }) { T("Verify Playback Capability", 13, C.Gold, Modifier.padding(14.dp, 6.dp), bold = true) }
            Focusable({ val det = DeviceRegistry.Detected(p.name, p.conn, p.vid, p.pid, false); fixing = p.key; pendingDet = det; pendingUdn = p.udn; query = p.label().ifEmpty { p.name } }) { T(s("Sửa danh tính thiết bị", "Correct device identity"), 13, C.Text, Modifier.padding(14.dp, 6.dp)) }
            Focusable({ DeviceProfiles.remove(p.key) }) { T(s("Xoá khỏi hồ sơ cá nhân (kèm kết quả đã thử)", "Remove from my profile (with its test results)"), 13, C.Muted, Modifier.padding(14.dp, 6.dp)) }
        }

        if (fixing != null) {
            SectionLabel(s("CHỌN MODEL TRONG DANH MỤC", "PICK A CATALOG MODEL"))
            DevField(s("Tìm hãng / model", "Search make / model"), query) { query = it }
            val found = remember(query, cat) { DeviceCatalog.search(cat.devices, DeviceCatalog.Query().also { it.text = query }).take(8) }
            for (dv in found) Focusable({
                val det = pendingDet
                if (det != null) { DeviceRegistry.linkCatalog(DeviceProfiles.store, det, pendingUdn, dv, System.currentTimeMillis()); DeviceProfiles.persist() }
                fixing = null
            }) { T(dv.label() + " · " + catLabel(dv.category), 14, C.Text, Modifier.padding(14.dp, 6.dp), lines = 2) }
            Focusable({ val det = pendingDet; if (det != null) { DeviceRegistry.linkCatalog(DeviceProfiles.store, det, pendingUdn, null, System.currentTimeMillis()); DeviceProfiles.persist() }; fixing = null }) { T(s("Gỡ liên kết danh mục", "Remove catalog link"), 13, C.Muted, Modifier.padding(14.dp, 6.dp)) }
            Focusable({ fixing = null }) { T(s("Huỷ", "Cancel"), 13, C.Muted, Modifier.padding(14.dp, 6.dp)) }
        }
        Focusable({ DeviceCatalogStore.mine.value = false }) { T(s("Đóng", "Close"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
    }
    verifying?.let { k -> profiles.firstOrNull { it.key == k }?.let { VerifyPlaybackDialog(it) { verifying = null } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOne(content: @Composable () -> Unit) = FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

// trạng thái sửa danh tính (một hộp thoại tại một thời điểm)
private var pendingDet: DeviceRegistry.Detected? = null
private var pendingUdn: String = ""
