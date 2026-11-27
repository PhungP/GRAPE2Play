package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase 1C.1: kết quả rỗng không đáng tin, nguồn bị xoá giữa lúc quét, an toàn theo từng ổ cục bộ, quét chồng lên nhau. */
final class ScanSafetyTests {
    private ScanSafetyTests() {}
    static final java.util.function.Function<String, String> P = ScanTests.P;

    static ScanResult complete(boolean emptyVerified) { ScanResult.Report r = new ScanResult.Report("s", 0); r.emptyVerified = emptyVerified; return r.result(0, 0, 1); }

    static void run() { remoteEmpty(); roots(); guard(); removal(); }

    // ---- HIGH-1: kết quả rỗng của nguồn từ xa ----
    static void remoteEmpty() {
        List<String> old = ScanTests.items("smb://nas/", 0, 40, 1);
        for (String type : new String[]{"smb", "dav", "dlna", "gdrive", "onedrive"})
            check(!ScanPolicy.remoteEmptyVerified(type), "SF1-" + type + ". kết quả rỗng của nguồn '" + type + "' không bao giờ được tin");
        ScanMerge.Reconciled<String> m = ScanMerge.reconcile(old, new ArrayList<String>(), P, complete(ScanPolicy.remoteEmptyVerified("smb")), null, null);
        check(m.items == old && !m.persist && m.note.contains("rỗng chưa được xác minh"), "SF2. SMB từng có 40 bài, quét COMPLETE nhưng 0 bài: giữ nguyên bài cũ, KHÔNG ghi cache (cache và .bak không bị đè), có ghi chú để hiện trạng thái");
        old = ScanTests.items("dav://nas/", 0, 5, 1);
        m = ScanMerge.reconcile(old, new ArrayList<String>(), P, complete(ScanPolicy.remoteEmptyVerified("dav")), null, null);
        check(m.items == old && !m.persist, "SF3. WebDAV từng có bài, 0 bài trong điều kiện không chắc chắn: giữ nguyên bài cũ, không ghi cache");
        m = ScanMerge.reconcile(old, ScanTests.items("dav://nas/", 0, 3, 2), P, complete(false), null, null);
        check(m.persist && m.items.size() == 3, "SF4. quét có bài (không rỗng): vẫn áp dụng bình thường, xoá bài thật sự không còn");
        m = ScanMerge.reconcile(new ArrayList<String>(), new ArrayList<String>(), P, complete(false), null, null);
        check(m.persist && m.items.isEmpty(), "SF5. nguồn mới/chưa từng có bài, quét rỗng: không có gì để giữ, không báo nghi ngờ");
    }

    // ---- MEDIUM-4: từng ổ cục bộ ----
    static ScanMerge.RootState rs(String root, boolean readable, int errs, boolean mounted, boolean other, int found, int oldCount) { return ScanPolicy.judgeRoot(root, readable, errs, mounted, other, found, oldCount); }

