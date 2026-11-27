package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test SafeJsonStore (khôi phục, khoá ghi, ghi nguyên lô, đồng thời) và PersistCodec (định dạng cũ/hỏng/cụt). */
final class SafeStoreTests {
    private SafeStoreTests() {}

    /** Backend trong bộ nhớ có thể giả lập lỗi. */
    static final class Mem implements SafeJsonStore.Backend {
        final Map<String, String> m = new ConcurrentHashMap<>();
        volatile boolean failPut, throwPut, dropPut, throwGet; volatile int puts;
        public String get(String k) { if (throwGet) throw new RuntimeException("ClassCastException giả lập"); return m.get(k); }
        public boolean putAll(Map<String, String> kv) {
            puts++;
            if (throwPut) throw new IllegalStateException("đầy bộ nhớ giả lập");
            if (failPut) return false;
            if (!dropPut) m.putAll(kv);     // dropPut: báo thành công nhưng không ghi (đọc lại phải phát hiện)
            return true;
        }
    }

    static List<String> logs = new ArrayList<>();
    static SafeJsonStore st(Mem b) { return new SafeJsonStore(b, PersistCodec::isStrings, logs::add, "T"); }

    static void run() {
        codec();
        store();
        writer();
    }

    static void codec() {
        List<String> l = Arrays.asList("/storage/emulated/0/Music/a.flac", "smb://nas1/Nhạc Việt/\"b\"\\c.dsf", "line\nbreak\ttab\u0001", "", "😀/x");
        check(l.equals(PersistCodec.decodeStrings(PersistCodec.encodeStrings(l))), "SS1. mảng chuỗi round-trip (Unicode, dấu nháy, \\, xuống dòng, điều khiển, rỗng, emoji)");
        check(PersistCodec.decodeStrings("[\"\\/storage\\/emulated\\/0\\/a.flac\",\"b\\u1ec7\"]").equals(Arrays.asList("/storage/emulated/0/a.flac", "bệ")), "SS2. đọc được định dạng cũ do org.json ghi (\\/ và \\uXXXX)");
        check(PersistCodec.decodeStrings("[]").isEmpty() && PersistCodec.decodeStrings(" [ ] \n").isEmpty(), "SS3. mảng rỗng hợp lệ (có khoảng trắng)");
        String good = PersistCodec.encodeStrings(l);
        boolean allCut = true;
        for (int i = 0; i < good.length(); i++) if (PersistCodec.decodeStrings(good.substring(0, i)) != null) allCut = false;
        check(allCut, "SS4. mọi điểm cắt cụt của JSON đều bị từ chối (không bao giờ thành danh sách rỗng)");
        String[] bad = {"", "null", "{}", "[1,2]", "[\"a\",]", "[\"a\" \"b\"]", "[\"a\"", "[\"a\"]x", "[\"a\"],", "[null]", "[\"\\x\"]", "[\"\\u12\"]", "\"a\"", "[\"a\nb\"]", "[01]", "<xml/>"};
        boolean ok = true;
        for (String b : bad) if (PersistCodec.decodeStrings(b) != null) { ok = false; System.out.println("  chấp nhận nhầm: " + b); }
        check(ok && PersistCodec.decodeStrings(null) == null, "SS5. cú pháp sai / sai kiểu / rác → null");

        List<PersistCodec.Playlist> pl = Arrays.asList(new PersistCodec.Playlist("Buổi \"thử\" dàn", Arrays.asList("/a", "smb://x/y"), 1700000000123L), new PersistCodec.Playlist("Rỗng", new ArrayList<String>(), 0));
        List<PersistCodec.Playlist> back = PersistCodec.decodePlaylists(PersistCodec.encodePlaylists(pl));
        check(back != null && back.size() == 2 && back.get(0).name.equals("Buổi \"thử\" dàn") && back.get(0).paths.equals(pl.get(0).paths) && back.get(0).at == 1700000000123L && back.get(1).paths.isEmpty(), "SS6. playlist round-trip (thứ tự, tên, thời điểm, playlist rỗng)");
        List<PersistCodec.Playlist> old = PersistCodec.decodePlaylists("[{\"n\":\"AI mix\",\"t\":1690000000000,\"p\":[\"\\/storage\\/a.flac\",\"\\/storage\\/b.flac\"]}]");
        check(old != null && old.size() == 1 && old.get(0).name.equals("AI mix") && old.get(0).at == 1690000000000L && old.get(0).paths.size() == 2 && old.get(0).paths.get(0).equals("/storage/a.flac"), "SS7. playlist định dạng cũ (org.json) đọc đúng");
        check(PersistCodec.decodePlaylists("[{\"n\":\"x\",\"p\":[\"/a\"]}]").get(0).at == 0, "SS8. thiếu 't' → 0 (như optLong)");
        String[] badPl = {"[{\"n\":\"x\"}]", "[{\"p\":[]}]", "[{\"n\":1,\"p\":[]}]", "[{\"n\":\"x\",\"p\":[1]}]", "[{\"n\":\"x\",\"p\":\"a\"}]", "[1]", "[{\"n\":\"x\",\"p\":[],\"t\":\"abc\"}]", "[{\"n\":\"x\",\"p\":[]}", "{\"n\":\"x\"}", ""};
        ok = true;
        for (String b : badPl) if (PersistCodec.decodePlaylists(b) != null) { ok = false; System.out.println("  chấp nhận nhầm: " + b); }
        check(ok, "SS9. playlist thiếu trường / sai kiểu / cụt → null (không bỏ lẳng lặng từng mục)");
        String pj = PersistCodec.encodePlaylists(pl); ok = true;
        for (int i = 0; i < pj.length(); i++) if (PersistCodec.decodePlaylists(pj.substring(0, i)) != null) ok = false;
        check(ok, "SS10. mọi điểm cắt cụt của playlist đều bị từ chối");

        // Recents tối đa 30
        List<String> r = new ArrayList<>();
        for (int i = 0; i < 100; i++) r = PersistCodec.pushRecent(r, "/t" + i, 30);
        check(r.size() == 30 && r.get(0).equals("/t99") && r.get(29).equals("/t70"), "SS11. Recents giới hạn 30, bài mới nhất đứng đầu");
        r = PersistCodec.pushRecent(r, "/t80", 30);
        check(r.size() == 30 && r.get(0).equals("/t80") && r.indexOf("/t80") == r.lastIndexOf("/t80") && r.get(1).equals("/t99"), "SS12. phát lại bài đã có → lên đầu, không trùng, vẫn 30");
        check(PersistCodec.pushRecent(new ArrayList<String>(), "/a", 30).equals(Arrays.asList("/a")), "SS13. Recents từ rỗng");
    }

