package com.grape.play

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cập nhật ứng dụng qua GitHub Releases.
 * Quy ước: mỗi bản phát hành có tag dạng "v0.8.2" (hoặc "0.8.2") và đính kèm đúng một file .apk.
 * App hỏi https://api.github.com/repos/REPO/releases/latest, so sánh tag với versionName đang chạy,
 * tải APK vào bộ nhớ đệm rồi mở trình cài đặt của Android. Repo phải để PUBLIC.
 * APK mới phải ký cùng khoá với bản đang cài, nếu không Android sẽ từ chối cài đè.
 */
object Updater {
    const val REPO = "PhungP/grape2play"

    enum class St { IDLE, CHECKING, UPTODATE, AVAILABLE, DOWNLOADING, READY, ERROR }
    class Release(val version: String, val url: String, val size: Long, val notes: String)

    val state = mutableStateOf(St.IDLE)
    val release = mutableStateOf<Release?>(null)
    val progress = mutableStateOf(0)
    val error = mutableStateOf("")
    private var apk: File? = null
    private var autoDone = false
    /** true = hiện cửa sổ thông báo có bản mới (đặt khi tự kiểm tra thấy bản mới; tắt khi người dùng chọn "Để sau"). */
    val prompt = mutableStateOf(false)

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** true nếu remote mới hơn local (so từng số: 0.8.10 > 0.8.9). Logic nằm trong Versions.java (có test). */
    fun isNewer(remote: String, local: String): Boolean = Versions.isNewer(remote, local)

