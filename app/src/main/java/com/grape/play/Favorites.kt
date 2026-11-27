package com.grape.play

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Danh sách bài hát yêu thích (lưu đường dẫn bài). Thứ tự = thứ tự thêm; khi hiển thị, bài mới thêm lên đầu. */
object Favorites {
    private lateinit var store: SafePrefStore
    private lateinit var app: Context
    val paths = mutableStateOf<Set<String>>(emptySet())

    /** Dữ liệu hỏng không bao giờ bị biến thành danh sách rỗng rồi ghi đè: xem [SafeJsonStore]. */
    fun init(ctx: Context) {
        if (::store.isInitialized) return
        app = ctx.applicationContext
        store = SafePrefStore(app, "favorites", PersistCodec::isStrings, "Favorites")
        paths.value = LinkedHashSet(PersistCodec.decodeStrings(store.load().value ?: "[]") ?: emptyList())
    }

    fun isFav(path: String): Boolean = path in paths.value

    /** Thêm/bỏ yêu thích. Trả về true nếu bài vừa được thêm. */
    @Synchronized fun toggle(t: Track): Boolean {
        val s = LinkedHashSet(paths.value)
        val added = if (t.path in s) { s.remove(t.path); false } else { s.add(t.path); true }
        paths.value = s
        if (::store.isInitialized) {
            store.writeAsync(PersistCodec.encodeStrings(s.toList()))
            TvHome.schedule(app, 2000)
        }
        return added
    }

    /** Đổi đường dẫn khi file được di chuyển/đổi tên (đã đối chiếu an toàn ở [Library]). Giữ nguyên thứ tự; không xóa mục nào. */
    @Synchronized fun remap(m: Map<String, String>) {
        if (!::store.isInitialized || paths.value.none { it in m }) return
        val s = LinkedHashSet<String>(); paths.value.forEach { s.add(m[it] ?: it) }
        paths.value = s
        store.writeAsync(PersistCodec.encodeStrings(s.toList()))
    }

    /** Các bài yêu thích có trong thư viện, bài mới thêm đứng đầu. */
    fun tracks(all: List<Track>): List<Track> {
        val m = HashMap<String, Track>(all.size * 2)
        for (t in all) m.putIfAbsent(t.path, t)
        return paths.value.reversed().mapNotNull { m[it] }
    }
}

/** 30 bài phát gần nhất (dùng cho hàng "Phát gần đây" trên trang chủ TV). */
object Recents {
    private lateinit var store: SafePrefStore
    private var list: List<String> = emptyList()

    @Synchronized fun init(ctx: Context) {
        if (::store.isInitialized) return
        store = SafePrefStore(ctx.applicationContext, "recents", PersistCodec::isStrings, "Recents")
        list = PersistCodec.decodeStrings(store.load().value ?: "[]") ?: emptyList()
    }

    @Synchronized fun add(path: String) {
        if (!::store.isInitialized) return
        list = PersistCodec.pushRecent(list, path, 30)
        store.writeAsync(PersistCodec.encodeStrings(list))
    }

    @Synchronized fun remap(m: Map<String, String>) {
        if (!::store.isInitialized || list.none { it in m }) return
        list = list.map { m[it] ?: it }.distinct()
        store.writeAsync(PersistCodec.encodeStrings(list))
    }

    @Synchronized fun tracks(all: List<Track>): List<Track> {
        val m = HashMap<String, Track>(all.size * 2)
        for (t in all) m.putIfAbsent(t.path, t)
        return list.mapNotNull { m[it] }
    }
}
