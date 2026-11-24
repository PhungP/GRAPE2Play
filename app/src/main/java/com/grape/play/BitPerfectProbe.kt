package com.grape.play

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import androidx.media3.common.C

/**
 * Thu thập dữ liệu để đánh giá Bit-perfect (chỉ ĐỌC: không chạm vào luồng âm thanh, bộ giải mã, DSP hay bộ xuất âm thanh).
 * Nguồn dữ liệu và độ tin cậy:
 *  - Track/AudioInfo: định dạng file (thẻ + đầu dò file).
 *  - Playback.sinkFmt: định dạng PCM ExoPlayer đưa vào bộ xuất âm thanh (= sau giải mã) — do app quan sát, chắc chắn.
 *  - AudioSettings.hires: app có yêu cầu đầu ra float hay hạ 16-bit — do app quyết định, chắc chắn.
 *  - AudioDeviceInfo (sampleRates, encodings, type, productName): KHẢ NĂNG thiết bị công bố — không phải định dạng đang chạy.
 *  - AudioManager PROPERTY_OUTPUT_SAMPLE_RATE: tần số mixer chính Android báo — gợi ý có chuyển tần số (Android báo, không do app đo).
 *  - Không đọc được: dữ liệu tại HAL/DAC/TV/renderer/Cast, codec Bluetooth (cần quyền và API hệ thống), độ lợi âm lượng thật.
 */
object BitPerfectProbe {
    private val LOSSY = setOf("mp3", "aac", "ogg", "oga", "opus")

    fun routeOf(type: Int): BitPerfect.Route = when (type) {
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, 29 /* TYPE_HDMI_EARC */ -> BitPerfect.Route.HDMI
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> BitPerfect.Route.OPTICAL
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> BitPerfect.Route.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 23 /* HEARING_AID */, 26 /* BLE_HEADSET */, 27 /* BLE_SPEAKER */ -> BitPerfect.Route.BLUETOOTH
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> BitPerfect.Route.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE -> BitPerfect.Route.WIRED_ANALOG
        else -> BitPerfect.Route.OTHER
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
            return x
        }
        x.dspActive = !DspParams.isFlat()
        val s = Playback.sinkFmt.value
        if (s != null) {
            x.hasDecoder = true; x.decRate = s.sampleRate; x.decFloat = s.encoding == C.ENCODING_PCM_FLOAT
            x.decBits = when (s.encoding) { C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_24BIT -> 24; else -> 32 }
            x.hasOut = true; x.outRate = s.sampleRate
            x.outFloat = AudioSettings.hires && s.encoding != C.ENCODING_PCM_16BIT   // cùng quy tắc với LevelProcessor/ExoPlayer
            x.outBits = if (x.outFloat) 32 else 16
        }
        val ctx = runCatching { Playback.context }.getOrNull() ?: return x
        val dev = AudioSettings.current(ctx)
        if (dev != null) {
            x.route = routeOf(dev.type)
            x.deviceName = dev.productName?.toString().orEmpty().ifEmpty { AudioSettings.label(dev.type) }
            x.deviceModel = x.deviceName
            x.capRates = runCatching { dev.sampleRates }.getOrDefault(IntArray(0))
            val enc = runCatching { dev.encodings }.getOrDefault(IntArray(0))
            x.capEncKnown = enc.isNotEmpty()
            x.cap16 = AudioFormat.ENCODING_PCM_16BIT in enc
            x.cap24 = AudioFormat.ENCODING_PCM_24BIT_PACKED in enc
            x.cap32 = AudioFormat.ENCODING_PCM_32BIT in enc
            x.capFloat = AudioFormat.ENCODING_PCM_FLOAT in enc
        } else if (AudioSettings.deviceKey.isNotEmpty()) {
            x.deviceDisconnected = true                      // thiết bị đã chọn đã rút: đang dùng mặc định hệ thống
            x.deviceName = AudioSettings.currentName(ctx)
        } else x.deviceName = AudioSettings.currentName(ctx)
        runCatching {
            val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            x.mixerRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            x.volumeFixed = am.isVolumeFixed
            x.volumeIdx = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            x.volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        return x
    }

    fun evaluate(t: Track?, i: AudioInfo?, remote: Renderer?): BitPerfect.Result = BitPerfect.evaluate(input(t, i, remote))
}
