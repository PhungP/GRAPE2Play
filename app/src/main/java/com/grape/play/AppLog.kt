package com.grape.play

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Nhật ký sử dụng để người dùng gửi cho nhà phát triển khi gặp lỗi.
 *  - Ghi vào file trong bộ nhớ riêng của app (xoay vòng 2 file × tối đa 384 KB, tự xoá cũ), giữ thêm 300 dòng gần nhất trong RAM để xem nhanh.
 *  - Bắt luôn lỗi làm app dừng đột ngột (crash) kèm dòng gọi hàm, để lần mở app sau vẫn còn bằng chứng.
 *  - Không ghi mật khẩu/token: URL được lọc bớt phần user:pass và tham số truy vấn. Có ghi phần cuối đường dẫn file bị lỗi để dễ tìm nguyên nhân.
 *  - Xuất ra: chia sẻ (điện thoại), hoặc mở bằng trình duyệt của máy khác qua mạng LAN (TV không có sẵn ứng dụng chia sẻ).
 */
object AppLog {
    const val DEV_EMAIL = "grape2play@gmail.com"
    private const val MAX_FILE = 384 * 1024
    private const val RAM_LINES = 300

    private lateinit var app: Context
    private lateinit var dir: File
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "grape-log").also { it.isDaemon = true } }
    private val ram = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val bootAt = SystemClock.elapsedRealtime()

    /** Cho phép máy khác trong mạng LAN đọc nhật ký qua http://IP:cổng/log. Chỉ bật khi cửa sổ Nhật ký đang mở. */
    @Volatile var lanShare = false

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        dir = File(app.cacheDir, "logs").apply { mkdirs() }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            runCatching { writeNow("CRASH", "FATAL trên luồng ${th.name}\n" + Log.getStackTraceString(e)) }
            prev?.uncaughtException(th, e)
        }
        i("App", "Khởi động ${header().lineSequence().first()}")
    }

    /** Chọn chuỗi theo ngôn ngữ giao diện hiện tại. */
    private fun s(vi: String, en: String) = if (Lang.en.value) en else vi

    fun i(tag: String, msg: String) = add("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = add("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = add("E", tag, msg, t)

    /** Rút gọn URL: bỏ user:pass và tham số truy vấn (có thể chứa token). */
    fun safe(s: String): String = s.replace(Regex("(https?://)[^/@\\s]+@"), "$1***@").replace(Regex("\\?[^\\s\"']*"), "?…")

    private fun add(level: String, tag: String, msg: String, t: Throwable?) {
        val body = safe(msg) + (if (t != null) "\n" + Log.getStackTraceString(t).trim().lines().take(12).joinToString("\n") else "")
        when (level) { "E" -> Log.e("GRAPEPlay", "$tag: $body"); "W" -> Log.w("GRAPEPlay", "$tag: $body"); else -> Log.i("GRAPEPlay", "$tag: $body") }
        if (!::dir.isInitialized) return
        val line = "${stamp.format(Date())} $level/$tag: $body"
        synchronized(ram) { ram.addLast(line); while (ram.size > RAM_LINES) ram.removeFirst() }
        io.execute { runCatching { append(line) } }
    }

    private fun writeNow(tag: String, msg: String) {
        val line = "${stamp.format(Date())} E/$tag: $msg"
        synchronized(ram) { ram.addLast(line) }
        append(line)
    }

    private fun cur() = File(dir, "grape.log")
    private fun old() = File(dir, "grape.old.log")

    @Synchronized private fun append(line: String) {
        val f = cur()
        if (f.length() > MAX_FILE) { old().delete(); f.renameTo(old()) }
        f.appendText(line + "\n")
    }

    /** Thông tin máy + cài đặt âm thanh hiện tại: dòng đầu tiên dùng làm tiêu đề. */
    fun header(): String {
        val v = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        val sb = StringBuilder()
        sb.append("GRAPE2Play $v · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n")
        val up = (SystemClock.elapsedRealtime() - bootAt) / 1000
        sb.append(s("Chế độ: ${if (Device.tv) "TV" else "Điện thoại/máy tính bảng"} · ngôn ngữ ${if (Lang.en.value) "EN" else "VI"} · chạy $up giây",
            "Mode: ${if (Device.tv) "TV" else "Phone/tablet"} · language ${if (Lang.en.value) "EN" else "VI"} · running for $up s")).append("\n")
        runCatching {
            val dop = AudioSettings.dsdRemote == AudioSettings.DSD_DOP
            sb.append(s("Đầu ra: ${Playback.outName.value} · phát tới: ${Playback.remote.value?.label ?: "chính thiết bị này"}",
                "Output: ${Playback.outName.value} · playing to: ${Playback.remote.value?.label ?: "this device"}")).append("\n")
            sb.append(s("DSD: tần số ${AudioSettings.dsdRate} · gửi thiết bị ngoài ${if (dop) "DoP" else "file gốc"} · hi-res ${AudioSettings.hires} · gapless ${AudioSettings.gapless}",
                "DSD: rate ${AudioSettings.dsdRate} · sent to external devices as ${if (dop) "DoP" else "original file"} · hi-res ${AudioSettings.hires} · gapless ${AudioSettings.gapless}")).append("\n")
            val t = Playback.queue.value.getOrNull(Playback.index.value)
            if (t != null) sb.append(s("Bài đang chọn: ${t.ext} ${t.bits}-bit/${t.sr} Hz · nguồn ${t.sourceName.substringBefore(" · ")}",
                "Selected track: ${t.ext} ${t.bits}-bit/${t.sr} Hz · source ${t.sourceName.substringBefore(" · ")}")).append("\n")
        }
        return sb.toString().trimEnd()
    }

    /** Toàn bộ nhật ký (file cũ + file hiện tại) có tiêu đề thông tin máy. */
    fun text(): String {
        if (!::dir.isInitialized) return ""
        io.submit {}.get()   // đợi các dòng đang xếp hàng ghi xong
        val sb = StringBuilder(header()).append("\n").append("—".repeat(40)).append("\n")
        val body = StringBuilder()
        runCatching { if (old().exists()) body.append(old().readText()) }
        runCatching { if (cur().exists()) body.append(cur().readText()) }
        // Nhật ký luôn được ghi bằng tiếng Việt; giao diện English thì dịch khi xuất (file gửi đi, trang xem qua LAN)
        sb.append(if (Lang.en.value) LogLang.text(body.toString()) else body.toString())
        return sb.toString()
    }

    /** Vài chục dòng gần nhất để hiện trong cửa sổ. */
    fun tail(n: Int = 40): List<String> = synchronized(ram) { ram.toList().takeLast(n) }.let { l -> if (Lang.en.value) l.map { LogLang.line(it) } else l }

    fun clear() {
        synchronized(ram) { ram.clear() }
        io.execute { runCatching { cur().delete(); old().delete() } }
        i("App", "Đã xoá nhật ký")
    }

    /** Ghi nhật ký ra file tạm để đính kèm email/chia sẻ. */
    fun exportFile(): File {
        val f = File(dir, "GRAPE2Play-log.txt")
        f.writeText(text())
        return f
    }

    /** Mở danh sách ứng dụng chia sẻ (email…) với file nhật ký đính kèm. Trả về thông báo lỗi nếu máy không có ứng dụng nào xử lý được (thường gặp trên TV). */
    fun share(ctx: Context): String? = try {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", exportFile())
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, s("GRAPE2Play – nhật ký lỗi", "GRAPE2Play – error log"))
            .putExtra(Intent.EXTRA_TEXT, s("Mô tả lỗi (bạn đang làm gì, thiết bị nào, bài/định dạng nào):", "Describe the problem (what you were doing, which device, which track/format):") + "\n\n")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val ch = Intent.createChooser(send, s("Gửi nhật ký cho nhà phát triển", "Send the log to the developer")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (send.resolveActivity(ctx.packageManager) == null) s("Thiết bị này không có ứng dụng gửi thư/chia sẻ. Hãy dùng cách mở bằng trình duyệt trên điện thoại.", "This device has no email/share app. Open the log in a browser on your phone instead.")
        else { ctx.startActivity(ch); null }
    } catch (e: Exception) { s("Không mở được chia sẻ: ${e.message}", "Could not open sharing: ${e.message}") }
}
