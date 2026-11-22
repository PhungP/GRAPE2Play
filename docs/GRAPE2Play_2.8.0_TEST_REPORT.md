# GRAPE2Play 2.8.0 Standard — TEST REPORT

Cập nhật: Phase 2A.

## Môi trường kiểm thử hiện có
- JDK và Gradle 8.14.3 có trong môi trường làm việc, nhưng **không có Android SDK** và không tải được phụ thuộc (không có mạng cho Gradle) → **không thể build APK hay chạy test** ở đây.
- Không có thiết bị/emulator.
- Không có test tự động nào trong dự án (không có `app/src/test`, `app/src/androidTest`).

## Kết quả giai đoạn 0

| Hạng mục | Kết quả |
|----------|---------|
| Build Gradle (assembleDebug) | **Chưa chạy** (thiếu Android SDK) |
| Unit test | **Chưa chạy** (chưa có test) |
| Kiểm thử trên TV/điện thoại | **Chưa chạy** (người dùng tự kiểm thử) |
| Rà mã tĩnh: tìm mọi chỗ ghi âm lượng | Đã làm: 3 chỗ ghi `Playback.exo.volume` trong `AiExtras.kt` (dòng 53, 90, 96); không có nơi nào khác trong mã |
| Rà mã tĩnh: ảnh bìa trên mạng | Đã làm: `Covers` tự tải/lưu ảnh Deezer & iTunes, mặc định bật, không có bước xác nhận |
| Rà tài liệu ShazamKit Android | Đã đọc trang tài liệu Apple (AAR, token, định dạng PCM); chưa chạy SDK |
| Rà tài liệu Deezer | Đã đọc trang hướng dẫn và FAQ chính thức; chưa xác minh được điều khoản đầy đủ |
| Đếm cân bằng ngoặc các file đã sửa gần đây | Đã làm ở các bản trước; không phải thay thế cho biên dịch |

## Lỗi/rủi ro còn tồn tại (chưa sửa — ngoài phạm vi giai đoạn 0)
1. ~~Sleep Timer ghi âm lượng ExoPlayer~~ — đã gỡ ở Phase 2A (chờ xác nhận khi chạy thật).
2. Ảnh bìa Deezer được lưu đệm và tự cập nhật không xác nhận (Giai đoạn 4).
3. Chưa có test tự động (Giai đoạn 7).
4. Nhiều bản gần đây chưa được biên dịch trong môi trường này; cần build thật trước khi phát hành.

## Phase 2A — Sleep Timer không còn can thiệp âm lượng

### Đã thực hiện (tĩnh)
| Kiểm tra | Kết quả |
|----------|---------|
| Tìm `applyFade`, `fadeMs`, `fadeSec`, `fade_seconds`, "nhỏ dần" trong AiExtras/AiHub/LangExtra/Lang | Không còn tham chiếu |
| Tìm mọi thao tác ghi âm lượng trong toàn mã (`exo.volume`, `.volume =`, `setVolume`, `setStreamVolume`, `adjustStreamVolume`, `adjustVolume`, `SetVolume`) | Không còn dòng nào |
| Các nơi gọi `SleepTimer.start/startEndOfTrack/cancel`, `AiSleep.Plan(...)`, `AiSleep.parse/local` | Đã đối chiếu chữ ký mới: 1 nơi `start(minutes)`, 2 `Plan(...)` ở nút nhanh, 3 `Plan(...)` trong AiExtras |
| `finish()` đổi từ `suspend` sang thường | Chỉ được gọi trong `scope.launch`; `Playback.toggle()` không phải `suspend` |
| Import không dùng / thiếu | `delay`, `roundToInt` vẫn dùng; không thêm import mới |
| Cân bằng ngoặc `()` `{}` của AiExtras.kt, LangExtra.kt | Cân bằng. AiHub.kt lệch 1 `{` do ký tự `{` nằm trong chuỗi `" of {"` có từ trước (không phải lỗi mới) |
| Danh sách file thay đổi so với bản sao lưu | Chỉ AiExtras.kt, AiHub.kt, LangExtra.kt |

