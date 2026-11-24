package com.grape.play;

/** Khớp thiết bị đầu ra đã lưu với danh sách đang kết nối (thuần Java để test). Khoá = "loại|địa chỉ|tên". */
public final class DeviceMatch {
    private DeviceMatch() {}

    public static final class Dev {
        public final int type; public final String addr, name;
        public Dev(int type, String addr, String name) { this.type = type; this.addr = addr == null ? "" : addr; this.name = name == null ? "" : name; }
    }

    public static String keyOf(Dev d) { return d.type + "|" + d.addr + "|" + d.name; }
    public static int typeOf(String key) {
        try { int i = key.indexOf('|'); return Integer.parseInt(i < 0 ? key : key.substring(0, i)); } catch (Exception e) { return 0; }
    }
    private static String part(String key, int n) { String[] p = key.split("\\|", 3); return n < p.length ? p[n] : ""; }

    /** Chỉ số thiết bị khớp, -1 = không có (khi đó dùng mặc định hệ thống). Khớp đúng khoá → cùng loại + cùng tên duy nhất (địa chỉ đổi sau cắm lại) → khoá cũ chỉ có loại. */
    public static int match(String key, Dev[] list) {
        if (key == null || key.isEmpty()) return -1;
        for (int i = 0; i < list.length; i++) if (keyOf(list[i]).equals(key)) return i;
        int t = typeOf(key); String name = part(key, 2), addr = part(key, 1);
        int found = -1, count = 0;
        for (int i = 0; i < list.length; i++) {
            if (list[i].type != t) continue;
            if (!name.isEmpty()) { if (list[i].name.equals(name)) { found = i; count++; } }
            else if (addr.isEmpty()) { if (found < 0) found = i; count = 1; }
        }
        return count == 1 ? found : -1;
    }
}
