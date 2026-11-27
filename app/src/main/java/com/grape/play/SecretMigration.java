package com.grape.play;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Logic kiểm chứng di trú bí mật plaintext → mã hoá (thuần Java, có test; phần Keystore nằm ở SecretBox.kt). */
public final class SecretMigration {
    private SecretMigration() {}

    /** Còn bản ghi nào có giá trị bí mật khác rỗng mà CHƯA mã hoá? */
    public static boolean hasPlain(List<Map<String, String>> recs, String field, Predicate<String> isEncrypted) {
        for (Map<String, String> r : recs) { String v = r.get(field); if (v != null && !v.isEmpty() && !isEncrypted.test(v)) return true; }
        return false;
    }

    /**
     * Dữ liệu ĐÃ GHI có đúng không? Mỗi bản ghi (theo [idField]) phải có bí mật giải mã ra đúng [expected] (id → bản rõ mong đợi; rỗng/thiếu = phải rỗng).
     * Bản ghi trong [lostIds] (đã mất khoá, giữ nguyên bản mã) được bỏ qua. Mọi id mong đợi phải xuất hiện. Không ném ngoại lệ.
     */
    public static boolean verifyStored(List<Map<String, String>> stored, String idField, String field, Map<String, String> expected, Set<String> lostIds, Function<String, String> decrypt) {
        if (stored == null) return false;
        Set<String> seen = new HashSet<>();
        Map<String, String> exp = new HashMap<>(expected);
        for (Map<String, String> r : stored) {
            String id = r.get(idField); if (id == null) return false;
            seen.add(id);
            if (lostIds.contains(id)) continue;
            String want = exp.containsKey(id) ? exp.get(id) : "";
            String v = r.get(field); if (v == null) v = "";
            if (want.isEmpty()) { if (!v.isEmpty()) return false; continue; }
            if (v.isEmpty()) return false;
            String got;
            try { got = decrypt.apply(v); } catch (RuntimeException e) { return false; }
            if (!want.equals(got)) return false;
        }
        for (String id : exp.keySet()) if (!seen.contains(id) && !lostIds.contains(id)) return false;
        return true;
    }
}
