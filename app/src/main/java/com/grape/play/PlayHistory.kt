package com.grape.play

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lịch sử nghe đầy đủ (mới nhất trước, tối đa [HistoryLog.MAX] lần nghe, kèm thời điểm). Khác "Phát gần đây" (30 bài, không thời điểm).
 * Lưu bằng [SafePrefStore] (dữ liệu hỏng được giữ nguyên, không bị ghi đè bằng lịch sử rỗng). Không ảnh hưởng đường phát nhạc.
 */
object PlayHistory {
    val entries = MutableStateFlow<List<HistoryLog.Entry>>(emptyList())
    private lateinit var safe: SafePrefStore

    @Synchronized fun init(ctx: Context) {
        if (::safe.isInitialized) return
        safe = SafePrefStore(ctx.applicationContext, "history", HistoryLog::isValid, "History")
        entries.value = safe.load().value?.let { HistoryLog.decode(it) } ?: emptyList()
    }

    /** Ghi một lần nghe: bài bắt đầu được phát (không gồm bài chỉ được khôi phục ở trạng thái tạm dừng). */
    @Synchronized fun add(path: String) {
        if (!::safe.isInitialized) return
        val n = HistoryLog.push(entries.value, path, System.currentTimeMillis())
        entries.value = n
        safe.writeAsync(HistoryLog.encode(n))
    }

    @Synchronized fun clear() {
        if (!::safe.isInitialized) return
        entries.value = emptyList(); safe.writeAsync(HistoryLog.encode(emptyList()))
    }

    @Synchronized fun remap(m: Map<String, String>) {
        if (!::safe.isInitialized || entries.value.none { it.path in m }) return
        val n = HistoryLog.remap(entries.value, m); entries.value = n; safe.writeAsync(HistoryLog.encode(n))
    }

    /** Các lần nghe còn bài trong thư viện, mới nhất trước (bài đã bị xoá khỏi thư viện bị ẩn nhưng vẫn nằm trong lịch sử). */
    fun resolve(all: List<Track>): List<Pair<Track, Long>> {
        val m = HashMap<String, Track>(all.size * 2); for (t in all) m.putIfAbsent(t.path, t)
        return entries.value.mapNotNull { e -> m[e.path]?.let { it to e.at } }
    }
}
