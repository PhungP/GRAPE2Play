# GRAPE2Play 2.8.0 Standard — CHANGELOG

Định dạng: mới nhất ở trên. Chỉ ghi những gì đã thực sự thay đổi trong mã hoặc tài liệu.

## 2.8.0 (đang phát triển — chưa phát hành)

### Phase 2A — Gỡ Fade Out khỏi Sleep Timer (Completed — kiểm tra tĩnh)
Sleep Timer giờ chỉ đếm ngược rồi dừng phát; không đọc hay ghi bất kỳ âm lượng nào.
- `AiExtras.kt` — `SleepTimer`:
  - Xóa `applyFade()`, biến `fadeMs`.
  - `start(minutes, fadeSec)` → `start(minutes)`.
  - `startEndOfTrack()` không còn fade; vẫn dừng khi hết bài.
  - Xóa `Playback.exo.volume = 1f` ở `finish()` và `cancel()`; `finish()` không còn `suspend` và không còn `delay(400)` (chỉ để chờ khôi phục âm lượng).
  - Ghi chú hiển thị: "Dừng nhạc sau N phút" / "Dừng nhạc khi hết bài đang phát" (bỏ "nhỏ dần…" và chú thích "thiết bị phát ngoài: chỉ dừng").
- `AiExtras.kt` — `AiSleep`:
  - `Plan(minutes, fadeSec, endOfTrack, music, say)` → `Plan(minutes, endOfTrack, music, say)`.
  - Xóa `fade_seconds` khỏi prompt AI và khỏi phần đọc kết quả; `local()` không còn tham số fade.
- `AiHub.kt` — `SleepPage`: cập nhật 3 chỗ gọi (`SleepTimer.start`, hai `Plan(...)` của nút nhanh); đổi mô tả trang, bỏ "âm lượng nhỏ dần ở cuối".
- `LangExtra.kt` (sinh lại từ bảng dịch): xóa các bản dịch Fade Out; thêm bản dịch cho ghi chú mới.
- Không đổi: Playback Engine, EQ/DSP/Preamp/Balance/Gain, System Volume, âm lượng UPnP, AudioSettings, PCM pipeline, version (vẫn 2.7.3).
- Sao lưu trước khi sửa: bản sao thư mục dự án 2.7.3 (ngoài gói giao hàng).

### Phase 1B/1C/1D + phát hành (mã nguồn xong, chưa build)
- `Models.kt`: `Track` thêm `year`, `disc`, `albumArtist`, `composer`, `ver` (mặc định rỗng/0).
- `FlacPcm.java`: đọc COMPOSER, DISCNUMBER, DATE/YEAR/ORIGINALDATE từ Vorbis comment (chỉ khi có; năm 1000–2999).
- `Library.kt`: đọc YEAR/DISC_NUMBER/ALBUMARTIST/COMPOSER qua MediaMetadataRetriever; `META_VER = 2` → bản ghi cache cũ (ver < 2) được đọc lại một lần, đọc lỗi thì giữ thẻ cũ; lưu khóa `yr,dc,aa,cp,v` (tùy chọn, tương thích ngược); áp dụng cả nguồn từ xa; `relink()` nối lại Yêu thích/Gần đây/thống kê khi đúng một file mới khớp (kích thước + thời lượng + tên + nghệ sĩ + album) với một file biến mất — mơ hồ thì bỏ qua.
- `Favorites.kt`, `Usage.kt`: thêm `remap()` (giữ thứ tự, không xóa); `UsageStats.init` đọc từng bản ghi, bản ghi hỏng không làm mất cả thống kê.
- `AiIndex.kt`: chỉ mục tìm kiếm gồm nghệ sĩ album + năm; tìm khớp cả thể loại; chữ ký cache gồm năm. Không đổi dữ liệu gửi AI (vẫn chỉ gửi phần liên quan theo yêu cầu).
- Lịch sử nghe: đã chống ghi trùng (cửa sổ 8 giây) và Recents loại trùng — giữ nguyên.
- `build.gradle.kts`: versionName 2.8.0, versionCode 84 (applicationId không đổi).
- Một lần đọc lại toàn bộ thẻ sau khi cập nhật (do thẻ mới) — lần quét đầu sau nâng cấp sẽ lâu hơn.

