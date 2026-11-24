package com.grape.play

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object AudioSettings {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { if (!::sp.isInitialized) sp = ctx.getSharedPreferences("audio", Context.MODE_PRIVATE) }

    /** false = PCM 16-bit (tương thích, mặc định); true = giữ 24-bit dạng float 32-bit. */
    var hires: Boolean
        get() = sp.getBoolean("hires", false)
        set(v) { sp.edit().putBoolean("hires", v).apply() }
    /** Gapless: nạp trước bài kế tiếp thật sớm (bộ đệm 90 giây) để qua bài liên tục, không có khoảng chờ. Mặc định bật. */
    var gapless: Boolean
        get() = if (::sp.isInitialized) sp.getBoolean("gapless", true) else true
        set(v) { sp.edit().putBoolean("gapless", v).apply() }
    /** Tốc độ PCM khi chuyển DSD → PCM (Hz): 44100 / 88200 / 176400. */
    var dsdRate: Int
        get() = if (::sp.isInitialized) sp.getInt("dsdRate", 176400) else 176400
        set(v) { sp.edit().putInt("dsdRate", v).apply() }
    /**
     * Tốc độ DSD→PCM thực dùng cho đầu ra hiện tại = min(lựa chọn của người dùng, mức đầu ra này từng nhận được).
     * Khi một đầu ra (ví dụ cổng quang) không nhận 176,4 kHz, app tự hạ và nhớ riêng cho đầu ra đó, không làm đổi lựa chọn chung.
     */
    fun effectiveDsdRate(): Int = minOf(dsdRate, if (::sp.isInitialized) sp.getInt("dsdLimit_" + limitId(), Int.MAX_VALUE) else Int.MAX_VALUE)
    /** Hạ một bậc (176,4 → 88,2 → 44,1 kHz) cho đầu ra hiện tại; trả về tốc độ mới hoặc null nếu đã ở mức thấp nhất. */
    fun lowerDsdRate(): Int? {
        val next = when { effectiveDsdRate() > 88200 -> 88200; effectiveDsdRate() > 44100 -> 44100; else -> return null }
        sp.edit().putInt("dsdLimit_" + limitId(), next).apply()
        return next
    }
    /** Quên các mức đã hạ (khi người dùng chủ động chọn lại tốc độ DSD trong Cài đặt). */
    fun resetDsdLimits() { sp.edit().apply { sp.all.keys.filter { it.startsWith("dsdLimit_") }.forEach { remove(it) } }.apply() }
    /** Cách gửi DSD tới thiết bị UPnP: DSD_RAW = file DSF/DFF gốc; DSD_DOP = DSD64 gói trong PCM 24-bit/176,4 kHz. */
    const val DSD_RAW = 0
    const val DSD_DOP = 1
    var dsdRemote: Int
        get() = when {
            !::sp.isInitialized -> DSD_RAW
            sp.contains("dsdRemote") -> if (sp.getInt("dsdRemote", DSD_RAW) == DSD_DOP) DSD_DOP else DSD_RAW   // 2 (PCM, bản 0.8.2) đã bỏ → file gốc
            sp.getBoolean("dopRemote", false) -> DSD_DOP      // tuỳ chọn cũ (bản ≤ 0.8.1)
            else -> DSD_RAW
        }
        set(v) { sp.edit().putInt("dsdRemote", if (v == DSD_DOP) DSD_DOP else DSD_RAW).apply() }
    var dopRemote: Boolean
        get() = dsdRemote == DSD_DOP
        set(v) { dsdRemote = if (v) DSD_DOP else DSD_RAW }
    // ---------- Chọn thiết bị đầu ra ----------
    // Khoá thiết bị = "loại|địa chỉ|tên" (địa chỉ chỉ có từ Android 9; trên Android 8 chỉ còn loại + tên). Không dùng AudioDeviceInfo.id vì id đổi sau mỗi lần cắm lại,
    // và không chỉ dựa vào TYPE_* vì có thể cắm nhiều USB DAC / HDMI / Bluetooth cùng loại.
    /** Khoá thiết bị đầu ra ưu tiên, "" = mặc định hệ thống. */
    var deviceKey: String
        get() = when {
            !::sp.isInitialized -> ""
            sp.contains("devKey") -> sp.getString("devKey", "") ?: ""
            sp.getInt("devType", 0) != 0 -> "${sp.getInt("devType", 0)}||"      // lựa chọn lưu theo bản cũ (chỉ có loại)
            else -> ""
        }
        set(v) { sp.edit().putString("devKey", v).apply() }
    /** Loại của thiết bị đã chọn (AudioDeviceInfo.TYPE_*), 0 = mặc định hệ thống. */
    val deviceType: Int get() = typeOf(deviceKey)
    private fun limitId() = if (deviceKey.isEmpty()) "0" else deviceKey

    private fun dev(d: AudioDeviceInfo) = DeviceMatch.Dev(d.type, if (Build.VERSION.SDK_INT >= 28) runCatching { d.address }.getOrDefault("") else "", d.productName?.toString().orEmpty())
    fun keyOf(d: AudioDeviceInfo): String = DeviceMatch.keyOf(dev(d))
    fun typeOf(key: String): Int = DeviceMatch.typeOf(key)
    private fun nameOf(key: String) = key.split('|', limit = 3).getOrElse(2) { "" }

    /** Tìm thiết bị đang kết nối khớp khoá đã lưu (xem DeviceMatch). Không khớp thì null → dùng mặc định hệ thống. */
    fun match(key: String, list: List<AudioDeviceInfo>): AudioDeviceInfo? =
        DeviceMatch.match(key, list.map { dev(it) }.toTypedArray()).let { if (it < 0) null else list[it] }

    /** Đầu ra mặc định khi mở app: "" = giữ lựa chọn lần trước; DEFAULT = mặc định hệ thống; còn lại = khoá thiết bị. */
    const val DEFAULT = "default"
    var startupKey: String
        get() = when {
            !::sp.isInitialized -> ""
            sp.contains("startupKey") -> sp.getString("startupKey", "") ?: ""
            sp.getInt("startupDev", -1) == 0 -> DEFAULT                       // khoá cũ: 0 = mặc định hệ thống
            sp.getInt("startupDev", -1) > 0 -> "${sp.getInt("startupDev", -1)}||"
            else -> ""
        }
        set(v) { sp.edit().putString("startupKey", v).apply() }
    /** Gọi một lần khi app khởi động, trước khi dựng bộ phát. */
    fun applyStartup() { val k = startupKey; if (k.isNotEmpty()) deviceKey = if (k == DEFAULT) "" else k }

    fun label(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> if (Device.tv) "Loa TV" else "Loa điện thoại"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Loa thoại (nghe gần)"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analogue (line)"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital / Quang (S/PDIF)"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Tai nghe có dây"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        else -> "Thiết bị loại $type"
    }
    /** Mọi đầu ra đang kết nối, KHÔNG gộp thiết bị cùng loại; sắp xếp ổn định để danh sách không nhảy khi cắm/rút. */
    fun sinks(ctx: Context): List<AudioDeviceInfo> =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            .sortedWith(compareBy({ it.type }, { it.productName?.toString().orEmpty() }, { keyOf(it) }))
    fun current(ctx: Context): AudioDeviceInfo? = match(deviceKey, sinks(ctx))
    /** Tên đầy đủ: khi có nhiều thiết bị cùng loại thì thêm tên sản phẩm để phân biệt. */
    fun title(d: AudioDeviceInfo, all: List<AudioDeviceInfo>): String {
        val name = d.productName?.toString()?.takeIf { it.isNotBlank() }
        return if (name != null && all.count { it.type == d.type } > 1) label(d.type) + " — " + name else label(d.type)
    }
    fun currentName(ctx: Context): String {
        val key = deviceKey
        if (key.isEmpty()) return "Mặc định hệ thống"
        val list = sinks(ctx)
        val d = match(key, list)
        val base = d?.let { title(it, list) } ?: (label(typeOf(key)) + nameOf(key).let { if (it.isNotEmpty()) " — $it" else "" })
        return if (d == null) "$base (chưa kết nối)" else base   // chưa cắm: tạm dùng mặc định hệ thống, tự chuyển khi cắm lại
    }
}
