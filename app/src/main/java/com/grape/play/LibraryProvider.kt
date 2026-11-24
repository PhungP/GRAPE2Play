package com.grape.play

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Cho launcher đọc tối đa 12 album (phát gần đây / yêu thích / nổi bật): content://com.grape.play.library/albums
 * Cột: _id, title, artist, cover (BLOB JPEG, cạnh dài ≤ 256 px). Chỉ đọc; chỉ trả tên và ảnh nhỏ, không trả đường dẫn file/nguồn.
 * Ảnh dùng thumbnail nén sẵn (filesDir/libcovers); chưa có thì tạo từ Covers.get với giới hạn thời gian, quá hạn thì để cover = NULL.
 */
class LibraryProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.grape.play.library"
        private const val MAX_ROWS = 12
        private const val THUMB_PX = 256
        private const val BUDGET_MS = 3500L          // tổng thời gian tạo thumbnail mới trong một lần query
        private const val ALBUMS = 1
    }
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply { addURI(AUTHORITY, "albums", ALBUMS) }

    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = if (matcher.match(uri) == ALBUMS) "vnd.android.cursor.dir/vnd.com.grape.play.album" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun known(s: String) = s.isNotBlank() && !s.startsWith("Không rõ")

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (matcher.match(uri) != ALBUMS) return null
        val ctx = context?.applicationContext ?: return null
        val cur = MatrixCursor(arrayOf("_id", "title", "artist", "cover"))
        try {
            // Khởi tạo nhẹ các kho dữ liệu (đã idempotent) rồi đọc thư viện từ cache nếu tiến trình mới được đánh thức
            SourceStore.init(ctx); Covers.init(ctx); Favorites.init(ctx); Recents.init(ctx)
            var all = Library.tracks.value
            if (all.isEmpty()) { runBlocking { Library.loadCacheOnly(ctx) }; all = Library.tracks.value }
            if (all.isEmpty()) return cur
            // Thứ tự ưu tiên: phát gần đây → yêu thích → phần còn lại của thư viện; mỗi album chỉ một dòng
            val seen = HashSet<String>(); val reps = ArrayList<Track>()
            fun take(l: List<Track>) { for (t in l) { if (reps.size >= MAX_ROWS) return; if (!known(t.album)) continue
                if (seen.add((t.albumArtist.ifEmpty { t.artist } + "|" + t.album).lowercase())) reps.add(t) } }
            take(Recents.tracks(all)); take(Favorites.tracks(all)); take(all)
            val start = android.os.SystemClock.elapsedRealtime()
            val dir = File(ctx.filesDir, "libcovers").apply { mkdirs() }
            reps.forEachIndexed { i, t ->
                val f = File(dir, Integer.toHexString((t.src + "|" + t.folder + "|" + t.album).hashCode()) + ".jpg")
                if (!f.exists() && android.os.SystemClock.elapsedRealtime() - start < BUDGET_MS) makeThumb(t, f)
                val blob = if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
                cur.addRow(arrayOf<Any?>(i.toLong(), t.album, t.albumArtist.ifEmpty { t.artist }, blob))
            }
        } catch (e: Exception) { AppLog.w("Provider", "query albums lỗi: ${e.message}") }
        return cur
    }

    /** Tạo thumbnail JPEG ≤ 256 px; ảnh gốc chỉ do Covers.get đã giải mã sẵn (có giới hạn thời gian). Không có ảnh thì bỏ qua. */
    private fun makeThumb(t: Track, f: File) {
        runCatching {
            val bmp = runBlocking { withTimeoutOrNull(1200) { Covers.get(t) } } ?: return
            val k = THUMB_PX.toFloat() / maxOf(bmp.width, bmp.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
            val tmp = File(f.path + ".tmp")
            tmp.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            if (!tmp.renameTo(f)) tmp.delete()
        }
    }
}
