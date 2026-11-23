package com.grape.play

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Chọn ảnh bìa bằng AI, CÓ XÁC NHẬN của người dùng:
 *  1. Chỉ chạy khi người dùng bấm tìm. App hỏi 3 nguồn công khai: Deezer (api.deezer.com), MusicBrainz + Cover Art Archive, iTunes Search.
 *  2. App tải từng ảnh, đo độ phân giải / tỉ lệ / ảnh trống (placeholder) ngay trên máy.
 *  3. AI (văn bản, không xem ảnh) nhận CHỈ tên bài/nghệ sĩ/album và các số đo ở trên rồi chọn 1 URL; không gửi thư viện, không gửi ảnh.
 *  4. Người dùng xem trước và bấm đồng ý thì ảnh mới được lưu — chỉ trong app (không ghi vào file nhạc). Không đồng ý thì không lưu gì.
 */
object AiCover {
    class Cand(val source: String, val url: String, val w: Int, val h: Int, val bytes: ByteArray, val placeholder: Boolean, val matched: Boolean) {
        val square: Boolean get() = w > 0 && h > 0 && abs(w - h) <= maxOf(2, min(w, h) / 50)
        val label: String get() = when (source) { "deezer" -> "Deezer"; "musicbrainz" -> "MusicBrainz / Cover Art Archive"; else -> "iTunes" }
    }
    class Result(val cands: List<Cand>, val chosen: Cand?, val reason: String, val placeholder: Boolean, val byAi: Boolean)

