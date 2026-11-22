package com.grape.play

import java.util.concurrent.ConcurrentHashMap

/**
 * Dịch sang English các thông điệp lỗi / dòng nhật ký được ghép động (có tên thiết bị, mã lỗi, nội dung exception…) mà bảng dịch
 * theo chuỗi nguyên văn của [Lang] không bắt được. Nhật ký luôn được GHI bằng tiếng Việt (để mọi dòng cũ đều đọc được),
 * và chỉ được dịch khi HIỂN THỊ / XUẤT khi giao diện đang là English.
 * Các cụm đều dài và đặc trưng để không làm hỏng tên bài, tên thư mục có trong dòng nhật ký.
 */
internal object LogLang {
    private const val VI_CHARS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
        "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"

    fun hasVi(s: String): Boolean { for (c in s) if (VI_CHARS.indexOf(c) >= 0) return true; return false }

    /** Mẫu regex, tìm ở bất kỳ vị trí nào trong dòng (phần đuôi không khớp được giữ nguyên rồi dịch tiếp bằng [FRAGS]). */
    private val RX: List<Pair<Regex, String>> = listOf(
        "màn hình thật (\\S+) · chế độ " to "real screen \$1 · mode ",
        "Kiểm tra cập nhật lỗi \\(lần (\\d+)\\)" to "Update check failed (attempt \$1)",
        ": mất phản hồi lần thứ (\\d+)" to ": no response (count \$1)",
        "Nạp bài (\\S+) tới (.+?) \\(" to "Loading \$1 track to \$2 (",
        "Nạp bài tới (.+?) lỗi: " to "Failed to load track to \$1: ",
        "Bỏ qua (\\S+): " to "Skipped \$1: ",
        "Gemini lỗi mạng \\(" to "Gemini network error (",
        "(\\S+) trả (\\d{3}): " to "\$1 returned \$2: ",
        "Lập chỉ mục (\\d+) bài, (\\d+) thư mục trong (\\d+) ms" to "Indexed \$1 tracks, \$2 folders in \$3 ms",
        "Đầu ra (.+?) không nhận DSD→PCM (.+?): hạ xuống (.+?) và phát lại" to "Output \$1 does not accept DSD→PCM \$2: lowering to \$3 and replaying",
        "Đầu ra (.+?) không nhận PCM (.+?) — đã tự hạ xuống (.+?) và phát lại\\." to "Output \$1 does not accept \$2 PCM — automatically lowered to \$3 and replaying.",
        "Đầu ra (.+?) không nhận được tín hiệu của bài này\\. Hãy chọn bài/đầu ra khác \\(bấm “Đầu ra” ở góc trên\\) — app sẽ phát lại ngay\\." to
            "Output \$1 did not accept the signal of this track. Pick another track/output (press “Output” at the top) — the app will replay right away.",
        "Đổi đầu ra sang (.+?) sau lỗi: thử phát lại" to "Switched output to \$1 after error: retrying playback",
        "Phát danh sách (\\d+) bài từ #(\\d+)" to "Playing list of \$1 tracks from #\$2",
        "Trộn danh sách phát (\\d+) bài" to "Shuffled playlist of \$1 tracks",
        "Giải mã (\\w+) lỗi: " to "Decoding \$1 failed: ",
        "\\blỗi (\\d{3})\\b" to "error \$1",
        "Phiên đăng nhập (.+?) đã hết hạn\\. Hãy xoá nguồn này rồi đăng nhập lại\\." to "Sign-in session for \$1 has expired. Remove this source and sign in again.",
        "Khóa (.+?) không hợp lệ hoặc chưa được bật: " to "\$1 key is invalid or not enabled: ",
        "Lệnh tới (.+?) lỗi: " to "Command to \$1 failed: ",
        "không phản hồi sau (\\d+) giây" to "no response after \$1 s",
        "Chưa có khóa (\\S+)" to "No key for \$1",
    ).map { (p, r) -> Regex(p) to r }