    static void store() {
        // Chưa có dữ liệu
        Mem b = new Mem(); SafeJsonStore s = st(b);
        SafeJsonStore.Loaded L = s.load();
        check(L.status == SafeJsonStore.Status.ABSENT && L.value == null && s.isWritable() && b.puts == 0, "SS14. chưa có dữ liệu → ABSENT, ghi được, load không ghi gì");
        check(s.write("[\"/a\"]") && "[\"/a\"]".equals(b.m.get("v")) && b.m.get("v.bak") == null, "SS15. ghi lần đầu OK, chưa có bản sao lưu");
        check(s.write("[\"/a\",\"/b\"]") && "[\"/a\"]".equals(b.m.get("v.bak")) && "[\"/a\",\"/b\"]".equals(b.m.get("v")), "SS16. ghi lần hai: bản cũ hợp lệ được chuyển sang .bak cùng lô");
        int p = b.puts;
        check(s.write("[\"/a\",\"/b\"]") && b.puts == p, "SS17. ghi giá trị y hệt → không ghi lại");
        check(s.write("[]") && "[]".equals(b.m.get("v")) && "[\"/a\",\"/b\"]".equals(b.m.get("v.bak")), "SS18. rỗng hợp lệ ghi được (người dùng bỏ hết yêu thích)");
        // khởi động lại
        SafeJsonStore s2 = st(b); L = s2.load();
        check(L.status == SafeJsonStore.Status.OK && "[]".equals(L.value), "SS19. khởi động lại: đọc đúng, rỗng hợp lệ ≠ lỗi");

        // Dữ liệu mới không hợp lệ bị từ chối, dữ liệu tốt giữ nguyên
        b = new Mem(); b.m.put("v", "[\"/a\"]"); s = st(b); s.load();
        check(!s.write("[\"/a\"") && !s.write(null) && !s.write("{}") && "[\"/a\"]".equals(b.m.get("v")) && b.m.get("v.bak") == null && b.puts == 0, "SS20. dữ liệu mới không hợp lệ (cụt/null/sai kiểu) bị từ chối, dữ liệu tốt không đổi");

        // Chính hỏng, backup hợp lệ
        b = new Mem(); b.m.put("v", "[\"/a\",\"/b"); b.m.put("v.bak", "[\"/a\"]"); s = st(b); L = s.load();
        check(L.status == SafeJsonStore.Status.RECOVERED && "[\"/a\"]".equals(L.value) && s.isWritable() && "[\"/a\",\"/b".equals(b.m.get("v")), "SS21. chính cụt + backup hợp lệ → RECOVERED, load không sửa gì");
        check(s.write("[\"/a\",\"/c\"]") && "[\"/a\",\"/c\"]".equals(b.m.get("v")) && "[\"/a\",\"/b".equals(b.m.get("v.corrupt")) && "[\"/a\"]".equals(b.m.get("v.bak")), "SS22. ghi sau khôi phục: bản hỏng được GIỮ trong v.corrupt, backup tốt không bị đè");
        // Chính thiếu, backup hợp lệ
        b = new Mem(); b.m.put("v.bak", "[\"/z\"]"); s = st(b); L = s.load();
        check(L.status == SafeJsonStore.Status.RECOVERED && "[\"/z\"]".equals(L.value), "SS23. chính thiếu + backup hợp lệ → RECOVERED");

        // Cả hai hỏng / chính hỏng không có backup → khoá ghi, giữ nguyên
        b = new Mem(); b.m.put("v", "[\"/a\""); b.m.put("v.bak", "xyz"); s = st(b); L = s.load();
        check(L.status == SafeJsonStore.Status.CORRUPT && L.value == null && !s.isWritable(), "SS24. cả hai hỏng → CORRUPT, khoá ghi");
        check(!s.write("[\"/new\"]") && "[\"/a\"".equals(b.m.get("v")) && "xyz".equals(b.m.get("v.bak")) && b.puts == 0 && b.m.get("v.corrupt") == null, "SS25. khoá ghi: từ chối mọi lần ghi, cả hai bản thô còn nguyên");
        b = new Mem(); b.m.put("v", "[\"/a\""); s = st(b);
        check(s.load().status == SafeJsonStore.Status.CORRUPT && !s.write("[]") && "[\"/a\"".equals(b.m.get("v")), "SS26. chính hỏng, không có backup → khoá ghi, không biến thành rỗng");
        b = new Mem(); b.m.put("v", "[]"); b.m.put("v.bak", "[\"/old\"]"); s = st(b);
        check(s.load().status == SafeJsonStore.Status.OK && "[]".equals(s.load().value), "SS27. chính rỗng hợp lệ + backup có dữ liệu → dùng chính (rỗng là có chủ ý)");

        // Backend lỗi đọc
        b = new Mem(); b.m.put("v", "[\"/a\"]"); b.throwGet = true; s = st(b);
        check(s.load().status == SafeJsonStore.Status.UNREADABLE && !s.isWritable() && !s.write("[]") && b.puts == 0, "SS28. không đọc được backend → UNREADABLE, khoá ghi, không crash");

        // Ghi thất bại: dữ liệu cũ nguyên vẹn, thử lại được
        b = new Mem(); b.m.put("v", "[\"/a\"]"); s = st(b); s.load();
        b.failPut = true;
        check(!s.write("[\"/a\",\"/b\"]") && "[\"/a\"]".equals(b.m.get("v")) && s.lastError() != null, "SS29. ghi thất bại (commit=false) → false, dữ liệu cũ nguyên vẹn");
        b.failPut = false; b.throwPut = true;
        check(!s.write("[\"/a\",\"/b\"]") && "[\"/a\"]".equals(b.m.get("v")), "SS30. backend ném exception khi ghi → false, không lan ra ngoài, dữ liệu cũ nguyên");
        b.throwPut = false; b.dropPut = true;
        check(!s.write("[\"/a\",\"/b\"]") && "[\"/a\"]".equals(b.m.get("v")), "SS31. ghi báo thành công nhưng đọc lại không khớp → coi là thất bại");
        b.dropPut = false;
        check(s.write("[\"/a\",\"/b\"]") && "[\"/a\"]".equals(b.m.get("v.bak")), "SS32. sau lỗi, lần ghi kế tiếp thành công và backup đúng (không bị lệch do lần lỗi)");

        // Ứng dụng bị dừng giữa chừng: (a) trước commit → trạng thái cũ; (b) file chính cụt + backup tốt → khôi phục
        b = new Mem(); s = st(b); s.write("[\"/1\"]"); s.write("[\"/1\",\"/2\"]");
        String trunc = "[\"/1\",\"/2\",\"/3\"]".substring(0, 9);
        b.m.put("v", trunc);                                   // mô phỏng file chính bị ghi dở
        L = st(b).load();
        check(L.status == SafeJsonStore.Status.RECOVERED && "[\"/1\"]".equals(L.value), "SS33. dừng giữa chừng làm chính cụt → khôi phục bản trước đó từ .bak");

        // Đồng thời: nhiều luồng ghi, mọi lần ghi thành công, kết quả cuối hợp lệ, backup hợp lệ, không mất cập nhật cuối
        b = new Mem(); final SafeJsonStore cs = st(b); cs.load();
        final int T = 8, N = 150; final boolean[] okAll = {true};
        Thread[] th = new Thread[T];
        for (int t = 0; t < T; t++) {
            final int id = t;
            th[t] = new Thread(() -> {
                for (int i = 0; i < N; i++) {
                    List<String> l = new ArrayList<>();
                    for (int k = 0; k <= i % 7; k++) l.add("/t" + id + "/" + i + "/" + k);
                    if (!cs.write(PersistCodec.encodeStrings(l))) okAll[0] = false;
                }
            });
            th[t].start();
        }
        try { for (Thread t : th) t.join(); } catch (InterruptedException e) { okAll[0] = false; }
        String fin = b.m.get("v"), bk = b.m.get("v.bak");
        check(okAll[0] && PersistCodec.isStrings(fin) && PersistCodec.isStrings(bk) && !fin.equals(bk), "SS34. 8 luồng ghi đồng thời: mọi lần ghi OK, chính và backup đều hợp lệ (không xen kẽ/hỏng)");
        check(cs.write("[\"/cuoi\"]") && "[\"/cuoi\"]".equals(b.m.get("v")) && fin.equals(b.m.get("v.bak")), "SS35. sau đồng thời, ghi tiếp vẫn đúng chuỗi chính→backup");

        // Playlist qua kho: ghi → khởi động lại → đọc lại; dữ liệu cũ vẫn đọc được qua kho
        b = new Mem();
        b.m.put("v", "[{\"n\":\"AI mix\",\"t\":5,\"p\":[\"\\/a\"]}]");
        SafeJsonStore ps = new SafeJsonStore(b, PersistCodec::isPlaylists, logs::add, "P");
        L = ps.load();
        List<PersistCodec.Playlist> got = PersistCodec.decodePlaylists(L.value);
        check(L.status == SafeJsonStore.Status.OK && got.size() == 1 && got.get(0).paths.get(0).equals("/a"), "SS36. dữ liệu playlist cũ đọc được qua kho, không cần chuyển đổi");
        List<PersistCodec.Playlist> upd = new ArrayList<>(); upd.add(new PersistCodec.Playlist("Mới", Arrays.asList("/n1", "/n2"), 9)); upd.addAll(got);
        check(ps.write(PersistCodec.encodePlaylists(upd)), "SS37. ghi playlist mới (thêm vào đầu)");
        SafeJsonStore ps2 = new SafeJsonStore(b, PersistCodec::isPlaylists, logs::add, "P");
        got = PersistCodec.decodePlaylists(ps2.load().value);
        check(got.size() == 2 && got.get(0).name.equals("Mới") && got.get(1).name.equals("AI mix") && b.m.get("v.bak").contains("AI mix"), "SS38. khởi động lại: đủ 2 playlist đúng thứ tự; bản cũ nằm trong .bak");
        // Validator playlist từ chối ghi đè bằng JSON yêu thích
        check(!ps2.write("[\"/a\"]"), "SS39. ghi sai định dạng vào kho playlist bị từ chối");
        check(!logs.isEmpty(), "SS40. các từ chối/lỗi đều được ghi log (không nuốt im lặng)");
    }

