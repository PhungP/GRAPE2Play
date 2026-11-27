package com.grape.play;

/**
 * Thứ tự bài trong album/nhóm (thuần Java, có test): đĩa → số bài → tên "tự nhiên" (số được so theo giá trị: "2" trước "10").
 * Bài không có số đĩa coi như đĩa 1; bài không có số bài xếp sau các bài có số.
 */
public final class TrackOrder {
    private TrackOrder() {}

    public static int compare(int discA, int noA, String keyA, int discB, int noB, String keyB) {
        int da = discA > 0 ? discA : 1, db = discB > 0 ? discB : 1;
        if (da != db) return da < db ? -1 : 1;
        int na = noA > 0 ? noA : Integer.MAX_VALUE, nb = noB > 0 ? noB : Integer.MAX_VALUE;
        if (na != nb) return na < nb ? -1 : 1;
        return natural(keyA, keyB);
    }

    /** So sánh không phân biệt hoa/thường, chuỗi chữ số được so theo giá trị (bỏ số 0 đầu), kết quả ổn định cho chuỗi bằng nhau về giá trị. */
    public static int natural(String a, String b) {
        int i = 0, j = 0, la = a.length(), lb = b.length();
        while (i < la && j < lb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (si < la && a.charAt(si) == '0') si++;
                while (sj < lb && b.charAt(sj) == '0') sj++;
                int ei = si, ej = sj;
                while (ei < la && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < lb && Character.isDigit(b.charAt(ej))) ej++;
                int lenA = ei - si, lenB = ej - sj;
                if (lenA != lenB) return lenA < lenB ? -1 : 1;
                for (int k = 0; k < lenA; k++) { char x = a.charAt(si + k), y = b.charAt(sj + k); if (x != y) return x < y ? -1 : 1; }
                i = ei; j = ej;
            } else {
                char x = Character.toLowerCase(ca), y = Character.toLowerCase(cb);
                if (x != y) return x < y ? -1 : 1;
                i++; j++;
            }
        }
        if (la - i != lb - j) return (la - i) < (lb - j) ? -1 : 1;
        return a.compareTo(b);
    }
}
