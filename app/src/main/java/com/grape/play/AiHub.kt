package com.grape.play

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Cầu nối với điện thoại: TV mở một máy chủ HTTP tạm trong mạng LAN (chỉ khi hộp thoại đang mở, kèm mã ngẫu nhiên trong địa chỉ).
 * Người dùng quét mã QR bằng điện thoại để dán khóa API hoặc gõ/nói câu hỏi — không cần gõ chữ trên TV.
 */
class AiPhoneLink(private val onKey: (String) -> Unit, private val onAsk: (String) -> Unit) {
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
            val k = form(body)["k"].orEmpty().trim()
            if (k.length < 20) return 200 to page("", "<p style='color:#b00020'><b>Khóa quá ngắn — hãy dán đúng toàn bộ khóa.</b></p><p><a href='/$tq'>← Quay lại</a></p>")
            onKey(k); return 200 to page("Đã gửi khóa tới TV ✓ (xem kết quả kiểm tra trên TV)", "<p><a href='/$tq'>← Quay lại</a></p>")
        }
        if (method == "POST" && route == "/ask") {
            val a = form(body)["q"].orEmpty().trim()
            if (a.isEmpty()) return 200 to page("", "<p><a href='/$tq'>← Quay lại</a></p>")
            onAsk(a); return 200 to page("Đã gửi tới TV ✓", "<p>Câu hỏi: <i>${esc(a)}</i></p><p><a href='/$tq'>Gửi câu khác →</a></p>")
        }
        val inner = "<h3>Hỏi / tìm nhạc</h3><p style='color:#555'>Gõ hoặc bấm micro trên bàn phím điện thoại để nói. Ví dụ: <i>nhạc Bolero nhẹ nhàng buổi tối</i>, <i>các album DSD hay nghe nhất</i>.</p>" +
            "<form method=post action='/ask$tq'><textarea name=q rows=3 style='width:100%;font-size:18px' placeholder='Bạn muốn nghe gì?'></textarea><p><button style='font-size:18px;padding:10px 24px'>Gửi tới TV</button></p></form>" +
            "<hr><h3>Khóa API Gemini</h3><p style='color:#555'>Trạng thái: <b>${if (Ai.hasKey.value) "đã có khóa" else "chưa có khóa"}</b>. Lấy khóa miễn phí tại " +
            "<a target=_blank rel=noopener href='https://aistudio.google.com/apikey'>aistudio.google.com/apikey</a> rồi dán vào ô dưới.</p>" +
            "<form method=post action='/key$tq'><input name=k autocomplete=off style='width:100%;font-size:16px;padding:6px' placeholder='AIza…'><p><button style='font-size:18px;padding:10px 24px'>Lưu khóa vào TV</button></p></form>"
        return 200 to page("", inner)
    }
}

private val ui = Handler(Looper.getMainLooper())

