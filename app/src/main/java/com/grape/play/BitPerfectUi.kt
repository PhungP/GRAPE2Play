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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

object BitPerfectUi { val open = mutableStateOf(false) }

private fun s(vi: String, en: String) = if (Lang.en.value) en else vi

private fun codeText(c: BitPerfect.Code): String = when (c) {
    BitPerfect.Code.DSP_ON -> s("DSP/EQ/Preamp/cân bằng đang bật: giá trị mẫu bị thay đổi.", "DSP/EQ/preamp/balance is on: sample values are changed.")
    BitPerfect.Code.DEPTH_REDUCED -> s("App đang cấu hình hạ độ sâu bit xuống 16-bit (chế độ tương thích): dữ liệu bị cắt bớt.", "The app is configured to reduce the bit depth to 16-bit (compatibility mode): data is truncated.")
    BitPerfect.Code.DECODE_DAMAGE -> s("File có khung dữ liệu hỏng (CRC sai): bộ giải mã đã thay các khung đó bằng im lặng, nên âm thanh KHÔNG còn giống dữ liệu trong file.", "The file has damaged data frames (bad CRC): the decoder replaced them with silence, so the audio no longer matches the data in the file.")
    BitPerfect.Code.DSD_TO_PCM -> s("DSD được chuyển sang PCM (giải mã/lọc): không còn là dữ liệu DSD gốc.", "DSD is converted to PCM (decoding/filtering): it is no longer the original DSD data.")
    BitPerfect.Code.RATE_CONVERSION_EVIDENCE -> s("Có bằng chứng đáng tin cậy về chuyển đổi tần số lấy mẫu.", "There is reliable evidence of sample-rate conversion.")
    BitPerfect.Code.BT_LOSSY_CODEC -> s("Bluetooth dùng codec nén mất dữ liệu so với PCM nguồn.", "Bluetooth uses a lossy codec compared with the source PCM.")
    BitPerfect.Code.DSP_OFF -> s("DSP/EQ của app đang tắt hoặc phẳng (app chép nguyên dữ liệu ở tầng DSP).", "The app DSP/EQ is off or flat (the app copies data unchanged at the DSP stage).")
    BitPerfect.Code.DSP_NOT_APPLIED_REMOTE -> s("DSP của app không áp dụng khi gửi file tới thiết bị ngoài.", "The app DSP is not applied when sending the file to an external device.")
    BitPerfect.Code.SENDS_ORIGINAL_FILE -> s("App gửi file gốc, không giải mã hay chuyển mã.", "The app sends the original file, without decoding or transcoding.")
    BitPerfect.Code.APP_NO_RESAMPLE -> s("App không phát hiện đổi tần số trước bộ xuất âm thanh (chỉ phần app; hệ thống chưa xác minh).", "The app detects no sample-rate change before the audio sink (app side only; the system is not verified).")
    BitPerfect.Code.USER_VERIFIED_CONFIG -> s("Bạn đã tự kiểm tra ĐÚNG tổ hợp này (thiết bị, kết nối, định dạng, điều kiện) và ghi nhận dữ liệu không đổi (User Verified). Đây là kết quả của bạn, app không tự quan sát được bên trong thiết bị nhận.", "You tested EXACTLY this combination (device, connection, format, conditions) and recorded that the data is unchanged (User Verified). This is your own result; the app cannot observe inside the receiver.")
    BitPerfect.Code.CONFIG_CRITERIA_MET -> s("Đủ điều kiện cấu hình: thiết bị + phương thức kết nối khớp danh mục, đúng thiết bị Android đang định tuyến, định dạng nằm trong thông số, không DSP, âm lượng tối đa. Đây là xác minh theo CẤU HÌNH, không phải phép đo toàn tuyến.", "Configuration criteria met: device + connection match the registry, it is the device Android is routing to, the format is within its specs, no DSP, volume at maximum. This is a CONFIGURATION check, not an end-to-end measurement.")
    BitPerfect.Code.ROUTE_UNKNOWN -> s("System Default – Route Unknown: Android không cho biết thiết bị nào thực sự đang nhận âm thanh (cần Android 13+ và đúng một thiết bị định tuyến). App không đoán HDMI/quang/Bluetooth.", "System Default – Route Unknown: Android does not say which device is actually receiving the audio (needs Android 13+ and exactly one routed device). The app does not guess HDMI/optical/Bluetooth.")
    BitPerfect.Code.SELECTED_NOT_CONFIRMED -> s("Thiết bị bạn chọn trong app chưa được Android xác nhận là thiết bị đang phát.", "The device you selected in the app is not confirmed by Android as the one playing.")
    BitPerfect.Code.PATH_UNVERIFIED -> s("Đường từ Android tới loa/DAC chưa xác minh được (mixer, HAL, phần cứng).", "The path from Android to the speaker/DAC cannot be verified (mixer, HAL, hardware).")
    BitPerfect.Code.CAPABILITY_ONLY -> s("Thông số thiết bị là KHẢ NĂNG công bố, không phải định dạng đang chạy.", "Device parameters are the advertised CAPABILITY, not the format in use.")
    BitPerfect.Code.CAPABILITY_MISMATCH_HINT -> s("Danh sách khả năng thiết bị công bố không chứa định dạng này. Chỉ là gợi ý (danh sách có thể thiếu): KHÔNG phải bằng chứng đã chuyển đổi.", "The advertised capability list does not include this format. A hint only (the list may be incomplete): NOT evidence that conversion happens.")
    BitPerfect.Code.MIXER_RATE_HINT -> s("Tần số mixer chính Android báo khác tần số nguồn. Chỉ là gợi ý, không phải bằng chứng resampling.", "The Android-reported main mixer rate differs from the source rate. A hint only, not evidence of resampling.")
    BitPerfect.Code.DEV_NOT_VERIFIED -> s("Thiết bị/phương thức kết nối chưa có hồ sơ xác minh (không nằm trong danh mục đã xác nhận).", "The device/connection has no verification profile (not in the confirmed registry).")
    BitPerfect.Code.DEV_ROUTE_MISMATCH -> s("Thiết bị/phương thức kết nối trong hồ sơ không trùng thiết bị Android đang định tuyến.", "The device/connection in the profile does not match the device Android is routing to.")
    BitPerfect.Code.DEV_FORMAT_OUTSIDE_REGISTRY -> s("Tần số/độ sâu bit của bài nằm ngoài thông số trong danh mục thiết bị.", "The track's sample rate/bit depth is outside the registry specs of the device.")
    BitPerfect.Code.SRC_INCOMPLETE -> s("Thiếu thông tin định dạng nguồn (độ sâu bit hoặc tần số).", "Source format information is incomplete (bit depth or sample rate).")
    BitPerfect.Code.SOURCE_SINK_RATE_DIFFERS -> s("Tần số đưa vào bộ xuất âm thanh khác tần số ghi trong file; chưa đủ để kết luận có resampling.", "The rate fed to the audio sink differs from the file's declared rate; not enough to conclude resampling.")
    BitPerfect.Code.RENDERER_NO_OUTPUT_INFO -> s("Thiết bị UPnP không cung cấp thông tin về dữ liệu đầu ra (giải mã, resampling, DAC).", "The UPnP device provides no information about its output (decoding, resampling, DAC).")
    BitPerfect.Code.CAST_NO_OUTPUT_INFO -> s("Google Cast không báo định dạng đầu ra thực tế của thiết bị nhận.", "Google Cast does not report the real output format of the receiver.")
    BitPerfect.Code.TV_PATH_UNKNOWN -> s("Không biết TV/ampli xử lý tín hiệu HDMI/S/PDIF thế nào (có thể đổi tần số, trộn, xử lý).", "It is unknown how the TV/amplifier handles the HDMI/S/PDIF signal (it may resample, mix or process).")
    BitPerfect.Code.USB_ACTUAL_UNVERIFIED -> s("Android không cho app đọc định dạng USB đang thực sự chạy; thông số USB là khả năng.", "Android does not let the app read the USB format actually in use; USB parameters are capabilities.")
    BitPerfect.Code.BT_CODEC_UNKNOWN -> s("Không đọc được codec Bluetooth đang dùng (A2DP thường dùng codec nén).", "The Bluetooth codec in use cannot be read (A2DP normally uses a compressed codec).")
    BitPerfect.Code.BT_CODEC_LOSSLESS_UNVERIFIED -> s("Codec Bluetooth tự khai không mất dữ liệu nhưng không xác minh được.", "The Bluetooth codec claims to be lossless but this cannot be verified.")
    BitPerfect.Code.DOP_RENDERER_UNVERIFIED -> s("DoP: app gửi đúng cấu trúc, nhưng không xác minh được thiết bị nhận giữ nguyên bit.", "DoP: the app sends the correct structure, but the receiver keeping the bits intact cannot be verified.")
    BitPerfect.Code.VOLUME_NOT_VERIFIED -> s("Âm lượng hệ thống: không xác minh được hệ số khuếch đại thực tế.", "System volume: the real gain cannot be verified.")
    BitPerfect.Code.VOLUME_BELOW_MAX -> s("Âm lượng hệ thống dưới mức tối đa: có thể có hệ số giảm biên độ bằng phần mềm.", "System volume is below maximum: a software attenuation may apply.")
    BitPerfect.Code.NO_SINK_INFO -> s("Chưa có thông tin định dạng bộ xuất âm thanh (chưa phát hoặc không phải PCM 16/24/32/Float).", "No audio-sink format information yet (not playing or not PCM 16/24/32/Float).")
    BitPerfect.Code.NO_TRACK -> s("Chưa có bài đang phát.", "Nothing is playing.")
    BitPerfect.Code.DEVICE_DISCONNECTED -> s("Thiết bị đã chọn không còn kết nối; app đang dùng đầu ra mặc định hệ thống.", "The selected device is no longer connected; the app is using the system default output.")
}

