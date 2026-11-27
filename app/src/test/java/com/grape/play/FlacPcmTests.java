package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/** Phase 2: FlacPcm giải mã ĐÚNG TỪNG BIT (dữ liệu tạo bởi bộ mã hoá kiểm thử độc lập), header WAV, tua, và hành vi khi khung hỏng. */
final class FlacPcmTests {
    private FlacPcmTests() {}

    static ByteSource mem(final byte[] d) {
        return new ByteSource() {
            public long length() { return d.length; }
            public int readAt(long pos, byte[] b, int off, int len) { if (pos >= d.length) return -1; int n = (int) Math.min(len, d.length - pos); System.arraycopy(d, (int) pos, b, off, n); return n; }
            public void close() { }
        };
    }
    static long[][] music(int ch, int n, int bps, long seed) {
        Random r = new Random(seed); long max = (1L << (bps - 1)) - 1; long[][] x = new long[ch][n];
        for (int c = 0; c < ch; c++) for (int i = 0; i < n; i++) {
            double s = 0.45 * Math.sin(2 * Math.PI * (220 + 110 * c) * i / 44100.0) + 0.25 * Math.sin(2 * Math.PI * 1760 * i / 44100.0) + 0.02 * (r.nextDouble() - 0.5);
            x[c][i] = Math.max(-max - 1, Math.min(max, Math.round(s * max)));
        }
        return x;
    }
    static byte[] decodeAll(byte[] flac) throws IOException {
        FlacPcm p = new FlacPcm(mem(flac)); byte[] out = new byte[(int) p.size()]; int pos = 0;
        while (pos < out.length) { int r = p.read(pos, out, pos, Math.min(7000, out.length - pos)); if (r <= 0) break; pos += r; }
        return Arrays.copyOf(out, pos);
    }
    static boolean exact(long[][] pcm, int bps, byte[] flac, String what) throws IOException {
        byte[] got = decodeAll(flac), data = FlacTestEncoder.wavData(pcm, bps);
        if (got.length != 44 + data.length) { System.out.println("  " + what + ": độ dài " + got.length + " ≠ " + (44 + data.length)); return false; }
        for (int i = 0; i < data.length; i++) if (got[44 + i] != data[i]) { System.out.println("  " + what + ": khác tại byte " + i); return false; }
        return true;
    }

    static void run() {
        try { all(); } catch (Exception e) { check(false, "FP-EX. ngoại lệ không mong đợi: " + e); }
    }

