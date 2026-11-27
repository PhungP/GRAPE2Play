package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

/** Phase 4: nhận diện nhạc — WAV, phản hồi AudD, thông báo lỗi, thử lại. */
final class MusicIdTests {
    private MusicIdTests() {}
    static int le32(byte[] b, int o) { return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24); }
    static int le16(byte[] b, int o) { return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8); }
    static String tag(byte[] b, int o) { return new String(b, o, 4, java.nio.charset.StandardCharsets.US_ASCII); }

    static void run() {
        short[] pcm = {0, 1, -1, 32767, -32768, 256};
        byte[] w = MusicIdCodec.wav(pcm, pcm.length, 16000);
        check(w.length == 44 + 12 && tag(w, 0).equals("RIFF") && tag(w, 8).equals("WAVE") && tag(w, 12).equals("fmt ") && tag(w, 36).equals("data"), "MI1. WAV: kích thước và các nhãn RIFF/WAVE/fmt/data");
        check(le32(w, 4) == 36 + 12 && le32(w, 16) == 16 && le16(w, 20) == 1 && le16(w, 22) == 1 && le32(w, 24) == 16000 && le32(w, 28) == 32000 && le16(w, 32) == 2 && le16(w, 34) == 16 && le32(w, 40) == 12, "MI2. WAV: PCM, 1 kênh, 16 kHz, byte rate 32000, block align 2, 16-bit, độ dài dữ liệu");
        check(le16(w, 44) == 0 && le16(w, 46) == 1 && (short) le16(w, 48) == -1 && (short) le16(w, 50) == 32767 && (short) le16(w, 52) == -32768 && le16(w, 54) == 256, "MI3. WAV: mẫu little-endian có dấu đúng (kể cả cực trị)");
        check(MusicIdCodec.wav(pcm, 2, 16000).length == 48 && MusicIdCodec.wav(pcm, 0, 16000).length == 44 && MusicIdCodec.wav(pcm, 99, 8000).length == 44 + 12 && MusicIdCodec.wav(pcm, -5, 8000).length == 44, "MI4. WAV: độ dài bị kẹp trong [0, số mẫu]");
        short[] ten = new short[160000]; check(MusicIdCodec.wav(ten, ten.length, 16000).length == 44 + 320000, "MI5. 10 giây 16 kHz = 320.000 byte dữ liệu");
        check(MusicIdCodec.peak(pcm, pcm.length) == 32768 && MusicIdCodec.peak(new short[100], 100) == 0 && MusicIdCodec.peak(pcm, 3) == 1, "MI6. biên độ đỉnh (phát hiện micro im lặng, kể cả -32768)");

        MusicIdCodec.Result r = MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{\"artist\":\"Adele\",\"title\":\"Hello\",\"album\":\"25\",\"release_date\":\"2015-10-23\",\"label\":\"XL\",\"timecode\":\"00:42\",\"song_link\":\"https://lis.tn/x\"}}");
        check(r.status == MusicIdCodec.Status.MATCH && r.artist.equals("Adele") && r.title.equals("Hello") && r.album.equals("25") && r.releaseDate.equals("2015-10-23") && r.label.equals("XL") && r.timecode.equals("00:42") && r.songLink.equals("https://lis.tn/x"), "MI7. phản hồi thành công có kết quả");
        r = MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":null}");
        check(r.status == MusicIdCodec.Status.NO_MATCH, "MI8. result null → NO_MATCH (không phải lỗi)");
        check(MusicIdCodec.parseAudd("{\"status\":\"success\"}").status == MusicIdCodec.Status.NO_MATCH && MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{}}").status == MusicIdCodec.Status.NO_MATCH && MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{\"album\":\"chỉ album\"}}").status == MusicIdCodec.Status.NO_MATCH, "MI9. thiếu result / result rỗng / không có tên bài lẫn nghệ sĩ → NO_MATCH");
        r = MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{\"title\":\"Việt Nam \\\"Quê\\\" hương\",\"artist\":\" \"}}");
        check(r.status == MusicIdCodec.Status.MATCH && r.title.equals("Việt Nam \"Quê\" hương") && r.artist.isEmpty(), "MI10. Unicode, dấu nháy, nghệ sĩ trống nhưng có tên bài → MATCH");
        r = MusicIdCodec.parseAudd("{\"status\":\"error\",\"error\":{\"error_code\":900,\"error_message\":\"Wrong API token\"}}");
        check(r.status == MusicIdCodec.Status.ERROR && r.errorCode == 900 && MusicIdCodec.describeError(r).contains("Khoá API") && MusicIdCodec.describeError(r).contains("Wrong API token"), "MI11. lỗi 900: thông báo khoá sai kèm lời dịch vụ");
        check(MusicIdCodec.describeError(MusicIdCodec.parseAudd("{\"status\":\"error\",\"error\":{\"error_code\":901}}")).contains("hạn mức"), "MI12. lỗi 901: chưa gửi khoá / hết hạn mức");
        String[][] codes = {{"700", "không nhận được"}, {"500", "không hợp lệ"}, {"400", "10 MB"}, {"300", "quá ngắn"}, {"902", "mã 902"}, {"999", "mã 999"}};
        boolean okc = true;
        for (String[] c : codes) { String d = MusicIdCodec.describeError(MusicIdCodec.parseAudd("{\"status\":\"error\",\"error\":{\"error_code\":" + c[0] + "}}")); if (!d.contains(c[1])) { okc = false; System.out.println("  " + c[0] + " → " + d); } }
        check(okc && MusicIdCodec.parseAudd("{\"status\":\"error\"}").errorCode == -1, "MI13. mã 700/500/400/300 có thông báo riêng theo tài liệu; mã chưa biết nêu mã; thiếu đối tượng error → -1");
        StringBuilder longMsg = new StringBuilder(); for (int i = 0; i < 400; i++) longMsg.append('x');
        check(MusicIdCodec.describeError(MusicIdCodec.parseAudd("{\"status\":\"error\",\"error\":{\"error_code\":1,\"error_message\":\"" + longMsg + "\"}}")).length() < 300, "MI13b. lời dịch vụ quá dài bị cắt ngắn");
        // Phản hồi THẬT của api.audd.io (lấy ngày 2026-11-27 bằng ví dụ công khai trong docs.audd.io, token 'test'), nguyên văn
        r = MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{\"artist\":\"Tears For Fears\",\"title\":\"Everybody Wants To Rule The World\",\"album\":\"Songs From The Big Chair\",\"release_date\":\"2014-11-10\",\"label\":\"UMC (Universal Music Catalogue)\",\"timecode\":\"00:57\",\"song_link\":\"https://lis.tn/NbkVb\"}}");
        check(r.status == MusicIdCodec.Status.MATCH && r.artist.equals("Tears For Fears") && r.title.equals("Everybody Wants To Rule The World") && r.album.equals("Songs From The Big Chair") && r.releaseDate.equals("2014-11-10") && r.label.startsWith("UMC") && r.timecode.equals("00:57") && r.songLink.equals("https://lis.tn/NbkVb"), "MI-REAL1. phản hồi thật (có kết quả) đọc đúng mọi trường");
        check(MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":null}").status == MusicIdCodec.Status.NO_MATCH, "MI-REAL2. phản hồi thật khi không nhận ra bài → NO_MATCH");
        r = MusicIdCodec.parseAudd("{\"status\":\"error\",\"error\":{\"error_code\":900,\"error_message\":\"Recognition failed: authorization failed: the provided api_token is incorrect, invalid, or inactive. Please check the status of your account on dashboard.audd.io: it needs to have either a trial or an active subscription.\"},\"request_params\":{\"api_token\":\"n***3\",\"url\":\"https://audd.tech/example.mp3\"},\"request_api_method\":\"recognize\",\"request_http_method\":\"GET\",\"see api documentation\":\"https://docs.audd.io\",\"contact us\":\"api@audd.io\"}");
        check(r.status == MusicIdCodec.Status.ERROR && r.errorCode == 900 && MusicIdCodec.describeError(r).contains("Khoá API"), "MI-REAL3. phản hồi thật khi token sai (900, có thêm các trường phụ) → ERROR đúng mã");
        check(MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":[]}").status == MusicIdCodec.Status.NO_MATCH, "MI-DOC1. theo tài liệu: result có thể là mảng rỗng khi không khớp → NO_MATCH");
        String[] bad = {null, "", "rác", "[]", "{\"status\":1}", "{\"status\":\"pending\"}", "{\"status\":\"success\",\"result\":", "{\"result\":{\"title\":\"x\"}}", "<html>502</html>"};
        boolean ok = true; for (String b : bad) { MusicIdCodec.Result x = MusicIdCodec.parseAudd(b); if (x.status != MusicIdCodec.Status.ERROR) { ok = false; System.out.println("  không phải ERROR: " + b); } }
        check(ok, "MI14. rỗng/rác/cụt/sai kiểu/thiếu status/HTML → ERROR, không ném ngoại lệ, không giả MATCH");
        check(MusicIdCodec.parseAudd("{\"status\":\"success\",\"result\":{\"title\":5,\"artist\":null}}").status == MusicIdCodec.Status.NO_MATCH, "MI15. trường sai kiểu bị coi là trống (không ép thành chuỗi)");

        check(MusicIdCodec.shouldRetry(0, true, 0) && MusicIdCodec.shouldRetry(503, false, 1) && MusicIdCodec.shouldRetry(502, false, 0) && MusicIdCodec.shouldRetry(504, false, 0), "MI16. thử lại: lỗi mạng và 502/503/504 khi còn lượt");
        check(!MusicIdCodec.shouldRetry(0, true, MusicIdCodec.MAX_ATTEMPTS) && !MusicIdCodec.shouldRetry(503, false, 2), "MI17. hết lượt thì không thử lại (giới hạn " + MusicIdCodec.MAX_ATTEMPTS + " lần gửi)");
        check(!MusicIdCodec.shouldRetry(400, false, 0) && !MusicIdCodec.shouldRetry(401, false, 0) && !MusicIdCodec.shouldRetry(429, false, 0) && !MusicIdCodec.shouldRetry(200, false, 0) && !MusicIdCodec.shouldRetry(500, false, 0), "MI18. không thử lại 4xx, 429 (hạn mức), 200 hay 500");
    }
}
