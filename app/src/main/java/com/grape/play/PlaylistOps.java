package com.grape.play;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Chỉnh sửa danh sách đường dẫn của playlist (thuần Java, có test). Mọi hàm trả danh sách MỚI, không sửa đối số. */
public final class PlaylistOps {
    private PlaylistOps() {}
    public static final int MAX_NAME = 60;

    public static String cleanName(String n) { String t = n == null ? "" : n.trim(); if (t.isEmpty()) t = "Playlist"; return t.length() > MAX_NAME ? t.substring(0, MAX_NAME) : t; }

    /** Thêm vào cuối, bỏ các bài đã có (và trùng trong [add]). */
    public static List<String> addDistinct(List<String> cur, List<String> add) {
        List<String> out = new ArrayList<>(cur); Set<String> have = new HashSet<>(cur);
        for (String p : add) if (have.add(p)) out.add(p);
        return out;
    }
    public static List<String> removeAt(List<String> cur, int i) { List<String> out = new ArrayList<>(cur); if (i >= 0 && i < out.size()) out.remove(i); return out; }
    public static List<String> move(List<String> cur, int from, int to) {
        List<String> out = new ArrayList<>(cur);
        if (from < 0 || from >= out.size() || to < 0 || to >= out.size() || from == to) return out;
        out.add(to, out.remove(from)); return out;
    }
    /** Đổi đường dẫn khi file bị di chuyển; giữ thứ tự, bỏ trùng phát sinh. */
    public static List<String> remap(List<String> cur, Map<String, String> m) {
        List<String> out = new ArrayList<>(cur.size()); Set<String> seen = new HashSet<>();
        for (String p : cur) { String q = m.containsKey(p) ? m.get(p) : p; if (seen.add(q)) out.add(q); }
        return out;
    }
}
