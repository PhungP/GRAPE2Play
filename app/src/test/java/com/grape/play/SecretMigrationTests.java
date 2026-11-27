package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Di trú bí mật: chỉ xoá plaintext/.bak sau khi bản mã được GHI, ĐỌC LẠI và GIẢI MÃ ĐỐI CHIẾU đúng; mất khoá không làm mất bản mã. (Keystore thật chỉ kiểm được trên máy/emulator.) */
final class SecretMigrationTests {
    private SecretMigrationTests() {}
    static final Predicate<String> ENC = v -> v.startsWith("enc1:");
    static final Function<String, String> DEC = v -> v.startsWith("enc1:") ? new StringBuilder(v.substring(5)).reverse().toString() : v;     // "mã hoá" giả: đảo chuỗi
    static String enc(String p) { return "enc1:" + new StringBuilder(p).reverse(); }
    static Map<String, String> rec(String id, String p) { Map<String, String> m = new LinkedHashMap<>(); m.put("id", id); m.put("t", "smb"); m.put("n", id); m.put("p", p); return m; }

    static void run() {
        List<Map<String, String>> plain = Arrays.asList(rec("a", "matkhau1"), rec("b", ""), rec("c", "mk3"));
        check(SecretMigration.hasPlain(plain, "p", ENC) && !SecretMigration.hasPlain(Arrays.asList(rec("a", enc("x")), rec("b", "")), "p", ENC), "SM1. phát hiện còn plaintext; rỗng và bản mã không tính");
        Map<String, String> exp = new HashMap<>(); exp.put("a", "matkhau1"); exp.put("c", "mk3");
        List<Map<String, String>> good = Arrays.asList(rec("a", enc("matkhau1")), rec("b", ""), rec("c", enc("mk3")));
        Set<String> none = new HashSet<>();
        check(SecretMigration.verifyStored(good, "id", "p", exp, none, DEC), "SM2. bản đã ghi giải mã ra đúng từng mật khẩu → xác minh đạt (cho phép xoá bản sao lưu)");
        check(!SecretMigration.verifyStored(Arrays.asList(rec("a", enc("SAI")), rec("b", ""), rec("c", enc("mk3"))), "id", "p", exp, none, DEC), "SM3. một mật khẩu giải mã ra sai → KHÔNG đạt (giữ bản sao lưu)");
        check(!SecretMigration.verifyStored(Arrays.asList(rec("a", enc("matkhau1")), rec("b", "")), "id", "p", exp, none, DEC), "SM4. thiếu một nguồn trong dữ liệu đã ghi → KHÔNG đạt");
        check(!SecretMigration.verifyStored(Arrays.asList(rec("a", enc("matkhau1")), rec("b", "x"), rec("c", enc("mk3"))), "id", "p", exp, none, DEC), "SM5. nguồn không có mật khẩu mà bản ghi lại có giá trị → KHÔNG đạt");
        check(!SecretMigration.verifyStored(Arrays.asList(rec("a", ""), rec("b", ""), rec("c", enc("mk3"))), "id", "p", exp, none, DEC), "SM6. mật khẩu bị mất (ghi ra rỗng) → KHÔNG đạt");
        check(!SecretMigration.verifyStored(good, "id", "p", exp, none, v -> { throw new IllegalStateException("Keystore lỗi"); }), "SM7. giải mã ném lỗi (Keystore hỏng) → KHÔNG đạt, không ném ra ngoài");
        check(!SecretMigration.verifyStored(null, "id", "p", exp, none, DEC) && !SecretMigration.verifyStored(Arrays.asList(rec("a", enc("matkhau1")), new HashMap<String, String>()), "id", "p", exp, none, DEC), "SM8. dữ liệu không đọc được / bản ghi thiếu id → KHÔNG đạt");
        // Nguồn đã mất khoá: bản mã giữ nguyên, bỏ qua khi đối chiếu, nhưng vẫn phải có mặt
        Set<String> lost = new HashSet<>(Arrays.asList("c"));
        Map<String, String> exp2 = new HashMap<>(); exp2.put("a", "matkhau1");
        List<Map<String, String>> withLost = Arrays.asList(rec("a", enc("matkhau1")), rec("b", ""), rec("c", "enc1:BLOB-KHONG-GIAI-MA-DUOC"));
        check(SecretMigration.verifyStored(withLost, "id", "p", exp2, lost, v -> v.contains("BLOB") ? null : DEC.apply(v)), "SM9. nguồn mất khoá: bản mã được giữ nguyên và bỏ qua khi đối chiếu; các nguồn khác vẫn xác minh");
        check(!SecretMigration.verifyStored(Arrays.asList(rec("b", ""), rec("c", "enc1:BLOB")), "id", "p", exp2, lost, DEC), "SM10. nguồn có mật khẩu mong đợi (a) biến mất khỏi dữ liệu đã ghi → KHÔNG đạt, dù nguồn mất khoá vẫn còn");
        // Quy trình qua SafeJsonStore: ghi → đọc lại → xác minh → mới xoá .bak; tiến trình dừng giữa chừng
        SafeStoreTests.Mem m = new SafeStoreTests.Mem();
        String plainJson = PersistCodec.encodeRecords(new ArrayList<Map<String, String>>(plain));
        m.m.put("v", plainJson);
        SafeJsonStore s = new SafeJsonStore(m, PersistCodec::isSources, x -> { }, "T"); s.load();
        String newJson = PersistCodec.encodeRecords(new ArrayList<Map<String, String>>(good));
        check(s.write(newJson) && plainJson.equals(m.m.get("v.bak")) && newJson.equals(s.peekMain()), "SM11. bước 1: ghi bản mã; bản plaintext chuyển sang .bak (CHƯA xoá) và đọc lại bản chính khớp");
        // Giả lập dừng giữa chừng ở đây: .bak còn plaintext, bản chính đã mã hoá → lần khởi động sau phát hiện và dọn
        boolean stale = PersistCodec.decodeSources(s.peekBackup()) != null && SecretMigration.hasPlain(PersistCodec.decodeSources(s.peekBackup()), "p", ENC);
        boolean mainOk = SecretMigration.verifyStored(PersistCodec.decodeSources(s.peekMain()), "id", "p", exp, none, DEC);
        check(stale && mainOk, "SM12. dừng dở giữa hai bước: phát hiện .bak còn plaintext VÀ bản chính xác minh đạt (đủ điều kiện dọn)");
        check(s.scrubBackup() && s.peekBackup() == null && newJson.equals(s.peekMain()), "SM13. bước cuối: xoá .bak sau khi đã xác minh; bản chính nguyên vẹn");
        // Xác minh thất bại → không xoá
        SafeStoreTests.Mem m2 = new SafeStoreTests.Mem(); m2.m.put("v", plainJson);
        SafeJsonStore s2 = new SafeJsonStore(m2, PersistCodec::isSources, x -> { }, "T"); s2.load();
        String broken = PersistCodec.encodeRecords(new ArrayList<Map<String, String>>(Arrays.asList(rec("a", enc("SAI")), rec("b", ""), rec("c", enc("mk3")))));
        s2.write(broken);
        boolean verified = SecretMigration.verifyStored(PersistCodec.decodeSources(s2.peekMain()), "id", "p", exp, none, DEC);
        check(!verified && plainJson.equals(s2.peekBackup()), "SM14. bản ghi ra sai: xác minh KHÔNG đạt nên .bak plaintext được GIỮ (còn đường khôi phục)");
        // Kho bị khoá không cho xoá sao lưu
        SafeStoreTests.Mem m3 = new SafeStoreTests.Mem(); m3.m.put("v", "[{\"id\":\"a\""); m3.m.put("v.bak", "giữ lại");
        SafeJsonStore s3 = new SafeJsonStore(m3, PersistCodec::isSources, x -> { }, "T"); s3.load();
        check(!s3.scrubBackup() && "giữ lại".equals(m3.m.get("v.bak")), "SM15. kho đang khoá ghi (dữ liệu hỏng): từ chối xoá sao lưu");
        // Backend lỗi khi xoá: báo false, không ném
        SafeStoreTests.Mem m4 = new SafeStoreTests.Mem(); m4.m.put("v", newJson); m4.m.put("v.bak", plainJson);
        SafeJsonStore s4 = new SafeJsonStore(m4, PersistCodec::isSources, x -> { }, "T"); s4.load(); m4.throwPut = true;
        check(!s4.scrubBackup() && plainJson.equals(m4.m.get("v.bak")), "SM16. xoá sao lưu gặp lỗi ghi: trả false, .bak còn nguyên, không ném ngoại lệ");
    }
}
