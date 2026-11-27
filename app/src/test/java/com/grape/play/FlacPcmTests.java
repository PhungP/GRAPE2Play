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

        // ---- Chính sách khi dữ liệu hỏng (xem FlacPcm): CRC16 sai → im lặng; mất khung → lấp im lặng, giữ đúng độ dài và vị trí các khung sau ----
        final int FL = 4096;
        byte[] clean = FlacTestEncoder.encode(s16, 44100, 16, FL, 0, 0, 0); byte[] expect = FlacTestEncoder.wavData(s16, 16);
        java.util.List<Integer> starts = new java.util.ArrayList<>(FlacTestEncoder.frameStarts);
        int nFrames = starts.size();
        // (a) bit hỏng trong dữ liệu mẫu của khung 2 (cú pháp nguyên vẹn): CRC16 phải phát hiện
        byte[] bad = clean.clone(); bad[starts.get(2) + 100] ^= 0x55;
        final java.util.List<String> msgs = new java.util.ArrayList<>();
        FlacPcm fp = new FlacPcm(mem(bad)); fp.listener = msgs::add;
        byte[] gotBad = readAll(fp);
        boolean region = true, outside = gotBad.length == 44 + expect.length;
        for (int i = 0; i < expect.length && outside; i++) { boolean in = i >= 2 * FL * 4 && i < 3 * FL * 4; byte g = gotBad[44 + i]; if (in ? g != 0 : g != expect[i]) { outside = false; } }
        check(outside && fp.damagedFrames() == 1 && fp.silencedSamples() == FL && msgs.size() == 1 && msgs.get(0).contains("CRC16"), "FP12. bit hỏng trong khung 2: CRC16 phát hiện → đúng khung đó thành im lặng, MỌI khung khác đúng từng bit, độ dài đủ, 1 khung hỏng được báo");
        // (b) tiêu đề khung hỏng (CRC8 sai): khung bị mất được lấp bằng im lặng, các khung sau vẫn đúng VỊ TRÍ
        byte[] bad2 = clean.clone(); bad2[starts.get(1) + 4] ^= 0x01;
        FlacPcm fp2 = new FlacPcm(mem(bad2)); msgs.clear(); fp2.listener = msgs::add; byte[] gotBad2 = readAll(fp2);
        boolean ok2 = gotBad2.length == 44 + expect.length;
        for (int i = 0; i < expect.length && ok2; i++) { boolean in = i >= FL * 4 && i < 2 * FL * 4; byte g = gotBad2[44 + i]; if (in ? g != 0 : g != expect[i]) ok2 = false; }
        check(ok2 && fp2.silencedSamples() == FL && fp2.damagedFrames() >= 1, "FP13. tiêu đề khung 1 hỏng (CRC8 sai): khung mất được lấp bằng im lặng, đầu ra ĐỦ độ dài khai báo, các khung sau đúng từng bit tại đúng vị trí");
        // (c) file cụt ở khung cuối: không lỗi, phần trước đúng
        byte[] cut = java.util.Arrays.copyOf(clean, starts.get(nFrames - 1) + 200);
        byte[] gotCut = decodeAll(cut); boolean ok3 = gotCut.length < 44 + expect.length;
        for (int i = 0; i < (nFrames - 1) * FL * 4 && ok3; i++) if (gotCut[44 + i] != expect[i]) ok3 = false;
        check(ok3, "FP14. file cụt giữa khung cuối: không ném lỗi, các khung đầy đủ đúng từng bit, đầu ra ngắn hơn (file thật sự thiếu dữ liệu — không bịa im lặng)");
        // (d) file hợp lệ: không báo hư hỏng, không im lặng
        FlacPcm fp3 = new FlacPcm(mem(clean)); fp3.listener = m -> msgs.add("LỖI: " + m); msgs.clear(); readAll(fp3);
        check(fp3.damagedFrames() == 0 && fp3.silencedSamples() == 0 && msgs.isEmpty(), "FP15. file hợp lệ: 0 khung hỏng, 0 mẫu im lặng, không có thông báo");
        // (e) fuzz: làm hỏng 1 byte ở 400 vị trí ngẫu nhiên (nhiều kiểu mã hoá) — không ném lỗi/treo; khung hỏng không rò dữ liệu sai ra ngoài khung k, k+1
        Random fz = new Random(21); boolean fuzzOk = true; int fuzzCases = 0;
        for (int it = 0; it < 400 && fuzzOk; it++) {
            int kind = it % 3, order = 1 + it % 3, asg = (it / 3) % 2 == 0 ? 0 : 10;
            byte[] f = FlacTestEncoder.encode(s16, 44100, 16, FL, asg, kind == 2 ? 0 : 1, order);
            java.util.List<Integer> st = new java.util.ArrayList<>(FlacTestEncoder.frameStarts);
            int k = fz.nextInt(st.size() - 1);                                  // không phải khung cuối
            int at = st.get(k) + fz.nextInt(st.get(k + 1) - st.get(k)); byte[] g = f.clone(); g[at] ^= (byte) (1 + fz.nextInt(255));
            byte[] out; try { out = decodeAll(g); } catch (Exception e) { fuzzOk = false; System.out.println("  fuzz ném lỗi: " + e); break; }
            byte[] base = decodeAll(f);
            if (out.length != base.length) { fuzzOk = false; System.out.println("  fuzz độ dài " + out.length + " ≠ " + base.length + " (khung " + k + ", kiểu " + kind + ")"); break; }
            int firstOk = (k + 2) * FL * 4;
            for (int i = firstOk; i < base.length - 44; i++) if (out[44 + i] != base[44 + i]) { fuzzOk = false; System.out.println("  fuzz: khung sau khung hỏng bị sai tại byte " + i + " (khung " + k + ", kiểu " + kind + ")"); break; }
            for (int i = 0; i < k * FL * 4 && fuzzOk; i++) if (out[44 + i] != base[44 + i]) { fuzzOk = false; System.out.println("  fuzz: khung TRƯỚC khung hỏng bị sai"); break; }
            fuzzCases++;
        }
        check(fuzzOk && fuzzCases == 400, "FP16. fuzz 400 ca (1 byte hỏng, FIXED/VERBATIM, độc lập/mid-side): không ném lỗi, độ dài luôn đủ, khung trước đó và từ khung k+2 trở đi đúng từng bit");
        // Bit-perfect: phiên có khung hỏng bị thay bằng im lặng không bao giờ được coi là Verified
        BitPerfect.Input good = BitPerfectTests.cfg(24, 96000);
        check(BitPerfect.evaluate(good).status == BitPerfect.Status.VERIFIED, "FP17a. cấu hình hoàn chỉnh, không hỏng dữ liệu: VERIFIED (đường cơ sở)");
        BitPerfect.Input dmg = BitPerfectTests.cfg(24, 96000); dmg.decodeDamage = 1;
        BitPerfect.Result rd = BitPerfect.evaluate(dmg);
        check(rd.status == BitPerfect.Status.NOT_BIT_PERFECT && rd.changes.contains(BitPerfect.Code.DECODE_DAMAGE), "FP17. cùng cấu hình nhưng file có khung hỏng bị thay bằng im lặng → NOT_BIT_PERFECT (DECODE_DAMAGE)");
        dmg.pathProof = true;
        check(BitPerfect.evaluate(dmg).status == BitPerfect.Status.NOT_BIT_PERFECT, "FP18. kể cả khi có bằng chứng đường truyền: dữ liệu đã bị đổi thì không VERIFIED");
        BitPerfect.Input rem = BitPerfectTests.cfg(24, 96000); rem.route = BitPerfect.Route.UPNP; rem.decodeDamage = 5;
        check(!BitPerfect.evaluate(rem).changes.contains(BitPerfect.Code.DECODE_DAMAGE), "FP19. phát tới thiết bị ngoài gửi file gốc (không qua bộ giải mã của app): không áp dụng DECODE_DAMAGE");
    }

    static byte[] readAll(FlacPcm p) throws IOException {
        byte[] out = new byte[(int) p.size()]; int pos = 0;
        while (pos < out.length) { int r = p.read(pos, out, pos, Math.min(7000, out.length - pos)); if (r <= 0) break; pos += r; }
        return Arrays.copyOf(out, pos);
    }
}
