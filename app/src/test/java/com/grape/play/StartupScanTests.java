package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Quét tự động lúc khởi động: tái hiện lỗi (cache nạp trước thì bỏ qua quét) và kiểm chứng chính sách mới. */
final class StartupScanTests {
    private StartupScanTests() {}
    static void run() {
        // Tái hiện: LibraryProvider đã nạp cache → thư viện không rỗng → chính sách cũ KHÔNG quét
        check(!StartupScan.legacyShouldScan(true) && StartupScan.legacyShouldScan(false), "SS-A1. [TÁI HIỆN] chính sách cũ: thư viện đã có bài do Provider nạp cache trước → bỏ qua quét khởi động (lỗi)");
        // Chính sách mới: quét lần đầu bất kể cache
        StartupScan g = new StartupScan();
        check(g.tryBegin(), "SS-A2. chính sách mới: khởi động lần đầu LUÔN quét, kể cả khi cache đã nạp trước");
        check(!g.tryBegin() && !g.tryBegin(), "SS-A3. Activity bị tạo lại / gọi lại: không quét trùng");
        g.finished(true);
        check(!g.tryBegin(), "SS-A4. sau khi quét xong: vẫn không quét lại cho tới lần khởi động tiến trình sau");
        StartupScan f = new StartupScan(); f.tryBegin(); f.finished(false);
        check(f.tryBegin() && !f.tryBegin(), "SS-A5. quét chưa chạy xong (lỗi/bị huỷ): cho phép thử lại đúng một lần ở lần khởi tạo Activity kế tiếp");
        // Đồng thời: nhiều luồng cùng gọi → đúng một lần
        final StartupScan c = new StartupScan(); final AtomicInteger started = new AtomicInteger(); final CountDownLatch go = new CountDownLatch(1), done = new CountDownLatch(16);
        for (int i = 0; i < 16; i++) new Thread(() -> { try { go.await(); } catch (InterruptedException e) { } if (c.tryBegin()) started.incrementAndGet(); done.countDown(); }).start();
        go.countDown();
        try { done.await(); } catch (InterruptedException e) { }
        check(started.get() == 1, "SS-A6. 16 luồng cùng gọi (Activity + callback quyền): đúng 1 lần quét");
    }
}
