package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Kho hồ sơ thiết bị Bit-perfect (User Device Profile) + trạng thái xác nhận. Lưu cục bộ (SharedPreferences "devices"), không mạng.
 * Logic đối chiếu/quyết định nằm trong DeviceRegistry.java (có test); lớp này chỉ lưu trữ và nối với UI. Không đụng vào Playback.kt/Audio Engine.
 */
object DeviceProfiles {
    /** Thiết bị đang chờ người dùng xác nhận. [declare] = đang ở bước khai báo thiết bị (không khớp danh mục, hoặc người dùng nói "không đúng"). */
    class Pending(val det: DeviceRegistry.Detected, val match: DeviceRegistry.Match, val ask: DeviceRegistry.Ask, val declare: Boolean = false)

    val regs: List<DeviceRegistry.Entry> = DeviceRegistry.builtin()
    val pending = MutableStateFlow<Pending?>(null)
    /** Tăng mỗi khi hồ sơ đổi — để UI/BitPerfect tính lại. */
    val version = MutableStateFlow(0)
    val manage = MutableStateFlow(false)
    var store = DeviceRegistry.Store(); private set
    private lateinit var sp: SharedPreferences
    private var lastKey = ""

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("devices", Context.MODE_PRIVATE)
        store = runCatching { DeviceRegistry.parse(sp.getString("store", "") ?: "") }.getOrDefault(DeviceRegistry.Store())
    }
    private fun save() {
        if (::sp.isInitialized) sp.edit().putString("store", DeviceRegistry.serialize(store)).apply()
        version.value = version.value + 1
    }

    /** Lưu lại sau khi Store bị sửa từ bên ngoài (ví dụ liên kết danh mục). */
    fun persist() = save()
    /** Lưu kết quả người dùng tự kiểm tra (User Verified); cập nhật firmware hồ sơ nếu người dùng nhập. Không liên quan tới danh tính thiết bị. */
    fun addVerification(v: DeviceVerification.UserVerification, firmware: String) {
        store.verifications.add(v)
        store.find(v.profileKey)?.let { if (firmware.isNotEmpty()) it.firmware = firmware }
        save()
    }
    fun removeVerification(v: DeviceVerification.UserVerification) { store.verifications.remove(v); save() }

    fun applyTo(x: BitPerfect.Input, det: DeviceRegistry.Detected) = DeviceRegistry.apply(x, det, regs, store)

    /**
     * Kiểm tra thiết bị hiện tại và (nếu cần) đặt hộp thoại xác nhận. Gọi khi khởi động, khi thiết bị âm thanh thay đổi (AudioDeviceCallback) và khi người dùng đổi đầu ra.
     * Không hỏi lại thiết bị đã lưu / đã từ chối / đã bỏ qua; không hỏi khi đang có hộp thoại khác chờ.
     */
    fun check(ctx: Context) {
        if (!::sp.isInitialized) init(ctx)
        if (pending.value != null) return
        val det = runCatching { BitPerfectProbe.detected(ctx) }.getOrNull() ?: return
        val m = DeviceRegistry.match(det, regs)
        val ask = DeviceRegistry.decide(det, m, store)
        if (ask == DeviceRegistry.Ask.NONE) { lastKey = ""; return }
        val key = DeviceRegistry.keyOf(det)
        if (key == lastKey) return                     // đã hỏi trong phiên này (người dùng chưa trả lời/đóng): không lặp lại
        lastKey = key
        pending.value = Pending(det, m, ask)
    }

    fun confirmYes(p: Pending) { DeviceRegistry.confirm(store, p.det, p.match, System.currentTimeMillis()); save(); pending.value = null }
    /** "Không đúng thiết bị": không đăng ký; chuyển sang bước cho người dùng khai báo/chọn lại. */
    fun confirmNo(p: Pending) { DeviceRegistry.reject(store, p.det, p.match); save(); pending.value = Pending(p.det, DeviceRegistry.Match(), DeviceRegistry.Ask.UNKNOWN_DEVICE, declare = true) }
    fun later(p: Pending) { DeviceRegistry.dismiss(store, p.det); save(); pending.value = null }
    fun declare(det: DeviceRegistry.Detected, maker: String, model: String, notes: String) {
        DeviceRegistry.declare(store, det, maker, model, notes, null, null, System.currentTimeMillis()); save(); pending.value = null
    }
    fun edit(key: String, maker: String, model: String, notes: String) {
        val p = store.find(key) ?: return
        if (p.source != DeviceRegistry.SRC_USER) return      // dữ liệu app phát hiện/danh mục không bị người dùng ghi đè
        p.manufacturer = maker.trim(); p.model = model.trim(); p.notes = notes.trim(); save()
    }
    fun remove(key: String) { store.remove(key); lastKey = ""; save() }
}
