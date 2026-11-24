#!/bin/sh
# Chạy test logic thuần (không cần Android SDK): DspMath, PlayLogic, DeviceMatch, BitPerfect.
# Cần JDK (javac/java). Kết quả: dòng "passed=N failed=M"; thoát mã 0 khi failed=0.
set -e
D=$(cd "$(dirname "$0")" && pwd)
S="$D/app/src"
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" "$S"/main/java/com/grape/play/DspMath.java "$S"/main/java/com/grape/play/PlayLogic.java "$S"/main/java/com/grape/play/DeviceMatch.java "$S"/main/java/com/grape/play/BitPerfect.java "$S"/test/java/com/grape/play/AudioCoreTests.java "$S"/test/java/com/grape/play/BitPerfectTests.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" com.grape.play.AudioCoreTests
