package com.grape.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.grape.play.BitPerfect.Route;

/**
 * Bit-perfect Device Catalog (2.9.9) — danh mục thiết bị đã nghiên cứu, có bằng chứng, dùng offline. Thuần Java (test được bằng javac).
 *
 * Khác với {@link DeviceRegistry#builtin()} (nhỏ, phục vụ đánh giá phiên phát): danh mục này là tài liệu tra cứu cho người dùng, mỗi thiết bị có nhiều
 * CẤU HÌNH theo phương thức kết nối (UPnP, USB, HDMI, S/PDIF…), mỗi cấu hình có mức xác minh và bằng chứng riêng. Không bao giờ gộp thành một cờ "Verified" chung.
 *
 * Bốn mức xác minh (xem BITPERFECT.md): 1 = đã nhận diện model; 2 = khả năng định dạng/giao thức được tài liệu hoá; 3 = cấu hình có thể bit-perfect được xác minh
 * (DSP/âm lượng/resampling/DSD đã biết rõ); 4 = đo được dữ liệu tại đầu nhận (end-to-end). {@link #validate} hạ mức mọi bản ghi tuyên bố vượt bằng chứng.
 * Dữ liệu người dùng xác nhận (thiết bị, kết quả thử) không bao giờ tự nâng quá mức 2.
 */
public final class DeviceCatalog {
    private DeviceCatalog() {}

    public enum Status { CONFIG_VERIFIED, CAPABILITY_VERIFIED, CANDIDATE, NOT_BIT_PERFECT }
    public enum Dsd { NATIVE, DOP, DSD_TO_PCM, NONE, UNDOCUMENTED }
    public enum Method { DOCUMENTATION, USER_TEST, MEASUREMENT }

    public static final String UNDOCUMENTED = "UNDOCUMENTED";
    /** Phiên bản schema tệp danh mục mà mã này đọc được. */
    public static final String SCHEMA = "1";

    public static final class Config {
        public Route conn = Route.ROUTE_UNKNOWN;
        public String protocol = "", maxPcm = "", dsdMax = "", dspBypass = UNDOCUMENTED, volume = UNDOCUMENTED, resampling = UNDOCUMENTED;
        public String conditions = "", limitations = "", evidenceNote = "", evidenceDate = "";
        public String[] formats = new String[0], evidenceUrls = new String[0], evidenceSources = new String[0];
        public int[] rates = new int[0], bits = new int[0];
        public Dsd dsd = Dsd.UNDOCUMENTED;
        public int level = 1;
        public Status status = Status.CANDIDATE;
        public Method method = Method.DOCUMENTATION;
        public boolean userConfirmationRequired = true;
        /** Ghi chú nếu {@link #validate} đã hạ mức/trạng thái của bản ghi này. */
        public String downgraded = "";
        public boolean nativeDsd() { return dsd == Dsd.NATIVE; }
        public boolean dop() { return dsd == Dsd.DOP; }
        public int maxRate() { int m = 0; for (int r : rates) m = Math.max(m, r); return m != 0 ? m : parseMaxRate(maxPcm); }
        public int maxBits() { int m = 0; for (int b : bits) m = Math.max(m, b); return m != 0 ? m : parseMaxBits(maxPcm); }
        public boolean hasDsd() { return dsd == Dsd.NATIVE || dsd == Dsd.DOP || dsd == Dsd.DSD_TO_PCM; }
    }

    public static final class Device {
        public String id = "", manufacturer = "", model = "", category = "", description = "", mfrProductId = "", lastReviewed = "";
        public int profileVersion = 1;
        public String[] aliases = new String[0], upnpNames = new String[0], hwIds = new String[0];   // hwIds: "vid:pid" hex thường
        public final List<Config> configs = new ArrayList<>();
        public String label() { return manufacturer + " " + model; }
        public Config config(Route r) { for (Config c : configs) if (c.conn == r) return c; return null; }
        public List<Config> configsFor(Route r) { List<Config> l = new ArrayList<>(); for (Config c : configs) if (c.conn == r) l.add(c); return l; }
        /** Trạng thái tốt nhất của thiết bị (chỉ để xếp hạng/hiển thị huy hiệu tổng; chi tiết luôn theo từng cấu hình). */
        public Status best() {
            Status b = null;
            for (Config c : configs) if (b == null || rank(c.status) > rank(b)) b = c.status;
            return b == null ? Status.CANDIDATE : b;
        }
        public int maxLevel() { int m = 1; for (Config c : configs) m = Math.max(m, c.level); return m; }
    }

