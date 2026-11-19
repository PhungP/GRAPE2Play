package com.grape.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import kotlin.coroutines.coroutineContext

/** Chuẩn hoá để so khớp không phân biệt dấu/hoa thường (tiếng Việt). */
internal fun aiNorm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

private val GENRES = "bolero, jazz, pop, rock, classical, folk, soundtrack, other"

internal fun timeOfDay(): String {
    val c = Calendar.getInstance(); val h = c.get(Calendar.HOUR_OF_DAY)
    val part = when { h < 5 -> "đêm khuya"; h < 11 -> "buổi sáng"; h < 14 -> "buổi trưa"; h < 18 -> "buổi chiều"; h < 22 -> "buổi tối"; else -> "đêm" }
    val day = listOf("Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy")[c.get(Calendar.DAY_OF_WEEK) - 1]
    return "$day, ${h}h, $part"
}

/** 1. Làm sạch thư viện: tên hiển thị gọn + thể loại cho từng thư mục. */
object AiClean {
    /** Danh sách (tên thư mục, nghệ sĩ mẫu, album mẫu) chưa được AI xử lý. */
    fun pending(lib: List<Track>): List<Triple<String, String, String>> {
        val seen = LinkedHashMap<String, Track>()
        for (t in lib) { val s = segs(t); for (n in s.drop(1).dropLast(1)) if (n.isNotBlank() && n !in seen && n !in AiMeta.map) seen[n] = t }
        return seen.map { (n, t) -> Triple(n, t.artist, t.album) }
    }

    suspend fun run(lib: List<Track>, onProgress: (done: Int, total: Int) -> Unit): Int {
        val todo = pending(lib)
        var done = 0; var ok = 0
        onProgress(0, todo.size)
        for (batch in todo.chunked(25)) {
            coroutineContext.ensureActive()
            val arr = JSONArray(); batch.forEach { (n, a, al) -> arr.put(JSONObject().put("n", n).put("artist", a).put("album", al)) }
            val sys = "Bạn chuẩn hoá tên thư mục trong thư viện nhạc hi-fi (nhạc Việt và quốc tế). Với mỗi mục trả về JSON mảng " +
                "[{\"n\":<tên gốc, giữ nguyên>, \"title\":<tên hiển thị gọn, đẹp>, \"genre\":<một trong: $GENRES>}]. " +
                "title: bỏ mã số/catalog/số thứ tự/bitrate/định dạng thừa; đặt dấu tiếng Việt đúng nếu rõ là tên Việt; dạng \"Nghệ sĩ – Album\" nếu xác định được, ngắn gọn. " +
                "Không bịa: nếu không chắc, giữ gần tên gốc và genre là other. Chỉ trả JSON."
            val res = Ai.parseJson(Ai.generate("Danh sách thư mục:\n$arr", sys, json = true, temp = 0.1)) as? JSONArray
            if (res == null) throw java.io.IOException("AI trả về định dạng lạ — thử lại")
            val out = HashMap<String, AiMeta.M>()
            val valid = batch.map { it.first }.toSet()
            if (res != null) for (i in 0 until res.length()) {
                val o = res.optJSONObject(i) ?: continue
                val n = o.optString("n"); if (n !in valid) continue
                out[n] = AiMeta.M(o.optString("title").trim().ifEmpty { n }, o.optString("genre").trim().lowercase().ifEmpty { "other" })
            }
            // mục AI bỏ sót vẫn được ghi để không hỏi lại mãi
            batch.forEach { if (it.first !in out) out[it.first] = AiMeta.M(it.first, "other") }
            AiMeta.putAll(out); ok += out.size; done += batch.size
            onProgress(done, todo.size)
            delay(3500)   // giữ dưới hạn mức yêu cầu/phút của gói miễn phí
        }
        return ok
    }
}

/** 2. Hiểu yêu cầu tự nhiên → bộ lọc cục bộ (thư viện không bị gửi lên mạng, chỉ gửi câu hỏi và vài tên nghệ sĩ). */
object AiSearch {
    class Result(val explain: String, val tracks: List<Track>, val name: String = "")

