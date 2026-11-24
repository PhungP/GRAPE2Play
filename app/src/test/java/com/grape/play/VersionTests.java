package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

import java.util.Arrays;
import java.util.List;

/** Test so sánh phiên bản và lọc danh sách rollback. */
final class VersionTests {
    private VersionTests() {}
    static void run() {
        check(Versions.isNewer("0.8.10", "0.8.9") && !Versions.isNewer("0.8.9", "0.8.10"), "VER1. 0.8.10 > 0.8.9 (so từng số)");
        check(Versions.compare("v2.9.4", "2.9.4") == 0 && Versions.compare("V.2.9.5", "2.9.4") > 0, "VER2. tiền tố v/V/v. được bỏ qua");
        check(Versions.compare("2.9", "2.9.0") == 0 && Versions.compare("2.9.1", "2.9") > 0, "VER3. thiếu số coi như 0");
        check(Versions.compare("", "0") == 0 && Versions.compare(null, "1") < 0, "VER4. rỗng/null không ném lỗi");
        List<String> all = Arrays.asList("v2.9.5", "v2.9.4", "2.9.3", "v2.9.3", "v2.8.9", "v2.10.0", "rác", "v2.9.0");
        List<String> old = Versions.olderThan("2.9.5", all, 3);
        check(old.equals(Arrays.asList("v2.9.4", "2.9.3", "v2.9.0")) || old.equals(Arrays.asList("v2.9.4", "v2.9.3", "v2.9.0")), "VER5. olderThan: chỉ bản cũ hơn, mới nhất trước, bỏ trùng, giới hạn 3 (" + old + ")");
        check(!Versions.olderThan("2.9.5", all, 10).contains("v2.9.5") && !Versions.olderThan("2.9.5", all, 10).contains("v2.10.0"), "VER6. không gồm bản hiện tại hoặc bản mới hơn");
        check(Versions.olderThan("1.0.0", all, 10).isEmpty(), "VER7. không có bản cũ hơn → rỗng");
    }
}