    static void all() throws Exception {
        // Vector kiểm thử độc lập cho CRC (không phụ thuộc bộ mã hoá)
        byte[] v = "123456789".getBytes("US-ASCII");
        check(FlacTestEncoder.crc16(v, v.length) == 0xFEE8 && FlacTestEncoder.crc8(v, v.length) == 0xF4, "FP1. vector CRC chuẩn: CRC-16/BUYPASS('123456789')=0xFEE8, CRC-8('123456789')=0xF4");

        long[][] s16 = music(2, 20000, 16, 1);
        check(exact(s16, 16, FlacTestEncoder.encode(s16, 44100, 16, 4096, 0, 0, 0), "16-bit verbatim"), "FP2. 16-bit stereo VERBATIM: PCM đầu ra đúng từng bit");
        for (int order = 0; order <= 4; order++)
            check(exact(s16, 16, FlacTestEncoder.encode(s16, 44100, 16, 4096, 0, 1, order), "16-bit fixed" + order), "FP3." + order + " 16-bit FIXED bậc " + order + " (Rice): PCM đúng từng bit");
        long[][] s24 = music(2, 30000, 24, 2);
        check(exact(s24, 24, FlacTestEncoder.encode(s24, 96000, 24, 4608, 0, 1, 2), "24-bit fixed2"), "FP4. 24-bit/96 kHz FIXED bậc 2: PCM 24-bit đúng từng bit (không hạ xuống 16-bit)");
        check(exact(s24, 24, FlacTestEncoder.encode(s24, 192000, 24, 4096, 0, 0, 0), "24-bit verbatim 192k"), "FP5. 24-bit/192 kHz VERBATIM đúng từng bit");
        for (int asg : new int[]{8, 9, 10})
            check(exact(s16, 16, FlacTestEncoder.encode(s16, 44100, 16, 4096, asg, 1, 1), "chasg " + asg) && exact(s24, 24, FlacTestEncoder.encode(s24, 48000, 24, 4096, asg, 1, 2), "chasg24 " + asg), "FP6." + asg + " ghép kênh " + (asg == 8 ? "trái/side" : asg == 9 ? "side/phải" : "mid/side") + ": 16 và 24-bit đúng từng bit");
        long[][] zero = new long[2][10000]; long[][] constant = new long[2][10000]; Arrays.fill(constant[0], 1234); Arrays.fill(constant[1], -777);
        check(exact(zero, 16, FlacTestEncoder.encode(zero, 44100, 16, 4096, 0, 2, 0), "silence") && exact(constant, 16, FlacTestEncoder.encode(constant, 44100, 16, 4096, 0, 2, 0), "constant"), "FP7. khung CONSTANT/im lặng đúng từng bit");
        long[][] ext = new long[2][8192]; for (int i = 0; i < 8192; i++) { ext[0][i] = (i % 2 == 0) ? 32767 : -32768; ext[1][i] = (i % 3 == 0) ? -32768 : 32767; }
        check(exact(ext, 16, FlacTestEncoder.encode(ext, 44100, 16, 4096, 10, 1, 1), "extremes mid/side"), "FP8. mẫu cực trị ±full-scale qua mid/side: đúng từng bit (không tràn/cắt)");
        long[][] mono = music(1, 9000, 16, 3);
        check(exact(mono, 16, FlacTestEncoder.encode(mono, 44100, 16, 4096, 0, 1, 2), "mono"), "FP9. mono 16-bit đúng từng bit");

        // Header WAV
        byte[] flac = FlacTestEncoder.encode(s24, 96000, 24, 4096, 0, 1, 2); byte[] wav = decodeAll(flac);
        int sr = (wav[24] & 0xff) | (wav[25] & 0xff) << 8 | (wav[26] & 0xff) << 16, bpsH = wav[34] & 0xff, chH = wav[22] & 0xff, br = (wav[28] & 0xff) | (wav[29] & 0xff) << 8 | (wav[30] & 0xff) << 16;
        check(sr == 96000 && bpsH == 24 && chH == 2 && br == 96000 * 2 * 3 && wav[20] == 1, "FP10. header WAV: 96 kHz, 24-bit, 2 kênh, byte-rate đúng, định dạng PCM — tần số/độ sâu được bảo toàn, không resample");

        // Tua: đọc ngẫu nhiên khớp với đọc tuần tự
        byte[] full = FlacTestEncoder.wavData(s24, 24); FlacPcm p = new FlacPcm(mem(flac)); Random rnd = new Random(5); boolean ok = true;
        for (int it = 0; it < 300 && ok; it++) {
            int off = rnd.nextInt(full.length - 1), len = 1 + rnd.nextInt(Math.min(20000, full.length - off));
            byte[] b = new byte[len]; int got = 0;
            while (got < len) { int r = p.read(44 + off + got, b, got, len - got); if (r <= 0) break; got += r; }
            if (got != len || !Arrays.equals(b, Arrays.copyOfRange(full, off, off + len))) { ok = false; System.out.println("  tua sai tại " + off + " len " + len + " got " + got); }
        }
        check(ok, "FP11. 300 lần đọc ngẫu nhiên (tua tới/lui, độ dài bất kỳ) khớp byte với đọc tuần tự");

        // Khung hỏng — đặc tả hành vi thực tế
        byte[] clean = FlacTestEncoder.encode(s16, 44100, 16, 4096, 0, 0, 0); byte[] expect = FlacTestEncoder.wavData(s16, 16);
        byte[] bad = clean.clone(); int frame2 = clean.length / 5 * 2;        // một vị trí giữa dữ liệu (VERBATIM: không làm hỏng cú pháp)
        bad[frame2] ^= 0x55;
        byte[] gotBad = decodeAll(bad);
        int diff = 0; for (int i = 0; i < Math.min(gotBad.length - 44, expect.length); i++) if (gotBad[44 + i] != expect[i]) diff++;
        check(gotBad.length == clean.length - clean.length + 44 + expect.length && diff >= 1 && diff <= 2, "FP12. [ĐẶC TẢ] bit hỏng trong dữ liệu mẫu (cú pháp còn nguyên): CRC16 không được kiểm → giải mã vẫn ra đủ độ dài, sai " + diff + " byte tại đúng chỗ hỏng (không phát hiện, không im lặng)");
        // Hỏng tiêu đề khung (CRC8 sai) → bỏ qua khung đó, các khung sau vẫn giải mã
        byte[] bad2 = clean.clone(); int hdrAt = -1; for (int i = 40; i < bad2.length - 1; i++) if ((bad2[i] & 0xff) == 0xff && (bad2[i + 1] & 0xff) == 0xf8 && i > clean.length / 4) { hdrAt = i; break; }
        bad2[hdrAt + 4] ^= 0x01;
        byte[] gotBad2 = decodeAll(bad2);
        check(gotBad2.length < 44 + expect.length, "FP13. [ĐẶC TẢ] tiêu đề khung hỏng (CRC8 sai): khung bị bỏ qua và KHÔNG được thay bằng im lặng — đầu ra ngắn hơn " + (44 + expect.length - gotBad2.length) + " byte so với khai báo (thời gian phía sau bị lệch)");
        byte[] tail = Arrays.copyOfRange(gotBad2, gotBad2.length - 2000, gotBad2.length);
        boolean tailOk = false; byte[] ex = expect; for (int i = 0; i <= ex.length - 2000 && !tailOk; i += 2) { tailOk = Arrays.equals(tail, Arrays.copyOfRange(ex, i, i + 2000)); }
        check(tailOk, "FP14. sau khung hỏng, các khung sau vẫn giải mã đúng từng bit (bộ giải mã tự đồng bộ lại)");
    }
}