    /** Trả về bản mới nhất, hoặc null nếu repo chưa có release / release chưa có file .apk. */
    private fun fetchLatest(): Release? {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "GRAPEPlay-Updater").build()
        Http.client.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val j = JSONObject(r.body?.string().orEmpty())
            val tag = j.optString("tag_name").trim().trimStart { !it.isDigit() }   // chịu được "v0.8.3", "v.0.8.3", "V0.8.3"...
            val assets = j.optJSONArray("assets")
            var url = ""; var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
            }
            if (tag.isEmpty() || url.isEmpty()) return null
            return Release(tag, url, size, j.optString("body").trim())
        }
    }

    suspend fun check(ctx: Context) {
        if (state.value == St.CHECKING || state.value == St.DOWNLOADING) return
        state.value = St.CHECKING; error.value = ""
        try {
            val rel = withContext(Dispatchers.IO) { fetchLatest() }
            release.value = rel
            state.value = if (rel != null && isNewer(rel.version, currentVersion(ctx))) St.AVAILABLE else St.UPTODATE
        } catch (e: Exception) {
            error.value = e.message ?: e.javaClass.simpleName; state.value = St.ERROR
        }
    }

    // ---------------- Quay về phiên bản trước (rollback) ----------------
    // Giới hạn của Android: KHÔNG cho cài đè một APK có versionCode THẤP HƠN bản đang cài (INSTALL_FAILED_VERSION_DOWNGRADE).
    // Vì vậy rollback = tải APK bản cũ vào Download/GRAPE2Play (còn lại sau khi gỡ app) → người dùng gỡ bản hiện tại → cài file đó.
    // Gỡ app sẽ xoá dữ liệu của app (nguồn nhạc đã thêm, cài đặt, hồ sơ thiết bị); KHÔNG xoá file nhạc của bạn.
    val older = mutableStateOf<List<Release>>(emptyList())
    val olderBusy = mutableStateOf(false)
    val olderMsg = mutableStateOf("")
    val savedPath = mutableStateOf("")

    private fun parseOlder(cur: String): List<Release> {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "GRAPEPlay-Updater").build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val arr = org.json.JSONArray(r.body?.string().orEmpty())
            val all = ArrayList<Release>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                if (j.optBoolean("draft") || j.optBoolean("prerelease")) continue
                val tag = j.optString("tag_name").trim().trimStart { !it.isDigit() }
                val assets = j.optJSONArray("assets") ?: continue
                var url = ""; var size = 0L
                for (k in 0 until assets.length()) {
                    val a = assets.getJSONObject(k)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
                }
                if (tag.isNotEmpty() && url.isNotEmpty()) all.add(Release(tag, url, size, j.optString("body").trim()))
            }
            val keep = Versions.olderThan(cur, all.map { it.version }, 10)
            return keep.mapNotNull { v -> all.firstOrNull { Versions.compare(it.version, v) == 0 } }
        }
    }

    suspend fun loadOlder(ctx: Context) {
        if (olderBusy.value) return
        olderBusy.value = true; olderMsg.value = ""; savedPath.value = ""
        try {
            older.value = withContext(Dispatchers.IO) { parseOlder(currentVersion(ctx)) }
            if (older.value.isEmpty()) olderMsg.value = "Không có bản cũ hơn trên GitHub (cần Release công khai có file .apk)."
        } catch (e: Exception) { olderMsg.value = "Không lấy được danh sách: " + (e.message ?: e.javaClass.simpleName) }
        olderBusy.value = false
    }

    private fun saveToDownloads(ctx: Context, f: File): String {
        val rel = "Download/GRAPE2Play"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("Không tạo được file trong Download")
            ctx.contentResolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) } } ?: throw IOException("Không ghi được file vào Download")
        } else {
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "GRAPE2Play").apply { mkdirs() }
            f.copyTo(File(dir, f.name), overwrite = true)
        }
        return rel + "/" + f.name
    }

    /** Tải bản cũ (kiểm kích thước + đúng package như bản cập nhật) rồi lưu vào Download/GRAPE2Play. Trả về thông báo cho người dùng. */
    suspend fun downloadOlder(ctx: Context, rel: Release): String {
        if (state.value == St.DOWNLOADING) return "Đang tải bản khác, hãy chờ."
        val prev = state.value
        state.value = St.DOWNLOADING; progress.value = 0
        return try {
            val f = withContext(Dispatchers.IO) { download(ctx, rel) }
            val path = withContext(Dispatchers.IO) { saveToDownloads(ctx, f) }
            savedPath.value = path
            state.value = prev
            "Đã lưu bản ${rel.version} vào $path. Android không cho cài đè bản thấp hơn: hãy gỡ GRAPEPlay hiện tại rồi mở file này để cài."
        } catch (e: Exception) {
            AppLog.e("Update", "Tải bản cũ lỗi", e)
            state.value = prev
            "Lỗi tải bản ${rel.version}: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Mở hộp thoại gỡ cài đặt của Android cho chính app này. */
    fun uninstallSelf(ctx: Context): String? = try {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null
    } catch (e: Exception) { "Không mở được hộp thoại gỡ cài đặt. Hãy gỡ GRAPEPlay trong Cài đặt ▸ Ứng dụng." }

    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Tự kiểm tra một lần mỗi lần mở app, chạy ở phạm vi toàn app (không bị huỷ khi đổi màn hình).
     * TV thường vừa mở app là mạng chưa sẵn sàng (Wi-Fi còn đang nối lại) nên lần đầu hay lỗi: thử lại sau 5 s, 20 s, 60 s thay vì bỏ qua cả phiên.
     */
    fun autoCheck(ctx: Context) {
        if (autoDone || state.value != St.IDLE) return
        autoDone = true
        val app = ctx.applicationContext
        bg.launch {
            val waits = longArrayOf(5_000, 20_000, 60_000)
            for (n in 0..waits.size) {
                check(app)
                if (state.value != St.ERROR) break
                AppLog.w("Update", "Kiểm tra cập nhật lỗi (lần ${n + 1}): ${error.value}")
                state.value = St.IDLE; error.value = ""
                if (n < waits.size) delay(waits[n])
            }
            if (state.value == St.AVAILABLE) { AppLog.i("Update", "Có bản mới ${release.value?.version}"); prompt.value = true }
        }
    }

    /** Ghi chú phát hành (Markdown) → các dòng tính năng gọn: bỏ #, **, gạch đầu dòng; tối đa [max] dòng. */
    fun featureLines(notes: String, max: Int = 8): List<String> =
        notes.lines().map { it.trim().trimStart('#', '-', '*', '+', '•', ' ').replace("**", "").replace("`", "").trim() }
            .filter { it.isNotEmpty() }.take(max)

    private fun download(ctx: Context, rel: Release): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "GRAPEPlay-${rel.version}.apk")
        val c = Http.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        c.newCall(Request.Builder().url(rel.url).header("User-Agent", "GRAPEPlay-Updater").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: rel.size
            body.byteStream().use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        os.write(buf, 0, n); done += n
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; progress.value = p } }
                    }
                }
            }
        }
        if (rel.size > 0 && out.length() != rel.size) { out.delete(); throw IOException("File tải về bị thiếu dữ liệu") }
        val info = ctx.packageManager.getPackageArchiveInfo(out.path, 0)
        if (info == null || info.packageName != ctx.packageName) { out.delete(); throw IOException("File tải về không phải bản cài của GRAPEPlay") }
        return out
    }

    /** Tải APK rồi mở trình cài đặt. Trả về thông báo lỗi/hướng dẫn nếu cần, null nếu ổn. */
    suspend fun downloadAndInstall(ctx: Context): String? {
        val rel = release.value ?: return null
        state.value = St.DOWNLOADING; progress.value = 0; error.value = ""
        return try {
            apk = withContext(Dispatchers.IO) { download(ctx, rel) }
            state.value = St.READY
            install(ctx)
        } catch (e: Exception) {
            AppLog.e("Update", "Tải/cài bản cập nhật lỗi", e)
            error.value = e.message ?: e.javaClass.simpleName; state.value = St.ERROR; null
        }
    }

    fun install(ctx: Context): String? {
        val f = apk?.takeIf { it.exists() } ?: return "Chưa có file cập nhật, hãy tải lại."
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { ctx.startActivity(i); "Hãy bật “Cho phép từ nguồn này” cho GRAPEPlay, quay lại rồi bấm Cài đặt ngay." }
            catch (e: Exception) { "TV chưa cho phép cài từ nguồn ngoài. Vào Cài đặt TV ▸ Bảo mật và hạn chế ▸ Nguồn không xác định, bật cho GRAPEPlay." }
        }
        return try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (e: Exception) { "Không mở được trình cài đặt: ${e.message}" }
    }
}

