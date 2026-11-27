package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Phase 3: tìm kiếm thư viện (SearchIndex) và thứ tự bài (TrackOrder). */
final class LibraryOrderTests {
    private LibraryOrderTests() {}
    static String[] row(String t, String a, String al, String x) { return new String[]{t, a, al, x}; }
    static List<Integer> list(int[] a) { List<Integer> l = new ArrayList<>(); for (int v : a) l.add(v); return l; }

    static void run() { search(); order(); }

    static void search() {
        check(SearchIndex.fold("Đặng Thái Sơn").equals("dang thai son") && SearchIndex.fold("Việt Nam – Ảnh").equals("viet nam – anh") && SearchIndex.fold(null).isEmpty() && SearchIndex.fold("").isEmpty(), "LS1. fold: bỏ dấu tiếng Việt, đ→d, chữ thường, null/rỗng an toàn");
        List<String[]> rows = Arrays.asList(
            row("Việt Nam Quê Hương Tôi", "Hồng Nhung", "Quê Hương", "Pop"),     // 0
            row("Hello", "Adele", "25", "Pop"),                                    // 1
            row("Someone Like You", "Adele", "21", "Soul"),                        // 2
            row("Hello Goodbye", "The Beatles", "Magical Mystery Tour", "Rock"),  // 3
            row("Yesterday", "The Beatles", "Help!", "Rock"),                      // 4
            row("Night", "Nguyễn Văn A", "Việt", ""));                             // 5
        SearchIndex ix = new SearchIndex(rows);
        check(ix.size() == 6, "LS2. chỉ mục đủ số dòng");
        check(list(ix.search("viet", 10)).equals(Arrays.asList(0, 5)), "LS3. 'viet' khớp 'Việt' (không dấu), tên bài xếp trước album");
        check(list(ix.search("HELLO", 10)).equals(Arrays.asList(1, 3)), "LS4. không phân biệt hoa/thường; hai bài, thứ tự gốc khi bằng điểm");
        check(list(ix.search("adele hello", 10)).equals(Arrays.asList(1)), "LS5. nhiều từ = AND ở các trường khác nhau (tên bài + nghệ sĩ)");
        check(list(ix.search("beatles", 10)).equals(Arrays.asList(3, 4)) && list(ix.search("rock", 10)).equals(Arrays.asList(3, 4)), "LS6. khớp theo nghệ sĩ và theo trường phụ (thể loại)");
        check(ix.search("zzz", 10).length == 0 && ix.search("", 10).length == 0 && ix.search("   ", 10).length == 0 && ix.search(null, 10).length == 0 && ix.search("hello", 0).length == 0, "LS7. không khớp / rỗng / khoảng trắng / null / limit 0 → rỗng");
        check(ix.search("hello", 1).length == 1 && ix.search("hello", 1)[0] == 1, "LS8. giới hạn kết quả giữ bài tốt nhất");
        // xếp hạng: tên bài bắt đầu bằng từ khoá > có từ khoá ở đầu từ > có trong tên > nghệ sĩ
        SearchIndex rk = new SearchIndex(Arrays.asList(row("Xyzlove", "A", "", ""), row("Pure Love Song", "B", "", ""), row("Lovely Day", "C", "", ""), row("Other", "Love Band", "", "")));
        check(list(rk.search("love", 10)).equals(Arrays.asList(2, 1, 0, 3)), "LS9. xếp hạng: bắt đầu bằng từ khoá > đầu một từ > chứa > nghệ sĩ");
        check(list(new SearchIndex(Arrays.asList(new String[]{"Chỉ tên"}, new String[0])).search("chi", 5)).equals(Arrays.asList(0)), "LS10. dòng thiếu trường không gây lỗi");
        // thư viện lớn: đúng đắn, không phụ thuộc thời gian
        List<String[]> big = new ArrayList<>(); for (int i = 0; i < 60000; i++) big.add(row("Bài " + i, "Nghệ sĩ " + (i % 300), "Album " + (i % 5000), i % 2 == 0 ? "Rock" : "Pop"));
        SearchIndex bi = new SearchIndex(big);
        // Đối chiếu vét cạn: dòng khớp khi MỌI từ có mặt trong ít nhất một trường (kể cả "299" trong tên bài như "Bài 2990")
        String[] tk = {"nghe", "si", "299", "bai"}; List<Integer> expect = new ArrayList<>();
        for (int i = 0; i < big.size(); i++) {
            boolean all = true;
            for (String t : tk) { boolean in = false; for (String f : big.get(i)) if (SearchIndex.fold(f).contains(t)) in = true; if (!in) all = false; }
            if (all) expect.add(i);
        }
        int[] r = bi.search("nghe si 299 bai", 100000);
        List<Integer> got = list(r); Collections.sort(got);
        check(got.equals(expect) && expect.size() > 200, "LS11. 60.000 bài: kết quả nhiều từ trùng khớp vét cạn (" + expect.size() + " bài, không sót, không thừa)");
        check(bi.search("bai 5", 100000).length > 0, "LS12. truy vấn rộng trả kết quả");
        int[] all = bi.search("album", 100000); boolean distinct = true; boolean[] seen = new boolean[60000];
        for (int idx : all) { if (seen[idx]) distinct = false; seen[idx] = true; }
        check(all.length == 60000 && distinct, "LS13. 60.000 kết quả: mỗi bài đúng một lần (kiểm tra mở rộng bộ đệm xếp hạng)");
    }

