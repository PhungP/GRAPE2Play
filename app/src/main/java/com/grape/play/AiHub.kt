package com.grape.play

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Cầu nối với điện thoại: TV mở một máy chủ HTTP tạm trong mạng LAN (chỉ khi hộp thoại đang mở, kèm mã ngẫu nhiên trong địa chỉ).
 * Người dùng quét mã QR bằng điện thoại để dán khóa API hoặc gõ/nói câu hỏi — không cần gõ chữ trên TV.
 */
class AiPhoneLink(private val prov: Prov?, private val onKey: ((String) -> Unit)?, private val onAsk: ((String) -> Unit)?) {
    private val token = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
    private var server: PairServer? = null
    var url: String? = null; private set

    fun start(): Boolean {
        if (server != null) return true
        val ip = CloudAuth.lanIp() ?: return false
        for (p in 8790..8799) {
            val s = PairServer(p) { m, path, body -> handle(m, path, body) }
            if (runCatching { s.start() }.isSuccess) { server = s; url = "http://$ip:$p/?t=$token"; return true }
        }
        return false
    }
    fun stop() { server?.stop(); server = null; url = null }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun form(body: String): Map<String, String> = body.split('&').filter { it.isNotEmpty() }.associate {
        val kv = it.split('=', limit = 2)
        URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
    }
    private fun page(msg: String, inner: String) = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>GRAPE2Play AI</title></head>" +
        "<body style='font-family:sans-serif;padding:18px;max-width:640px;margin:auto;line-height:1.5'><h2>GRAPE2Play · AI</h2>" +
        (if (msg.isNotEmpty()) "<p style='background:#e6f4ea;padding:10px;border-radius:8px'><b>${esc(msg)}</b></p>" else "") + inner + "</body></html>"

    private fun handle(method: String, path: String, body: String): Pair<Int, String> {
        val q = path.substringAfter('?', "")
        val t = q.split('&').firstOrNull { it.startsWith("t=") }?.substring(2)
        if (t != token) return 403 to page("", "<p>Liên kết đã hết hạn. Hãy mở lại hộp thoại AI trên TV và quét lại mã QR. / Link expired — scan the QR on the TV again.</p>")
        val route = path.substringBefore('?')
        val tq = "?t=$token"
        if (method == "POST" && route == "/key") {
            val f = form(body)
            val k = if (prov?.twoPart == true) f["a"].orEmpty().trim() + ":" + f["k"].orEmpty().trim() else f["k"].orEmpty().trim()
            if (k.length < 20) return 200 to page("", "<p style='color:#b00020'><b>Khóa quá ngắn — hãy dán đúng toàn bộ khóa.</b></p><p><a href='/$tq'>← Quay lại</a></p>")
            onKey?.invoke(k); return 200 to page("Đã gửi khóa tới TV ✓ (xem kết quả kiểm tra trên TV)", "<p><a href='/$tq'>← Quay lại</a></p>")
        }
        if (method == "POST" && route == "/ask") {
            val a = form(body)["q"].orEmpty().trim()
            if (a.isEmpty()) return 200 to page("", "<p><a href='/$tq'>← Quay lại</a></p>")
            onAsk?.invoke(a); return 200 to page("Đã gửi tới TV ✓", "<p>Câu hỏi: <i>${esc(a)}</i></p><p><a href='/$tq'>Gửi câu khác →</a></p>")
        }
        val sb = StringBuilder()
        if (onAsk != null) sb.append("<h3>Hỏi / tạo playlist</h3><p style='color:#555'>Gõ hoặc bấm micro trên bàn phím điện thoại để nói. Ví dụ: <i>nhạc Trịnh nghe lúc mưa khoảng 1 giờ</i>, <i>các album DSD hay nghe nhất</i>.</p>" +
            "<form method=post action='/ask$tq'><textarea name=q rows=3 style='width:100%;font-size:18px' placeholder='Bạn muốn nghe gì?'></textarea><p><button style='font-size:18px;padding:10px 24px'>Gửi tới TV</button></p></form>")
        if (onKey != null && prov != null) sb.append("<h3>Khóa API ${esc(prov.name)}</h3><p style='color:#555'>Trạng thái: <b>${if (Ai.has(prov.id)) "đã có khóa" else "chưa có khóa"}</b>. Lấy khóa miễn phí tại " +
            "<a target=_blank rel=noopener href='${prov.keyUrl}'>${esc(prov.keyUrl.removePrefix("https://"))}</a> rồi dán vào ô dưới.</p>" +
            "<form method=post action='/key$tq'>" + (if (prov.twoPart) "<p>Account ID (dán từ trang Cloudflare):<br><input name=a autocomplete=off style='width:100%;font-size:16px;padding:6px' placeholder='32 ký tự'></p><p>API token (quyền Workers AI):<br>" else "") +
            "<input name=k autocomplete=off style='width:100%;font-size:16px;padding:6px' placeholder='${esc(if (prov.twoPart) "API token" else prov.keyHint)}'>" + (if (prov.twoPart) "</p>" else "<p>") + "<button style='font-size:18px;padding:10px 24px'>Lưu khóa vào TV</button></p></form>")
        val inner = sb.toString()
        return 200 to page("", inner)
    }
}

private val ui = Handler(Looper.getMainLooper())

