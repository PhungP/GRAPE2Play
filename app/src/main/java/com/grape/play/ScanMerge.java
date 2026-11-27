package com.grape.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Đối chiếu danh sách cũ và kết quả quét theo kết quả có cấu trúc (thuần Java, có test).
 *  COMPLETE : bản ghi mới/đổi được cập nhật; bản ghi cũ không còn bị xoá — TRỪ những bản nằm trong vùng không được quét ([preserve], vd. bài thuộc ổ đang vắng).
 *             Kết quả rỗng khi cache có bản ghi trong phạm vi quét chỉ được chấp nhận khi [ScanResult.emptyVerified] (nguồn/ổ/quyền đã xác minh).
 *  PARTIAL  : giữ mọi bản ghi cũ chưa được xác nhận là đã xoá ([verifiedGone] — kiểm tra độc lập, null = không xoá gì); thêm/cập nhật bản đã thấy.
 *  CANCELLED: không xoá gì, không ghi cache; vẫn giữ bản vừa thấy (không thu hẹp).
 *  FAILED   : trả nguyên bản cũ, không ghi cache.
 */
public final class ScanMerge {
    private ScanMerge() {}

    public static final class Reconciled<T> {
        public final List<T> items; public final boolean persist; public final String note;
        Reconciled(List<T> items, boolean persist, String note) { this.items = items; this.persist = persist; this.note = note; }
    }

    public static <T> Reconciled<T> reconcile(List<T> old, List<T> fresh, Function<T, String> path, ScanResult r, Predicate<T> preserve, Predicate<T> verifiedGone) {
        switch (r.outcome) {
            case FAILED:
                return new Reconciled<>(old, false, "quét lỗi — giữ nguyên dữ liệu cũ");
            case CANCELLED:
                return new Reconciled<>(union(fresh, old, path, null), false, "đã dừng — giữ nguyên dữ liệu cũ");
            case PARTIAL: {
                List<T> out = union(fresh, old, path, verifiedGone);
                return new Reconciled<>(out, true, "quét chưa đầy đủ — giữ lại các bài chưa xác nhận đã xoá");
            }
            default: {
                Map<String, T> have = new LinkedHashMap<>(fresh.size() * 2);
                for (T t : fresh) have.put(path.apply(t), t);
                List<T> inScopeOld = new ArrayList<>(), outScopeOld = new ArrayList<>();
                for (T o : old) { if (have.containsKey(path.apply(o))) continue; (preserve != null && preserve.test(o) ? outScopeOld : inScopeOld).add(o); }
                if (have.isEmpty() && !inScopeOld.isEmpty() && !r.emptyVerified)
                    return new Reconciled<>(old, false, "kết quả rỗng chưa được xác minh (" + inScopeOld.size() + " bài trong cache) — giữ nguyên dữ liệu cũ");
                List<T> out = new ArrayList<>(have.size() + outScopeOld.size());
                out.addAll(have.values()); out.addAll(outScopeOld);
                return new Reconciled<>(out, true, "");
            }
        }
    }

    // ---------- Đối chiếu theo từng ổ lưu trữ (quét cục bộ) ----------
    /** Trạng thái tin cậy của một ổ sau khi quét (xem [ScanPolicy.judgeRoot]). [prefix] kết thúc bằng '/'. */
    public static final class RootState {
        public final String prefix; public final boolean trusted, allowVerifiedGone;
        public RootState(String prefix, boolean trusted, boolean allowVerifiedGone) { this.prefix = prefix; this.trusted = trusted; this.allowVerifiedGone = allowVerifiedGone; }
    }

    /** Chỉ số tiền tố dài nhất khớp với [path]; -1 nếu bài không thuộc ổ nào (ổ đang vắng). */
    public static int rootIndex(String path, List<String> prefixes) {
        int best = -1, len = -1;
        for (int i = 0; i < prefixes.size(); i++) { String p = prefixes.get(i); if (p.length() > len && path.startsWith(p)) { best = i; len = p.length(); } }
        return best;
    }

    /**
     * Quét cục bộ: quyết định theo TỪNG ổ, không chỉ theo kết quả chung.
     * Bài cũ không có trong kết quả mới: thuộc ổ không có mặt → giữ; ổ tin cậy → xoá; ổ không tin cậy → giữ (trừ khi
     * [RootState.allowVerifiedGone] và [verifiedGone] xác nhận). FAILED/CANCELLED xử lý như [reconcile].
     */
    public static <T> Reconciled<T> reconcileRoots(List<T> old, List<T> fresh, Function<T, String> path, ScanResult r, List<RootState> roots, Predicate<T> verifiedGone) {
        if (r.outcome == ScanResult.Outcome.FAILED) return new Reconciled<>(old, false, "quét lỗi — giữ nguyên dữ liệu cũ");
        if (r.outcome == ScanResult.Outcome.CANCELLED) return new Reconciled<>(union(fresh, old, path, null), false, "đã dừng — giữ nguyên dữ liệu cũ");
        List<String> prefixes = new ArrayList<>(roots.size());
        for (RootState rs : roots) prefixes.add(rs.prefix);
        Map<String, T> have = new LinkedHashMap<>(fresh.size() * 2);
        for (T t : fresh) have.put(path.apply(t), t);
        List<T> out = new ArrayList<>(have.size() + old.size());
        out.addAll(have.values());
        int preserved = 0;
        for (T o : old) {
            String p = path.apply(o);
            if (have.containsKey(p)) continue;
            int i = rootIndex(p, prefixes);
            if (i < 0) { out.add(o); continue; }
            RootState rs = roots.get(i);
            if (rs.trusted) continue;
            if (rs.allowVerifiedGone && verifiedGone != null && verifiedGone.test(o)) continue;
            out.add(o); preserved++;
        }
        return new Reconciled<>(out, true, preserved > 0 ? "giữ lại " + preserved + " bài của ổ chưa xác minh được" : "");
    }

    /** Bản mới (theo thứ tự quét, bỏ trùng) rồi các bản cũ chưa thấy; bản cũ chỉ bị bỏ nếu [gone] xác nhận. */
    static <T> List<T> union(List<T> fresh, List<T> old, Function<T, String> path, Predicate<T> gone) {
        Map<String, T> have = new LinkedHashMap<>(fresh.size() * 2);
        for (T t : fresh) have.put(path.apply(t), t);
        List<T> out = new ArrayList<>(have.size() + old.size());
        out.addAll(have.values());
        for (T o : old) if (!have.containsKey(path.apply(o)) && (gone == null || !gone.test(o))) out.add(o);
        return out;
    }
}
