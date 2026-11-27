package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Test kết quả quét có cấu trúc (ScanResult), đối chiếu an toàn (ScanMerge) và bản xem tăng dần (IncrementalView). */
final class ScanTests {
    private ScanTests() {}
    /** Mục kiểm thử "đường dẫn|phiên bản". */
    static final Function<String, String> P = s -> s.substring(0, s.indexOf('|'));
    static List<String> items(String prefix, int from, int to, int v) { List<String> l = new ArrayList<>(); for (int i = from; i < to; i++) l.add(prefix + i + "|" + v); return l; }
    static ScanResult res(ScanResult.Report r, int scanned, int prev) { return r.result(scanned, prev, 1000); }
    static ScanResult.Report rep() { return new ScanResult.Report("s", 0); }
    static boolean containsAll(List<String> big, List<String> small) {
        Set<String> paths = new HashSet<>(); for (String b : big) paths.add(P.apply(b));
        for (String s : small) if (!paths.contains(P.apply(s))) return false; return true;
    }

    static void run() {
        outcomes(); merge(); view(); pipeline();
    }

    static void outcomes() {
        ScanResult.Report r = rep();
        ScanResult x = res(r, 10, 10);
        check(x.outcome == ScanResult.Outcome.COMPLETE && x.scanned == 10 && x.previous == 10 && x.durationMs == 1000 && x.failures == 0, "SC1. quét sạch → COMPLETE kèm số liệu");
        r = rep(); r.error(); check(res(r, 1, 1).outcome == ScanResult.Outcome.PARTIAL && res(r, 1, 1).failures == 1, "SC2. có lỗi thư mục → PARTIAL");
        r = rep(); r.timedOut = true; check(res(r, 1, 1).outcome == ScanResult.Outcome.PARTIAL && res(r, 1, 1).timedOut, "SC3. quá thời gian → PARTIAL");
        r = rep(); r.limitReached = true; check(res(r, 1, 1).outcome == ScanResult.Outcome.PARTIAL && res(r, 1, 1).limitReached, "SC4. đạt giới hạn → PARTIAL");
        r = rep(); r.unreadable("/storage/usb/x/"); x = res(r, 1, 1);
        check(x.outcome == ScanResult.Outcome.PARTIAL && x.unreadable.equals(Arrays.asList("/storage/usb/x/")), "SC5. vùng không đọc được → PARTIAL, lưu lại danh sách vùng");
        r = rep(); r.cancelled = true; r.error(); check(res(r, 1, 1).outcome == ScanResult.Outcome.CANCELLED, "SC6. hủy → CANCELLED (ưu tiên hơn PARTIAL)");
        r = rep(); r.failed = true; r.cancelled = true; check(res(r, 1, 1).outcome == ScanResult.Outcome.FAILED, "SC7. thất bại → FAILED (ưu tiên cao nhất)");
        r = rep(); r.note("a"); r.note("b"); x = res(r, 0, 0); r.note("c");
        check(x.diagnostics.equals(Arrays.asList("a", "b")), "SC8. kết quả là ảnh chụp bất biến (ghi chú thêm sau không đổi kết quả)");
    }

