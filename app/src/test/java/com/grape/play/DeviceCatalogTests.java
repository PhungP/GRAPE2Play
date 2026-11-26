package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.grape.play.BitPerfect.Route;
import com.grape.play.DeviceCatalog.Catalog;
import com.grape.play.DeviceCatalog.Config;
import com.grape.play.DeviceCatalog.Device;
import com.grape.play.DeviceCatalog.Discovered;
import com.grape.play.DeviceCatalog.Dsd;
import com.grape.play.DeviceCatalog.Query;
import com.grape.play.DeviceCatalog.Status;

/** Test Device Catalog 2.9.9 (DeviceCatalog.java + assets/device_catalog.tsv). Chỉ kiểm logic/dữ liệu; không có phép đo thiết bị thật. */
final class DeviceCatalogTests {
    private DeviceCatalogTests() {}

    static final String HDR = "GRAPE-CATALOG\t1\tt\t2026-01-01\n";
    static Catalog load() {
        try {
            // run_core_tests.sh truyền -Dgrape.assets; Gradle chạy test với thư mục làm việc = app/
            String dir = System.getProperty("grape.assets", "");
            java.nio.file.Path f = dir.isEmpty() ? null : Paths.get(dir, "device_catalog.tsv");
            for (String c : new String[]{"app/src/main/assets", "src/main/assets"}) if (f == null || !Files.exists(f)) f = Paths.get(c, "device_catalog.tsv");
            return DeviceCatalog.parse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
        } catch (Exception e) { check(false, "CAT0. đọc được device_catalog.tsv (" + e + ")"); return new Catalog(); }
    }
    static Discovered up(String maker, String model, String friendly) { return new Discovered(maker, model, friendly, Route.UPNP); }

