package com.grape.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect

object BitPerfectUi { val open = mutableStateOf(false) }

private fun s(vi: String, en: String) = if (Lang.en.value) en else vi

private fun codeText(c: BitPerfect.Code): String = when (c) {
    BitPerfect.Code.DSP_ON -> s("DSP/EQ/Preamp/cân bằng đang bật: giá trị mẫu bị thay đổi.", "DSP/EQ/preamp/balance is on: sample values are changed.")
    BitPerfect.Code.DEPTH_REDUCED -> s("Chế độ tương thích hạ độ sâu bit xuống 16-bit: dữ liệu bị cắt bớt.", "Compatibility mode reduces the bit depth to 16-bit: data is truncated.")
    BitPerfect.Code.DSD_TO_PCM -> s("DSD được chuyển sang PCM (giải mã/lọc): không còn là dữ liệu DSD gốc.", "DSD is converted to PCM (decoding/filtering): it is no longer the original DSD data.")
    BitPerfect.Code.DEVICE_RATE_UNSUPPORTED -> s("Thiết bị tự công bố không hỗ trợ tần số này: Android phải chuyển tần số (resampling).", "The device itself lists this sample rate as unsupported: Android must resample.")
    BitPerfect.Code.DEVICE_FORMAT_UNSUPPORTED -> s("Thiết bị công bố không nhận được độ sâu bit cần thiết: dữ liệu bị đổi khi chuyển định dạng.", "The device lists no support for the bit depth needed: data changes when the format is converted.")
    BitPerfect.Code.ANDROID_RATE_DIFFERS -> s("Android báo tần số xử lý/đầu ra khác tần số nguồn: có chuyển tần số (resampling).", "Android reports a processing/output rate different from the source rate: resampling takes place.")
    BitPerfect.Code.BT_LOSSY_CODEC -> s("Bluetooth dùng codec nén mất dữ liệu so với PCM nguồn.", "Bluetooth uses a lossy codec compared with the source PCM.")
    BitPerfect.Code.DSP_OFF -> s("DSP/EQ của app đang tắt hoặc phẳng (app chép nguyên dữ liệu ở tầng DSP).", "The app DSP/EQ is off or flat (the app copies data unchanged at the DSP stage).")
    BitPerfect.Code.DSP_NOT_APPLIED_REMOTE -> s("DSP của app không áp dụng khi gửi file tới thiết bị ngoài.", "The app DSP is not applied when sending the file to an external device.")
    BitPerfect.Code.SENDS_ORIGINAL_FILE -> s("App gửi file gốc, không giải mã hay chuyển mã.", "The app sends the original file, without decoding or transcoding.")
    BitPerfect.Code.APP_NO_RESAMPLE -> s("App không đổi tần số lấy mẫu trước bộ xuất âm thanh (chỉ phần app; hệ thống chưa xác minh).", "The app does not change the sample rate before the audio sink (app side only; the system is not verified).")
    BitPerfect.Code.DEVICE_RATE_LISTED -> s("Thiết bị công bố có hỗ trợ tần số này (chỉ là khả năng).", "The device lists this sample rate as supported (capability only).")
    BitPerfect.Code.PATH_UNVERIFIED -> s("Đường từ Android tới loa/DAC chưa xác minh được (mixer, HAL, phần cứng).", "The path from Android to the speaker/DAC cannot be verified (mixer, HAL, hardware).")
    BitPerfect.Code.CAPABILITY_ONLY -> s("Thông tin thiết bị là KHẢ NĂNG công bố, không phải định dạng đang chạy.", "Device information is the advertised CAPABILITY, not the format in use.")
    BitPerfect.Code.RENDERER_NO_OUTPUT_INFO -> s("Thiết bị UPnP không cung cấp thông tin về dữ liệu đầu ra (giải mã, resampling, DAC).", "The UPnP device provides no information about its output (decoding, resampling, DAC).")
    BitPerfect.Code.CAST_NO_OUTPUT_INFO -> s("Google Cast không báo định dạng đầu ra thực tế của thiết bị nhận.", "Google Cast does not report the real output format of the receiver.")
    BitPerfect.Code.TV_PATH_UNKNOWN -> s("Không biết TV/ampli xử lý tín hiệu HDMI/quang thế nào (có thể đổi tần số, trộn, xử lý).", "It is unknown how the TV/amplifier handles the HDMI/optical signal (it may resample, mix or process).")
    BitPerfect.Code.USB_ACTUAL_UNVERIFIED -> s("Android không cho app đọc định dạng USB đang thực sự chạy; thông số USB là khả năng.", "Android does not let the app read the USB format actually in use; USB parameters are capabilities.")
    BitPerfect.Code.BT_CODEC_UNKNOWN -> s("Không đọc được codec Bluetooth đang dùng (A2DP thường dùng codec nén).", "The Bluetooth codec in use cannot be read (A2DP normally uses a compressed codec).")
    BitPerfect.Code.BT_CODEC_LOSSLESS_UNVERIFIED -> s("Codec Bluetooth tự khai không mất dữ liệu nhưng không xác minh được.", "The Bluetooth codec claims to be lossless but this cannot be verified.")
    BitPerfect.Code.DOP_RENDERER_UNVERIFIED -> s("DoP: app gửi đúng cấu trúc, nhưng không xác minh được thiết bị nhận giữ nguyên bit.", "DoP: the app sends the correct structure, but the receiver keeping the bits intact cannot be verified.")
    BitPerfect.Code.VOLUME_NOT_VERIFIED -> s("Âm lượng hệ thống: không xác minh được hệ số khuếch đại thực tế.", "System volume: the real gain cannot be verified.")
    BitPerfect.Code.VOLUME_BELOW_MAX -> s("Âm lượng hệ thống dưới mức tối đa: có thể có hệ số giảm biên độ bằng phần mềm.", "System volume is below maximum: a software attenuation may apply.")
    BitPerfect.Code.NO_SINK_INFO -> s("Chưa có thông tin định dạng bộ xuất âm thanh (chưa phát hoặc không phải PCM).", "No audio-sink format information yet (not playing or not PCM).")
    BitPerfect.Code.NO_TRACK -> s("Chưa có bài đang phát.", "Nothing is playing.")
    BitPerfect.Code.DEVICE_DISCONNECTED -> s("Thiết bị đã chọn không còn kết nối; app đang dùng đầu ra mặc định hệ thống.", "The selected device is no longer connected; the app is using the system default output.")
}