### Phân tích 7 kịch bản bắt buộc (suy luận từ mã, chưa chạy)
1. Đặt timer và phát nhạc: `start()` chỉ gán state và chạy vòng đếm; không gọi API âm lượng.
2. Chỉnh âm lượng thủ công khi đang đếm: timer không đọc/ghi âm lượng nên không có gì để xung đột.
3. Hủy timer: `cancel()` chỉ hủy job và xóa state; không khôi phục âm lượng.
4. Đặt lại timer: `start()` gọi `cancel()` rồi đếm lại; không ghi âm lượng.
5. Đổi thời gian: như 4.
6. Hết giờ: `finish()` gọi `Playback.toggle()` nếu đang phát, rồi cập nhật state.
7. Dừng khi hết bài: vòng 500 ms so sánh vị trí/độ dài; còn ≤ 400 ms hoặc đổi bài thì `finish()`; không fade.

### Chưa thể thực hiện
- Build APK, chạy ứng dụng, thử 7 kịch bản trên thiết bị thật (không có Android SDK/thiết bị).
- Quan sát thực tế âm lượng khi timer chạy trên TV, UPnP và Cast.

### Việc người dùng nên kiểm tra sau khi build
Chạy 7 kịch bản ở trên với âm lượng đặt ở mức khác 100%; xác nhận âm lượng không đổi lúc đếm, lúc hủy và lúc nhạc dừng; thử cả phát qua UPnP/Cast.

## Phase 1A — kiểm tra tĩnh
Đã làm: cân bằng `{}`/`()` của `Library.kt`, `Models.kt`; rà chữ ký `Track(...)` (tham số mới ở cuối, có mặc định); `AppLog.i(tag,msg)` tồn tại; không còn tham chiếu `fromJson` kiểu cũ.
Chưa làm được: build, chạy.
Cần thử trên thiết bị: (1) cài đè 2.7.3 → thư viện, Yêu thích, Gần đây giữ nguyên; (2) quét lần 2 nhanh hơn, sửa tag 1 file → chỉ file đó đọc lại; (3) rút USB rồi quét → bài USB vẫn còn trong danh sách; (4) hủy quét NAS giữa chừng → không mất bài cũ; (5) làm hỏng thủ công `library_v4.json` → nạp từ `.bak`; (6) nguồn DLNA nạp lại từ cache vẫn phát được (mime).
Lưu ý: dấu vết size/mtime chưa áp dụng cho nguồn từ xa (không có thông tin rẻ); các nguồn đó vẫn dùng path khi quét lại.

## Rà soát tĩnh bản 2.8.0 (master task)
Đã làm: cân bằng `{}`/`()` các file sửa (Library, Models, AiIndex, FlacPcm.java, Favorites, Usage); grep không còn Fade Out/ghi `exo.volume`; manifest/quyền không đổi; không thấy khóa API/secret nhúng trong mã; không có cấu hình ký trong repo (ký do bạn); constructor `Track` ở Upnp.kt dùng tham số vị trí đến `mime` — trường mới thêm sau, tương thích.
Chưa làm được: build Gradle, unit test, Android test, chạy thiết bị (không có Android SDK, không tải được dependency). KHÔNG có kết quả build/test nào được tuyên bố.
Kịch bản cần thử thêm: (7) cài đè 2.7.3→2.8.0, quét lần đầu đọc lại thẻ, Yêu thích/Gần đây/Thói quen còn nguyên; (8) đổi tên/di chuyển 1 file → Yêu thích đi theo; (9) tìm "rock", "2005", tên nghệ sĩ album; (10) FLAC có DATE/DISCNUMBER/COMPOSER hiện đúng sau quét; (11) Sleep Timer 1 phút: âm lượng giữ nguyên.

## Kế hoạch kiểm thử các giai đoạn sau
- G1: nhóm album/nghệ sĩ với thư viện lớn; tải lại sau sửa tag; hiển thị Full Music Details.
- G2: Sleep Timer — xác nhận bằng test rằng không còn ghi âm lượng; hẹn giờ 1 phút dừng đúng hạn; hủy hẹn giờ không đổi âm lượng; phát qua UPnP/Cast.
- G3: AI trả JSON bị cắt, tiếng Anh/Việt, tự chuyển nhà cung cấp.
- G4: Shazam: tap PCM không làm đổi âm thanh đầu ra/độ trễ; Artwork: xem trước → xác nhận → ghi, hủy không ghi.
- G5: mất kết nối NAS/UPnP, đăng nhập đám mây.
- G6: VI/EN đầy đủ, các giao diện (Mặc định, Nhôm, Robot, Cổ điển, Liquid Glass).
- G7: build release, kiểm thử hồi quy trên TV và điện thoại.