    private val FRAGS: List<Pair<String, String>> = listOf(
        // --- nhật ký ứng dụng
        "Khởi động " to "Started ", "Đã xoá nhật ký" to "Log cleared", "FATAL trên luồng " to "FATAL on thread ",
        "Có bản mới " to "New version available ", "Tải/cài bản cập nhật lỗi" to "Update download/install failed",
        "714 với URL gốc của máy chủ: thử lại qua TV " to "714 with the server's original URL: retrying through the TV ",
        ": liên lạc lại được" to ": communication restored",
        " đang được máy khác điều khiển (URL lạ) — app này ngừng gửi lệnh" to " is being controlled by another device (unknown URL) — this app stops sending commands",
        " tự qua bài kế (gapless)" to " advanced to the next track by itself (gapless)",
        " báo dừng: hết bài" to " reported stop: end of track",
        " không phát được bài (báo dừng sau 20 s)" to " cannot play this track (reported stop after 20 s)",
        "Mất kết nối tới " to "Lost connection to ", "DSD gốc" to "original DSD", "file gốc" to "original file",
        "Chọn đầu ra: " to "Selected output: ", "TV/thiết bị này" to "this TV/device",
        " · đầu ra " to " · output ", " | đầu ra=" to " | output=", " @ bài kế tiếp" to " @ next track",
        " đang trả lời — " to " is answering — ",
        // --- lỗi phát / bộ nhớ đệm
        "Lỗi khi đọc từ ổ — đang sao chép file sang bộ nhớ đệm để thử lại…" to "Error while reading from the drive — copying the file to the cache to retry…",
        "Đã phát lại từ bản sao trong bộ nhớ đệm (lỗi gốc: " to "Replayed from the cache copy (original error: ",
        "Không sao chép được file từ ổ: " to "Could not copy the file from the drive: ", "danh sách đã đổi" to "the queue changed",
        "Không đủ dung lượng bộ nhớ đệm" to "Not enough cache storage",
        // --- giải mã
        "Không phải file WAV hợp lệ" to "Not a valid WAV file", "Không đọc được thông số WAV" to "Cannot read WAV parameters",
        "Không tìm thấy luồng âm thanh" to "No audio stream found",
        "Thiết bị không có bộ giải mã cho định dạng này" to "This device has no decoder for this format",
        "File quá ngắn hoặc không xác định được độ dài" to "File is too short or its length cannot be determined",
        "Không hỗ trợ định dạng " to "Unsupported format ", "Giải mã lỗi: " to "Decode error: ",
        "Vị trí tua vượt quá độ dài file" to "Seek position is beyond the end of the file",
        "DoP chỉ hỗ trợ DSD64, tối đa 2 kênh" to "DoP supports DSD64 with at most 2 channels only",
        "Không phải FLAC" to "Not a FLAC file", "Metadata FLAC hỏng" to "FLAC metadata is corrupt", "Thiếu STREAMINFO" to "STREAMINFO is missing",
        "Mất đồng bộ FLAC" to "FLAC sync lost", "blocksize không hợp lệ" to "invalid blocksize", "bit depth không hợp lệ" to "invalid bit depth",
        "channel assignment không hợp lệ" to "invalid channel assignment", "số kênh đổi giữa chừng" to "channel count changed mid-stream",
        "subframe type không hợp lệ" to "invalid subframe type",
        "File DSD bị cắt cụt" to "DSD file is truncated", "Không phải DSD" to "Not a DSD file", "Header DSF ngắn" to "DSF header is too short",
        "DSF thiếu khối fmt" to "DSF is missing the fmt block", "DSF thiếu khối data" to "DSF is missing the data block",
        "Header DSF không hợp lệ" to "Invalid DSF header", "Header DFF không hợp lệ" to "Invalid DFF header",
        "DFF nén (DST) chưa được hỗ trợ" to "Compressed DFF (DST) is not supported yet", "Tốc độ DSD lạ: " to "Unusual DSD rate: ",
        // --- mạng, UPnP, Cast, cloud
        "UPnP không phản hồi" to "UPnP is not responding", "Máy chủ liên tục báo lỗi: " to "The server keeps reporting errors: ",
        "thiết bị không mở được trình phát Cast" to "the device could not open the Cast player",
        "Chưa kết nối tới " to "Not connected to ", " đã ngắt kết nối" to " disconnected",
        " không tải được bài này (" to " could not load this track (", "Không phát được tới " to "Cannot play to ",
        " báo lỗi khi phát bài này" to " reported an error while playing this track",
        " không phản hồi khi phát bài này" to " is not responding while playing this track",
        " đang được một thiết bị khác điều khiển. Để tránh giằng co, app đã ngừng gửi lệnh. Bấm Phát để điều khiển lại từ máy này." to
            " is being controlled by another device. To avoid a tug-of-war, the app stopped sending commands. Press Play to take control again from this device.",
        " không phát được bài này (thiết bị báo dừng). Thử bài khác hoặc định dạng khác." to " cannot play this track (the device reported stop). Try another track or format.",
        "đã có lệnh mới" to "a newer command arrived", "Lệnh Phát lỗi: " to "Play command failed: ", "Lệnh Tạm dừng lỗi: " to "Pause command failed: ",
        "Tua lỗi: " to "Seek failed: ", "Thư viện có quá ít album để dựng buổi thử dàn" to "The library has too few albums to build a listening session",
        ": token bị từ chối (401)" to ": token rejected (401)", " đang bận (" to " is busy (", " không phản hồi" to " is not responding",
        "Nguồn đã bị xoá" to "The source was removed", "Không đọc được thông tin tài khoản (" to "Cannot read account information (",
        "Không mở được cổng phát nhạc: " to "Cannot open the streaming port: ", "Không mở được cổng ghép nối: " to "Cannot open the pairing port: ",
        "TV chưa kết nối mạng LAN/Wi-Fi" to "The TV is not connected to the LAN/Wi-Fi",
        "Chưa cấu hình Client ID (xem README, mục Đăng nhập đám mây)" to "Client ID is not configured (see README, Cloud sign-in section)",
        "Bạn đã từ chối cấp quyền" to "You declined the permission request", "Mã đã hết hạn, hãy thử lại" to "The code has expired, please try again",
        "File tải về bị thiếu dữ liệu" to "Downloaded file is incomplete", "File tải về không phải bản cài của GRAPEPlay" to "Downloaded file is not a GRAPEPlay installer",
        // --- AI
        "Chưa có khóa API nào — vào “Dịch vụ AI & khóa API” để thêm" to "No API key yet — open “AI services & API keys” to add one",
        "lỗi mạng (" to "network error (", ": không rõ)" to ": unknown)", "không trả kết quả" to "returned no result",
        "khóa không hợp lệ hoặc chưa được bật" to "the key is invalid or not enabled", "khóa không hợp lệ" to "invalid key",
        "khóa phải có dạng ACCOUNT_ID:API_TOKEN" to "the key must look like ACCOUNT_ID:API_TOKEN",
        "Không kết nối được " to "Cannot connect to ", "Câu trả lời của AI bị cắt, thử lại" to "The AI's answer was cut off, try again",
        "AI trả về định dạng lạ — thử lại" to "The AI returned an unexpected format — try again",
        "Hết bộ nhớ khi xử lý thư viện lớn — đóng bớt ứng dụng khác rồi thử lại" to "Out of memory while processing a large library — close other apps and try again",
        "Không khởi tạo được nhận giọng nói: " to "Could not start speech recognition: ", "Không nghe rõ — thử nói lại." to "Could not hear clearly — try again.",
        "Không bắt đầu nghe được: " to "Could not start listening: ",
    ).sortedByDescending { it.first.length }

    private val cache = ConcurrentHashMap<String, String>()

    /** Dịch một chuỗi/dòng. Chuỗi không có chữ Việt hoặc không khớp cụm nào được trả về nguyên vẹn. */
    fun localize(s: String): String {
        if (!hasVi(s)) return s
        cache[s]?.let { return it }
        var r = s
        for ((rx, rep) in RX) r = rx.replace(r, rep)
        for ((vi, en) in FRAGS) if (r.contains(vi)) r = r.replace(vi, en)
        if (cache.size > 3000) cache.clear()
        cache[s] = r
        return r
    }

    private val prefix = Regex("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3} [IWE]/[^:]+: )(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Dịch một dòng nhật ký, giữ nguyên phần thời gian / mức / thẻ ở đầu. */
    fun line(l: String): String {
        if (!hasVi(l)) return l
        val m = prefix.matchEntire(l) ?: return localize(l)
        return m.groupValues[1] + localize(m.groupValues[2])
    }

    /** Dịch toàn bộ văn bản nhật ký (nhiều dòng). */
    fun text(t: String): String = t.lineSequence().joinToString("\n") { line(it) }
}
