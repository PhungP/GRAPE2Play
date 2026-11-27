package com.grape.play;

/**
 * Điều kiện chạy quét thư viện lúc khởi động (thuần Java, có test): đúng MỘT lần mỗi tiến trình, bất kể cache đã được nạp trước hay chưa.
 * Lỗi cũ: Activity chỉ quét khi thư viện còn trống; nếu LibraryProvider (launcher TV) đánh thức tiến trình và nạp cache trước thì quét khởi động bị bỏ qua.
 */
public final class StartupScan {
    private boolean started;

    /** true = người gọi phải khởi chạy quét khởi động (lần đầu, hoặc lần trước chưa chạy xong). */
    public synchronized boolean tryBegin() { if (started) return false; started = true; return true; }

    /** [completed] = false (ngoại lệ/bị hủy giữa chừng): cho phép thử lại ở lần khởi động Activity kế tiếp. */
    public synchronized void finished(boolean completed) { if (!completed) started = false; }

    /** Chính sách CŨ (có lỗi), chỉ giữ để test tái hiện: bỏ qua quét nếu thư viện đã có bài. */
    public static boolean legacyShouldScan(boolean tracksAlreadyLoaded) { return !tracksAlreadyLoaded; }
}
