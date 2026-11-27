package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaFormat
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Đọc thông số kỹ thuật thật của file (FLAC/WAV đọc từ header, còn lại qua MediaExtractor). Hỗ trợ cả file trên NAS. */
object Probe {
    private fun le16(b: ByteArray, p: Int) = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, p: Int) = le16(b, p) or (le16(b, p + 2) shl 16)

    fun probe(t: Track): AudioInfo {
        if (t.ext == "dsf" || t.ext == "dff") {
            if (t.sr > 0) return AudioInfo(t.sr, 1, t.ch.coerceAtLeast(2), t.kbps)
            runCatching { val rd = Remote.reader(t.path); try { val m = DsdPcm.readMeta(ReaderSource(rd)); return AudioInfo(m.rate, 1, m.channels, 0) } finally { rd.close() } }
        }
        var sr = t.sr; var ch = t.ch; var bits = t.bits; var size = -1L
        // Luôn mở file một lần: lấy dung lượng (tính bitrate thật) và đọc header FLAC/WAV nếu còn thiếu thông số (DLNA thường không báo độ sâu bit).
        runCatching {
            val rd = Remote.reader(t.path)
            try {
                size = rd.size
                if (sr == 0 || ch == 0 || bits == 0) {
                    val b = ByteArray(4096); val n = rd.read(0, b, 0, b.size)
                    fun u(i: Int) = b[i].toInt() and 0xff
                    if (n >= 42 && String(b, 0, 4) == "fLaC") {
                        sr = (u(18) shl 12) or (u(19) shl 4) or (u(20) shr 4)
                        ch = ((u(20) shr 1) and 7) + 1
                        bits = (((u(20) and 1) shl 4) or (u(21) shr 4)) + 1
                    } else if (n >= 44 && String(b, 0, 4) == "RIFF") {
                        var p = 12
                        while (p + 8 <= n) {
                            val id = String(b, p, 4); val sz = le32(b, p + 4)
                            if (id == "fmt " && p + 24 <= n) { ch = le16(b, p + 10); sr = le32(b, p + 12); bits = le16(b, p + 22); break }
                            if (sz < 0) break
                            p += 8 + sz + (sz and 1)
                        }
                    }
                }
            } finally { rd.close() }
        }
        if (sr == 0 || ch == 0) runCatching {
            Media.withExtractor(t.path) { ex ->
                val fm = ex.getTrackFormat(0)
                sr = fm.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = fm.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        // Bitrate tính từ dung lượng/thời lượng; bỏ qua số liệu vô lý (lớn hơn PCM thô) mà một số máy chủ DLNA báo sai.
        val rawKbps = if (sr > 0 && bits > 0 && ch > 0) (sr.toLong() * bits * ch / 1000 * 102 / 100).toInt() else Int.MAX_VALUE
        val fromSize = if (size > 0 && t.durationMs > 0) (size * 8 / (t.durationMs / 1000.0) / 1000).toInt() else 0
        val kbps = when {
            fromSize in 1..rawKbps -> fromSize
            t.kbps in 1..rawKbps -> t.kbps
            else -> 0
        }
        return AudioInfo(sr, bits, ch, kbps)
    }
}

/** Ảnh bìa: ảnh từ DLNA, ảnh nhúng trong file, hoặc cover/folder/front.jpg|png cùng thư mục (cả trên NAS). */
object Covers {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val none = ConcurrentHashMap.newKeySet<String>()
    private const val SIZE = 720
    private val names = listOf("cover.jpg", "folder.jpg", "front.jpg", "cover.png", "folder.png", "front.png")

    // Ảnh bìa KHÔNG còn được tự động tải từ mạng. Nguồn ảnh: DLNA, nhúng trong file, thư mục, hoặc ảnh người dùng đã xác nhận (qua "Chọn ảnh bìa bằng AI").
    private lateinit var userDir: File   // ảnh bìa người dùng đã xác nhận (ưu tiên cao nhất, chỉ dùng để hiển thị trong app)

    fun init(ctx: Context) {
        if (::userDir.isInitialized) return
        val app = ctx.applicationContext
        userDir = File(app.filesDir, "usercovers").apply { mkdirs() }
        // Dọn bộ nhớ đệm ảnh tải tự động của các phiên bản cũ (chỉ là cache app tự tải, không phải dữ liệu người dùng; ảnh người dùng xác nhận nằm ở "usercovers" và được giữ nguyên).
        runCatching { File(app.filesDir, "webcovers").deleteRecursively() }
        runCatching { app.getSharedPreferences("covers", Context.MODE_PRIVATE).edit().remove("web").apply() }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun sample(o: BitmapFactory.Options): Int { var s = 1; while (o.outWidth / (s * 2) >= SIZE && o.outHeight / (s * 2) >= SIZE) s *= 2; return s }
    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample(o) })
    }

    private fun readAll(path: String): ByteArray? {
        val rd = Remote.reader(path)
        try {
            val sz = rd.size
            if (sz <= 0 || sz > 8_000_000) return null
            val b = ByteArray(sz.toInt()); var got = 0
            while (got < b.size) { val n = rd.read(got.toLong(), b, got, b.size - got); if (n <= 0) break; got += n }
            return if (got == b.size) b else null
        } finally { rd.close() }
    }

    private fun fromArt(t: Track): Bitmap? = if (t.art.isEmpty()) null else runCatching {
        Http.client.newCall(Request.Builder().url(t.art).build()).execute().use { r -> r.body?.bytes()?.let { decode(it) } }
    }.getOrNull()

    private fun fromFlacPicture(t: Track): Bitmap? = if (t.ext != "flac") null else runCatching {
        val rd = Remote.reader(t.path)
        try { FlacPcm.readMeta(ReaderSource(rd), true).picture?.let { decode(it) } } finally { rd.close() }
    }.getOrNull()

    private fun fromEmbedded(t: Track): Bitmap? = runCatching { Media.withMmr(t.path) { r -> r.embeddedPicture?.let { decode(it) } } }.getOrNull()

    private fun fromFolder(t: Track): Bitmap? {
        if (t.path.startsWith("http") || (t.dir.isNotEmpty() && !t.path.startsWith("/"))) return null
        for (n in names) { val b = runCatching { readAll(t.folder + "/" + n) }.getOrNull(); if (b != null) decode(b)?.let { return it } }
        return null
    }

    // ---- Ảnh bìa do người dùng chọn và xác nhận (qua "Chọn ảnh bìa bằng AI"): chỉ lưu trong app, KHÔNG ghi vào file nhạc ----
    private fun userId(t: Track): String {
        val base = if (norm(t.album).isEmpty()) "f|" + t.folder else norm(t.artist) + "|" + norm(t.album)
        return "%08x%08x".format(base.hashCode(), base.reversed().hashCode())
    }
    private fun invalidate(t: Track) { val key = t.src + "|" + t.folder + "|" + t.album; cache.remove(key); none.remove(key) }
    fun hasUser(t: Track): Boolean = ::userDir.isInitialized && File(userDir, userId(t) + ".jpg").exists()
    fun setUser(t: Track, bytes: ByteArray): Boolean {
        if (!::userDir.isInitialized || decode(bytes) == null) return false
        return runCatching { File(userDir, userId(t) + ".jpg").writeBytes(bytes); invalidate(t); true }.getOrDefault(false)
    }
    fun clearUser(t: Track) { if (::userDir.isInitialized) { runCatching { File(userDir, userId(t) + ".jpg").delete() }; invalidate(t) } }
    private fun fromUser(t: Track): Bitmap? {
        if (!::userDir.isInitialized) return null
        val f = File(userDir, userId(t) + ".jpg")
        return if (f.exists()) runCatching { decode(f.readBytes()) }.getOrNull() else null
    }
    /** Giải mã ảnh để xem trước (dùng chung cho cửa sổ chọn ảnh bìa). */
    internal fun preview(bytes: ByteArray): Bitmap? = runCatching { decode(bytes) }.getOrNull()

    suspend fun get(t: Track): Bitmap? = withContext(Dispatchers.IO) {
        val key = t.src + "|" + t.folder + "|" + t.album
        cache.get(key)?.let { return@withContext it }
        if (key in none) return@withContext null
        val bmp = fromUser(t) ?: fromArt(t) ?: fromFlacPicture(t) ?: fromEmbedded(t) ?: fromFolder(t)
        if (bmp != null) cache.put(key, bmp) else none.add(key)
        bmp
    }
}