    static int rank(Status s) { switch (s) { case CONFIG_VERIFIED: return 4; case CAPABILITY_VERIFIED: return 3; case CANDIDATE: return 2; default: return 1; } }

    static int parseMaxRate(String s) {          // "24/192" hoặc "32/768" (bit/kHz) → Hz
        int i = s == null ? -1 : s.indexOf('/'); if (i < 0) return 0;
        try { return (int) Math.round(Double.parseDouble(s.substring(i + 1).replaceAll("[^0-9.]", "")) * 1000); } catch (RuntimeException e) { return 0; }
    }
    static int parseMaxBits(String s) {
        int i = s == null ? -1 : s.indexOf('/'); if (i < 0) return 0;
        try { return Integer.parseInt(s.substring(0, i).replaceAll("[^0-9]", "")); } catch (RuntimeException e) { return 0; }
    }

    // ------------------------------------------------------------------ chính sách bằng chứng
    /**
     * Áp chính sách: không cấp mức/trạng thái vượt bằng chứng. Trả về danh sách vấn đề (rỗng = hợp lệ); bản ghi vi phạm bị HẠ mức tại chỗ, không bị loại.
     * Quy tắc: không có bằng chứng (URL hoặc nguồn do người dùng cung cấp) ⇒ tối đa mức 1/CANDIDATE; kết quả do người dùng thử ⇒ tối đa mức 2;
     * CONFIG_VERIFIED cần mức ≥3 và DSP/âm lượng/resampling đã được tài liệu hoá; mức 4 cần phương pháp MEASUREMENT có nguồn.
     */
    public static List<String> validate(Device d) {
        List<String> p = new ArrayList<>();
        if (d.id.isEmpty() || d.manufacturer.isEmpty() || d.model.isEmpty()) p.add(d.id + ": thiếu id/hãng/model");
        if (d.configs.isEmpty()) p.add(d.id + ": không có cấu hình kết nối");
        for (Config c : d.configs) {
            String w = d.id + "/" + c.conn + "/" + c.protocol;
            boolean hasEv = c.evidenceUrls.length > 0 || c.evidenceSources.length > 0;
            if (c.level < 1) c.level = 1;
            if (c.level > 4) c.level = 4;
            if (c.level >= 2 && !hasEv) { down(c, 1, Status.CANDIDATE, "không có bằng chứng"); p.add(w + ": mức ≥2 nhưng không có bằng chứng"); }
            if (c.level >= 2 && c.evidenceDate.isEmpty()) p.add(w + ": thiếu ngày bằng chứng");
            if (c.method == Method.USER_TEST && c.level > 2) { down(c, 2, c.status, "kết quả do người dùng báo chỉ đạt mức 2"); p.add(w + ": USER_TEST không được vượt mức 2"); }
            if (c.level == 4 && !(c.method == Method.MEASUREMENT && c.evidenceUrls.length > 0)) { down(c, 3, c.status, "mức 4 cần phép đo có nguồn"); p.add(w + ": mức 4 không có phép đo"); }
            boolean documented = !c.dspBypass.equals(UNDOCUMENTED) && !c.volume.equals(UNDOCUMENTED) && !c.resampling.equals(UNDOCUMENTED) && !c.conditions.isEmpty() && c.formats.length > 0;
            if (c.level == 3 && !documented) { down(c, 2, c.status, "mức 3 cần DSP/âm lượng/resampling và điều kiện được tài liệu hoá"); p.add(w + ": mức 3 thiếu điều kiện"); }
            if (c.status == Status.CONFIG_VERIFIED && c.level < 3) { down(c, c.level, c.level >= 2 ? Status.CAPABILITY_VERIFIED : Status.CANDIDATE, "CONFIG_VERIFIED cần mức ≥3"); p.add(w + ": CONFIG_VERIFIED với mức <3"); }
            if (c.status == Status.CAPABILITY_VERIFIED && c.level < 2) { down(c, c.level, Status.CANDIDATE, "CAPABILITY_VERIFIED cần mức ≥2"); p.add(w + ": CAPABILITY_VERIFIED với mức <2"); }
            if (c.status == Status.NOT_BIT_PERFECT && !hasEv) { down(c, c.level, Status.CANDIDATE, "NOT_BIT_PERFECT cần bằng chứng khẳng định"); p.add(w + ": NOT_BIT_PERFECT không có bằng chứng"); }
            if (c.dsd == Dsd.DSD_TO_PCM && c.status == Status.CONFIG_VERIFIED) { down(c, c.level, Status.NOT_BIT_PERFECT, "DSD→PCM không bảo toàn dữ liệu DSD"); p.add(w + ": DSD→PCM không thể CONFIG_VERIFIED"); }
            if (c.conn == Route.BLUETOOTH && c.status == Status.CONFIG_VERIFIED) { down(c, c.level, Status.CANDIDATE, "Bluetooth không mặc nhiên bit-perfect"); p.add(w + ": Bluetooth CONFIG_VERIFIED"); }
        }
        return p;
    }
    private static void down(Config c, int level, Status st, String why) { c.level = Math.min(c.level, level); c.status = st; c.downgraded = why; }

