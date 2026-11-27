package com.grape.play;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Lưu/khôi phục hàng đợi phát (thuần Java, không phụ thuộc Android — có unit test).
 * Hai file văn bản có phiên bản + CRC: danh sách đường dẫn (chỉ ghi khi hàng đợi đổi) và trạng thái nhỏ (chỉ số, vị trí, shuffle, repeat).
 * Ghi nguyên tử: file tạm → fsync → rename đè. Đọc hỏng/thiếu/cụt → trả null (không bao giờ ném lỗi, không ảnh hưởng phát nhạc).
 */
public final class QueueStore {
    private QueueStore() {}

    public static final class State {
        public final int index; public final long positionMs; public final boolean shuffle; public final int repeat;
        public final int queueCount; public final long queueCrc;
        public State(int index, long positionMs, boolean shuffle, int repeat, int queueCount, long queueCrc) {
            this.index = index; this.positionMs = positionMs; this.shuffle = shuffle; this.repeat = repeat; this.queueCount = queueCount; this.queueCrc = queueCrc;
        }
    }

    /** Kết quả đối chiếu: [kept] = chỉ số (trong danh sách đã lưu) của các bài còn tồn tại; [index]/[positionMs] theo danh sách đã lọc. */
    public static final class Resolved {
        public final int[] kept; public final int index; public final long positionMs; public final boolean currentMissing;
        Resolved(int[] kept, int index, long positionMs, boolean currentMissing) { this.kept = kept; this.index = index; this.positionMs = positionMs; this.currentMissing = currentMissing; }
    }

    // ---------- Mã hoá ----------
    /** Ký tự surrogate lẻ không mã hoá được UTF-8 (sẽ thành '?'): chuẩn hoá trước khi tính CRC/ghi để CRC luôn khớp sau khi đọc lại. */
    private static String norm(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean bad = false;
            if (Character.isHighSurrogate(c)) { if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) { if (b != null) b.append(c).append(s.charAt(i + 1)); i++; continue; } bad = true; }
            else if (Character.isLowSurrogate(c)) bad = true;
            if (bad) { if (b == null) { b = new StringBuilder(s.length()); b.append(s, 0, i); } b.append('?'); } else if (b != null) b.append(c);
        }
        return b == null ? s : b.toString();
    }

    private static String esc(String s) {
        s = norm(s);
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\"); else if (c == '\n') b.append("\\n"); else if (c == '\r') b.append("\\r"); else b.append(c);
        }
        return b.toString();
    }
    private static String unesc(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else b.append(c);
        }
        return b.toString();
    }

    public static long crc(List<String> paths) {
        CRC32 c = new CRC32();
        for (String p : paths) { c.update(norm(p).getBytes(StandardCharsets.UTF_8)); c.update(0); }
        return c.getValue();
    }

    public static String encodeQueue(List<String> paths) {
        StringBuilder b = new StringBuilder(paths.size() * 64 + 64);
        b.append("GRQ1\n").append(paths.size()).append('\n').append(Long.toHexString(crc(paths))).append('\n');
        for (String p : paths) b.append(esc(p)).append('\n');
        b.append("END\n");
        return b.toString();
    }

    /** null nếu sai định dạng / cụt / sai CRC. */
    public static List<String> decodeQueue(String s) {
        if (s == null) return null;
        String[] l = s.split("\n", -1);
        // l = GRQ1, count, crc, entries..., END, ""
        if (l.length < 5 || !"GRQ1".equals(l[0])) return null;
        int n; long crc;
        try { n = Integer.parseInt(l[1]); crc = Long.parseLong(l[2], 16); } catch (NumberFormatException e) { return null; }
        if (n < 0 || l.length != n + 5 || !"END".equals(l[n + 3]) || !l[n + 4].isEmpty()) return null;
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(unesc(l[3 + i]));
        return crc(out) == crc ? out : null;
    }

    public static String encodeState(State st) {
        return "GRS1\n" + st.index + "\n" + st.positionMs + "\n" + (st.shuffle ? 1 : 0) + "\n" + st.repeat + "\n" + st.queueCount + "\n" + Long.toHexString(st.queueCrc) + "\nEND\n";
    }

    public static State decodeState(String s) {
        if (s == null) return null;
        String[] l = s.split("\n", -1);
        if (l.length != 9 || !"GRS1".equals(l[0]) || !"END".equals(l[7]) || !l[8].isEmpty()) return null;
        try {
            int idx = Integer.parseInt(l[1]); long pos = Long.parseLong(l[2]); int sh = Integer.parseInt(l[3]); int rep = Integer.parseInt(l[4]);
            int cnt = Integer.parseInt(l[5]); long crc = Long.parseLong(l[6], 16);
            if (idx < 0 || pos < 0 || (sh != 0 && sh != 1) || rep < 0 || rep > 2 || cnt < 0) return null;
            return new State(idx, pos, sh == 1, rep, cnt, crc);
        } catch (NumberFormatException e) { return null; }
    }

    /**
     * Trạng thái đọc được [st] có khớp hàng đợi [keys] vừa đọc không? Không khớp (ghi dở giữa hai file, hoặc state hỏng = null):
     * giữ shuffle/repeat nếu có nhưng bắt đầu từ bài đầu, vị trí 0.
     */
    public static State reconcile(State st, List<String> keys) {
        long crc = crc(keys);
        if (st != null && st.queueCount == keys.size() && st.queueCrc == crc && st.index < keys.size()) return st;
        return new State(0, 0L, st != null && st.shuffle, st != null ? st.repeat : 2, keys.size(), crc);
    }

    // ---------- Đối chiếu ----------
    /**
     * [present][i] = bài i của danh sách đã lưu còn tồn tại trong thư viện. Trả null nếu không còn bài nào.
     * Bài đang nghe còn → giữ vị trí. Bài đang nghe mất → chuyển sang bài còn lại kế tiếp (hoặc bài cuối còn lại), vị trí về 0.
     */
    public static Resolved resolve(boolean[] present, int index, long positionMs) {
        int n = present.length, k = 0;
        for (boolean b : present) if (b) k++;
        if (k == 0) return null;
        int[] kept = new int[k]; int j = 0;
        for (int i = 0; i < n; i++) if (present[i]) kept[j++] = i;
        int idx = Math.max(0, Math.min(index, n - 1));
        int before = 0;
        for (int i = 0; i < idx; i++) if (present[i]) before++;
        boolean has = present[idx];
        return new Resolved(kept, Math.min(before, k - 1), has ? Math.max(0L, positionMs) : 0L, !has);
    }

    // ---------- Tệp ----------
    /** Ghi nguyên tử. Ném IOException nếu thất bại (file cũ giữ nguyên). */
    public static void writeAtomic(File f, String content) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(content.getBytes(StandardCharsets.UTF_8));
            o.flush(); o.getFD().sync();
        } catch (IOException e) { tmp.delete(); throw e; }
        if (!tmp.renameTo(f)) { tmp.delete(); throw new IOException("rename failed: " + f.getName()); }
    }

    /** Đọc nội dung; null nếu không có/không đọc được. */
    public static String read(File f) {
        try { return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null; } catch (IOException | RuntimeException e) { return null; }
    }
}