@Composable
private fun AiFrame(title: String, back: String?, onBack: () -> Unit, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { first.requestFocus() } }
    val maxH = if (Device.tv) 520.dp else (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = { if (back != null) onBack() else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val gl by Ai.glass
        val bl by Ai.blur
        val view = LocalView.current
        SideEffect {
            val w = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            w.setDimAmount(if (GlassTheme.on) 0.08f else if (gl < 100) 0.15f else 0.5f)
            if (Build.VERSION.SDK_INT >= 31) {
                if (bl > 0) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = w.attributes; lp.blurBehindRadius = intArrayOf(0, 16, 32, 56)[bl.coerceIn(0, 3)]; w.attributes = lp
                } else w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        val al = gl / 100f
        val base = Modifier.dlgW(560).heightIn(max = maxH)
        val surf = when (Themes.style.value) {
            GlassTheme.ID -> if (Ai.dark.value) base.clip(RoundedCornerShape(30.dp)).background(Color(0xFF141226).copy(alpha = al * 0.8f)).border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x4DFFFFFF))), RoundedCornerShape(30.dp)) else base.clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = 0.16f)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f + (al - 0.7f) * 1.07f), Color.White.copy(alpha = (0.18f + (al - 0.7f) * 1.07f) * 0.5f)))).border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x4DFFFFFF))), RoundedCornerShape(30.dp))
            5, 6, 7, 8, 9, 10 -> base.clip(RoundedCornerShape(12.dp)).background(C.Bg.copy(alpha = al)).sfPanel(12.dp)
            4 -> base.clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Panel.copy(alpha = al), C.Side.copy(alpha = al)))).border(1.dp, C.Line, RoundedCornerShape(4.dp))
            1 -> base.clip(RoundedCornerShape(22.dp)).background(C.Panel.copy(alpha = al))
            else -> base.clip(RoundedCornerShape(12.dp)).background(C.Panel.copy(alpha = al)).border(1.dp, C.Line, RoundedCornerShape(12.dp))
        }
        Column(surf.padding(18.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (back != null) Focusable(onBack, requester = first) { T("◂ $back", 14, C.Gold, Modifier.padding(12.dp, 5.dp)) }
                T(Lang.tr(title).uppercase(), 13, C.Muted, spaced = true, modifier = Modifier.weight(1f))
                Focusable(onClose, requester = if (back == null) first else null) { T("Đóng", 14, C.Muted, Modifier.padding(12.dp, 5.dp)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            val st by Ai.status
            if (st.isNotEmpty()) T(st, 12, if (st.startsWith("↪")) C.Gold else if (st.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Muted, lines = 3)
        }
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Focusable(onClick, modifier.fillMaxWidth(), RoundedCornerShape(8.dp)) { T(text, 15, C.Gold, Modifier.padding(12.dp, 7.dp), bold = true, lines = 2) }

@Composable
fun AiHubDialog(onClose: () -> Unit, onPlayed: () -> Unit) {
    val hasKey by Ai.hasKey
    val rawTracks by Library.tracks.collectAsState()
    val hidden by SourceVisibility.hidden
    val lib = remember(rawTracks, hidden) { if (hidden.isEmpty()) rawTracks else rawTracks.filter { it.src !in hidden } }
    var page by remember { mutableStateOf(if (!Ai.hasKey.value) "key" else Overlay.aiStart) }
    fun close() { Overlay.aiStart = "menu"; onClose() }
    fun play(l: List<Track>) { if (l.isNotEmpty()) { Playback.play(l, 0, false); onPlayed(); close() } }
    fun playAt(l: List<Track>, i: Int) { if (l.isNotEmpty()) { Playback.play(l, i.coerceIn(0, l.lastIndex), false); onPlayed(); close() } }
    val goMenu = { page = "menu" }
    val wantMic by Overlay.aiListen
    LaunchedEffect(wantMic) { if (wantMic && Ai.hasKey.value) page = "ask" }
    when {
        page == "key" -> AiFrame("Dịch vụ AI & khóa API", "Menu", goMenu, ::close) { ServicesPage { page = "key:$it" } }
        page.startsWith("key:") -> AiFrame("Khóa " + AiProvs.get(page.removePrefix("key:")).name, "Dịch vụ", { page = "key" }, ::close) { KeyPage(AiProvs.get(page.removePrefix("key:"))) { page = "key" } }
        page == "sleep" -> AiFrame("Hẹn giờ ngủ", "Menu", goMenu, ::close) { SleepPage(lib, ::play) }
        page == "saver" -> AiFrame("Màn hình chờ", "Menu", goMenu, ::close) { SaverPage { close(); AiSaver.showing.value = true } }
        page == "testset" -> AiFrame("Buổi thử dàn", "Menu", goMenu, ::close) { TestPage(lib, ::playAt) }
        page == "quick" -> AiFrame("Tìm nhanh", "Menu", goMenu, ::close) { QuickPage(rawTracks, ::playAt) { Overlay.aiAsk = it; page = "ask" } }
        page == "ask" -> AiFrame("Hỏi / tạo playlist", "Menu", goMenu, ::close) { AskPage(lib, ::play) }
        page == "plist" -> AiFrame("Playlist đã lưu", "Menu", goMenu, ::close) { SavedListsPage(lib, ::play) }
        page == "tags" -> AiFrame("Sửa tag, gộp trùng", "Menu", goMenu, ::close) { TagsPage(lib) }
        page == "forgot" -> AiFrame("Album bị bỏ quên", "Menu", goMenu, ::close) { ForgottenPage(lib, ::play) }
        page == "lyrics" -> AiFrame("Lời bài hát", "Menu", goMenu, ::close) { LyricsPage() }
        page == "suggest" -> AiFrame("Gợi ý theo bối cảnh", "Menu", goMenu, ::close) { SuggestPage(lib, ::play) }
        page == "info" -> AiFrame("Giới thiệu bài đang phát", "Menu", goMenu, ::close) { InfoPage() }
        page == "clean" -> AiFrame("Làm sạch thư viện", "Menu", goMenu, ::close) { CleanPage(lib) }
        else -> AiFrame("✨ Trợ lý AI (Gemini)", null, goMenu, ::close) {
            T("AI chỉ chạy khi bạn bấm. Thư viện không bị gửi lên mạng — chỉ gửi câu hỏi, vài tên nghệ sĩ/album hoặc tên thư mục cần xử lý.", 13, C.Muted, lines = 3)
            val dj by AiDj.on
            Btn("⚡  Tìm nhanh bài hát / thư mục trong mọi nguồn") { page = "quick" }
            Btn("🔎  Hỏi AI / tạo playlist bằng lời nói hoặc chữ") { page = "ask" }
            Btn("📂  Playlist đã lưu (${UserPlaylists.list.size})") { page = "plist" }
            Btn(if (dj) "📻  AI DJ: ĐANG BẬT — bấm để tắt" else "📻  AI DJ: tự thêm bài tiếp theo hợp mạch (đang tắt)") { AiDj.set(!dj) }
            if (dj && AiDj.note.value.isNotEmpty()) T("DJ: " + AiDj.note.value, 13, C.Muted, lines = 2)
            Btn("🌤  Gợi ý theo thời tiết, lễ hội, tâm trạng") { page = "suggest" }
            Btn("💎  Album bị bỏ quên — đáng nghe lại hôm nay") { page = "forgot" }
            Btn("🎚  Buổi thử dàn — AI chọn bài thử âm từ thư viện") { page = "testset" }
            Btn("😴  Hẹn giờ ngủ thông minh" + if (SleepTimer.active.value) " — còn ${fmtMs(SleepTimer.remainingMs.value)}" else "") { page = "sleep" }
            Btn("🌙  Màn hình chờ thông minh: " + if (AiSaver.on.value) "BẬT (${AiSaver.minutes.value} phút)" else "TẮT") { page = "saver" }
            Btn("ℹ  Giới thiệu bài / album / nghệ sĩ đang phát") { page = "info" }
            Btn("📝  Lời bài hát: xem, dịch, giải nghĩa") { page = "lyrics" }
            Btn("🏷  Sửa tag, gộp tên nghệ sĩ/album bị trùng") { page = "tags" }
            Btn("🧹  Làm sạch tên và thể loại thư mục") { page = "clean" }
            Btn("🔑  Dịch vụ AI & khóa API (Gemini, Groq, OpenRouter, Cloudflare, NVIDIA)") { page = "key" }
        }
    }
}

@Composable
private fun ServicesPage(open: (String) -> Unit) {
    val rev by Ai.rev
    var auto by remember { mutableStateOf(Ai.auto) }
    val prim = remember(rev) { Ai.primary }
    T("Mỗi dịch vụ đều có gói miễn phí (hạn mức khác nhau). Chọn một dịch vụ để thêm khóa; có thể dùng nhiều dịch vụ cùng lúc.", 13, C.Muted, lines = 3)
    Focusable({ auto = !auto; Ai.auto = auto }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), selected = auto) {
        Column(Modifier.padding(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T((if (auto) "● " else "○ ") + "Tự chuyển AI: " + (if (auto) "BẬT" else "TẮT"), 16, C.Gold, bold = true)
            T("Khi AI chính hết hạn mức hoặc không phản hồi sau ${Ai.SWITCH_MS / 1000} giây, tự thử AI kế tiếp và ghi chú dịch vụ nào đang trả lời.", 12, C.Muted, lines = 3)
        }
    }
    val gl by Ai.glass
    val bl by Ai.blur
    T("Độ đặc cửa sổ AI:", 13, C.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(100 to "Đặc 100%", 85 to "Trong 85%", 70 to "Trong 70%").forEach { (v, n) ->
            Focusable({ Ai.setGlass(v) }, Modifier, RoundedCornerShape(8.dp), selected = gl == v) { T(n, 14, if (gl == v) C.Gold else C.Text, Modifier.padding(12.dp, 6.dp)) }
        }
    }
    val dk by Ai.dark
    T("Màu kính (giao diện Liquid Glass):", 13, C.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false to "Kính trắng", true to "Kính tối").forEach { (v, n) ->
            Focusable({ Ai.setDark(v) }, Modifier, RoundedCornerShape(8.dp), selected = dk == v) { T(n, 14, if (dk == v) C.Gold else C.Text, Modifier.padding(12.dp, 6.dp)) }
        }
    }
    T("Làm mờ nền phía sau" + (if (Build.VERSION.SDK_INT < 31) " (cần Android 12 trở lên — máy này chưa hỗ trợ)" else "") + ":", 13, C.Muted, lines = 2)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Tắt", "Nhẹ", "Vừa", "Mạnh").forEachIndexed { v, n ->
            Focusable({ Ai.setBlur(v) }, Modifier, RoundedCornerShape(8.dp), selected = bl == v) { T(n, 14, if (bl == v) C.Gold else C.Text, Modifier.padding(12.dp, 6.dp)) }
        }
    }
    AiProvs.ALL.forEach { p ->
        val has = Ai.has(p.id)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Focusable({ open(p.id) }, Modifier.weight(1f), RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp, 8.dp)) {
                    T(p.name + if (has && p.id == prim) "  ★ chính" else "", 16, C.Gold, bold = true)
                    T(if (has) "✓ Đã có khóa — bấm để đổi / kiểm tra / xóa" else "Chưa có khóa — bấm để thêm", 12, C.Muted)
                }
            }
            if (has && p.id != prim) Focusable({ Ai.setPrimary(p.id) }) { T("★ Làm chính", 13, C.Gold, Modifier.padding(12.dp, 8.dp)) }
        }
    }
    T("Thứ tự dự phòng: AI chính, rồi lần lượt các dịch vụ còn lại theo danh sách trên.", 12, C.Muted, lines = 2)
}

