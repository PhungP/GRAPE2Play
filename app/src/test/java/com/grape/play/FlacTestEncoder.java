package com.grape.play;

import java.io.ByteArrayOutputStream;

/** Bộ mã hoá FLAC tối giản chỉ dùng để TẠO DỮ LIỆU KIỂM THỬ cho FlacPcm: CONSTANT, VERBATIM, FIXED (bậc 0-4, Rice phân vùng bậc 0), các kiểu ghép kênh stereo. Không dùng trong app. */
final class FlacTestEncoder {
    private FlacTestEncoder() {}

    static final class Bits {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(); long acc; int n;
        void put(long v, int bits) { for (int i = bits - 1; i >= 0; i--) { acc = (acc << 1) | ((v >> i) & 1); if (++n == 8) { out.write((int) acc); acc = 0; n = 0; } } }
        void unary(long q) { for (long i = 0; i < q; i++) put(0, 1); put(1, 1); }
        void align() { while (n != 0) put(0, 1); }
        byte[] bytes() { align(); return out.toByteArray(); }
    }

    static int crc8(byte[] b, int len) { int c = 0; for (int i = 0; i < len; i++) { c ^= b[i] & 0xff; for (int k = 0; k < 8; k++) c = (c & 0x80) != 0 ? ((c << 1) ^ 0x07) & 0xff : (c << 1) & 0xff; } return c; }
    /** CRC-16/BUYPASS (đa thức 0x8005, khởi tạo 0, không đảo bit) — đúng định nghĩa CRC16 của khung FLAC. */
    static int crc16(byte[] b, int len) { int c = 0; for (int i = 0; i < len; i++) { c ^= (b[i] & 0xff) << 8; for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x8005) & 0xffff : (c << 1) & 0xffff; } return c; }

    /** kind: 0 = VERBATIM, 1 = FIXED bậc [order], 2 = CONSTANT (dùng mẫu đầu). */
    static void subframe(Bits w, long[] x, int n, int sb, int kind, int order) {
        if (kind == 2) { w.put(0, 1); w.put(0, 6); w.put(0, 1); w.put(x[0], sb); return; }
        if (kind == 0) { w.put(0, 1); w.put(1, 6); w.put(0, 1); for (int i = 0; i < n; i++) w.put(x[i], sb); return; }
        w.put(0, 1); w.put(8 + order, 6); w.put(0, 1);
        for (int i = 0; i < order; i++) w.put(x[i], sb);
        long[] r = new long[n - order];
        for (int i = order; i < n; i++) {
            long p;
            switch (order) { case 0: p = 0; break; case 1: p = x[i - 1]; break; case 2: p = 2 * x[i - 1] - x[i - 2]; break; case 3: p = 3 * x[i - 1] - 3 * x[i - 2] + x[i - 3]; break; default: p = 4 * x[i - 1] - 6 * x[i - 2] + 4 * x[i - 3] - x[i - 4]; }
            r[i - order] = x[i] - p;
        }
        int bestK = 0; long bestBits = Long.MAX_VALUE;
        for (int k = 0; k <= 14; k++) { long bits = 0; for (long v : r) { long u = v >= 0 ? v << 1 : ((-v) << 1) - 1; bits += (u >> k) + 1 + k; } if (bits < bestBits) { bestBits = bits; bestK = k; } }
        w.put(0, 2); w.put(0, 4); w.put(bestK, 4);
        for (long v : r) { long u = v >= 0 ? v << 1 : ((-v) << 1) - 1; w.unary(u >> bestK); if (bestK > 0) w.put(u & ((1L << bestK) - 1), bestK); }
    }

    /**
     * Tạo file FLAC từ [pcm][kênh][mẫu]. chAsg: 0 = độc lập, 8 = trái/side, 9 = side/phải, 10 = mid/side (chỉ stereo). frameLen: số mẫu mỗi khung. kind/order: kiểu subframe.
     */
    static byte[] encode(long[][] pcm, int rate, int bps, int frameLen, int chAsg, int kind, int order) {
        int ch = pcm.length, total = pcm[0].length;
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write('f'); f.write('L'); f.write('a'); f.write('C');
        Bits si = new Bits();
        si.put(1, 1); si.put(0, 7); si.put(34, 24);
        si.put(frameLen, 16); si.put(frameLen, 16); si.put(0, 24); si.put(0, 24);
        si.put(rate, 20); si.put(ch - 1, 3); si.put(bps - 1, 5); si.put(total, 36);
        for (int i = 0; i < 16; i++) si.put(0, 8);
        byte[] sib = si.bytes(); f.write(sib, 0, sib.length);
        int frameNo = 0;
        for (int start = 0; start < total; start += frameLen, frameNo++) {
            int n = Math.min(frameLen, total - start);
            Bits h = new Bits();
            h.put(0xFFF8, 16);
            h.put(7, 4); h.put(0, 4);                                  // blocksize = 16 bit (n-1) ở cuối; tần số lấy từ STREAMINFO
            h.put(chAsg == 0 ? ch - 1 : chAsg, 4); h.put(bps == 16 ? 4 : bps == 24 ? 6 : 4, 3); h.put(0, 1);
            h.put(frameNo, 8);                                          // số khung (< 128: UTF-8 một byte)
            h.put(n - 1, 16);
            byte[] hb = h.bytes();
            byte[] hdr = new byte[hb.length + 1]; System.arraycopy(hb, 0, hdr, 0, hb.length); hdr[hb.length] = (byte) crc8(hb, hb.length);
            Bits w = new Bits();
            long[][] sub = new long[ch][n];
            for (int c = 0; c < ch; c++) System.arraycopy(pcm[c], start, sub[c], 0, n);
            long[][] enc = new long[ch][];
            int[] sbs = new int[ch];
            for (int c = 0; c < ch; c++) { enc[c] = sub[c]; sbs[c] = bps; }
            if (ch == 2 && chAsg == 8) { enc[1] = new long[n]; for (int i = 0; i < n; i++) enc[1][i] = sub[0][i] - sub[1][i]; sbs[1] = bps + 1; }
            else if (ch == 2 && chAsg == 9) { enc[0] = new long[n]; for (int i = 0; i < n; i++) enc[0][i] = sub[0][i] - sub[1][i]; sbs[0] = bps + 1; }
            else if (ch == 2 && chAsg == 10) { enc[0] = new long[n]; enc[1] = new long[n]; for (int i = 0; i < n; i++) { enc[0][i] = (sub[0][i] + sub[1][i]) >> 1; enc[1][i] = sub[0][i] - sub[1][i]; } sbs[1] = bps + 1; }
            for (int c = 0; c < ch; c++) subframe(w, enc[c], n, sbs[c], kind, Math.min(order, n - 1));
            byte[] body = w.bytes();
            byte[] frame = new byte[hdr.length + body.length + 2];
            System.arraycopy(hdr, 0, frame, 0, hdr.length); System.arraycopy(body, 0, frame, hdr.length, body.length);
            int c16 = crc16(frame, frame.length - 2);
            frame[frame.length - 2] = (byte) (c16 >> 8); frame[frame.length - 1] = (byte) c16;
            f.write(frame, 0, frame.length);
        }
        return f.toByteArray();
    }

    /** PCM mong đợi (WAV little-endian, 16/24-bit) cho [pcm][kênh][mẫu]. */
    static byte[] wavData(long[][] pcm, int bps) {
        int ch = pcm.length, n = pcm[0].length, ob = bps <= 16 ? 2 : 3;
        byte[] out = new byte[n * ch * ob]; int p = 0;
        for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) { long v = pcm[c][i]; for (int k = 0; k < ob; k++) out[p++] = (byte) (v >> (8 * k)); }
        return out;
    }
}
