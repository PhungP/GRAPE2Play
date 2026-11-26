package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.grape.play.BitPerfect.Route;
import com.grape.play.DeviceCatalog.Dsd;

/**
 * Nguồn xác minh (Verification Source) và Độ chắc chắn kỹ thuật (Technical Confidence) — thuần Java, test được bằng javac.
 *
 * HAI THUỘC TÍNH ĐỘC LẬP: {@link Source} nói "bằng chứng đến từ đâu" (nhà sản xuất / người dùng / cộng đồng); {@link Confidence} nói "bằng chứng này đủ mạnh đến đâu
 * cho ĐÚNG thiết bị + kết nối + cấu hình". Không có quy tắc nào suy Confidence từ Source: Manufacturer Verified có thể chỉ Supported (tài liệu chỉ nêu khả năng),
 * User Verified có thể Confirmed (bắt được luồng số và so sánh bit), Community Verified chỉ đạt khi đủ báo cáo độc lập, nhất quán.
 * Xác nhận danh tính thiết bị KHÔNG phải xác minh khả năng phát; việc đó là {@link UserVerification}.
 */
public final class DeviceVerification {
    private DeviceVerification() {}

    public enum Source { MANUFACTURER, USER, COMMUNITY }
    /** Confirmed: đủ mạnh cho đúng thiết bị/kết nối/cấu hình; Supported: phù hợp nhưng chưa đủ cho toàn chuỗi; Inconclusive: chưa đủ hoặc mâu thuẫn; Unverified: chưa có bằng chứng đáng tin. */
    public enum Confidence { CONFIRMED, SUPPORTED, INCONCLUSIVE, UNVERIFIED }
    /** Cách người dùng kiểm tra. */
    public enum TestMethod { LISTENING, DEVICE_INDICATOR, DIGITAL_CAPTURE }
    public enum Result { PASS, FAIL, INCONCLUSIVE }

    // ------------------------------------------------------------------ bằng chứng
    public static final class Evidence {
        public Source source = Source.MANUFACTURER;
        public Confidence confidence = Confidence.UNVERIFIED;
        public String scope = "", firmware = "", date = "", reference = "", note = "", reporter = "";
        public Evidence() {}
        public Evidence(Source s, Confidence c, String scope, String firmware, String date, String reference, String note) {
            source = s; confidence = c; this.scope = scope; this.firmware = firmware; this.date = date; this.reference = reference; this.note = note;
        }
    }

