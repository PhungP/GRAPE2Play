package com.grape.play;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Điều phối quét (thuần Java, có test):
 *  1. Mỗi nguồn chỉ có MỘT lần quét tại một thời điểm ([tryAcquire]/[release]); yêu cầu thứ hai bị TỪ CHỐI (không chạy song song, không đặt lại trạng thái hủy).
 *     Các nguồn khác nhau vẫn quét đồng thời.
 *  2. Hủy theo "thế hệ": [cancelAll] tăng bộ đếm; một lần quét coi là bị hủy nếu bộ đếm đã đổi so với lúc nó bắt đầu.
 *     Không có cờ dùng chung bị lần quét mới xoá mất yêu cầu hủy của lần quét đang chạy.
 *  3. Khoá theo nguồn cho đoạn "xác nhận kết quả" (publish + ghi cache) và cho việc XOÁ nguồn: kiểm tra nguồn còn tồn tại và ghi nằm
 *     trong cùng một khoá nên kết quả quét không thể đưa nguồn đã xoá trở lại thư viện hay tạo lại cache của nó.
 *  Thứ tự khoá: khoá nguồn → (bên trong) khoá của Library; Library không bao giờ lấy khoá nguồn.
 */
public final class ScanGuard {
    private final Set<String> active = new HashSet<>();
    private final Set<String> removing = new HashSet<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong();

    public synchronized boolean tryAcquire(String id) { return active.add(id); }
    public synchronized void release(String id) { active.remove(id); }
    public synchronized boolean isActive(String id) { return active.contains(id); }

    /** Chạy [r] độc quyền theo nguồn; trả false (không chạy) nếu nguồn đang được quét. Luôn nhả khoá, kể cả khi [r] ném ngoại lệ. */
    public boolean runExclusive(String id, Runnable r) {
        if (!tryAcquire(id)) return false;
        try { r.run(); } finally { release(id); }
        return true;
    }

    public long epoch() { return epoch.get(); }
    public void cancelAll() { epoch.incrementAndGet(); }
    public boolean cancelledSince(long startEpoch) { return epoch.get() != startEpoch; }

    private Object lock(String id) { return locks.computeIfAbsent(id, k -> new Object()); }

    /** Nếu nguồn còn tồn tại thì chạy [action] (publish + ghi cache) trong khoá của nguồn; trả false và KHÔNG chạy gì nếu nguồn đã bị xoá. */
    public boolean commitIfPresent(String id, BooleanSupplier exists, Runnable action) {
        synchronized (lock(id)) {
            if (!exists.getAsBoolean()) return false;
            action.run();
            return true;
        }
    }

    /**
     * Yêu cầu xoá nguồn KHÔNG chặn luồng gọi (luồng UI): toàn bộ giao dịch xoá [txn] (gỡ khỏi kho, gỡ khỏi thư viện, xoá cache) chạy trên [executor]
     * trong khoá của nguồn — cùng khoá với [commitIfPresent] — nên vẫn nguyên tử so với mọi lần xác nhận kết quả quét:
     * kết quả quét xác nhận ngay trước đó được [txn] dọn, kết quả xác nhận sau đó bị từ chối.
     * Trả false (không làm gì) nếu nguồn này đang có một yêu cầu xoá chưa xong hoặc [executor] từ chối. [done] được gọi đúng một lần
     * (lỗi = Throwable, thành công = null) trên luồng của [executor]; lỗi của [txn] không làm kẹt trạng thái, cho phép yêu cầu lại.
     */
    public boolean requestRemove(String id, Executor executor, Runnable txn, Consumer<Throwable> done) {
        synchronized (removing) { if (!removing.add(id)) return false; }
        try {
            executor.execute(() -> {
                Throwable err = null;
                try { withLock(id, txn); } catch (Throwable t) { err = t; }
                finally { synchronized (removing) { removing.remove(id); } }
                done.accept(err);
            });
        } catch (RejectedExecutionException e) {
            synchronized (removing) { removing.remove(id); }
            return false;
        }
        return true;
    }

    public boolean isRemoving(String id) { synchronized (removing) { return removing.contains(id); } }

    /** Dùng cho việc xoá nguồn (gỡ khỏi kho, gỡ khỏi thư viện, xoá cache) — cùng khoá với [commitIfPresent]. */
    public void withLock(String id, Runnable r) { synchronized (lock(id)) { r.run(); } }
}
