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

    /** Ghi một lần, KHÔNG gộp, và xoá bản sao lưu cũ (dùng khi di trú bí mật plaintext → mã hoá). Chạy trên luồng ghi chung, không chặn UI. */
    fun writeScrubbed(raw: String) {
        exec.execute { try { store.write(raw, true) } catch (e: Throwable) { AppLog.w("Data", "$tag: lỗi khi ghi (xoá bản sao lưu plaintext)", e) } }
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
