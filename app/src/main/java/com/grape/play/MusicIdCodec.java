package com.grape.play;

import java.util.Map;

/**
 * Nhận diện nhạc (thuần Java, có test): đóng gói WAV từ PCM micro, đọc phản hồi AudD, chính sách thử lại.
 * KHÔNG liên quan tới Audio Engine / đường phát nhạc: dữ liệu đến từ micro (âm thanh môi trường), không phải luồng phát nội bộ của app.
 */
public final class MusicIdCodec {
    private MusicIdCodec() {}

    public enum Status { MATCH, NO_MATCH, ERROR }

    public static final class Result {
        public final Status status;
        public final String artist, title, album, releaseDate, label, timecode, songLink, errorMessage;
        public final int errorCode;
        Result(Status s, String artist, String title, String album, String releaseDate, String label, String timecode, String songLink, int errorCode, String errorMessage) {
            status = s; this.artist = artist; this.title = title; this.album = album; this.releaseDate = releaseDate; this.label = label; this.timecode = timecode; this.songLink = songLink;
            this.errorCode = errorCode; this.errorMessage = errorMessage;
        }
        static Result error(int code, String msg) { return new Result(Status.ERROR, "", "", "", "", "", "", "", code, msg); }
    }

    /** WAV PCM 16-bit little-endian, đơn kênh, từ [length] mẫu đầu của [pcm]. */
    public static byte[] wav(short[] pcm, int length, int sampleRate) {
        int n = Math.max(0, Math.min(length, pcm.length));
        int dataLen = n * 2;
        byte[] out = new byte[44 + dataLen];
        put(out, 0, "RIFF"); le32(out, 4, 36 + dataLen); put(out, 8, "WAVE"); put(out, 12, "fmt ");
        le32(out, 16, 16); le16(out, 20, 1); le16(out, 22, 1); le32(out, 24, sampleRate); le32(out, 28, sampleRate * 2); le16(out, 32, 2); le16(out, 34, 16);
        put(out, 36, "data"); le32(out, 40, dataLen);
        for (int i = 0; i < n; i++) { out[44 + 2 * i] = (byte) (pcm[i] & 0xFF); out[45 + 2 * i] = (byte) ((pcm[i] >> 8) & 0xFF); }
        return out;
    }
    private static void put(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    private static void le16(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
    private static void le32(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }

    /** Biên độ đỉnh (0..32767) của đoạn thu — để phát hiện micro im lặng trước khi gửi đi. */
    public static int peak(short[] pcm, int length) {
        int p = 0, n = Math.min(length, pcm.length);
        for (int i = 0; i < n; i++) { int a = Math.abs((int) pcm[i]); if (a > p) p = a; }
        return p;
    }

    /** Phản hồi của api.audd.io. Không bao giờ ném ngoại lệ; sai cú pháp/kiểu → ERROR. Không có kết quả = NO_MATCH (không phải lỗi). */
    public static Result parseAudd(String json) {
        Object o = PersistCodec.parseJson(json);
        if (!(o instanceof Map)) return Result.error(-1, "Phản hồi từ dịch vụ không hợp lệ");
        Map<?, ?> m = (Map<?, ?>) o;
        Object st = m.get("status");
        if ("error".equals(st)) {
            int code = -1; String msg = "";
            Object e = m.get("error");
            if (e instanceof Map) {
                Object c = ((Map<?, ?>) e).get("error_code"), mm = ((Map<?, ?>) e).get("error_message");
                if (c instanceof Number) code = ((Number) c).intValue();
                if (mm instanceof String) msg = (String) mm;
            }
            return Result.error(code, msg);
        }
        if (!"success".equals(st)) return Result.error(-1, "Phản hồi từ dịch vụ không hợp lệ");
        Object r = m.get("result");
        if (!(r instanceof Map)) return new Result(Status.NO_MATCH, "", "", "", "", "", "", "", 0, "");
        Map<?, ?> rm = (Map<?, ?>) r;
        String title = str(rm, "title"), artist = str(rm, "artist");
        if (title.isEmpty() && artist.isEmpty()) return new Result(Status.NO_MATCH, "", "", "", "", "", "", "", 0, "");
        return new Result(Status.MATCH, artist, title, str(rm, "album"), str(rm, "release_date"), str(rm, "label"), str(rm, "timecode"), str(rm, "song_link"), 0, "");
    }
    private static String str(Map<?, ?> m, String k) { Object v = m.get(k); return v instanceof String ? ((String) v).trim() : ""; }

    /** Thông báo cho người dùng (tiếng Việt) theo mã lỗi; luôn kèm lời của dịch vụ nếu có. Mã theo tài liệu AudD (900 token sai, 901/902 hết hạn mức). */
    public static String describeError(Result r) {
        String base;
        switch (r.errorCode) {
            case 900: base = "Khoá API AudD không hợp lệ — kiểm tra lại khoá."; break;
            case 901: case 902: base = "Đã hết hạn mức nhận diện của khoá AudD."; break;
            default: base = "Dịch vụ nhận diện báo lỗi" + (r.errorCode >= 0 ? " (mã " + r.errorCode + ")" : "") + ".";
        }
        return r.errorMessage.isEmpty() || base.contains(r.errorMessage) ? base : base + " " + r.errorMessage;
    }

    /** Thử lại có giới hạn: chỉ lỗi mạng hoặc 502/503/504, tối đa [MAX_ATTEMPTS] lần gửi. Không thử lại 4xx/429 (khoá sai, hết hạn mức). */
    public static final int MAX_ATTEMPTS = 2;
    public static boolean shouldRetry(int httpCode, boolean networkError, int attemptsDone) {
        if (attemptsDone >= MAX_ATTEMPTS) return false;
        return networkError || httpCode == 502 || httpCode == 503 || httpCode == 504;
    }
}