private fun mb(n: Long) = "%.1f MB".format(n / 1048576.0)

@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Updater.state
    val rel by Updater.release
    val pct by Updater.progress
    val err by Updater.error
    var msg by remember { mutableStateOf("") }
    var rbOpen by remember { mutableStateOf(false) }
    val cur = remember { Updater.currentVersion(ctx) }
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }

    @Composable fun Btn(text: String, onClick: () -> Unit) =
        Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp)) { T(text, 18, C.Gold, Modifier.padding(14.dp, 9.dp), bold = true) }

    Card {
        T("CẬP NHẬT ỨNG DỤNG", 13, C.Muted, spaced = true)
        T("Đang dùng bản: $cur", 18, C.Text)
        when (st) {
            Updater.St.IDLE -> T("Chưa kiểm tra cập nhật.", 15, C.Muted)
            Updater.St.CHECKING -> T("Đang kiểm tra cập nhật…", 15, C.Muted)
            Updater.St.UPTODATE -> {
                val r = rel
                if (r != null) {
                    T("✓ Bạn đang dùng bản mới nhất.", 17, Grade.GREEN.color)
                    T("Bản mới nhất trên GitHub: ${r.version}", 14, C.Muted)
                } else T("Chưa thấy bản phát hành hợp lệ trên GitHub (cần Release công khai, có đính kèm file .apk).", 15, C.Gold, lines = 3)
            }
            Updater.St.AVAILABLE -> {
                val r = rel
                if (r != null) {
                    T("Có bản mới: ${r.version}" + (if (r.size > 0) " · ${mb(r.size)}" else ""), 18, C.Gold, bold = true)
                    if (r.notes.isNotEmpty()) T(r.notes.lines().filter { it.isNotBlank() }.take(4).joinToString("\n"), 14, C.Muted, lines = 4)
                }
            }
            Updater.St.DOWNLOADING -> T("Đang tải… $pct%", 17, C.Gold)
            Updater.St.READY -> T("✓ Đã tải xong, sẵn sàng cài đặt.", 17, Grade.GREEN.color)
            Updater.St.ERROR -> T("⚠ Lỗi: $err", 15, C.Gold, lines = 3)
        }
        if (msg.isNotEmpty()) T(msg, 14, C.Gold, lines = 3)
        when (st) {
            Updater.St.AVAILABLE -> Btn("Tải và cài đặt") { msg = ""; scope.launch { msg = Updater.downloadAndInstall(ctx).orEmpty() } }
            Updater.St.READY -> Btn("Cài đặt ngay") { msg = Updater.install(ctx).orEmpty() }
            Updater.St.CHECKING, Updater.St.DOWNLOADING -> {}
            else -> Btn("Kiểm tra cập nhật") { msg = ""; scope.launch { Updater.check(ctx) } }
        }
        T("Cập nhật được tải từ trang phát hành GitHub của GRAPEPlay. Bản mới sẽ cài đè, không mất nguồn nhạc và cài đặt.", 13, C.Muted, lines = 3)
        Btn("Quay về phiên bản trước") { rbOpen = true; scope.launch { Updater.loadOlder(ctx) } }
    }
    if (rbOpen) RollbackDialog(onClose = { rbOpen = false })
}

