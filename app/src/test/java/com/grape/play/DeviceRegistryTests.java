package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.List;

import com.grape.play.BitPerfect.DevLevel;
import com.grape.play.BitPerfect.Route;
import com.grape.play.DeviceRegistry.Ask;
import com.grape.play.DeviceRegistry.Detected;
import com.grape.play.DeviceRegistry.Entry;
import com.grape.play.DeviceRegistry.Match;
import com.grape.play.DeviceRegistry.Store;

/** Test Device Registry / User Device Profile (DeviceRegistry.java). Chỉ kiểm LOGIC thuần; không có thiết bị thật. */
final class DeviceRegistryTests {
    private DeviceRegistryTests() {}

    static Detected dev(String name, Route conn, int vid, int pid, boolean active) { return new Detected(name, conn, vid, pid, active); }

    static void run() {
        List<Entry> reg = DeviceRegistry.builtin();
        Store st = new Store();

        // 1. Nhận diện thiết bị có trong danh mục
        Detected k9 = dev("FiiO K9 Pro ESS", Route.USB, -1, -1, true);
        Match m = DeviceRegistry.match(k9, reg);
        check(m.entry != null && m.entry.id.equals("fiio-k9pro-ess/usb") && m.quality == DeviceRegistry.NAME_ONLY, "REG1. FiiO K9 Pro ESS qua USB khớp bản ghi USB");
        check(m.otherConnections.size() == 2, "REG1b. ghi nhận hai phương thức khác của cùng model (chỉ để thông tin)");
        check(DeviceRegistry.match(dev("K9 Pro ESS", Route.USB, -1, -1, true), reg).entry != null, "REG1c. tên rút gọn K9 Pro ESS vẫn khớp");
        // 2. Thiết bị chưa có trong danh mục
        Detected unk = dev("Acme DAC 3000", Route.USB, 0x1234, 0x0001, true);
        check(DeviceRegistry.match(unk, reg).entry == null, "REG2. thiết bị lạ không khớp");
        check(DeviceRegistry.decide(unk, DeviceRegistry.match(unk, reg), st) == Ask.UNKNOWN_DEVICE, "REG2b. thiết bị lạ → hỏi khai báo");
        check(DeviceRegistry.match(dev("FiiO K9 Pro", Route.USB, -1, -1, true), reg).entry == null, "REG2c. K9 Pro (không ESS) không bị nhận nhầm là K9 Pro ESS");
        check(DeviceRegistry.match(dev("", Route.USB, -1, -1, true), reg).entry == null && DeviceRegistry.match(null, reg).entry == null, "REG2d. tên rỗng / null → không khớp, không lỗi");
        // 3. Trùng tên nhưng khác hardware ID
        List<Entry> reg2 = new ArrayList<>(reg);
        reg2.add(new Entry("test/usb", "Test", "Same Name", new String[]{"samename"}, 0x2972, 0x0001, Route.USB, new int[]{16}, new int[]{44100}, "", "", "", "test", DevLevel.REGISTRY_CONFIG, "2026-01-01", true));
        Match same = DeviceRegistry.match(dev("Same Name", Route.USB, 0x2972, 0x0001, true), reg2);
        check(same.entry != null && same.quality == DeviceRegistry.EXACT_ID, "REG3. cùng tên + đúng VID/PID → EXACT_ID");
        check(DeviceRegistry.match(dev("Same Name", Route.USB, 0x9999, 0x0002, true), reg2).entry == null, "REG3b. cùng tên khác VID/PID → KHÔNG khớp");
        Match noId = DeviceRegistry.match(dev("Same Name", Route.USB, -1, -1, true), reg2);
        check(noId.entry != null && noId.quality == DeviceRegistry.NAME_ONLY, "REG3c. không có VID/PID: chỉ khớp theo tên (mức NAME_ONLY, cần người dùng xác nhận)");
        check(DeviceRegistry.match(dev("Tên khác hoàn toàn", Route.USB, 0x2972, 0x0001, true), reg2).quality == DeviceRegistry.EXACT_ID, "REG3d. đúng VID/PID dù tên khác (firmware đổi tên) → EXACT_ID");
        check(!DeviceRegistry.keyOf(dev("Same Name", Route.USB, 0x2972, 1, true)).equals(DeviceRegistry.keyOf(dev("Same Name", Route.USB, 0x9999, 2, true))), "REG3e. khoá hồ sơ khác nhau cho hai phần cứng cùng tên");
        // 4. Nhiều phương thức kết nối: không suy rộng
        Detected opt = dev("FiiO K9 Pro ESS", Route.SPDIF, -1, -1, true);
        Match mo = DeviceRegistry.match(opt, reg);
        check(mo.entry != null && mo.entry.conn == Route.SPDIF && mo.entry.level == DevLevel.REGISTRY_UNVERIFIED, "REG4. S/PDIF khớp bản ghi S/PDIF mức 'chưa có căn cứ'");
        Detected bt = dev("FiiO K9 Pro ESS", Route.BLUETOOTH, -1, -1, true);
        check(DeviceRegistry.match(bt, reg).entry.level == DevLevel.REGISTRY_UNVERIFIED, "REG4b. Bluetooth không mặc nhiên bit-perfect");
        check(DeviceRegistry.match(dev("FiiO K9 Pro ESS", Route.HDMI, -1, -1, true), reg).entry == null, "REG4c. HDMI không có bản ghi → không khớp (không mượn kết quả USB)");
        // 5. Người dùng xác nhận đúng
        check(DeviceRegistry.decide(k9, m, st) == Ask.REGISTRY_MATCH, "REG5. thiết bị khớp danh mục, chưa lưu → hỏi xác nhận");
        DeviceRegistry.Profile p = DeviceRegistry.confirm(st, k9, m, 1000L);
        check(p.entryId.equals("fiio-k9pro-ess/usb") && p.conn == Route.USB && p.source.equals(DeviceRegistry.SRC_DETECTED), "REG5b. hồ sơ liên kết bản ghi, ghi đúng phương thức USB");
        // 10. Không hỏi lặp
        check(DeviceRegistry.decide(k9, m, st) == Ask.NONE, "REG10. đã xác nhận → không hỏi lại");
        check(DeviceRegistry.decide(dev("fiio k9 pro ess", Route.USB, -1, -1, false), m, st) == Ask.NONE, "REG10b. khác hoa/thường, không phải active → vẫn không hỏi lại");
        // resolve + apply
        BitPerfect.Input in = new BitPerfect.Input();
        DeviceRegistry.apply(in, k9, reg, st);
        check(in.devLevel == DevLevel.REGISTRY_CONFIG && in.devConn == Route.USB && in.devIsActive && in.devRates.length == 8 && in.devBits.length == 3, "REG5c. apply: mức CONFIG, đúng USB, đang active, thông số lấy từ danh mục");
        BitPerfect.Input in2 = new BitPerfect.Input();
        DeviceRegistry.apply(in2, dev("FiiO K9 Pro ESS", Route.USB, -1, -1, false), reg, st);
        check(in2.devLevel == DevLevel.REGISTRY_CONFIG && !in2.devIsActive, "REG5d. thiết bị chỉ được chọn/kết nối (activeKnown=false) → devIsActive=false");
        // 6. Người dùng xác nhận sai
        Store st2 = new Store();
        DeviceRegistry.reject(st2, k9, m);
        check(st2.profiles.isEmpty(), "REG6. 'Không đúng thiết bị': không tự đăng ký");
        check(DeviceRegistry.decide(k9, m, st2) == Ask.NONE, "REG6b. không hỏi lại cùng bản ghi sau khi từ chối");
        BitPerfect.Input in3 = new BitPerfect.Input(); DeviceRegistry.apply(in3, k9, reg, st2);
        check(in3.devLevel == DevLevel.NONE, "REG6c. bị từ chối → không gán thông tin model nào");
        DeviceRegistry.Profile d = DeviceRegistry.declare(st2, k9, "Hãng X", "Model Y", "ghi chú", new int[]{16, 24}, new int[]{44100, 96000}, 5L);
        BitPerfect.Input in4 = new BitPerfect.Input(); DeviceRegistry.apply(in4, k9, reg, st2);
        check(d.source.equals(DeviceRegistry.SRC_USER) && in4.devLevel == DevLevel.USER_DECLARED && in4.devRates.length == 0 && in4.devBits.length == 0,
                "REG6d. khai báo lại: USER_DECLARED, thông số tự khai KHÔNG đi vào đánh giá, không ghi đè danh mục");
        // người dùng khai báo không cấp Verified
        BitPerfect.Input pb = BitPerfectTests.cfg(24, 96000); pb.devLevel = in4.devLevel; pb.devRates = in4.devRates; pb.devBits = in4.devBits;
        check(BitPerfect.evaluate(pb).status != BitPerfect.Status.VERIFIED, "REG6e. thiết bị tự khai báo không bao giờ VERIFIED");
        // 7. Lưu và tải lại
        Store st3 = new Store();
        DeviceRegistry.confirm(st3, k9, m, 42L);
        DeviceRegistry.declare(st3, unk, "Acme", "DAC\t3000\nX", "dòng 1\ndòng 2\\ tab\tx", new int[]{16, 24}, new int[]{44100}, 7L);
        DeviceRegistry.reject(st3, opt, mo); DeviceRegistry.dismiss(st3, dev("Loa lạ", Route.HDMI, -1, -1, false));
        String text = DeviceRegistry.serialize(st3);
        Store back = DeviceRegistry.parse(text);
        check(back.profiles.size() == 2 && back.rejected.size() == 1 && back.dismissed.size() == 1, "REG7. lưu/tải: đủ hồ sơ, từ chối, bỏ qua");
        DeviceRegistry.Profile bp = back.find(DeviceRegistry.keyOf(unk));
        check(bp != null && bp.manufacturer.equals("Acme") && bp.model.equals("DAC\t3000\nX") && bp.notes.equals("dòng 1\ndòng 2\\ tab\tx") && bp.bits.length == 2 && bp.vid == 0x1234 && bp.confirmedAt == 7L, "REG7b. ký tự đặc biệt/ghi chú được bảo toàn");
        check(DeviceRegistry.serialize(back).equals(text), "REG7c. serialize(parse(x)) == x");
        check(DeviceRegistry.parse(null).profiles.isEmpty() && DeviceRegistry.parse("").profiles.isEmpty() && DeviceRegistry.parse("rác\nP\tx").profiles.isEmpty(), "REG7d. dữ liệu hỏng/rỗng → Store rỗng, không ném lỗi");
        String corrupt = text + "P\tkey\tUSER\t\tA\tB\tC\tKHONG_CO_ROUTE\t1\t2\tn\t3\t\t\nP\tkey2\tUSER\n";
        check(DeviceRegistry.parse(corrupt).profiles.size() == 2, "REG7e. dòng hỏng (route sai, thiếu cột) bị bỏ qua, dòng tốt còn nguyên");
        // 8. Thiết bị bị ngắt kết nối: hồ sơ còn nhưng không áp dụng (không active)
        BitPerfect.Input gone = BitPerfectTests.cfg(24, 96000); gone.devIsActive = false; gone.route = Route.ROUTE_UNKNOWN;
        check(BitPerfect.evaluate(gone).status == BitPerfect.Status.NOT_VERIFIED && st.find(DeviceRegistry.keyOf(k9)) != null, "REG8. ngắt kết nối: hồ sơ giữ nguyên, phiên phát không được Verified");
        // 9. Đổi phương thức kết nối
        check(DeviceRegistry.decide(opt, mo, st) == Ask.REGISTRY_MATCH, "REG9. cùng thiết bị chuyển USB → S/PDIF: hỏi xác nhận lại (hồ sơ USB không suy rộng)");
        BitPerfect.Input in5 = new BitPerfect.Input(); DeviceRegistry.apply(in5, opt, reg, st);
        check(in5.devLevel == DevLevel.NONE, "REG9b. chưa xác nhận S/PDIF → không có mức xác minh dù đã xác nhận USB");
        DeviceRegistry.confirm(st, opt, mo, 2000L);
        BitPerfect.Input in6 = new BitPerfect.Input(); DeviceRegistry.apply(in6, opt, reg, st);
        check(in6.devLevel == DevLevel.REGISTRY_UNVERIFIED && in6.devConn == Route.SPDIF, "REG9c. sau khi xác nhận S/PDIF: mức 'chưa có căn cứ'");
        BitPerfect.Input pc = BitPerfectTests.cfg(16, 44100); pc.route = Route.SPDIF; pc.devConn = Route.SPDIF; pc.devLevel = in6.devLevel; pc.devRates = new int[]{44100}; pc.devBits = new int[]{16};
        check(BitPerfect.evaluate(pc).status == BitPerfect.Status.NOT_VERIFIED, "REG9d. bản ghi S/PDIF 'chưa có căn cứ' không cấp Verified");
        // Hồ sơ cũ tham chiếu bản ghi đã biến mất
        Store st4 = new Store(); DeviceRegistry.Profile ghost = DeviceRegistry.confirm(st4, k9, m, 1L); ghost.entryId = "khong-ton-tai";
        check(DeviceRegistry.resolve(k9, reg, st4).level == DevLevel.USER_DECLARED, "REG9e. bản ghi danh mục không còn tồn tại → không giữ mức xác minh cũ");
        // Không hỏi cho loa/mặc định/UPnP/Cast
        check(DeviceRegistry.decide(dev("Loa TV", Route.SPEAKER, -1, -1, true), new Match(), new Store()) == Ask.NONE
                && DeviceRegistry.decide(dev("WiiM", Route.UPNP, -1, -1, true), new Match(), new Store()) == Ask.NONE
                && DeviceRegistry.decide(dev("Cast", Route.CAST, -1, -1, true), new Match(), new Store()) == Ask.NONE
                && DeviceRegistry.decide(dev("x", Route.ROUTE_UNKNOWN, -1, -1, false), new Match(), new Store()) == Ask.NONE, "REG11. loa/mặc định/UPnP/Cast không bị hỏi xác nhận");
        // Thiết bị lạ: bỏ qua rồi không hỏi lại; xoá hồ sơ thì hỏi lại được
        Store st5 = new Store(); Match mu = DeviceRegistry.match(unk, reg);
        DeviceRegistry.dismiss(st5, unk);
        check(DeviceRegistry.decide(unk, mu, st5) == Ask.NONE, "REG12. đã bỏ qua thiết bị lạ → không hỏi lại");
        DeviceRegistry.declare(st5, unk, "Acme", "3000", "", null, null, 1L);
        check(st5.dismissed.isEmpty() && DeviceRegistry.decide(unk, mu, st5) == Ask.NONE, "REG12b. sau khi khai báo: không còn trong danh sách bỏ qua, không hỏi lại");
        check(st5.remove(DeviceRegistry.keyOf(unk)) && DeviceRegistry.decide(unk, mu, st5) == Ask.UNKNOWN_DEVICE, "REG12c. xoá khỏi hồ sơ cá nhân → lần sau hỏi lại");
        // Danh mục: mọi bản ghi có nguồn + ngày + mức hợp lệ; chỉ USB có mức CONFIG
        boolean okReg = !reg.isEmpty();
        for (Entry e : reg) if (e.source.isEmpty() || e.updated.isEmpty() || e.id.isEmpty() || e.nameKeys.length == 0 || (e.level != DevLevel.REGISTRY_CONFIG && e.level != DevLevel.REGISTRY_UNVERIFIED)) okReg = false;
        check(okReg, "REG13. mọi bản ghi tích hợp có nguồn xác minh, ngày cập nhật, mức hợp lệ");
        check(DeviceRegistry.byId(reg, "fiio-k9pro-ess/usb").level == DevLevel.REGISTRY_CONFIG && DeviceRegistry.byId(reg, "fiio-k9pro-ess/bluetooth").level == DevLevel.REGISTRY_UNVERIFIED, "REG13b. USB mức CONFIG; Bluetooth chưa có căn cứ");
        // Tích hợp đầu-cuối logic: hồ sơ xác nhận → phiên phát Verified – Configuration; đổi sang S/PDIF → không
        BitPerfect.Input e2e = BitPerfectTests.base(Route.USB, 24, 96000, true);
        DeviceRegistry.apply(e2e, k9, reg, st);
        BitPerfect.Result res = BitPerfect.evaluate(e2e);
        check(res.status == BitPerfect.Status.VERIFIED && res.level == BitPerfect.VerLevel.CONFIGURATION, "REG14. hồ sơ xác nhận + route USB đã biết + định dạng trong danh mục → Verified – Configuration");
        e2e.route = Route.ROUTE_UNKNOWN;
        check(BitPerfect.evaluate(e2e).status == BitPerfect.Status.NOT_VERIFIED, "REG14b. cùng hồ sơ nhưng Android không xác định route → NOT_VERIFIED (Device Verification độc lập với Playback Status)");
        AudioCoreTests.info.add("Device Registry: " + reg.size() + " bản ghi tích hợp (chỉ FiiO K9 Pro ESS theo thông số nhà sản xuất; chưa có thiết bị thật nào được kiểm thử).");
    }
}