    static void merge() {
        List<String> old = items("/a/", 0, 10, 1);
        ScanResult complete = res(rep(), 0, 0);
        // COMPLETE
        List<String> fresh = new ArrayList<>(items("/a/", 0, 8, 2)); fresh.addAll(items("/n/", 0, 3, 1));
        ScanMerge.Reconciled<String> m = ScanMerge.reconcile(old, fresh, P, complete, null, null);
        check(m.persist && m.items.size() == 11 && m.items.contains("/a/0|2") && !m.items.contains("/a/0|1") && !containsAll(m.items, Arrays.asList("/a/9|1")) && containsAll(m.items, items("/n/", 0, 3, 1)), "SC9. COMPLETE: thêm mới, cập nhật, xoá bài thật sự không còn");
        // COMPLETE + vùng không quét được giữ lại
        List<String> oldMix = new ArrayList<>(old); oldMix.addAll(items("/usb/", 0, 5, 1));
        Predicate<String> offline = s -> s.startsWith("/usb/");
        m = ScanMerge.reconcile(oldMix, items("/a/", 0, 10, 1), P, complete, offline, null);
        check(m.persist && m.items.size() == 15 && containsAll(m.items, items("/usb/", 0, 5, 1)), "SC10. COMPLETE: bài của ổ vắng (ngoài phạm vi quét) được giữ nguyên");
        // Rỗng
        m = ScanMerge.reconcile(old, new ArrayList<String>(), P, complete, null, null);
        check(!m.persist && m.items == old && !m.note.isEmpty(), "SC11. COMPLETE rỗng nhưng chưa xác minh + cache có bài → giữ nguyên bản cũ, không ghi cache, có ghi chú");
        ScanResult.Report rv = rep(); rv.emptyVerified = true;
        m = ScanMerge.reconcile(old, new ArrayList<String>(), P, res(rv, 0, 10), null, null);
        check(m.persist && m.items.isEmpty(), "SC12. COMPLETE rỗng ĐÃ xác minh → nguồn rỗng hợp lệ, được chấp nhận");
        m = ScanMerge.reconcile(new ArrayList<String>(), new ArrayList<String>(), P, complete, null, null);
        check(m.persist && m.items.isEmpty(), "SC13. cache rỗng + quét rỗng → bình thường (không báo nghi ngờ)");
        m = ScanMerge.reconcile(items("/usb/", 0, 5, 1), new ArrayList<String>(), P, complete, offline, null);
        check(m.persist && m.items.size() == 5, "SC14. rỗng nhưng mọi bài cũ đều ngoài phạm vi quét → giữ chúng, không coi là nghi ngờ");
        // PARTIAL
        ScanResult.Report rp = rep(); rp.error(); ScanResult partial = res(rp, 0, 0);
        fresh = items("/a/", 0, 4, 2);
        m = ScanMerge.reconcile(old, fresh, P, partial, null, null);
        check(m.persist && m.items.size() == 10 && m.items.contains("/a/0|2") && m.items.contains("/a/9|1"), "SC15. PARTIAL: cập nhật bản đã thấy, giữ nguyên bản chưa duyệt (không coi là đã xoá)");
        m = ScanMerge.reconcile(old, fresh, P, partial, null, s -> P.apply(s).equals("/a/9"));
        check(m.items.size() == 9 && !containsAll(m.items, Arrays.asList("/a/9|1")) && containsAll(m.items, Arrays.asList("/a/8|1")), "SC16. PARTIAL: chỉ xoá bài được xác minh độc lập là đã xoá");
        m = ScanMerge.reconcile(old, new ArrayList<String>(), P, partial, null, null);
        check(m.items.size() == 10 && m.persist, "SC17. PARTIAL không thấy gì → giữ nguyên toàn bộ");
        // CANCELLED
        ScanResult.Report rc = rep(); rc.cancelled = true; ScanResult cancelled = res(rc, 0, 0);
        m = ScanMerge.reconcile(old, items("/a/", 0, 3, 2), P, cancelled, null, s -> true);
        check(!m.persist && m.items.size() == 10 && m.items.contains("/a/1|2"), "SC18. CANCELLED: không xoá gì (kể cả khi verifiedGone), không ghi cache, vẫn giữ bản vừa thấy");
        // FAILED
        ScanResult.Report rf = rep(); rf.failed = true;
        m = ScanMerge.reconcile(old, items("/a/", 0, 2, 2), P, res(rf, 0, 0), null, s -> true);
        check(!m.persist && m.items == old, "SC19. FAILED: trả nguyên bản cũ (cùng đối tượng), không ghi cache");
        // Trùng trong kết quả mới
        List<String> dup = Arrays.asList("/d/1|1", "/d/1|2", "/d/2|1");
        m = ScanMerge.reconcile(new ArrayList<String>(), dup, P, complete, null, null);
        check(m.items.size() == 2 && m.items.contains("/d/1|2"), "SC20. trùng đường dẫn trong kết quả: giữ bản cuối, không nhân đôi");
        // Bất biến ngẫu nhiên: mọi kết quả ngoài COMPLETE không bao giờ làm thu hẹp
        Random rnd = new Random(11); boolean ok = true;
        for (int it = 0; it < 2000; it++) {
            int n = rnd.nextInt(30); List<String> o = items("/x/", 0, n, 1);
            List<String> f = new ArrayList<>(); for (int i = 0; i < n + 5; i++) if (rnd.nextInt(3) == 0) f.add("/x/" + i + "|2");
            ScanResult.Report rr = rep(); int kind = rnd.nextInt(4);
            if (kind == 0) rr.failed = true; else if (kind == 1) rr.cancelled = true; else if (kind == 2) rr.error(); else rr.timedOut = true;
            ScanMerge.Reconciled<String> q = ScanMerge.reconcile(o, f, P, res(rr, f.size(), n), null, null);
            if (!containsAll(q.items, o) || q.items.size() < o.size() || (kind != 0 && !containsAll(q.items, f))) ok = false;
            if (kind <= 1 && q.persist) ok = false;
        }
        check(ok, "SC21. 2000 ca ngẫu nhiên (FAILED/CANCELLED/PARTIAL/timeout): kết quả luôn ⊇ cũ (và ⊇ mới trừ FAILED), không thu hẹp; FAILED/CANCELLED không ghi cache");
    }