    static void writer() {
        // Chuỗi: A ok, B thất bại (RAM có B), thử lại → B được ghi, backup = A
        Mem b = new Mem(); final SafeJsonStore s = st(b); s.load();
        final LatestWriter w = new LatestWriter();
        LatestWriter.Sink sink = new LatestWriter.Sink() { public boolean write(String r) { return s.write(r); } public boolean canRetry() { return s.isWritable(); } };
        w.submit("[\"A\"]"); check(!w.runOnce(sink) && "[\"A\"]".equals(b.m.get("v")), "LW1. ghi A thành công, không cần thử lại");
        b.failPut = true;
        w.submit("[\"B\"]"); check(w.runOnce(sink) && "[\"A\"]".equals(b.m.get("v")) && b.m.get("v.bak") == null, "LW2. ghi B thất bại → yêu cầu thử lại, A nguyên vẹn, không sinh backup rỗng/lệch");
        b.failPut = false;
        check(!w.runOnce(sink) && "[\"B\"]".equals(b.m.get("v")) && "[\"A\"]".equals(b.m.get("v.bak")), "LW3. thử lại thành công: B được lưu, backup = A");
        // Bản mới hơn đến trong lúc chờ thử lại thì thay bản đang thử lại (không ghi bản cũ sau bản mới)
        b.failPut = true;
        w.submit("[\"C\"]"); check(w.runOnce(sink), "LW4. C thất bại, chờ thử lại");
        w.submit("[\"D\"]"); b.failPut = false;
        check(!w.runOnce(sink) && "[\"D\"]".equals(b.m.get("v")), "LW5. D (mới hơn) thay C: kết quả cuối là D");
        check(!w.runOnce(sink) && "[\"D\"]".equals(b.m.get("v")), "LW6. không còn gì để ghi → không làm gì");
        // Thử lại có giới hạn
        b.failPut = true; w.submit("[\"E\"]"); int n = 1;
        while (w.runOnce(sink)) n++;
        check(n == LatestWriter.MAX_ATTEMPTS && "[\"D\"]".equals(b.m.get("v")), "LW7. thất bại liên tục: dừng sau " + LatestWriter.MAX_ATTEMPTS + " lần, dữ liệu tốt không đổi");
        b.failPut = false; w.submit("[\"F\"]");
        check(!w.runOnce(sink) && "[\"F\"]".equals(b.m.get("v")), "LW8. sau khi hết lượt, lần ghi mới vẫn thành công (bộ đếm được reset)");
        // Kho bị khoá: không thử lại
        Mem c = new Mem(); c.m.put("v", "[\"x\""); final SafeJsonStore cs = st(c); cs.load();
        LatestWriter w2 = new LatestWriter();
        LatestWriter.Sink sk2 = new LatestWriter.Sink() { public boolean write(String r) { return cs.write(r); } public boolean canRetry() { return cs.isWritable(); } };
        w2.submit("[\"y\"]");
        check(!w2.runOnce(sk2) && "[\"x\"".equals(c.m.get("v")) && c.puts == 0, "LW9. kho khoá: không thử lại, không ghi, dữ liệu thô nguyên vẹn");
        // Log từ chối chỉ một lần mỗi lần khoá
        logs.clear();
        for (int i = 0; i < 20; i++) cs.write("[\"z" + i + "\"]");
        int refusals = 0; for (String l : logs) if (l.contains("từ chối ghi")) refusals++;
        check(refusals == 0, "LW10. đã ghi log từ chối rồi thì các lần sau không lặp (không spam nhật ký)");
        logs.clear(); Mem d = new Mem(); d.m.put("v", "[\"x\""); SafeJsonStore ds = st(d); ds.load(); ds.write("[]"); ds.write("[]");
        refusals = 0; for (String l : logs) if (l.contains("từ chối ghi")) refusals++;
        check(refusals == 1, "LW11. lần từ chối đầu tiên được ghi log đúng một lần");
        // Phục hồi từ backup rồi ghi mới, rồi khởi động lại: không bao giờ đè backup tốt bằng dữ liệu hỏng
        Mem e = new Mem(); e.m.put("v", "[\"A\""); e.m.put("v.bak", "[\"A0\"]");
        SafeJsonStore es = st(e); es.load(); es.write("[\"B\"]"); es.write("[\"C\"]");
        check("[\"C\"]".equals(e.m.get("v")) && "[\"B\"]".equals(e.m.get("v.bak")) && "[\"A\"".equals(e.m.get("v.corrupt")), "LW12. hỏng→phục hồi→ghi B→ghi C: chính=C, backup=B (hợp lệ), bản hỏng còn trong v.corrupt");
        SafeJsonStore es2 = st(e);
        check("[\"C\"]".equals(es2.load().value) && es2.status() == SafeJsonStore.Status.OK, "LW13. khởi động lại sau chuỗi trên: đọc C, trạng thái OK");
    }
}
