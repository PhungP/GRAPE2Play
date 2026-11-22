package com.grape.play

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Normalizer

private val MN = Regex("\\p{Mn}+")
private val NON_ALNUM = Regex("[^a-z0-9]+")

/** Chuẩn hoá để so khớp không phân biệt dấu/hoa thường (tiếng Việt). Regex biên dịch sẵn để không tốn CPU/bộ nhớ khi gọi hàng nghìn lần. */
internal fun aiNorm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MN, "").replace('đ', 'd')

/** Chạy việc nặng (quét thư viện) ở luồng nền và biến hết bộ nhớ thành thông báo thay vì làm app sập. */
internal suspend fun <T> aiHeavy(block: suspend () -> T): T = withContext(Dispatchers.Default) {
    try { block() } catch (e: OutOfMemoryError) {
        AiIndex.drop()
        throw IOException("Hết bộ nhớ khi xử lý thư viện lớn — đóng bớt ứng dụng khác rồi thử lại")
    }
}

/**
 * Chỉ mục thư viện dựng một lần (ở luồng nền) cho mọi nguồn đã quét: chuẩn hoá tên bài / nghệ sĩ / album / thư mục / thể loại.
 * Mọi tính năng AI và Tìm nhanh dùng chung chỉ mục này nên không phải quét lại hàng chục nghìn bài mỗi lần.
 */
object AiIndex {
    class Alb(val key: String, val value: List<Track>)
    class Entry(val t: Track, val nt: String, val nr: String, val ng: String)
    class Folder(val key: String, val name: String, val where: String, val src: String, val tracks: List<Track>, val norm: String)
    class Snap(
        val sig: String, val entries: Array<Entry>, val albums: List<Alb>, val folders: List<Folder>,
        val names: LinkedHashMap<String, Track>, val topArtists: List<String>, val artistCount: Int,
        val formats: List<String>, val srcCounts: List<Pair<String, Int>>,
    )
    class Hits(val folders: List<Folder>, val tracks: List<Track>, val totalFolders: Int, val totalTracks: Int)

    @Volatile private var cache: Snap? = null
    fun drop() { cache = null }

    private fun sigOf(l: List<Track>) = "${l.size}|${l.firstOrNull()?.path}|${l.lastOrNull()?.path}|${TagFix.count}|${AiMeta.map.size / 200}|${l.sumOf { it.year }}"

    suspend fun get(lib: List<Track>): Snap = withContext(Dispatchers.Default) {
        val sig = sigOf(lib)
        cache?.takeIf { it.sig == sig } ?: run {
            val t0 = System.currentTimeMillis()
            build(lib, sig).also { cache = it; AppLog.i("AI", "Lập chỉ mục ${lib.size} bài, ${it.folders.size} thư mục trong ${System.currentTimeMillis() - t0} ms") }
        }
    }

    private fun build(lib: List<Track>, sig: String): Snap {
        val entries = ArrayList<Entry>(lib.size)
        val folderTracks = LinkedHashMap<String, MutableList<Track>>()
        val folderInfo = HashMap<String, Triple<String, String, String>>()
        val names = LinkedHashMap<String, Track>()
        for (t in lib) {
            val s = runCatching { segs(t) }.getOrElse { listOf(t.sourceName, t.title) }
            val mid = if (s.size > 2) s.subList(1, s.size - 1) else emptyList()
            for (n in mid) if (n.isNotBlank() && n !in names) names[n] = t
            val fname = mid.lastOrNull() ?: s.firstOrNull() ?: ""
            val aiTitles = mid.mapNotNull { AiMeta.map[it]?.title }
            val aiGenres = mid.mapNotNull { AiMeta.map[it]?.genre }
            entries += Entry(t, aiNorm(t.title), aiNorm(t.artist + " " + t.albumArtist + " " + t.album + (if (t.year > 0) " ${t.year}" else "") + " " + mid.joinToString(" ") + " " + aiTitles.joinToString(" ")).take(400), aiNorm(t.genre + " " + aiGenres.joinToString(" ")))
            val fk = t.folder
            folderTracks.getOrPut(fk) { ArrayList() }.add(t)
            if (fk !in folderInfo) folderInfo[fk] = Triple(AiMeta.map[fname]?.title ?: fname, s.dropLast(1).dropLast(1).joinToString(" › "), t.sourceName)
        }
        val folders = folderTracks.map { (k, ts) ->
            val (name, where, src) = folderInfo[k]!!
            Folder(k, name, where, src, ts, aiNorm("$name $where ${ts.first().artist}"))
        }
        val albums = lib.groupBy { it.artist.trim() + "\u0000" + it.album.trim() }.filter { it.key.length > 1 }.map { Alb(it.key, it.value) }
        val ac = lib.groupingBy { it.artist }.eachCount()
        val top = ac.entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotBlank() }
        val fmts = lib.map { it.ext }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
        val src = lib.groupingBy { it.sourceName }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        return Snap(sig, entries.toTypedArray(), albums, folders, names, top.take(150), top.size, fmts, src)
    }

    fun tokens(q: String): List<String> = aiNorm(q).split(NON_ALNUM).filter { it.isNotEmpty() }

    /** Tìm nhanh trong mọi nguồn: bài hát (tên bài / nghệ sĩ / album / thư mục) và thư mục. Không cần mạng, không cần khóa AI. */
    fun search(s: Snap, q: String, maxTracks: Int = 300, maxFolders: Int = 60): Hits {
        val toks = tokens(q)
        if (toks.isEmpty()) return Hits(emptyList(), emptyList(), 0, 0)
        fun scoreTracks(need: Int): List<Pair<Entry, Int>> {
            val out = ArrayList<Pair<Entry, Int>>()
            for (e in s.entries) {
                var hit = 0; var inTitle = 0
                for (t in toks) { val a = t in e.nt; if (a) inTitle++; if (a || t in e.nr || t in e.ng) hit++ }
                if (hit >= need) out += e to (hit * 10 + inTitle * 3 + if (e.nt.startsWith(toks[0])) 2 else 0)
            }
            return out
        }
        var tr = scoreTracks(toks.size)
        if (tr.isEmpty() && toks.size > 1) tr = scoreTracks(toks.size - 1)
        val sorted = tr.sortedByDescending { it.second }
        fun foldersFor(need: Int) = s.folders.filter { f -> toks.count { it in f.norm } >= need }
        var fl = foldersFor(toks.size)
        if (fl.isEmpty() && toks.size > 1) fl = foldersFor(toks.size - 1)
        fl = fl.sortedWith(compareByDescending<Folder> { f -> toks.count { it in aiNorm(f.name) } }.thenByDescending { it.tracks.size })
        return Hits(fl.take(maxFolders), sorted.take(maxTracks).map { it.first.t }, fl.size, sorted.size)
    }
}