    static void order() {
        check(TrackOrder.natural("Track 2", "Track 10") < 0 && TrackOrder.natural("Track 10", "Track 2") > 0 && TrackOrder.natural("a1", "b1") < 0 && TrackOrder.natural("A1", "a2") < 0, "TO1. tên tự nhiên: 2 trước 10, hoa/thường không đổi thứ tự tương đối");
        check(TrackOrder.natural("01 Intro", "2 Intro") < 0 && TrackOrder.natural("007 x", "7 x") != 0 && TrackOrder.natural("007 x", "8 x") < 0, "TO2. số 0 đầu được bỏ khi so giá trị (007 < 8), bằng giá trị thì ổn định theo chuỗi");
        check(TrackOrder.natural("file007", "file7") != 0 && TrackOrder.natural("file007", "file8") < 0 && TrackOrder.natural("file9", "file010") < 0, "TO3. so theo giá trị số, bằng giá trị thì ổn định theo chuỗi gốc");
        check(TrackOrder.compare(1, 5, "x", 2, 1, "a") < 0 && TrackOrder.compare(2, 1, "a", 1, 5, "x") > 0, "TO4. đĩa 1 trước đĩa 2 bất kể số bài");
        check(TrackOrder.compare(0, 3, "a", 1, 3, "a") == 0 && TrackOrder.compare(0, 1, "a", 2, 1, "a") < 0, "TO5. không có số đĩa = đĩa 1");
        check(TrackOrder.compare(1, 2, "b", 1, 0, "a") < 0, "TO6. bài có số bài đứng trước bài không có số");
        check(TrackOrder.compare(1, 0, "10.mp3", 1, 0, "2.mp3") > 0 && TrackOrder.compare(1, 0, "2.mp3", 1, 0, "10.mp3") < 0, "TO7. không có số bài: theo tên tự nhiên (2 trước 10)");
        // sắp xếp một album nhiều đĩa
        List<Object[]> t = new ArrayList<>();
        t.add(new Object[]{2, 1, "d2t1"}); t.add(new Object[]{1, 10, "d1t10"}); t.add(new Object[]{1, 2, "d1t2"}); t.add(new Object[]{0, 0, "z"}); t.add(new Object[]{0, 0, "y10"}); t.add(new Object[]{0, 0, "y2"});
        Collections.sort(t, (x, y) -> TrackOrder.compare((Integer) x[0], (Integer) x[1], (String) x[2], (Integer) y[0], (Integer) y[1], (String) y[2]));
        StringBuilder sb = new StringBuilder(); for (Object[] o : t) sb.append(o[2]).append(' ');
        check(sb.toString().trim().equals("d1t2 d1t10 y2 y10 z d2t1"), "TO8. album nhiều đĩa: " + sb.toString().trim());
        // quan hệ thứ tự hợp lệ (đối xứng, bắc cầu) trên dữ liệu ngẫu nhiên — Java sort sẽ ném lỗi nếu vi phạm
        java.util.Random rnd = new java.util.Random(3); boolean ok = true;
        for (int it = 0; it < 200 && ok; it++) {
            List<Object[]> l = new ArrayList<>();
            for (int i = 0; i < 300; i++) l.add(new Object[]{rnd.nextInt(3), rnd.nextInt(4), "f" + rnd.nextInt(30) + (rnd.nextBoolean() ? "a" : "B")});
            try { Collections.sort(l, (x, y) -> TrackOrder.compare((Integer) x[0], (Integer) x[1], (String) x[2], (Integer) y[0], (Integer) y[1], (String) y[2])); } catch (IllegalArgumentException e) { ok = false; }
        }
        check(ok, "TO9. comparator hợp lệ trên 200 tập ngẫu nhiên (không vi phạm hợp đồng so sánh)");
    }
}
