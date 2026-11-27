package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Playlist do người dùng/AI tạo (lưu đường dẫn bài). */
object UserPlaylists {
    class P(val name: String, val paths: List<String>, val at: Long)
    val list = mutableStateListOf<P>()
    private lateinit var store: SafePrefStore

    /** Dữ liệu hỏng không bao giờ bị biến thành danh sách rỗng rồi ghi đè: xem [SafeJsonStore]. */
    @Synchronized fun init(ctx: Context) {
        if (::store.isInitialized) return
        store = SafePrefStore(ctx.applicationContext, "aiplaylists", PersistCodec::isPlaylists, "Playlists")
        PersistCodec.decodePlaylists(store.load().value ?: "[]")?.forEach { list.add(P(it.name, it.paths, it.at)) }
    }

    @Synchronized fun add(name: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        list.add(0, P(name.trim().ifEmpty { "Playlist" }.take(60), tracks.map { it.path }, System.currentTimeMillis())); save()
    }
    @Synchronized fun remove(p: P) { list.remove(p); save() }
    fun tracks(p: P, lib: List<Track>): List<Track> { val m = lib.associateBy { it.path }; return p.paths.mapNotNull { m[it] } }

    @Synchronized private fun save() {
        if (!::store.isInitialized) return
        store.writeAsync(PersistCodec.encodePlaylists(list.map { PersistCodec.Playlist(it.name, it.paths, it.at) }))
    }
}

/** Sửa tên nghệ sĩ/album chỉ trong hiển thị (file thật không bị đổi). */
object TagFix {
    val artist = HashMap<String, String>()
    val album = HashMap<String, String>()
    private lateinit var safe: SafePrefStore

    /** Sửa tag do người dùng nhập: dữ liệu hỏng không bao giờ bị biến thành rỗng rồi ghi đè: xem [SafeJsonStore]. */
    @Synchronized fun init(ctx: Context) {
        if (::safe.isInitialized) return
        safe = SafePrefStore(ctx.applicationContext, "tagfix", PersistCodec::isTagFix, "TagFix")
        safe.load().value?.let { PersistCodec.decodeTagFix(it) }?.let { m -> artist.putAll(m.getValue("a")); album.putAll(m.getValue("b")) }
    }

    val count: Int get() = artist.size + album.size

    fun apply(l: List<Track>): List<Track> {
        if (artist.isEmpty() && album.isEmpty()) return l
        return l.map { t ->
            val a = artist[t.artist]; val b = album[t.album]
            if (a == null && b == null) t else t.copy(artist = a ?: t.artist, album = b ?: t.album)
        }
    }

    @Synchronized fun put(kind: String, from: List<String>, to: String) {
        val m = if (kind == "artist") artist else album
        from.forEach { if (it != to) m[it] = to }
    }
    @Synchronized fun clear() { artist.clear(); album.clear(); save() }
    @Synchronized fun save() {
        if (!::safe.isInitialized) return
        safe.writeAsync(PersistCodec.encodeTagFix(artist, album))
    }
}

/** Đọc lời bài hát: file .lrc/.txt cùng tên, hoặc lời nhúng trong tag (MP3 USLT, FLAC LYRICS). Không có thì trả null — không bao giờ để AI tự bịa lời. */
object Lyrics {
    private val lrcTag = Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
    private val lrcMeta = Regex("^\\[[A-Za-z]+:.*]$")

    fun clean(raw: String): String = raw.replace("\r", "").lines()
        .filter { !lrcMeta.matches(it.trim()) }
        .map { lrcTag.replace(it, "").trim() }
        .joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()

    fun load(t: Track): String? {
        if (!t.path.startsWith("/")) return null
        return runCatching { sidecar(t.path) ?: embedded(t.path) }.getOrNull()?.let { clean(it) }?.takeIf { it.length > 8 }
    }

    private fun sidecar(path: String): String? {
        val base = path.substringBeforeLast('.', path)
        for (e in listOf("lrc", "LRC", "txt", "TXT")) {
            val f = File("$base.$e")
            if (f.isFile && f.length() in 1..400_000) return f.readText(Charsets.UTF_8)
        }
        return null
    }

    private fun embedded(path: String): String? = when (path.substringAfterLast('.').lowercase()) {
        "mp3" -> id3(path)
        "flac" -> flac(path)
        else -> null
    }

    private fun synch(b: ByteArray, o: Int) = (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)
    private fun be32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun id3(path: String): String? {
        RandomAccessFile(path, "r").use { f ->
            val h = ByteArray(10); f.readFully(h)
            if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return null
            val ver = h[3].toInt()
            val size = synch(h, 6).coerceAtMost(6_000_000)
            val b = ByteArray(size); f.readFully(b)
            var p = 0
            while (p + 10 <= size) {
                if (b[p].toInt() == 0) break
                val id = String(b, p, 4, Charsets.ISO_8859_1)
                val fs = if (ver >= 4) synch(b, p + 4) else be32(b, p + 4)
                if (fs <= 0 || p + 10 + fs > size) break
                if (id == "USLT") return uslt(b, p + 10, fs)
                p += 10 + fs
            }
        }
        return null
    }

    private fun uslt(b: ByteArray, o: Int, n: Int): String? {
        if (n < 5) return null
        val enc = b[o].toInt()
        var p = o + 4          // bỏ mã hoá + 3 byte ngôn ngữ
        val end = o + n
        val wide = enc == 1 || enc == 2
        // bỏ phần mô tả (kết thúc bằng null)
        if (wide) { while (p + 1 < end && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2; p += 2 }
        else { while (p < end && b[p].toInt() != 0) p++; p++ }
        if (p >= end) return null
        val cs: Charset = when (enc) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
        return String(b, p, end - p, cs)
    }

    private fun flac(path: String): String? {
        RandomAccessFile(path, "r").use { f ->
            val m = ByteArray(4); f.readFully(m)
            if (String(m, Charsets.ISO_8859_1) != "fLaC") return null
            while (true) {
                val hd = f.read(); if (hd < 0) return null
                val len = (f.read() shl 16) or (f.read() shl 8) or f.read()
                if ((hd and 0x7F) == 4 && len in 1..3_000_000) {
                    val b = ByteArray(len); f.readFully(b)
                    fun le(o: Int) = (b[o].toInt() and 0xFF) or (b[o + 1].toInt() and 0xFF shl 8) or (b[o + 2].toInt() and 0xFF shl 16) or (b[o + 3].toInt() and 0xFF shl 24)
                    var p = 4 + le(0)
                    val cnt = le(p); p += 4
                    for (i in 0 until cnt) {
                        if (p + 4 > len) break
                        val l = le(p); p += 4
                        if (l < 0 || p + l > len) break
                        val s = String(b, p, l, Charsets.UTF_8); p += l
                        val k = s.substringBefore('=').uppercase()
                        if (k == "LYRICS" || k == "UNSYNCEDLYRICS" || k == "UNSYNCED LYRICS") return s.substringAfter('=')
                    }
                    return null
                }
                if ((hd and 0x80) != 0) return null
                f.seek(f.filePointer + len)
            }
        }
        return null
    }
}

/** Chế độ AI DJ: khi danh sách đang phát sắp hết, tự thêm bài tiếp theo hợp mạch. */
object AiDj {
    val on = mutableStateOf(false)
    val note = mutableStateOf("")
    @Volatile var busy = false
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("aidj", Context.MODE_PRIVATE)
        on.value = sp.getBoolean("on", false)
    }
    fun set(v: Boolean) { on.value = v; if (::sp.isInitialized) sp.edit().putBoolean("on", v).apply() }
}
