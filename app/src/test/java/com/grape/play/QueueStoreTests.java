package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test lưu/khôi phục hàng đợi (QueueStore): mã hoá, hỏng/cụt, ghi nguyên tử, đối chiếu bài còn tồn tại. */
final class QueueStoreTests {
    private QueueStoreTests() {}
    static void run() {
        List<String> q = Arrays.asList("/storage/emulated/0/Music/a.flac", "smb://nas/Nhạc Việt/b c.dsf", "/x/dòng\nmới\\lạ\r.mp3", "", "/storage/emulated/0/Music/a.flac");
        String enc = QueueStore.encodeQueue(q);
        List<String> dec = QueueStore.decodeQueue(enc);
        check(q.equals(dec), "QS1. round-trip hàng đợi (Unicode, \\n, \\r, \\, chuỗi rỗng, bài trùng)");
        check(QueueStore.decodeQueue(QueueStore.encodeQueue(new ArrayList<String>())).isEmpty(), "QS2. hàng đợi rỗng round-trip");
        check(QueueStore.decodeQueue(enc.substring(0, enc.length() - 6)) == null, "QS3. file cụt (mất END) → null");
        check(QueueStore.decodeQueue(enc.replace("b c.dsf", "b d.dsf")) == null, "QS4. sai CRC → null");
        check(QueueStore.decodeQueue(enc.replace("\n5\n", "\n6\n")) == null, "QS5. sai số lượng → null");
        check(QueueStore.decodeQueue(null) == null && QueueStore.decodeQueue("") == null && QueueStore.decodeQueue("rác\nrác\n") == null && QueueStore.decodeQueue(enc.replace("GRQ1", "GRQ9")) == null, "QS6. rác/null/sai phiên bản → null");
        boolean allCut = true;
        for (int i = 0; i < enc.length(); i++) if (QueueStore.decodeQueue(enc.substring(0, i)) != null) allCut = false;
        check(allCut, "QS7. mọi điểm cắt cụt của file đều bị từ chối");

        QueueStore.State st = new QueueStore.State(3, 123456L, true, 1, 5, QueueStore.crc(q));
        QueueStore.State d = QueueStore.decodeState(QueueStore.encodeState(st));
        check(d != null && d.index == 3 && d.positionMs == 123456L && d.shuffle && d.repeat == 1 && d.queueCount == 5 && d.queueCrc == st.queueCrc, "QS8. round-trip trạng thái");
        String se = QueueStore.encodeState(st);
        check(QueueStore.decodeState(se.substring(0, se.length() - 4)) == null && QueueStore.decodeState("") == null && QueueStore.decodeState(se.replace("GRS1", "GRS2")) == null, "QS9. trạng thái cụt/rác/sai phiên bản → null");
        check(QueueStore.decodeState(se.replace("\n123456\n", "\n-5\n")) == null && QueueStore.decodeState(se.replace("\n1\n5\n", "\n7\n5\n")) == null, "QS10. giá trị ngoài phạm vi (vị trí âm, repeat > 2) → null");
        boolean cutOk = true;
        for (int i = 0; i < se.length(); i++) if (QueueStore.decodeState(se.substring(0, i)) != null) cutOk = false;
        check(cutOk, "QS11. mọi điểm cắt cụt của trạng thái đều bị từ chối");

        // Đối chiếu bài còn tồn tại
        QueueStore.Resolved r = QueueStore.resolve(new boolean[]{true, true, true, true}, 2, 5000);
        check(r != null && r.kept.length == 4 && r.index == 2 && r.positionMs == 5000 && !r.currentMissing, "QS12. tất cả còn → giữ nguyên chỉ số và vị trí");
        r = QueueStore.resolve(new boolean[]{true, false, true, true}, 2, 5000);
        check(r.kept.length == 3 && r.index == 1 && r.positionMs == 5000 && r.kept[r.index] == 2, "QS13. bài trước bài đang nghe bị mất → chỉ số dịch đúng, vẫn đúng bài, giữ vị trí");
        r = QueueStore.resolve(new boolean[]{true, true, false, true}, 2, 5000);
        check(r.index == 2 && r.kept[r.index] == 3 && r.positionMs == 0 && r.currentMissing, "QS14. bài đang nghe mất → bài còn lại kế tiếp, vị trí 0");
        r = QueueStore.resolve(new boolean[]{true, true, false, false}, 3, 5000);
        check(r.index == 1 && r.kept[r.index] == 1 && r.positionMs == 0 && r.currentMissing, "QS15. bài đang nghe mất và không còn bài sau → bài còn lại cuối");
        check(QueueStore.resolve(new boolean[]{false, false}, 0, 0) == null && QueueStore.resolve(new boolean[0], 0, 0) == null, "QS16. không còn bài nào / rỗng → null (không khôi phục)");
        r = QueueStore.resolve(new boolean[]{true, true}, 99, -5);
        check(r.index == 1 && r.positionMs == 0 && !r.currentMissing, "QS17. chỉ số ngoài phạm vi được kẹp");
        boolean inv = true;
        java.util.Random rnd = new java.util.Random(7);
        for (int it = 0; it < 3000; it++) {
            int n = 1 + rnd.nextInt(12); boolean[] p = new boolean[n]; int k = 0;
            for (int i = 0; i < n; i++) { p[i] = rnd.nextInt(3) != 0; if (p[i]) k++; }
            int idx = rnd.nextInt(n + 3) - 1; long pos = rnd.nextInt(100000) - 50;
            QueueStore.Resolved x = QueueStore.resolve(p, idx, pos);
            if (k == 0) { if (x != null) inv = false; continue; }
            if (x == null || x.kept.length != k || x.index < 0 || x.index >= k || x.positionMs < 0) { inv = false; continue; }
            for (int i = 1; i < k; i++) if (x.kept[i] <= x.kept[i - 1]) inv = false;       // thứ tự được giữ, không trùng
            for (int i : x.kept) if (!p[i]) inv = false;                                    // chỉ giữ bài còn tồn tại
            int c = Math.max(0, Math.min(idx, n - 1));
            if (p[c] && (x.kept[x.index] != c || x.currentMissing)) inv = false;            // bài còn → đúng bài đó
            if (!p[c] && (x.positionMs != 0 || !x.currentMissing)) inv = false;
        }
        check(inv, "QS18. ngẫu nhiên 3000 ca: thứ tự giữ nguyên, chỉ bài tồn tại, chỉ số hợp lệ, đúng bài khi còn");

        // Tệp: ghi nguyên tử, ghi đè, ghi lỗi không làm hỏng bản cũ
        try {
            File dir = Files.createTempDirectory("grq").toFile();
            File f = new File(dir, "queue.grq");
            check(QueueStore.read(f) == null, "QS19. file chưa có → read null");
            QueueStore.writeAtomic(f, enc);
            check(q.equals(QueueStore.decodeQueue(QueueStore.read(f))) && !new File(dir, "queue.grq.tmp").exists(), "QS20. ghi rồi đọc lại đúng, không để lại file tạm");
            List<String> q2 = Arrays.asList("/a", "/b");
            QueueStore.writeAtomic(f, QueueStore.encodeQueue(q2));
            check(q2.equals(QueueStore.decodeQueue(QueueStore.read(f))), "QS21. ghi đè bản cũ");
            // mô phỏng crash giữa chừng: file tạm dở dang không ảnh hưởng bản chính
            Files.write(new File(dir, "queue.grq.tmp").toPath(), "GRQ1\n9\nabc".getBytes(StandardCharsets.UTF_8));
            check(q2.equals(QueueStore.decodeQueue(QueueStore.read(f))), "QS22. file tạm dở dang không làm hỏng bản chính");
            QueueStore.writeAtomic(f, QueueStore.encodeQueue(q));
            check(q.equals(QueueStore.decodeQueue(QueueStore.read(f))), "QS23. ghi tiếp sau file tạm dở dang vẫn đúng");
            boolean threw = false;
            try { QueueStore.writeAtomic(new File(dir, "khong/co/thu/muc/x.grq"), "x"); } catch (java.io.IOException e) { threw = true; }
            check(threw, "QS24. thư mục đích không tồn tại → ném IOException (người gọi bắt, không crash)");
            // hàng đợi lớn
            List<String> big = new ArrayList<>();
            for (int i = 0; i < 50000; i++) big.add("smb://nas/Nhạc/Album " + (i / 12) + "/" + i + ".flac");
            QueueStore.writeAtomic(f, QueueStore.encodeQueue(big));
            check(big.equals(QueueStore.decodeQueue(QueueStore.read(f))), "QS25. hàng đợi 50.000 bài round-trip");
            for (File x : dir.listFiles()) x.delete();
            dir.delete();
        } catch (Exception e) { check(false, "QS-IO. lỗi không mong đợi: " + e); }
    }
}
