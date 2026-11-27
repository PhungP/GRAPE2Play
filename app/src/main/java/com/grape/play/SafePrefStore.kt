package com.grape.play

import android.content.Context
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Gắn [SafeJsonStore] vào một SharedPreferences (cùng tên file, cùng khoá "v" như trước — không đổi dữ liệu hiện có).
 * Đọc: gọi [load] một lần lúc khởi tạo. Ghi: [writeAsync] chụp bản mới nhất và ghi bằng commit() trên MỘT luồng nền chung
 * (không bao giờ chặn luồng chính; các lần ghi được tuần tự hoá, bản mới nhất luôn thắng, không mất cập nhật vì mỗi lần ghi là toàn bộ trạng thái).
 * Ghi thất bại (commit=false, ổ đầy…) → dữ liệu vẫn nằm trong RAM của người gọi và được THỬ LẠI tối đa [LatestWriter.MAX_ATTEMPTS] lần, cách nhau vài giây;
 * kho bị khoá (dữ liệu hỏng) thì không thử lại.
 */
class SafePrefStore(ctx: Context, prefName: String, validator: SafeJsonStore.Validator, private val tag: String, private val mainKey: String = SafeJsonStore.KEY) {
    private val sp = ctx.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val writer = LatestWriter()
    private val store = SafeJsonStore(object : SafeJsonStore.Backend {
        /** Khoá chính của kho này (mặc định "v"); bản sao lưu/bản hỏng là "<khoá>.bak"/"<khoá>.corrupt". */
        private fun k(key: String) = when (key) { SafeJsonStore.KEY -> mainKey; SafeJsonStore.BAK -> "$mainKey.bak"; SafeJsonStore.CORRUPT_KEY -> "$mainKey.corrupt"; else -> key }
        override fun get(key: String): String? = sp.getString(k(key), null)
        override fun putAll(kv: MutableMap<String, String>): Boolean {
            val ed = sp.edit()
            for ((key, v) in kv) if (v == null) ed.remove(k(key)) else ed.putString(k(key), v)
            return ed.commit()
        }
    }, validator, { m -> AppLog.w("Data", m) }, tag)

    fun load(): SafeJsonStore.Loaded = store.load()

    private val sink = object : LatestWriter.Sink {
        override fun write(raw: String) = store.write(raw)
        override fun canRetry() = store.isWritable
    }

    /**
     * Di trú bí mật plaintext → mã hoá, theo thứ tự an toàn (trên luồng ghi chung, không chặn UI):
     *  1) ghi bản MỚI (bản cũ plaintext chuyển sang .bak, chưa xoá); 2) đọc lại bản chính từ kho; 3) [verify] giải mã đối chiếu với bản rõ gốc;
     *  4) chỉ khi (3) đạt mới xoá .bak plaintext. Bất kỳ bước nào lỗi: giữ nguyên .bak (không mất bí mật) và ghi nhật ký.
     */
    fun migrate(raw: String, verify: (String) -> Boolean) {
        exec.execute {
            try {
                if (!store.write(raw)) { AppLog.w("Data", "$tag: di trú bí mật — ghi thất bại, giữ nguyên dữ liệu cũ"); return@execute }
                val back = store.peekMain()
                if (back != null && verify(back)) {
                    if (store.scrubBackup()) AppLog.i("Data", "$tag: di trú bí mật xong, đã xoá bản sao lưu plaintext")
                    else AppLog.w("Data", "$tag: đã mã hoá nhưng chưa xoá được bản sao lưu cũ — sẽ thử lại lần khởi động sau")
                } else AppLog.w("Data", "$tag: đọc lại sau khi ghi không khớp — GIỮ bản sao lưu cũ, không xoá gì")
            } catch (e: Throwable) { AppLog.w("Data", "$tag: lỗi di trú bí mật", e) }
        }
    }

    /** Dọn bản sao lưu còn plaintext từ lần di trú dở dang (tiến trình bị dừng giữa chừng): chỉ khi bản chính đã xác minh đạt. */
    fun scrubStaleBackup(backupIsPlain: (String) -> Boolean, mainVerified: (String) -> Boolean) {
        exec.execute {
            try {
                val bak = store.peekBackup(); val main = store.peekMain()
                if (bak != null && main != null && backupIsPlain(bak) && mainVerified(main) && store.scrubBackup()) AppLog.i("Data", "$tag: đã dọn bản sao lưu plaintext còn sót")
            } catch (e: Throwable) { AppLog.w("Data", "$tag: lỗi dọn sao lưu plaintext", e) }
        }
    }

    fun writeAsync(raw: String) {
        writer.submit(raw)
        exec.execute { step() }
    }

    /** Chạy trên luồng ghi duy nhất. Ngoại lệ không mong đợi không làm chết luồng. */
    private fun step() {
        val again = try { writer.runOnce(sink) } catch (e: Throwable) { AppLog.w("Data", "$tag: lỗi không mong đợi khi ghi", e); false }
        if (again) exec.schedule({ step() }, 3, TimeUnit.SECONDS)
    }

    companion object {
        private val exec = ScheduledThreadPoolExecutor(1) { r -> Thread(r, "SafePrefStore").apply { isDaemon = true } }
    }
}