    suspend fun run(query: String, lib: List<Track>): Result {
        val artists = lib.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.take(120).map { it.key }.filter { it.isNotBlank() }
        val formats = lib.map { it.ext }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
        val sys = "Bạn phân tích yêu cầu nghe nhạc thành bộ lọc cho app phát nhạc hi-fi. Chỉ trả JSON dạng " +
            "{\"explain\":<1 câu mô tả bạn hiểu yêu cầu thế nào>, \"artists\":[tên nghệ sĩ, ưu tiên đúng tên trong danh sách có sẵn], \"keywords\":[từ khoá tên bài/album/thư mục], " +
            "\"genres\":[từ: $GENRES], \"formats\":[vd dsd, flac, wav, mp3], \"sort\":<relevance|most_played|least_played|never_played|recent|random>, \"limit\":<số bài 10-120>, \"minutes\":<tổng thời lượng mong muốn tính bằng phút nếu người dùng nói, nếu không thì 0>, \"name\":<tên playlist ngắn gọn, gợi cảm>}. " +
            "Các trường không liên quan để mảng rỗng. Tâm trạng (buồn, nhẹ nhàng, sôi động...) hãy chuyển thành genres/keywords hợp lý. ${Ai.langHint()}"
        val prompt = "Yêu cầu: $query\nBây giờ: ${timeOfDay()}\nĐịnh dạng trong thư viện: ${formats.joinToString()}\nNghệ sĩ có trong thư viện: ${artists.joinToString(" | ")}"
        val o = Ai.parseJson(Ai.generate(prompt, sys, json = true, temp = 0.2)) as JSONObject
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        val terms = (list("artists") + list("keywords")).map { aiNorm(it) }
        val genres = list("genres").map { aiNorm(it) }.filter { it != "other" }
        val fmts = list("formats").map { it.lowercase().removePrefix(".") }
        val limit = if (o.optInt("minutes", 0) > 0) 400 else o.optInt("limit", 60).coerceIn(10, 200)
        var r = lib.filter { t ->
            if (terms.isEmpty() && genres.isEmpty()) return@filter true
            val hay = aiNorm(t.artist + " " + t.title + " " + t.album + " " + t.dir)
            val byTerm = terms.any { it in hay }
            val g = aiNorm(t.genre + " " + (segs(t).drop(1).dropLast(1).mapNotNull { AiMeta.map[it]?.genre }.joinToString(" ")))
            byTerm || genres.any { it in g }
        }
        if (fmts.isNotEmpty()) r = r.filter { t -> val e = t.ext; fmts.any { f -> e == f || (f == "dsd" && (e == "dsf" || e == "dff")) } }
        r = when (o.optString("sort")) {
            "most_played" -> r.sortedByDescending { UsageStats.plays(it.path) }.filter { UsageStats.plays(it.path) > 0 }.ifEmpty { r.shuffled() }
            "least_played" -> r.filter { UsageStats.plays(it.path) > 0 }.sortedBy { UsageStats.plays(it.path) }.ifEmpty { r.shuffled() }
            "never_played" -> r.filter { UsageStats.plays(it.path) == 0 }.shuffled()
            "recent" -> r.filter { UsageStats.tracks[it.path] != null }.sortedByDescending { UsageStats.tracks[it.path]?.last ?: 0L }.ifEmpty { r.shuffled() }
            "random" -> r.shuffled()
            else -> if (r.size > limit) r.shuffled() else r.sortedWith(compareBy({ it.dir }, { it.no }))
        }
        val mins = o.optInt("minutes", 0)
        if (mins > 0) {
            val src = if (o.optString("sort").let { it == "relevance" || it.isEmpty() }) r.shuffled() else r
            var acc = 0L; val out = ArrayList<Track>()
            for (t in src) { if (acc >= mins * 60_000L) break; out += t; acc += t.durationMs }
            return Result(o.optString("explain"), out, o.optString("name"))
        }
        return Result(o.optString("explain"), r.take(limit), o.optString("name"))
    }
}

/** 3. Gợi ý hôm nay: dựa vào thời điểm trong ngày + thói quen nghe, chọn ra vài danh sách từ chính thư viện. */
object AiSuggest {
    class Playlist(val title: String, val reason: String, val tracks: List<Track>)