### Giai đoạn 1A — Library Core & lưu trữ (mã nguồn xong, chưa build)
- `Models.kt`: `Track` thêm `size`, `mtime` (mặc định 0, thêm cuối → mọi chỗ gọi cũ không đổi).
- `Library.kt`:
  - Quét local: chỉ đọc lại file khi kích thước/thời điểm sửa đổi; cache cũ (chưa có dấu vết) giữ thẻ và được ghi dấu vết, không đọc lại hàng loạt.
  - Đọc lại lỗi (không ra thời lượng) mà đã có thẻ tốt → giữ thẻ cũ.
  - Ổ không đọc được / USB đang rút: giữ nguyên bản ghi cũ, không xóa.
  - Nguồn từ xa: quét bị dừng, có thư mục lỗi hoặc trả rỗng → gộp/giữ bản ghi cũ thay vì ghi đè bằng kết quả thiếu.
  - Ghi cache nguyên tử (file tạm → sao lưu `.bak` → đổi tên); nạp cache tự dùng `.bak` nếu file chính hỏng.
  - `fromJson` đọc từng bản ghi, bỏ qua bản ghi hỏng (có log); khóa mới tùy chọn (`mi` mime, `sz`, `mt`) → tương thích ngược; mime DLNA nay được lưu.
  - `duplicateGroups()`: báo cáo bài nghi trùng, chỉ đọc, không xóa/gộp (chưa gắn UI).
  - Không đổi `Track.path` (ID), tên file cache `library_v4.json`/`src_<id>.json`. Không cần migration.
- Không đổi: UI, Playback, EQ/DSP/AudioSettings, Sleep Timer, AI Provider. Phiên bản vẫn 2.7.3 (83).

### Giai đoạn 0 — Project Assessment (Completed)
- Thêm ba tài liệu tiến độ: `GRAPE2Play_2.8.0_ROADMAP.md`, `GRAPE2Play_2.8.0_CHANGELOG.md`, `GRAPE2Play_2.8.0_TEST_REPORT.md` (thư mục `docs/`).
- Không thay đổi mã nguồn ứng dụng. Phiên bản vẫn là 2.7.3 (versionCode 83).

## 2.7.3 (nền của 2.8.0)
- Giao diện English: AI được yêu cầu trả lời mọi nội dung hiển thị bằng tiếng Anh.

## Các mốc gần đây trước 2.8.0 (tóm tắt, chi tiết trong README.md)
- 2.7.2: dịch nhãn ô thông số trang Đang phát, dòng Tín hiệu, nhãn làm mờ nền.
- 2.7.1: điểm dừng đầu trang Cài đặt, dịch thêm ~350 chuỗi + ~140 mẫu sang English.
- 2.7.0: chế độ sáng/tối cho Liquid Glass.
- 2.6.x: giao diện Liquid Glass, kính trắng/tối cho cửa sổ AI, chuyển "Quét lại" vào Thư viện.
- 2.5.0: độ trong suốt cửa sổ AI, làm mờ nền.
- 2.4.x: Buổi thử dàn, Hẹn giờ ngủ, Màn hình chờ thông minh, sửa lỗi JSON bị cắt.

## Quyết định phát hành
- Shazam/ShazamKit: bỏ khỏi phạm vi 2.8.0 (không có mã Shazam nào trong dự án).
- Gói phát hành: GRAPEPlay-2.8.0.zip (mã nguồn, chưa build). Chưa có APK/AAB.

## Sửa sau phản hồi (2.8.0, chưa build)
- `AiIndex.kt`: sửa lỗi biên dịch tên biến `lib` → `l` trong `sigOf`.
- `MainActivity.kt`: thêm `BackHandler` (TV): Back/chuột phải ở các trang ngoài Thư viện về Đang phát; ở Đang phát hiện hộp xác nhận Thoát thay vì đóng app ngay. Nguyên nhân thực sự của lỗi "chuyển focus từ Thoát lên Cài đặt thì app thoát" CHƯA xác định được (không tái hiện được) — cần thông tin theo dõi thêm.
- `LangExtra.kt`: thêm ~45 quy tắc dịch cho thông báo lỗi/trạng thái động (AI, đám mây, UPnP/Cast, giải mã, cập nhật, quét thư viện) để UI English không còn hiện tiếng Việt.
- `SciFi.kt`: sửa lỗi giao diện Robot — nút "Cài đặt" chỉ gắn FocusRequester khi đang quét, trong khi nút Bật/Tắt luôn trỏ `up` tới nó → khi không quét, focus trỏ vào requester chưa gắn gây lỗi. Nay luôn gắn.
- Chẩn đoán (chỉ nhật ký, không đổi hành vi): `Playback.kt` ghi lý do tạm dừng không do người dùng; `MainActivity.kt` ghi onStop/onDestroy. Nguyên nhân nhạc dừng khi bấm Home CHƯA xác định.
- `build.gradle.kts`: versionName 2.8.1, versionCode 85 (để cập nhật đè lên 2.8.0).
