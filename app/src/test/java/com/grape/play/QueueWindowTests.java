package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

/** Test cửa sổ hàng đợi công bố cho MediaSession. */
final class QueueWindowTests {
    private QueueWindowTests() {}
    static void run() {
        check(QueueWindow.from(0, 0, 10, 100) == 0 && QueueWindow.to(0, 0, 10, 100) == 0, "QW1. hàng đợi rỗng → [0,0)");
        check(QueueWindow.from(30, 5, 10, 100) == 0 && QueueWindow.to(30, 5, 10, 100) == 30, "QW2. danh sách ngắn: công bố toàn bộ");
        check(QueueWindow.from(500, 250, 10, 100) == 240 && QueueWindow.to(500, 250, 10, 100) == 340, "QW3. giữa danh sách dài: 10 bài trước + phần sau, tổng 100");
        check(QueueWindow.from(500, 3, 10, 100) == 0 && QueueWindow.to(500, 3, 10, 100) == 100, "QW4. đầu danh sách");
        check(QueueWindow.from(500, 499, 10, 100) == 400 && QueueWindow.to(500, 499, 10, 100) == 500, "QW5. cuối danh sách vẫn đủ 100 bài và chứa bài hiện tại");
        boolean ok = true;
        for (int n = 0; n <= 300; n += 7) for (int i = -2; i <= n + 2; i++) {
            int f = QueueWindow.from(n, i, 10, 100), t = QueueWindow.to(n, i, 10, 100), c = Math.max(0, Math.min(i, n - 1));
            if (n == 0) { if (f != 0 || t != 0) ok = false; continue; }
            if (f < 0 || t > n || t - f > 100 || c < f || c >= t) ok = false;
        }
        check(ok, "QW6. mọi (kích thước, chỉ số): cửa sổ trong phạm vi, ≤ 100 bài, luôn chứa bài hiện tại (kể cả chỉ số ngoài phạm vi)");
    }
}