    public static Source source(String s) { try { return Source.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Source.MANUFACTURER; } }
    public static Confidence confidence(String s) { try { return Confidence.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { return Confidence.UNVERIFIED; } }

    /** Đánh giá lại một bằng chứng trong danh mục tích hợp: không bao giờ nâng; trả về lý do nếu bị hạ. */
    public static String enforce(Evidence e, int cfgLevel) {
        if (e.source == Source.COMMUNITY) { e.confidence = Confidence.UNVERIFIED; return "báo cáo cộng đồng không được nhúng trực tiếp trong danh mục tích hợp"; }
        if (e.confidence == Confidence.CONFIRMED && cfgLevel < 3) { e.confidence = Confidence.SUPPORTED; return "CONFIRMED cần cấu hình mức ≥3"; }
        if ((e.confidence == Confidence.CONFIRMED || e.confidence == Confidence.SUPPORTED) && e.reference.isEmpty() && e.note.isEmpty()) { e.confidence = Confidence.UNVERIFIED; return "thiếu nguồn tham chiếu"; }
        return "";
    }

    // ------------------------------------------------------------------ xác minh do người dùng
    /**
     * Người dùng kiểm tra trực tiếp một cấu hình phát. Dùng để tạo bằng chứng nguồn USER; chỉ lưu cục bộ. KHÔNG tự đưa vào danh mục tích hợp.
     */
    public static final class UserVerification {
        public String profileKey = "", catalogId = "", manufacturer = "", model = "", firmware = "", protocol = "", software = "", notes = "";
        public Route conn = Route.ROUTE_UNKNOWN;
        public boolean dsd;                       // false = PCM
        public Dsd dsdMode = Dsd.UNDOCUMENTED;    // chỉ có nghĩa khi dsd = true
        public int rate, bits;
        public Result result = Result.INCONCLUSIVE;
        public TestMethod method = TestMethod.LISTENING;
        /** Điều kiện người dùng khai báo đã áp dụng khi thử. */
        public boolean dspOff, volumeFixed, resamplingOff;
        public long at;
        public String scope() {
            return connLabel(conn) + (protocol.isEmpty() ? "" : " / " + protocol) + " / " + (dsd ? "DSD " + dsdName(dsdMode) : "PCM " + (bits > 0 ? bits + "-bit " : "") + (rate > 0 ? rate + " Hz" : "")).trim();
        }
    }
    static String dsdName(Dsd d) { return d == Dsd.NATIVE ? "Native" : d == Dsd.DOP ? "DoP" : d == Dsd.DSD_TO_PCM ? "DSD→PCM" : "?"; }
    static String connLabel(Route r) { return r.name(); }

    /**
     * Độ chắc chắn từ bằng chứng người dùng. Nghe bằng tai không chứng minh dữ liệu được bảo toàn; chỉ báo trên thiết bị (vd. đèn tần số/DSD) cho Supported khi điều kiện được khai báo đầy đủ;
     * bắt luồng số và so sánh bit (kèm mô tả) mới đạt Confirmed. Thiếu thông tin cấu hình → Unverified.
     */
    public static Confidence userConfidence(UserVerification v) {
        if (v.conn == Route.ROUTE_UNKNOWN || (!v.dsd && v.rate <= 0) || (v.dsd && v.dsdMode == Dsd.UNDOCUMENTED)) return Confidence.UNVERIFIED;
        if (v.result == Result.INCONCLUSIVE) return Confidence.INCONCLUSIVE;
        boolean cond = v.dspOff && v.volumeFixed && v.resamplingOff;
        switch (v.method) {
            case DIGITAL_CAPTURE: return cond && v.notes.trim().length() >= 10 ? Confidence.CONFIRMED : Confidence.SUPPORTED;
            case DEVICE_INDICATOR: return cond ? Confidence.SUPPORTED : Confidence.INCONCLUSIVE;
            default: return Confidence.INCONCLUSIVE;
        }
    }
    public static Evidence toEvidence(UserVerification v) {
        Evidence e = new Evidence(Source.USER, userConfidence(v), v.scope(), v.firmware, v.at > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date(v.at)) : "", "",
                resultText(v.result) + " · " + methodText(v.method) + (v.software.isEmpty() ? "" : " · " + v.software) + (v.notes.isEmpty() ? "" : " · " + v.notes));
        return e;
    }

    static String resultText(Result r) { return r == Result.PASS ? "Passed: data unchanged as tested" : r == Result.FAIL ? "Failed: data altered" : "Inconclusive"; }
    static String methodText(TestMethod m) { return m == TestMethod.DIGITAL_CAPTURE ? "digital capture + bit compare" : m == TestMethod.DEVICE_INDICATOR ? "device indicator" : "listening only"; }

    // ------------------------------------------------------------------ cộng đồng
    public static final class CommunityReport {
        public String reporter = "", deviceId = "", firmware = "", protocol = "", evidence = "", date = "";
        public Route conn = Route.ROUTE_UNKNOWN;
        public boolean dsd; public Dsd dsdMode = Dsd.UNDOCUMENTED; public int rate, bits;
        public Result result = Result.INCONCLUSIVE; public TestMethod method = TestMethod.LISTENING;
        public String key() { return deviceId + "|" + conn + "|" + (dsd ? "DSD:" + dsdMode : "PCM"); }
    }
    public static final class Aggregate {
        public int independent, pass, fail, duplicates, rejected;
        public boolean conflict;
        public Confidence confidence = Confidence.UNVERIFIED;
        /** Chỉ true khi đạt tiêu chí của app: ≥3 báo cáo độc lập, nhất quán, có bằng chứng, không mâu thuẫn. */
        public boolean communityVerified() { return confidence == Confidence.SUPPORTED || confidence == Confidence.CONFIRMED; }
    }
    public static final int MIN_INDEPENDENT = 3, MIN_FOR_CONFIRMED = 5, MIN_CAPTURE_FOR_CONFIRMED = 3;

    static String normText(String s) { StringBuilder b = new StringBuilder(); for (char c : (s == null ? "" : s).toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c); return b.toString(); }