    suspend fun run(lib: List<Track>, context: String = ""): List<Playlist> {
        val albums = lib.groupBy { it.artist.trim() + "\u0000" + it.album.trim() }.filter { it.key.length > 1 }
        val withPlays = albums.entries.map { it to it.value.sumOf { t -> UsageStats.plays(t.path) } }
        val pool = (withPlays.sortedByDescending { it.second }.take(25) + withPlays.shuffled().take(110)).distinctBy { it.first.key }.map { it.first }
        val lines = pool.mapIndexed { i, e -> val t = e.value.first(); "$i|${t.artist}|${t.album}|${t.ext}" }
        val topArtists = lib.filter { UsageStats.plays(it.path) > 0 }.groupBy { it.artist }.entries.sortedByDescending { e -> e.value.sumOf { UsageStats.plays(it.path) } }.take(8).map { it.key }
        val recent = lib.filter { UsageStats.tracks[it.path] != null }.sortedByDescending { UsageStats.tracks[it.path]?.last ?: 0L }.take(6).map { it.title }
        val sys = "Bạn là người chọn nhạc cho app nghe nhạc hi-fi. Từ danh sách album có sẵn (id|nghệ sĩ|album|định dạng), tạo 3 đến 4 danh sách nghe hợp bối cảnh hiện tại (thời tiết thực tế, mùa, lễ hội/phong tục, tâm trạng nếu có) và thói quen. Tên danh sách phải gợi đúng bối cảnh; \"reason\" nêu rõ lý do (ví dụ trời mưa, sắp Tết, tâm trạng buồn). " +
            "Chỉ trả JSON {\"playlists\":[{\"title\":<tên ngắn>, \"reason\":<1 câu vì sao hợp>, \"albums\":[id, tối đa 6 id]}]}. Chỉ dùng id có trong danh sách. ${Ai.langHint()}"
        val prompt = "Bối cảnh:\n${context.ifEmpty { "Bây giờ: ${timeOfDay()}" }}\nNghệ sĩ hay nghe: ${topArtists.joinToString().ifEmpty { "(chưa có)" }}\nMới nghe: ${recent.joinToString(" | ").ifEmpty { "(chưa có)" }}\nAlbum:\n${lines.joinToString("\n")}"
        val o = Ai.parseJson(Ai.generate(prompt, sys, json = true, temp = 0.7)) as JSONObject
        val arr = o.optJSONArray("playlists") ?: return emptyList()
        val out = ArrayList<Playlist>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val ids = p.optJSONArray("albums") ?: continue
            val tracks = (0 until ids.length()).mapNotNull { pool.getOrNull(ids.optInt(it, -1)) }.flatMap { e -> e.value.sortedWith(compareBy({ it.dir }, { it.no })) }.take(150)
            if (tracks.isNotEmpty()) out += Playlist(p.optString("title"), p.optString("reason"), tracks)
        }
        return out
    }
}

/** 4. Giới thiệu bài / album / nghệ sĩ đang phát. */
object AiInfo {
    suspend fun run(t: Track): String {
        val sys = "Bạn là chuyên gia âm nhạc và audio hi-fi. Viết giới thiệu ngắn (tối đa 170 từ) về nghệ sĩ, album và bản thu của bài được hỏi: bối cảnh, phong cách, điểm đáng nghe. " +
            "Nếu tên là tên Việt viết không dấu, hãy ghi lại tên có dấu. Không bịa: chỗ nào không chắc phải nói rõ là không chắc. Không trích lời bài hát. ${Ai.langHint()}"
        val prompt = "Bài: ${t.title}\nNghệ sĩ: ${t.artist}\nAlbum: ${t.album}\nĐịnh dạng: ${t.ext.uppercase()} ${if (t.bits > 0) t.bits.toString() + "-bit " else ""}${if (t.sr > 0) khz(t.sr) else ""}"
        return Ai.generate(prompt, sys, temp = 0.5).trim()
    }
}

private fun albumPool(lib: List<Track>, picker: (List<Map.Entry<String, List<Track>>>) -> List<Map.Entry<String, List<Track>>>): Pair<List<Map.Entry<String, List<Track>>>, List<String>> {
    val albums = lib.groupBy { it.artist.trim() + "\u0000" + it.album.trim() }.filter { it.key.length > 1 }.entries.toList()
    val pool = picker(albums)
    return pool to pool.mapIndexed { i, e -> val t = e.value.first(); "$i|${t.artist}|${t.album}|${t.ext}" }
}

private fun albumTracks(e: Map.Entry<String, List<Track>>) = e.value.sortedWith(compareBy({ it.dir }, { it.no })).take(150)

