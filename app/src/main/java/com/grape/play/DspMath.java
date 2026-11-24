package com.grape.play;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Toán của EQ 10 băng + preamp + cân bằng L/R, tách khỏi Android để chạy được trong test (javac/JUnit).
 * DspProcessor (Dsp.kt) chỉ gọi lại các hàm này nên test kiểm đúng mã đang chạy trong app.
 */
public final class DspMath {
    private DspMath() {}

    public static final float[] FREQS = {31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f};
    public static final int PCM16 = 0, PCM24 = 1, PCM32 = 2, FLOAT = 3;
    public static final double Q = 1.4;
    private static final int GRID = 240;
    public static final double MARGIN_DB = 0.3;

    /** EQ phẳng / tắt: DSP chép nguyên dữ liệu (chép nguyên ở tầng DSP; chưa phải bằng chứng Bit-perfect toàn đường truyền). */
    public static boolean isFlat(boolean on, float[] bands, float balance, float preampDb) {
        if (!on) return true;
        for (float b : bands) if (Math.abs(b) >= 0.05f) return false;
        return Math.abs(balance) < 0.01f && Math.abs(preampDb) < 0.05f;
    }

    public static final class Plan {
        public int nb;
        public final double[] b0 = new double[10], b1 = new double[10], b2 = new double[10], a1 = new double[10], a2 = new double[10];
        public final double[] gain = new double[8];
        /** Đỉnh đáp tuyến (dB) của EQ + preamp trên lưới 20 Hz…Nyquist (đo bằng lưới 241 điểm log, không phải chứng minh toán học). */
        public double peakDb;
        /** Độ lợi tự thêm (≤ 0 dB) để đỉnh đáp tuyến không vượt 0 dB. */
        public double headroomDb;
    }

    public static Plan plan(float[] bands, float preampDb, float balance, int sr, boolean autoHeadroom) {
        Plan p = new Plan();
        for (int i = 0; i < 10; i++) {
            float db = bands[i], f0 = FREQS[i];
            if (Math.abs(db) < 0.05f || f0 >= sr * 0.45f) continue;
            double a = Math.pow(10.0, db / 40.0);
            double w = 2 * Math.PI * f0 / sr;
            double al = Math.sin(w) / (2 * Q);
            double cw = Math.cos(w);
            double a0 = 1 + al / a;
            p.b0[p.nb] = (1 + al * a) / a0; p.b1[p.nb] = (-2 * cw) / a0; p.b2[p.nb] = (1 - al * a) / a0;
            p.a1[p.nb] = (-2 * cw) / a0; p.a2[p.nb] = (1 - al / a) / a0;
            p.nb++;
        }
        double peak = preampDb;
        for (int i = 0; i <= GRID; i++) {
            double fr = 20.0 * Math.pow(1000.0, i / (double) GRID);
            if (fr >= sr * 0.5) break;
            double db = preampDb + eqResponseDb(p, fr, sr);
            if (db > peak) peak = db;
        }
        p.peakDb = peak;
        // Chỉ hạ khi EQ/preamp thực sự nâng (peak > 0,05 dB): EQ chỉ cắt không bị đổi mức. Dư thêm MARGIN_DB cho sai số lưới và độ vọt khi tín hiệu bắt đầu đột ngột (đo được ≤ 1,5 % ≈ 0,13 dB).
        p.headroomDb = (autoHeadroom && peak > 0.05) ? -(peak + MARGIN_DB) : 0.0;
        double pre = Math.pow(10.0, (preampDb + p.headroomDb) / 20.0);
        for (int c = 0; c < 8; c++)
            p.gain[c] = pre * (c == 0 ? Math.min(1f, 1f - balance) : c == 1 ? Math.min(1f, 1f + balance) : 1f);
        return p;
    }

    /** Đáp tuyến biên độ (dB) của riêng các băng EQ (không gồm preamp/headroom/cân bằng) tại tần số f. */
    public static double eqResponseDb(Plan p, double f, int sr) {
        double w = 2 * Math.PI * f / sr;
        double c1 = Math.cos(w), s1 = Math.sin(w), c2 = Math.cos(2 * w), s2 = Math.sin(2 * w);
        double db = 0;
        for (int k = 0; k < p.nb; k++) {
            double nr = p.b0[k] + p.b1[k] * c1 + p.b2[k] * c2, ni = -(p.b1[k] * s1 + p.b2[k] * s2);
            double dr = 1 + p.a1[k] * c1 + p.a2[k] * c2, di = -(p.a1[k] * s1 + p.a2[k] * s2);
            db += 10 * Math.log10((nr * nr + ni * ni) / (dr * dr + di * di));
        }
        return db;
    }

    public static final class State {
        final double[][] z1 = new double[10][8], z2 = new double[10][8];
        public void reset() { for (int k = 0; k < 10; k++) { java.util.Arrays.fill(z1[k], 0); java.util.Arrays.fill(z2[k], 0); } }
    }

    public static int bytesPer(int kind) { return kind == PCM16 ? 2 : kind == PCM24 ? 3 : 4; }

    /**
     * Xử lý toàn bộ [in] (từ position tới limit) sang [out] (đủ chỗ, đã đặt LITTLE_ENDIAN hoặc không — hàm tự đặt). Trả về số mẫu bị cắt đỉnh
     * (chỉ tính cho PCM số nguyên; float không bị cắt). Sau khi gọi, [in] đã đọc hết và [out] chưa flip.
     */
    public static long process(ByteBuffer in, ByteBuffer out, int kind, int ch, Plan p, State st) {
        in.order(ByteOrder.LITTLE_ENDIAN); out.order(ByteOrder.LITTLE_ENDIAN);
        final int bytes = bytesPer(kind);
        final int n = in.remaining();
        final int frames = n / (bytes * Math.max(ch, 1));
        int pos = in.position();
        long clipped = 0;
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < ch; c++) {
                double x;
                switch (kind) {
                    case PCM16: x = in.getShort(pos) / 32768.0; break;
                    case PCM24: x = ((in.get(pos + 2) << 16) | ((in.get(pos + 1) & 0xff) << 8) | (in.get(pos) & 0xff)) / 8388608.0; break;
                    case PCM32: x = in.getInt(pos) / 2147483648.0; break;
                    default: x = in.getFloat(pos);
                }
                x *= p.gain[c];
                for (int k = 0; k < p.nb; k++) {
                    double y = p.b0[k] * x + st.z1[k][c];
                    st.z1[k][c] = p.b1[k] * x - p.a1[k] * y + st.z2[k][c];
                    st.z2[k][c] = p.b2[k] * x - p.a2[k] * y;
                    x = y;
                }
                if (kind != FLOAT && (x > 1.0 || x < -1.0)) clipped++;
                switch (kind) {
                    case PCM16: out.putShort((short) clamp(Math.round(x * 32768.0), -32768L, 32767L)); break;
                    case PCM24: { int v = (int) clamp(Math.round(x * 8388608.0), -8388608L, 8388607L); out.put((byte) v); out.put((byte) (v >> 8)); out.put((byte) (v >> 16)); break; }
                    case PCM32: out.putInt((int) clamp(Math.round(x * 2147483648.0), -2147483648L, 2147483647L)); break;
                    default: out.putFloat((float) x);
                }
                pos += bytes;
            }
        }
        for (int i = frames * bytes * Math.max(ch, 1); i < n; i++) { out.put(in.get(pos)); pos++; }
        in.position(in.limit());
        return clipped;
    }

    private static long clamp(long v, long lo, long hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
