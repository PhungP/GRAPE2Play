package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bản xem tăng dần trong lúc quét (thuần Java, có test): bắt đầu từ danh sách cũ, mỗi bản ghi thấy được ghi đè/thêm vào — KHÔNG BAO GIỜ bớt đi.
 * Chỉ báo "đến hạn publish" khi có thay đổi thật và đã qua [minIntervalMs] (không publish bản trùng, không làm UI làm mới thừa).
 */
public final class IncrementalView<T> {
    private final Function<T, String> path;
    private final Map<String, T> map;
    private final long minIntervalMs;
    private int changes; private long lastPublishMs;

    public IncrementalView(List<T> old, Function<T, String> path, long minIntervalMs, long nowMs) {
        this.path = path; this.minIntervalMs = minIntervalMs; this.lastPublishMs = nowMs;
        map = new LinkedHashMap<>(Math.max(16, old.size() * 2));
        for (T t : old) map.put(path.apply(t), t);
    }

    /** Ghi nhận một bản ghi vừa thấy. Giống hệt bản đang có (equals) thì không tính là thay đổi. */
    public synchronized void offer(T t) {
        T prev = map.put(path.apply(t), t);
        if (prev == null || !prev.equals(t)) changes++;
    }

    public synchronized boolean due(long nowMs) { return changes > 0 && nowMs - lastPublishMs >= minIntervalMs; }

    /** Ảnh chụp (một lần sao chép O(n)) và đặt lại bộ đếm. */
    public synchronized List<T> snapshot(long nowMs) { changes = 0; lastPublishMs = nowMs; return new ArrayList<>(map.values()); }

    public synchronized int pendingChanges() { return changes; }
    public synchronized int size() { return map.size(); }
}
