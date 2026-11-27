package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Phase 1C.2: xoá nguồn không chặn luồng gọi nhưng vẫn nguyên tử so với việc xác nhận kết quả quét. Đồng bộ bằng latch (không sleep); timeout chỉ để chống treo. */
final class SourceRemovalTests {
    private SourceRemovalTests() {}
    static final long SAFETY_MS = 10_000;

    /** Mô hình Library: danh sách đã publish, cache, kho nguồn — cùng thao tác như Library.kt. */
    static final class Model {
        final ScanGuard g = new ScanGuard();
        final Set<String> store = ConcurrentHashMap.newKeySet();
        final Map<String, List<String>> lists = new ConcurrentHashMap<>();
        final Set<String> cache = ConcurrentHashMap.newKeySet();
        Model(String... ids) { store.addAll(Arrays.asList(ids)); }
        boolean commit(String id, Runnable beforeWrite) { return g.commitIfPresent(id, () -> store.contains(id), () -> { lists.put(id, Arrays.asList("t1", "t2")); if (beforeWrite != null) beforeWrite.run(); cache.add(id); }); }
        Runnable removeTxn(String id) { return () -> { store.remove(id); lists.remove(id); cache.remove(id); }; }
    }

    static Executor newThread() { return r -> { Thread t = new Thread(r); t.setDaemon(true); t.start(); }; }
    static void await(CountDownLatch l) { try { l.await(SAFETY_MS, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { } }

    static void run() {
        blockedByScan(); repeated(); isolation(); failure(); rejected();
    }

    static void blockedByScan() {
        final Model m = new Model("A", "B");
        final CountDownLatch inCommit = new CountDownLatch(1), releaseCommit = new CountDownLatch(1), commitDone = new CountDownLatch(1), removed = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<>(); final AtomicInteger doneCalls = new AtomicInteger();
        // Lần quét đang xác nhận kết quả cuối: giữ khoá nguồn trong lúc "ghi cache"
        Thread scan = new Thread(() -> { m.commit("A", () -> { inCommit.countDown(); await(releaseCommit); }); commitDone.countDown(); });
        scan.setDaemon(true); scan.start();
        await(inCommit);
        // Yêu cầu xoá khi khoá đang bị giữ: phải TRẢ VỀ NGAY (luồng gọi không chờ khoá)
        final AtomicReference<Boolean> accepted = new AtomicReference<>(); final CountDownLatch returned = new CountDownLatch(1);
        Thread ui = new Thread(() -> { accepted.set(m.g.requestRemove("A", newThread(), m.removeTxn("A"), e -> { err.set(e); doneCalls.incrementAndGet(); removed.countDown(); })); returned.countDown(); });
        ui.setDaemon(true); ui.start();
        await(returned);
        check(returned.getCount() == 0 && Boolean.TRUE.equals(accepted.get()), "SR1. yêu cầu xoá khi lần quét đang giữ khoá: trả về ngay (không chờ khoá), được chấp nhận");
        check(removed.getCount() == 1 && m.g.isRemoving("A") && m.store.contains("A"), "SR2. giao dịch xoá chưa chạy xong khi khoá còn bị giữ (đang chờ trên luồng nền), nguồn vẫn còn trong kho");
        releaseCommit.countDown();
        await(commitDone); await(removed);
        check(doneCalls.get() == 1 && err.get() == null, "SR3. sau khi lần quét nhả khoá, việc xoá hoàn tất đúng một lần, không lỗi");
        check(!m.lists.containsKey("A") && !m.cache.contains("A") && !m.store.contains("A"), "SR4. kết quả quét vừa xác nhận NGAY TRƯỚC khi xoá đã được dọn: không còn bài, không còn cache, không còn trong kho");
        check(!m.g.isRemoving("A"), "SR5. trạng thái 'đang xoá' được xoá sau khi hoàn tất");
        // Sau khi xoá xong: quét không thể publish lại hay tạo lại cache
        boolean inter = m.commit("A", null), fin = m.commit("A", null);
        check(!inter && !fin && !m.lists.containsKey("A") && !m.cache.contains("A"), "SR6. sau khi xoá xong: publish trung gian và kết quả cuối đều bị từ chối, không tạo lại cache");
    }

    static void repeated() {
        final Model m = new Model("A");
        final CountDownLatch inCommit = new CountDownLatch(1), release = new CountDownLatch(1), removed = new CountDownLatch(1); final AtomicInteger txnRuns = new AtomicInteger(), doneCalls = new AtomicInteger();
        Thread scan = new Thread(() -> m.commit("A", () -> { inCommit.countDown(); await(release); })); scan.setDaemon(true); scan.start(); await(inCommit);
        Runnable txn = () -> { txnRuns.incrementAndGet(); m.removeTxn("A").run(); };
        boolean first = m.g.requestRemove("A", newThread(), txn, e -> { doneCalls.incrementAndGet(); removed.countDown(); });
        boolean second = m.g.requestRemove("A", newThread(), txn, e -> doneCalls.incrementAndGet());
        boolean third = m.g.requestRemove("A", newThread(), txn, e -> doneCalls.incrementAndGet());
        check(first && !second && !third, "SR7. bấm Xoá lặp lại khi yêu cầu đầu chưa xong: chỉ yêu cầu đầu được nhận");
        release.countDown(); await(removed);
        check(txnRuns.get() == 1 && doneCalls.get() == 1, "SR8. giao dịch xoá chạy đúng một lần, callback hoàn tất đúng một lần");
        // Sau khi xong, yêu cầu lại (nguồn đã mất) vẫn an toàn và idempotent
        final CountDownLatch again = new CountDownLatch(1);
        boolean fourth = m.g.requestRemove("A", newThread(), txn, e -> again.countDown());
        await(again);
        check(fourth && txnRuns.get() == 2 && !m.store.contains("A") && !m.lists.containsKey("A"), "SR9. yêu cầu xoá sau khi đã xoá: được xử lý an toàn (idempotent), không lỗi");
    }

    static void isolation() {
        final Model m = new Model("A", "B");
        final CountDownLatch inCommit = new CountDownLatch(1), release = new CountDownLatch(1), removedA = new CountDownLatch(1);
        Thread scanA = new Thread(() -> m.commit("A", () -> { inCommit.countDown(); await(release); })); scanA.setDaemon(true); scanA.start(); await(inCommit);
        m.g.requestRemove("A", newThread(), m.removeTxn("A"), e -> removedA.countDown());
        // Trong lúc việc xoá A còn chờ khoá của A: nguồn B vẫn xác nhận được kết quả quét bình thường
        boolean bCommitted = m.commit("B", null);
        check(bCommitted && m.lists.containsKey("B") && m.cache.contains("B"), "SR10. việc xoá A đang chờ không cản việc xác nhận kết quả của nguồn B");
        release.countDown(); await(removedA);
        check(m.store.contains("B") && m.lists.containsKey("B") && m.cache.contains("B") && !m.store.contains("A") && !m.lists.containsKey("A"), "SR11. xoá A không ảnh hưởng nguồn B (kho, danh sách, cache của B còn nguyên)");
        // Xoá B và A song song độc lập
        final CountDownLatch both = new CountDownLatch(1);
        boolean rb = m.g.requestRemove("B", newThread(), m.removeTxn("B"), e -> both.countDown());
        await(both);
        check(rb && m.store.isEmpty() && m.lists.isEmpty() && m.cache.isEmpty(), "SR12. xoá B sau đó: kho, danh sách và cache đều rỗng");
    }

    static void failure() {
        final Model m = new Model("A");
        final AtomicReference<Throwable> err = new AtomicReference<>(); final CountDownLatch d1 = new CountDownLatch(1);
        boolean ok = m.g.requestRemove("A", newThread(), () -> { m.store.remove("A"); throw new IllegalStateException("lỗi giả lập khi xoá cache"); }, e -> { err.set(e); d1.countDown(); });
        await(d1);
        check(ok && err.get() instanceof IllegalStateException && !m.g.isRemoving("A"), "SR13. giao dịch xoá ném ngoại lệ: callback nhận lỗi đúng một lần, trạng thái 'đang xoá' được nhả");
        // Khoá không bị kẹt: xác nhận kết quả quét và yêu cầu xoá lại đều chạy được
        final AtomicInteger commits = new AtomicInteger(); final CountDownLatch t = new CountDownLatch(1);
        Thread th = new Thread(() -> { m.g.withLock("A", () -> commits.incrementAndGet()); t.countDown(); }); th.setDaemon(true); th.start(); await(t);
        check(commits.get() == 1, "SR14. sau ngoại lệ: khoá nguồn không bị kẹt");
        final CountDownLatch d2 = new CountDownLatch(1); final AtomicReference<Throwable> err2 = new AtomicReference<>(new Exception("chưa gọi"));
        boolean retry = m.g.requestRemove("A", newThread(), m.removeTxn("A"), e -> { err2.set(e); d2.countDown(); });
        await(d2);
        check(retry && err2.get() == null && !m.store.contains("A"), "SR15. thử xoá lại sau lỗi: thành công");
    }

    static void rejected() {
        final Model m = new Model("A");
        Executor rejecting = r -> { throw new java.util.concurrent.RejectedExecutionException("đóng"); };
        boolean r1 = m.g.requestRemove("A", rejecting, m.removeTxn("A"), e -> { });
        check(!r1 && !m.g.isRemoving("A") && m.store.contains("A"), "SR16. executor từ chối: không nhận yêu cầu, không kẹt trạng thái, nguồn nguyên vẹn");
        final CountDownLatch d = new CountDownLatch(1);
        boolean r2 = m.g.requestRemove("A", newThread(), m.removeTxn("A"), e -> d.countDown());
        await(d);
        check(r2 && !m.store.contains("A"), "SR17. yêu cầu kế tiếp với executor bình thường chạy được");
        // Quét và xoá đua nhau nhiều lượt: sau khi xoá hoàn tất không còn dấu vết (kiểm bất biến)
        boolean clean = true;
        for (int it = 0; it < 200; it++) {
            final Model mm = new Model("A"); final CountDownLatch gone = new CountDownLatch(1);
            Thread scan = new Thread(() -> { for (int i = 0; i < 100; i++) mm.commit("A", null); }); scan.setDaemon(true);
            scan.start();
            mm.g.requestRemove("A", newThread(), mm.removeTxn("A"), e -> gone.countDown());
            await(gone);
            try { scan.join(SAFETY_MS); } catch (InterruptedException e) { }
            if (mm.lists.containsKey("A") || mm.cache.contains("A") || mm.store.contains("A")) clean = false;
        }
        check(clean, "SR18. 200 lượt quét đua với xoá bất đồng bộ: sau khi quét kết thúc không còn bản ghi, cache hay kho của nguồn đã xoá");
    }
}
