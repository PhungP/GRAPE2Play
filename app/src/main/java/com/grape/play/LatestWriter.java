package com.grape.play;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hàng chờ ghi "bản mới nhất thắng" kèm thử lại có giới hạn (thuần Java, có test). Gọi [runOnce] từ MỘT luồng ghi duy nhất.
 * Mỗi lần ghi là toàn bộ trạng thái nên bỏ qua bản cũ hơn là an toàn; bản mới đến trong lúc chờ thử lại sẽ thay bản đang thử lại.
 */
public final class LatestWriter {
    public interface Sink {
        boolean write(String raw);
        /** false nếu kho đang khoá ghi (thử lại vô ích). */
        boolean canRetry();
    }
    public static final int MAX_ATTEMPTS = 5;

    private final AtomicReference<String> latest = new AtomicReference<>();
    private int attempts;

    public void submit(String raw) { latest.set(raw); }

    /** true = ghi thất bại và cần gọi lại runOnce sau một khoảng chờ; false = xong (thành công, không còn gì, hết lượt, hoặc kho khoá). */
    public boolean runOnce(Sink s) {
        String r = latest.getAndSet(null);
        if (r == null) return false;
        boolean ok;
        try { ok = s.write(r); } catch (RuntimeException e) { ok = false; }
        if (ok) { attempts = 0; return false; }
        if (!s.canRetry() || ++attempts >= MAX_ATTEMPTS) { attempts = 0; return false; }
        latest.compareAndSet(null, r);      // không đè bản mới hơn nếu đã có
        return true;
    }
}
