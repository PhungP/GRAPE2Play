package com.grape.play;

import java.util.Arrays;

/**
 * Thao tác hàng đợi phát (thuần Java, có test): tính chỉ số mới sau khi xoá/di chuyển/chèn. Không chạm ExoPlayer — Playback áp dụng kết quả.
 * Quy ước: [cur] = chỉ số bài đang phát (có thể -1 khi rỗng). Chỉ số ngoài phạm vi và chỉ số trùng bị bỏ qua.
 */
public final class QueueOps {
    private QueueOps() {}

    public static final class Removal {
        /** Chỉ số CŨ của các phần tử còn lại, theo thứ tự. */
        public final int[] kept;
        /** Chỉ số (trong danh sách mới) của bài đang phát; nếu nó bị xoá = bài còn lại kế tiếp (hoặc bài cuối nếu không còn bài sau); -1 nếu danh sách rỗng. */
        public final int newCurrent;
        public final boolean currentRemoved;
        Removal(int[] kept, int newCurrent, boolean currentRemoved) { this.kept = kept; this.newCurrent = newCurrent; this.currentRemoved = currentRemoved; }
    }

    public static Removal remove(int n, int cur, int[] remove) {
        boolean[] del = new boolean[Math.max(n, 0)];
        for (int r : remove) if (r >= 0 && r < n) del[r] = true;
        int k = 0; for (boolean b : del) if (!b) k++;
        int[] kept = new int[k]; int j = 0;
        for (int i = 0; i < n; i++) if (!del[i]) kept[j++] = i;
        boolean curDel = cur >= 0 && cur < n && del[cur];
        int nc;
        if (k == 0) nc = -1;
        else if (cur < 0 || cur >= n) nc = 0;
        else {
            int before = 0; for (int i = 0; i < cur; i++) if (!del[i]) before++;
            nc = Math.min(before, k - 1);     // còn: đúng vị trí mới; bị xoá: bài còn lại kế tiếp (= before) hoặc bài cuối
        }
        return new Removal(kept, nc, curDel);
    }

    public static final class Move {
        /** perm[i] = chỉ số cũ của phần tử đứng ở vị trí mới i. */
        public final int[] perm; public final int newCurrent;
        Move(int[] perm, int newCurrent) { this.perm = perm; this.newCurrent = newCurrent; }
    }

    /** Di chuyển phần tử [from] đến vị trí [to] (chỉ số SAU khi di chuyển, cùng quy ước ExoPlayer.moveMediaItem). null nếu không hợp lệ hoặc không đổi. */
    public static Move move(int n, int cur, int from, int to) {
        if (from < 0 || from >= n || to < 0 || to >= n || from == to) return null;
        int[] perm = new int[n]; int p = 0;
        for (int i = 0; i < n; i++) if (i != from) perm[p++] = i;
        int[] out = new int[n];
        for (int i = 0, src = 0; i < n; i++) out[i] = i == to ? from : perm[src++];
        int nc = cur;
        if (cur == from) nc = to;
        else if (from < cur && to >= cur) nc = cur - 1;
        else if (from > cur && to <= cur) nc = cur + 1;
        return new Move(out, nc);
    }

    /** Vị trí chèn "phát tiếp theo" = ngay sau bài đang phát (đầu danh sách nếu chưa có bài). */
    public static int playNextPos(int n, int cur) { return n <= 0 ? 0 : Math.min(n, Math.max(cur, -1) + 1); }

    /** Chuyển bài ở [i] (≠ bài đang phát) thành bài kế tiếp: trả vị trí đích cho [move]; -1 nếu không cần/không hợp lệ. */
    public static int moveNextTarget(int n, int cur, int i) {
        if (i < 0 || i >= n || cur < 0 || cur >= n || i == cur) return -1;
        return i < cur ? cur : cur + 1;
    }

    public static int[] above(int idx) { int[] a = new int[Math.max(idx, 0)]; for (int i = 0; i < a.length; i++) a[i] = i; return a; }
    public static int[] below(int n, int idx) { int s = Math.max(idx + 1, 0); int[] a = new int[Math.max(n - s, 0)]; for (int i = 0; i < a.length; i++) a[i] = s + i; return a; }
    public static int[] allExcept(int n, int idx) { int[] a = new int[Math.max(n - (idx >= 0 && idx < n ? 1 : 0), 0)]; int j = 0; for (int i = 0; i < n; i++) if (i != idx) a[j++] = i; return Arrays.copyOf(a, j); }
}