private fun statusText(st: BitPerfect.Status) = when (st) {
    BitPerfect.Status.VERIFIED -> "BIT-PERFECT VERIFIED"
    BitPerfect.Status.NOT_VERIFIED -> "NOT VERIFIED"
    BitPerfect.Status.NOT_BIT_PERFECT -> "NOT BIT-PERFECT"
    BitPerfect.Status.UNKNOWN -> "UNKNOWN"
}

private fun why(st: BitPerfect.Status) = when (st) {
    BitPerfect.Status.VERIFIED -> s("Có bằng chứng xác minh toàn đường truyền và không có thay đổi dữ liệu nào.", "There is evidence verifying the whole path and no data change was found.")
    BitPerfect.Status.NOT_VERIFIED -> s("Chưa phát hiện thay đổi dữ liệu, nhưng cũng chưa đủ bằng chứng để khẳng định toàn bộ đường truyền giữ nguyên dữ liệu. Đây là trạng thái mặc định, KHÔNG có nghĩa là có lỗi.", "No data change was detected, but there is not enough evidence to claim the whole path keeps the data intact. This is the default state and does NOT mean something is wrong.")
    BitPerfect.Status.NOT_BIT_PERFECT -> s("Đã phát hiện bằng chứng dữ liệu âm thanh bị thay đổi so với nguồn sau giải mã (xem mục \"Bằng chứng thay đổi\").", "Evidence was found that the audio data differs from the decoded source (see \"Evidence of change\").")
    BitPerfect.Status.UNKNOWN -> s("Chưa đủ dữ liệu để đánh giá (chưa phát nhạc hoặc chưa có thông tin bộ xuất âm thanh).", "Not enough data to assess (nothing playing or no audio-sink information yet).")
}

