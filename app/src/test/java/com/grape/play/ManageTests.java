package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Phase 3: thao tác hàng đợi (QueueOps), chỉnh sửa playlist (PlaylistOps), lịch sử nghe (HistoryLog). */
final class ManageTests {
    private ManageTests() {}
    static List<String> apply(List<String> q, int[] kept) { List<String> o = new ArrayList<>(); for (int k : kept) o.add(q.get(k)); return o; }

    static void run() { queue(); playlist(); history(); }

    static void queue() {
        List<String> q = Arrays.asList("a", "b", "c", "d", "e"); // cur = c (2)
        QueueOps.Removal r = QueueOps.remove(5, 2, new int[]{0, 4});
        check(apply(q, r.kept).equals(Arrays.asList("b", "c", "d")) && r.newCurrent == 1 && !r.currentRemoved, "QO1. xoá bài trước/sau bài đang phát: vẫn đúng bài, chỉ số dịch đúng");
        r = QueueOps.remove(5, 2, new int[]{2});
        check(apply(q, r.kept).equals(Arrays.asList("a", "b", "d", "e")) && r.newCurrent == 2 && r.currentRemoved, "QO2. xoá chính bài đang phát: chuyển sang bài kế tiếp (d)");
        r = QueueOps.remove(5, 4, new int[]{4});
        check(r.newCurrent == 3 && r.currentRemoved, "QO3. xoá bài đang phát ở cuối: lùi về bài cuối còn lại");
        r = QueueOps.remove(5, 2, new int[]{0, 1, 2, 3, 4});
        check(r.kept.length == 0 && r.newCurrent == -1 && r.currentRemoved, "QO4. xoá hết: rỗng, không còn bài đang phát");
        r = QueueOps.remove(5, 2, new int[]{-1, 9, 1, 1});
        check(r.kept.length == 4 && r.newCurrent == 1, "QO5. chỉ số ngoài phạm vi/trùng bị bỏ qua");
        r = QueueOps.remove(0, -1, new int[]{0});
        check(r.kept.length == 0 && r.newCurrent == -1 && !r.currentRemoved, "QO6. danh sách rỗng an toàn");
        check(Arrays.equals(QueueOps.above(2), new int[]{0, 1}) && Arrays.equals(QueueOps.below(5, 2), new int[]{3, 4}) && Arrays.equals(QueueOps.allExcept(5, 2), new int[]{0, 1, 3, 4}) && QueueOps.below(5, 4).length == 0 && QueueOps.above(0).length == 0, "QO7. tập 'phía trên / phía dưới / tất cả trừ bài này'");
        r = QueueOps.remove(5, 2, QueueOps.allExcept(5, 2));
        check(apply(q, r.kept).equals(Arrays.asList("c")) && r.newCurrent == 0 && !r.currentRemoved, "QO8. xoá tất cả trừ bài đang phát: còn đúng một bài, đang phát");

        QueueOps.Move m = QueueOps.move(5, 2, 0, 3);     // a → vị trí 3
        check(apply(q, m.perm).equals(Arrays.asList("b", "c", "d", "a", "e")) && m.newCurrent == 1, "QO9. chuyển bài từ trước ra sau bài đang phát: bài đang phát dịch lên 1");
        m = QueueOps.move(5, 2, 4, 1);
        check(apply(q, m.perm).equals(Arrays.asList("a", "e", "b", "c", "d")) && m.newCurrent == 3, "QO10. chuyển bài từ sau lên trước bài đang phát: bài đang phát dịch xuống 1");
        m = QueueOps.move(5, 2, 2, 0);
        check(apply(q, m.perm).equals(Arrays.asList("c", "a", "b", "d", "e")) && m.newCurrent == 0, "QO11. chuyển chính bài đang phát: nó đi theo");
        m = QueueOps.move(5, 2, 0, 1);
        check(m.newCurrent == 2 && apply(q, m.perm).equals(Arrays.asList("b", "a", "c", "d", "e")), "QO12. đổi chỗ hai bài cùng phía: bài đang phát không đổi");
        check(QueueOps.move(5, 2, 1, 1) == null && QueueOps.move(5, 2, -1, 2) == null && QueueOps.move(5, 2, 1, 5) == null && QueueOps.move(0, -1, 0, 0) == null, "QO13. di chuyển không hợp lệ/không đổi → null");
        check(QueueOps.playNextPos(5, 2) == 3 && QueueOps.playNextPos(5, 4) == 5 && QueueOps.playNextPos(0, -1) == 0 && QueueOps.playNextPos(3, -1) == 0, "QO14. vị trí 'phát tiếp theo' = ngay sau bài đang phát (đầu danh sách nếu chưa phát)");
        check(QueueOps.moveNextTarget(5, 2, 4) == 3 && QueueOps.moveNextTarget(5, 2, 0) == 2 && QueueOps.moveNextTarget(5, 2, 2) == -1 && QueueOps.moveNextTarget(5, 2, 9) == -1, "QO15. 'phát tiếp theo' cho bài đã có trong hàng đợi: đích đúng cho cả hai phía");
        // Kiểm chứng khớp ngữ nghĩa: sau khi chuyển theo moveNextTarget, bài nằm ngay sau bài đang phát
        boolean ok = true;
        for (int cur = 0; cur < 6; cur++) for (int i = 0; i < 6; i++) {
            int to = QueueOps.moveNextTarget(6, cur, i); if (to < 0) continue;
            QueueOps.Move mv = QueueOps.move(6, cur, i, to);
            if (mv == null) { if (i != cur + 1) ok = false; continue; }      // đã nằm ngay sau bài đang phát: không cần di chuyển
            if (mv.perm[mv.newCurrent + 1] != i || mv.perm[mv.newCurrent] != cur) ok = false;
        }
        check(ok, "QO16. với mọi (đang phát, bài chọn) trong 6 bài: sau 'phát tiếp theo' bài chọn đứng NGAY SAU bài đang phát");
        Random rnd = new Random(8); boolean inv = true;
        for (int it = 0; it < 3000; it++) {
            int n = 1 + rnd.nextInt(10), cur = rnd.nextInt(n);
            QueueOps.Move mv = QueueOps.move(n, cur, rnd.nextInt(n), rnd.nextInt(n));
            if (mv == null) continue;
            boolean[] seen = new boolean[n]; for (int p : mv.perm) seen[p] = true;
            for (boolean s : seen) if (!s) inv = false;
            if (mv.perm[mv.newCurrent] != cur) inv = false;       // bài đang phát vẫn là bài đó
            int[] rem = new int[rnd.nextInt(n + 1)]; for (int i = 0; i < rem.length; i++) rem[i] = rnd.nextInt(n + 2) - 1;
            QueueOps.Removal rr = QueueOps.remove(n, cur, rem);
            if (rr.kept.length > 0 && (rr.newCurrent < 0 || rr.newCurrent >= rr.kept.length)) inv = false;
            if (!rr.currentRemoved && rr.kept.length > 0 && rr.kept[rr.newCurrent] != cur) inv = false;
        }
        check(inv, "QO17. 3000 ca ngẫu nhiên: hoán vị hợp lệ, bài đang phát luôn được theo dõi đúng, chỉ số mới luôn trong phạm vi");
    }

