package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mã hoá/giải mã NGHIÊM NGẶT các định dạng JSON hiện hành của Favorites, Recents (mảng chuỗi) và User Playlists
 * (mảng {n, t, p}) — thuần Java để kiểm thử. Dữ liệu sai cú pháp / cụt / sai kiểu → trả null (không bao giờ thành danh sách rỗng).
 * Đọc được mọi dữ liệu do org.json ghi (kể cả "\/").
 */
public final class PersistCodec {
    private PersistCodec() {}

    public static final class Playlist {
        public final String name; public final List<String> paths; public final long at;
        public Playlist(String name, List<String> paths, long at) { this.name = name; this.paths = paths; this.at = at; }
    }

    // ---------- Mảng chuỗi ----------
    public static String encodeStrings(List<String> l) {
        StringBuilder b = new StringBuilder(l.size() * 48 + 2).append('[');
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(','); str(b, l.get(i)); }
        return b.append(']').toString();
    }
    public static List<String> decodeStrings(String raw) {
        Object o = parse(raw);
        return o instanceof List ? strings((List<?>) o) : null;
    }
    public static boolean isStrings(String raw) { return decodeStrings(raw) != null; }

    // ---------- Playlist ----------
    public static String encodePlaylists(List<Playlist> l) {
        StringBuilder b = new StringBuilder(256).append('[');
        for (int i = 0; i < l.size(); i++) {
            Playlist p = l.get(i);
            if (i > 0) b.append(',');
            b.append("{\"n\":"); str(b, p.name); b.append(",\"t\":").append(p.at).append(",\"p\":").append(encodeStrings(p.paths)).append('}');
        }
        return b.append(']').toString();
    }
    public static List<Playlist> decodePlaylists(String raw) {
        Object o = parse(raw);
        if (!(o instanceof List)) return null;
        List<Playlist> out = new ArrayList<>();
        for (Object e : (List<?>) o) {
            if (!(e instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) e;
            Object n = m.get("n"), p = m.get("p"), t = m.get("t");
            if (!(n instanceof String) || !(p instanceof List)) return null;
            List<String> paths = strings((List<?>) p);
            if (paths == null) return null;
            long at = 0;
            if (t != null) { if (!(t instanceof Number)) return null; at = ((Number) t).longValue(); }
            out.add(new Playlist((String) n, paths, at));
        }
        return out;
    }
    public static boolean isPlaylists(String raw) { return decodePlaylists(raw) != null; }

    // ---------- Bản ghi {chuỗi: chuỗi} (danh sách nguồn nhạc) ----------
    /** Mảng đối tượng, mọi giá trị là chuỗi; mỗi bản ghi PHẢI có đủ các khoá [required]. Sai kiểu/thiếu khoá/cụt → null (không bỏ lẳng lặng từng mục). */
    public static List<Map<String, String>> decodeRecords(String raw, String... required) {
        Object o = parse(raw);
        if (!(o instanceof List)) return null;
        List<Map<String, String>> out = new ArrayList<>();
        for (Object e : (List<?>) o) {
            if (!(e instanceof Map)) return null;
            Map<String, String> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> kv : ((Map<?, ?>) e).entrySet()) {
                if (!(kv.getKey() instanceof String) || !(kv.getValue() instanceof String)) return null;
                m.put((String) kv.getKey(), (String) kv.getValue());
            }
            for (String k : required) if (!m.containsKey(k)) return null;
            out.add(m);
        }
        return out;
    }
    public static String encodeRecords(List<Map<String, String>> l) {
        StringBuilder b = new StringBuilder(256).append('[');
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(',');
            b.append('{'); boolean first = true;
            for (Map.Entry<String, String> kv : l.get(i).entrySet()) { if (!first) b.append(','); first = false; str(b, kv.getKey()); b.append(':'); str(b, kv.getValue()); }
            b.append('}');
        }
        return b.append(']').toString();
    }
    private static final String[] SOURCE_REQUIRED = {"id", "t", "n"};
    public static List<Map<String, String>> decodeSources(String raw) { return decodeRecords(raw, SOURCE_REQUIRED); }
    public static boolean isSources(String raw) { return decodeSources(raw) != null; }

    // ---------- Sửa tag nghệ sĩ/album: {"a":{từ:đến},"b":{từ:đến}} ----------
    /** Trả bản đồ {"a": nghệ sĩ, "b": album}; null nếu sai cú pháp/kiểu. Thiếu "a"/"b" = rỗng. */
    public static Map<String, Map<String, String>> decodeTagFix(String raw) {
        Object o = parse(raw);
        if (!(o instanceof Map)) return null;
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        out.put("a", new LinkedHashMap<String, String>()); out.put("b", new LinkedHashMap<String, String>());
        for (Map.Entry<?, ?> top : ((Map<?, ?>) o).entrySet()) {
            if (!(top.getKey() instanceof String)) return null;
            String k = (String) top.getKey();
            if (!k.equals("a") && !k.equals("b")) continue;          // khoá lạ (phiên bản mới hơn): bỏ qua
            if (!(top.getValue() instanceof Map)) return null;
            for (Map.Entry<?, ?> kv : ((Map<?, ?>) top.getValue()).entrySet()) {
                if (!(kv.getKey() instanceof String) || !(kv.getValue() instanceof String)) return null;
                out.get(k).put((String) kv.getKey(), (String) kv.getValue());
            }
        }
        return out;
    }
    public static boolean isTagFix(String raw) { return decodeTagFix(raw) != null; }
    public static String encodeTagFix(Map<String, String> artist, Map<String, String> album) {
        StringBuilder b = new StringBuilder(128).append("{\"a\":{");
        boolean first = true;
        for (Map.Entry<String, String> kv : artist.entrySet()) { if (!first) b.append(','); first = false; str(b, kv.getKey()); b.append(':'); str(b, kv.getValue()); }
        b.append("},\"b\":{"); first = true;
        for (Map.Entry<String, String> kv : album.entrySet()) { if (!first) b.append(','); first = false; str(b, kv.getKey()); b.append(':'); str(b, kv.getValue()); }
        return b.append("}}").toString();
    }

    /** "Phát gần đây": bài mới lên đầu, bỏ trùng, tối đa [max]. */
    public static List<String> pushRecent(List<String> cur, String path, int max) {
        List<String> out = new ArrayList<>(Math.min(max, cur.size() + 1));
        out.add(path);
        for (String s : cur) { if (out.size() >= max) break; if (!s.equals(path)) out.add(s); }
        return out;
    }

    private static List<String> strings(List<?> l) {
        List<String> out = new ArrayList<>(l.size());
        for (Object e : l) { if (!(e instanceof String)) return null; out.add((String) e); }
        return out;
    }

    private static void str(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }

    // ---------- Bộ đọc JSON nghiêm ngặt tối giản ----------
    private static final Object BAD = new Object();
    /** Giá trị trả về cho literal JSON null trong [parseJson]. */
    public static final Object JSON_NULL = BAD;
    /** JSON nghiêm ngặt → List / Map / String / Number / Boolean / [JSON_NULL]; null nếu sai cú pháp hoặc cụt. */
    public static Object parseJson(String raw) { return parse(raw); }
    /** null nếu sai cú pháp / cụt / còn ký tự thừa. (Giá trị JSON null cũng coi là không hợp lệ cho các định dạng này.) */
    private static Object parse(String s) {
        if (s == null) return null;
        try {
            P p = new P(s);
            Object v = p.value(0);
            p.ws();
            return p.i == s.length() ? v : null;
        } catch (IllegalArgumentException | StackOverflowError e) { return null; }
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }
        IllegalArgumentException bad() { return new IllegalArgumentException("json@" + i); }
        char peek() { if (i >= s.length()) throw bad(); return s.charAt(i); }
        Object value(int depth) {
            if (depth > 16) throw bad();
            ws();
            char c = peek();
            if (c == '[') {
                i++; List<Object> l = new ArrayList<>(); ws();
                if (peek() == ']') { i++; return l; }
                while (true) { l.add(value(depth + 1)); ws(); char d = peek(); i++; if (d == ']') return l; if (d != ',') throw bad(); }
            }
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (peek() == '}') { i++; return m; }
                while (true) {
                    ws(); if (peek() != '"') throw bad();
                    String k = string(); ws(); if (peek() != ':') throw bad(); i++;
                    m.put(k, value(depth + 1)); ws(); char d = peek(); i++; if (d == '}') return m; if (d != ',') throw bad();
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return BAD; }
            return number();
        }
        Number number() {
            int st = i;
            if (peek() == '-') i++;
            int d0 = i; while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i == d0 || (s.charAt(d0) == '0' && i - d0 > 1)) throw bad();
            boolean integral = true;
            if (i < s.length() && s.charAt(i) == '.') { integral = false; i++; int f = i; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; if (i == f) throw bad(); }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                integral = false; i++; if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                int f = i; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; if (i == f) throw bad();
            }
            String t = s.substring(st, i);
            if (integral) { try { return Long.parseLong(t); } catch (NumberFormatException e) { /* quá lớn: dùng double */ } }
            return Double.parseDouble(t);
        }
        String string() {
            i++; StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw bad();
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 0x20) throw bad();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw bad();
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break; case '\\': b.append('\\'); break; case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break; case 't': b.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw bad();
                        int v = 0;
                        for (int k = 0; k < 4; k++) { int h = Character.digit(s.charAt(i + k), 16); if (h < 0) throw bad(); v = v * 16 + h; }
                        i += 4; b.append((char) v); break;
                    }
                    default: throw bad();
                }
            }
        }
    }
}