    static void view() {
        List<String> old = items("/a/", 0, 100, 1);
        IncrementalView<String> v = new IncrementalView<>(old, P, 1000, 0);
        check(v.size() == 100 && !v.due(5000) && v.pendingChanges() == 0, "SC22. bản xem bắt đầu bằng danh sách cũ, chưa có thay đổi → không đến hạn publish");
        for (String s : old) v.offer(s);
        check(v.pendingChanges() == 0 && !v.due(9999), "SC23. gặp lại bản ghi y hệt → không tính thay đổi (không publish bản trùng)");
        v.offer("/n/1|1");
        check(v.pendingChanges() == 1 && !v.due(500) && v.due(1000), "SC24. có thay đổi: chưa đến hạn trước 1 s, đến hạn sau 1 s");
        List<String> snap = v.snapshot(1000);
        check(snap.size() == 101 && v.pendingChanges() == 0 && !v.due(1500) && !v.due(1999), "SC25. snapshot gồm cũ + mới (không thu hẹp), đặt lại bộ đếm và mốc thời gian");
        v.offer("/a/5|2");
        check(v.size() == 101 && v.pendingChanges() == 1 && v.snapshot(3000).contains("/a/5|2") && !v.snapshot(3000).contains("/a/5|1"), "SC26. bản đổi ghi đè bản cũ, không nhân đôi");
        // Đồng thời
        final IncrementalView<String> cv = new IncrementalView<>(new ArrayList<String>(), P, 1000, 0);
        Thread[] th = new Thread[6];
        for (int t = 0; t < th.length; t++) { final int id = t; th[t] = new Thread(() -> { for (int i = 0; i < 2000; i++) cv.offer("/t" + id + "/" + i + "|1"); }); th[t].start(); }
        try { for (Thread t : th) t.join(); } catch (InterruptedException e) { check(false, "SC27. gián đoạn"); }
        check(cv.size() == 12000 && cv.snapshot(0).size() == 12000, "SC27. 6 luồng offer đồng thời: không mất bản ghi");
    }

    /** Mô phỏng một lần quét: không bao giờ thu hẹp giữa chừng, số lần publish bị chặn theo thời gian (thay vì mỗi 40 bài). */
    static void pipeline() {
        int N = 50000;
        List<String> old = items("/m/", 0, N, 1);
        IncrementalView<String> v = new IncrementalView<>(old, P, 1000, 0);
        int publishes = 0; boolean shrink = false; long now = 0;
        // quét lại toàn bộ, mỗi 25 bài có 1 bài đổi; mỗi bài mất 1 ms
        for (int i = 0; i < N; i++) {
            now++;
            v.offer(i % 25 == 0 ? "/m/" + i + "|2" : old.get(i));
            if (v.due(now)) { publishes++; if (v.snapshot(now).size() < N) shrink = true; }
        }
        check(!shrink, "SC28. quét 50.000 bài: mọi bản publish giữa chừng đều đủ ≥ số bài cũ (không thu hẹp)");
        check(publishes <= 60 && publishes >= 1, "SC29. publish giữa chừng bị chặn theo thời gian: " + publishes + " lần (cách cũ ~1250 lần mỗi 40 bài)");
        // Không có thay đổi nào → không publish lần nào
        IncrementalView<String> same = new IncrementalView<>(old, P, 1000, 0); int p2 = 0;
        for (int i = 0; i < N; i++) { same.offer(old.get(i)); if (same.due(i * 5L)) p2++; }
        check(p2 == 0, "SC30. quét lại không đổi gì: 0 lần publish (không làm UI làm mới thừa)");
        // Quét lỗi giữa chừng: bản cuối được chọn là bản cũ (FAILED) hoặc ⊇ cũ (PARTIAL/CANCELLED)
        ScanResult.Report rf = rep(); rf.failed = true;
        ScanMerge.Reconciled<String> m = ScanMerge.reconcile(old, v.snapshot(now).subList(0, 300), P, res(rf, 300, N), null, null);
        check(m.items == old && !m.persist, "SC31. quét thất bại sau khi đã publish tăng dần: bản cuối = bản hợp lệ trước đó, cache không bị đụng");
        ScanResult.Report rc = rep(); rc.cancelled = true;
        m = ScanMerge.reconcile(old, new ArrayList<>(old.subList(0, 300)), P, res(rc, 300, N), null, null);
        check(m.items.size() == N && !m.persist, "SC32. hủy giữa chừng (mới thấy 300/50.000): vẫn đủ 50.000, không ghi cache");
    }
}