    private const val UA = "GRAPEPlay/2.9 (grape2play@gmail.com)"   // MusicBrainz yêu cầu User-Agent có thông tin liên hệ
    private const val MAX_BYTES = 8_000_000

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    private fun unknown(s: String) = s.isBlank() || s.startsWith("Không rõ")
    private fun same(a: String, b: String): Boolean { val x = norm(a); val y = norm(b); return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x)) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(url: String): ByteArray? = Http.client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
        val b = r.body
        if (!r.isSuccessful || b == null || (b.contentLength() > MAX_BYTES)) null else b.bytes().takeIf { it.size in 1..MAX_BYTES }
    }
    private fun json(url: String): JSONObject? = fetch(url)?.let { JSONObject(String(it, Charsets.UTF_8)) }

    /** Đọc kích thước, dựng ảnh thu nhỏ để phát hiện ảnh trống (một màu / gần như không có chi tiết). */
    private fun inspect(source: String, url: String, bytes: ByteArray, matched: Boolean): Cand? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var flat = bytes.size < 3000
        if (!flat) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, min(o.outWidth, o.outHeight) / 96) })
            if (bmp != null) {
                val sm = Bitmap.createScaledBitmap(bmp, 24, 24, true)
                val lum = DoubleArray(24 * 24) { i -> val c = sm.getPixel(i % 24, i / 24); 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255) }
                val mean = lum.average()
                flat = sqrt(lum.sumOf { (it - mean) * (it - mean) } / lum.size) < 8.0
            }
        }
        return Cand(source, url, o.outWidth, o.outHeight, bytes, flat, matched)
    }

    private fun deezer(t: Track, art: String): Cand? {
        val d = json("https://api.deezer.com/search?q=${enc((art + " " + t.title).trim())}&limit=5")?.optJSONArray("data") ?: return null
        var pick: JSONObject? = null; var ok = false
        for (i in 0 until d.length()) {
            val o = d.optJSONObject(i) ?: continue
            val m = same(o.optString("title"), t.title) && (art.isEmpty() || same(o.optJSONObject("artist")?.optString("name").orEmpty(), art))
            if (pick == null || (m && !ok)) { pick = o; ok = m }
            if (ok) break
        }
        val al = pick?.optJSONObject("album") ?: return null
        val url = al.optString("cover_xl").ifEmpty { al.optString("cover_big") }.takeIf { it.isNotEmpty() } ?: return null
        return inspect("deezer", url, fetch(url) ?: return null, ok)
    }

    private fun musicbrainz(t: Track, art: String): Cand? {
        if (norm(t.album).isEmpty()) return null
        val q = "release:\"${t.album.replace("\"", "")}\"" + if (art.isNotEmpty()) " AND artist:\"${art.replace("\"", "")}\"" else ""
        val rel = json("https://musicbrainz.org/ws/2/release/?query=${enc(q)}&fmt=json&limit=5")?.optJSONArray("releases") ?: return null
        for (i in 0 until min(rel.length(), 3)) {
            val o = rel.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val credit = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
            val m = same(o.optString("title"), t.album) && (art.isEmpty() || same(credit, art))
            val url = "https://coverartarchive.org/release/$id/front-500"
            val bytes = runCatching { fetch(url) }.getOrNull() ?: continue
            return inspect("musicbrainz", url, bytes, m)
        }
        return null
    }

    private fun itunes(t: Track, art: String): Cand? {
        val r = json("https://itunes.apple.com/search?term=${enc((t.title + " " + art).trim())}&media=music&entity=song&limit=1")?.optJSONArray("results")?.optJSONObject(0) ?: return null
        val url = r.optString("artworkUrl100").takeIf { it.isNotEmpty() }?.replace("100x100", "600x600") ?: return null
        val m = same(r.optString("trackName"), t.title) && (art.isEmpty() || same(r.optString("artistName"), art))
        return inspect("itunes", url, fetch(url) ?: return null, m)
    }

    suspend fun run(t: Track): Result = withContext(Dispatchers.IO) {
        val art = if (unknown(t.artist)) "" else t.artist
        val cands = coroutineScope {
            listOf(
                async { withTimeoutOrNull(20_000) { runCatching { deezer(t, art) }.getOrNull() } },
                async { withTimeoutOrNull(25_000) { runCatching { musicbrainz(t, art) }.getOrNull() } },
                async { withTimeoutOrNull(20_000) { runCatching { itunes(t, art) }.getOrNull() } },
            ).mapNotNull { it.await() }
        }
        choose(t, art, cands)
    }

    /** Quy tắc cục bộ (dự phòng khi AI không dùng được hoặc trả kết quả không hợp lệ): khớp bài/album → độ phân giải → tỉ lệ vuông. */
    private fun localBest(cands: List<Cand>): Cand? =
        cands.filter { !it.placeholder }.sortedWith(compareByDescending<Cand> { it.matched }.thenByDescending { min(it.w, it.h) }.thenByDescending { it.square }).firstOrNull()

    private suspend fun choose(t: Track, art: String, cands: List<Cand>): Result {
        if (cands.isEmpty()) return Result(cands, null, "Không tìm thấy ảnh bìa nào ở 3 nguồn.", true, false)
        if (cands.all { it.placeholder }) return Result(cands, null, "Cả ảnh tìm được đều là ảnh trống / placeholder.", true, false)
        var ai: Result? = null
        if (Ai.hasKey.value) {
            try {
                val arr = JSONArray()
                cands.forEach { c -> arr.put(JSONObject().put("source", c.source).put("url", c.url).put("width", c.w).put("height", c.h).put("square", c.square)
                    .put("matches_artist_and_title_or_album", c.matched).put("looks_like_placeholder", c.placeholder)) }
                val sys = "Bạn là bộ kiểm tra ảnh bìa. Bạn KHÔNG xem được ảnh; chỉ dựa trên các số đo được cung cấp. Chọn 1 URL tốt nhất theo thứ tự ưu tiên: " +
                    "1) đúng nghệ sĩ/album, không phải ảnh trống; 2) độ phân giải cao nhất; 3) tỉ lệ vuông 1:1. Không bịa URL, chỉ chọn trong danh sách. " +
                    "Nếu tất cả đều là placeholder thì is_placeholder=true và chosen_cover_url rỗng. " +
                    "Chỉ trả JSON: {\"chosen_cover_url\":\"\",\"source\":\"deezer|musicbrainz|itunes\",\"reason\":\"\",\"is_placeholder\":false}"
                val prompt = "Bài: ${t.title}\nNghệ sĩ: ${art.ifEmpty { "(không rõ)" }}\nAlbum: ${t.album}\nCác ảnh ứng viên: $arr"
                val txt = Ai.generate(prompt, sys, json = true, temp = 0.0)
                val o = JSONObject(txt.substring(txt.indexOf('{'), txt.lastIndexOf('}') + 1))
                val url = o.optString("chosen_cover_url")
                val pick = cands.firstOrNull { it.url == url && !it.placeholder }
                if (pick != null) ai = Result(cands, pick, o.optString("reason").ifEmpty { "AI đã chọn ảnh này." }, false, true)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.i("AI", "Chọn ảnh bìa bằng AI lỗi: ${e.message}") }
        }
        if (ai != null) return ai
        val b = localBest(cands)
        return Result(cands, b, if (Ai.hasKey.value) "AI không trả kết quả hợp lệ — chọn theo quy tắc: khớp album/bài, độ phân giải cao nhất, tỉ lệ vuông." else "Chưa có khóa AI — chọn theo quy tắc: khớp album/bài, độ phân giải cao nhất, tỉ lệ vuông.", b == null, false)
    }
}
