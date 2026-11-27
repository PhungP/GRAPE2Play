#!/bin/sh
# Chạy test logic thuần (không cần Android SDK): DspMath, PlayLogic, DeviceMatch, BitPerfect, DeviceRegistry, DeviceCatalog, Versions.
# Cần JDK (javac/java). Kết quả: dòng "passed=N failed=M"; thoát mã 0 khi failed=0.
set -e
D=$(cd "$(dirname "$0")" && pwd)
S="$D/app/src"
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" "$S"/main/java/com/grape/play/DspMath.java "$S"/main/java/com/grape/play/PlayLogic.java "$S"/main/java/com/grape/play/DeviceMatch.java "$S"/main/java/com/grape/play/BitPerfect.java "$S"/main/java/com/grape/play/DeviceRegistry.java "$S"/main/java/com/grape/play/Versions.java "$S"/main/java/com/grape/play/QueueWindow.java "$S"/main/java/com/grape/play/QueueStore.java "$S"/main/java/com/grape/play/QueueSession.java "$S"/main/java/com/grape/play/SafeJsonStore.java "$S"/main/java/com/grape/play/PersistCodec.java "$S"/main/java/com/grape/play/LatestWriter.java "$S"/test/java/com/grape/play/SafeStoreTests.java "$S"/test/java/com/grape/play/QueueStoreTests.java "$S"/test/java/com/grape/play/QueueSessionTests.java "$S"/test/java/com/grape/play/AudioCoreTests.java "$S"/test/java/com/grape/play/BitPerfectTests.java "$S"/test/java/com/grape/play/DeviceRegistryTests.java "$S"/main/java/com/grape/play/DeviceVerification.java "$S"/main/java/com/grape/play/DeviceCatalog.java "$S"/test/java/com/grape/play/DeviceCatalogTests.java "$S"/test/java/com/grape/play/DeviceVerificationTests.java "$S"/test/java/com/grape/play/VersionTests.java "$S"/test/java/com/grape/play/QueueWindowTests.java
java -Dgrape.assets="$S/main/assets" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" com.grape.play.AudioCoreTests
