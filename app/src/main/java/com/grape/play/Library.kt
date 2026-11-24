package com.grape.play

import android.content.Context
import android.media.MediaMetadataRetriever as MMR
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object Library {
    /** Tăng khi thêm trường metadata mới → bản ghi trong cache cũ được đọc lại một lần (nếu đọc lỗi vẫn giữ thẻ cũ). */
    const val META_VER = 2
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val scanning = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lists = HashMap<String, List<Track>>()
    private val active = AtomicInteger(0)
    private val localBusy = AtomicBoolean(false)
    private val cancelFlag = AtomicBoolean(false)
    /** Dừng lần quét NAS/DLNA/đám mây đang chạy (giữ lại những gì đã tìm được). */
    fun cancelScan() { cancelFlag.set(true) }

    private fun begin() { active.incrementAndGet(); scanning.value = true }
    private fun end() { if (active.decrementAndGet() <= 0) scanning.value = false }
    @Synchronized private fun publish(id: String, l: List<Track>) { lists[id] = l; tracks.value = TagFix.apply(lists.values.flatten()) }
    @Synchronized private fun drop(id: String) { lists.remove(id); tracks.value = TagFix.apply(lists.values.flatten()) }
    /** Áp lại các sửa tên nghệ sĩ/album (hoặc hoàn tác) lên toàn thư viện. */
    @Synchronized fun reapply() { tracks.value = TagFix.apply(lists.values.flatten()) }
    @Synchronized private fun cached(id: String): List<Track> = lists[id].orEmpty()
    private fun cacheFile(ctx: Context, id: String) = File(ctx.filesDir, if (id == "local") "library_v4.json" else "src_$id.json")
    private fun bakFile(f: File) = File(f.path + ".bak")

    /** Đọc cache; nếu file chính hỏng/thiếu thì thử bản sao lưu .bak. Trả null nếu không có gì dùng được. */
    private fun readCache(ctx: Context, id: String): List<Track>? {
        val f = cacheFile(ctx, id)
        for (c in listOf(f, bakFile(f))) {
            if (!c.exists()) continue
            val r = runCatching { fromJson(c.readText()) }
            if (r.isSuccess) { if (c !== f) AppLog.i("Library", "cache $id: dùng bản sao lưu"); return r.getOrNull() }
            AppLog.i("Library", "cache $id (${c.name}) không đọc được: ${r.exceptionOrNull()?.message}")
        }
        return null
    }

    /** Ghi cache an toàn: ghi file tạm → sao lưu bản cũ (.bak) → đổi tên. Không bao giờ làm hỏng bản cũ nếu ghi dở. */
    private fun writeCache(ctx: Context, id: String, l: List<Track>) {
        val f = cacheFile(ctx, id); val tmp = File(f.path + ".tmp")
        try {
            tmp.writeText(toJson(l))
            if (f.exists()) runCatching { f.copyTo(bakFile(f), overwrite = true) }
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            AppLog.i("Library", "không ghi được cache $id: ${e.message}")
        }
    }

    /** Gộp bản ghi cũ mà lần quét này không thấy (do bị dừng / thư mục lỗi / nguồn offline) — không làm mất bản ghi. */
    private fun mergeKeep(fresh: List<Track>, old: Collection<Track>): List<Track> {
        val have = fresh.mapTo(HashSet()) { it.path }
        return fresh + old.filter { it.path !in have }
    }

    /**
     * File bị di chuyển/đổi tên: bài cũ biến mất và đúng một bài mới xuất hiện với cùng kích thước, thời lượng, tên, nghệ sĩ, album
     * → chuyển Yêu thích / Gần đây / thống kê nghe sang đường dẫn mới. Mơ hồ (nhiều ứng viên) thì không làm gì. Không xóa gì cả.
     */
    private fun relink(old: Collection<Track>, fresh: List<Track>) {
        val newPaths = fresh.mapTo(HashSet()) { it.path }; val oldPaths = old.mapTo(HashSet()) { it.path }
        val gone = old.filter { it.path !in newPaths && it.size > 0 }
        val added = fresh.filter { it.path !in oldPaths && it.size > 0 }
        if (gone.isEmpty() || added.isEmpty()) return
        fun key(t: Track) = "${t.size}|${t.durationMs / 1000}|${t.title.lowercase()}|${t.artist.lowercase()}|${t.album.lowercase()}"
        val g = gone.groupBy(::key); val a = added.groupBy(::key)
        val map = HashMap<String, String>()
        for ((k, gl) in g) { val al = a[k] ?: continue; if (gl.size == 1 && al.size == 1) map[gl[0].path] = al[0].path }
        if (map.isEmpty()) return
        Favorites.remap(map); Recents.remap(map); UsageStats.remap(map)
        AppLog.i("Library", "đã nối lại ${map.size} bài bị di chuyển/đổi tên")
    }

    /** Báo cáo nhóm bài có vẻ trùng (cùng tên + nghệ sĩ + album + thời lượng làm tròn giây). Chỉ để xem — không xóa, không gộp. */
    fun duplicateGroups(): List<List<Track>> =
        tracks.value.filter { it.durationMs > 0 }
            .groupBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.album.trim().lowercase()}|${it.durationMs / 1000}" }
            .values.filter { g -> g.size > 1 && g.map { it.path }.distinct().size > 1 }

    /** Dò mọi ổ đọc được: bộ nhớ trong, thư mục app trên từng ổ (cách chuẩn để thấy USB), và thư mục gốc của từng ổ trong thư mục storage. */
    private fun roots(ctx: Context): List<File> {
        val r = LinkedHashMap<String, File>()
        fun add(f: File?) { if (f != null) r.putIfAbsent(f.path, f) }
        add(Environment.getExternalStorageDirectory())
        runCatching {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val i = d.path.indexOf("/Android/"); if (i > 0) add(File(d.path.substring(0, i)))
            }
        }
        File("/storage").listFiles()?.forEach { if (it.name != "self" && it.name != "emulated" && it.isDirectory) add(it) }
        return r.values.toList()
    }
    private fun rootLabel(f: File) = if (f.path.startsWith("/storage/emulated") || f.path.startsWith("/sdcard")) "TV" else "USB ${f.name}"

    /** Chỉ nạp cache (không quét) — dành cho LibraryProvider khi tiến trình vừa được launcher đánh thức. */
    suspend fun loadCacheOnly(ctx: Context) {
        if (tracks.value.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            for (id in listOf("local") + SourceStore.list.value.map { it.id })
                readCache(ctx, id)?.let { publish(id, it) }
        }
    }

    /** Nạp cache (nhanh) rồi quét lại bộ nhớ TV/USB. Nguồn NAS chỉ quét khi người dùng bấm. */
    suspend fun load(ctx: Context) {
        withContext(Dispatchers.IO) {
            for (id in listOf("local") + SourceStore.list.value.map { it.id })
                readCache(ctx, id)?.let { publish(id, it) }
        }
        rescanLocal(ctx)
    }

    private val localPending = AtomicBoolean(false)

    suspend fun rescanLocal(ctx: Context) = withContext(Dispatchers.IO) {
        if (!localBusy.compareAndSet(false, true)) { localPending.set(true); return@withContext } // yêu cầu quét lại được xếp hàng
        try {
            do { localPending.set(false); scanLocalOnce(ctx) } while (localPending.get())
        } finally { localBusy.set(false) }
    }

    private fun scanLocalOnce(ctx: Context) {
        begin()
        try {
            status.value = "Đang quét bộ nhớ TV & USB…"
            val old = cached("local").associateBy { it.path }
            val out = ArrayList<Track>()
            val report = ArrayList<String>()
            val rs = roots(ctx)
            val seen = HashSet<String>()
            val carry = ArrayList<Track>() // bản ghi của ổ hiện không đọc được
            var reread = 0; var failedRead = 0
            for (root in rs) {
                val readable = root.listFiles() != null
                if (!readable) carry += old.values.filter { it.path.startsWith(root.path + "/") }
                val files = if (!readable) emptyList() else root.walkTopDown()
                    .onEnter { d -> !d.name.startsWith(".") && d.name != "Android" && d.name != "LOST.DIR" }
                    .filter { it.isFile && isAudioName(it.name) }.toList()
                report += "${rootLabel(root)}: " + if (!readable) "không đọc được" else "${files.size}"
                for (f in files) {
                    if (!seen.add(f.path)) continue
                    val size = f.length(); val mt = f.lastModified()
                    val o = old[f.path]
                    out += when {
                        o != null && o.ver >= META_VER && o.mtime == mt && o.size == size -> o  // không đổi và đã có thẻ đầy đủ: giữ nguyên
                        else -> {
                            reread++
                            val disp = Recycle.displayDir(f.path)
                            val n = readMeta(f.path, "local", disp).let { if (disp.isNotEmpty()) it.copy(dir = disp) else it }
                            // đọc lỗi (không ra thời lượng) mà trước đó đã có thẻ tốt → giữ thẻ cũ, không ghi đè bằng dữ liệu rỗng
                            if (o != null && n.durationMs == 0L && o.durationMs > 0L) { failedRead++; o.copy(size = size, mtime = mt) }
                            else n.copy(size = size, mtime = mt)
                        }
                    }
                    if (out.size % 40 == 0) publish("local", out.toList())
                }
            }
            // Bản ghi thuộc ổ đang không có mặt (USB rút ra) hoặc không đọc được: giữ lại, không xóa
            val offline = old.values.filter { t -> rs.none { t.path.startsWith(it.path + "/") } }
            val unique = (out + carry + offline).distinctBy { it.path }
            publish("local", unique)
            writeCache(ctx, "local", unique)
            runCatching { relink(old.values, unique) }.onFailure { AppLog.i("Library", "relink lỗi: ${it.message}") }
            if (offline.isNotEmpty() || carry.isNotEmpty()) report += "giữ ${offline.size + carry.size} bài của ổ đang vắng"
            if (reread > 0 || failedRead > 0) AppLog.i("Library", "quét local: đọc lại $reread file, $failedRead file đọc lỗi (giữ thẻ cũ)")
            status.value = "Đã quét: " + report.joinToString(" · ")
        } catch (e: Exception) {
            status.value = "Lỗi quét bộ nhớ: ${e.message ?: e.javaClass.simpleName}"
        } finally { end() }
    }

    suspend fun scanSource(ctx: Context, s: Source) = withContext(Dispatchers.IO) {
        begin()
        cancelFlag.set(false)
        try {
            status.value = "Đang quét ${s.name}…"
            val old = cached(s.id).associateBy { it.path }
            var note = ""
            val result: List<Track> = if (s.type == "dlna") {
                val acc = ArrayList<Track>()
                note = Upnp.crawl(s, { cancelFlag.get() }, { f, t, w -> status.value = "Đang quét ${s.name}: $t bài · $f thư mục · $w" }) {
                    acc += it; if (acc.size % 20 == 0) publish(s.id, acc.toList())
                }
                acc
            } else if (Cloud.isCloud(s.type)) {
                val files = ArrayList<CloudFile>()
                val failed = Cloud.walk(s, { cancelFlag.get() }, { f, n -> status.value = "Đang liệt kê ${s.name}: $n file nhạc · $f thư mục" }) { files += it }
                val acc = Collections.synchronizedList(ArrayList<Track>())
                val sem = Semaphore(4); val done = AtomicInteger(0)
                coroutineScope {
                    files.map { cf ->
                        async {
                            sem.withPermit {
                                if (!cancelFlag.get()) {
                                    acc.add(old[cf.path]?.takeIf { it.ver >= META_VER } ?: readCloudMeta(cf, s.id).let { n -> old[cf.path]?.takeIf { n.durationMs == 0L && it.durationMs > 0 } ?: n })
                                    val d = done.incrementAndGet()
                                    if (d % 20 == 0) { status.value = "Đọc thẻ nhạc ${s.name}: $d/${files.size}"; publish(s.id, synchronized(acc) { acc.toList() }) }
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (cancelFlag.get()) note = "đã dừng"
                if (failed > 0) note += (if (note.isEmpty()) "" else " · ") + "$failed thư mục lỗi"
                synchronized(acc) { acc.toList() }
            } else {
                val paths = ArrayList<String>()
                if (s.type == "smb") Remote.walkSmb(s) { paths += "smb://${s.id}/$it" } else Remote.walkDav(s) { paths += "dav://${s.id}/$it" }
                val acc = Collections.synchronizedList(ArrayList<Track>())
                val sem = Semaphore(4)
                coroutineScope {
                    paths.map { p ->
                        async {
                            sem.withPermit {
                                acc.add(old[p]?.takeIf { it.ver >= META_VER } ?: readMeta(p, s.id).let { n -> old[p]?.takeIf { n.durationMs == 0L && it.durationMs > 0 } ?: n })
                                if (acc.size % 40 == 0) publish(s.id, synchronized(acc) { acc.toList() })
                            }
                        }
                    }.awaitAll()
                }
                synchronized(acc) { acc.toList() }
            }
            // Quét bị dừng / có thư mục lỗi / nguồn trả về rỗng: giữ lại bản ghi cũ thay vì ghi đè bằng kết quả thiếu
            val partial = cancelFlag.get() || note.contains("thư mục lỗi")
            val result2 = if (result.isEmpty() && old.isNotEmpty()) { note += (if (note.isEmpty()) "" else " · ") + "không đọc được, giữ dữ liệu cũ"; old.values.toList() }
                else if (partial) mergeKeep(result, old.values) else result
            publish(s.id, result2)
            writeCache(ctx, s.id, result2)
            status.value = "Đã quét ${s.name}: ${result2.size} bài" + (if (note.isNotEmpty()) " · $note" else "")
        } catch (e: Exception) {
            status.value = "Lỗi quét ${s.name}: ${e.message ?: e.javaClass.simpleName}"
        } finally { end() }
    }

    fun scanSourceAsync(ctx: Context, s: Source) { scope.launch { scanSource(ctx.applicationContext, s) } }
    fun rescanAllAsync(ctx: Context) { scope.launch { rescanLocal(ctx.applicationContext); SourceStore.list.value.forEach { scanSource(ctx.applicationContext, it) } } }
    fun removeSource(ctx: Context, id: String) { SourceStore.remove(id); drop(id); cacheFile(ctx, id).let { it.delete(); bakFile(it).delete() }; Smb.reset(id) }

    /** FLAC: đọc thẻ + thông số thẳng từ header (Android 8 / MediaMetadataRetriever hay thiếu thẻ và thời lượng). */
    private fun flacMeta(path: String, src: String, name: String, album: String): Track? = try {
        val rd = Remote.reader(path)
        try {
            val m = FlacPcm.readMeta(ReaderSource(rd), false)
            val dur = m.durationMs()
            val kb = if (dur > 0 && rd.size > 0) (rd.size * 8 / dur).toInt() else 0
            Track(path, m.title.ifEmpty { name }, m.artist.ifEmpty { m.albumArtist }.ifEmpty { "Không rõ nghệ sĩ" },
                m.album.ifEmpty { album }, m.genre.ifEmpty { "Không rõ thể loại" }, dur, m.track, src, "flac", "", "",
                m.sampleRate, m.bps, m.channels, kb, year = m.year, disc = m.disc, albumArtist = m.albumArtist, composer = m.composer, ver = META_VER)
        } finally { rd.close() }
    } catch (e: Exception) { null }

    /** DSD (DSF/DFF): đọc tốc độ, số kênh, thời lượng và thẻ ID3 từ header. */
    private fun dsdMeta(path: String, src: String, name: String, album: String, ext: String): Track? = try {
        val rd = Remote.reader(path)
        try {
            val m = DsdPcm.readMeta(ReaderSource(rd))
            val dur = m.durationMs()
            val kb = if (dur > 0 && rd.size > 0) (rd.size * 8 / dur).toInt() else 0
            Track(path, m.title.ifEmpty { name }, m.artist.ifEmpty { "Không rõ nghệ sĩ" }, m.album.ifEmpty { album }, m.genre.ifEmpty { "Không rõ thể loại" },
                dur, 0, src, ext, "", "", m.rate, 1, m.channels, kb, ver = META_VER)
        } finally { rd.close() }
    } catch (e: Exception) { null }

    /** File trên đám mây: đường dẫn chỉ là id, nên tên file và thư mục phải truyền riêng. */
    private fun readCloudMeta(f: CloudFile, src: String): Track {
        val t = readMeta(f.path, src, f.dir.ifEmpty { SourceStore.name(src) }, f.name)
        return t.copy(dir = f.dir, format = t.format.ifEmpty { Cloud.extOf(f.name) })
    }

    fun readMeta(path: String, src: String, dispDir: String = "", fileName: String? = null): Track {
        val name = (fileName ?: path.substringAfterLast('/')).substringBeforeLast('.')
        val album = dispDir.substringAfterLast('/').ifEmpty { dirLabel(path.substringBeforeLast('/')) }
        val low = (fileName ?: path).lowercase()
        if (low.endsWith(".flac")) flacMeta(path, src, name, album)?.let { return it }
        if (low.endsWith(".dsf") || low.endsWith(".dff")) dsdMeta(path, src, name, album, low.substringAfterLast('.'))?.let { return it }
        return try {
            Media.withMmr(path) { r ->
                fun g(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
                Track(
                    path, g(MMR.METADATA_KEY_TITLE) ?: name,
                    g(MMR.METADATA_KEY_ARTIST) ?: g(MMR.METADATA_KEY_ALBUMARTIST) ?: "Không rõ nghệ sĩ",
                    g(MMR.METADATA_KEY_ALBUM) ?: album, g(MMR.METADATA_KEY_GENRE) ?: "Không rõ thể loại",
                    g(MMR.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    g(MMR.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0, src,
                    year = g(MMR.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()?.takeIf { it in 1000..2999 } ?: 0,
                    disc = g(MMR.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
                    albumArtist = g(MMR.METADATA_KEY_ALBUMARTIST) ?: "", composer = g(MMR.METADATA_KEY_COMPOSER) ?: "", ver = META_VER,
                )
            }
        } catch (e: Exception) { Track(path, name, "Không rõ nghệ sĩ", album, "Không rõ thể loại", 0L, 0, src, ver = META_VER) }
    }

    private fun toJson(l: List<Track>): String {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("p", it.path).put("t", it.title).put("a", it.artist).put("b", it.album).put("g", it.genre).put("d", it.durationMs).put("n", it.no)
            .put("s", it.src).put("f", it.format).put("r", it.art).put("dr", it.dir).put("sr", it.sr).put("bt", it.bits).put("ch", it.ch).put("kb", it.kbps).put("mi", it.mime).put("sz", it.size).put("mt", it.mtime)
            .put("yr", it.year).put("dc", it.disc).put("aa", it.albumArtist).put("cp", it.composer).put("v", it.ver)) }
        return a.toString()
    }
    /** Đọc từng bản ghi riêng: bản ghi hỏng bị bỏ qua (có log) chứ không làm mất cả cache. Khóa mới đều tùy chọn → cache cũ đọc bình thường. */
    private fun fromJson(s: String): List<Track> {
        val a = JSONArray(s)
        val out = ArrayList<Track>(a.length()); var bad = 0
        for (i in 0 until a.length()) {
            val t = runCatching {
                val o = a.getJSONObject(i)
                val p = o.getString("p").also { require(it.isNotEmpty()) }
                Track(p, o.optString("t").ifEmpty { p.substringAfterLast('/').substringBeforeLast('.') },
                    o.optString("a").ifEmpty { "Không rõ nghệ sĩ" }, o.optString("b"), o.optString("g").ifEmpty { "Không rõ thể loại" },
                    o.optLong("d"), o.optInt("n"),
                    o.optString("s", "local"), o.optString("f"), o.optString("r"), o.optString("dr"), o.optInt("sr"), o.optInt("bt"), o.optInt("ch"), o.optInt("kb"),
                    o.optString("mi"), o.optLong("sz"), o.optLong("mt"),
                    o.optInt("yr"), o.optInt("dc"), o.optString("aa"), o.optString("cp"), o.optInt("v"))
            }.getOrNull()
            if (t != null) out += t else bad++
        }
        if (bad > 0) AppLog.i("Library", "cache: bỏ qua $bad bản ghi hỏng")
        return out
    }

    private fun sorted(v: List<Track>) = v.sortedWith(compareBy({ if (it.no > 0) it.no else Int.MAX_VALUE }, { it.path.lowercase() }))

    fun groups(tab: Tab, all: List<Track>): List<Group> = when (tab) {
        Tab.ALBUM -> all.groupBy { it.album }.map { (k, v) ->
            val ar = v.map { it.artist }.distinct()
            Group(k, k, if (ar.size == 1) ar[0] else "Nhiều nghệ sĩ", sorted(v))
        }
        Tab.ARTIST -> all.groupBy { it.artist }.map { (k, v) -> Group(k, k, "${v.map { it.album }.distinct().size} album · ${v.size} bài", sorted(v)) }
        Tab.GENRE -> all.groupBy { it.genre }.map { (k, v) -> Group(k, k, "${v.size} bài", sorted(v)) }
        Tab.FOLDER -> all.groupBy { it.folder }.map { (k, v) ->
            val t = if (v[0].dir.isNotEmpty()) v[0].dir.substringAfterLast('/') else k.substringAfterLast('/')
            Group(k, t, "${v.size} bài · ${v[0].sourceName}", sorted(v))
        }
        Tab.SONG, Tab.FAV, Tab.HABIT -> emptyList()
    }.sortedBy { it.title.lowercase() }
}
