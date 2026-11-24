package com.grape.play;

import java.util.ArrayList;
import java.util.List;

/** So sánh số phiên bản và lọc "các bản cũ hơn" cho tính năng rollback — thuần Java (có test). */
public final class Versions {
    private Versions() {}

    private static int[] parts(String v) {
        int i = 0; String s = v == null ? "" : v.trim();
        while (i < s.length() && !Character.isDigit(s.charAt(i))) i++;
        String[] raw = s.substring(i).split("[.\\-+]");
        int[] r = new int[raw.length];
        for (int k = 0; k < raw.length; k++) {
            int e = 0; String t = raw[k];
            while (e < t.length() && Character.isDigit(t.charAt(e))) e++;
            try { r[k] = e == 0 ? 0 : Integer.parseInt(t.substring(0, e)); } catch (NumberFormatException x) { r[k] = 0; }
        }
        return r;
    }

    /** >0 nếu a mới hơn b, <0 nếu cũ hơn, 0 nếu bằng (0.8.10 > 0.8.9; "v2.9.4" = "2.9.4"). */
    public static int compare(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q ? 1 : -1;
        }
        return 0;
    }
    public static boolean isNewer(String remote, String local) { return compare(remote, local) > 0; }

    /** Các phiên bản CŨ HƠN [current], mới nhất trước, bỏ trùng, tối đa [max]. */
    public static List<String> olderThan(String current, List<String> all, int max) {
        List<String> out = new ArrayList<>();
        for (String v : all) {
            if (v == null || !v.matches(".*\\d.*") || compare(v, current) >= 0) continue;   // bỏ tag không có chữ số
            boolean dup = false; for (String o : out) if (compare(o, v) == 0) dup = true;
            if (!dup) out.add(v);
        }
        out.sort((a, b) -> compare(b, a));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
