package com.grape.play

import android.content.Context
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.C

/**
 * Thu thập dữ liệu để đánh giá Bit-perfect (chỉ ĐỌC: không chạm vào luồng âm thanh, bộ giải mã, DSP hay bộ xuất âm thanh).
 * Nguồn dữ liệu và độ tin cậy:
 *  - Track/AudioInfo: định dạng file (thẻ + đầu dò file).
 *  - Playback.sinkFmt: định dạng PCM mà bộ giải mã đưa vào AudioSink (ForwardingAudioSink.configure) — do app quan sát. encoding lấy ĐÚNG từ đây (16/24/32/Float),
 *    KHÔNG suy luận từ AudioSettings.hires. Đây KHÔNG phải định dạng AudioTrack/Android/DAC thực tế (app không đọc được).
 *  - AudioSettings.hires: chỉ là CẤU HÌNH app (yêu cầu đầu ra float); không nói AudioTrack thực sự dùng gì.
 *  - Thiết bị ĐANG ĐỊNH TUYẾN: chỉ xác định được bằng AudioManager.getAudioDevicesForAttributes (API 33+) khi Android báo đúng một thiết bị và khớp lựa chọn của người dùng (nếu có).
 *    Ngược lại → "System Default – Route Unknown". Thiết bị người dùng chọn (AudioSettings.current) ≠ thiết bị đang định tuyến. Không suy từ tên hay danh sách thiết bị.
 *  - AudioDeviceInfo (sampleRates, encodings): KHẢ NĂNG thiết bị công bố — không phải định dạng đang chạy, và không là bằng chứng chuyển đổi.
 *  - AudioManager PROPERTY_OUTPUT_SAMPLE_RATE: tần số mixer chính Android báo — gợi ý có chuyển tần số (Android báo, không do app đo).
 *  - Không đọc được: dữ liệu tại HAL/DAC/TV/renderer/Cast, codec Bluetooth (cần quyền và API hệ thống), độ lợi âm lượng thật.
 */
object BitPerfectProbe {
    private val LOSSY = setOf("mp3", "aac", "ogg", "oga", "opus")

    fun routeOf(type: Int): BitPerfect.Route = when (type) {
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, 29 /* TYPE_HDMI_EARC */ -> BitPerfect.Route.HDMI
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> BitPerfect.Route.SPDIF
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> BitPerfect.Route.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 23 /* HEARING_AID */, 26 /* BLE_HEADSET */, 27 /* BLE_SPEAKER */ -> BitPerfect.Route.BLUETOOTH
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> BitPerfect.Route.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE -> BitPerfect.Route.WIRED_ANALOG
        else -> BitPerfect.Route.OTHER
    }

    /** encoding của Playback.sinkFmt → mã hiển thị; không có dữ liệu/encoding lạ → ENC_UNKNOWN (không đoán). */
    fun sinkEnc(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> BitPerfect.ENC_16
        C.ENCODING_PCM_24BIT -> BitPerfect.ENC_24
        C.ENCODING_PCM_32BIT -> BitPerfect.ENC_32
        C.ENCODING_PCM_FLOAT -> BitPerfect.ENC_FLOAT
        else -> BitPerfect.ENC_UNKNOWN
    }

    private fun maxDepth(enc: IntArray): Int {
        var d = 0
        if (AudioFormat.ENCODING_PCM_16BIT in enc) d = maxOf(d, 16)
        if (AudioFormat.ENCODING_PCM_24BIT_PACKED in enc) d = maxOf(d, 24)
        if (AudioFormat.ENCODING_PCM_32BIT in enc || AudioFormat.ENCODING_PCM_FLOAT in enc) d = maxOf(d, 32)
        return d
    }

