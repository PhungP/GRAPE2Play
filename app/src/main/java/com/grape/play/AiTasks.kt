package com.grape.play

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
    class Result(val explain: String, val tracks: List<Track>)

    suspend fun run(query: String, lib: List<Track>): Result {
        val artists = lib.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.take(120).map { it.key }.filter { it.isNotBlank() }
        val formats = lib.map { it.ext }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
        val sys = "Bạn phân tích yêu cầu nghe nhạc thành bộ lọc cho app phát nhạc hi-fi. Chỉ trả JSON dạng " +
            "{\"explain\":<1 câu mô tả bạn hiểu yêu cầu thế nào>, \"artists\":[tên nghệ sĩ, ưu tiên đúng tên trong danh sách có sẵn], \"keywords\":[từ khoá tên bài/album/thư mục], " +
            "\"genres\":[từ: $GENRES], \"formats\":[vd dsd, flac, wav, mp3], \"sort\":<relevance|most_played|least_played|never_played|recent|random>, \"limit\":<số bài 10-120>}. " +
            "Các trường không liên quan để mảng rỗng. Tâm trạng (buồn, nhẹ nhàng, sôi động...) hãy chuyển thành genres/keywords hợp lý. ${Ai.langHint()}"
        val prompt = "Yêu cầu: $query\nBây giờ: ${timeOfDay()}\nĐịnh dạng trong thư viện: ${formats.joinToString()}\nNghệ sĩ có trong thư viện: ${artists.joinToString(" | ")}"
        val o = Ai.parseJson(Ai.generate(prompt, sys, json = true, temp = 0.2)) as JSONObject
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        val terms = (list("artists") + list("keywords")).map { aiNorm(it) }
        val genres = list("genres").map { aiNorm(it) }.filter { it != "other" }
        val fmts = list("formats").map { it.lowercase().removePrefix(".") }
        val limit = o.optInt("limit", 60).coerceIn(10, 200)
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
        return Result(o.optString("explain"), r.take(limit))
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
