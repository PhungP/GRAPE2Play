package com.grape.play

import androidx.compose.ui.graphics.Color

enum class Grade(val color: Color) {
    GREEN(Color(0xFF5BD67A)),   // BIT-PERFECT VERIFIED (chỉ khi có bằng chứng toàn đường truyền)
    YELLOW(Color(0xFFE6C34A)),  // NOT VERIFIED / UNKNOWN
    RED(Color(0xFFE5575A)),     // NOT BIT-PERFECT (đã có bằng chứng dữ liệu bị đổi)
}

/** Bài DSD64 đang được gửi tới thiết bị UPnP dưới dạng DoP (theo tuỳ chọn trong Cài đặt → Phát DSD). */
fun dopActive(t: Track, i: AudioInfo?, remote: Renderer?): Boolean =
    remote != null && remote.kind == "upnp" && AudioSettings.dsdRemote == AudioSettings.DSD_DOP && (t.ext == "dsf" || t.ext == "dff") && (i?.sampleRate ?: t.sr) == DopPcm.DSD64

/**
 * Trạng thái Bit-perfect (4 mức, xem BitPerfect.java / BITPERFECT.md): (màu, nhãn ngắn).
 * Không còn tuyên bố "BIT-PERFECT" chỉ vì EQ tắt, dùng USB/UPnP/Cast hay định dạng trùng nguồn: app không có cách đọc dữ liệu tại HAL/DAC/renderer.
 * [pcmFmt] giữ lại cho tương thích chỗ gọi; dữ liệu thật lấy từ BitPerfectProbe.
 */
@Suppress("UNUSED_PARAMETER")
fun signalGrade(t: Track, i: AudioInfo?, pcmFmt: String, remote: Renderer?): Pair<Grade, String> =
    BitPerfectProbe.evaluate(t, i, remote).let { r -> when (r.status) {
        BitPerfect.Status.VERIFIED -> Grade.GREEN to (if (r.level == BitPerfect.VerLevel.END_TO_END) "BIT-PERFECT VERIFIED" else "VERIFIED – CONFIG")
        BitPerfect.Status.NOT_VERIFIED -> Grade.YELLOW to "NOT VERIFIED"
        BitPerfect.Status.NOT_BIT_PERFECT -> Grade.RED to "NOT BIT-PERFECT"
        BitPerfect.Status.UNKNOWN -> Grade.YELLOW to "UNKNOWN"
    } }