    static void roots() {
        check(rs("/storage/A", false, 1, true, false, 0, 9).trusted == false && !rs("/storage/A", false, 1, true, false, 0, 9).allowVerifiedGone, "SF6. ổ không đọc được: không tin, không cho xoá gì");
        check(!rs("/storage/A", true, 2, true, true, 5, 9).trusted && rs("/storage/A", true, 2, true, true, 5, 9).allowVerifiedGone, "SF7. ổ có thư mục con lỗi: không tin cả ổ, chỉ cho xoá từng file đã xác minh");
        check(!rs("/storage/A", true, 0, true, false, 0, 9).trusted, "SF8. ổ liệt kê rỗng hoàn toàn (không nội dung nào khác) mà trước đó có bài: không tin");
        check(!rs("/storage/A", true, 0, false, true, 0, 9).trusted, "SF9. ổ chưa gắn/không có quyền, rỗng, trước đó có bài: không tin");
        check(rs("/storage/A", true, 0, true, true, 0, 9).trusted, "SF10. ổ đã gắn, có nội dung khác, quét sạch không thấy bài: tin (người dùng thật sự đã xoá nhạc)");
        check(rs("/storage/A", true, 0, false, false, 3, 9).trusted && rs("/storage/A", true, 0, false, false, 0, 0).trusted, "SF11. ổ có bài trong kết quả, hoặc trước đó chưa có bài: tin");
        check(rs("/storage/A", true, 0, true, true, 1, 1).prefix.equals("/storage/A/") && rs("/storage/A/", true, 0, true, true, 1, 1).prefix.equals("/storage/A/"), "SF12. tiền tố ổ luôn kết thúc bằng '/' (không khớp nhầm /storage/AB)");

        String A = "/storage/A", B = "/storage/B", C = "/storage/C";
        List<String> old = new ArrayList<>(); old.addAll(ScanTests.items(A + "/m", 0, 5, 1)); old.addAll(ScanTests.items(B + "/m", 0, 6, 1)); old.addAll(ScanTests.items(C + "/m", 0, 3, 1));
        ScanResult done = complete(false);
        // Ổ A rỗng bất thường (không tin), ổ B quét tốt: B xoá 2 bài + sửa 1 + thêm 2; C không có mặt
        List<String> fresh = new ArrayList<>(ScanTests.items(B + "/m", 0, 4, 1)); fresh.set(1, B + "/m1|2"); fresh.addAll(ScanTests.items(B + "/n", 0, 2, 1));
        List<ScanMerge.RootState> st = Arrays.asList(rs(A, true, 0, true, false, 0, 5), rs(B, true, 0, true, true, 6, 6));
        ScanMerge.Reconciled<String> m = ScanMerge.reconcileRoots(old, fresh, P, done, st, null);
        check(ScanTests.containsAll(m.items, ScanTests.items(A + "/m", 0, 5, 1)), "SF13. ổ A tạm rỗng/chưa xác minh: toàn bộ 5 bài của A được giữ");
        check(m.items.contains(B + "/m1|2") && !m.items.contains(B + "/m1|1") && ScanTests.containsAll(m.items, ScanTests.items(B + "/n", 0, 2, 1)), "SF14. ổ B quét tốt SONG SONG: bài sửa được cập nhật, bài mới được thêm");
        check(!ScanTests.containsAll(m.items, Arrays.asList(B + "/m4|1")) && !ScanTests.containsAll(m.items, Arrays.asList(B + "/m5|1")), "SF15. ổ B: bài đã xoá thật (không còn trong ổ tin cậy) vẫn bị xoá");
        check(ScanTests.containsAll(m.items, ScanTests.items(C + "/m", 0, 3, 1)) && m.persist && m.note.contains("5"), "SF16. ổ C không có mặt: bài giữ nguyên; ghi cache bình thường; ghi chú nêu số bài giữ lại");
        check(m.items.size() == 5 + (4 + 2) + 3, "SF17. tổng: A 5 + B (4 còn + 2 mới) + C 3, không nhân đôi");

        // Ổ có lỗi thư mục con: chỉ xoá file xác minh được
        List<ScanMerge.RootState> st2 = Arrays.asList(rs(A, true, 1, true, true, 2, 5));
        ScanMerge.Reconciled<String> m2 = ScanMerge.reconcileRoots(ScanTests.items(A + "/m", 0, 5, 1), ScanTests.items(A + "/m", 0, 2, 1), P, done, st2, s -> P.apply(s).equals(A + "/m3"));
        check(m2.items.size() == 4 && !ScanTests.containsAll(m2.items, Arrays.asList(A + "/m3|1")) && ScanTests.containsAll(m2.items, Arrays.asList(A + "/m2|1", A + "/m4|1")), "SF18. ổ có lỗi thư mục con: file đã xác minh là xoá thì bị xoá, các bài chưa xác nhận được giữ nguyên");
        ScanMerge.Reconciled<String> m3 = ScanMerge.reconcileRoots(ScanTests.items(A + "/m", 0, 5, 1), new ArrayList<String>(), P, done, Arrays.asList(rs(A, true, 0, true, false, 0, 5)), s -> true);
        check(m3.items.size() == 5, "SF19. ổ rỗng chưa xác minh: kể cả verifiedGone trả true cũng KHÔNG xoá (file ở thư mục gốc ổ tạm rỗng trông như 'đã xoá')");
        ScanMerge.Reconciled<String> m4 = ScanMerge.reconcileRoots(ScanTests.items(A + "/m", 0, 5, 1), new ArrayList<String>(), P, done, Arrays.asList(rs(A, true, 0, true, true, 0, 5)), null);
        check(m4.items.isEmpty() && m4.persist, "SF20. xoá sạch nhạc trên ổ hoạt động bình thường (đã gắn, có nội dung khác): được phản ánh, thư viện của ổ về rỗng");
        // Lồng ổ: tiền tố dài nhất thắng
        List<String> pref = Arrays.asList("/storage/", "/storage/A/");
        check(ScanMerge.rootIndex("/storage/A/m/x", pref) == 1 && ScanMerge.rootIndex("/storage/B/x", pref) == 0 && ScanMerge.rootIndex("/other/x", pref) == -1 && ScanMerge.rootIndex("/storage/AB/x", Arrays.asList("/storage/A/")) == -1, "SF21. gán bài vào ổ theo tiền tố dài nhất; ngoài mọi ổ → -1 (giữ)");
        // FAILED / CANCELLED
        ScanResult.Report rc = new ScanResult.Report("local", 0); rc.cancelled = true;
        ScanMerge.Reconciled<String> m5 = ScanMerge.reconcileRoots(old, ScanTests.items(B + "/m", 0, 1, 2), P, rc.result(1, 15, 1), st, s -> true);
        check(!m5.persist && m5.items.size() == old.size(), "SF22. hủy giữa chừng: không xoá gì, không ghi cache (kể cả ổ B tin cậy)");
        ScanResult.Report rf = new ScanResult.Report("local", 0); rf.failed = true;
        check(ScanMerge.reconcileRoots(old, fresh, P, rf.result(0, 15, 1), st, null).items == old, "SF23. lỗi: trả nguyên bản cũ");
        // Ngẫu nhiên: bài thuộc ổ không tin cậy hoặc ngoài mọi ổ không bao giờ bị mất
        java.util.Random rnd = new java.util.Random(5); boolean ok = true;
        for (int it = 0; it < 1500; it++) {
            List<String> o = new ArrayList<>(); List<ScanMerge.RootState> sts = new ArrayList<>(); String[] names = {"/r/a", "/r/b", "/r/c"};
            for (int r = 0; r < 3; r++) { for (int i = 0; i < rnd.nextInt(6); i++) o.add(names[r] + "/f" + i + "|1");
                boolean trusted = rnd.nextBoolean(); if (r < 2) sts.add(new ScanMerge.RootState(names[r] + "/", trusted, false)); }   // ổ c ngoài danh sách (vắng)
            List<String> f = new ArrayList<>(); for (String x : o) if (rnd.nextInt(3) == 0) f.add(x.replace("|1", "|2"));
            ScanMerge.Reconciled<String> q = ScanMerge.reconcileRoots(o, f, P, done, sts, null);
            for (String x : o) {
                String path = P.apply(x); boolean inFresh = false; for (String y : f) if (P.apply(y).equals(path)) inFresh = true;
                int idx = path.startsWith("/r/a/") ? 0 : path.startsWith("/r/b/") ? 1 : -1;
                boolean mustKeep = idx < 0 || !sts.get(idx).trusted;
                boolean has = ScanTests.containsAll(q.items, Arrays.asList(x));
                if (mustKeep && !has) ok = false;
                if (!mustKeep && !inFresh && has) ok = false;       // ổ tin cậy: bài không còn thì bị xoá
            }
        }
        check(ok, "SF24. 1500 ca ngẫu nhiên: bài của ổ không tin cậy/ổ vắng không bao giờ mất; ổ tin cậy xoá đúng bài không còn");
    }

