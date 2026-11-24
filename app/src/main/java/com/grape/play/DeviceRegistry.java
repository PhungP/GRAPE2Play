package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.grape.play.BitPerfect.DevLevel;
import com.grape.play.BitPerfect.Route;

/**
 * Bit-perfect Device Registry — thuần Java (test được bằng javac), local-first, không mạng.
 * Ba lớp dữ liệu: (A) danh mục tích hợp {@link #builtin()}, (B) hồ sơ người dùng {@link Store}, (C) danh mục cộng đồng: CHƯA triển khai (chỉ chừa chỗ: {@link Entry#source}).
 *
 * Nguyên tắc: khớp theo MODEL + PHƯƠNG THỨC KẾT NỐI, không suy rộng giữa các phương thức; cùng tên nhưng khác VID/PID thì không khớp;
 * dữ liệu người dùng khai báo không bao giờ ghi đè thông số trong danh mục và không tự cấp mức xác minh; người dùng xác nhận thiết bị
 * chỉ liên kết hồ sơ với bản ghi, KHÔNG phải bằng chứng dữ liệu được bảo toàn (việc đó do BitPerfect.evaluate quyết định theo từng phiên phát).
 */
public final class DeviceRegistry {
    private DeviceRegistry() {}

    // ------------------------------------------------------------------ (A) danh mục tích hợp
    public static final class Entry {
        public final String id, manufacturer, model, source, updated, dsd, exclusive, conditions;
        public final String[] nameKeys;        // chuỗi (đã chuẩn hoá) xuất hiện trong tên sản phẩm Android báo
        public final int vid, pid;             // -1 = không biết
        public final Route conn;
        public final int[] bits, rates;
        public final DevLevel level;           // REGISTRY_CONFIG hoặc REGISTRY_UNVERIFIED (chưa có căn cứ)
        public final boolean needsMaxVolume;
        Entry(String id, String manufacturer, String model, String[] nameKeys, int vid, int pid, Route conn, int[] bits, int[] rates, String dsd, String exclusive,
              String conditions, String source, DevLevel level, String updated, boolean needsMaxVolume) {
            this.id = id; this.manufacturer = manufacturer; this.model = model; this.nameKeys = nameKeys; this.vid = vid; this.pid = pid; this.conn = conn;
            this.bits = bits; this.rates = rates; this.dsd = dsd; this.exclusive = exclusive; this.conditions = conditions; this.source = source; this.level = level; this.updated = updated;
            this.needsMaxVolume = needsMaxVolume;
        }
        public String label() { return manufacturer + " " + model; }
    }

    /** Danh mục khởi tạo — CỐ Ý nhỏ: chỉ bản ghi có thông số công bố của nhà sản xuất. Mức REGISTRY_CONFIG là mức cấu hình, không phải kết quả đo. Cần mở rộng khi có căn cứ. */
    public static List<Entry> builtin() {
        List<Entry> l = new ArrayList<>();
        int[] usbRates = {44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000};
        String src = "Thông số nhà sản xuất (fiio.com/k9proess_parameters, tra 2026-11-24): USB tối đa 384 kHz/32-bit PCM, DSD256; quang tối đa 96 kHz/24-bit; đồng trục tối đa 192 kHz/24-bit. "
                + "Trang không liệt kê từng tần số trung gian: danh sách tần số là các tần số chuẩn ≤ mức tối đa (giả định). GRAPE2Play chưa tự kiểm thử thiết bị này.";
        l.add(new Entry("fiio-k9pro-ess/usb", "FiiO", "K9 Pro ESS", new String[]{"k9proess"}, -1, -1, Route.USB, new int[]{16, 24, 32}, usbRates,
                "DSD256 (qua USB; app chưa kiểm thử DoP/native với thiết bị này)", "chưa xác minh (Android 14+ có chế độ bit-perfect USB, app chưa dùng)",
                "Âm lượng hệ thống tối đa; không bật DSP/EQ; Android không được xác minh là không trộn/đổi tần số trong mixer", src, DevLevel.REGISTRY_CONFIG, "2026-11-24", true));
        l.add(new Entry("fiio-k9pro-ess/spdif-optical", "FiiO", "K9 Pro ESS", new String[]{"k9proess"}, -1, -1, Route.SPDIF, new int[]{16, 24}, new int[]{44100, 48000, 88200, 96000},
                "không", "không áp dụng", "Chưa có căn cứ cho đường Android → S/PDIF (Android không phân biệt quang/đồng trục; TV box có thể đổi tần số)",
                "Thông số nhà sản xuất cho cổng quang (tối đa 96 kHz/24-bit), nhưng CHƯA có căn cứ cho đường Android → S/PDIF.", DevLevel.REGISTRY_UNVERIFIED, "2026-11-24", true));
        l.add(new Entry("fiio-k9pro-ess/bluetooth", "FiiO", "K9 Pro ESS", new String[]{"k9proess"}, -1, -1, Route.BLUETOOTH, new int[0], new int[0],
                "không", "không áp dụng", "Bluetooth dùng codec nén (SBC/AAC/aptX/LDAC): không mặc nhiên là bit-perfect",
                "Bluetooth không mặc nhiên được coi là bit-perfect.", DevLevel.REGISTRY_UNVERIFIED, "2026-11-24", true));
        return l;
    }

