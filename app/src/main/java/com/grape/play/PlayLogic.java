package com.grape.play;

import java.util.HashMap;
import java.util.Random;

/** Logic hàng đợi/shuffle/xử lý lỗi của Playback, tách khỏi ExoPlayer để test được. Playback.kt gọi lại đúng các hàm này. */
public final class PlayLogic {
    private PlayLogic() {}

    public static final int REPEAT_OFF = 0, REPEAT_ONE = 1, REPEAT_ALL = 2;   // trùng Player.REPEAT_MODE_*

    /** Thứ tự xáo trộn có bài [first] đứng đầu (mọi chỉ số 0..n-1 xuất hiện đúng một lần). */
    public static int[] pinnedOrder(int n, int first, Random rnd) {
        int[] o = new int[n];
        if (n == 0) return o;
        int f = Math.max(0, Math.min(first, n - 1));
        int[] rest = new int[n - 1]; int k = 0;
        for (int i = 0; i < n; i++) if (i != f) rest[k++] = i;
        for (int i = rest.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = rest[i]; rest[i] = rest[j]; rest[j] = t; }
        o[0] = f; System.arraycopy(rest, 0, o, 1, rest.length);
        return o;
    }

    /** Thêm [addN] bài vào cuối danh sách: giữ nguyên thứ tự cũ (bài đã nghe vẫn đã nghe), bài mới nối vào cuối thứ tự. null nếu thứ tự cũ không khớp số bài. */
    public static int[] appendedOrder(int[] order, int oldN, int addN) {
        if (order == null || order.length != oldN) return null;
        int[] o = new int[oldN + addN];
        System.arraycopy(order, 0, o, 0, oldN);
        for (int i = 0; i < addN; i++) o[oldN + i] = oldN + i;
        return o;
    }

    /** Bài kế sau lỗi: lặp 1 bài thì tự chọn bài kế (hasNext của ExoPlayer quay lại bài lỗi); ngược lại dùng chỉ số kế của ExoPlayer ([exoNext], -1 = không có). -1 = không có bài để sang. */
    public static int skipTarget(int repeatMode, int cur, int n, int exoNext) {
        if (repeatMode == REPEAT_ONE) return cur + 1 < n ? cur + 1 : (n > 1 ? 0 : -1);
        return exoNext;
    }

    /** Bộ đếm thử lại theo khoá; [take] trả số lần thử (1-based) hoặc 0 nếu đã hết lượt. */
    public static final class Retries {
        private final HashMap<String, Integer> m = new HashMap<>();
        public int take(String key, int max) { int n = m.containsKey(key) ? m.get(key) : 0; if (n >= max) return 0; m.put(key, n + 1); return n + 1; }
        public void clear() { m.clear(); }
        public boolean isEmpty() { return m.isEmpty(); }
    }

    public enum Action { DSD_LOWER, RETRY_SINK, SINK_STOP, SINK_SKIP, SINK_PRELOAD, USB_COPY, RETRY_IO, PRELOAD_IGNORE, STOP_MANY, SKIP }

    public static final class Decision {
        public final Action action; public final int attempt; public final long delayMs;
        Decision(Action a, int attempt, long delayMs) { this.action = a; this.attempt = attempt; this.delayMs = delayMs; }
        /** Hành động này tính là một lỗi liên tiếp (errCount++). */
        public boolean countsError() { return action == Action.SINK_STOP || action == Action.SINK_SKIP || action == Action.SINK_PRELOAD || action == Action.STOP_MANY || action == Action.SKIP; }
    }

    /**
     * Quyết định xử lý một lỗi phát. [errCount] = số lỗi liên tiếp TRƯỚC lần này; [retries] bị thay đổi khi chọn RETRY_*.
     * preload = lỗi của bài kế tiếp đang nạp trước (không phải bài đang nghe).
     */
    public static Decision decide(boolean preload, boolean sinkError, boolean dsdFile, boolean canLowerDsd, boolean usb, boolean usbFirstTry,
                                  boolean transientIo, int errCount, int queueSize, String itemKey, Retries retries) {
        if (!preload && dsdFile && sinkError && canLowerDsd) return new Decision(Action.DSD_LOWER, 0, 0);
        if (sinkError) {
            if (!preload) { int a = retries.take("sink|" + itemKey, 2); if (a > 0) return new Decision(Action.RETRY_SINK, a, 1200); }
            if (preload) return new Decision(Action.SINK_PRELOAD, 0, 0);
            return new Decision((errCount + 1) >= Math.min(queueSize, 3) ? Action.SINK_STOP : Action.SINK_SKIP, 0, 0);
        }
        if (usb && usbFirstTry) return new Decision(Action.USB_COPY, 0, 0);
        if (!preload && transientIo) { int a = retries.take("io|" + itemKey, 2); if (a > 0) return new Decision(Action.RETRY_IO, a, a == 1 ? 1500 : 5000); }
        if (preload) return new Decision(Action.PRELOAD_IGNORE, 0, 0);
        return new Decision((errCount + 1) >= Math.min(queueSize, 4) ? Action.STOP_MANY : Action.SKIP, 0, 0);
    }

    /** Phân loại một lần tự chuyển bài từ hai dấu hiệu quan sát được. 2 = định dạng PCM đổi + sink cấu hình lại; 1 = sink cấu hình lại dù định dạng không đổi; 0 = KHÔNG phát hiện thay đổi (không đồng nghĩa với gapless: khoảng lặng thực tế chưa đo). */
    public static int transitionKind(int formatChanges, int configureCalls) {
        if (formatChanges > 0) return 2;
        return configureCalls > 0 ? 1 : 0;
    }
}
