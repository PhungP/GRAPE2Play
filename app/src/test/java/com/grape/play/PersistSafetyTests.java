package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.grape.play.BitPerfect.Route;
import com.grape.play.DeviceCatalog.Dsd;
import com.grape.play.DeviceVerification.Result;
import com.grape.play.DeviceVerification.TestMethod;

/** Phase 1 (đóng): kho nguồn nhạc, sửa tag và hồ sơ/xác minh thiết bị không bao giờ bị ghi đè bằng dữ liệu rỗng khi đọc lỗi. */
final class PersistSafetyTests {
    private PersistSafetyTests() {}

    static Map<String, String> src(String id, String pass) { Map<String, String> m = new LinkedHashMap<>(); m.put("id", id); m.put("t", "smb"); m.put("n", "NAS \"nhà\""); m.put("h", "192.168.1.2"); m.put("s", "nhạc"); m.put("r", ""); m.put("u", ""); m.put("us", "u"); m.put("p", pass); return m; }

    static void run() { sources(); tagfix(); devices(); sessionLabels(); }

    static void sources() {
        List<Map<String, String>> l = Arrays.asList(src("a1", "p/w\\\"\néệ"), src("b2", ""));
        List<Map<String, String>> back = PersistCodec.decodeSources(PersistCodec.encodeRecords(l));
        check(back != null && back.equals(l), "PS1. danh sách nguồn round-trip (mật khẩu có ký tự đặc biệt, Unicode, chuỗi rỗng, thứ tự)");
        List<Map<String, String>> old = PersistCodec.decodeSources("[{\"id\":\"a1\",\"t\":\"smb\",\"n\":\"NAS\",\"h\":\"192.168.1.2\",\"s\":\"music\",\"r\":\"\",\"u\":\"\",\"us\":\"u\",\"p\":\"p\\/w\"}]");
        check(old != null && old.size() == 1 && old.get(0).get("p").equals("p/w") && old.get(0).get("n").equals("NAS"), "PS2. dữ liệu nguồn do org.json cũ ghi (\\/) vẫn đọc được");
        check(PersistCodec.decodeSources("[]").isEmpty() && PersistCodec.isSources(" [ ] "), "PS3. danh sách nguồn rỗng hợp lệ");
        String[] bad = {"", "{}", "[1]", "[{\"id\":\"a\",\"t\":\"smb\"}]", "[{\"id\":1,\"t\":\"smb\",\"n\":\"x\"}]", "[{\"id\":\"a\",\"t\":\"smb\",\"n\":null}]", "[{\"id\":\"a\",\"t\":\"smb\",\"n\":\"x\"}", "rác"};
        boolean ok = true; for (String b : bad) if (PersistCodec.isSources(b)) { ok = false; System.out.println("  chấp nhận nhầm: " + b); }
        check(ok, "PS4. thiếu khoá bắt buộc (id/t/n), sai kiểu, null, cụt, rác → không hợp lệ (không bỏ lẳng lặng từng nguồn)");
        String json = PersistCodec.encodeRecords(l); boolean cuts = true;
        for (int i = 0; i < json.length(); i++) if (PersistCodec.isSources(json.substring(0, i))) cuts = false;
        check(cuts, "PS5. mọi điểm cắt cụt của danh sách nguồn đều bị từ chối");
        List<Map<String, String>> extra = PersistCodec.decodeSources("[{\"id\":\"a\",\"t\":\"dav\",\"n\":\"x\",\"moi\":\"truong\"}]");
        check(extra != null && extra.get(0).get("moi").equals("truong"), "PS6. khoá lạ (phiên bản mới hơn) không làm danh sách bị từ chối");
        // qua kho an toàn: dữ liệu nguồn hỏng không bị ghi đè khi người dùng thêm nguồn mới
        SafeStoreTests.Mem b = new SafeStoreTests.Mem(); b.m.put("v", "[{\"id\":\"a1\",\"t\":\"smb\",\"n\":\"NAS\",\"p\":\"matkhau");
        SafeJsonStore s = new SafeJsonStore(b, PersistCodec::isSources, m -> { }, "Sources");
        check(s.load().status == SafeJsonStore.Status.CORRUPT && !s.write(PersistCodec.encodeRecords(Arrays.asList(src("new", "x")))) && b.m.get("v").contains("matkhau"), "PS7. danh sách nguồn (kèm mật khẩu) bị cụt, không có backup: khoá ghi, thêm nguồn mới KHÔNG ghi đè bản thô");
        b = new SafeStoreTests.Mem(); b.m.put("v", "[{\"id\":\"a1\""); b.m.put("v.bak", PersistCodec.encodeRecords(l));
        s = new SafeJsonStore(b, PersistCodec::isSources, m -> { }, "Sources");
        SafeJsonStore.Loaded L = s.load();
        check(L.status == SafeJsonStore.Status.RECOVERED && PersistCodec.decodeSources(L.value).equals(l), "PS8. chính cụt + backup tốt: khôi phục đủ danh sách nguồn từ backup");
    }