    public static Entry byId(List<Entry> reg, String id) { for (Entry e : reg) if (e.id.equals(id)) return e; return null; }

    // ------------------------------------------------------------------ nhận diện + đối chiếu
    public static final class Detected {
        public String name = "", manufacturer = "", address = "";
        public int vid = -1, pid = -1;
        public Route conn = Route.ROUTE_UNKNOWN;
        /** true = Android xác định đúng thiết bị này đang định tuyến; false = chỉ là thiết bị người dùng chọn / đang kết nối. */
        public boolean activeKnown;
        public Detected() {}
        public Detected(String name, Route conn, int vid, int pid, boolean activeKnown) { this.name = name; this.conn = conn; this.vid = vid; this.pid = pid; this.activeKnown = activeKnown; }
    }

    public static final int NO_MATCH = 0, NAME_ONLY = 1, EXACT_ID = 2;
    public static final class Match {
        public Entry entry; public int quality = NO_MATCH;
        /** Cùng model nhưng phương thức kết nối khác (chỉ để thông tin; KHÔNG áp dụng kết quả sang phương thức đang dùng). */
        public final List<Entry> otherConnections = new ArrayList<>();
    }

    public static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c);
        return b.toString();
    }
    private static int nameScore(Detected d, Entry e) {          // độ dài khoá dài nhất khớp, 0 = không khớp
        String n = norm(d.name); int best = 0;
        for (String k : e.nameKeys) { String nk = norm(k); if (nk.length() >= 4 && n.contains(nk)) best = Math.max(best, nk.length()); }
        return best;
    }
    private static boolean idConflict(Detected d, Entry e) { return e.vid >= 0 && d.vid >= 0 && (e.vid != d.vid || e.pid != d.pid); }
    private static boolean idExact(Detected d, Entry e) { return e.vid >= 0 && d.vid == e.vid && d.pid == e.pid; }

    public static Match match(Detected d, List<Entry> reg) {
        Match m = new Match();
        if (d == null || d.conn == Route.ROUTE_UNKNOWN) return m;
        int bestQ = 0, bestScore = 0; boolean ambiguous = false; Entry best = null;
        for (Entry e : reg) {
            if (idConflict(d, e)) continue;                                   // cùng tên, khác VID/PID → không khớp
            int sc = nameScore(d, e); boolean ex = idExact(d, e);
            if (sc == 0 && !ex) continue;
            if (e.conn != d.conn) { m.otherConnections.add(e); continue; }    // khác phương thức kết nối: không suy rộng
            int q = ex ? EXACT_ID : NAME_ONLY;
            if (q > bestQ || (q == bestQ && sc > bestScore)) { bestQ = q; bestScore = sc; best = e; ambiguous = false; }
            else if (q == bestQ && sc == bestScore && best != null && best != e) ambiguous = true;
        }
        if (best != null && !ambiguous) { m.entry = best; m.quality = bestQ; }
        return m;
    }

    // ------------------------------------------------------------------ (B) hồ sơ người dùng
    public static final String SRC_DETECTED = "DETECTED", SRC_USER = "USER";
    public static final class Profile {
        public String key = "", source = SRC_DETECTED, entryId = "", manufacturer = "", model = "", name = "", notes = "";
        public Route conn = Route.ROUTE_UNKNOWN; public int vid = -1, pid = -1; public long confirmedAt;
        public int[] bits = new int[0], rates = new int[0];   // do người dùng khai báo; chỉ để hiển thị, KHÔNG dùng để cấp xác minh
        public String label() { String s = (manufacturer + " " + model).trim(); return s.isEmpty() ? name : s; }
    }
    public static final class Store {
        public final List<Profile> profiles = new ArrayList<>();
        public final Set<String> rejected = new LinkedHashSet<>();   // "key#entryId": người dùng nói "không đúng thiết bị"
        public final Set<String> dismissed = new LinkedHashSet<>();  // key thiết bị lạ người dùng chọn "để sau"
        public Profile find(String key) { for (Profile p : profiles) if (p.key.equals(key)) return p; return null; }
        public boolean remove(String key) { boolean r = profiles.removeIf(p -> p.key.equals(key)); dismissed.remove(key); rejected.removeIf(x -> x.startsWith(key + "#")); return r; }
        public void put(Profile p) { profiles.removeIf(x -> x.key.equals(p.key)); profiles.add(p); dismissed.remove(p.key); }
    }

    public static String keyOf(Detected d) {
        return d.conn.name() + "|" + (d.vid >= 0 ? Integer.toHexString(d.vid) + ":" + Integer.toHexString(d.pid) : "-") + "|" + norm(d.name);
    }

    /** Người dùng xác nhận "Đúng thiết bị": lưu hồ sơ liên kết bản ghi (nếu có) với đúng phương thức kết nối đã phát hiện. */
    public static Profile confirm(Store st, Detected d, Match m, long now) {
        Profile p = new Profile();
        p.key = keyOf(d); p.source = SRC_DETECTED; p.conn = d.conn; p.vid = d.vid; p.pid = d.pid; p.name = d.name; p.confirmedAt = now;
        if (m != null && m.entry != null) { p.entryId = m.entry.id; p.manufacturer = m.entry.manufacturer; p.model = m.entry.model; }
        st.rejected.removeIf(x -> x.startsWith(p.key + "#"));
        st.put(p); return p;
    }
    /** Người dùng nói "Không đúng thiết bị": không đăng ký, không gán thông tin model khác; nhớ để không hỏi lại cùng bản ghi. */
    public static void reject(Store st, Detected d, Match m) { if (m != null && m.entry != null) st.rejected.add(keyOf(d) + "#" + m.entry.id); }
    /** Người dùng khai báo thiết bị mới (hãng/model/thông số). Không liên kết bản ghi danh mục ⇒ không bao giờ có mức xác minh. */
    public static Profile declare(Store st, Detected d, String manufacturer, String model, String notes, int[] bits, int[] rates, long now) {
        Profile p = new Profile();
        p.key = keyOf(d); p.source = SRC_USER; p.conn = d.conn; p.vid = d.vid; p.pid = d.pid; p.name = d.name; p.confirmedAt = now;
        p.manufacturer = manufacturer == null ? "" : manufacturer.trim(); p.model = model == null ? "" : model.trim(); p.notes = notes == null ? "" : notes.trim();
        p.bits = bits == null ? new int[0] : bits; p.rates = rates == null ? new int[0] : rates;
        st.put(p); return p;
    }
    public static void dismiss(Store st, Detected d) { st.dismissed.add(keyOf(d)); }

    // ------------------------------------------------------------------ xác nhận: khi nào hỏi
    public enum Ask { NONE, REGISTRY_MATCH, UNKNOWN_DEVICE }

    /** Chỉ hỏi về thiết bị số/ngoài (USB, HDMI, S/PDIF, Bluetooth); không hỏi loa, mặc định hệ thống, UPnP/Cast; không hỏi lại thiết bị đã lưu/đã từ chối/đã bỏ qua. */
    public static Ask decide(Detected d, Match m, Store st) {
        if (d == null || d.name == null || d.name.trim().isEmpty()) return Ask.NONE;
        switch (d.conn) { case USB: case HDMI: case SPDIF: case BLUETOOTH: break; default: return Ask.NONE; }
        String key = keyOf(d);
        Profile p = st.find(key);
        if (p != null && p.conn == d.conn) {
            // hồ sơ đã có: chỉ hỏi lại khi bản ghi đã liên kết không còn tồn tại hoặc đổi phương thức (xử lý bằng resolve → NONE) — ở đây giữ nguyên, không hỏi lại
            return Ask.NONE;
        }
        if (m != null && m.entry != null) return st.rejected.contains(key + "#" + m.entry.id) ? Ask.NONE : Ask.REGISTRY_MATCH;
        return st.dismissed.contains(key) ? Ask.NONE : Ask.UNKNOWN_DEVICE;
    }

    // ------------------------------------------------------------------ áp hồ sơ vào đánh giá
    public static final class Resolved { public DevLevel level = DevLevel.NONE; public Profile profile; public Entry entry; }

    /** Trạng thái xác minh của thiết bị đang dùng. Chỉ bản ghi danh mục cấp mức; hồ sơ tự khai báo tối đa USER_DECLARED. */
    public static Resolved resolve(Detected d, List<Entry> reg, Store st) {
        Resolved r = new Resolved();
        if (d == null || d.conn == Route.ROUTE_UNKNOWN) return r;
        Profile p = st.find(keyOf(d));
        if (p == null || p.conn != d.conn) return r;
        r.profile = p;
        if (p.entryId.isEmpty()) { r.level = DevLevel.USER_DECLARED; return r; }
        Entry e = byId(reg, p.entryId);
        if (e == null || e.conn != d.conn || idConflict(d, e)) { r.level = DevLevel.USER_DECLARED; return r; }   // bản ghi biến mất/đổi: cần xác nhận lại, không giữ mức cũ
        r.entry = e; r.level = e.level; return r;
    }

    /** Điền các trường đối chiếu vào Input (chỉ khi thiết bị đang dùng đã được Android xác định là đang định tuyến). */
    public static void apply(BitPerfect.Input in, Detected d, List<Entry> reg, Store st) {
        Resolved r = resolve(d, reg, st);
        in.devLevel = r.level;
        in.devConn = r.profile == null ? Route.ROUTE_UNKNOWN : r.profile.conn;
        in.devIsActive = d != null && d.activeKnown && r.profile != null;
        if (r.entry != null) { in.devRates = r.entry.rates; in.devBits = r.entry.bits; in.devNeedsMaxVolume = r.entry.needsMaxVolume; }
    }

    // ------------------------------------------------------------------ lưu / tải (định dạng dòng, thuần Java; hỏng thì bỏ qua dòng hỏng)
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", ""); }
    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n); } else b.append(c);
        }
        return b.toString();
    }
    private static String csv(int[] a) { StringBuilder b = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); } return b.toString(); }
    private static int[] parseCsv(String s) {
        if (s.isEmpty()) return new int[0];
        List<Integer> l = new ArrayList<>();
        for (String t : s.split(",")) { try { l.add(Integer.parseInt(t.trim())); } catch (NumberFormatException ignored) {} }
        int[] r = new int[l.size()]; for (int i = 0; i < r.length; i++) r[i] = l.get(i); return r;
    }
    public static String serialize(Store st) {
        StringBuilder b = new StringBuilder("GRAPE-DEVICES\t1\n");
        for (Profile p : st.profiles)
            b.append("P\t").append(esc(p.key)).append('\t').append(p.source).append('\t').append(esc(p.entryId)).append('\t').append(esc(p.manufacturer)).append('\t').append(esc(p.model)).append('\t')
                    .append(esc(p.name)).append('\t').append(p.conn.name()).append('\t').append(p.vid).append('\t').append(p.pid).append('\t').append(esc(p.notes)).append('\t')
                    .append(p.confirmedAt).append('\t').append(csv(p.bits)).append('\t').append(csv(p.rates)).append('\n');
        for (String r : st.rejected) b.append("R\t").append(esc(r)).append('\n');
        for (String r : st.dismissed) b.append("D\t").append(esc(r)).append('\n');
        return b.toString();
    }
    /** Trả về Store (không bao giờ null); dòng hỏng bị bỏ qua. */
    public static Store parse(String text) {
        Store st = new Store();
        if (text == null || !text.startsWith("GRAPE-DEVICES\t")) return st;
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            try {
                if (f[0].equals("P") && f.length >= 13) {
                    Profile p = new Profile();
                    p.key = unesc(f[1]); p.source = f[2].equals(SRC_USER) ? SRC_USER : SRC_DETECTED; p.entryId = unesc(f[3]); p.manufacturer = unesc(f[4]); p.model = unesc(f[5]); p.name = unesc(f[6]);
                    p.conn = Route.valueOf(f[7]); p.vid = Integer.parseInt(f[8]); p.pid = Integer.parseInt(f[9]); p.notes = unesc(f[10]); p.confirmedAt = Long.parseLong(f[11]);
                    p.bits = parseCsv(f[12]); p.rates = f.length > 13 ? parseCsv(f[13]) : new int[0];
                    if (!p.key.isEmpty()) st.profiles.removeIf(x -> x.key.equals(p.key)); if (!p.key.isEmpty()) st.profiles.add(p);
                } else if (f[0].equals("R") && f.length >= 2 && !f[1].isEmpty()) st.rejected.add(unesc(f[1]));
                else if (f[0].equals("D") && f.length >= 2 && !f[1].isEmpty()) st.dismissed.add(unesc(f[1]));
            } catch (RuntimeException ignored) { /* dòng hỏng: bỏ qua */ }
        }
        return st;
    }
}
