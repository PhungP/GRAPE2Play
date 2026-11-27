package com.grape.play;

import java.io.EOFException;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bộ giải mã FLAC thuần Java → luồng WAV PCM (16/24/32-bit little-endian).
 * Android 8.0 không có decoder FLAC trong MediaCodec (chỉ có từ 8.1/10), nên ExoPlayer không phát được FLAC
 * → ta giải mã bằng phần mềm rồi để WavExtractor của ExoPlayer phát như file WAV.
 */
public final class FlacPcm implements PcmStream {
    public static final class Meta {
        public int sampleRate, channels, bps, minBlock, maxBlock;
        public long totalSamples;
        public long audioStart;
        public String title = "", artist = "", albumArtist = "", album = "", genre = "";
        public int track, disc, year;
        public String composer = "";
        public byte[] picture;
        public long durationMs() { return sampleRate > 0 && totalSamples > 0 ? totalSamples * 1000L / sampleRate : 0L; }
    }

    // ---------------------------------------------------------------- metadata
    private static int u8(byte[] b, int i) { return b[i] & 0xff; }

    public static Meta readMeta(ByteSource s, boolean wantPicture) throws IOException {
        byte[] h = new byte[4];
        long pos = 0;
        if (s.readAt(0, h, 0, 4) < 4) throw new IOException("Không phải FLAC");
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') { // bỏ qua thẻ ID3v2 ở đầu file
            byte[] t = new byte[10];
            readFully(s, 0, t, 0, 10);
            pos = 10 + (((t[6] & 0x7f) << 21) | ((t[7] & 0x7f) << 14) | ((t[8] & 0x7f) << 7) | (t[9] & 0x7f));
            readFully(s, pos, h, 0, 4);
        }
        if (!(h[0] == 'f' && h[1] == 'L' && h[2] == 'a' && h[3] == 'C')) throw new IOException("Không phải FLAC");
        pos += 4;
        Meta m = new Meta();
        boolean haveInfo = false, last = false;
        while (!last) {
            byte[] bh = new byte[4];
            readFully(s, pos, bh, 0, 4);
            last = (bh[0] & 0x80) != 0;
            int type = bh[0] & 0x7f;
            int len = (u8(bh, 1) << 16) | (u8(bh, 2) << 8) | u8(bh, 3);
            long body = pos + 4;
            pos = body + len;
            if (type == 0 && len >= 34) {
                byte[] b = new byte[34];
                readFully(s, body, b, 0, 34);
                m.minBlock = (u8(b, 0) << 8) | u8(b, 1); m.maxBlock = (u8(b, 2) << 8) | u8(b, 3);
                m.sampleRate = (u8(b, 10) << 12) | (u8(b, 11) << 4) | (u8(b, 12) >> 4);
                m.channels = ((u8(b, 12) >> 1) & 7) + 1;
                m.bps = (((u8(b, 12) & 1) << 4) | (u8(b, 13) >> 4)) + 1;
                m.totalSamples = ((long) (u8(b, 13) & 15) << 32) | ((long) u8(b, 14) << 24) | ((long) u8(b, 15) << 16) | ((long) u8(b, 16) << 8) | u8(b, 17);
                haveInfo = true;
            } else if (type == 4 && len < 2_000_000) {
                byte[] b = new byte[len];
                readFully(s, body, b, 0, len);
                parseVorbis(b, m);
            } else if (type == 6 && wantPicture && m.picture == null && len < 12_000_000) {
                byte[] b = new byte[len];
                readFully(s, body, b, 0, len);
                parsePicture(b, m);
            }
            if (pos > s.length()) throw new IOException("Metadata FLAC hỏng");
        }
        if (!haveInfo) throw new IOException("Thiếu STREAMINFO");
        m.audioStart = pos;
        return m;
    }

    private static int le32(byte[] b, int p) { return u8(b, p) | (u8(b, p + 1) << 8) | (u8(b, p + 2) << 16) | (u8(b, p + 3) << 24); }
    private static int be32(byte[] b, int p) { return (u8(b, p) << 24) | (u8(b, p + 1) << 16) | (u8(b, p + 2) << 8) | u8(b, p + 3); }

    private static void parseVorbis(byte[] b, Meta m) {
        try {
            int p = 0;
            int vl = le32(b, p); p += 4 + vl;
            int n = le32(b, p); p += 4;
            for (int i = 0; i < n && p + 4 <= b.length; i++) {
                int l = le32(b, p); p += 4;
                if (l < 0 || p + l > b.length) break;
                String kv = new String(b, p, l, "UTF-8"); p += l;
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq).toUpperCase(), v = kv.substring(eq + 1).trim();
                if (v.isEmpty()) continue;
                switch (k) {
                    case "TITLE": m.title = v; break;
                    case "ARTIST": if (m.artist.isEmpty()) m.artist = v; break;
                    case "ALBUMARTIST": case "ALBUM ARTIST": m.albumArtist = v; break;
                    case "ALBUM": m.album = v; break;
                    case "COMPOSER": if (m.composer.isEmpty()) m.composer = v; break;
                    case "DISCNUMBER": case "DISC":
                        try { m.disc = Integer.parseInt(v.split("/")[0].trim()); } catch (Exception ignore) { }
                        break;
                    case "DATE": case "YEAR": case "ORIGINALDATE":
                        if (m.year == 0 && v.length() >= 4) { try { int y = Integer.parseInt(v.substring(0, 4)); if (y >= 1000 && y <= 2999) m.year = y; } catch (Exception ignore) { } }
                        break;
                    case "GENRE": if (m.genre.isEmpty()) m.genre = v; break;
                    case "TRACKNUMBER": case "TRACK":
                        try { m.track = Integer.parseInt(v.split("/")[0].trim()); } catch (Exception ignore) { }
                        break;
                    default: break;
                }
            }
        } catch (Exception ignore) { }
    }

    private static void parsePicture(byte[] b, Meta m) {
        try {
            int p = 4;
            int ml = be32(b, p); p += 4 + ml;
            int dl = be32(b, p); p += 4 + dl;
            p += 16;
            int n = be32(b, p); p += 4;
            if (n > 0 && p + n <= b.length) { m.picture = new byte[n]; System.arraycopy(b, p, m.picture, 0, n); }
        } catch (Exception ignore) { }
    }

    private static void readFully(ByteSource s, long pos, byte[] b, int off, int len) throws IOException {
        int got = 0;
        while (got < len) {
            int r = s.readAt(pos + got, b, off + got, len - got);
            if (r <= 0) throw new EOFException();
            got += r;
        }
    }

    // ---------------------------------------------------------------- stream
    private final ByteSource src;
    private final Meta meta;
    private final int outBytes;
    private final int frameBytes;
    private final long dataBytes;
    private final byte[] header = new byte[44];

    // đọc đệm
    private final byte[] ib = new byte[128 * 1024];
    private int ibPos, ibLen;
    private long fpos; // vị trí file của byte kế tiếp cần nạp vào ib
    // bit reader
    private long bits;
    private int nb;
    private final byte[] hbuf = new byte[32];
    private int hlen;
    private boolean capture;

    // trạng thái giải mã
    private long decSample; // chỉ số mẫu đầu của frame kế tiếp
    private byte[] pcm = new byte[0];
    private int pcmPos, pcmLen;
    private long curPos; // vị trí byte (trong phần data) của byte kế tiếp sẽ trả ra
    private long[][] ch = new long[0][];
    private final TreeMap<Long, Long> cps = new TreeMap<>(); // mẫu -> offset file (điểm đặt)
    private long lastCp = -1_000_000_000L;

    public FlacPcm(ByteSource src) throws IOException { this(src, readMeta(src, false)); }

    public FlacPcm(ByteSource src, Meta m) throws IOException {
        this.src = src; this.meta = m;
        outBytes = m.bps <= 16 ? 2 : (m.bps <= 24 ? 3 : 4);
        frameBytes = outBytes * m.channels;
        long total = m.totalSamples > 0 ? m.totalSamples : Math.max(1, (src.length() - m.audioStart)) * 2L / Math.max(1, m.channels * 2); // ước lượng thô nếu thiếu
        dataBytes = total * frameBytes;
        buildHeader();
        cps.put(0L, m.audioStart);
        resetTo(0, m.audioStart);
    }

    public Meta meta() { return meta; }

    // ---- Chính sách khi dữ liệu FLAC hỏng (theo hành vi của libFLAC) ----
    //  * CRC16 của khung sai  → khung đó được thay bằng IM LẶNG (đúng độ dài, giữ nguyên thời gian), đếm là khung hỏng và báo qua [listener].
    //    Dữ liệu hỏng KHÔNG BAO GIỜ được phát như dữ liệu hợp lệ.
    //  * Khung mất đồng bộ / tiêu đề (CRC8) sai / cú pháp subframe sai → bỏ qua tới mã đồng bộ kế tiếp; khung hợp lệ kế tiếp mang số khung/số mẫu nên
    //    khoảng thời gian bị mất được lấp bằng im lặng (đầu ra giữ đúng độ dài khai báo, các khung sau đúng vị trí).
    //  * File hợp lệ không bao giờ đi vào hai nhánh trên: đầu ra không đổi từng bit.
    /** Nhận thông báo hư hỏng (mô tả ngắn). Có thể null. */
    public interface Listener { void onDamage(String message); }
    public Listener listener;
    private long damagedFrames;
    /** Số khung bị thay bằng im lặng do CRC16 sai + số lần mất đồng bộ đã lấp. */
    public long damagedFrames() { return damagedFrames; }
    /** Tổng số mẫu (mỗi kênh) đã được lấp bằng im lặng thay cho dữ liệu hỏng/mất. */
    public long silencedSamples() { return silencedSamples; }
    private long silencedSamples;
    private static final long MAX_GAP = 1L << 20;           // chỉ lấp khoảng trống tới ~1 triệu mẫu; lớn hơn coi như không đáng tin
    private void damage(String msg) { damagedFrames++; Listener l = listener; if (l != null) { try { l.onDamage(msg); } catch (RuntimeException ignored) { } } }

    private void buildHeader() {
        byte[] h = header;
        put(h, 0, "RIFF"); le(h, 4, (int) Math.min(0x7fffffffL, 36 + dataBytes)); put(h, 8, "WAVE");
        put(h, 12, "fmt "); le(h, 16, 16); h[20] = 1; h[21] = 0;
        h[22] = (byte) meta.channels; h[23] = 0;
        le(h, 24, meta.sampleRate); le(h, 28, meta.sampleRate * frameBytes);
        h[32] = (byte) frameBytes; h[33] = 0; h[34] = (byte) (outBytes * 8); h[35] = 0;
        put(h, 36, "data"); le(h, 40, (int) Math.min(0x7fffffffL, dataBytes));
    }
    private static void put(byte[] b, int p, String s) { for (int i = 0; i < 4; i++) b[p + i] = (byte) s.charAt(i); }
    private static void le(byte[] b, int p, int v) { b[p] = (byte) v; b[p + 1] = (byte) (v >> 8); b[p + 2] = (byte) (v >> 16); b[p + 3] = (byte) (v >> 24); }

    @Override public long size() { return 44 + dataBytes; }
    @Override public void close() { src.close(); }

    private void resetTo(long sample, long fileOff) {
        fpos = fileOff; ibPos = 0; ibLen = 0; bits = 0; nb = 0; lost = false;
        decSample = sample; pcmPos = 0; pcmLen = 0;
        curPos = sample * frameBytes;
    }

    @Override
    public int read(long pos, byte[] b, int off, int len) throws IOException {
        if (len <= 0) return 0;
        if (pos >= size()) return -1;
        if (pos < 44) {
            int n = (int) Math.min(len, 44 - pos);
            System.arraycopy(header, (int) pos, b, off, n);
            return n;
        }
        long want = pos - 44;
        if (want >= dataBytes) return -1;
        if (want != curPos) seekTo(want);
        int total = 0;
        while (total < len && curPos < dataBytes) {
            if (pcmPos >= pcmLen) { if (!decodeFrame()) break; }
            int n = Math.min(Math.min(len - total, pcmLen - pcmPos), (int) Math.min(Integer.MAX_VALUE, dataBytes - curPos));
            System.arraycopy(pcm, pcmPos, b, off + total, n);
            pcmPos += n; curPos += n; total += n;
        }
        return total == 0 ? -1 : total;
    }

    private void seekTo(long want) throws IOException {
        long target = want / frameBytes;
        long bufStartSample = (curPos - pcmPos) / frameBytes; // mẫu đầu của frame đang giữ trong đệm
        boolean forwardOk = want >= curPos && target - bufStartSample < 20L * meta.sampleRate; // tiến tới gần: giải mã bỏ qua
        if (!forwardOk) {
            Map.Entry<Long, Long> e = cps.floorEntry(target);
            if (e == null) e = cps.firstEntry();
            // nếu điểm đặt gần nhất vẫn nằm sau vị trí hiện tại và hiện tại ≤ đích thì tiếp tục từ hiện tại
            if (want >= curPos && bufStartSample >= e.getKey()) { /* giữ nguyên */ }
            else resetTo(e.getKey(), e.getValue());
        }
        // bỏ qua byte cho tới đích
        while (curPos < want) {
            if (pcmPos >= pcmLen) { if (!decodeFrame()) return; }
            int n = (int) Math.min(pcmLen - pcmPos, want - curPos);
            pcmPos += n; curPos += n;
        }
    }

    // ---------------------------------------------------------------- byte & bit input
    private int crc16; private boolean crcOn, lost;
    private int rb() throws IOException {
        if (ibPos >= ibLen) {
            ibLen = 0; ibPos = 0;
            int r = src.readAt(fpos, ib, 0, ib.length);
            if (r <= 0) return -1;
            ibLen = r; fpos += r;
        }
        int v = ib[ibPos++] & 0xff;
        if (capture && hlen < hbuf.length) hbuf[hlen++] = (byte) v;
        if (crcOn) crc16 = ((crc16 << 8) & 0xffff) ^ CRC16[((crc16 >> 8) ^ v) & 0xff];
        return v;
    }

    private long readU(int n) throws IOException {
        while (nb < n) {
            int b = rb();
            if (b < 0) throw new EOFException();
            bits = (bits << 8) | b; nb += 8;
        }
        nb -= n;
        long v = (bits >>> nb) & ((1L << n) - 1);
        bits &= (1L << nb) - 1;
        return v;
    }
    private long readS(int n) throws IOException {
        if (n == 0) return 0;
        long v = readU(n);
        return (v << (64 - n)) >> (64 - n);
    }
    private int readUnary() throws IOException {
        int q = 0;
        for (;;) {
            if (nb == 0) {
                int b = rb();
                if (b < 0) throw new EOFException();
                bits = b; nb = 8;
            }
            if (bits == 0) { q += nb; nb = 0; continue; }
            int lz = nb - (64 - Long.numberOfLeadingZeros(bits));
            q += lz; nb -= lz + 1;
            bits &= (1L << nb) - 1;
            return q;
        }
    }

    /** CRC-16/BUYPASS (đa thức 0x8005, khởi tạo 0, không đảo bit): CRC của khung FLAC, tính từ mã đồng bộ tới hết subframe. */
    private static final int[] CRC16 = new int[256];
    private static final int[] CRC8 = new int[256];
    static {
        for (int i = 0; i < 256; i++) { int c = i << 8; for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x8005) & 0xffff : (c << 1) & 0xffff; CRC16[i] = c; }
        for (int i = 0; i < 256; i++) { int c = i; for (int k = 0; k < 8; k++) c = (c & 0x80) != 0 ? ((c << 1) ^ 0x07) & 0xff : (c << 1) & 0xff; CRC8[i] = c; }
    }

    // ---------------------------------------------------------------- frame
    /** Giải mã frame kế tiếp vào pcm. false = hết dữ liệu. */
    private boolean decodeFrame() throws IOException {
        int attempts = 0;
        while (true) {
            if (meta.totalSamples > 0 && decSample >= meta.totalSamples) return false;
            long frameStartPos = fpos - (ibLen - ibPos);
            try {
                if (decodeFrameInner(frameStartPos)) return true;
                return false;
            } catch (EOFException e) {
                return false;
            } catch (IOException e) {
                if (++attempts > 50) throw e;
                lost = true;                                   // khung bị mất: khung hợp lệ kế tiếp sẽ lấp khoảng trống bằng im lặng
                if (!resync()) return false;
            }
        }
    }

    /** Tìm mã đồng bộ frame kế tiếp (0xFFF8/0xFFF9) có CRC8 header hợp lệ. */
    private boolean resync() throws IOException {
        crcOn = false; nb = 0; bits = 0;
        int prev = rb();
        for (int guard = 0; guard < 4_000_000; guard++) {
            int b = rb();
            if (b < 0 || prev < 0) return false;
            if (prev == 0xff && (b == 0xf8 || b == 0xf9)) {
                // lùi lại 2 byte để đọc lại từ mã đồng bộ
                if (ibPos >= 2) { ibPos -= 2; return true; }
                fpos = fpos - (ibLen - ibPos) - 0; ibPos = 0; ibLen = 0; fpos -= 2; return true;
            }
            prev = b;
        }
        return false;
    }

    private boolean decodeFrameInner(long frameStartPos) throws IOException {
        nb = 0; bits = 0; hlen = 0; capture = true; crc16 = 0; crcOn = true;
        long sync;
        int first = rb();
        if (first < 0) { capture = false; return false; }
        int second = rb();
        if (second < 0) { capture = false; return false; }
        if (first != 0xff || (second != 0xf8 && second != 0xf9)) { capture = false; crcOn = false; throw new IOException("Mất đồng bộ FLAC"); }
        long hdr = readU(16);
        int bsCode = (int) ((hdr >> 12) & 15), srCode = (int) ((hdr >> 8) & 15), chAsg = (int) ((hdr >> 4) & 15), szCode = (int) ((hdr >> 1) & 7);
        // số thứ tự (UTF-8 mở rộng)
        int b0 = (int) readU(8);
        long frameNum = b0 < 0x80 ? b0 : -1;
        if (b0 >= 0xc0) { int extra = b0 >= 0xfe ? 6 : b0 >= 0xfc ? 5 : b0 >= 0xf8 ? 4 : b0 >= 0xf0 ? 3 : b0 >= 0xe0 ? 2 : 1; frameNum = b0 & (0x3F >> extra); for (int i = 0; i < extra; i++) frameNum = (frameNum << 6) | (readU(8) & 0x3F); }
        int blocksize;
        if (bsCode == 1) blocksize = 192;
        else if (bsCode >= 2 && bsCode <= 5) blocksize = 576 << (bsCode - 2);
        else if (bsCode == 6) blocksize = (int) readU(8) + 1;
        else if (bsCode == 7) blocksize = (int) readU(16) + 1;
        else if (bsCode >= 8) blocksize = 256 << (bsCode - 8);
        else { capture = false; throw new IOException("blocksize không hợp lệ"); }
        if (srCode == 12) readU(8); else if (srCode == 13 || srCode == 14) readU(16);
        long crcByte = readU(8);
        capture = false;
        int c = 0;
        for (int i = 0; i < hlen - 1; i++) c = CRC8[c ^ (hbuf[i] & 0xff)];
        if (c != (int) crcByte) { crcOn = false; throw new IOException("CRC8 header sai"); }
        // Mẫu đầu tiên của khung (từ số khung/số mẫu trong tiêu đề): khung độ dài cố định → số khung × cỡ khối; độ dài thay đổi → chính số mẫu
        long startSample = -1;
        if (frameNum >= 0) startSample = second == 0xf8 ? (meta.maxBlock > 0 && meta.minBlock == meta.maxBlock ? frameNum * meta.maxBlock : -1) : frameNum;
        int bps;
        switch (szCode) { case 0: bps = meta.bps; break; case 1: bps = 8; break; case 2: bps = 12; break; case 4: bps = 16; break; case 5: bps = 20; break; case 6: bps = 24; break; case 7: bps = 32; break; default: throw new IOException("bit depth không hợp lệ"); }
        int nch = chAsg <= 7 ? chAsg + 1 : 2;
        if (chAsg > 10) throw new IOException("channel assignment không hợp lệ");
        if (nch != meta.channels) throw new IOException("số kênh đổi giữa chừng");

        if (ch.length < nch || ch[0].length < blocksize) { ch = new long[nch][blocksize]; }
        for (int i = 0; i < nch; i++) {
            int sb = bps + ((chAsg == 8 && i == 1) || (chAsg == 9 && i == 0) || (chAsg == 10 && i == 1) ? 1 : 0);
            subframe(ch[i], blocksize, sb);
        }
        nb = 0; bits = 0; // căn byte
        crcOn = false;
        int crcHi = rb(), crcLo = rb();
        boolean crcBad = crcHi >= 0 && crcLo >= 0 && ((crcHi << 8) | crcLo) != crc16;   // thiếu byte CRC (file cụt ở khung cuối): giữ hành vi cũ

        if (chAsg == 8) for (int i = 0; i < blocksize; i++) ch[1][i] = ch[0][i] - ch[1][i];
        else if (chAsg == 9) for (int i = 0; i < blocksize; i++) ch[0][i] = ch[0][i] + ch[1][i];
        else if (chAsg == 10) for (int i = 0; i < blocksize; i++) {
            long mid = ch[0][i], side = ch[1][i];
            mid = (mid << 1) | (side & 1);
            ch[0][i] = (mid + side) >> 1; ch[1][i] = (mid - side) >> 1;
        }

        // Khoảng trống do khung trước bị mất: lấp bằng im lặng (chỉ khi có bằng chứng mất đồng bộ — file hợp lệ không bao giờ vào nhánh này)
        long gap = 0;
        if (lost) {
            lost = false;
            if (startSample > decSample && startSample - decSample <= MAX_GAP && (meta.totalSamples <= 0 || startSample + blocksize <= meta.totalSamples)) gap = startSample - decSample;
        }
        int gapBytes = (int) (gap * frameBytes);
        int need = blocksize * frameBytes;
        if (pcm.length < gapBytes + need) pcm = new byte[gapBytes + need];
        if (gapBytes > 0) { java.util.Arrays.fill(pcm, 0, gapBytes, (byte) 0); silencedSamples += gap; damage("FLAC: mất " + gap + " mẫu trước mẫu " + startSample + " (khung hỏng/mất đồng bộ) — đã lấp bằng im lặng"); }
        if (crcBad) {
            // CRC16 sai: dữ liệu khung không đáng tin → im lặng (giữ thời gian), không phát dữ liệu hỏng như thể hợp lệ
            java.util.Arrays.fill(pcm, gapBytes, gapBytes + need, (byte) 0);
            silencedSamples += blocksize;
            damage("FLAC: CRC16 sai ở khung mẫu " + (decSample + gap) + " (" + blocksize + " mẫu) — đã thay bằng im lặng");
        } else {
            int shift = outBytes * 8 - bps;
            int p = gapBytes;
            for (int i = 0; i < blocksize; i++) {
                for (int k = 0; k < nch; k++) {
                    long v = shift >= 0 ? ch[k][i] << shift : ch[k][i] >> -shift;
                    pcm[p++] = (byte) v;
                    pcm[p++] = (byte) (v >> 8);
                    if (outBytes >= 3) pcm[p++] = (byte) (v >> 16);
                    if (outBytes == 4) pcm[p++] = (byte) (v >> 24);
                }
            }
        }
        pcmPos = 0; pcmLen = gapBytes + need;
        long frameSample = decSample + gap;
        if (frameSample - lastCp >= 2L * meta.sampleRate || frameSample == 0) { cps.put(frameSample, frameStartPos); lastCp = frameSample; }
        decSample = frameSample + blocksize;
        if (crcBad) {
            // Không tin vị trí đã đọc (nội dung hỏng có thể làm ta đọc lấn sang khung kế tiếp): quay lại ngay sau mã đồng bộ của khung này và tìm mã đồng bộ kế tiếp.
            // Khung hợp lệ kế tiếp sẽ được neo bằng số khung/số mẫu của nó (lost = true).
            fpos = frameStartPos + 2; ibPos = 0; ibLen = 0; nb = 0; bits = 0; crcOn = false;
            lost = true;
            if (!resync()) { ibPos = 0; ibLen = 0; }
        }
        return true;
    }

    private void subframe(long[] out, int n, int sb) throws IOException {
        readU(1);
        int type = (int) readU(6);
        int wasted = 0;
        if (readU(1) == 1) wasted = readUnary() + 1;
        sb -= wasted;
        if (sb <= 0 || sb > 33) throw new IOException("bit depth subframe sai");
        if (type == 0) {
            long v = readS(sb);
            for (int i = 0; i < n; i++) out[i] = v;
        } else if (type == 1) {
            for (int i = 0; i < n; i++) out[i] = readS(sb);
        } else if (type >= 8 && type <= 12) {
            int order = type - 8;
            if (order > n) throw new IOException("order > blocksize");
            for (int i = 0; i < order; i++) out[i] = readS(sb);
            residual(out, n, order);
            switch (order) {
                case 0: break;
                case 1: for (int i = 1; i < n; i++) out[i] += out[i - 1]; break;
                case 2: for (int i = 2; i < n; i++) out[i] += 2 * out[i - 1] - out[i - 2]; break;
                case 3: for (int i = 3; i < n; i++) out[i] += 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3]; break;
                default: for (int i = 4; i < n; i++) out[i] += 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4]; break;
            }
        } else if (type >= 32) {
            int order = type - 31;
            if (order > n) throw new IOException("order > blocksize");
            for (int i = 0; i < order; i++) out[i] = readS(sb);
            int prec = (int) readU(4) + 1;
            if (prec == 16) throw new IOException("precision sai");
            int shift = (int) readS(5);
            if (shift < 0) shift = 0;
            long[] coef = new long[order];
            for (int i = 0; i < order; i++) coef[i] = readS(prec);
            residual(out, n, order);
            for (int i = order; i < n; i++) {
                long sum = 0;
                for (int j = 0; j < order; j++) sum += coef[j] * out[i - 1 - j];
                out[i] += sum >> shift;
            }
        } else throw new IOException("subframe type không hợp lệ");
        if (wasted > 0) for (int i = 0; i < n; i++) out[i] <<= wasted;
    }

    private void residual(long[] out, int n, int predOrder) throws IOException {
        int method = (int) readU(2);
        if (method > 1) throw new IOException("residual method sai");
        int pb = method == 0 ? 4 : 5;
        int esc = method == 0 ? 15 : 31;
        int order = (int) readU(4);
        int parts = 1 << order;
        int per = n >> order;
        if ((n & (parts - 1)) != 0 || per < predOrder) throw new IOException("partition sai");
        int idx = predOrder;
        for (int p = 0; p < parts; p++) {
            int cnt = p == 0 ? per - predOrder : per;
            int k = (int) readU(pb);
            if (k == esc) {
                int bitsPer = (int) readU(5);
                for (int i = 0; i < cnt; i++) out[idx++] = readS(bitsPer);
            } else {
                for (int i = 0; i < cnt; i++) {
                    long q = readUnary();
                    long r = k == 0 ? 0 : readU(k);
                    long v = (q << k) | r;
                    out[idx++] = (v >>> 1) ^ -(v & 1);
                }
            }
        }
    }
}