@Composable
private fun KeyPage(prov: Prov, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val rev by Ai.rev
    val has = remember(rev) { Ai.has(prov.id) }
    fun check() {
        if (busy) return
        busy = true; status = "Đang kiểm tra kết nối tới ${prov.name}…"
        scope.launch {
            val t0 = System.currentTimeMillis()
            try { Ai.test(prov.id); status = "Khóa hợp lệ ✓"; onDone() }
            catch (e: Exception) { status = "✗ Kiểm tra thất bại sau ${(System.currentTimeMillis() - t0) / 1000}s: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }
    fun accept(k: String) { Ai.setKey(prov.id, k); check() }
    val link = remember { AiPhoneLink(prov, { k -> ui.post { accept(k) } }, null) }
    LaunchedEffect(Unit) { if (Device.tv) { if (link.start()) url = link.url else status = "Không lấy được địa chỉ mạng — hãy dán khóa trực tiếp ở ô bên dưới." } }
    DisposableEffect(Unit) { onDispose { link.stop() } }
    T(if (has) "Đã có khóa ${prov.name}. Có thể thay bằng khóa khác." else "Cần khóa API ${prov.name} (miễn phí): ${prov.keyUrl.removePrefix("https://")}", 14, C.Muted, lines = 2)
    val u = url
    if (!Device.tv) Btn("🌐 Mở trang tạo khóa của ${prov.name}") {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(prov.keyUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { status = "Không mở được trình duyệt — hãy tự vào ${prov.keyUrl.removePrefix("https://")}" }
    }
    if (u != null && Device.tv) Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("① Tạo khóa", 15, C.Gold, bold = true); QrCode(prov.keyUrl, 140.dp); T("Trang ${prov.name}", 12, C.Muted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("② Gửi khóa về TV", 15, C.Gold, bold = true); QrCode(u, 140.dp); T("Trang của TV", 12, C.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Không cần gõ khóa trên TV (điện thoại cùng Wi-Fi):", 15, C.Gold, bold = true)
            T("① Quét mã ①: đăng nhập, tạo API key, sao chép khóa.", 14, lines = 3)
            T("② Quét mã ②: dán khóa → Lưu khóa vào TV.", 14, lines = 2)
            T(u, 12, C.Muted, lines = 2)
        }
    }
    if (prov.twoPart) T("Cloudflare cần 2 giá trị: Account ID (hiện trong địa chỉ trang Cloudflare Workers AI) và API token (tạo bằng mẫu “Workers AI”). Gõ/dán gộp dạng ACCOUNT_ID:API_TOKEN, hoặc dùng điện thoại quét mã ② để nhập hai ô riêng.", 13, C.Muted, lines = 4)
    T(if (Device.tv) "Hoặc dán trực tiếp (bàn phím rời):" else "Dán khóa vào ô dưới:", 14, C.Muted)
    OutlinedTextField(manual, { manual = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 16.sp), modifier = Modifier.fillMaxWidth(),
        placeholder = { if (prov.keyHint.isNotEmpty()) T(prov.keyHint, 14, C.Muted) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Focusable({ if (manual.isNotBlank()) accept(manual.trim()) }) { T("Lưu khóa", 15, C.Gold, Modifier.padding(14.dp, 8.dp), bold = true) }
        Focusable({
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val s = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (s.length >= 20 && (!prov.twoPart || ":" in s)) accept(s) else status = if (prov.twoPart) "Bộ nhớ tạm cần có dạng ACCOUNT_ID:API_TOKEN" else "Bộ nhớ tạm không có khóa"
        }) { T("Dán từ bộ nhớ tạm", 15, C.Gold, Modifier.padding(14.dp, 8.dp)) }
        if (has) Focusable({ check() }) { T("Kiểm tra", 15, C.Gold, Modifier.padding(14.dp, 8.dp)) }
        if (has) Focusable({ Ai.setKey(prov.id, ""); status = "Đã xóa khóa" }) { T("Xóa khóa", 15, androidx.compose.ui.graphics.Color(0xFFE5575A), Modifier.padding(14.dp, 8.dp)) }
    }
    if (has && Ai.primary != prov.id) Btn("★ Đặt ${prov.name} làm AI chính") { Ai.setPrimary(prov.id); status = "Đã đặt làm AI chính ✓" }
    var diag by remember { mutableStateOf("") }
    Focusable({
        if (!busy) { busy = true; diag = "Đang đo từng bước, có thể mất tới 1 phút…"; scope.launch { try { diag = Ai.diagnose(prov) } catch (e: Exception) { diag = "Lỗi: ${e.message}" } finally { busy = false } } }
    }) { T("🩺 Chẩn đoán mạng", 15, C.Gold, Modifier.padding(14.dp, 8.dp)) }
    if (diag.isNotEmpty()) T(diag, 13, C.Text, lines = 10)
    if (status.isNotEmpty()) T(status, 14, if (status.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    T("Khóa lưu riêng trong app, trên thiết bị này. Máy chủ tạm chỉ chạy khi hộp thoại này mở.", 12, C.Muted, lines = 2)
}

/**
 * Ô nhập chung. Điện thoại/máy tính bảng: có ô gõ chữ. TV: KHÔNG có ô gõ (gõ trên TV không khả thi) — chỉ có 🎤 nói và 📱 nhập bằng điện thoại.
 * [onSubmit] được gọi khi người dùng bấm Tìm (nếu [showSubmit]) hoặc khi giọng nói / điện thoại gửi chữ tới.
 */
@Composable
private fun AiInput(q: String, onQ: (String) -> Unit, hint: String, showSubmit: Boolean, autoMic: Boolean, onSubmit: (String) -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    val listening by Voice.listening
    val partial by Voice.partial
    fun got(t: String) { onQ(t); onSubmit(t) }
    fun listen() {
        if (Voice.listening.value) { Voice.stop(); return }
        if (!Voice.available(ctx)) { msg = "Thiết bị này chưa có dịch vụ nhận giọng nói (cần app Google / Google Assistant). Dùng nút 📱 để nói bằng điện thoại."; return }
        msg = "🎙 Đang nghe… hãy nói"
        Voice.start(ctx, { t -> ui.post { msg = ""; got(t) } }, { e -> ui.post { msg = e } })
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else msg = "Bạn chưa cho phép micro. Vào Cài đặt hệ thống → Ứng dụng → GRAPE2Play → Quyền để bật, hoặc dùng điện thoại để nhập."
    }
    fun startMic() { if (Voice.hasPermission(ctx)) listen() else micPerm.launch(android.Manifest.permission.RECORD_AUDIO) }
    if (autoMic) {
        val wantMic by Overlay.aiListen
        LaunchedEffect(wantMic) { if (wantMic) { Overlay.aiListen.value = false; startMic() } }
    }
    DisposableEffect(Unit) { onDispose { Voice.stop() } }
    val link = remember { AiPhoneLink(null, null) { s -> ui.post { got(s) } } }
    DisposableEffect(Unit) { onDispose { link.stop() } }
    if (!Device.tv) OutlinedTextField(q, onQ, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 18.sp), modifier = Modifier.fillMaxWidth(), placeholder = { T(hint, 16, C.Muted) })
    else if (q.isNotBlank()) T("“$q”", 18, C.Gold, lines = 3)
    val typing by Ai.typing
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!Device.tv && showSubmit) Focusable({ onSubmit(q) }) { T("Tìm", 17, C.Gold, Modifier.padding(18.dp, 9.dp), bold = true) }
        Focusable({ startMic() }) { T(if (listening) "■ Dừng nghe" else "🎤 Nói", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
        if (Device.tv) Focusable({ if (link.start()) url = link.url else msg = "Không lấy được địa chỉ mạng." }) { T("📱 Nhập bằng điện thoại", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
        if (Device.tv) Focusable({ Ai.setTyping(!typing) }, selected = typing) { T(if (typing) "⌨ Ẩn ô gõ" else "⌨ Hiện ô gõ", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
    }
    val u = url
    if (u != null) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        QrCode(u, 140.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Quét mã bằng điện thoại, gõ hoặc nói rồi bấm Gửi — kết quả hiện ở đây.", 14, lines = 3)
            T(u, 12, C.Muted, lines = 2)
        }
    }
    if (listening && partial.isNotEmpty()) T("“$partial”", 17, lines = 3)
    if (msg.isNotEmpty()) T(msg, 14, C.Gold, lines = 4)
}

/** TV: ô gõ chữ đặt ở cuối hộp thoại, chỉ hiện khi bật bằng nút ⌨ để không vướng khi di chuyển bằng điều khiển. */
@Composable
private fun AiTyping(q: String, onQ: (String) -> Unit, hint: String, showSubmit: Boolean, onSubmit: (String) -> Unit) {
    val typing by Ai.typing
    if (!Device.tv || !typing) return
    OutlinedTextField(q, onQ, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 18.sp), modifier = Modifier.fillMaxWidth(), placeholder = { T(hint, 16, C.Muted) })
    if (showSubmit) Focusable({ onSubmit(q) }) { T("Tìm", 17, C.Gold, Modifier.padding(18.dp, 9.dp), bold = true) }
}

@Composable
private fun AskPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(Overlay.aiAsk) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<AiSearch.Result?>(null) }
    var saved by remember { mutableStateOf(false) }
    fun run(text: String) {
        if (busy || text.isBlank()) return
        busy = true; msg = "AI đang quét thư viện và đối chiếu…"; res = null; saved = false
        scope.launch {
            try { res = AiSearch.run(text, lib); msg = if (res!!.tracks.isEmpty()) "Không tìm thấy bài phù hợp — thử nói khác đi." else "" }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { if (Overlay.aiAsk.isNotBlank()) { val t = Overlay.aiAsk; Overlay.aiAsk = ""; run(t) } }
    T("Ví dụ: “nhạc Trịnh nghe lúc mưa khoảng 1 giờ”, “10 bài DSD để thử dàn loa”, “jazz chưa nghe bao giờ”.", 14, C.Muted, lines = 2)
    AiInput(q, { q = it }, "Bạn muốn nghe gì?", showSubmit = true, autoMic = true) { run(it) }
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    res?.let { r ->
        if (r.tracks.isNotEmpty()) {
            if (r.explain.isNotEmpty()) T(r.explain, 15, C.Muted, lines = 3)
            T("${r.tracks.size} bài phù hợp", 17, bold = true)
            r.tracks.take(6).forEach { T("• ${it.title} — ${it.artist}", 14, C.Muted, lines = 1) }
            if (r.tracks.size > 6) T("… và ${r.tracks.size - 6} bài nữa", 13, C.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Focusable({ play(r.tracks) }) { T("▶ Phát", 17, C.Gold, Modifier.padding(18.dp, 9.dp), bold = true) }
                Focusable({ play(r.tracks.shuffled()) }) { T("⤮ Trộn & phát", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
                Focusable({ UserPlaylists.add(r.name.ifBlank { q }, r.tracks); saved = true }) { T(if (saved) "✓ Đã lưu" else "💾 Lưu playlist", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
            }
            T("${fmtTotal(r.tracks)} tổng thời lượng", 13, C.Muted)
        }
    }
    AiTyping(q, { q = it }, "Bạn muốn nghe gì?", true) { run(it) }
}

/** Rút gọn thông báo lỗi để không tràn cửa sổ (bỏ phần trích nguyên văn JSON). */
internal fun errText(e: Throwable): String {
    var m = e.message ?: "Lỗi"
    val i = m.indexOf(" of {"); if (i > 0) m = m.substring(0, i)
    val j = m.indexOf(" of ["); if (j > 0) m = m.substring(0, j)
    if (m.startsWith("Unterminated") || m.startsWith("Expected") || m.startsWith("Value ") || m.startsWith("End of input")) m = "AI trả về dữ liệu chưa đầy đủ, thử lại"
    return m.lineSequence().first().take(140)
}

private fun fmtMs(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    val scope = rememberCoroutineScope()
    val active by SleepTimer.active
    val left by SleepTimer.remainingMs
    val note by SleepTimer.note
    var q by remember { mutableStateOf("") }
    var music by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun go(p: AiSleep.Plan) {
        if (p.endOfTrack) SleepTimer.startEndOfTrack() else SleepTimer.start(p.minutes, p.fadeSec)
        msg = p.say
        if ((music || p.music) && lib.isNotEmpty()) {
            if (!Ai.hasKey.value) { msg = "Đã hẹn giờ. Cần khóa AI để chọn nhạc ru ngủ."; return }
            scope.launch {
                try { play(AiSearch.run("nhạc nhẹ nhàng ru ngủ thư giãn, khoảng ${if (p.minutes > 0) p.minutes else 30} phút", lib).tracks) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { msg = "Đã hẹn giờ, nhưng chọn nhạc lỗi: " + errText(e) }
            }
        }
    }
    fun submit(t: String) {
        if (busy || t.isBlank()) return
        busy = true; msg = "Đang hiểu yêu cầu…"
        scope.launch {
            try { go(AiSleep.parse(t)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
            finally { busy = false }
        }
    }
    T("Dừng nhạc sau một khoảng thời gian, âm lượng nhỏ dần ở cuối rồi tạm dừng. Nói hoặc gõ tự nhiên: “ru ngủ 30 phút”, “1 giờ 15”, “hết bài này”.", 13, C.Muted, lines = 3)
    if (active) {
        T("😴 Còn ${fmtMs(left)}", 22, C.Gold, bold = true)
        if (note.isNotEmpty()) T(note, 13, C.Muted, lines = 2)
        Btn("✕ Hủy hẹn giờ") { SleepTimer.cancel(); msg = "Đã hủy hẹn giờ" }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            Focusable({ go(AiSleep.Plan(m, 60, false, false, "Đã hẹn giờ $m phút")) }, Modifier, RoundedCornerShape(8.dp)) { T("$m phút", 16, C.Gold, Modifier.padding(14.dp, 8.dp)) }
        }
        Focusable({ go(AiSleep.Plan(0, 12, true, false, "Sẽ dừng khi hết bài này")) }, Modifier, RoundedCornerShape(8.dp)) { T("Hết bài này", 16, C.Gold, Modifier.padding(14.dp, 8.dp)) }
    }
    Focusable({ music = !music }, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), selected = music) {
        T((if (music) "● " else "○ ") + "Kèm phát nhạc ru ngủ từ thư viện (AI chọn)", 15, if (music) C.Gold else C.Text, Modifier.padding(14.dp, 8.dp), lines = 2)
    }
    AiInput(q, { q = it }, "Ví dụ: ru ngủ 30 phút", showSubmit = true, autoMic = false) { submit(it) }
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 3)
    AiTyping(q, { q = it }, "Ví dụ: ru ngủ 30 phút", true) { submit(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaverPage(preview: () -> Unit) {
    val on by AiSaver.on
    val mins by AiSaver.minutes
    T("Khi không thao tác, màn hình chuyển sang nền đen (tiết kiệm OLED) hiện ảnh bìa, tên bài, thời tiết, âm lịch/ngày lễ và vài câu giới thiệu album do AI viết. Nội dung dịch chỗ chậm để không in bóng. Chạm hoặc bấm phím để thoát.", 13, C.Muted, lines = 5)
    Focusable({ AiSaver.setOn(!on) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), selected = on) {
        T((if (on) "● " else "○ ") + "Màn hình chờ thông minh: " + (if (on) "BẬT" else "TẮT"), 17, C.Gold, Modifier.padding(14.dp, 9.dp), bold = true)
    }
    T("Hiện sau:", 14, C.Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 3, 5, 10, 15).forEach { m ->
            Focusable({ AiSaver.setMinutes(m) }, Modifier, RoundedCornerShape(8.dp), selected = mins == m) { T("$m phút", 16, if (mins == m) C.Gold else C.Text, Modifier.padding(14.dp, 8.dp)) }
        }
    }
    Btn("👁 Xem thử ngay") { preview() }
    T("Câu giới thiệu album cần khóa AI (mỗi album chỉ hỏi một lần). Không có khóa thì chỉ hiện bài hát, thời tiết và ngày lễ.", 12, C.Muted, lines = 3)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestPage(lib: List<Track>, playAt: (List<Track>, Int) -> Unit) {
    var focus by remember { mutableStateOf("Tổng hợp") }
    var items by remember { mutableStateOf<List<AiTestSet.Item>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(tick, focus) {
        msg = "AI đang chọn bài thử âm từ thư viện của bạn…"; items = emptyList(); saved = false
        try { items = AiTestSet.run(lib, focus); msg = if (items.isEmpty()) "AI chưa chọn được bài nào." else "" }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
    }
    T("Mỗi mục là một bài thử một khía cạnh của dàn loa/tai nghe, kèm lời nhắc nghe điều gì. Chọn từ chính thư viện của bạn, ưu tiên bản thu hi-res.", 13, C.Muted, lines = 3)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Tổng hợp", "Giọng hát", "Trầm sâu & động lực", "Không gian & chi tiết").forEach { f ->
            Focusable({ focus = f }, Modifier, RoundedCornerShape(8.dp), selected = focus == f) { T(f, 15, if (focus == f) C.Gold else C.Text, Modifier.padding(12.dp, 6.dp)) }
        }
    }
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    val tracks = items.map { x -> x.track }
    items.forEachIndexed { i, x ->
        Focusable({ playAt(tracks, i) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("▶ ${x.category} — ${x.track.title}", 16, C.Gold, bold = true, lines = 2)
                T("${x.track.artist} · ${x.track.album} · ${x.track.ext.uppercase()}", 12, C.Muted, lines = 1)
                if (x.tip.isNotBlank()) T("👂 " + x.tip, 13, lines = 3)
            }
        }
    }
    if (items.isNotEmpty()) {
        Btn("▶ Phát cả buổi thử dàn (${items.size} bài)") { playAt(tracks, 0) }
        Btn(if (saved) "✓ Đã lưu playlist" else "💾 Lưu thành playlist “Buổi thử dàn”") { UserPlaylists.add("Buổi thử dàn — $focus", tracks); saved = true }
    }
    Focusable({ tick++ }) { T("↻ Chọn bộ khác", 15, C.Gold, Modifier.padding(14.dp, 7.dp)) }
}

/** Tìm nhanh bài hát và thư mục trong MỌI nguồn đã quét (bộ nhớ, USB, NAS/DLNA, đám mây). Chạy cục bộ, kết quả hiện ngay khi gõ/nói; không cần khóa AI. */
@Composable
private fun QuickPage(all: List<Track>, playAt: (List<Track>, Int) -> Unit, askAi: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val snap by produceState<AiIndex.Snap?>(null, all) {
        value = try { AiIndex.get(all) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }
    var hits by remember { mutableStateOf<AiIndex.Hits?>(null) }
    LaunchedEffect(q, snap) {
        val s = snap
        if (s == null || q.isBlank()) { hits = null; return@LaunchedEffect }
        delay(150)
        hits = try { aiHeavy { AiIndex.search(s, q) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
    }
    val s = snap
    T(if (s == null) "Đang lập chỉ mục thư viện…" else "Đã lập chỉ mục ${s.entries.size} bài · ${s.folders.size} thư mục · " + s.srcCounts.take(4).joinToString(" · ") { "${it.first} ${it.second}" }, 13, C.Muted, lines = 2)
    AiInput(q, { q = it }, "Tên bài, ca sĩ, album hoặc thư mục…", showSubmit = false, autoMic = false) { }
    val h = hits
    if (q.isBlank()) T(if (Device.tv) "Bấm 🎤 Nói hoặc 📱 để nhập tên bài, ca sĩ, album hay thư mục cần tìm." else "Gõ tên bài, ca sĩ, album hoặc thư mục — kết quả hiện ngay.", 14, C.Muted, lines = 2)
    else if (h != null) {
        if (h.folders.isEmpty() && h.tracks.isEmpty()) T("Không thấy — thử bớt chữ hoặc bấm “Hỏi AI”.", 15, C.Gold)
        if (h.folders.isNotEmpty()) {
            T("Thư mục (${h.totalFolders})", 15, C.Gold, bold = true)
            h.folders.take(8).forEach { f ->
                Focusable({ playAt(f.tracks.sortedWith(compareBy({ it.dir }, { it.no })), 0) }, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp)) { foc ->
                    Column(Modifier.padding(12.dp, 6.dp)) {
                        TM("📁 " + f.name, 16, C.Text, Modifier.fillMaxWidth(), run = foc)
                        T("${f.tracks.size} bài · ${f.src}" + if (f.where.isNotBlank()) " · " + f.where else "", 12, C.Muted)
                    }
                }
            }
            if (h.totalFolders > 8) T("… và ${h.totalFolders - 8} thư mục nữa — gõ thêm chữ để thu hẹp", 12, C.Muted)
        }
        if (h.tracks.isNotEmpty()) {
            T("Bài hát (${h.totalTracks}) — bấm để phát, tiếp tục các bài khớp", 15, C.Gold, bold = true)
            h.tracks.take(20).forEachIndexed { i, t ->
                Focusable({ playAt(h.tracks, i) }, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp)) { foc ->
                    Column(Modifier.padding(12.dp, 6.dp)) {
                        TM("♪ " + t.title, 16, C.Text, Modifier.fillMaxWidth(), run = foc)
                        TM(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · ") + " · " + t.sourceName, 12, C.Muted, Modifier.fillMaxWidth(), run = foc)
                    }
                }
            }
            if (h.totalTracks > 20) T("… và ${h.totalTracks - 20} bài nữa", 12, C.Muted)
            Btn("▶ Phát tất cả bài khớp (${h.tracks.size})") { playAt(h.tracks, 0) }
        }
        if (Ai.hasKey.value) Btn("✨ Hỏi AI về “$q” (hiểu ý, đối chiếu kỹ)") { askAi(q) }
    }
    AiTyping(q, { q = it }, "Tên bài, ca sĩ, album hoặc thư mục…", false) { }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    var msg by remember { mutableStateOf("Đang chọn nhạc cho bạn…") }
    var lists by remember { mutableStateOf<List<AiSuggest.Playlist>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var mood by remember { mutableStateOf("Tự động") }
    var head by remember { mutableStateOf("") }
    LaunchedEffect(tick, mood) {
        msg = "Đang xem thời tiết và chọn nhạc…"; lists = emptyList()
        try {
            val info = AiContext.load(tick > 0 && mood == "Tự động")
            head = AiContext.headline(info)
            lists = AiSuggest.run(lib, AiContext.describe(info, mood))
            msg = if (lists.isEmpty()) "AI chưa chọn được danh sách nào." else ""
        } catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
    }
    if (head.isNotEmpty()) T(head, 14, C.Muted, lines = 2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AiContext.MOODS.forEach { m ->
            Focusable({ mood = m }, Modifier, RoundedCornerShape(8.dp), selected = mood == m) { T(m, 15, if (mood == m) C.Gold else C.Text, Modifier.padding(12.dp, 6.dp)) }
        }
    }
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    lists.forEach { p ->
        Focusable({ play(p.tracks) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("▶ ${p.title}  ·  ${p.tracks.size} bài", 17, C.Gold, bold = true, lines = 1)
                T(p.reason, 13, C.Muted, lines = 2)
            }
        }
    }
    Focusable({ tick++ }) { T("↻ Gợi ý khác", 15, C.Gold, Modifier.padding(14.dp, 7.dp)) }
    T("Vị trí ước lượng theo địa chỉ IP; thời tiết từ Open-Meteo. Chỉ gửi thành phố, thời tiết và ngày giờ — không gửi thư viện.", 12, C.Muted, lines = 2)
}

@Composable
private fun InfoPage() {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val t = queue.getOrNull(idx)
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    if (t == null) { T("Chưa có bài nào đang phát.", 16, C.Muted); return }
    LaunchedEffect(t.path) {
        text = ""; err = ""
        try { text = AiInfo.run(t) } catch (e: Exception) { err = "✗ " + errText(e) }
    }
    T(t.title, 20, bold = true, lines = 2)
    T("${t.artist} · ${t.album}", 15, C.Muted, lines = 2)
    if (text.isEmpty() && err.isEmpty()) T("Đang hỏi AI…", 15, C.Gold)
    if (err.isNotEmpty()) T(err, 15, androidx.compose.ui.graphics.Color(0xFFE5575A), lines = 4)
    if (text.isNotEmpty()) {
        T(text, 16, lines = 40)
        T("Nội dung do AI viết, có thể chưa chính xác.", 12, C.Muted)
    }
}

@Composable
private fun CleanPage(lib: List<Track>) {
    val scope = rememberCoroutineScope()
    val done = AiMeta.map.size
    val pending by produceState(0, lib, done) { value = try { AiClean.pending(lib).size } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { 0 } catch (e: OutOfMemoryError) { 0 } }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    T("AI đọc tên thư mục (kèm một nghệ sĩ/album mẫu) rồi đặt tên hiển thị gọn, có dấu tiếng Việt và xác định thể loại để chọn màu/biểu tượng. File và thư mục thật không bị đổi tên.", 15, C.Muted, lines = 5)
    T("Đã xử lý: $done thư mục · Còn lại: $pending", 17, bold = true)
    if (prog.isNotEmpty()) T(prog, 15, C.Gold, lines = 3)
    if (!busy) {
        if (pending > 0) Btn("▶ Bắt đầu làm sạch ($pending thư mục)") {
            busy = true; prog = "Đang xử lý…"
            job = scope.launch {
                try { AiClean.run(lib) { d, n -> prog = "Đã xử lý $d / $n" }; AiIndex.drop(); prog = "Xong ✓" }
                catch (e: kotlinx.coroutines.CancellationException) { prog = "Đã dừng" }
                catch (e: Exception) { prog = "✗ " + errText(e) + " — bấm Bắt đầu để tiếp tục phần còn lại." }
                finally { busy = false }
            }
        }
        if (done > 0) Btn("↺ Khôi phục tên gốc (xóa kết quả AI)") { AiMeta.clear(); AiIndex.drop(); prog = "Đã khôi phục" }
    } else Btn("■ Dừng") { job?.cancel() }
}

private fun fmtTotal(l: List<Track>): String { val m = l.sumOf { it.durationMs } / 60000; return if (m >= 60) "${m / 60}g ${m % 60}p" else "${m}p" }

@Composable
private fun SavedListsPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    val items = UserPlaylists.list
    if (items.isEmpty()) T("Chưa có playlist nào. Vào “Hỏi / tạo playlist”, nói điều bạn muốn nghe rồi bấm Lưu playlist.", 15, C.Muted, lines = 3)
    items.toList().forEach { p ->
        val tr = UserPlaylists.tracks(p, lib)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Focusable({ play(tr) }, Modifier.weight(1f), RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp, 8.dp)) {
                    T("▶ ${p.name}", 17, C.Gold, bold = true)
                    T("${tr.size} bài · ${fmtTotal(tr)}", 13, C.Muted)
                }
            }
            Focusable({ UserPlaylists.remove(p) }) { T("✕ Xóa", 14, C.Muted, Modifier.padding(12.dp, 8.dp)) }
        }
    }
}

@Composable
private fun TagsPage(lib: List<Track>) {
    val scope = rememberCoroutineScope()
    var fixes by remember { mutableStateOf<List<AiTags.Fix>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var undoCount by remember { mutableStateOf(TagFix.count) }
    DisposableEffect(Unit) { onDispose { } }
    T("Tìm tên nghệ sĩ/album bị viết nhiều kiểu (Khanh Ly / KHÁNH LY) và tên Việt thiếu dấu. Chỉ đổi cách hiển thị trong app, file nhạc không bị sửa. Bạn duyệt từng mục trước khi áp dụng.", 14, C.Muted, lines = 4)
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 3)
    val list = fixes
    if (list == null) {
        if (!busy) Btn("🔍 Quét thư viện") {
            busy = true; msg = "Đang quét và hỏi AI…"
            scope.launch {
                try { val r = AiTags.scan(lib).take(80); fixes = r; msg = if (r.isEmpty()) "Không thấy tên nào cần sửa ✓" else "" }
                catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
                finally { busy = false }
            }
        }
    } else {
        list.forEach { f ->
            Focusable({ f.on = !f.on }, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), selected = f.on) {
                Column(Modifier.padding(12.dp, 7.dp)) {
                    T((if (f.on) "● " else "○ ") + f.from.joinToString(", ") + "  →  " + f.to, 15, if (f.on) C.Gold else C.Text, lines = 2)
                    T((if (f.kind == "artist") "nghệ sĩ" else "album") + if (f.viaAi) " · thêm dấu (AI)" else " · gộp tên trùng", 12, C.Muted)
                }
            }
        }
        val n = list.count { it.on }
        if (n > 0) Btn("✔ Áp dụng $n mục") { AiTags.apply(list); undoCount = TagFix.count; fixes = null; msg = "Đã áp dụng $n mục ✓" }
    }
    if (undoCount > 0) Btn("↺ Hoàn tác mọi sửa tag ($undoCount)") { AiTags.undoAll(); undoCount = 0; msg = "Đã hoàn tác ✓" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForgottenPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    var msg by remember { mutableStateOf("Đang lục kho nhạc…") }
    var lists by remember { mutableStateOf<List<AiSuggest.Playlist>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        msg = "Đang lục kho nhạc…"; lists = emptyList()
        try {
            lists = AiForgotten.run(lib, AiContext.describe(AiContext.load(), ""))
            msg = if (lists.isEmpty()) "Chưa chọn được album nào." else ""
        } catch (e: Exception) { msg = "✗ " + errText(e).lines().first().take(120) }
    }
    T("Album chất lượng cao hoặc lâu rồi chưa nghe, AI chọn những cái hợp với hôm nay.", 14, C.Muted, lines = 2)
    if (msg.isNotEmpty()) T(msg, 15, if (msg.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    lists.forEach { p ->
        Focusable({ play(p.tracks) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("▶ ${p.title}  ·  ${p.tracks.size} bài", 16, C.Gold, bold = true, lines = 2)
                T(p.reason, 13, C.Muted, lines = 3)
            }
        }
    }
    Focusable({ tick++ }) { T("↻ Chọn album khác", 15, C.Gold, Modifier.padding(14.dp, 7.dp)) }
}

@Composable
private fun LyricsPage() {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val t = queue.getOrNull(idx)
    if (t == null) { T("Chưa có bài nào đang phát.", 16, C.Muted); return }
    var lyrics by remember(t.path) { mutableStateOf<String?>(null) }
    var loaded by remember(t.path) { mutableStateOf(false) }
    var trans by remember(t.path) { mutableStateOf("") }
    var meaning by remember(t.path) { mutableStateOf("") }
    var showTrans by remember(t.path) { mutableStateOf(false) }
    var busy by remember(t.path) { mutableStateOf(false) }
    var err by remember(t.path) { mutableStateOf("") }
    var pageNo by remember(t.path) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(t.path) { lyrics = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Lyrics.load(t) }; loaded = true }
    T(t.title, 18, bold = true, lines = 2)
    T("${t.artist} · ${t.album}", 13, C.Muted, lines = 1)
    val ly = lyrics
    if (!loaded) { T("Đang tìm lời…", 15, C.Gold); return }
    if (ly == null) {
        T("Không tìm thấy lời bài hát. Hãy đặt file .lrc hoặc .txt cùng tên với bài nhạc (vd. bai.flac → bai.lrc), hoặc nhúng lời vào tag (MP3/FLAC). AI sẽ không tự bịa lời.", 15, C.Muted, lines = 5)
        return
    }
    val src = if (showTrans && trans.isNotEmpty()) trans else ly
    val lines = remember(src) { src.lines() }
    val per = 12
    val pages = ((lines.size + per - 1) / per).coerceAtLeast(1)
    val pg = pageNo.coerceIn(0, pages - 1)
    lines.drop(pg * per).take(per).forEach { T(it.ifBlank { " " }, 17, lines = 2) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pages > 1) {
            Focusable({ pageNo = (pg - 1).coerceAtLeast(0) }) { T("▲ Trước", 14, C.Gold, Modifier.padding(12.dp, 7.dp)) }
            T("${pg + 1}/$pages", 13, C.Muted)
            Focusable({ pageNo = (pg + 1).coerceAtMost(pages - 1) }) { T("Sau ▼", 14, C.Gold, Modifier.padding(12.dp, 7.dp)) }
        }
        if (trans.isNotEmpty()) Focusable({ showTrans = !showTrans; pageNo = 0 }) { T(if (showTrans) "Xem bản gốc" else "Xem bản dịch", 14, C.Gold, Modifier.padding(12.dp, 7.dp)) }
    }
    if (busy) T("Đang hỏi AI…", 15, C.Gold)
    if (err.isNotEmpty()) T(err, 14, androidx.compose.ui.graphics.Color(0xFFE5575A), lines = 3)
    if (!busy) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Focusable({
            busy = true; err = ""
            scope.launch {
                try { trans = AiLyrics.translate(ly, t); showTrans = true; pageNo = 0 } catch (e: Exception) { err = "✗ " + errText(e) } finally { busy = false }
            }
        }) { T("🌐 Dịch", 15, C.Gold, Modifier.padding(14.dp, 8.dp), bold = true) }
        Focusable({
            busy = true; err = ""
            scope.launch {
                try { meaning = AiLyrics.meaning(ly, t) } catch (e: Exception) { err = "✗ " + errText(e) } finally { busy = false }
            }
        }) { T("💡 Giải nghĩa", 15, C.Gold, Modifier.padding(14.dp, 8.dp), bold = true) }
    }
    if (meaning.isNotEmpty()) { T(meaning, 15, lines = 12); T("Nội dung do AI viết, có thể chưa chính xác.", 12, C.Muted) }
}

/** Chạy nền khi bật AI DJ: sắp hết danh sách đang phát thì nhờ AI thêm bài. */
@Composable
fun AiDjEffect() {
    val on by AiDj.on
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val lib by Library.tracks.collectAsState()
    val has by Ai.hasKey
    LaunchedEffect(on, has, queue.size, idx) {
        if (!on || !has || queue.isEmpty() || idx < queue.size - 2 || AiDj.busy) return@LaunchedEffect
        AiDj.busy = true
        try {
            val more = AiDjTask.more(lib, queue, idx, AiContext.describe(AiContext.load(), ""))
            if (more.isNotEmpty()) Playback.append(more)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { AiDj.note.value = "✗ " + errText(e) }
        finally { AiDj.busy = false }
    }
}

/** Thẻ trong Cài đặt. */
@Composable
fun AiCard() {
    val has by Ai.hasKey
    val rev by Ai.rev
    val n = remember(rev) { AiProvs.ALL.count { Ai.has(it.id) } }
    Card {
        T("TRỢ LÝ AI", 13, C.Muted, spaced = true)
        T(if (has) "Đã có $n dịch vụ AI (chính: ${AiProvs.get(Ai.primary).name}). Tạo playlist bằng lời nói, AI DJ, gợi ý theo thời tiết / lễ hội / tâm trạng, lời bài hát, sửa tag, làm sạch thư mục." else "Chưa có khóa. Hỗ trợ Gemini, Groq, OpenRouter, Cloudflare AI, NVIDIA AI (đều có gói miễn phí). Ghép bằng điện thoại: quét mã QR, dán khóa.", 15, C.Muted, lines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (has) Focusable({ Overlay.aiStart = "menu"; Overlay.ai.value = true }) { T("✨ Mở trợ lý AI", 16, C.Gold, Modifier.padding(14.dp, 7.dp), bold = true) }
            Focusable({ Overlay.aiStart = "key"; Overlay.ai.value = true }) { T(if (has) "🔑 Dịch vụ AI & khóa" else "🔑 Ghép khóa bằng điện thoại", 16, C.Gold, Modifier.padding(14.dp, 7.dp)) }
        }
    }
}
