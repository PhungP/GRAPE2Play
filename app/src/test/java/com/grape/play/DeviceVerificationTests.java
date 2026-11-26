package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.List;

import com.grape.play.BitPerfect.Route;
import com.grape.play.DeviceCatalog.Catalog;
import com.grape.play.DeviceCatalog.Config;
import com.grape.play.DeviceCatalog.Dsd;
import com.grape.play.DeviceVerification.Applies;
import com.grape.play.DeviceVerification.CommunityReport;
import com.grape.play.DeviceVerification.Confidence;
import com.grape.play.DeviceVerification.Evidence;
import com.grape.play.DeviceVerification.Result;
import com.grape.play.DeviceVerification.Source;
import com.grape.play.DeviceVerification.TestMethod;
import com.grape.play.DeviceVerification.UserVerification;

/** Test nguồn xác minh (Manufacturer/User/Community) và độ chắc chắn kỹ thuật — hai thuộc tính độc lập. */
final class DeviceVerificationTests {
    private DeviceVerificationTests() {}

    static UserVerification uv(Route c, boolean dsd, Dsd mode, int rate, int bits, Result r, TestMethod m, boolean cond, String notes) {
        UserVerification v = new UserVerification();
        v.profileKey = "k1"; v.catalogId = "wiim-ultra"; v.conn = c; v.dsd = dsd; v.dsdMode = mode; v.rate = rate; v.bits = bits; v.result = r; v.method = m;
        v.dspOff = cond; v.volumeFixed = cond; v.resamplingOff = cond; v.notes = notes; v.at = 1_700_000_000_000L;
        return v;
    }
    static CommunityReport cr(String who, Result r, TestMethod m, String ev, String date) {
        CommunityReport c = new CommunityReport();
        c.reporter = who; c.deviceId = "wiim-ultra"; c.conn = Route.USB; c.result = r; c.method = m; c.evidence = ev; c.date = date; c.rate = 96000; c.bits = 24;
        return c;
    }