    // ---- MEDIUM-2: một lần quét mỗi nguồn, hủy theo thế hệ ----
    static void guard() {
        ScanGuard g = new ScanGuard();
        check(g.tryAcquire("nas") && !g.tryAcquire("nas") && g.isActive("nas"), "SF25. nguồn đang quét: yêu cầu quét thứ hai bị từ chối");
        check(g.tryAcquire("dlna") && g.tryAcquire("cloud"), "SF26. các nguồn khác nhau vẫn quét đồng thời");
        g.release("nas");
        check(!g.isActive("nas") && g.tryAcquire("nas"), "SF27. nhả khoá xong thì nguồn quét lại được");

        // Nhiều luồng cùng xin quét một nguồn: đúng một luồng thắng
        final ScanGuard g2 = new ScanGuard(); final AtomicInteger won = new AtomicInteger(); final CountDownLatch go = new CountDownLatch(1), done = new CountDownLatch(16);
        for (int i = 0; i < 16; i++) new Thread(() -> { try { go.await(); } catch (InterruptedException e) { } if (g2.tryAcquire("same")) won.incrementAndGet(); done.countDown(); }).start();
        go.countDown();
        try { done.await(); } catch (InterruptedException e) { }
        check(won.get() == 1, "SF28. 16 luồng cùng yêu cầu quét một nguồn: chỉ 1 lần quét được chạy (không có hai bộ ghi cache cùng lúc)");

        // runExclusive không chồng lấn
        final ScanGuard g3 = new ScanGuard(); final AtomicInteger inside = new AtomicInteger(), maxInside = new AtomicInteger(), ran = new AtomicInteger();
        Thread[] th = new Thread[8];
        for (int t = 0; t < th.length; t++) { th[t] = new Thread(() -> { for (int i = 0; i < 300; i++) g3.runExclusive("x", () -> { int n = inside.incrementAndGet(); maxInside.accumulateAndGet(n, Math::max); ran.incrementAndGet(); inside.decrementAndGet(); }); }); th[t].start(); }
        try { for (Thread t : th) t.join(); } catch (InterruptedException e) { }
        check(maxInside.get() == 1 && ran.get() >= 1, "SF29. runExclusive: không bao giờ có hai lần chạy cùng nguồn chồng lên nhau");

        // Ngoại lệ → nhả khoá
        final ScanGuard g4 = new ScanGuard(); boolean threw = false;
        try { g4.runExclusive("s", () -> { throw new IllegalStateException("lỗi giả lập"); }); } catch (IllegalStateException e) { threw = true; }
        check(threw && !g4.isActive("s") && g4.runExclusive("s", () -> { }), "SF30. ngoại lệ trong lúc quét: khoá được nhả, lần quét sau chạy bình thường");
        // Hủy bằng ngoại lệ kiểu coroutine (try/finally) và nguồn bị xoá: mô phỏng mẫu try { } finally { release }
        ScanGuard g5 = new ScanGuard();
        boolean first = g5.tryAcquire("s");
        try { /* quét bị hủy/nguồn bị xoá giữa chừng */ } finally { g5.release("s"); }
        check(first && g5.tryAcquire("s"), "SF31. thoát bình thường/hủy/xoá nguồn: khoá nhả, quét lại được");

        // Hủy theo thế hệ
        ScanGuard g6 = new ScanGuard();
        long a = g6.epoch();
        check(!g6.cancelledSince(a), "SF32. lần quét mới bắt đầu: chưa bị hủy");
        g6.cancelAll();
        check(g6.cancelledSince(a), "SF33. 'Dừng quét' sau khi lần quét A bắt đầu: A bị hủy");
        long b = g6.epoch();
        check(!g6.cancelledSince(b) && g6.cancelledSince(a), "SF34. lần quét B bắt đầu SAU lần hủy: không bị ảnh hưởng, và việc bắt đầu B không xoá yêu cầu hủy của A (không có cờ dùng chung bị đặt lại)");
        boolean firstScan = g6.tryAcquire("n"), secondScan = g6.tryAcquire("n");
        check(firstScan && !secondScan && g6.cancelledSince(a) && !g6.cancelledSince(g6.epoch()), "SF35. yêu cầu quét thứ hai bị từ chối không đụng tới trạng thái hủy của lần quét đầu");
    }