/** Cửa sổ thông báo khi có bản mới: tự hiện sau khi mở app. Nút "Để sau" đóng cửa sổ (lần mở app sau sẽ nhắc lại). */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prompt by Updater.prompt
    val st by Updater.state
    val rel by Updater.release
    val pct by Updater.progress
    val err by Updater.error
    var msg by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val cur = remember { Updater.currentVersion(ctx) }
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }
    val r = rel
    if (!prompt || r == null || st == Updater.St.UPTODATE || st == Updater.St.IDLE || st == Updater.St.CHECKING) return
    val compact = LocalCompact.current
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }   // chờ cửa sổ dựng xong rồi mới nhận tiêu điểm D-pad
    val big = compact
    fun f(n: Int) = if (big) n else (n * 0.62f).toInt().coerceAtLeast(10)
    Dialog(onDismissRequest = { if (st != Updater.St.DOWNLOADING) Updater.prompt.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            (if (compact) Modifier.dlgW(520) else Modifier.dlgFrac(0.4f)).clip(RoundedCornerShape(14.dp)).background(C.Dlg).border(1.dp, C.Line, RoundedCornerShape(14.dp)).dlgPad(26, 18),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("CẬP NHẬT ỨNG DỤNG", f(13), C.Muted, spaced = true)
            T("Có phiên bản mới ${r.version}", f(30), serif = true, bold = true, lines = 2)
            T("Bạn đang dùng bản $cur" + (if (r.size > 0) " · dung lượng ${mb(r.size)}" else ""), f(17), C.Muted, lines = 2)
            val feats = remember(r.notes) { Updater.featureLines(r.notes) }
            T("TÍNH NĂNG / THAY ĐỔI TRONG BẢN NÀY", f(12), C.Muted, spaced = true)
            if (feats.isEmpty()) T("Chưa có mô tả thay đổi cho bản này.", f(16), C.Muted, lines = 2)
            else for (l in feats) T("•  $l", f(16), C.Text, lines = 2)
            when (st) {
                Updater.St.DOWNLOADING -> T("Đang tải… $pct%", f(18), C.Gold, bold = true)
                Updater.St.READY -> T("✓ Đã tải xong, sẵn sàng cài đặt.", f(17), Grade.GREEN.color)
                Updater.St.ERROR -> T("⚠ Lỗi: $err", f(15), C.Gold, lines = 3)
                else -> {}
            }
            if (msg.isNotEmpty()) T(msg, f(15), C.Gold, lines = 4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.End)) {
                if (st != Updater.St.DOWNLOADING) Focusable({ Updater.prompt.value = false }) { T("Để sau", f(18), C.Muted, Modifier.padding(if (big) 22.dp else 14.dp, if (big) 10.dp else 6.dp), bold = true) }
                if (st != Updater.St.DOWNLOADING) Focusable({
                    msg = ""
                    if (st == Updater.St.READY) msg = Updater.install(ctx).orEmpty()
                    else scope.launch { msg = Updater.downloadAndInstall(ctx).orEmpty() }
                }, requester = first) {
                    T(if (st == Updater.St.READY) "Cài đặt ngay" else if (st == Updater.St.ERROR) "Thử lại" else "Cập nhật ngay", f(18), C.Gold, Modifier.padding(if (big) 22.dp else 14.dp, if (big) 10.dp else 6.dp), bold = true)
                }
            }
        }
    }
}