    static void tagfix() {
        Map<String, String> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
        a.put("Beatles, The", "The Beatles"); a.put("Tiếng \"Việt\"", "Việt\\"); b.put("Abbey Rd", "Abbey Road");
        Map<String, Map<String, String>> back = PersistCodec.decodeTagFix(PersistCodec.encodeTagFix(a, b));
        check(back != null && back.get("a").equals(a) && back.get("b").equals(b), "PS9. sửa tag round-trip (Unicode, dấu nháy, \\)");
        Map<String, Map<String, String>> e = PersistCodec.decodeTagFix(PersistCodec.encodeTagFix(new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>()));
        check(e != null && e.get("a").isEmpty() && e.get("b").isEmpty() && PersistCodec.isTagFix("{}"), "PS10. rỗng hợp lệ ({} hoặc a,b rỗng) — thiếu khoá a/b = rỗng");
        Map<String, Map<String, String>> old = PersistCodec.decodeTagFix("{\"a\":{\"x\":\"y\"},\"b\":{}}");
        check(old != null && old.get("a").get("x").equals("y") && PersistCodec.decodeTagFix("{\"a\":{},\"b\":{},\"z\":5}") != null, "PS11. định dạng cũ đọc được; khoá lạ ở cấp trên bị bỏ qua");
        String[] bad = {"", "[]", "{\"a\":[]}", "{\"a\":{\"x\":1}}", "{\"a\":{\"x\":\"y\"}", "{\"a\":{\"x\":null}}", "x"};
        boolean ok = true; for (String s : bad) if (PersistCodec.isTagFix(s)) { ok = false; System.out.println("  chấp nhận nhầm: " + s); }
        check(ok, "PS12. sai kiểu/cụt/rác → không hợp lệ (sửa tag của người dùng không bị mất âm thầm)");
        String j = PersistCodec.encodeTagFix(a, b); boolean cuts = true;
        for (int i = 0; i < j.length(); i++) if (PersistCodec.isTagFix(j.substring(0, i))) cuts = false;
        check(cuts, "PS13. mọi điểm cắt cụt của sửa tag đều bị từ chối");
    }

