# GRAPE2Play — Sẵn sàng phát hành (chuẩn bị, CHƯA phát hành)

Repo: `PhungP/GRAPE2Play` (remote `origin`). Bản đang phát hành: **v3.0.5** (versionCode 108). Mã nguồn đã nâng lên **3.0.6 / 109**.

## Phiên bản (đã được chủ dự án phê duyệt)
`versionName 3.0.6`, `versionCode 109`. `Updater` so tag với versionName (`v3.0.6`) và cần đúng **một** tệp `.apk` đính kèm.

## Điều kiện còn thiếu để phát hành chính thức
1. Keystore release của chủ dự án (chứng chỉ SHA-256 mong đợi `853ecf48…06f4`) — không có trên máy này.
2. Xác nhận bản phát hành và số phiên bản.
3. Kiểm tra trên thiết bị thật (`docs/REAL_DEVICE_TEST_PLAN.md`) — tối thiểu A1–A4, B1–B3, G1 (cài đè giữ dữ liệu) trước khi gọi là Stable.
4. Quyết định về bản build release chưa thu gọn (R8 đang tắt, xem dưới) và các cảnh báo lint đã biết.

## Các lệnh (chủ dự án chạy; mật khẩu chỉ gõ tại máy, không ghi vào repo/log/lệnh)
```bash
cd <thư mục gốc của repo GRAPE2Play đã clone>
git status --short                    # phải sạch
# 1) Version 3.0.6/109, CHANGELOG, RELEASE_NOTES đã được cập nhật trong mã nguồn.
./run_core_tests.sh                   # kỳ vọng passed=… failed=0
# 2) Build release có ký
export GRAPE_KEYSTORE=/đường/dẫn/khoá.jks GRAPE_KEY_ALIAS=<alias>
read -rs GRAPE_KEYSTORE_PASSWORD; export GRAPE_KEYSTORE_PASSWORD
~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle clean :app:testDebugUnitTest :app:assembleRelease
unset GRAPE_KEYSTORE_PASSWORD
APK=app/build/outputs/apk/release/app-release.apk
# 3) Xác minh chữ ký + tương thích cài đè với bản 3.0.5 đã phát hành
gh release download v3.0.5 -R PhungP/GRAPE2Play -p '*.apk' -D /tmp/old305
scripts/verify_ota.sh /tmp/old305/*.apk "$APK"        # phải thoát 0
cp "$APK" GRAPE2Play-3.0.6.apk && shasum -a 256 GRAPE2Play-3.0.6.apk
# 4) Thử trên thiết bị: adb install -r GRAPE2Play-3.0.6.apk (đè lên 3.0.5), mở app, kiểm dữ liệu (ACCEPTANCE_GUIDE §9)
# 5) Phát hành (chỉ khi mọi điều kiện đạt)
git push origin main
git tag -a v3.0.6 -m "GRAPE2Play 3.0.6" && git push origin v3.0.6
# Ghi chú phát hành lấy từ mục 3.0.6 của RELEASE_NOTES.md (phần trước dòng '---' đầu tiên); không duy trì bản sao riêng
awk '/^---$/{exit} {print}' RELEASE_NOTES.md > release-notes-3.0.6.md
gh release create v3.0.6 GRAPE2Play-3.0.6.apk -R PhungP/GRAPE2Play --title "GRAPE2Play 3.0.6" --notes-file release-notes-3.0.6.md --latest
```
Không dùng `--prerelease`/`--draft`. Không commit tệp `.apk`/keystore (`.apk` đã bị `.gitignore`); xoá `release-notes-3.0.6.md` sau khi dùng.

## Kiểm tra tự động đã chạy (tại thời điểm soạn)
Xem mục "Kết quả" trong `docs/CHECKPOINT_phase1-4_2026-11-27.md`. Release không ký: `app-release-unsigned.apk` (≈17 MB, 3 dex, không có thư viện native; `apksigner verify` báo không ký — đúng mong đợi).

## Cấu hình/Rủi ro đã biết (không chặn nhưng cần biết)
- **R8/ProGuard tắt** (`isMinifyEnabled=false`, không có tệp rules): APK lớn (≈17 MB; dex ≈ 52 MB giải nén) vì kèm BouncyCastle (qua smbj). Bật R8 là thay đổi rủi ro cao (phản chiếu của smbj/okhttp/Compose) và không thể kiểm trên thiết bị lúc này → để cho bản sau, có thử nghiệm.
- `CastClient` dùng TrustManager chấp nhận mọi chứng chỉ khi nối thiết bị Cast trong LAN (cổng 8009 dùng chứng chỉ tự ký theo giao thức Cast). Chỉ dùng trong mạng nội bộ; nếu cần thắt chặt phải ghim chứng chỉ thiết bị.
- `usesCleartextTraffic=true` (NAS WebDAV/UPnP qua HTTP trong LAN); `CoverProvider`/`LibraryProvider` exported (launcher TV đọc; LibraryProvider chỉ trả tên album/nghệ sĩ/ảnh bìa ≤256 px, không đường dẫn).
- Lint (`lintDebug`): còn 149 lỗi, **đều đã có ở 3.0.5 và không phải lỗi chạy**: ~137 `UnsafeOptInUsageError` (media3 `@UnstableApi`, mã vẫn biên dịch/chạy đúng) và RestrictedApi (tvprovider, `dispatchKeyEvent`) là dương tính giả/đã biết. Không sửa hàng loạt trong đợt này để tránh rủi ro.
- Hạ cấp về bản cũ không được hỗ trợ (bí mật đã mã hoá bằng Keystore).