    static void playlist() {
        List<String> cur = Arrays.asList("/a", "/b");
        check(PlaylistOps.addDistinct(cur, Arrays.asList("/b", "/c", "/c", "/d")).equals(Arrays.asList("/a", "/b", "/c", "/d")) && cur.size() == 2, "PL1. thêm bài: bỏ trùng với bài đã có và trùng trong lô thêm, không sửa danh sách gốc");
        check(PlaylistOps.removeAt(cur, 0).equals(Arrays.asList("/b")) && PlaylistOps.removeAt(cur, 5).equals(cur) && PlaylistOps.removeAt(cur, -1).equals(cur), "PL2. xoá bài theo vị trí; vị trí sai không làm gì");
        List<String> l3 = Arrays.asList("/a", "/b", "/c");
        check(PlaylistOps.move(l3, 0, 2).equals(Arrays.asList("/b", "/c", "/a")) && PlaylistOps.move(l3, 2, 0).equals(Arrays.asList("/c", "/a", "/b")) && PlaylistOps.move(l3, 1, 1).equals(l3) && PlaylistOps.move(l3, 0, 9).equals(l3), "PL3. di chuyển trong playlist (lên/xuống/không đổi/ngoài phạm vi)");
        check(PlaylistOps.cleanName("  Nhạc Việt  ").equals("Nhạc Việt") && PlaylistOps.cleanName("   ").equals("Playlist") && PlaylistOps.cleanName(null).equals("Playlist") && PlaylistOps.cleanName(new String(new char[100]).replace('\0', 'x')).length() == 60, "PL4. tên playlist: cắt khoảng trắng, mặc định 'Playlist', tối đa 60 ký tự");
        Map<String, String> m = new HashMap<>(); m.put("/a", "/new/a"); m.put("/b", "/c");
        check(PlaylistOps.remap(Arrays.asList("/a", "/b", "/c"), m).equals(Arrays.asList("/new/a", "/c")), "PL5. file bị di chuyển: đổi đường dẫn, giữ thứ tự, bỏ trùng phát sinh");
    }