private fun pcmName(bits: Int, float: Boolean) = if (float) "PCM float 32-bit" else "PCM $bits-bit"

private fun rateList(r: IntArray) = if (r.isEmpty()) s("không công bố", "not listed") else r.sorted().joinToString(", ") { khz(it) }

/** Hộp giải thích Bit-perfect: 8 trường độc lập + lý do + điều đã/chưa xác minh. Chỉ đọc dữ liệu, không can thiệp phát nhạc. */
@Composable
fun BitPerfectDialog() {
    val queue by Playback.queue.collectAsState()
    val idx by Playback.index.collectAsState()
    val info by Playback.info.collectAsState()
    val remote by Playback.remote.collectAsState()
    val out by Playback.outName.collectAsState()
    val sink by Playback.sinkFmt.collectAsState()
    val dspOn by DspParams.on.collectAsState()
    val bands by DspParams.bands.collectAsState()
    val pre by DspParams.preamp.collectAsState()
    val bal by DspParams.balance.collectAsState()
    val t = queue.getOrNull(idx)
    val inp = remember(t, info, remote, out, sink, dspOn, bands, pre, bal) { BitPerfectProbe.input(t, info, remote) }
    val r = BitPerfect.evaluate(inp)
    val compact = LocalCompact.current
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { ok.requestFocus() } }
    val grade = when (r.status) { BitPerfect.Status.VERIFIED -> Grade.GREEN; BitPerfect.Status.NOT_BIT_PERFECT -> Grade.RED; else -> Grade.YELLOW }
    val rem = inp.route == BitPerfect.Route.UPNP || inp.route == BitPerfect.Route.CAST
    Dialog(onDismissRequest = { BitPerfectUi.open.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            (if (compact) Modifier.dlgW(560) else Modifier.dlgFrac(0.5f)).clip(RoundedCornerShape(14.dp)).background(C.Panel)
                .border(1.dp, C.Line, RoundedCornerShape(14.dp)).dlgPad(24, 16).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(s("Trạng thái Bit-perfect", "Bit-perfect status"), if (compact) 18 else 22, serif = true)
            T(statusText(r.status), if (compact) 17 else 22, grade.color, bold = true)
            T(why(r.status), 13, C.Muted, lines = 6)
            if (t != null) T(t.title, 14, C.Gold, bold = true, lines = 1)

            SectionLabel(s("THÔNG TIN TÁCH RIÊNG", "SEPARATE INFORMATION"))
            @Composable fun field(label: String, value: String) { T(label, 12, C.Muted, bold = true); T(value, 14, C.Text, lines = 4) }
            // 1. Source Format
            field("Source Format · " + s("Định dạng nguồn", "source file"),
                if (t == null) "—" else (t.ext.uppercase() + (if (inp.srcDsd) " · DSD" else "") + (if (inp.srcBits > 0) " · ${inp.srcBits}-bit" else "") + (if (inp.srcRate > 0) " · ${khz(inp.srcRate)}" else "") +
                    (if (inp.srcChannels > 0) " · ${inp.srcChannels}ch" else "") + (if (inp.srcLossy) " · " + s("nén mất dữ liệu (so sánh sau giải mã)", "lossy (compared after decoding)") else "")))
            // 2. Decoder Output
            field("Decoder Output · " + s("Sau giải mã", "after decoding"),
                if (rem) s("Không giải mã trong app (gửi file gốc)", "Not decoded in the app (original file is sent)")
                else if (inp.hasDecoder) pcmName(inp.decBits, inp.decFloat) + " · " + khz(inp.decRate) else s("Chưa có thông tin", "No information yet"))
            // 3. DSP Processing
            field("DSP Processing",
                if (rem) s("Không áp dụng khi gửi file tới thiết bị ngoài", "Not applied when sending the file to an external device")
                else if (inp.dspActive) s("BẬT — EQ/Preamp/cân bằng đang chỉnh", "ON — EQ/preamp/balance adjusted") else s("Tắt / phẳng", "Off / flat"))
            // 4. Output Device
            field("Output Device · " + s("Thiết bị đầu ra", "output device"),
                (if (rem) (remote?.label ?: "") else inp.deviceName.ifEmpty { s("Mặc định hệ thống", "System default") }) +
                    (if (inp.deviceDisconnected) " · " + s("đã ngắt", "disconnected") else "") + " · " + inp.route.name)
            // 5. Output Capability
            field("Output Capability · " + s("Khả năng thiết bị công bố", "advertised capability"),
                if (rem) s("Không có thông tin từ thiết bị nhận", "No information from the receiving device")
                else if (inp.capRates.isNotEmpty() || inp.capEncKnown) s("Tần số: ", "Rates: ") + rateList(inp.capRates) + " · " + s("độ sâu: ", "depth: ") +
                    (if (!inp.capEncKnown) s("không công bố", "not listed") else listOfNotNull(if (inp.cap16) "16" else null, if (inp.cap24) "24" else null, if (inp.cap32) "32" else null, if (inp.capFloat) "float" else null).joinToString("/")) +
                    " (" + s("khả năng, KHÔNG phải định dạng đang chạy", "capability, NOT the format in use") + ")"
                else s("Không có thông tin", "No information"))
            // 6. Actual Output Format
            field("Actual Output Format · " + s("Định dạng thực tế", "actual format"),
                if (rem) s("Gửi file gốc qua mạng; định dạng thực tế tại DAC/renderer không biết", "Original file sent over the network; the real format at the DAC/renderer is unknown")
                else if (inp.hasOut) s("App yêu cầu AudioTrack: ", "The app requests from AudioTrack: ") + pcmName(inp.outBits, inp.outFloat) + " · " + khz(inp.outRate) +
                    " — " + s("Android/thiết bị có thể đổi lại; chưa xác minh", "Android/the device may convert it; not verified") +
                    (if (inp.mixerRate > 0 && inp.capRates.isEmpty()) " · " + s("tần số mixer Android báo: ", "Android-reported mixer rate: ") + khz(inp.mixerRate) else "")
                else s("Chưa có thông tin", "No information yet"))
            // 7. Resampling Status
            field("Resampling Status",
                when (r.resampling) { BitPerfect.Tri.YES -> "Yes"; BitPerfect.Tri.NO -> "No"; else -> "Unknown" } +
                    if (r.resampling == BitPerfect.Tri.UNKNOWN) " — " + s("không thể xác minh tại hệ thống/thiết bị nhận", "cannot be verified in the system/receiver") else "")
            // 8. Bit-perfect Status
            field("Bit-perfect Status", statusText(r.status))

            if (r.changes.isNotEmpty()) { SectionLabel(s("BẰNG CHỨNG THAY ĐỔI DỮ LIỆU", "EVIDENCE OF CHANGE")); r.changes.forEach { T("• " + codeText(it), 13, Grade.RED.color, lines = 4) } }
            if (r.confirmed.isNotEmpty()) { SectionLabel(s("ĐÃ XÁC MINH ĐƯỢC", "VERIFIED FACTS")); r.confirmed.forEach { T("• " + codeText(it), 13, C.Text, lines = 4) } }
            if (r.unverified.isNotEmpty()) { SectionLabel(s("CHƯA XÁC MINH ĐƯỢC", "NOT VERIFIED")); r.unverified.distinct().forEach { T("• " + codeText(it), 13, C.Muted, lines = 4) } }
            T(s("Giới hạn: Android không cho app đọc dữ liệu tại tầng mixer/HAL/DAC, TV hay thiết bị nhận, nên app chưa có phương pháp xác minh toàn đường truyền và không cấp VERIFIED chỉ dựa trên tên model, thông số công bố, định dạng báo cáo hay việc tắt EQ.",
                "Limits: Android does not let the app read data at the mixer/HAL/DAC, the TV or the receiver, so the app has no method to verify the whole path and never grants VERIFIED from a model name, advertised specs, reported formats or EQ being off."), 12, C.Muted, lines = 8)
            Focusable({ BitPerfectUi.open.value = false }, requester = ok) { T(s("Đóng", "Close"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
        }
    }
}
