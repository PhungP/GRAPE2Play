package com.grape.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kết quả có cấu trúc của một lần quét nguồn nhạc (thuần Java, có test). Mọi quyết định xoá/giữ bản ghi dựa vào [outcome], KHÔNG dựa vào chuỗi thông báo.
 * [Report] là bộ thu thập có thể truy cập từ nhiều coroutine; [Result] là ảnh chụp bất biến.
 */
public final class ScanResult {
    public enum Outcome { COMPLETE, PARTIAL, FAILED, CANCELLED }

    public final String sourceId; public final Outcome outcome;
    public final int scanned, previous, failures; public final long durationMs;
    public final boolean timedOut, limitReached, cancelled, emptyVerified;
    public final List<String> unreadable, diagnostics;

    private ScanResult(Report r, int scanned, int previous, long durationMs) {
        sourceId = r.sourceId; this.scanned = scanned; this.previous = previous; this.durationMs = durationMs;
        failures = r.errors.get(); timedOut = r.timedOut; limitReached = r.limitReached; cancelled = r.cancelled; emptyVerified = r.emptyVerified;
        synchronized (r.unreadable) { unreadable = Collections.unmodifiableList(new ArrayList<>(r.unreadable)); }
        synchronized (r.diagnostics) { diagnostics = Collections.unmodifiableList(new ArrayList<>(r.diagnostics)); }
        outcome = r.outcome(unreadable.isEmpty());
    }

    public static final class Report {
        public final String sourceId; public final long startedMs;
        public final AtomicInteger errors = new AtomicInteger();
        public volatile boolean timedOut, limitReached, cancelled, failed, emptyVerified;
        final List<String> unreadable = new ArrayList<>(), diagnostics = new ArrayList<>();

        public Report(String sourceId, long nowMs) { this.sourceId = sourceId; this.startedMs = nowMs; }
        public void error() { errors.incrementAndGet(); }
        public void unreadable(String path) { synchronized (unreadable) { unreadable.add(path); } errors.incrementAndGet(); }
        public void note(String d) { synchronized (diagnostics) { diagnostics.add(d); } }

        /** FAILED > CANCELLED > PARTIAL (bất kỳ lỗi/giới hạn/vùng không đọc được) > COMPLETE. */
        Outcome outcome(boolean noUnreadable) {
            if (failed) return Outcome.FAILED;
            if (cancelled) return Outcome.CANCELLED;
            if (errors.get() > 0 || timedOut || limitReached || !noUnreadable) return Outcome.PARTIAL;
            return Outcome.COMPLETE;
        }
        public ScanResult result(int scanned, int previous, long nowMs) { return new ScanResult(this, scanned, previous, Math.max(0, nowMs - startedMs)); }
    }
}
