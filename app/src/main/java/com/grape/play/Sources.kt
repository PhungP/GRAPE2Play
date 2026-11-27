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
    /** Nguồn có mật khẩu ĐÃ MÃ HOÁ nhưng không giải mã được (khoá Keystore bị mất/hỏng): bản mã được GIỮ NGUYÊN trong kho cho tới khi người dùng nhập mật khẩu mới — không bị ghi đè âm thầm. */
    val undecryptable = MutableStateFlow<Set<String>>(emptySet())
    private val lostBlobs = HashMap<String, String>()

    /** Danh sách nguồn (kèm thông tin đăng nhập) hỏng không bao giờ bị biến thành rỗng rồi ghi đè: xem [SafeJsonStore]. */
    @Synchronized fun init(ctx: Context) {
        if (::safe.isInitialized) return
        safe = SafePrefStore(ctx.applicationContext, "sources", PersistCodec::isSources, "Sources")
        val recs = safe.load().value?.let { PersistCodec.decodeSources(it) } ?: emptyList()
        val srcs = recs.map { r ->
            val p = r["p"].orEmpty(); var pass = ""
            if (p.isNotEmpty()) { val d = SecretBox.decrypt(p); if (d == null) lostBlobs[r.getValue("id")] = p else pass = d }
            fromRecord(r, pass)
        }
        list.value = srcs
        undecryptable.value = lostBlobs.keys.toSet()
        if (lostBlobs.isNotEmpty()) AppLog.w("Sources", "${lostBlobs.size} nguồn có mật khẩu đã mã hoá nhưng không giải mã được (khoá bảo mật của máy đã mất) — giữ nguyên bản mã, cần nhập lại mật khẩu")
        migrateSecrets(recs)
    }

    /**
     * Di trú mật khẩu plaintext → mã hoá: ghi bản mã, ĐỌC LẠI, giải mã đối chiếu từng mật khẩu, chỉ sau đó mới xoá bản sao lưu plaintext ([SafePrefStore.migrate]).
     * Nếu lần trước dừng dở (bản chính đã mã hoá nhưng .bak còn plaintext) thì dọn khi bản chính xác minh đạt. Không đủ điều kiện (Keystore lỗi) thì giữ nguyên.
     */
    private fun migrateSecrets(recs: List<Map<String, String>>) {
        val expected = list.value.filter { it.pass.isNotEmpty() }.associate { it.id to it.pass }
        val lost = lostBlobs.keys.toSet()
        val verify = { stored: String -> SecretMigration.verifyStored(PersistCodec.decodeSources(stored), "id", "p", expected, lost) { v -> SecretBox.decrypt(v) } }
        if (SecretMigration.hasPlain(recs, "p") { SecretBox.isEncrypted(it) }) {
            if (list.value.any { it.pass.isNotEmpty() && SecretBox.encrypt(it.pass) == null }) { AppLog.w("Sources", "Keystore không dùng được — giữ nguyên mật khẩu dạng cũ"); return }
            safe.migrate(PersistCodec.encodeRecords(list.value.map { toRecord(it) }), verify)
        } else safe.scrubStaleBackup({ bak -> PersistCodec.decodeSources(bak)?.let { r -> SecretMigration.hasPlain(r, "p") { SecretBox.isEncrypted(it) } } == true }, verify)
    }

    fun get(id: String) = list.value.firstOrNull { it.id == id }
    fun name(id: String) = get(id)?.name ?: "NAS"
    // Đọc-sửa-ghi trên danh sách: đồng bộ vì xoá nguồn chạy ở luồng nền (Library.removeSource) còn thêm/sửa chạy ở luồng UI
    @Synchronized fun add(s: Source) { list.value = list.value + s; gotNewPass(s); save() }
    @Synchronized fun update(s: Source) { list.value = list.value.map { if (it.id == s.id) s else it }; gotNewPass(s); save() }
    @Synchronized fun remove(id: String) { list.value = list.value.filter { it.id != id }; lostBlobs.remove(id); undecryptable.value = undecryptable.value - id; save() }
    /** Người dùng nhập mật khẩu mới cho nguồn đang "mất khoá": bản mã cũ không còn cần giữ. */
    private fun gotNewPass(s: Source) { if (s.pass.isNotEmpty() && lostBlobs.remove(s.id) != null) undecryptable.value = undecryptable.value - s.id }

    private fun save() {
        if (!::safe.isInitialized) return
        safe.writeAsync(PersistCodec.encodeRecords(list.value.map { toRecord(it) }))
    }
    /** Trường mật khẩu khi lưu: mã hoá (đã kiểm giải mã lại); nếu Keystore lỗi giữ bản rõ; nguồn "mất khoá" mà chưa nhập mật khẩu mới thì ghi lại ĐÚNG bản mã cũ. */
    private fun passField(s: Source): String = if (s.pass.isEmpty()) (lostBlobs[s.id] ?: "") else (SecretBox.encrypt(s.pass) ?: s.pass)
    private fun toRecord(it: Source): Map<String, String> = linkedMapOf("id" to it.id, "t" to it.type, "n" to it.name, "h" to it.host, "s" to it.share, "r" to it.root, "u" to it.url, "us" to it.user, "p" to passField(it))
    private fun fromRecord(o: Map<String, String>, pass: String) = Source(o.getValue("id"), o.getValue("t"), o.getValue("n"), o["h"].orEmpty(), o["s"].orEmpty(), o["r"].orEmpty(), o["u"].orEmpty(), o["us"].orEmpty(), pass)
}
