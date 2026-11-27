package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test chính sách ghi/khôi phục hàng đợi (QueueSession) và các tình huống vòng đời cùng QueueStore. */
final class QueueSessionTests {
    private QueueSessionTests() {}
    static void run() {
        // Khởi động: hàng đợi rỗng (emit khởi tạo của Flow) không bao giờ được ghi
        QueueSession s = new QueueSession();
        s.observe(false);
        check(s.plan(new Object(), 0, 0).action == QueueSession.Action.SKIP && s.canRestore(), "QSN1. emit khởi tạo (rỗng) → không ghi, vẫn được khôi phục");
        for (int i = 0; i < 5; i++) s.observe(false);
        check(s.plan(new Object(), 0, 0).action == QueueSession.Action.SKIP, "QSN2. rỗng lặp lại lúc khởi động/đang chờ thư viện → không ghi");

        // Người dùng phát trong lúc đang chờ thư viện → không được khôi phục đè lên
        s = new QueueSession();
        Object q1 = new Object();
        s.observe(true);
        check(!s.canRestore(), "QSN3. người dùng đã có hàng đợi → không khôi phục");
        QueueSession.Plan p = s.plan(q1, 3, 1);
        check(p.action == QueueSession.Action.WRITE && !p.useBaseline && p.index == 1, "QSN4. hàng đợi có bài → ghi, chỉ số giữ nguyên");

        // Xoá hàng đợi → ghi rỗng tường minh đúng một lần; sau đó không khôi phục bản cũ
        p = s.plan(new Object(), 0, 0);
        check(p.action == QueueSession.Action.WRITE_EMPTY, "QSN5. xoá hàng đợi sau khi từng có bài → ghi trạng thái RỖNG tường minh");
        check(s.plan(new Object(), 0, 0).action == QueueSession.Action.SKIP, "QSN6. rỗng tiếp theo không ghi lặp");
        check(!s.canRestore(), "QSN7. sau khi xoá, bản cũ KHÔNG được khôi phục (kể cả khi việc khôi phục đang chờ thư viện)");
        s.observe(true);
        check(s.plan(new Object(), 2, 0).action == QueueSession.Action.WRITE, "QSN8. phát lại sau khi xoá → ghi bình thường");
        check(s.plan(new Object(), 0, 0).action == QueueSession.Action.WRITE_EMPTY, "QSN9. xoá lần nữa → lại ghi rỗng");

        // Có bài rồi xoá trong chớp mắt (trước debounce): observe vẫn ghi nhận
        s = new QueueSession();
        s.observe(true); s.observe(false);
        check(s.plan(new Object(), 0, 0).action == QueueSession.Action.WRITE_EMPTY, "QSN10. có bài rồi xoá trước khi debounce → vẫn ghi rỗng");

        // Khôi phục thiếu bài (nguồn tạm vắng): giữ danh sách đầy đủ cho tới khi hàng đợi bị sửa
        s = new QueueSession();
        List<String> full = Arrays.asList("local\t/a", "smb1\t/b", "local\t/c", "smb1\t/d");
        int[] kept = {0, 2};
        Object restoredQ = new Object();
        s.restored(restoredQ, full, kept);
        p = s.plan(restoredQ, 2, 1);
        check(p.action == QueueSession.Action.WRITE && p.useBaseline && p.baselineKeys == full && p.index == 2, "QSN11. sau khôi phục thiếu bài: ghi danh sách ĐẦY ĐỦ, chỉ số ánh xạ về hệ đầy đủ");
        p = s.plan(restoredQ, 2, 0);
        check(p.useBaseline && p.index == 0, "QSN12. chỉ số 0 → bài đầu tiên của danh sách đầy đủ");
        p = s.plan(restoredQ, 2, 99);
        check(p.useBaseline && p.index == 2, "QSN13. chỉ số ngoài phạm vi được kẹp");
        check(!s.canRestore(), "QSN14. đã khôi phục → không khôi phục lần nữa");
        p = s.plan(new Object(), 3, 1);
        check(!p.useBaseline && p.index == 1, "QSN15. hàng đợi bị sửa (đối tượng khác) → bản hiện tại là chuẩn, bỏ danh sách đầy đủ");
        p = s.plan(restoredQ, 2, 1);
        check(!p.useBaseline, "QSN16. đã bỏ danh sách đầy đủ thì không quay lại");

        s = new QueueSession();
        s.restored(restoredQ, full, new int[]{0, 1, 2, 3});
        check(!s.plan(restoredQ, 4, 3).useBaseline && s.plan(restoredQ, 4, 3).index == 3, "QSN17. khôi phục đủ bài → ghi như thường");

        // Nguồn trùng đường dẫn: danh tính gồm cả nguồn
        List<String> k = Arrays.asList("gdrive1\tID42", "gdrive2\tID42", "local\t/x/ID42");
        check(QueueStore.crc(k) != QueueStore.crc(Arrays.asList("gdrive1\tID42", "gdrive1\tID42", "local\t/x/ID42")), "QSN18. cùng path khác nguồn cho CRC khác nhau");
        check(k.equals(QueueStore.decodeQueue(QueueStore.encodeQueue(k))), "QSN19. khoá nguồn+đường dẫn (có TAB) round-trip");
        // Chỉ nguồn 'gdrive1' có trong thư viện: bài của gdrive2 không bị nhầm sang gdrive1
        java.util.Set<String> lib = new java.util.HashSet<>(Arrays.asList("gdrive1\tID42", "local\t/x/ID42"));
        boolean[] pres = new boolean[k.size()];
        for (int i = 0; i < pres.length; i++) pres[i] = lib.contains(k.get(i));
        QueueStore.Resolved r = QueueStore.resolve(pres, 1, 1000);
        check(r.kept.length == 2 && r.kept[0] == 0 && r.kept[1] == 2 && r.currentMissing && r.positionMs == 0, "QSN20. nguồn thiếu: đúng bài của nguồn đó bị coi là vắng, không nhầm nguồn khác");

        // Trạng thái không khớp hàng đợi / hỏng
        List<String> q = Arrays.asList("local\t/1", "local\t/2", "local\t/3");
        QueueStore.State good = new QueueStore.State(2, 777L, true, 1, 3, QueueStore.crc(q));
        check(QueueStore.reconcile(good, q) == good, "QSN21. trạng thái khớp → giữ nguyên");
        QueueStore.State stale = new QueueStore.State(2, 777L, true, 0, 3, QueueStore.crc(q) ^ 1);
        QueueStore.State rc = QueueStore.reconcile(stale, q);
        check(rc.index == 0 && rc.positionMs == 0 && rc.shuffle && rc.repeat == 0 && rc.queueCount == 3, "QSN22. CRC lệch → về bài đầu, vị trí 0, giữ shuffle/repeat");
        rc = QueueStore.reconcile(new QueueStore.State(2, 5L, false, 1, 9, QueueStore.crc(q)), q);
        check(rc.index == 0 && rc.positionMs == 0, "QSN23. số lượng lệch → về bài đầu");
        rc = QueueStore.reconcile(null, q);
        check(rc.index == 0 && rc.positionMs == 0 && !rc.shuffle && rc.repeat == 2 && rc.queueCount == 3, "QSN24. state hỏng/thiếu → mặc định (repeat ALL), hàng đợi vẫn khôi phục");
        rc = QueueStore.reconcile(new QueueStore.State(3, 5L, false, 1, 3, QueueStore.crc(q)), q);
        check(rc.index == 0, "QSN25. chỉ số ≥ số bài → về bài đầu");

        // Surrogate lẻ không làm hỏng CRC của cả file
        List<String> odd = Arrays.asList("local\t/ok.flac", "local\t/lỗi\uD800.flac", "local\t/\uDC00x");
        List<String> back = QueueStore.decodeQueue(QueueStore.encodeQueue(odd));
        check(back != null && back.size() == 3 && back.get(0).equals(odd.get(0)), "QSN26. surrogate lẻ không làm file bị từ chối (chuẩn hoá thành '?')");
        check(QueueStore.crc(odd) == QueueStore.crc(back), "QSN27. CRC ổn định qua round-trip khi có surrogate lẻ");

        // Mô phỏng vòng đời: lưu → 'kill' → khởi động → khôi phục → xoá → khởi động lại
        try {
            java.io.File dir = java.nio.file.Files.createTempDirectory("grs").toFile();
            java.io.File qf = new java.io.File(dir, "queue.grq"), sf = new java.io.File(dir, "queue_state.grs");
            QueueStore.writeAtomic(qf, QueueStore.encodeQueue(full));
            QueueStore.writeAtomic(sf, QueueStore.encodeState(new QueueStore.State(3, 9000L, false, 2, 4, QueueStore.crc(full))));
            List<String> lk = QueueStore.decodeQueue(QueueStore.read(qf));
            QueueStore.State ls = QueueStore.reconcile(QueueStore.decodeState(QueueStore.read(sf)), lk);
            check(lk.equals(full) && ls.index == 3 && ls.positionMs == 9000L, "QSN28. khởi động lại: đọc đúng hàng đợi, chỉ số, vị trí");
            QueueStore.writeAtomic(qf, QueueStore.encodeQueue(new ArrayList<String>()));
            sf.delete();
            List<String> after = QueueStore.decodeQueue(QueueStore.read(qf));
            check(after != null && after.isEmpty(), "QSN29. xoá hàng đợi rồi khởi động lại: file rỗng hợp lệ → không có gì để khôi phục (không hiện lại bản cũ)");
            // state cũ còn sót (xoá file state thất bại) + queue rỗng: reconcile không được tạo ra hàng đợi
            QueueStore.writeAtomic(sf, QueueStore.encodeState(new QueueStore.State(3, 9000L, false, 2, 4, QueueStore.crc(full))));
            check(QueueStore.decodeQueue(QueueStore.read(qf)).isEmpty(), "QSN30. state cũ sót lại không làm hàng đợi rỗng sống lại");
            for (java.io.File x : dir.listFiles()) x.delete();
            dir.delete();
        } catch (Exception e) { check(false, "QSN-IO. lỗi không mong đợi: " + e); }
    }
}
