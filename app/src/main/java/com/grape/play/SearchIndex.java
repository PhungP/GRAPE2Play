package com.grape.play;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Tìm kiếm thư viện trong bộ nhớ (thuần Java, có test). Không phân biệt hoa/thường và dấu tiếng Việt ("viet" khớp "Việt", "dang" khớp "Đặng").
 * Mọi từ trong truy vấn phải khớp (AND) ở ít nhất một trường (tên bài, nghệ sĩ, album, trường phụ). Kết quả xếp theo mức khớp rồi theo thứ tự gốc (ổn định).
 * Dựng chỉ mục O(n) một lần cho mỗi danh sách; mỗi truy vấn O(n) — đủ nhanh cho hàng chục nghìn bài khi chạy ở luồng nền.
 */
public final class SearchIndex {
    private final String[] title, artist, album, extra;

    /** [rows] mỗi phần tử = {tên bài, nghệ sĩ, album, trường phụ (album artist, thể loại, nhà soạn nhạc…)}. Thiếu phần tử = rỗng. */
    public SearchIndex(List<String[]> rows) {
        int n = rows.size();
        title = new String[n]; artist = new String[n]; album = new String[n]; extra = new String[n];
        for (int i = 0; i < n; i++) {
            String[] r = rows.get(i);
            title[i] = fold(r.length > 0 ? r[0] : ""); artist[i] = fold(r.length > 1 ? r[1] : "");
            album[i] = fold(r.length > 2 ? r[2] : ""); extra[i] = fold(r.length > 3 ? r[3] : "");
        }
    }

    public int size() { return title.length; }

    /** Chữ thường, bỏ dấu (NFD), đ→d. */
    public static String fold(String s) {
        if (s == null || s.isEmpty()) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            int t = Character.getType(c);
            if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || t == Character.ENCLOSING_MARK) continue;
            if (c == 'đ' || c == 'Đ') c = 'd';
            b.append(Character.toLowerCase(c));
        }
        return b.toString();
    }

    /** Chỉ số các dòng khớp, tốt nhất trước, tối đa [limit]. Truy vấn rỗng/chỉ khoảng trắng → mảng rỗng. */
    public int[] search(String query, int limit) {
        String q = fold(query == null ? "" : query).trim();
        if (q.isEmpty() || limit <= 0) return new int[0];
        String[] tok = q.split("\\s+");
        int n = title.length, count = 0;
        long[] hit = new long[Math.min(n, 4096)];       // (điểm << 32) | (đảo chỉ số) để sắp xếp một lần
        int cap = hit.length;
        for (int i = 0; i < n; i++) {
            int score = 0; boolean ok = true;
            for (String t : tok) {
                int s;
                if (title[i].startsWith(t)) s = 40;
                else if (title[i].contains(" " + t)) s = 30;
                else if (title[i].contains(t)) s = 20;
                else if (artist[i].contains(t)) s = 10;
                else if (album[i].contains(t)) s = 5;
                else if (extra[i].contains(t)) s = 1;
                else { ok = false; break; }
                score += s;
            }
            if (!ok) continue;
            if (count == cap) { cap = Math.min(n, cap * 2); hit = Arrays.copyOf(hit, cap); }
            hit[count++] = ((long) score << 32) | (long) (Integer.MAX_VALUE - i);
        }
        Arrays.sort(hit, 0, count);
        int m = Math.min(count, limit);
        int[] out = new int[m];
        for (int k = 0; k < m; k++) out[k] = Integer.MAX_VALUE - (int) (hit[count - 1 - k] & 0xFFFFFFFFL);
        return out;
    }
}