    static void run() {
        Catalog cat = DeviceCatalogTests.load();
        Config jbl = cat.byId("jbl-l75ms").config(Route.UPNP);
        // --- nguồn tách khỏi độ chắc chắn; nhiều nguồn không ghi đè nhau
        boolean jblUserOnly = jbl.evidence.size() == 1 && jbl.evidence.get(0).source == Source.USER && jbl.evidence.get(0).confidence == Confidence.SUPPORTED;
        check(jblUserOnly && jbl.bestConfidence(Source.MANUFACTURER) == null, "SRC1. JBL L75ms: chỉ có bằng chứng USER (mconnect), không bị gán nhãn Manufacturer");
        Config wd = null; for (Config c : cat.byId("wiim-ultra").configsFor(Route.UPNP)) if (c.dsd == Dsd.DSD_TO_PCM) wd = c;
        check(wd != null && wd.bestConfidence(Source.USER) != null && wd.bestConfidence(Source.MANUFACTURER) != null && wd.evidence.size() == 2, "SRC2. WiiM Ultra DSD→PCM: giữ đủ hai nguồn (Manufacturer FAQ + User), không ghi đè");
        Config wxc = cat.byId("yamaha-wxc-50").config(Route.UPNP);
        check(wxc.bestConfidence(Source.MANUFACTURER) == Confidence.SUPPORTED && wxc.bestConfidence(Source.USER) == null && wxc.bestConfidence(Source.COMMUNITY) == null, "SRC3. WXC-50: Manufacturer + Supported (tài liệu chỉ nêu khả năng); chưa có User/Community");
        boolean noConfirmedBuiltin = true, noCommunity = true; int manu = 0, usr = 0;
        for (DeviceCatalog.Device d : cat.devices) for (Config c : d.configs) for (Evidence e : c.evidence) {
            if (e.confidence == Confidence.CONFIRMED) noConfirmedBuiltin = false;
            if (e.source == Source.COMMUNITY) noCommunity = false;
            if (e.source == Source.MANUFACTURER) manu++; else if (e.source == Source.USER) usr++;
        }
        check(noConfirmedBuiltin && noCommunity && manu > 100 && usr == 2, "SRC4. danh mục tích hợp: không có Confirmed/Community tự gán (Manufacturer " + manu + ", User " + usr + ")");
        Catalog t = DeviceCatalog.parse("GRAPE-CATALOG\t1\tx\t2026-01-01\n"
            + "D\tz\tAcme\tOne\t\tDAC\t\t\t\t\t2026-01-01\t1\n"
            + "C\tz\tUSB\tUAC2\tPCM\t24/192\t\t\tNONE\t\tUNDOCUMENTED\tUNDOCUMENTED\tUNDOCUMENTED\t2\tCAPABILITY_VERIFIED\tDOCUMENTATION\tc\t\t\thttp://e\tn\t2026-01-01\tY\n"
            + "E\tMANUFACTURER\tCONFIRMED\tUSB\t\t2026-01-01\thttp://e\tmanual says so\n"
            + "E\tCOMMUNITY\tSUPPORTED\tUSB\t\t2026-01-01\thttp://x\tclaimed\n"
            + "E\tUSER\tSUPPORTED\tUSB\t\t2026-01-01\t\t\n"
            + "E\n");
        Config tc = t.byId("z").configs.get(0);
        check(tc.evidence.get(0).confidence == Confidence.SUPPORTED, "SRC5. Confirmed bị hạ về Supported khi cấu hình < mức 3 (chỉ tài liệu khả năng)");
        check(tc.evidence.get(1).confidence == Confidence.UNVERIFIED, "SRC6. báo cáo cộng đồng không thể nhúng trực tiếp vào danh mục tích hợp");
        check(tc.evidence.get(2).confidence == Confidence.UNVERIFIED, "SRC7. bằng chứng không có nguồn tham chiếu/ghi chú → Unverified");
        // --- người dùng: độ chắc chắn theo bằng chứng, không theo nguồn
        UserVerification weak = uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.LISTENING, true, "nghe thấy ổn");
        check(DeviceVerification.userConfidence(weak) == Confidence.INCONCLUSIVE, "USR1. chỉ nghe bằng tai: Inconclusive (không chứng minh bit-perfect)");
        UserVerification ind = uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.DEVICE_INDICATOR, true, "");
        check(DeviceVerification.userConfidence(ind) == Confidence.SUPPORTED, "USR2. báo hiệu trên DAC + điều kiện đầy đủ: Supported");
        ind.dspOff = false; check(DeviceVerification.userConfidence(ind) == Confidence.INCONCLUSIVE, "USR2b. thiếu điều kiện (DSP chưa tắt): Inconclusive");
        UserVerification cap = uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.DIGITAL_CAPTURE, true, "bắt S/PDIF bằng bộ ghi, so sánh bit với file gốc: khớp 100%");
        check(DeviceVerification.userConfidence(cap) == Confidence.CONFIRMED, "USR3. bắt luồng số + so sánh bit + mô tả: User Verified đạt Confirmed");
        cap.notes = ""; check(DeviceVerification.userConfidence(cap) == Confidence.SUPPORTED, "USR3b. thiếu mô tả phương pháp: chỉ Supported");
        check(DeviceVerification.userConfidence(uv(Route.ROUTE_UNKNOWN, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.DIGITAL_CAPTURE, true, "x")) == Confidence.UNVERIFIED, "USR4. thiếu kết nối: Unverified");
        check(DeviceVerification.userConfidence(uv(Route.UPNP, true, Dsd.UNDOCUMENTED, 0, 0, Result.PASS, TestMethod.DIGITAL_CAPTURE, true, "xxxxxxxxxxxx")) == Confidence.UNVERIFIED, "USR4b. DSD chưa chọn kiểu Native/DoP: Unverified");
        check(DeviceVerification.userConfidence(uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.INCONCLUSIVE, TestMethod.DIGITAL_CAPTURE, true, "xxxxxxxxxxxx")) == Confidence.INCONCLUSIVE, "USR4c. kết quả không rõ: Inconclusive");
        Config manuSupported = cat.byId("wiim-ultra").config(Route.USB);
        check(manuSupported.bestConfidence(Source.MANUFACTURER) == Confidence.SUPPORTED && DeviceVerification.userConfidence(cap) != Confidence.UNVERIFIED && DeviceVerification.toEvidence(uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.DIGITAL_CAPTURE, true, "bắt luồng số so sánh bit khớp")).confidence == Confidence.CONFIRMED,
                "USR5. Manufacturer chỉ Supported trong khi User có thể Confirmed: nguồn ≠ độ chắc chắn");
        Evidence ev = DeviceVerification.toEvidence(cap);
        check(ev.source == Source.USER && ev.scope.contains("USB") && ev.scope.contains("96000"), "USR6. bằng chứng người dùng ghi rõ phạm vi (kết nối, định dạng)");
        // --- cộng đồng
        List<CommunityReport> two = new ArrayList<>(); two.add(cr("a", Result.PASS, TestMethod.DEVICE_INDICATOR, "đèn 96k sáng", "2026-01-01")); two.add(cr("b", Result.PASS, TestMethod.DEVICE_INDICATOR, "DAC báo 96k 24 bit", "2026-01-02"));
        DeviceVerification.Aggregate a2 = DeviceVerification.aggregate(two);
        check(a2.independent == 2 && !a2.communityVerified() && a2.confidence == Confidence.INCONCLUSIVE, "COM1. 2 báo cáo: chưa đủ tiêu chí, không phải Community Verified");
        two.add(cr("c", Result.PASS, TestMethod.DEVICE_INDICATOR, "xác nhận bằng đèn trên mặt máy", "2026-01-03"));
        check(DeviceVerification.aggregate(two).communityVerified() && DeviceVerification.aggregate(two).confidence == Confidence.SUPPORTED, "COM2. 3 báo cáo độc lập nhất quán: Community Verified (Supported)");
        List<CommunityReport> dup = new ArrayList<>(two); dup.add(cr("a", Result.PASS, TestMethod.DEVICE_INDICATOR, "báo cáo lại lần hai của a", "2026-02-01")); dup.add(cr("d", Result.PASS, TestMethod.DEVICE_INDICATOR, "DAC báo 96k 24 bit!", "2026-01-04"));
        DeviceVerification.Aggregate ad = DeviceVerification.aggregate(dup);
        check(ad.independent == 3 && ad.duplicates == 2, "COM3. cùng người báo cáo lại và bằng chứng sao chép chỉ tính một (độc lập=" + ad.independent + ", trùng=" + ad.duplicates + ")");
        List<CommunityReport> conf = new ArrayList<>(two); conf.add(cr("e", Result.FAIL, TestMethod.DIGITAL_CAPTURE, "bắt được dữ liệu bị đổi tần số", "2026-01-05"));
        DeviceVerification.Aggregate ac = DeviceVerification.aggregate(conf);
        check(ac.conflict && ac.confidence == Confidence.INCONCLUSIVE && !ac.communityVerified(), "COM4. phát hiện mâu thuẫn PASS/FAIL → Inconclusive, không nâng");
        List<CommunityReport> five = new ArrayList<>(); for (int i = 0; i < 5; i++) five.add(cr("u" + i, Result.PASS, i < 3 ? TestMethod.DIGITAL_CAPTURE : TestMethod.DEVICE_INDICATOR, "bằng chứng riêng số " + i, "2026-01-0" + (i + 1)));
        check(DeviceVerification.aggregate(five).confidence == Confidence.CONFIRMED, "COM5. ≥5 độc lập, ≥3 bắt luồng số, nhất quán → Confirmed");
        List<CommunityReport> noEv = new ArrayList<>(); noEv.add(cr("x", Result.PASS, TestMethod.DIGITAL_CAPTURE, "", "2026-01-01")); noEv.add(cr("", Result.PASS, TestMethod.DIGITAL_CAPTURE, "có bằng chứng dài", "2026-01-01")); noEv.add(cr("y", Result.INCONCLUSIVE, TestMethod.LISTENING, "không rõ kết quả", "2026-01-01"));
        DeviceVerification.Aggregate an = DeviceVerification.aggregate(noEv);
        check(an.independent == 0 && an.rejected == 3 && an.confidence == Confidence.UNVERIFIED, "COM6. báo cáo thiếu bằng chứng/người báo cáo/kết quả bị loại");
        check(DeviceVerification.aggregate(new ArrayList<>()).confidence == Confidence.UNVERIFIED, "COM7. không có báo cáo → Unverified");
        String f = "GRAPE-COMMUNITY\t1\nR\tu1\twiim-ultra\tUSB\tUAC2\tfw1\tPCM\tUNDOCUMENTED\t96000\t24\tPASS\tDEVICE_INDICATOR\t2026-01-01\tđèn 96k\nR\tbad\nR\tu2\t\tUSB\n";
        check(DeviceVerification.parseCommunity(f).size() == 1 && DeviceVerification.parseCommunity("rác").isEmpty() && DeviceVerification.parseCommunity(null).isEmpty(), "COM8. đọc tệp báo cáo cộng đồng; dòng hỏng/không tiêu đề bị bỏ qua");
        check(DeviceVerification.groupByCombo(two).size() == 1, "COM9. nhóm theo tổ hợp thiết bị+kết nối+định dạng");
        // --- đối chiếu phiên phát
        UserVerification base = uv(Route.USB, false, Dsd.UNDOCUMENTED, 96000, 24, Result.PASS, TestMethod.DIGITAL_CAPTURE, true, "bắt luồng số so sánh bit khớp");
        DeviceVerification.Combo cb = new DeviceVerification.Combo(); cb.catalogId = "wiim-ultra"; cb.conn = Route.USB; cb.rate = 96000; cb.bits = 24; cb.dspOff = cb.volumeFixed = cb.resamplingOff = true;
        check(DeviceVerification.appliesTo(base, cb).applies == Applies.EXACT, "CMB1. cùng thiết bị, kết nối, định dạng, điều kiện → áp dụng chính xác");
        cb.conn = Route.UPNP; check(DeviceVerification.appliesTo(base, cb).applies == Applies.NONE, "CMB2. xác minh USB không đại diện cho UPnP");
        cb.conn = Route.USB; cb.catalogId = "other"; check(DeviceVerification.appliesTo(base, cb).applies == Applies.NONE, "CMB3. khác thiết bị → không áp dụng");
        cb.catalogId = "wiim-ultra"; cb.rate = 192000; check(DeviceVerification.appliesTo(base, cb).applies == Applies.PARTIAL, "CMB4. khác tần số đã thử → chỉ một phần");
        cb.rate = 96000; cb.dspOff = false; check(DeviceVerification.appliesTo(base, cb).applies == Applies.PARTIAL, "CMB5. DSP đang bật khác điều kiện lúc thử → một phần");
        cb.dspOff = true; base.firmware = "1.0"; check(DeviceVerification.appliesTo(base, cb).applies == Applies.PARTIAL, "CMB6. firmware phiên hiện tại chưa rõ → một phần");
        cb.firmware = "2.0"; check(DeviceVerification.appliesTo(base, cb).applies == Applies.NONE, "CMB7. khác firmware → không áp dụng");
        UserVerification dsdv = uv(Route.UPNP, true, Dsd.NATIVE, 0, 0, Result.PASS, TestMethod.DEVICE_INDICATOR, true, "");
        DeviceVerification.Combo dc = new DeviceVerification.Combo(); dc.catalogId = "wiim-ultra"; dc.conn = Route.UPNP; dc.dsd = true; dc.dsdMode = Dsd.DSD_TO_PCM; dc.dspOff = dc.volumeFixed = dc.resamplingOff = true;
        check(DeviceVerification.appliesTo(dsdv, dc).applies == Applies.NONE, "CMB8. Native DSD đã thử không áp dụng cho phiên DSD→PCM");
        dc.dsdMode = Dsd.NATIVE; check(DeviceVerification.appliesTo(dsdv, dc).applies == Applies.EXACT, "CMB9. Native DSD cùng kiểu → áp dụng");
        // --- lưu bền vững
        UserVerification rt = uv(Route.USB, false, Dsd.UNDOCUMENTED, 44100, 16, Result.FAIL, TestMethod.DEVICE_INDICATOR, true, "ghi chú\ncó tab\tvà dòng");
        rt.software = "mconnect 1.2"; rt.firmware = "v1.2";
        UserVerification back = DeviceVerification.parse(DeviceVerification.serialize(rt));
        check(back != null && back.notes.equals(rt.notes) && back.software.equals("mconnect 1.2") && back.firmware.equals("v1.2") && back.result == Result.FAIL && back.dspOff && back.rate == 44100 && back.at == rt.at, "PER1. User Verified lưu/tải đầy đủ (kể cả tab/xuống dòng)");
        check(DeviceVerification.parse("V\tbroken") == null && DeviceVerification.parse("") == null && DeviceVerification.parse("V" + "\t1".repeat(17)) == null, "PER2. dòng V hỏng bị bỏ qua");
        DeviceRegistry.Store st = new DeviceRegistry.Store();
        DeviceRegistry.Detected det = new DeviceRegistry.Detected("WiiM Ultra", Route.USB, -1, -1, true);
        DeviceRegistry.Profile p = DeviceRegistry.linkCatalog(st, det, "", cat.byId("wiim-ultra"), 5L);
        check(st.verificationsFor(p.key).isEmpty(), "PER3. xác nhận danh tính KHÔNG tạo xác minh khả năng phát");
        rt.profileKey = p.key; st.verifications.add(rt); p.firmware = "v1.2";
        DeviceRegistry.Store re = DeviceRegistry.parse(DeviceRegistry.serialize(st));
        check(re.verificationsFor(p.key).size() == 1 && re.find(p.key).firmware.equals("v1.2") && re.find(p.key).catalogId.equals("wiim-ultra"), "PER4. khôi phục hồ sơ + firmware + xác minh sau khi khởi động lại");
        check(DeviceRegistry.parse("GRAPE-DEVICES\t1\nP\tUSB|-|x\tDETECTED\t\tA\tB\tn\tUSB\t-1\t-1\t\t1\t\t\n").verifications.isEmpty(), "PER5. hồ sơ phiên bản cũ (không có dòng V) vẫn đọc được");
        st.remove(p.key);
        check(st.verifications.isEmpty() && st.profiles.isEmpty(), "PER6. xoá liên kết thiết bị cũng xoá kết quả xác minh của nó");
    }
}