/** 2. AI DJ: chọn tiếp các bài hợp mạch với những bài vừa phát. */
object AiDjTask {
    suspend fun more(lib: List<Track>, queue: List<Track>, idx: Int, context: String): List<Track> {
        val inQueue = queue.map { it.path }.toHashSet()
        val recentAlbums = queue.drop((idx - 8).coerceAtLeast(0)).take(10).map { it.artist + "\u0000" + it.album }.toSet()
        val (pool, lines) = albumPool(lib) { all ->
            val cand = all.filter { e -> e.value.any { it.path !in inQueue } }
            (cand.filter { UsageStats.plays(it.value.first().path) > 0 }.shuffled().take(45) + cand.shuffled().take(80)).distinctBy { it.key }.filter { it.key !in recentAlbums }
        }
        if (pool.isEmpty()) return emptyList()
        val played = queue.take(idx + 1).takeLast(6).joinToString("\n") { "- ${it.title} — ${it.artist} (${it.album})" }
        val sys = "Bạn là DJ cho app nghe nhạc hi-fi. Dựa vào các bài vừa phát và bối cảnh, chọn 3 album tiếp theo từ danh sách (id|nghệ sĩ|album|định dạng) để nghe liền mạch: giữ mạch cảm xúc và nhịp, chuyển tiếp mượt, xen kẽ nghệ sĩ quen và mới, tránh lặp nghệ sĩ vừa phát. " +
            "Chỉ trả JSON {\"why\":<1 câu>, \"albums\":[id, đúng 3 id theo thứ tự nghe]}. Chỉ dùng id có trong danh sách. ${Ai.langHint()}"
        val prompt = "Bối cảnh:\n$context\nVừa phát:\n$played\nAlbum:\n${lines.joinToString("\n")}"
        val o = Ai.parseJson(Ai.generate(prompt, sys, json = true, temp = 0.8)) as JSONObject
        AiDj.note.value = o.optString("why")
        val ids = o.optJSONArray("albums") ?: return emptyList()
        return (0 until ids.length()).mapNotNull { pool.getOrNull(ids.optInt(it, -1)) }.flatMap { albumTracks(it) }.filter { it.path !in inQueue }.take(40)
    }
}

/** 3. Gom tên nghệ sĩ/album bị viết nhiều kiểu và thêm dấu tiếng Việt cho tên không dấu — chỉ đề xuất, người dùng duyệt rồi mới áp dụng. */
object AiTags {
    class Fix(val kind: String, val from: List<String>, val to: String, val viaAi: Boolean) {
        var on by androidx.compose.runtime.mutableStateOf(true)
    }

    private fun key(s: String) = aiNorm(s).replace(Regex("[^a-z0-9]"), "")
    private fun accents(s: String) = s.count { it.code > 127 }
    private fun shouting(s: String) = s.any { it.isLetter() } && s == s.uppercase()

    private fun variants(names: List<String>, kind: String): List<Fix> {
        val freq = names.groupingBy { it }.eachCount()
        val out = ArrayList<Fix>()
        for ((_, g) in freq.keys.filter { it.isNotBlank() && key(it).isNotEmpty() }.groupBy { key(it) }) {
            if (g.size < 2) continue
            val best = g.maxWithOrNull(compareBy<String>({ accents(it) }, { !shouting(it) }, { freq[it] ?: 0 }))!!
            out += Fix(kind, g.filter { it != best }, best, false)
        }
        return out
    }

    suspend fun scan(lib: List<Track>): List<Fix> {
        val fixes = ArrayList<Fix>()
        fixes += variants(lib.map { it.artist }, "artist")
        // album: chỉ gom khi cùng nghệ sĩ
        for ((_, ts) in lib.groupBy { key(it.artist) }) fixes += variants(ts.map { it.album }, "album")
        // AI: thêm dấu cho tên nghệ sĩ viết không dấu
        val handled = fixes.filter { it.kind == "artist" }.flatMap { it.from + it.to }.toSet()
        val ascii = lib.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }
            .map { it.key }.filter { it.isNotBlank() && it !in handled && accents(it) == 0 && it.any { c -> c.isLetter() } }
            .take(120)
        for (batch in ascii.chunked(60)) {
            coroutineContext.ensureActive()
            val sys = "Bạn khôi phục dấu tiếng Việt cho tên nghệ sĩ. Với mỗi tên được đưa vào, nếu chắc chắn đó là tên người/ban nhạc Việt viết không dấu thì trả tên đúng có dấu; " +
                "nếu không chắc hoặc không phải tên Việt thì để fix rỗng. Không đổi chữ cái, chỉ thêm dấu. Chỉ trả JSON mảng [{\"n\":<tên gốc>,\"fix\":<tên có dấu hoặc rỗng>}]."
            val res = Ai.parseJson(Ai.generate("Tên:\n${JSONArray(batch)}", sys, json = true, temp = 0.1)) as? JSONArray ?: continue
            val valid = batch.toSet()
            for (i in 0 until res.length()) {
                val o = res.optJSONObject(i) ?: continue
                val n = o.optString("n"); val f = o.optString("fix").trim()
                if (n in valid && f.isNotEmpty() && f != n && aiNorm(f) == aiNorm(n) && accents(f) > 0) fixes += Fix("artist", listOf(n), f, true)
            }
            delay(3000)
        }
        return fixes
    }

    fun apply(fixes: List<Fix>) {
        fixes.filter { it.on }.forEach { TagFix.put(it.kind, it.from, it.to) }
        TagFix.save(); Library.reapply()
    }
    fun undoAll() { TagFix.clear(); Library.reapply() }
}