/** Quay về phiên bản trước: chọn bản cũ → tải vào Download/GRAPE2Play → (tuỳ chọn) gỡ bản hiện tại. Nói rõ giới hạn của Android và rủi ro mất dữ liệu app. */
@Composable
fun RollbackDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by Updater.older
    val busy by Updater.olderBusy
    val listMsg by Updater.olderMsg
    val st by Updater.state
    val pct by Updater.progress
    val saved by Updater.savedPath
    var chosen by remember { mutableStateOf<Updater.Release?>(null) }
    var msg by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val compact = LocalCompact.current
    LaunchedEffect(busy) { delay(150); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = { if (st != Updater.St.DOWNLOADING) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            (if (compact) Modifier.dlgW(560) else Modifier.dlgFrac(0.45f)).clip(RoundedCornerShape(14.dp)).background(C.Dlg).border(1.dp, C.Line, RoundedCornerShape(14.dp)).dlgPad(26, 18),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("QUAY VỀ PHIÊN BẢN TRƯỚC", 13, C.Muted, spaced = true)
            T("Đang dùng bản ${Updater.currentVersion(ctx)}", 18, C.Text)
            T("Android không cho cài đè bản có versionCode thấp hơn. Để quay về, app tải bản cũ vào thư mục Download/GRAPE2Play, sau đó bạn gỡ GRAPEPlay hiện tại rồi cài file đó. " +
                "Gỡ app sẽ XOÁ dữ liệu của app (nguồn nhạc đã thêm, cài đặt, hồ sơ thiết bị Bit-perfect); file nhạc của bạn không bị xoá. Hãy ghi lại các nguồn nhạc trước khi làm.", 13, C.Gold, lines = 8)
            if (busy) T("Đang lấy danh sách…", 15, C.Muted)
            else if (list.isEmpty()) T(listMsg.ifEmpty { "Không có bản cũ hơn." }, 15, C.Muted, lines = 3)
            else {
                T("CHỌN BẢN CẦN QUAY VỀ", 12, C.Muted, spaced = true)
                list.forEachIndexed { i, r ->
                    Focusable({ chosen = r; msg = "" }, if (i == 0) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(), selected = chosen === r, requester = if (i == 0) first else null) {
                        T((if (chosen === r) "● " else "○ ") + r.version + (if (r.size > 0) " · ${mb(r.size)}" else ""), 17, if (chosen === r) C.Gold else C.Text, Modifier.padding(14.dp, 8.dp))
                    }
                }
            }
            if (st == Updater.St.DOWNLOADING) T("Đang tải… $pct%", 17, C.Gold, bold = true)
            if (msg.isNotEmpty()) T(msg, 14, C.Gold, lines = 6)
            val c = chosen
            if (c != null && st != Updater.St.DOWNLOADING)
                Focusable({ msg = ""; scope.launch { msg = Updater.downloadOlder(ctx, c) } }) { T("Tải bản ${c.version} vào Download", 16, C.Gold, Modifier.padding(14.dp, 8.dp), bold = true) }
            if (saved.isNotEmpty() && st != Updater.St.DOWNLOADING)
                Focusable({ msg = Updater.uninstallSelf(ctx).orEmpty() }) { T("Gỡ GRAPEPlay hiện tại (mất dữ liệu app)", 15, C.Muted, Modifier.padding(14.dp, 8.dp)) }
            if (st != Updater.St.DOWNLOADING) Focusable({ onClose() }) { T("Đóng", 16, C.Muted, Modifier.padding(14.dp, 8.dp), bold = true) }
        }
    }
}