    // ---- MEDIUM-1: nguồn bị xoá trong lúc quét ----
    static void removal() {
        final ScanGuard g = new ScanGuard();
        final AtomicBoolean exists = new AtomicBoolean(true);
        final Map<String, List<String>> lists = new HashMap<>();
        final AtomicBoolean cacheFile = new AtomicBoolean(false);
        Runnable publish = () -> { lists.put("nas", Arrays.asList("/a")); };
        check(g.commitIfPresent("nas", exists::get, publish) && lists.containsKey("nas"), "SF36. nguồn còn tồn tại: publish trung gian được chấp nhận");
        g.withLock("nas", () -> { exists.set(false); lists.remove("nas"); cacheFile.set(false); });      // removeSource
        boolean inter = g.commitIfPresent("nas", exists::get, publish);
        boolean fin = g.commitIfPresent("nas", exists::get, () -> { lists.put("nas", Arrays.asList("/a", "/b")); cacheFile.set(true); });
        check(!inter && !fin && !lists.containsKey("nas") && !cacheFile.get(), "SF37. nguồn đã xoá: publish trung gian và kết quả cuối đều bị từ chối — không trở lại thư viện, không tạo lại cache");
        // Điều kiện đua: nhiều lần commit song song với việc xoá; sau khi xoá xong thì không còn dấu vết nào
        boolean allClean = true;
        for (int it = 0; it < 300; it++) {
            final ScanGuard gg = new ScanGuard(); final AtomicBoolean ex = new AtomicBoolean(true); final Map<String, List<String>> ls = new java.util.concurrent.ConcurrentHashMap<>(); final AtomicBoolean cf = new AtomicBoolean(false);
            Thread scan = new Thread(() -> { for (int i = 0; i < 200; i++) gg.commitIfPresent("s", ex::get, () -> { ls.put("s", Arrays.asList("x")); cf.set(true); }); });
            Thread remove = new Thread(() -> gg.withLock("s", () -> { ex.set(false); ls.remove("s"); cf.set(false); }));
            scan.start(); if (it % 3 == 0) Thread.yield(); remove.start();
            try { scan.join(); remove.join(); } catch (InterruptedException e) { }
            if (ls.containsKey("s") || cf.get()) allClean = false;
        }
        check(allClean, "SF38. 300 lượt đua quét-xoá song song: sau khi xoá, không bao giờ còn bản ghi hay cache của nguồn đã xoá");
        // Nguồn khác không bị ảnh hưởng
        final ScanGuard g2 = new ScanGuard(); final AtomicBoolean ex2 = new AtomicBoolean(true); final List<String> other = new ArrayList<>();
        g2.withLock("removed", () -> { });
        check(g2.commitIfPresent("other", ex2::get, () -> other.add("kept")) && other.size() == 1, "SF39. xoá nguồn này không cản việc xác nhận kết quả của nguồn khác");
    }
}