    /**
     * Gộp báo cáo của MỘT tổ hợp (cùng deviceId + kết nối + loại định dạng). Mỗi người báo cáo tính một lần (báo cáo mới nhất thay cũ); báo cáo không có bằng chứng bị loại;
     * hai người khác nhau nộp cùng một đoạn bằng chứng chỉ tính một (nghi trùng lặp/sao chép). PASS và FAIL cùng tồn tại ⇒ mâu thuẫn ⇒ Inconclusive, không nâng.
     */
    public static Aggregate aggregate(List<CommunityReport> reports) {
        Aggregate a = new Aggregate();
        Map<String, CommunityReport> latest = new LinkedHashMap<>();
        for (CommunityReport r : reports) {
            if (r.reporter.trim().isEmpty() || r.evidence.trim().length() < 5 || r.result == Result.INCONCLUSIVE) { a.rejected++; continue; }
            CommunityReport old = latest.get(r.reporter);
            if (old != null) a.duplicates++;
            if (old == null || r.date.compareTo(old.date) >= 0) latest.put(r.reporter, r);
        }
        Set<String> seenEv = new LinkedHashSet<>(); int capture = 0;
        for (CommunityReport r : latest.values()) {
            if (!seenEv.add(normText(r.evidence))) { a.duplicates++; continue; }
            a.independent++;
            if (r.result == Result.PASS) a.pass++; else a.fail++;
            if (r.method == TestMethod.DIGITAL_CAPTURE) capture++;
        }
        a.conflict = a.pass > 0 && a.fail > 0;
        if (a.conflict) a.confidence = Confidence.INCONCLUSIVE;
        else if (a.independent >= MIN_FOR_CONFIRMED && capture >= MIN_CAPTURE_FOR_CONFIRMED) a.confidence = Confidence.CONFIRMED;
        else if (a.independent >= MIN_INDEPENDENT) a.confidence = Confidence.SUPPORTED;
        else a.confidence = a.independent > 0 ? Confidence.INCONCLUSIVE : Confidence.UNVERIFIED;
        return a;
    }
    public static Map<String, List<CommunityReport>> groupByCombo(List<CommunityReport> all) {
        Map<String, List<CommunityReport>> m = new LinkedHashMap<>();
        for (CommunityReport r : all) m.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r);
        return m;
    }

    /**
     * Tệp báo cáo cộng đồng (TSV, offline): dòng đầu "GRAPE-COMMUNITY\t1"; mỗi dòng "R\treporter\tdeviceId\tconn\tprotocol\tfirmware\tDSD|PCM\tdsdMode\trate\tbits\tresult\tmethod\tdate\tevidence".
     * Dòng hỏng bị bỏ qua. Hiện không có báo cáo nào được phát hành cùng app (danh sách rỗng — không bịa dữ liệu).
     */
    public static List<CommunityReport> parseCommunity(String text) {
        List<CommunityReport> l = new ArrayList<>();
        if (text == null || !text.startsWith("GRAPE-COMMUNITY\t1")) return l;
        for (String line : text.split("\n")) {
            String[] a = line.split("\t", -1);
            if (a.length < 14 || !a[0].equals("R")) continue;
            try {
                CommunityReport r = new CommunityReport();
                r.reporter = a[1].trim(); r.deviceId = a[2].trim(); r.conn = DeviceCatalog.route(a[3]); r.protocol = a[4].trim(); r.firmware = a[5].trim();
                r.dsd = a[6].trim().equalsIgnoreCase("DSD"); r.dsdMode = DeviceCatalog.dsd(a[7]); r.rate = Integer.parseInt(a[8].trim()); r.bits = Integer.parseInt(a[9].trim());
                r.result = Result.valueOf(a[10].trim().toUpperCase(Locale.ROOT)); r.method = TestMethod.valueOf(a[11].trim().toUpperCase(Locale.ROOT)); r.date = a[12].trim(); r.evidence = a[13].trim();
                if (!r.deviceId.isEmpty()) l.add(r);
            } catch (RuntimeException ignored) { /* dòng hỏng: bỏ qua */ }
        }
        return l;
    }

    // ------------------------------------------------------------------ đối chiếu với phiên phát hiện tại
    public enum Applies { EXACT, PARTIAL, NONE }
    /** Tổ hợp đang phát: Device + Firmware + Transport + Protocol + Audio Format + Playback Configuration. */
    public static final class Combo {
        public String catalogId = "", firmware = "", protocol = "";
        public Route conn = Route.ROUTE_UNKNOWN;
        public boolean dsd; public Dsd dsdMode = Dsd.UNDOCUMENTED; public int rate, bits;
        public boolean dspOff, volumeFixed, resamplingOff;
    }
    public static final class Applicability { public Applies applies = Applies.NONE; public final List<String> reasons = new ArrayList<>(); }

    /** Một xác minh của người dùng áp dụng cho phiên hiện tại? Chỉ EXACT khi cùng thiết bị, kết nối, loại định dạng, DSD mode, tần số/bit; firmware khác ⇒ không áp dụng, firmware chưa rõ ⇒ PARTIAL. */
    public static Applicability appliesTo(UserVerification v, Combo c) {
        Applicability r = new Applicability();
        if (!v.catalogId.isEmpty() && !c.catalogId.isEmpty() && !v.catalogId.equals(c.catalogId)) { r.reasons.add("khác thiết bị"); return r; }
        if (v.conn != c.conn) { r.reasons.add("khác phương thức kết nối (" + v.conn + " ≠ " + c.conn + ")"); return r; }
        if (v.dsd != c.dsd) { r.reasons.add("khác loại định dạng (PCM/DSD)"); return r; }
        if (v.dsd && v.dsdMode != c.dsdMode) { r.reasons.add("khác kiểu DSD (" + dsdName(v.dsdMode) + " ≠ " + dsdName(c.dsdMode) + ")"); return r; }
        if (!v.firmware.isEmpty() && !c.firmware.isEmpty() && !v.firmware.equalsIgnoreCase(c.firmware)) { r.reasons.add("khác firmware"); return r; }
        boolean partial = false;
        if (!v.dsd && (v.rate != c.rate || v.bits != c.bits)) { r.reasons.add("khác tần số/độ sâu bit đã thử"); partial = true; }
        if (!v.protocol.isEmpty() && !c.protocol.isEmpty() && !v.protocol.equalsIgnoreCase(c.protocol)) { r.reasons.add("khác giao thức"); partial = true; }
        if (!v.firmware.isEmpty() && c.firmware.isEmpty()) { r.reasons.add("firmware phiên hiện tại chưa rõ"); partial = true; }
        if (v.dspOff && !c.dspOff) { r.reasons.add("DSP đang bật, khác điều kiện đã thử"); partial = true; }
        if (v.resamplingOff && !c.resamplingOff) { r.reasons.add("điều kiện resampling khác lúc thử"); partial = true; }
        if (v.volumeFixed && !c.volumeFixed) { r.reasons.add("điều kiện âm lượng khác lúc thử"); partial = true; }
        r.applies = partial ? Applies.PARTIAL : Applies.EXACT;
        return r;
    }

    // ------------------------------------------------------------------ lưu / tải (dòng "V" trong hồ sơ thiết bị)
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", ""); }
    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n); } else b.append(c); }
        return b.toString();
    }
    public static String serialize(UserVerification v) {
        return "V\t" + esc(v.profileKey) + "\t" + esc(v.catalogId) + "\t" + esc(v.manufacturer) + "\t" + esc(v.model) + "\t" + esc(v.firmware) + "\t" + v.conn.name() + "\t" + esc(v.protocol)
                + "\t" + (v.dsd ? "DSD" : "PCM") + "\t" + v.dsdMode.name() + "\t" + v.rate + "\t" + v.bits + "\t" + v.result.name() + "\t" + v.method.name()
                + "\t" + (v.dspOff ? 1 : 0) + (v.volumeFixed ? 1 : 0) + (v.resamplingOff ? 1 : 0) + "\t" + v.at + "\t" + esc(v.software) + "\t" + esc(v.notes);
    }
    /** null nếu dòng hỏng. */
    public static UserVerification parse(String line) {
        String[] a = line.split("\t", -1);
        if (a.length < 18 || !a[0].equals("V")) return null;
        try {
            UserVerification v = new UserVerification();
            v.profileKey = unesc(a[1]); v.catalogId = unesc(a[2]); v.manufacturer = unesc(a[3]); v.model = unesc(a[4]); v.firmware = unesc(a[5]); v.conn = Route.valueOf(a[6]); v.protocol = unesc(a[7]);
            v.dsd = a[8].equals("DSD"); v.dsdMode = Dsd.valueOf(a[9]); v.rate = Integer.parseInt(a[10]); v.bits = Integer.parseInt(a[11]); v.result = Result.valueOf(a[12]); v.method = TestMethod.valueOf(a[13]);
            String c = a[14]; if (c.length() != 3) return null;
            v.dspOff = c.charAt(0) == '1'; v.volumeFixed = c.charAt(1) == '1'; v.resamplingOff = c.charAt(2) == '1';
            v.at = Long.parseLong(a[15]); v.software = unesc(a[16]); v.notes = unesc(a[17]);
            return v.profileKey.isEmpty() ? null : v;
        } catch (RuntimeException e) { return null; }
    }
}