    static void history() {
        List<HistoryLog.Entry> h = new ArrayList<>();
        h = HistoryLog.push(h, "/a", 1); h = HistoryLog.push(h, "/b", 2); h = HistoryLog.push(h, "/a", 3);
        check(h.size() == 3 && h.get(0).path.equals("/a") && h.get(0).at == 3 && h.get(1).path.equals("/b") && h.get(2).path.equals("/a"), "HL1. mới nhất đứng đầu; nghe lại bài cũ vẫn là một lần nghe mới");
        h = HistoryLog.push(h, "/a", 4);
        check(h.size() == 3 && h.get(0).at == 4, "HL2. phát liên tiếp cùng một bài chỉ cập nhật thời điểm (không nhân đôi)");
        List<HistoryLog.Entry> big = new ArrayList<>(); for (int i = 0; i < HistoryLog.MAX + 50; i++) big = HistoryLog.push(big, "/t" + i, i);
        check(big.size() == HistoryLog.MAX && big.get(0).path.equals("/t" + (HistoryLog.MAX + 49)) && big.get(big.size() - 1).path.equals("/t50"), "HL3. giới hạn " + HistoryLog.MAX + " mục, bỏ mục cũ nhất");
        List<HistoryLog.Entry> back = HistoryLog.decode(HistoryLog.encode(h));
        check(back != null && back.size() == 3 && back.get(0).path.equals("/a") && back.get(0).at == 4 && back.get(1).path.equals("/b"), "HL4. lưu/đọc lại (round-trip)");
        check(HistoryLog.decode(HistoryLog.encode(new ArrayList<HistoryLog.Entry>())).isEmpty() && HistoryLog.isValid("[]"), "HL5. lịch sử rỗng hợp lệ");
        String[] bad = {"", "{}", "[{\"p\":\"/a\"}]", "[{\"p\":\"/a\",\"t\":\"abc\"}]", "[{\"p\":\"/a\",\"t\":\"1\"}", "[{\"p\":1,\"t\":\"1\"}]"};
        boolean ok = true; for (String b : bad) if (HistoryLog.isValid(b)) ok = false;
        check(ok, "HL6. sai định dạng/thiếu trường/t không phải số/cụt → không hợp lệ");
        Map<String, String> m = new HashMap<>(); m.put("/b", "/moved/b");
        List<HistoryLog.Entry> rm = HistoryLog.remap(h, m);
        check(rm.size() == 3 && rm.get(1).path.equals("/moved/b") && rm.get(1).at == 2 && rm.get(0).path.equals("/a"), "HL7. file bị di chuyển: lịch sử trỏ tới đường dẫn mới, giữ thời điểm");
        String json = HistoryLog.encode(h); boolean cuts = true;
        for (int i = 0; i < json.length(); i++) if (HistoryLog.isValid(json.substring(0, i))) cuts = false;
        check(cuts, "HL8. mọi điểm cắt cụt đều bị từ chối");
    }
}
