package com.grape.play

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** type: "smb" | "dav" | "dlna" | "gdrive" | "onedrive".  smb: host+share+root.  dav: url.  dlna: url = ContentDirectory control URL.
 *  gdrive/onedrive: pass = refresh token, user = tài khoản, root = id thư mục gốc, host = "personal"/"work", url = đường dẫn thư mục (để hiển thị). */
data class Source(
    val id: String, val type: String, val name: String,
    val host: String = "", val share: String = "", val root: String = "",
    val url: String = "", val user: String = "", val pass: String = "",
)

object SourceStore {
    private lateinit var safe: SafePrefStore
    val list = MutableStateFlow<List<Source>>(emptyList())

    /** Danh sách nguồn (kèm thông tin đăng nhập) hỏng không bao giờ bị biến thành rỗng rồi ghi đè: xem [SafeJsonStore]. */
    @Synchronized fun init(ctx: Context) {
        if (::safe.isInitialized) return
        safe = SafePrefStore(ctx.applicationContext, "sources", PersistCodec::isSources, "Sources")
        list.value = safe.load().value?.let { PersistCodec.decodeSources(it) }?.map { fromRecord(it) } ?: emptyList()
    }
    fun get(id: String) = list.value.firstOrNull { it.id == id }
    fun name(id: String) = get(id)?.name ?: "NAS"
    // Đọc-sửa-ghi trên danh sách: đồng bộ vì xoá nguồn chạy ở luồng nền (Library.removeSource) còn thêm/sửa chạy ở luồng UI
    @Synchronized fun add(s: Source) { list.value = list.value + s; save() }
    @Synchronized fun update(s: Source) { list.value = list.value.map { if (it.id == s.id) s else it }; save() }
    @Synchronized fun remove(id: String) { list.value = list.value.filter { it.id != id }; save() }

    private fun save() {
        if (!::safe.isInitialized) return
        safe.writeAsync(PersistCodec.encodeRecords(list.value.map { toRecord(it) }))
    }
    private fun toRecord(it: Source): Map<String, String> = linkedMapOf("id" to it.id, "t" to it.type, "n" to it.name, "h" to it.host, "s" to it.share, "r" to it.root, "u" to it.url, "us" to it.user, "p" to it.pass)
    private fun fromRecord(o: Map<String, String>) = Source(o.getValue("id"), o.getValue("t"), o.getValue("n"), o["h"].orEmpty(), o["s"].orEmpty(), o["r"].orEmpty(), o["u"].orEmpty(), o["us"].orEmpty(), o["p"].orEmpty())
}
