# Checklist phát hành 2.8.0

## Trong Android Studio
- [ ] Mở dự án, Sync Gradle (cần mạng). Build: `./gradlew assembleDebug` → gửi lại toàn bộ log lỗi nếu có.
- [ ] `./gradlew testDebugUnitTest` (hiện chưa có test nào).
- [ ] Cài đè lên bản 2.7.3 (không gỡ): kiểm tra thư viện, Yêu thích, Gần đây, Thói quen, khóa AI còn nguyên.
- [ ] Chạy các kịch bản trong TEST_REPORT (1–11) trên TV và điện thoại.
- [ ] Sleep Timer: đặt âm lượng khác 100%, hẹn 1 phút → dừng đúng giờ, âm lượng không đổi.
- [ ] Rút USB rồi quét → bài USB không biến mất; hủy quét NAS → không mất bài.

## Phát hành
- [ ] Dùng đúng keystore đang dùng cho 2.7.3 (không tạo khóa mới).
- [ ] `./gradlew assembleRelease` (và `bundleRelease` nếu cần AAB), ký bằng keystore cũ.
- [ ] Xác nhận applicationId com.grape.play, versionName 2.8.0, versionCode 84.
- [ ] Kiểm tra không có khóa API trong mã/APK.

## Quyết định cần từ bạn
- [ ] Ảnh bìa web (Deezer/iTunes): giữ mặc định bật, hay chuyển sang tắt/hỏi xác nhận?
- Shazam: đã quyết định bỏ khỏi 2.8.0.