    // ------------------------------------------------------------------ tải / lưu (TSV: dòng D = thiết bị, dòng C = cấu hình)
    /**
     * Định dạng: dòng đầu "GRAPE-CATALOG\t<schema>\t<catalogVersion>\t<researchDate>". Trường D: id, hãng, model, bí danh(|), loại, mô tả, mã sản phẩm, tên UPnP(|), VID:PID(|), ngày xem xét, phiên bản hồ sơ.
     * Trường C: id thiết bị, kết nối, giao thức, định dạng(|), PCM tối đa, tần số(|), độ sâu bit(|), DSD, DSD tối đa, bypass DSP, âm lượng, resampling, mức, trạng thái,
     * phương pháp, điều kiện, giới hạn, nguồn(|), URL(|), ghi chú bằng chứng, ngày bằng chứng, cần người dùng xác nhận (Y/N).
     */
    public static final class Catalog {
        public String schema = "", version = "", researched = "";
        public final List<Device> devices = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
        public Device byId(String id) { for (Device d : devices) if (d.id.equals(id)) return d; return null; }
    }

    private static String[] split(String s, char sep) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        List<String> l = new ArrayList<>();
        for (String t : s.split(java.util.regex.Pattern.quote(String.valueOf(sep)))) if (!t.trim().isEmpty()) l.add(t.trim());
        return l.toArray(new String[0]);
    }
    private static int[] ints(String s) {
        String[] a = split(s, '|'); List<Integer> l = new ArrayList<>();
        for (String t : a) { try { l.add(Integer.parseInt(t.trim())); } catch (NumberFormatException ignored) {} }
        int[] r = new int[l.size()]; for (int i = 0; i < r.length; i++) r[i] = l.get(i); return r;
    }
    private static String f(String[] a, int i) { return i < a.length ? a[i].trim() : ""; }
    private static String undoc(String s) { return s.isEmpty() ? UNDOCUMENTED : s; }
    static Route route(String s) { try { return Route.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Route.OTHER; } }
    static Dsd dsd(String s) { try { return Dsd.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Dsd.UNDOCUMENTED; } }
    static Status status(String s) { try { return Status.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Status.CANDIDATE; } }
    static Method method(String s) { try { return Method.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Method.DOCUMENTATION; } }

    /** Không bao giờ ném lỗi: dòng hỏng được ghi vào {@link Catalog#problems} và bỏ qua; trùng id bị loại (giữ bản đầu). Mọi thiết bị đều qua {@link #validate}. */
    public static Catalog parse(String text) {
        Catalog cat = new Catalog();
        if (text == null) return cat;
        Device cur = null;
        int n = 0;
        for (String line : text.split("\n")) {
            n++;
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\t", -1);
            try {
                switch (a[0]) {
                    case "GRAPE-CATALOG": cat.schema = f(a, 1); cat.version = f(a, 2); cat.researched = f(a, 3); break;
                    case "D": {
                        Device d = new Device();
                        d.id = f(a, 1); d.manufacturer = f(a, 2); d.model = f(a, 3); d.aliases = split(f(a, 4), '|'); d.category = f(a, 5); d.description = f(a, 6);
                        d.mfrProductId = f(a, 7); d.upnpNames = split(f(a, 8), '|'); d.hwIds = lowerAll(split(f(a, 9), '|')); d.lastReviewed = f(a, 10);
                        try { d.profileVersion = Integer.parseInt(f(a, 11)); } catch (NumberFormatException e) { d.profileVersion = 1; }
                        if (d.id.isEmpty() || cat.byId(d.id) != null) { cat.problems.add("dòng " + n + ": id rỗng hoặc trùng (" + d.id + ")"); cur = null; break; }
                        cat.devices.add(d); cur = d; break;
                    }
                    case "C": {
                        Device d = cat.byId(f(a, 1));
                        if (d == null) { cat.problems.add("dòng " + n + ": cấu hình cho thiết bị không tồn tại (" + f(a, 1) + ")"); break; }
                        Config c = new Config();
                        c.conn = route(f(a, 2)); c.protocol = f(a, 3); c.formats = split(f(a, 4), '|'); c.maxPcm = f(a, 5); c.rates = ints(f(a, 6)); c.bits = ints(f(a, 7));
                        c.dsd = dsd(f(a, 8)); c.dsdMax = f(a, 9); c.dspBypass = undoc(f(a, 10)); c.volume = undoc(f(a, 11)); c.resampling = undoc(f(a, 12));
                        try { c.level = Integer.parseInt(f(a, 13)); } catch (NumberFormatException e) { c.level = 1; }
                        c.status = status(f(a, 14)); c.method = method(f(a, 15)); c.conditions = f(a, 16); c.limitations = f(a, 17);
                        c.evidenceSources = split(f(a, 18), '|'); c.evidenceUrls = split(f(a, 19), '|'); c.evidenceNote = f(a, 20); c.evidenceDate = f(a, 21);
                        c.userConfirmationRequired = !f(a, 22).equalsIgnoreCase("N");
                        d.configs.add(c); break;
                    }
                    default: cat.problems.add("dòng " + n + ": loại dòng không hợp lệ"); break;
                }
            } catch (RuntimeException e) { cat.problems.add("dòng " + n + ": " + e.getClass().getSimpleName()); }
        }
        if (!cat.schema.equals(SCHEMA)) {          // thiếu tiêu đề hoặc schema lạ: không đoán, dùng danh mục rỗng (app vẫn chạy)
            cat.problems.add("schema không được hỗ trợ: '" + cat.schema + "' (cần " + SCHEMA + ")");
            cat.devices.clear(); return cat;
        }
        for (Device d : cat.devices) cat.problems.addAll(validate(d));
        return cat;
    }
    private static String[] lowerAll(String[] a) { for (int i = 0; i < a.length; i++) a[i] = a[i].toLowerCase(Locale.ROOT); return a; }

    // ------------------------------------------------------------------ nhận diện
    /** Metadata thiết bị đang khám phá (UPnP device description, USB AudioDeviceInfo…). Trường nào không biết thì để rỗng / -1. */
    public static final class Discovered {
        public String manufacturer = "", modelName = "", modelDescription = "", friendlyName = "", udn = "", mfrProductId = "";
        public int vid = -1, pid = -1;
        public Route conn = Route.ROUTE_UNKNOWN;
        public Discovered() {}
        public Discovered(String manufacturer, String modelName, String friendlyName, Route conn) { this.manufacturer = manufacturer; this.modelName = modelName; this.friendlyName = friendlyName; this.conn = conn; }
        public String hwId() { return vid >= 0 && pid >= 0 ? Integer.toHexString(vid) + ":" + Integer.toHexString(pid) : ""; }
    }

    public static final int NONE = 0, ALIAS = 1, MODEL = 2, HARDWARE = 3;
    public static final class CatMatch {
        public Device device; public int quality = NONE; public boolean ambiguous;
        public final List<Device> candidates = new ArrayList<>();
        /** Mọi cấu hình của model khớp (dùng để hiển thị: cấu hình đã xác minh và chưa xác minh); cấu hình của kết nối đang dùng nằm ở {@link #active}. */
        public final List<Config> active = new ArrayList<>();
        /** Chỉ nhận diện chắc chắn (VID:PID, hoặc hãng + tên model trùng khớp) mới không cần hỏi người dùng. Đây vẫn KHÔNG phải bằng chứng bit-perfect. */
        public boolean needsConfirmation() { return quality < MODEL || ambiguous; }
    }

    static String norm(String s) { return DeviceRegistry.norm(s); }
    static List<String> tokens(String s) {
        List<String> t = new ArrayList<>();
        if (s == null) return t;
        for (String x : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!x.isEmpty()) t.add(x);
        return t;
    }
    /** Dãy token của [needle] xuất hiện liên tiếp trong [hay] (ranh giới token: "wxc 50" không nằm trong "wxc 500"). */
    static boolean containsTokens(List<String> hay, List<String> needle) {
        if (needle.isEmpty() || needle.size() > hay.size()) return false;
        for (int i = 0; i + needle.size() <= hay.size(); i++) if (hay.subList(i, i + needle.size()).equals(needle)) return true;
        return false;
    }
    private static boolean makerOk(Device d, Discovered x) {
        String a = norm(x.manufacturer), b = norm(d.manufacturer);
        if (a.length() < 3 || b.length() < 3) return false;      // tên hãng quá ngắn không đủ để so khớp tiền tố
        return a.equals(b) || a.startsWith(b) || b.startsWith(a);      // "Yamaha Corporation" ~ "Yamaha"
    }
    private static boolean modelEq(String a, String b) { return !norm(a).isEmpty() && norm(a).equals(norm(b)); }

    /**
     * Ưu tiên: (1) VID:PID chính xác; (2) hãng + tên model/mô tả/tên UPnP trùng khít; (3) bí danh hoặc tên thân thiện chứa model — chỉ là bằng chứng phụ, luôn cần người dùng xác nhận.
     * Cùng điểm cho ≥2 model khác nhau ⇒ ambiguous, không chọn. Tên giống nhau (WXC-50 / WXC-500) không bị gộp. Không dùng tên thân thiện nếu hãng không khớp
     * khi đã biết hãng; nếu không biết hãng thì chỉ đạt mức ALIAS.
     */
    public static CatMatch match(Discovered x, List<Device> all) {
        CatMatch m = new CatMatch();
        if (x == null) return m;
        int bestQ = NONE, bestLen = -1;
        List<Device> best = new ArrayList<>();
        for (Device d : all) {
            int q = NONE, len = 0;
            String hw = x.hwId();
            if (!hw.isEmpty()) for (String h : d.hwIds) if (h.equals(hw)) { q = HARDWARE; len = 100; }
            if (q == NONE && !x.mfrProductId.isEmpty() && !d.mfrProductId.isEmpty() && x.mfrProductId.equalsIgnoreCase(d.mfrProductId) && (x.manufacturer.isEmpty() || makerOk(d, x))) { q = MODEL; len = 90; }
            if (q == NONE && makerOk(d, x)) {
                boolean eq = modelEq(x.modelName, d.model);
                for (String u : d.upnpNames) if (modelEq(x.modelName, u)) eq = true;
                if (eq) { q = MODEL; len = norm(d.model).length(); }
            }
            if (q == NONE) {
                if (!x.manufacturer.isEmpty() && !makerOk(d, x)) continue;      // hãng đã biết nhưng khác ⇒ không thử đoán qua tên
                List<String> names = new ArrayList<>(); names.addAll(tokens(x.friendlyName)); List<String> desc = tokens(x.modelDescription);
                List<String> cand = new ArrayList<>(); cand.add(d.model); for (String al : d.aliases) cand.add(al);
                for (String c : cand) {
                    List<String> ct = tokens(c);
                    if (ct.isEmpty() || norm(c).length() < 4) continue;
                    if (containsTokens(names, ct) || containsTokens(desc, ct) || modelEq(x.modelName, c)) { q = ALIAS; len = Math.max(len, norm(c).length()); }
                }
            }
            if (q == NONE) continue;
            if (q > bestQ || (q == bestQ && len > bestLen)) { bestQ = q; bestLen = len; best.clear(); best.add(d); }
            else if (q == bestQ && len == bestLen) best.add(d);
        }
        if (best.isEmpty()) return m;
        m.quality = bestQ; m.candidates.addAll(best);
        // Một tên chứa mô hình dài hơn (vd. "WiiM Ultra Pro" chứa "WiiM Ultra"): nếu bản dài hơn cũng khớp thì len đã ưu tiên bản dài hơn; phần còn lại cần người dùng xác nhận (ALIAS).
        if (best.size() == 1) { m.device = best.get(0); if (x.conn != Route.ROUTE_UNKNOWN) m.active.addAll(m.device.configsFor(x.conn)); }
        else m.ambiguous = true;
        return m;
    }

    // ------------------------------------------------------------------ tìm kiếm / bộ lọc
    public static final class Query {
        public String text = "", category = "";
        public Route conn = null;                // null = mọi kết nối
        public int minPcmRate = 0;               // Hz; 0 = không lọc
        public int minPcmBits = 0;
        public Dsd dsd = null;                   // null = không lọc; NONE = không hỗ trợ DSD
        public boolean anyDsd = false;           // true = có bất kỳ kiểu DSD nào
        public int minLevel = 0;
        public Status status = null;
    }

    private static boolean configPasses(Config c, Query q) {
        if (q.conn != null && c.conn != q.conn) return false;
        if (q.minPcmRate > 0 && c.maxRate() < q.minPcmRate) return false;
        if (q.minPcmBits > 0 && c.maxBits() < q.minPcmBits) return false;
        if (q.anyDsd && !c.hasDsd()) return false;
        if (q.dsd != null && c.dsd != q.dsd) return false;
        if (q.minLevel > 0 && c.level < q.minLevel) return false;
        if (q.status != null && c.status != q.status) return false;
        return true;
    }
    /** Tất cả điều kiện lọc cấu hình phải đồng thời đúng trên CÙNG MỘT cấu hình (không ghép đặc tính của hai kết nối khác nhau). */
    public static List<Device> search(List<Device> all, Query q) {
        List<Device> out = new ArrayList<>();
        List<String> words = tokens(q.text);
        boolean cfgFilter = q.conn != null || q.minPcmRate > 0 || q.minPcmBits > 0 || q.anyDsd || q.dsd != null || q.minLevel > 0 || q.status != null;
        for (Device d : all) {
            if (!q.category.isEmpty() && !d.category.equalsIgnoreCase(q.category)) continue;
            if (!words.isEmpty()) {
                StringBuilder hay = new StringBuilder(" ");
                hay.append(d.manufacturer).append(' ').append(d.model).append(' ').append(String.join(" ", d.aliases)).append(' ').append(d.mfrProductId);
                String h = " " + String.join(" ", tokens(hay.toString())) + " ";
                String hn = norm(hay.toString());
                boolean all2 = true;
                for (String w : words) if (!h.contains(" " + w) && !hn.contains(norm(w))) { all2 = false; break; }
                if (!all2) continue;
            }
            if (cfgFilter) { boolean ok = false; for (Config c : d.configs) if (configPasses(c, q)) { ok = true; break; } if (!ok) continue; }
            out.add(d);
        }
        Collections.sort(out, (a, b) -> {
            int r = Integer.compare(rank(b.best()), rank(a.best()));
            if (r != 0) return r;
            r = a.manufacturer.compareToIgnoreCase(b.manufacturer);
            return r != 0 ? r : a.model.compareToIgnoreCase(b.model);
        });
        return out;
    }

    public static Set<String> categories(List<Device> all) { Set<String> s = new LinkedHashSet<>(); for (Device d : all) if (!d.category.isEmpty()) s.add(d.category); return s; }
    public static Set<Route> connections(List<Device> all) { Set<Route> s = new LinkedHashSet<>(); for (Device d : all) for (Config c : d.configs) s.add(c.conn); return s; }
}