internal fun statusText(st: BitPerfect.Status, lv: BitPerfect.VerLevel = BitPerfect.VerLevel.NONE) = when (st) {
    BitPerfect.Status.VERIFIED -> if (lv == BitPerfect.VerLevel.CONFIGURATION) "VERIFIED – CONFIGURATION" else if (lv == BitPerfect.VerLevel.END_TO_END) "VERIFIED – END-TO-END" else if (lv == BitPerfect.VerLevel.USER_CONFIGURATION) "VERIFIED – USER-VERIFIED CONFIGURATION" else "VERIFIED"
    BitPerfect.Status.NOT_VERIFIED -> "NOT VERIFIED"
    BitPerfect.Status.NOT_BIT_PERFECT -> "NOT BIT-PERFECT"
    BitPerfect.Status.UNKNOWN -> "UNKNOWN"
}

private fun why(st: BitPerfect.Status) = when (st) {
    BitPerfect.Status.VERIFIED -> s("Đáp ứng điều kiện xác minh và không phát hiện thay đổi dữ liệu. Mức \"Configuration\" dựa trên cấu hình/thông số kỹ thuật, KHÔNG phải phép đo từ nguồn tới DAC.", "Verification criteria are met and no data change was found. The \"Configuration\" level rests on configuration/technical specs, NOT a source-to-DAC measurement.")
    BitPerfect.Status.NOT_VERIFIED -> s("Chưa phát hiện thay đổi dữ liệu, nhưng cũng chưa đủ bằng chứng để khẳng định toàn bộ đường truyền giữ nguyên dữ liệu. Đây là trạng thái mặc định, KHÔNG có nghĩa là có lỗi.", "No data change was detected, but there is not enough evidence to claim the whole path keeps the data intact. This is the default state and does NOT mean something is wrong.")
    BitPerfect.Status.NOT_BIT_PERFECT -> s("Đã phát hiện bằng chứng dữ liệu âm thanh bị thay đổi so với nguồn sau giải mã (xem mục \"Bằng chứng thay đổi\").", "Evidence was found that the audio data differs from the decoded source (see \"Evidence of change\").")
    BitPerfect.Status.UNKNOWN -> s("Chưa đủ dữ liệu để đánh giá (chưa phát nhạc hoặc chưa có thông tin bộ xuất âm thanh).", "Not enough data to assess (nothing playing or no audio-sink information yet).")
}


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
    val profVer by DeviceProfiles.version.collectAsState()
    val dspOn by DspParams.on.collectAsState()
    val bands by DspParams.bands.collectAsState()
    val pre by DspParams.preamp.collectAsState()
    val bal by DspParams.balance.collectAsState()
    val t = queue.getOrNull(idx)
    val inp = remember(t, info, remote, out, sink, dspOn, bands, pre, bal, profVer) { BitPerfectProbe.input(t, info, remote) }
    val r = BitPerfect.evaluate(inp)
    val compact = LocalCompact.current
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { ok.requestFocus() } }
    val grade = when (r.status) { BitPerfect.Status.VERIFIED -> Grade.GREEN; BitPerfect.Status.NOT_BIT_PERFECT -> Grade.RED; else -> Grade.YELLOW }
    val rem = inp.route == BitPerfect.Route.UPNP || inp.route == BitPerfect.Route.CAST
    // Frame dùng chung: cuộn được cả bằng cảm ứng lẫn D-pad (▲ cuộn lên khi không còn phần tử nhận tiêu điểm phía trên), nút đóng ở ĐẦU trang nhận tiêu điểm đầu tiên
    val lctx = LocalContext.current
    val actDet = remember(inp, profVer) { runCatching { BitPerfectProbe.detected(lctx) }.getOrNull() }
    // Thiết bị của phiên phát hiện tại: thiết bị ngoài (Cast/UPnP) hoặc đầu ra Android đã xác định được
    val qDet: DeviceRegistry.Detected? = if (remote != null) DeviceRegistry.Detected(remote!!.name, inp.route, -1, -1, true) else actDet?.takeIf { it.conn != BitPerfect.Route.ROUTE_UNKNOWN && it.name.isNotEmpty() }
    val qX: DeviceCatalog.Discovered? = qDet?.let { d ->
        if (remote != null) DeviceCatalog.Discovered(remote!!.maker, remote!!.model, remote!!.name, d.conn).also { it.modelDescription = remote!!.modelDesc; it.udn = remote!!.id }
        else DeviceCatalog.Discovered().also { it.friendlyName = d.name; it.modelName = d.name; it.vid = d.vid; it.pid = d.pid; it.conn = d.conn; it.manufacturer = d.manufacturer }
    }
    val key: String? = qDet?.let { DeviceRegistry.keyOf(it) }
    val prof = key?.let { DeviceProfiles.store.find(it) }
    var verifying by remember { mutableStateOf(false) }
    Frame(onDismiss = { BitPerfectUi.open.value = false }) {
            Focusable({ BitPerfectUi.open.value = false }, requester = ok) { T(s("← Đóng", "← Close"), 15, C.Gold, Modifier.padding(16.dp, 6.dp), bold = true) }
            T(s("Trạng thái Bit-perfect", "Bit-perfect status"), if (compact) 18 else 22, serif = true)
            T(statusText(r.status, r.level), if (compact) 17 else 22, grade.color, bold = true)
            T(why(r.status), 13, C.Muted, lines = 6)
            if (rem && r.status != BitPerfect.Status.VERIFIED) T(s("Thiết bị ngoài (Cast/UPnP): app không quan sát được bên trong thiết bị nhận nên mặc định NOT VERIFIED — nhãn “Manufacturer Verified” trong danh mục chỉ là tài liệu của hãng, không đổi trạng thái này. Muốn thành VERIFIED: dùng mục “XÁC MINH NHANH” ngay bên dưới: ① Confirm Device Identity, ② Verify Playback Capability cho đúng định dạng đang phát.", "External device (Cast/UPnP): the app cannot observe inside the receiver, so it defaults to NOT VERIFIED — the “Manufacturer Verified” badge in the catalog is only the manufacturer's documentation and does not change this. To get VERIFIED: use “QUICK VERIFY” right below: ① Confirm Device Identity, ② Verify Playback Capability for the exact format being played."), 12, C.Gold, lines = 9)
            if (r.level == BitPerfect.VerLevel.USER_CONFIGURATION) T(s("Mức xác minh: do chính bạn xác minh (User Verified) cho đúng tổ hợp thiết bị + kết nối + định dạng đang phát — không phải phép đo từ nguồn tới DAC.", "Verification level: verified by you (User Verified) for exactly this device + connection + format combination — not a measurement from source to DAC."), 12, C.Gold, lines = 5)
            if (t != null) T(t.title, 14, C.Gold, bold = true, lines = 1)
            // XÁC MINH NHANH: ngay tại màn hình này, không phải đi tìm ở menu khác
            SectionLabel(s("XÁC MINH NHANH THIẾT BỊ NÀY", "QUICK VERIFY THIS DEVICE"))
            if (qDet == null || qX == null) T(s("Chưa xác định được thiết bị của phiên phát này (cần phát nhạc tới thiết bị ngoài hoặc đầu ra Android xác định được).", "The device of this playback session cannot be identified yet (play to an external device or an identifiable Android output)."), 12, C.Muted, lines = 4)
            else {
                val cm = DeviceCatalog.match(qX, DeviceCatalogStore.catalog.devices)
                val savedHere = prof != null && prof.conn == qDet.conn
                T(qDet.name + " · " + connLabel(qDet.conn), 14, C.Gold, bold = true, lines = 2)
                T(if (savedHere) s("✓ Đã xác nhận danh tính: ", "✓ Identity confirmed: ") + prof!!.label().ifEmpty { prof.name } + (if (prof.catalogId.isEmpty()) s(" (chưa liên kết danh mục)", " (not linked to the catalog)") else "")
                  else if (cm.device != null) s("Khớp danh mục: ", "Catalog match: ") + cm.device!!.label() + (if (cm.needsConfirmation()) s(" (khớp theo tên — bấm ① để xác nhận)", " (matched by name — press ① to confirm)") else s(" (khớp chắc chắn)", " (exact match)"))
                  else if (cm.ambiguous) s("Nhiều model trùng khớp — chọn thủ công ở “Thiết bị âm thanh của tôi”.", "Several models match — choose manually in “My Audio Devices”.")
                  else s("Chưa có trong danh mục; vẫn có thể xác nhận thiết bị này để tự xác minh.", "Not in the catalog; you can still confirm this device in order to verify it yourself."), 12, C.Text, lines = 4)
                if (!savedHere) {
                    if (cm.ambiguous) Focusable({ BitPerfectUi.open.value = false; DeviceCatalogStore.mine.value = true }) { T(s("① Confirm Device Identity — chọn model", "① Confirm Device Identity — choose model"), 15, C.Gold, Modifier.padding(16.dp, 8.dp), bold = true) }
                    else Focusable({ DeviceRegistry.linkCatalog(DeviceProfiles.store, qDet, qX.udn, cm.device, System.currentTimeMillis()); DeviceProfiles.persist() }) {
                        T(s("① Confirm Device Identity", "① Confirm Device Identity") + (cm.device?.let { " — " + it.label() } ?: s(" — đúng thiết bị này", " — this device")), 15, C.Gold, Modifier.padding(16.dp, 8.dp), bold = true, lines = 2)
                    }
                    T(s("② Verify Playback Capability: cần xong ① trước (xác nhận danh tính không tự động).", "② Verify Playback Capability: needs ① first (identity is never confirmed automatically)."), 12, C.Dim, lines = 3)
                } else {
                    Focusable({ verifying = true }) { T(s("② Verify Playback Capability — điền sẵn theo bài đang phát", "② Verify Playback Capability — pre-filled from the current track"), 15, C.Gold, Modifier.padding(16.dp, 8.dp), bold = true, lines = 2) }
                    Focusable({ BitPerfectUi.open.value = false; DeviceCatalogStore.mine.value = true }) { T(s("Sửa danh tính / xem hồ sơ (Thiết bị âm thanh của tôi)", "Edit identity / view profile (My Audio Devices)"), 13, C.Text, Modifier.padding(16.dp, 6.dp), lines = 2) }
                }
            }

            SectionLabel(s("THÔNG TIN TÁCH RIÊNG", "SEPARATE INFORMATION"))
            @Composable fun field(label: String, value: String) { T(label, 12, C.Muted, bold = true); T(value, 14, C.Text, lines = 4) }
            val sinkTxt = if (inp.hasSink) BitPerfect.pcmName(inp.sinkEnc) + " · " + khz(inp.sinkRate) + (if (inp.sinkChannels > 0) " · ${inp.sinkChannels}ch" else "") else ""
            // 1. Source Format
            field("Source Format · " + s("Định dạng nguồn", "source file"),
                if (t == null) "—" else (t.ext.uppercase() + (if (inp.srcDsd) " · DSD" else "") + (if (inp.srcBits > 0) " · ${inp.srcBits}-bit" else "") + (if (inp.srcRate > 0) " · ${khz(inp.srcRate)}" else "") +
                    (if (inp.srcChannels > 0) " · ${inp.srcChannels}ch" else "") + (if (inp.srcLossy) " · " + s("nén mất dữ liệu (so sánh sau giải mã)", "lossy (compared after decoding)") else "")))
            // 2. Decoder Output (= định dạng bộ giải mã đưa vào AudioSink; app quan sát tại ForwardingAudioSink)
            field("Decoder Output · " + s("Sau giải mã", "after decoding"),
                if (rem) s("Không giải mã trong app (gửi file gốc)", "Not decoded in the app (original file is sent)")
                else if (inp.hasSink) sinkTxt else s("Chưa có thông tin", "No information yet"))
            // 3. DSP Status
            field("DSP Status",
                if (rem) s("Không áp dụng khi gửi file tới thiết bị ngoài", "Not applied when sending the file to an external device")
                else if (inp.dspActive) s("BẬT — EQ/Preamp/cân bằng đang chỉnh", "ON — EQ/preamp/balance adjusted") else s("Tắt / phẳng", "Off / flat"))
            // 4. Output Device (thiết bị Android thực sự định tuyến; nếu không biết thì nói rõ, kèm thiết bị người dùng chọn)
            field("Output Device · " + s("Thiết bị đầu ra", "output device"),
                if (rem) (remote?.label ?: "")
                else if (inp.route == BitPerfect.Route.ROUTE_UNKNOWN) "System Default – Route Unknown" +
                    (if (inp.selectedName.isNotEmpty()) "\n" + s("Thiết bị bạn chọn trong app: ", "Device you selected in the app: ") + inp.selectedName + (if (inp.selectedDisconnected) " (" + s("đã ngắt", "disconnected") + ")" else "") + " — " + s("chưa được Android xác nhận là đang phát", "not confirmed by Android as playing") else "")
                else inp.deviceName.ifEmpty { s("(không có tên)", "(no name)") })
            // 5. Connection Type
            field("Connection Type · " + s("Phương thức kết nối", "connection"), connLabel(inp.route))
            // 6. Device Verification Status (tách khỏi trạng thái phiên phát)
            field("Device Verification Status", devLevelText(inp.devLevel) + (if (inp.devLevel != BitPerfect.DevLevel.NONE && !inp.devIsActive) " — " + s("không áp dụng cho phiên này vì thiết bị chưa được Android xác nhận đang định tuyến", "not applied to this session because Android has not confirmed the device is routed") else ""))
            // 7. Output Capability (khả năng công bố của thiết bị đang định tuyến; chỉ khi route đã biết)
            field("Output Capability · " + s("Khả năng thiết bị công bố", "advertised capability"),
                if (rem) s("Không có thông tin từ thiết bị nhận", "No information from the receiving device")
                else if (inp.route == BitPerfect.Route.ROUTE_UNKNOWN) s("Không xác định (chưa biết thiết bị đang định tuyến)", "Unknown (the routed device is not known)")
                else if (inp.capRates.isNotEmpty() || inp.capDepth > 0) s("Tần số: ", "Rates: ") + rateList(inp.capRates) + " · " + s("độ sâu tối đa: ", "max depth: ") + (if (inp.capDepth > 0) "${inp.capDepth}-bit" else s("không công bố", "not listed")) +
                    " (" + s("khả năng, KHÔNG phải định dạng đang chạy", "capability, NOT the format in use") + ")"
                else s("Không có thông tin", "No information"))
            // 8. AudioSink Format (định dạng AudioSink NHẬN) ≠ định dạng Android/DAC thực tế
            field("AudioSink Format · " + s("AudioSink nhận", "received by the AudioSink"),
                if (rem) s("Không có (file gốc gửi qua mạng)", "None (the original file is sent over the network)")
                else if (inp.hasSink) sinkTxt + "\n" + s("Cấu hình app: ", "App setting: ") + (if (inp.floatOutputEnabled) s("yêu cầu đầu ra float 32-bit", "float 32-bit output requested") else s("hạ >16-bit xuống 16-bit", "reduce >16-bit to 16-bit")) + " (" + s("cấu hình, không phải định dạng thực tế", "a setting, not the actual format") + ")"
                else s("Chưa có thông tin", "No information yet"))
            field("Android Active Output Format",
                if (rem) s("Không áp dụng", "Not applicable") else s("Không xác định — app không đọc được định dạng AudioTrack/mixer của Android", "Unknown — the app cannot read Android's AudioTrack/mixer format") +
                    (if (inp.mixerRate > 0) "\n" + s("Tần số mixer chính Android báo (gợi ý): ", "Android-reported main mixer rate (hint): ") + khz(inp.mixerRate) else ""))
            field("DAC / Receiver Accepted Format",
                if (rem) s("Không có thông tin từ thiết bị nhận", "No information from the receiving device") else s("Không có bằng chứng", "No evidence"))
            // 9. Resampling Status
            field("Resampling Status",
                when (r.resampling) { BitPerfect.Tri.YES -> "Yes"; BitPerfect.Tri.NO -> "No"; else -> "Unknown" } +
                    if (r.resampling == BitPerfect.Tri.UNKNOWN) " — " + s("không có bằng chứng, nhưng cũng không thể xác minh tại hệ thống/thiết bị nhận", "no evidence found, but it cannot be verified in the system/receiver either") else "")
            // 10. Playback Bit-perfect Status
            field("Playback Bit-perfect Status", statusText(r.status, r.level))

            // Xác minh theo TỔ HỢP: Device + Firmware + Transport + Protocol + Audio Format + Playback Configuration. Không thay đổi kết luận ở trên (chỉ dựa trên bằng chứng của phiên phát).
            SectionLabel(s("XÁC MINH CHO TỔ HỢP NÀY", "VERIFICATION FOR THIS COMBINATION"))
            val combo = DeviceProfiles.comboOf(inp, prof)
            T(s("Kết quả xác minh của một thiết bị/kết nối/cấu hình KHÔNG đại diện cho phương thức hay cấu hình khác, và không thay thế bằng chứng của phiên phát này.", "A verification of one device/connection/configuration does not stand for another, and does not replace the evidence of this playback session."), 12, C.Muted, lines = 5)
            if (prof == null) T(s("Thiết bị chưa được lưu trong “Thiết bị âm thanh của tôi”: chưa áp dụng được xác minh theo thiết bị. Vào đó để Confirm Device Identity.", "The device is not saved in “My Audio Devices”: no device-specific verification can be applied. Open it to Confirm Device Identity."), 12, C.Muted, lines = 5)
            else {
                T(prof.label().ifEmpty { prof.name } + " · " + connLabel(inp.route) + (if (prof.firmware.isNotEmpty()) " · firmware " + prof.firmware else " · " + s("firmware chưa rõ", "firmware unknown")), 13, C.Text, bold = true, lines = 3)
                val cdev = if (prof.catalogId.isEmpty()) null else DeviceCatalogStore.catalog.byId(prof.catalogId)
                val cfgs = cdev?.configsFor(inp.route).orEmpty()
                if (cdev == null) T(s("Danh mục: chưa liên kết model.", "Catalog: model not linked."), 12, C.Muted)
                else if (cfgs.isEmpty()) T(s("Danh mục: chưa có cấu hình cho kết nối này.", "Catalog: no configuration for this connection."), 12, C.Muted, lines = 2)
                for (cf in cfgs) {
                    T(cf.protocol + " · " + s("khả năng thiết bị (tài liệu): ", "device capability (documented): ") + cf.evidence.filter { it.confidence != DeviceVerification.Confidence.UNVERIFIED }.joinToString(", ") { sourceName(it.source) + " – " + confidenceName(it.confidence) }.ifEmpty { s("chưa có nguồn xác minh", "no verification source") }, 12, C.Text, lines = 4)
                }
                val mine = DeviceProfiles.store.verificationsFor(prof.key)
                if (mine.isEmpty()) T(s("Chưa có kết quả bạn tự kiểm tra cho thiết bị này (User Verified).", "No result you tested for this device yet (User Verified)."), 12, C.Muted, lines = 2)
                for (v in mine) {
                    val ap = DeviceVerification.appliesTo(v, combo)
                    T("User Verified – " + confidenceName(DeviceVerification.userConfidence(v)) + " · " + v.scope() + " · " + (when (ap.applies) { DeviceVerification.Applies.EXACT -> s("áp dụng cho phiên này", "applies to this session"); DeviceVerification.Applies.PARTIAL -> s("áp dụng MỘT PHẦN", "applies only PARTIALLY"); else -> s("KHÔNG áp dụng", "does NOT apply") }) +
                        (if (ap.reasons.isEmpty()) "" else " (" + ap.reasons.joinToString("; ") + ")"), 12, C.Text, lines = 6)
                }
            }

            if (r.changes.isNotEmpty()) { SectionLabel(s("BẰNG CHỨNG THAY ĐỔI DỮ LIỆU", "EVIDENCE OF CHANGE")); r.changes.forEach { T("• " + codeText(it), 13, Grade.RED.color, lines = 4) } }
            if (r.confirmed.isNotEmpty()) { SectionLabel(s("ĐÃ XÁC MINH ĐƯỢC", "VERIFIED FACTS")); r.confirmed.forEach { T("• " + codeText(it), 13, C.Text, lines = 4) } }
            if (r.unverified.isNotEmpty()) { SectionLabel(s("CHƯA XÁC MINH ĐƯỢC", "NOT VERIFIED")); r.unverified.distinct().forEach { T("• " + codeText(it), 13, C.Muted, lines = 4) } }
            T(s("Giới hạn: Android không cho app đọc dữ liệu tại tầng mixer/HAL/DAC, TV hay thiết bị nhận, nên app chưa có phương pháp xác minh toàn đường truyền. VERIFIED – Configuration cần thiết bị + phương thức kết nối đã xác nhận trong danh mục VÀ Android thực sự định tuyến tới đó; không cấp chỉ dựa trên tên model, thông số công bố, thiết bị bạn chọn hay việc tắt EQ.",
                "Limits: Android does not let the app read data at the mixer/HAL/DAC, the TV or the receiver, so the app has no method to verify the whole path. VERIFIED – Configuration needs a confirmed registry device + connection that Android is actually routing to; it is never granted from a model name alone, advertised specs, the device you selected, or EQ being off."), 12, C.Muted, lines = 8)
            T(s("Danh mục thiết bị chưa đầy đủ: thiết bị không có trong danh mục không có nghĩa là không hỗ trợ Bit-perfect, và có trong danh mục không có nghĩa là đã bit-perfect.", "The device catalog is incomplete: a device missing from it does not mean it lacks Bit-perfect support, and being listed does not mean it is bit-perfect."), 12, C.Muted, lines = 5)
            Focusable({ BitPerfectUi.open.value = false; DeviceCatalogStore.open.value = true }) { T(s("Bit-perfect Verified Devices (danh mục thiết bị)", "Bit-perfect Verified Devices (catalog)"), 15, C.Text, Modifier.padding(24.dp, 8.dp)) }
            Focusable({ BitPerfectUi.open.value = false; DeviceCatalogStore.mine.value = true }) { T(s("Thiết bị âm thanh của tôi", "My Audio Devices"), 15, C.Text, Modifier.padding(24.dp, 8.dp)) }
            Focusable({ BitPerfectUi.open.value = false; DeviceProfiles.manage.value = true }) { T(s("Quản lý thiết bị Bit-perfect", "Manage Bit-perfect devices"), 15, C.Text, Modifier.padding(24.dp, 8.dp)) }
            Focusable({ BitPerfectUi.open.value = false }) { T(s("Đóng", "Close"), 16, C.Gold, Modifier.padding(24.dp, 8.dp), bold = true) }
    }
    if (verifying) prof?.let { VerifyPlaybackDialog(it, inp) { verifying = false } }
}
