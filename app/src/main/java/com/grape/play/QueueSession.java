package com.grape.play;

import java.util.List;

/**
 * Máy trạng thái (thuần Java, chỉ gọi từ luồng chính) quyết định KHI NÀO được ghi/khôi phục hàng đợi:
 *  - hàng đợi rỗng lúc khởi động (chưa từng có bài) → KHÔNG ghi (không xoá bản đã lưu);
 *  - người dùng đã xoá hàng đợi sau khi từng có bài → ghi trạng thái RỖNG tường minh (một lần) để lần khởi động sau không khôi phục bản cũ;
 *  - sau khi người dùng đã thao tác (kể cả xoá), KHÔNG BAO GIỜ khôi phục bản đã lưu nữa;
 *  - sau khôi phục mà một số bài chưa tìm thấy (USB/NAS chưa sẵn sàng): chừng nào hàng đợi chưa bị sửa, tiếp tục lưu danh sách ĐẦY ĐỦ ban đầu.
 */
public final class QueueSession {
    public enum Action { SKIP, WRITE, WRITE_EMPTY }

    public static final class Plan {
        public final Action action;
        /** true: ghi danh sách đầy đủ lúc khôi phục ([baselineKeys]) thay vì hàng đợi hiện tại. */
        public final boolean useBaseline; public final List<String> baselineKeys;
        /** Chỉ số theo hệ toạ độ của danh sách sẽ ghi. */
        public final int index;
        Plan(Action a, boolean ub, List<String> bk, int idx) { action = a; useBaseline = ub; baselineKeys = bk; index = idx; }
    }

    private boolean touched;        // hàng đợi từng có bài trong phiên này (vĩnh viễn)
    private boolean emptyWritten;   // đã yêu cầu ghi trạng thái rỗng cho lần rỗng hiện tại
    private Object baseRef; private List<String> baseKeys; private int[] baseKept;

    /** Gọi mỗi lần quan sát hàng đợi (trước debounce, để không bỏ sót hàng đợi có bài trong thời gian ngắn). */
    public void observe(boolean nonEmpty) { if (nonEmpty) { touched = true; emptyWritten = false; } }

    /** Còn được phép khôi phục bản đã lưu không (chưa ai đụng vào hàng đợi trong phiên này). */
    public boolean canRestore() { return !touched; }

    /** Đã khôi phục: [queueRef] là đối tượng danh sách đã đặt vào Playback, [fullKeys] danh sách đầy đủ đã lưu, [kept] = chỉ số các bài đã khôi phục. */
    public void restored(Object queueRef, List<String> fullKeys, int[] kept) {
        touched = true; emptyWritten = false;
        if (kept.length == fullKeys.size()) { baseRef = null; baseKeys = null; baseKept = null; }   // đủ bài: không cần giữ
        else { baseRef = queueRef; baseKeys = fullKeys; baseKept = kept; }
    }

    public Plan plan(Object queueRef, int size, int index) {
        if (size == 0) {
            baseRef = null; baseKeys = null; baseKept = null;
            if (!touched || emptyWritten) return new Plan(Action.SKIP, false, null, 0);
            emptyWritten = true;
            return new Plan(Action.WRITE_EMPTY, false, null, 0);
        }
        touched = true; emptyWritten = false;
        if (baseRef != null && baseRef == queueRef && baseKept.length > 0) {
            int i = Math.max(0, Math.min(index, baseKept.length - 1));
            return new Plan(Action.WRITE, true, baseKeys, baseKept[i]);
        }
        baseRef = null; baseKeys = null; baseKept = null;      // hàng đợi đã bị sửa: bản hiện tại là chuẩn
        return new Plan(Action.WRITE, false, null, Math.max(0, Math.min(index, size - 1)));
    }
}
