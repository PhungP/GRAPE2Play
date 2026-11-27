package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lịch sử nghe (thuần Java, có test): mới nhất đứng đầu, tối đa [MAX] mục; phát liên tiếp cùng một bài chỉ cập nhật thời điểm. */
public final class HistoryLog {
    private HistoryLog() {}
    public static final int MAX = 2000;

    public static final class Entry {
        public final String path; public final long at;
        public Entry(String path, long at) { this.path = path; this.at = at; }
    }

    public static List<Entry> push(List<Entry> cur, String path, long at) {
        List<Entry> out = new ArrayList<>(Math.min(MAX, cur.size() + 1));
        out.add(new Entry(path, at));
        int start = (!cur.isEmpty() && cur.get(0).path.equals(path)) ? 1 : 0;
        for (int i = start; i < cur.size() && out.size() < MAX; i++) out.add(cur.get(i));
        return out;
    }

    public static List<Entry> remap(List<Entry> cur, Map<String, String> m) {
        List<Entry> out = new ArrayList<>(cur.size());
        for (Entry e : cur) out.add(m.containsKey(e.path) ? new Entry(m.get(e.path), e.at) : e);
        return out;
    }

    public static String encode(List<Entry> l) {
        List<Map<String, String>> recs = new ArrayList<>(l.size());
        for (Entry e : l) { Map<String, String> m = new LinkedHashMap<>(); m.put("p", e.path); m.put("t", Long.toString(e.at)); recs.add(m); }
        return PersistCodec.encodeRecords(recs);
    }
    /** null nếu sai định dạng (bản ghi thiếu p/t hoặc t không phải số). */
    public static List<Entry> decode(String raw) {
        List<Map<String, String>> recs = PersistCodec.decodeRecords(raw, "p", "t");
        if (recs == null) return null;
        List<Entry> out = new ArrayList<>(recs.size());
        for (Map<String, String> m : recs) { try { out.add(new Entry(m.get("p"), Long.parseLong(m.get("t")))); } catch (NumberFormatException e) { return null; } }
        return out;
    }
    public static boolean isValid(String raw) { return decode(raw) != null; }
}
