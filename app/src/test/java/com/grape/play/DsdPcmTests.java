package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

/** Phase 2: DSD→PCM bằng tín hiệu DSD tổng hợp (bộ điều chế sigma-delta bậc 2): độ lợi, đáp tuyến tần số, tràn đỉnh, header — đo thật, không suy đoán. */
final class DsdPcmTests {
    private DsdPcmTests() {}
    static final int RATE = 2822400, BLOCK = 4096;

    /** DSF stereo, LSB-first, [blocks] khối 4096 byte mỗi kênh; kênh phải là sin tần số [fr] cùng biên độ. */
    static byte[] dsf(double amp, double freq, int blocks) {
        int bytesPerCh = blocks * BLOCK, samples = bytesPerCh * 8;
        byte[] f = new byte[92 + blocks * BLOCK * 2];
        put(f, 0, "DSD "); le64(f, 4, 28); le64(f, 12, f.length); le64(f, 20, 0);
        put(f, 28, "fmt "); le64(f, 32, 52); le32(f, 40, 1); le32(f, 44, 0); le32(f, 48, 2); le32(f, 52, 2); le32(f, 56, RATE); le32(f, 60, 1); le64(f, 64, samples); le32(f, 72, BLOCK);
        put(f, 80, "data"); le64(f, 84, 12 + blocks * BLOCK * 2L);
        for (int c = 0; c < 2; c++) {
            double i1 = 0, i2 = 0, y = 1;
            for (int n = 0; n < samples; n++) {
                double x = amp * Math.sin(2 * Math.PI * freq * n / RATE);
                i1 += x - y; i2 += i1 - y; y = i2 >= 0 ? 1 : -1;
                if (y > 0) { int blk = (n / 8) / BLOCK, within = (n / 8) % BLOCK; f[92 + blk * BLOCK * 2 + c * BLOCK + within] |= (byte) (1 << (n % 8)); }
            }
        }
        return f;
    }
    static void put(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    static void le32(byte[] b, int o, long v) { for (int i = 0; i < 4; i++) b[o + i] = (byte) (v >> (8 * i)); }
    static void le64(byte[] b, int o, long v) { for (int i = 0; i < 8; i++) b[o + i] = (byte) (v >> (8 * i)); }

    /** {đỉnh, số mẫu bị cắt, biên độ riêng của tần số thử}. Bỏ qua 4000 khung đầu/cuối (khởi động bộ lọc). Biên độ tần số thử đo bằng tương quan trên số nguyên chu kỳ. */
    static double[] measure(byte[] dsf, int rate, double freq, int cycles) throws Exception {
        DsdPcm p = new DsdPcm(FlacPcmTests.mem(dsf), rate);
        byte[] out = new byte[(int) p.size()]; int pos = 0;
        while (pos < out.length) { int r = p.read(pos, out, pos, Math.min(9000, out.length - pos)); if (r <= 0) break; pos += r; }
        int frames = (pos - 44) / 6, skip = 4000; double peak = 0; int clip = 0;
        int n = (int) Math.round(cycles * rate / freq); double re = 0, im = 0; int used = 0;
        for (int i = skip; i < frames - skip; i++) {
            int o = 44 + i * 6; int v = (out[o] & 0xff) | (out[o + 1] & 0xff) << 8 | out[o + 2] << 16;     // 24-bit có dấu
            if (v >= 8388607 || v <= -8388608) clip++;
            double x = v / 8388608.0; peak = Math.max(peak, Math.abs(x));
            if (used < n) { double w = 2 * Math.PI * freq * used / rate; re += x * Math.cos(w); im += x * Math.sin(w); used++; }
        }
        return new double[]{peak, clip, used == n ? 2 * Math.hypot(re, im) / n : -1};
    }

    static byte[] allOnesData() { byte[] a = dsf(0, 1000, 24); for (int i = 92; i < a.length; i++) a[i] = (byte) 0xFF; return a; }
    static void run() {
        try { all(); } catch (Exception e) { e.printStackTrace(); check(false, "DP-EX. ngoại lệ không mong đợi: " + e); }
    }

    static void all() throws Exception {
        double[] m = measure(dsf(0.5, 1000, 24), 44100, 1000, 10);
        double db = 20 * Math.log10(m[2] / 0.5);
        check(Math.abs(db) < 0.1 && m[1] == 0, String.format("DP1. DSD64 biên độ 0.5 @1 kHz → PCM 44,1 kHz/24-bit: biên độ tần số thử %.4f (lệch %.3f dB so với 0.5): độ lợi đơn vị, không cắt đỉnh", m[2], db));
        double[] m7 = measure(dsf(0.7, 1000, 24), 44100, 1000, 10);
        check(m7[1] == 0 && m7[0] < 0.75 && m7[0] > 0.65, String.format("DP2. biên độ 0.7 (DSD nóng) → đỉnh %.4f, %d mẫu bị cắt: KHÔNG tràn (headroom đủ cho DSD thực tế)", m7[0], (int) m7[1]));
        double[] m15 = measure(dsf(0.5, 15000, 24), 44100, 15000, 100);
        double db15 = 20 * Math.log10(m15[2] / 0.5);
        check(Math.abs(db15) < 0.3, String.format("DP3. 15 kHz: biên độ %.4f (%.3f dB so với 0.5) — băng thông giữ phẳng trong ±0,3 dB", m15[2], db15));
        double[] m176 = measure(dsf(0.5, 1000, 24), 176400, 1000, 10);
        check(Math.abs(20 * Math.log10(m176[2] / 0.5)) < 0.1 && m176[1] == 0, String.format("DP4. cùng tín hiệu → PCM 176,4 kHz: biên độ tần số thử %.4f (đổi tốc độ PCM không đổi mức)", m176[2]));
        DsdPcm p = new DsdPcm(FlacPcmTests.mem(dsf(0.5, 1000, 4)), 88200); byte[] h = new byte[44]; p.read(0, h, 0, 44);
        int sr = (h[24] & 0xff) | (h[25] & 0xff) << 8 | (h[26] & 0xff) << 16;
        check(sr == 88200 && h[34] == 24 && h[22] == 2 && p.outRate() == 88200, "DP5. header WAV: đúng tốc độ PCM yêu cầu (88,2 kHz), 24-bit, 2 kênh");
        AudioCoreTests.info.add(String.format("DSD→PCM (đo bằng DSD tổng hợp bậc 2): 1 kHz A=0.5 → %.4f (%.3f dB); A=0.7 → đỉnh %.4f; 15 kHz → %.3f dB; 176,4 kHz → %.4f; toàn bit 1 → đỉnh %.5f", m[2], db, m7[0], db15, m176[2], measure(allOnesData(), 44100, 1000, 10)[0]));
        byte[] allOnes = dsf(0, 1000, 24); for (int i = 92; i < allOnes.length; i++) allOnes[i] = (byte) 0xFF;
        double[] mx = measure(allOnes, 44100, 1000, 10);
        check(mx[0] > 0.999 && mx[0] <= 1.0, String.format("DP6. toàn bit 1 (tín hiệu DSD cực đại) → đỉnh PCM %.5f: đúng full-scale, không vượt", mx[0]));
    }
}