    static void run() {
        Catalog cat = load();
        List<Device> all = cat.devices;
        // --- dữ liệu tích hợp
        check(all.size() >= 80 && cat.problems.isEmpty(), "CAT1. danh mục nạp được ≥30 thiết bị, không có cảnh báo dữ liệu (" + all.size() + " thiết bị; " + cat.problems + ")");
        check(cat.version.equals("2.9.10") && cat.researched.equals("2026-11-26"), "CAT1b. phiên bản và ngày nghiên cứu được ghi");
        boolean noUnsupported = true, evid = true;
        for (Device d : all) for (Config c : d.configs) {
            if (c.status == Status.CONFIG_VERIFIED || c.level >= 3) noUnsupported = false;   // dữ liệu hiện tại không có phép đo/bằng chứng đủ cho mức 3-4
            if (c.level >= 2 && (c.evidenceUrls.length + c.evidenceSources.length == 0 || c.evidenceDate.isEmpty())) evid = false;
            if (d.lastReviewed.isEmpty()) evid = false;
        }
        check(noUnsupported, "CAT2. không bản ghi nào được gán 'Verified' (mức ≥3) khi chưa có bằng chứng đo");
        check(evid, "CAT2b. mọi cấu hình mức ≥2 đều có nguồn bằng chứng + ngày; mọi thiết bị có ngày xem xét");
        // --- ba thiết bị bắt buộc
        Device wxc = cat.byId("yamaha-wxc-50"), jbl = cat.byId("jbl-l75ms"), wiim = cat.byId("wiim-ultra");
        check(wxc != null && jbl != null && wiim != null, "CAT3. có Yamaha WXC-50, JBL L75ms, WiiM Ultra");
        Config wu = wxc.config(Route.UPNP);
        check(wu.dsd == Dsd.UNDOCUMENTED && !wu.nativeDsd() && wu.status == Status.CAPABILITY_VERIFIED && wu.level == 2, "CAT3b. WXC-50: DSD có trong tài liệu nhưng KHÔNG coi là Native DSD qua UPnP");
        Config jc = jbl.config(Route.UPNP);
        check(jc.dsd == Dsd.NATIVE && jc.method == DeviceCatalog.Method.USER_TEST && jc.level == 2 && jc.status != Status.CONFIG_VERIFIED && jc.evidenceSources[0].contains("mconnect"),
                "CAT3c. JBL L75ms: Native DSD (mconnect) giữ làm bằng chứng người dùng, mức 2, không phải DSD→PCM, không được nâng quá mức 2");
        Config wd = null; for (Config c : wiim.configsFor(Route.UPNP)) if (c.dsd == Dsd.DSD_TO_PCM) wd = c;
        check(wd != null && wd.status == Status.NOT_BIT_PERFECT && !wd.nativeDsd(), "CAT3d. WiiM Ultra: DSD qua DLNA = DSD→PCM, không Native");
        check(wiim.configsFor(Route.UPNP).size() == 2 && wiim.config(Route.USB) != null && wiim.config(Route.SPDIF) != null, "CAT3e. cấu hình theo từng kết nối (UPnP PCM/DSD, USB, S/PDIF) tách riêng");
        check(wiim.config(Route.USB).dsd != Dsd.DSD_TO_PCM && wiim.config(Route.USB).status == Status.CAPABILITY_VERIFIED, "CAT3f. kết quả DSD→PCM trên UPnP không lan sang USB");
        // --- phân loại DSD
        Device cq = cat.byId("chord-qutest"), mj = cat.byId("chord-mojo-2"), k11 = cat.byId("fiio-k11-r2r");
        check(cq.config(Route.USB).dsd == Dsd.NATIVE && mj.config(Route.USB).dsd == Dsd.DOP && k11.config(Route.USB).dsd == Dsd.DSD_TO_PCM, "CLS1. Native / DoP / DSD→PCM phân loại đúng (Qutest/Mojo 2/K11 R2R)");
        check(cq.config(Route.USB).maxRate() == 768000 && cq.config(Route.USB).maxBits() == 32, "CLS2. PCM tối đa đọc đúng từ trường maxPcm");
        check(cat.byId("cambridge-audio-cxa81").config(Route.BLUETOOTH).status == Status.NOT_BIT_PERFECT, "CLS3. Bluetooth codec nén: không bit-perfect");
        // --- chính sách bằng chứng
        Catalog t = DeviceCatalog.parse("GRAPE-CATALOG\t1\tx\t2026-01-01\n"
            + "D\ta\tAcme\tOne\t\tUSB_DAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\ta\tUSB\tUAC2\tPCM\t24/192\t\t\tUNDOCUMENTED\t\tUNDOCUMENTED\tUNDOCUMENTED\tUNDOCUMENTED\t3\tCONFIG_VERIFIED\tDOCUMENTATION\t\t\t\t\t\t2026-01-01\tY\n"
            + "D\tb\tAcme\tTwo\t\tUSB_DAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\tb\tUSB\tUAC2\tPCM\t24/192\t\t\tUNDOCUMENTED\t\tYES\tNO\tNO\t4\tCONFIG_VERIFIED\tUSER_TEST\tx\t\tuser\thttp://e\t\t2026-01-01\tN\n"
            + "D\tc\tAcme\tThree\t\tDAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\tc\tUSB\tp\tDSD\t\t\t\tDSD_TO_PCM\t\tYES\tYES\tYES\t3\tCONFIG_VERIFIED\tDOCUMENTATION\tc\t\tsrc\t\t\t2026-01-01\tY\n"
            + "C\tc\tBLUETOOTH\tp\tPCM\t\t\t\tUNDOCUMENTED\t\tYES\tYES\tYES\t3\tCONFIG_VERIFIED\tDOCUMENTATION\tc\t\tsrc\t\t\t2026-01-01\tY\n"
            + "D\ta\tDup\tDup\t\tDAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\tzz\tUSB\tp\tPCM\n" + "garbage\n");
        Config a = t.byId("a").configs.get(0), b = t.byId("b").configs.get(0), c1 = t.byId("c").configs.get(0), c2 = t.byId("c").configs.get(1);
        check(a.level == 1 && a.status == Status.CANDIDATE && !a.downgraded.isEmpty(), "POL1. mức 3 không có bằng chứng bị hạ về mức 1/CANDIDATE");
        check(b.level == 2 && b.status != Status.CONFIG_VERIFIED, "POL2. kết quả người dùng thử không vượt mức 2 (mức 4 USER_TEST bị hạ)");
        check(c1.status == Status.NOT_BIT_PERFECT, "POL3. DSD→PCM không thể CONFIG_VERIFIED");
        check(c2.status == Status.CANDIDATE, "POL4. Bluetooth không thể CONFIG_VERIFIED");
        check(t.devices.size() == 3 && t.problems.size() >= 6, "POL5. id trùng/dòng hỏng/cấu hình mồ côi bị bỏ qua và báo cáo, không ném lỗi (" + t.problems.size() + " cảnh báo)");
        check(DeviceCatalog.parse(null).devices.isEmpty() && DeviceCatalog.parse("rác").devices.isEmpty(), "POL6. đầu vào null/rác → danh mục rỗng, không lỗi");
        // Mức 3 hợp lệ chỉ khi đủ điều kiện + bằng chứng (kiểm đường hợp lệ)
        Catalog ok = DeviceCatalog.parse(HDR + "D\tz\tAcme\tFour\t\tDAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\tz\tUSB\tUAC2\tPCM\t24/192\t\t\tNONE\t\tOFF\tFIXED\tNONE\t3\tCONFIG_VERIFIED\tDOCUMENTATION\tcond\t\tsrc\thttp://e\t\t2026-01-01\tY\n");
        check(ok.byId("z").configs.get(0).status == Status.CONFIG_VERIFIED && ok.problems.isEmpty(), "POL7. mức 3 đủ điều kiện + bằng chứng được giữ nguyên");
        Catalog e2e = DeviceCatalog.parse(HDR + "D\ty\tAcme\tFive\t\tDAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\ty\tUSB\tUAC2\tPCM\t24/192\t\t\tNONE\t\tOFF\tFIXED\tNONE\t4\tCONFIG_VERIFIED\tDOCUMENTATION\tcond\t\tsrc\thttp://e\t\t2026-01-01\tY\n");
        check(e2e.byId("y").configs.get(0).level == 3, "POL8. mức 4 không có phép đo (MEASUREMENT) bị hạ xuống 3");
        // --- nhận diện
        Discovered x = up("Yamaha Corporation", "WXC-50", "Phòng khách");
        DeviceCatalog.CatMatch m = DeviceCatalog.match(x, all);
        check(m.device == wxc && m.quality == DeviceCatalog.MODEL && !m.needsConfirmation() && m.active.size() == 1, "MAT1. hãng + model UPnP khớp chính xác (WXC-50), không cần xác nhận");
        check(DeviceCatalog.match(up("Yamaha", "WXC-500", "WXC-500"), all).device == null, "MAT2. WXC-500 không bị nhận nhầm là WXC-50");
        check(DeviceCatalog.match(up("Yamaha", "", "Yamaha WXC-50 (kitchen)"), all).quality == DeviceCatalog.ALIAS, "MAT3. tên thân thiện chứa model chỉ là bằng chứng phụ (ALIAS), cần xác nhận");
        check(DeviceCatalog.match(up("Yamaha", "", "Yamaha WXC-50 (kitchen)"), all).needsConfirmation(), "MAT3b. khớp qua bí danh/tên thân thiện luôn cần người dùng xác nhận");
        check(DeviceCatalog.match(up("Other Brand", "", "WiiM Ultra"), all).device == null, "MAT4. hãng đã biết nhưng khác → không đoán qua tên thân thiện");
        check(DeviceCatalog.match(up("WiiM", "Ultra", "x"), all).device == wiim && DeviceCatalog.match(up("WiiM", "Mini", "x"), all).device.id.equals("wiim-mini"), "MAT5. cùng hãng khác model không lẫn (Ultra / Mini)");
        check(DeviceCatalog.match(up("WiiM", "", "WiiM Ultra Kitchen"), all).device == wiim, "MAT6. 'WiiM Ultra' trong tên thân thiện → ALIAS tới đúng Ultra");
        check(DeviceCatalog.match(up("WiiM", "", "Pro"), all).device == null, "MAT7. tên quá ngắn/chung chung (<4 ký tự) không khớp");
        Discovered hw = new Discovered("", "", "Unknown DAC", Route.USB); hw.vid = 0x1234; hw.pid = 0x5678;
        List<Device> withHw = new java.util.ArrayList<>(all); Device fake = DeviceCatalog.parse(HDR + "D\thw1\tFake\tBox\t\tUSB_DAC\t\t\t\t1234:5678\t2026-01-01\t1\nC\thw1\tUSB\tUAC2\tPCM\n").devices.get(0); withHw.add(fake);
        DeviceCatalog.CatMatch mh = DeviceCatalog.match(hw, withHw);
        check(mh.device == fake && mh.quality == DeviceCatalog.HARDWARE, "MAT8. VID:PID chính xác khớp ưu tiên cao nhất dù tên khác");
        hw.pid = 0x9999; check(DeviceCatalog.match(hw, withHw).device == null, "MAT8b. khác PID → không khớp");
        Device twin = DeviceCatalog.parse(HDR + "D\thw2\tFake\tBox\t\tUSB_DAC\t\t\t\t1234:5678\t2026-01-01\t1\nC\thw2\tUSB\tUAC2\tPCM\n").devices.get(0); withHw.add(twin);
        hw.pid = 0x5678; DeviceCatalog.CatMatch amb = DeviceCatalog.match(hw, withHw);
        check(amb.ambiguous && amb.device == null && amb.candidates.size() == 2 && amb.needsConfirmation(), "MAT9. hai bản ghi cùng VID:PID → mơ hồ, không tự chọn");
        Discovered k9 = new Discovered("FiiO", "K9 Pro ESS", "K9 Pro ESS", Route.USB);
        DeviceCatalog.CatMatch mk = DeviceCatalog.match(k9, all);
        check(mk.device != null && mk.device.id.equals("fiio-k9-pro-ess") && mk.active.size() == 1 && mk.active.get(0).conn == Route.USB, "MAT10. USB DAC: nhận diện K9 Pro ESS và chỉ trả cấu hình USB");
        check(DeviceCatalog.match(new Discovered("FiiO", "K9 Pro", "K9 Pro", Route.USB), all).device == null, "MAT10b. K9 Pro (không ESS) không nhầm với K9 Pro ESS");
        check(DeviceCatalog.match(new Discovered("FiiO", "K9 Pro ESS", "x", Route.HDMI), all).active.isEmpty(), "MAT11. kết nối không có trong danh mục (HDMI) → không mượn kết quả của USB");
        check(DeviceCatalog.match(null, all).device == null && DeviceCatalog.match(new Discovered(), all).device == null, "MAT12. đầu vào rỗng/null không lỗi");
        // Thiết bị thật quan sát trên LAN: JBL L75ms khai manufacturer "Harman Luxury Audio", modelName "JBL L75ms"
        DeviceCatalog.CatMatch jm = DeviceCatalog.match(up("Harman Luxury Audio", "JBL L75ms", "JBL L75ms"), all);
        check(jm.device == jbl && jm.quality == DeviceCatalog.MODEL && !jm.needsConfirmation(), "MAT15. JBL L75ms thật (Harman Luxury Audio / 'JBL L75ms') khớp chắc chắn");
        check(DeviceCatalog.match(up("Harman Luxury Audio", "Ultra", "x"), all).device == null && DeviceCatalog.match(up("Harman", "JBL L750ms", "x"), all).device == null, "MAT16. hãng mẹ không làm khớp nhầm model/hãng khác (Ultra, L750ms)");
        check(DeviceCatalog.match(up("Panasonic Corporation", "Technics SU-GX70", "x"), all).device != null, "MAT17. Panasonic → Technics, tiền tố hãng trong tên model được bỏ");
        // --- tìm kiếm và lọc
        Query q = new Query(); q.text = "wiim";
        check(DeviceCatalog.search(all, q).size() == 4, "SRCH1. tìm theo hãng (WiiM: Ultra, Mini, Pro, Amp)");
        q = new Query(); q.text = "x2";
        List<Device> x2 = DeviceCatalog.search(all, q); boolean allMatch = !x2.isEmpty(), hasEl = false;
        for (Device d : x2) { if (!DeviceRegistry.norm(d.label() + String.join("", d.aliases)).contains("x2")) allMatch = false; if (d.model.equals("element X2")) hasEl = true; }
        check(allMatch && hasEl, "SRCH2. tìm theo model (mọi kết quả chứa 'x2', gồm element X2)");
        q = new Query(); q.text = "cxa 81";
        check(DeviceCatalog.search(all, q).size() >= 1, "SRCH2b. bí danh có khoảng trắng");
        q = new Query(); q.category = "AVR";
        check(DeviceCatalog.search(all, q).size() >= 4, "SRCH3. lọc theo loại thiết bị");
        q = new Query(); q.conn = Route.HDMI;
        List<Device> hd = DeviceCatalog.search(all, q);
        boolean hdmiOnly = !hd.isEmpty(); for (Device d : hd) if (d.config(Route.HDMI) == null) hdmiOnly = false;
        check(hdmiOnly, "SRCH4. lọc theo kết nối HDMI chỉ trả thiết bị có cấu hình HDMI");
        q = new Query(); q.conn = Route.USB; q.dsd = Dsd.DSD_TO_PCM;
        List<Device> r5 = DeviceCatalog.search(all, q);
        check(r5.size() >= 1 && r5.get(0).id.equals("fiio-k11-r2r"), "SRCH5. lọc DSD→PCM trên USB (điều kiện áp lên CÙNG một cấu hình)");
        q = new Query(); q.conn = Route.UPNP; q.dsd = Dsd.NATIVE; q.text = "wiim";
        check(DeviceCatalog.search(all, q).isEmpty(), "SRCH5b. WiiM không có UPnP Native DSD (không ghép đặc tính của hai kết nối)");
        q = new Query(); q.minPcmRate = 768000; q.conn = Route.USB;
        List<Device> hi = DeviceCatalog.search(all, q);
        check(hi.size() >= 5 && hi.contains(cq), "SRCH6. lọc PCM ≥768 kHz");
        q = new Query(); q.anyDsd = true; q.conn = Route.SPDIF; q.text = "dacmagic";
        check(DeviceCatalog.search(all, q).size() == 1, "SRCH7. lọc có DSD trên S/PDIF");
        q = new Query(); q.status = Status.NOT_BIT_PERFECT;
        check(DeviceCatalog.search(all, q).size() >= 2, "SRCH8. lọc theo trạng thái NOT_BIT_PERFECT");
        q = new Query(); q.minLevel = 3;
        check(DeviceCatalog.search(all, q).isEmpty(), "SRCH9. lọc mức ≥3 → rỗng (chưa có bằng chứng mức 3)");
        q = new Query(); q.minLevel = 2;
        check(DeviceCatalog.search(all, q).size() > 20, "SRCH9b. lọc mức ≥2");
        List<Device> sorted = DeviceCatalog.search(all, new Query());
        check(sorted.size() == all.size() && DeviceCatalog.rank(sorted.get(0).best()) >= DeviceCatalog.rank(sorted.get(sorted.size() - 1).best()), "SRCH10. sắp xếp theo trạng thái rồi tên; không mất thiết bị");
        check(DeviceCatalog.categories(all).contains("STREAMER") && DeviceCatalog.connections(all).contains(Route.UPNP), "SRCH11. danh sách giá trị bộ lọc suy ra từ dữ liệu");

        // --- hồ sơ cục bộ + xác nhận danh tính
        DeviceRegistry.Store st = new DeviceRegistry.Store();
        DeviceRegistry.Detected det = new DeviceRegistry.Detected("Yamaha WXC-50", Route.UPNP, -1, -1, false);
        DeviceRegistry.Profile p = DeviceRegistry.linkCatalog(st, det, "uuid:abc", wxc, 1234L);
        check(p.catalogId.equals("yamaha-wxc-50") && p.conn == Route.UPNP && p.udn.equals("uuid:abc") && p.manufacturer.equals("Yamaha"), "PRO1. xác nhận danh tính lưu catalogId + UDN + kết nối");
        DeviceRegistry.Resolved rs = DeviceRegistry.resolve(det, DeviceRegistry.builtin(), st);
        check(rs.level == BitPerfect.DevLevel.USER_DECLARED || rs.level == BitPerfect.DevLevel.NONE, "PRO2. xác nhận danh tính KHÔNG cấp mức xác minh phiên phát");
        DeviceRegistry.Store back = DeviceRegistry.parse(DeviceRegistry.serialize(st));
        check(back.profiles.size() == 1 && back.profiles.get(0).catalogId.equals("yamaha-wxc-50") && back.profiles.get(0).udn.equals("uuid:abc"), "PRO3. lưu/tải giữ catalogId và UDN");
        DeviceRegistry.linkCatalog(st, det, "uuid:abc", wiim, 2L);
        check(st.profiles.size() == 1 && st.find(DeviceRegistry.keyOf(det)).catalogId.equals("wiim-ultra"), "PRO4. sửa danh tính thay liên kết, không nhân đôi hồ sơ");
        DeviceRegistry.linkCatalog(st, det, "uuid:abc", null, 3L);
        check(st.find(DeviceRegistry.keyOf(det)).catalogId.isEmpty() && st.profiles.size() == 1, "PRO5. gỡ liên kết danh mục (giữ hồ sơ)");
        check(st.remove(DeviceRegistry.keyOf(det)) && st.profiles.isEmpty(), "PRO6. xoá liên kết thiết bị cục bộ");
        String legacy = "GRAPE-DEVICES\t1\nP\tUSB|-|k9\tDETECTED\tfiio-k9pro-ess/usb\tFiiO\tK9 Pro ESS\tK9\tUSB\t-1\t-1\t\t1000\t16,24\t44100\n";
        DeviceRegistry.Store old = DeviceRegistry.parse(legacy);
        check(old.profiles.size() == 1 && old.profiles.get(0).catalogId.isEmpty() && old.profiles.get(0).entryId.equals("fiio-k9pro-ess/usb"), "PRO7. tương thích ngược: hồ sơ phiên bản cũ (không có catalogId) vẫn đọc được");
        check(DeviceCatalog.parse(HDR + "D\tq\tA\tB\t\tDAC\t\t\t\t\t2026-01-01\t1\nC\tq\tUSB\tp\tPCM\t\t\t\tUNDOCUMENTED\t\t\t\t\t2\tCAPABILITY_VERIFIED\tDOCUMENTATION\t\t\t\t\t\t\tY\n").devices.get(0).configs.get(0).level == 1, "PRO8. mức 2 thiếu bằng chứng bị hạ");
        // --- schema + dự phòng khi dữ liệu lỗi
        Catalog bad = DeviceCatalog.parse("GRAPE-CATALOG\t99\tx\t2030-01-01\nD\tq\tA\tB\t\tDAC\t\t\t\t\t2026-01-01\t1\nC\tq\tUSB\tp\tPCM\n");
        check(bad.devices.isEmpty() && !bad.problems.isEmpty() && !bad.schema.equals(DeviceCatalog.SCHEMA), "SCH1. schema lạ → danh mục rỗng + cảnh báo (không đoán dữ liệu)");
        check(DeviceCatalog.parse("D\tq\tA\tB\t\tDAC\t\t\t\t\t2026-01-01\t1\n").devices.isEmpty(), "SCH2. thiếu tiêu đề → danh mục rỗng");
        check(DeviceCatalog.parse(HDR).devices.isEmpty() && DeviceCatalog.parse(HDR).problems.isEmpty() && DeviceCatalog.search(DeviceCatalog.parse("").devices, new Query()).isEmpty(), "SCH3. danh mục rỗng: tìm kiếm không lỗi");
        Catalog trunc = DeviceCatalog.parse(HDR + "D\tq\tA\tB\nC\tq\tUSB\nC\tq\n\u0000\u0001binary\tgarbage\n");
        check(trunc.devices.size() == 1 && trunc.devices.get(0).configs.size() <= 2, "SCH4. dòng cụt/nhị phân không làm hỏng việc nạp (" + trunc.problems.size() + " cảnh báo)");
        check(DeviceCatalog.match(up("Yamaha", "WXC-50", "x"), trunc.devices).device == null, "SCH5. khớp trên danh mục lỗi/cụt không ném lỗi");
        check(DeviceCatalog.match(up("Yo", "Ultra", "x"), all).device == null && DeviceCatalog.match(up("Wi", "Ultra", "x"), all).device == null, "MAT13. tên hãng quá ngắn (<3) không khớp tiền tố");
        check(DeviceCatalog.match(up("Yamaha", "WXC-50", "x"), all).device == wxc && DeviceCatalog.match(up("Yamaha", "WXC-500", "x"), all).device == null
                && DeviceCatalog.match(up("Yamaha", "WXC 50", "x"), all).device == wxc && DeviceCatalog.match(up("Yamaha", "", "MusicCast WXC-500 lounge"), all).device == null, "MAT14. Yamaha WXC-50 không bị nhầm WXC-500 (modelName, biến thể khoảng trắng, tên thân thiện)");
        // --- di trú hồ sơ: dòng hỏng, dòng mới, thiếu trường
        String mixed = "GRAPE-DEVICES\t1\nP\tbroken\nP\tk1\tUSER\t\tAcme\tBox\tAcme Box\tUSB\t-1\t-1\tn\t5\t16\t44100\tcat-id\tuuid:1\nR\tk1#x\nD\tk2\n";
        DeviceRegistry.Store ms = DeviceRegistry.parse(mixed);
        check(ms.profiles.size() == 1 && ms.profiles.get(0).catalogId.equals("cat-id") && ms.profiles.get(0).udn.equals("uuid:1") && ms.rejected.size() == 1 && ms.dismissed.size() == 1, "MIG1. đọc hồ sơ lẫn dòng hỏng/mới/cũ; dòng hỏng bị bỏ qua");
        check(DeviceRegistry.parse(DeviceRegistry.serialize(ms)).profiles.get(0).catalogId.equals("cat-id") && DeviceRegistry.parse("").profiles.isEmpty() && DeviceRegistry.parse(null).profiles.isEmpty(), "MIG2. lưu/tải lặp lại ổn định; rỗng/null không lỗi");
        DeviceRegistry.Store old2 = DeviceRegistry.parse("GRAPE-DEVICES\t1\nP\tUSB|-|k9\tDETECTED\tfiio-k9pro-ess/usb\tFiiO\tK9 Pro ESS\tK9\tUSB\t-1\t-1\t\t1000\t16,24\n");
        check(old2.profiles.size() == 1 && old2.profiles.get(0).rates.length == 0 && old2.profiles.get(0).catalogId.isEmpty(), "MIG3. hồ sơ 13 trường (trước 2.9.5) vẫn đọc được");
        // --- xác nhận người dùng không cấp xác minh phiên phát
        DeviceRegistry.Store us = new DeviceRegistry.Store();
        DeviceRegistry.Detected ud = new DeviceRegistry.Detected("Yamaha WXC-50", Route.UPNP, -1, -1, true);
        DeviceRegistry.linkCatalog(us, ud, "u", wxc, 1L);
        BitPerfect.Input in = new BitPerfect.Input(); DeviceRegistry.apply(in, ud, DeviceRegistry.builtin(), us);
        check(in.devLevel != BitPerfect.DevLevel.REGISTRY_CONFIG && in.devLevel != BitPerfect.DevLevel.REGISTRY_E2E, "CONF1. xác nhận thiết bị trong catalog không làm phiên phát thành VERIFIED");
    }
}