    static void devices() {
        DeviceRegistry.Store st = new DeviceRegistry.Store();
        DeviceRegistry.Detected det = new DeviceRegistry.Detected("DAC lạ", Route.USB, 0x1234, 0x5678, true);
        DeviceRegistry.declare(st, det, "Acme", "DAC\t3000\nX", "ghi chú\nnhiều dòng", new int[]{16, 24}, new int[]{44100, 96000}, 7L);
        DeviceRegistry.reject(st, det, new DeviceRegistry.Match());
        DeviceRegistry.dismiss(st, new DeviceRegistry.Detected("Loa lạ", Route.HDMI, -1, -1, false));
        String key = DeviceRegistry.keyOf(det);
        DeviceVerification.UserVerification v = DeviceVerificationTests.uv(Route.UPNP, true, Dsd.NATIVE, 0, 0, Result.PASS, TestMethod.DEVICE_INDICATOR, true, "đã nghe thử\ncó tab\t");
        v.profileKey = key; st.verifications.add(v);
        String text = DeviceRegistry.serialize(st);
        check(DeviceRegistry.isValid(text) && DeviceRegistry.isValid("GRAPE-DEVICES\t1\n") && DeviceRegistry.isValid(text + "Z\ttuong lai\n"), "PS14. dữ liệu thiết bị hợp lệ (đầy đủ, chỉ tiêu đề, có dòng loại lạ của bản mới hơn)");
        String[] bad = {null, "", "rác", "GRAPE-DEVICES", "GRAPE-DEVICES\t1\nP\tkey\tUSER", "GRAPE-DEVICES\t1\nP\tk\tUSER\t\tA\tB\tn\tKHONGPHAILAROUTE\t0\t0\t\t1\t\t\n", "GRAPE-DEVICES\t1\nR\t\n", "GRAPE-DEVICES\t1\nV\tbroken\n"};
        boolean ok = true; for (String b : bad) if (DeviceRegistry.isValid(b)) { ok = false; System.out.println("  chấp nhận nhầm: " + b); }
        check(ok, "PS15. thiếu tiêu đề, P thiếu trường/sai route, R rỗng, V hỏng → không hợp lệ (parse sẽ lẳng lặng bỏ qua những dòng này)");
        check(!DeviceRegistry.isValid(text.substring(0, text.indexOf("\nV\t") + 5)), "PS16. dòng xác minh (V) bị cắt ngang → không hợp lệ (kết quả người dùng tự xác minh không bị mất âm thầm)");
        DeviceRegistry.Store back = DeviceRegistry.parse(text);
        check(back.profiles.size() == 1 && back.verificationsFor(key).size() == 1 && DeviceRegistry.serialize(back).equals(text), "PS17. parse(serialize) khớp và serialize ổn định (hồ sơ + xác minh)");

        // Qua kho an toàn
        SafeStoreTests.Mem b = new SafeStoreTests.Mem(); b.m.put("v", text.substring(0, text.indexOf("\nV\t") + 5));
        SafeJsonStore s = new SafeJsonStore(b, DeviceRegistry::isValid, m -> { }, "Devices");
        check(s.load().status == SafeJsonStore.Status.CORRUPT && !s.write("GRAPE-DEVICES\t1\n") && b.m.get("v").contains("\nV\t"), "PS18. hồ sơ thiết bị bị cụt giữa dòng V, không có backup: khoá ghi, KHÔNG bị ghi đè bằng kho rỗng");
        b = new SafeStoreTests.Mem(); b.m.put("v", "GRAPE-DEVICES\t1\nV\tbroken\n"); b.m.put("v.bak", text);
        s = new SafeJsonStore(b, DeviceRegistry::isValid, m -> { }, "Devices"); SafeJsonStore.Loaded L = s.load();
        check(L.status == SafeJsonStore.Status.RECOVERED && DeviceRegistry.parse(L.value).verificationsFor(key).size() == 1, "PS19. dòng V hỏng ở bản chính + backup tốt: khôi phục đủ hồ sơ và xác minh từ backup");
        b = new SafeStoreTests.Mem(); s = new SafeJsonStore(b, DeviceRegistry::isValid, m -> { }, "Devices");
        check(s.load().status == SafeJsonStore.Status.ABSENT && s.write(text) && b.m.get("v").equals(text) && b.m.get("v.bak") == null, "PS20. chưa có dữ liệu: ghi lần đầu bình thường (kho mới không bị khoá)");
    }


    static void sessionLabels() {
        check(DeviceVerification.sessionDsdMode(false, false, false) == Dsd.UNDOCUMENTED && DeviceVerification.sessionDsdMode(false, true, true) == Dsd.UNDOCUMENTED, "PS21. nguồn PCM: nhãn DSD là 'chưa có tài liệu' bất kể đường truyền");
        check(DeviceVerification.sessionDsdMode(true, true, false) == Dsd.DOP && DeviceVerification.sessionDsdMode(true, true, true) == Dsd.DOP, "PS22. DoP đang chạy: nhãn DOP");
        check(DeviceVerification.sessionDsdMode(true, false, true) == Dsd.NATIVE, "PS23. gửi file DSD gốc tới thiết bị ngoài (UPnP và Cast như nhau): nhãn NATIVE = 'app gửi gốc', không khẳng định thiết bị phát gốc");
        check(DeviceVerification.sessionDsdMode(true, false, false) == Dsd.DSD_TO_PCM, "PS24. phát cục bộ DSD không DoP: DSD→PCM");
        // Một kết quả người dùng tự xác minh ghi từ form (nhãn lấy từ cùng hàm) phải khớp CHÍNH XÁC phiên phát tương ứng — kể cả Cast+DSD
        DeviceVerification.UserVerification v = DeviceVerificationTests.uv(Route.CAST, true, DeviceVerification.sessionDsdMode(true, false, true), 0, 0, Result.PASS, TestMethod.DEVICE_INDICATOR, true, "đã thử");
        DeviceVerification.Combo c = new DeviceVerification.Combo(); c.catalogId = "wiim-ultra"; c.conn = Route.CAST; c.dsd = true; c.dsdMode = DeviceVerification.sessionDsdMode(true, false, true); c.dspOff = c.volumeFixed = c.resamplingOff = true;
        check(DeviceVerification.appliesTo(v, c).applies == DeviceVerification.Applies.EXACT, "PS25. xác minh Cast+DSD lưu từ form khớp EXACT với phiên Cast+DSD (không còn lệch nhãn giữa form và so khớp)");
    }
}
