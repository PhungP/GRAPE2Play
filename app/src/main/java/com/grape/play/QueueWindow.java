package com.grape.play;

/** Cửa sổ hàng đợi công bố cho MediaSession: tối đa [max] bài, bắt đầu [before] bài trước bài hiện tại; luôn chứa bài hiện tại. Thuần Java (có test). */
public final class QueueWindow {
    private QueueWindow() {}
    /** Chỉ số bắt đầu (bao gồm). */
    public static int from(int size, int index, int before, int max) {
        if (size <= 0) return 0;
        int i = Math.max(0, Math.min(index, size - 1));
        int f = Math.max(0, i - Math.max(0, before));
        return Math.max(0, Math.min(f, size - Math.max(1, max)));
    }
    /** Chỉ số kết thúc (không bao gồm). */
    public static int to(int size, int index, int before, int max) { return Math.min(size, from(size, index, before, max) + Math.max(1, max)); }
}