@Composable
private fun AiFrame(title: String, back: String?, onBack: () -> Unit, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { first.requestFocus() } }
    val maxH = if (Device.tv) 640.dp else (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = { if (back != null) onBack() else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val base = Modifier.dlgW(720).heightIn(max = maxH)
        val surf = when (Themes.style.value) {
            5, 6, 7, 8, 9, 10 -> base.clip(RoundedCornerShape(12.dp)).background(C.Bg).sfPanel(12.dp)
            4 -> base.clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Panel, C.Side))).border(1.dp, C.Line, RoundedCornerShape(4.dp))
            1 -> base.clip(RoundedCornerShape(22.dp)).background(C.Panel)
            else -> base.clip(RoundedCornerShape(12.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(12.dp))
        }
        Column(surf.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (back != null) Focusable(onBack, requester = first) { T("◂ $back", 14, C.Gold, Modifier.padding(12.dp, 5.dp)) }
                T(title.uppercase(), 13, C.Muted, spaced = true, modifier = Modifier.weight(1f))
                Focusable(onClose, requester = if (back == null) first else null) { T("Đóng", 14, C.Muted, Modifier.padding(12.dp, 5.dp)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Focusable(onClick, modifier.fillMaxWidth(), RoundedCornerShape(10.dp)) { T(text, 16, C.Gold, Modifier.padding(14.dp, 9.dp), bold = true, lines = 2) }

@Composable
fun AiHubDialog(onClose: () -> Unit, onPlayed: () -> Unit) {
    val hasKey by Ai.hasKey
    val rawTracks by Library.tracks.collectAsState()
    val hidden by SourceVisibility.hidden
    val lib = remember(rawTracks, hidden) { if (hidden.isEmpty()) rawTracks else rawTracks.filter { it.src !in hidden } }
    var page by remember { mutableStateOf(if (!Ai.hasKey.value) "key" else Overlay.aiStart) }
    fun close() { Overlay.aiStart = "menu"; onClose() }
    fun play(l: List<Track>) { if (l.isNotEmpty()) { Playback.play(l, 0, false); onPlayed(); close() } }
    val goMenu = { page = "menu" }
    when {
        page == "key" -> AiFrame("Khóa Gemini", if (hasKey) "Menu" else null, goMenu, ::close) { KeyPage { page = "menu" } }
        page == "ask" -> AiFrame("Hỏi / tìm nhạc", "Menu", goMenu, ::close) { AskPage(lib, ::play) }
        page == "suggest" -> AiFrame("Gợi ý theo bối cảnh", "Menu", goMenu, ::close) { SuggestPage(lib, ::play) }
        page == "info" -> AiFrame("Giới thiệu bài đang phát", "Menu", goMenu, ::close) { InfoPage() }
        page == "clean" -> AiFrame("Làm sạch thư viện", "Menu", goMenu, ::close) { CleanPage(lib) }
        else -> AiFrame("✨ Trợ lý AI (Gemini)", null, goMenu, ::close) {
            T("AI chỉ chạy khi bạn bấm. Thư viện không bị gửi lên mạng — chỉ gửi câu hỏi, vài tên nghệ sĩ/album hoặc tên thư mục cần xử lý.", 13, C.Muted, lines = 3)
            Btn("🔎  Hỏi / tìm nhạc bằng lời nói hoặc chữ") { page = "ask" }
            Btn("🌤  Gợi ý theo thời tiết, lễ hội, tâm trạng") { page = "suggest" }
            Btn("ℹ  Giới thiệu bài / album / nghệ sĩ đang phát") { page = "info" }
            Btn("🧹  Làm sạch tên và thể loại thư mục") { page = "clean" }
            Btn("🔑  Khóa API / ghép bằng điện thoại") { page = "key" }
        }
    }
}

@Composable
private fun KeyPage(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun accept(k: String) {
        if (busy) return
        busy = true; Ai.key = k
        status = "Đã lưu khóa. Đang kiểm tra kết nối tới Gemini…"
        scope.launch {
            val t0 = System.currentTimeMillis()
            try { Ai.test(); status = "Khóa hợp lệ ✓"; onDone() }
            catch (e: Exception) { status = "✗ Đã lưu khóa nhưng kiểm tra thất bại sau ${(System.currentTimeMillis() - t0) / 1000}s: ${e.message ?: e.javaClass.simpleName}. Kiểm tra mạng của TV rồi bấm “Kiểm tra khóa”." }
            finally { busy = false }
        }
    }
    val link = remember { AiPhoneLink({ k -> ui.post { accept(k) } }, {}) }
    LaunchedEffect(Unit) { if (link.start()) url = link.url else status = "Không lấy được địa chỉ mạng — hãy dán khóa trực tiếp ở ô bên dưới." }
    DisposableEffect(Unit) { onDispose { link.stop() } }
    T(if (Ai.hasKey.value) "Đã có khóa. Có thể thay bằng khóa khác." else "Cần khóa API Gemini (miễn phí, tạo ở aistudio.google.com/apikey).", 15, C.Muted, lines = 2)
    val u = url
    val gUrl = "https://aistudio.google.com/apikey"
    if (!Device.tv) Btn("🌐 Mở trang tạo khóa của Google") {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(gUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { status = "Không mở được trình duyệt — hãy tự vào aistudio.google.com/apikey" }
    }
    if (u != null && Device.tv) Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("① Tạo khóa", 15, C.Gold, bold = true); QrCode(gUrl, 150.dp); T("Trang Google", 12, C.Muted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("② Gửi khóa về TV", 15, C.Gold, bold = true); QrCode(u, 150.dp); T("Trang của TV", 12, C.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Không cần gõ khóa trên TV (điện thoại cùng Wi-Fi):", 16, C.Gold, bold = true)
            T("① Quét mã ①: đăng nhập Google → Create API key → sao chép khóa.", 15, lines = 3)
            T("② Quét mã ②: dán khóa → Lưu khóa vào TV.", 15, lines = 2)
            T(u, 12, C.Muted, lines = 2)
        }
    }
    T("Hoặc dán trực tiếp (điện thoại / bàn phím rời):", 14, C.Muted)
    OutlinedTextField(manual, { manual = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Focusable({ if (manual.isNotBlank()) accept(manual.trim()) }) { T("Lưu khóa", 16, C.Gold, Modifier.padding(16.dp, 8.dp), bold = true) }
        Focusable({
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val s = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (s.length >= 20) accept(s) else status = "Bộ nhớ tạm không có khóa"
        }) { T("Dán từ bộ nhớ tạm", 16, C.Gold, Modifier.padding(16.dp, 8.dp)) }
        if (Ai.hasKey.value) Focusable({ accept(Ai.key) }) { T("Kiểm tra khóa", 16, C.Gold, Modifier.padding(16.dp, 8.dp)) }
        if (Ai.hasKey.value) Focusable({ Ai.key = ""; status = "Đã xóa khóa" }) { T("Xóa khóa", 16, androidx.compose.ui.graphics.Color(0xFFE5575A), Modifier.padding(16.dp, 8.dp)) }
    }
    var diag by remember { mutableStateOf("") }
    Focusable({
        if (!busy) { busy = true; diag = "Đang đo từng bước, có thể mất tới 1 phút…"; scope.launch { try { diag = Ai.diagnose() } catch (e: Exception) { diag = "Lỗi: ${e.message}" } finally { busy = false } } }
    }) { T("🩺 Chẩn đoán mạng", 16, C.Gold, Modifier.padding(16.dp, 8.dp)) }
    if (diag.isNotEmpty()) T(diag, 14, C.Text, lines = 10)
    if (status.isNotEmpty()) T(status, 15, if (status.startsWith("✗")) androidx.compose.ui.graphics.Color(0xFFE5575A) else C.Gold, lines = 4)
    T("Khóa lưu riêng trong app, trên thiết bị này. Máy chủ tạm chỉ chạy khi hộp thoại này mở.", 12, C.Muted, lines = 2)
}

@Composable
private fun AskPage(lib: List<Track>, play: (List<Track>) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<AiSearch.Result?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    fun run(text: String) {
        if (busy || text.isBlank()) return
        busy = true; msg = "Đang hỏi AI…"; res = null
        scope.launch {
            try { res = AiSearch.run(text, lib); msg = if (res!!.tracks.isEmpty()) "Không tìm thấy bài phù hợp — thử nói khác đi." else "" }
            catch (e: Exception) { msg = "✗ " + (e.message ?: "Lỗi") }
            finally { busy = false }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) { q = t; run(t) }
    }
    val link = remember { AiPhoneLink({}, { s -> ui.post { q = s; run(s) } }) }
    DisposableEffect(Unit) { onDispose { link.stop() } }
    T("Ví dụ: “nhạc Bolero nhẹ nhàng buổi tối”, “album DSD nghe nhiều nhất”, “jazz chưa nghe bao giờ”.", 14, C.Muted, lines = 2)
    OutlinedTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 18.sp), modifier = Modifier.fillMaxWidth(), placeholder = { T("Bạn muốn nghe gì?", 16, C.Muted) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Focusable({ run(q) }) { T("Tìm", 17, C.Gold, Modifier.padding(18.dp, 9.dp), bold = true) }
        Focusable({
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.en.value) "en-US" else "vi-VN").putExtra(RecognizerIntent.EXTRA_PROMPT, "Bạn muốn nghe gì?")
            try { speech.launch(i) } catch (e: Exception) { msg = "Thiết bị này không có nhận dạng giọng nói — dùng điện thoại (nút bên cạnh) hoặc gõ chữ." }
        }) { T("🎤 Nói", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
        if (Device.tv) Focusable({ if (link.start()) url = link.url else msg = "Không lấy được địa chỉ mạng." }) { T("📱 Nhập bằng điện thoại", 17, C.Gold, Modifier.padding(18.dp, 9.dp)) }
    }
    val u = url
    if (u != null) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        QrCode(u, 150.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Quét mã bằng điện thoại, gõ hoặc nói câu hỏi rồi bấm Gửi — kết quả hiện ở đây.", 15, lines = 3)
            T(u, 12, C.Muted, lines = 2)
        }
    }
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
            }
        }
    }
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
        } catch (e: Exception) { msg = "✗ " + (e.message ?: "Lỗi") }
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
        try { text = AiInfo.run(t) } catch (e: Exception) { err = "✗ " + (e.message ?: "Lỗi") }
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
    val pending = remember(lib, done) { AiClean.pending(lib).size }
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
                try { AiClean.run(lib) { d, n -> prog = "Đã xử lý $d / $n" }; prog = "Xong ✓" }
                catch (e: kotlinx.coroutines.CancellationException) { prog = "Đã dừng" }
                catch (e: Exception) { prog = "✗ " + (e.message ?: "Lỗi") + " — bấm Bắt đầu để tiếp tục phần còn lại." }
                finally { busy = false }
            }
        }
        if (done > 0) Btn("↺ Khôi phục tên gốc (xóa kết quả AI)") { AiMeta.clear(); prog = "Đã khôi phục" }
    } else Btn("■ Dừng") { job?.cancel() }
}

/** Thẻ trong Cài đặt. */
@Composable
fun AiCard() {
    val has by Ai.hasKey
    Card {
        T("AI · GEMINI", 13, C.Muted, spaced = true)
        T(if (has) "Đã có khóa API. Tìm nhạc bằng giọng nói, gợi ý theo thời tiết / lễ hội / tâm trạng, giới thiệu bài, làm sạch tên thư mục." else "Chưa có khóa. Ghép bằng điện thoại: quét mã QR, dán khóa — không cần gõ trên TV.", 15, C.Muted, lines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (has) Focusable({ Overlay.aiStart = "menu"; Overlay.ai.value = true }) { T("✨ Mở trợ lý AI", 16, C.Gold, Modifier.padding(14.dp, 7.dp), bold = true) }
            Focusable({ Overlay.aiStart = "key"; Overlay.ai.value = true }) { T(if (has) "🔑 Đổi / xóa khóa" else "🔑 Ghép khóa bằng điện thoại", 16, C.Gold, Modifier.padding(14.dp, 7.dp)) }
        }
    }
}