    /**
     * Thiết bị Android thực sự định tuyến nhạc (theo chính sách định tuyến), hoặc null = KHÔNG xác định được (→ "System Default – Route Unknown").
     * Quy tắc nằm trong BitPerfect.activeIndex (có test). Không ảnh hưởng việc chọn thiết bị/phát nhạc hiện có: chỉ ĐỌC.
     */
    fun activeDevice(ctx: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < 33) return null
        return runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val list = am.getAudioDevicesForAttributes(attrs)
            val sel = AudioSettings.current(ctx)
            val idx = BitPerfect.activeIndex(Build.VERSION.SDK_INT, AudioSettings.deviceKey.isNotEmpty(), sel?.id ?: -1, list.map { it.id }.toIntArray())
            if (idx >= 0) list[idx] else null
        }.getOrNull()
    }

    /** USB VID/PID của thiết bị âm thanh USB: chỉ khi đúng MỘT thiết bị USB trùng tên sản phẩm (nếu không: không biết, không đoán). */
    private fun usbIds(ctx: Context, name: String): Pair<Int, Int>? {
        if (name.isBlank()) return null
        return try {
            val um = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
            val m = um?.deviceList?.values?.filter { (it.productName ?: "").equals(name, ignoreCase = true) }.orEmpty()
            if (m.size == 1) Pair(m[0].vendorId, m[0].productId) else null
        } catch (e: Exception) { null }
    }

    /** Mô tả thiết bị cho Device Registry. [activeKnown] = Android đã xác định đây là thiết bị đang định tuyến. */
    fun detectedOf(ctx: Context, d: AudioDeviceInfo, activeKnown: Boolean): DeviceRegistry.Detected {
        val x = DeviceRegistry.Detected()
        x.name = d.productName?.toString().orEmpty()
        x.conn = routeOf(d.type)
        x.activeKnown = activeKnown
        x.address = if (Build.VERSION.SDK_INT >= 28) runCatching { d.address }.getOrDefault("") else ""
        if (x.conn == BitPerfect.Route.USB) usbIds(ctx, x.name)?.let { x.vid = it.first; x.pid = it.second }
        return x
    }

    /** Thiết bị để hỏi xác nhận/đối chiếu hồ sơ: thiết bị đang định tuyến nếu biết, nếu không thì thiết bị người dùng chọn (activeKnown=false); không có → null. */
    fun detected(ctx: Context): DeviceRegistry.Detected? {
        val act = activeDevice(ctx)
        if (act != null) return detectedOf(ctx, act, true)
        val sel = AudioSettings.current(ctx) ?: return null
        return detectedOf(ctx, sel, false)
    }

    fun input(t: Track?, i: AudioInfo?, remote: Renderer?): BitPerfect.Input {
        val x = BitPerfect.Input()
        if (t == null) return x
        x.hasTrack = true
        x.srcExt = t.ext
        x.srcDsd = t.ext == "dsf" || t.ext == "dff"
        x.srcLossy = t.ext in LOSSY
        x.srcBits = i?.bits ?: t.bits; x.srcRate = i?.sampleRate ?: t.sr; x.srcChannels = i?.channels ?: t.ch
        if (remote != null) {   // gửi file gốc qua mạng: DSP của app không áp dụng, app không biết gì về đầu ra thật
            x.route = if (remote.kind == "cast") BitPerfect.Route.CAST else BitPerfect.Route.UPNP
            x.deviceName = remote.name
            x.dopActive = dopActive(t, i, remote)
            x.userCfgVerified = DeviceProfiles.userVerifiedFor(x, remote.name)      // kết quả người dùng tự kiểm tra (User Verified) cho đúng tổ hợp này
            return x
        }
        x.dspActive = !DspParams.isFlat()
        x.decodeDamage = DecodeHealth.damaged(t.path).toInt()       // khung hỏng đã được thay bằng im lặng trong lúc giải mã file này
        x.floatOutputEnabled = AudioSettings.hires          // cấu hình app, KHÔNG dùng để suy định dạng
        val s = Playback.sinkFmt.value
        if (s != null) {
            val enc = sinkEnc(s.encoding)
            if (enc != BitPerfect.ENC_UNKNOWN) { x.hasSink = true; x.sinkEnc = enc; x.sinkRate = s.sampleRate; x.sinkChannels = s.channels }
        }
        val ctx = runCatching { Playback.context }.getOrNull() ?: return x
        // Thiết bị người dùng chọn (có thể khác với thiết bị Android thực sự dùng)
        val sel = AudioSettings.current(ctx)
        if (sel != null) {
            x.selectedName = sel.productName?.toString().orEmpty().ifEmpty { AudioSettings.label(sel.type) }
            x.selCapRates = runCatching { sel.sampleRates }.getOrDefault(IntArray(0)); x.selCapDepth = maxDepth(runCatching { sel.encodings }.getOrDefault(IntArray(0)))
        } else if (AudioSettings.deviceKey.isNotEmpty()) { x.selectedDisconnected = true; x.selectedName = AudioSettings.currentName(ctx) }
        // Thiết bị Android thực sự định tuyến: chỉ khi xác định được
        val act = activeDevice(ctx)
        if (act != null) {
            x.route = routeOf(act.type)
            x.deviceName = act.productName?.toString().orEmpty().ifEmpty { AudioSettings.label(act.type) }
            x.capRates = runCatching { act.sampleRates }.getOrDefault(IntArray(0))
            x.capDepth = maxDepth(runCatching { act.encodings }.getOrDefault(IntArray(0)))
        }   // else: route = ROUTE_UNKNOWN ("System Default – Route Unknown")
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            x.mixerRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            x.volumeFixed = am.isVolumeFixed
            x.volumeIdx = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            x.volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        // Device Registry / User Device Profile (Device Verification Status tách khỏi Playback Status)
        runCatching {
            val det = detected(ctx)
            if (det != null) DeviceProfiles.applyTo(x, det)
        }
        return x
    }

    fun evaluate(t: Track?, i: AudioInfo?, remote: Renderer?): BitPerfect.Result = BitPerfect.evaluate(input(t, i, remote))
}
