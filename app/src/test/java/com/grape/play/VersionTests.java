package com.grape.play;

import static com.grape.play.AudioCoreTests.check;

/** Test so sánh phiên bản . */
final class VersionTests {
    private VersionTests() {}
    static void run() {
        check(Versions.isNewer("0.8.10", "0.8.9") && !Versions.isNewer("0.8.9", "0.8.10"), "VER1. 0.8.10 > 0.8.9 (so từng số)");
        check(Versions.compare("v2.9.4", "2.9.4") == 0 && Versions.compare("V.2.9.5", "2.9.4") > 0, "VER2. tiền tố v/V/v. được bỏ qua");
        check(Versions.compare("2.9", "2.9.0") == 0 && Versions.compare("2.9.1", "2.9") > 0, "VER3. thiếu số coi như 0");
        check(Versions.compare("", "0") == 0 && Versions.compare(null, "1") < 0, "VER4. rỗng/null không ném lỗi");
    }
}