/** 6. Album bị bỏ quên: album chất lượng cao ít/không được nghe, AI chọn vài album hợp hôm nay. */
object AiForgotten {
    suspend fun run(lib: List<Track>, context: String): List<AiSuggest.Playlist> {
        val now = System.currentTimeMillis()
        val (pool, lines) = albumPool(lib) { all ->
            fun plays(e: Map.Entry<String, List<Track>>) = e.value.sumOf { UsageStats.plays(it.path) }
            fun last(e: Map.Entry<String, List<Track>>) = e.value.maxOf { UsageStats.tracks[it.path]?.last ?: 0L }
            fun hi(e: Map.Entry<String, List<Track>>) = e.value.any { it.bits > 16 || it.sr > 48000 || it.ext == "dsf" || it.ext == "dff" }
            val never = all.filter { plays(it) == 0 }
            val old = all.filter { plays(it) > 0 }.sortedBy { last(it) }.take(35)
            (never.filter { hi(it) }.shuffled().take(55) + never.shuffled().take(30) + old).distinctBy { it.key }
        }
        if (pool.isEmpty()) return emptyList()
        val withInfo = pool.mapIndexed { i, e ->
            val t = e.value.first(); val p = e.value.sumOf { UsageStats.plays(it.path) }
            val l = e.value.maxOf { UsageStats.tracks[it.path]?.last ?: 0L }
            val q = if (t.ext == "dsf" || t.ext == "dff") "DSD" else if (t.bits > 0) "${t.bits}bit/${t.sr / 1000}k" else ""
            "$i|${t.artist}|${t.album}|${t.ext} $q|${if (p == 0) "chưa nghe" else "${((now - l) / 86_400_000L)} ngày trước"}"
        }
        val sys = "Bạn là người giữ kho nhạc hi-fi. Từ các album (id|nghệ sĩ|album|định dạng|lần nghe gần nhất) mà người dùng ít hoặc chưa nghe, chọn 4 album đáng nghe lại nhất hôm nay, hợp bối cảnh. " +
            "Ưu tiên bản thu chất lượng cao hoặc album có giá trị. Chỉ trả JSON {\"picks\":[{\"id\":<id>,\"reason\":<1 câu vì sao nên nghe hôm nay>}]}. Chỉ dùng id có trong danh sách. ${Ai.langHint()}"
        val o = Ai.parseJson(Ai.generate("Bối cảnh:\n$context\nAlbum:\n${withInfo.joinToString("\n")}", sys, json = true, temp = 0.8)) as JSONObject
        val arr = o.optJSONArray("picks") ?: return emptyList()
        val out = ArrayList<AiSuggest.Playlist>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val e = pool.getOrNull(p.optInt("id", -1)) ?: continue
            val t = e.value.first()
            out += AiSuggest.Playlist("${t.artist} — ${t.album}", p.optString("reason"), albumTracks(e))
        }
        return out
    }
}

/** 7. Dịch / giải nghĩa lời bài hát có sẵn của người dùng. */
object AiLyrics {
    suspend fun translate(lyrics: String, t: Track): String {
        val sys = "Bạn dịch lời bài hát sang ${if (Lang.en.value) "tiếng Anh" else "tiếng Việt"}, sát nghĩa, tự nhiên, giữ nguyên số dòng và dòng trống như bản gốc, không thêm chú thích, không lặp lại bản gốc. Chỉ trả bản dịch."
        return Ai.generate("Bài: ${t.title} — ${t.artist}\nLời:\n${lyrics.take(6000)}", sys, temp = 0.3).trim()
    }
    suspend fun meaning(lyrics: String, t: Track): String {
        val sys = "Bạn giải nghĩa ngắn (tối đa 130 từ) nội dung, hình ảnh và cảm xúc chính của bài hát dựa trên lời được đưa vào, bằng lời của bạn. Không trích lại câu lời quá vài từ. ${Ai.langHint()}"
        return Ai.generate("Bài: ${t.title} — ${t.artist}\nLời:\n${lyrics.take(6000)}", sys, temp = 0.5).trim()
    }
}
