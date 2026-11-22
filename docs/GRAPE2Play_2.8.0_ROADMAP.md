# GRAPE2Play 2.8.0 Standard — ROADMAP

Base: 2.7.3 (versionCode 83) · Application ID: `com.grape.play` (giữ nguyên) · Cập nhật: Phase 2A.

## Trạng thái các giai đoạn

| # | Giai đoạn | Trạng thái |
|---|-----------|-----------|
| 0 | Project Assessment | **Completed** (báo cáo này) |
| 1 | Music Library | Completed về mã nguồn (kiểm tra tĩnh; chưa build) — xem bảng chi tiết |
| 2A | Gỡ Fade Out khỏi Sleep Timer | **Completed** (chỉ kiểm tra tĩnh; chưa build/chạy — xem TEST_REPORT) |
| 2 | Playback & Audio (phần còn lại) | Not Started |
| 3 | AI Core & AI Features | Not Started |
| 4 | Shazam, Deezer & Music Discovery | Not Started — có điều kiện chặn (xem mục 4) |
| 5 | Sources & Connectivity | Not Started |
| 6 | UI Integration | Not Started |
| 7 | Testing & Release | Not Started |

## 1. Hiện trạng mã nguồn 2.7.3 (đo thực tế)

- Kotlin + Jetpack Compose, 71 file nguồn (~13,9 nghìn dòng) trong `app/src/main/java/com/grape/play/`. Một module `app`.
- minSdk 26, targetSdk 34, compileSdk 34, Media3 ExoPlayer 1.3.1, OkHttp 4.12, smbj 0.13 (SMB), ZXing (QR).
- Không có thư mục `test`/`androidTest`: **chưa có test tự động nào**.
- Chưa từng được biên dịch trong môi trường làm việc này (không có Android SDK, Gradle không tải được phụ thuộc). Kiểm tra duy nhất đã làm trên các bản gần đây là đếm cân bằng ngoặc và rà chuỗi bằng script. Người dùng tự build và báo lỗi.
- Bí mật (Google/Microsoft client) đọc từ `local.properties`/biến môi trường, không hardcode.
- Phát nhạc: `Playback` (ExoPlayer) với `DefaultAudioSink` + `DspProcessor` (EQ/preamp) và `ForwardingAudioSink` đo mức tại `handleBuffer` (`LevelProcessor.tap`). Phát ra thiết bị ngoài qua UPnP/OpenHome (`UpnpAv`) và Cast (`CastClient`) khi `Playback.isRemote()` (biến `rc != null`).
- Thư viện: `Library`, `AiIndex`, `TagFix`, `Covers` (Probe.kt), nguồn trong `Sources.kt` (cục bộ, SMB, WebDAV, DLNA/UPnP, Google Drive, OneDrive).

## 2. Phát hiện quan trọng của giai đoạn 0

### 2.1 Sleep Timer / Fade Out (`AiExtras.kt`) — ĐÃ XỬ LÝ ở Phase 2A
Trạng thái: toàn bộ nội dung bên dưới là hiện trạng **trước** Phase 2A; xem CHANGELOG để biết kết quả.
Các chỗ đang can thiệp âm lượng, cần gỡ ở Giai đoạn 2:
- `AiExtras.kt:53` `applyFade`: ghi `Playback.exo.volume` giảm dần.
- `AiExtras.kt:90` (`finish`) và `:96` (`cancel`): ghi `Playback.exo.volume = 1f` để "khôi phục" âm lượng.
- Tham số/luồng fade: `SleepTimer.start(minutes, fadeSec)`, biến `fadeMs`, `startEndOfTrack()` (fade 12 s), `AiSleep.Plan.fadeSec`, prompt AI có `fade_seconds`, `AiHub.kt:431` (truyền `p.fadeSec`), nút nhanh trong `SleepPage` (truyền 60/12), ghi chú và mô tả UI có chữ "nhỏ dần" (AiExtras, AiHub:452, LangExtra).
- Không tìm thấy nơi nào khác trong mã gọi `setVolume`/`setStreamVolume`/`exo.volume`; EQ/DSP/Preamp/Balance không bị Timer đụng tới. Phần "Âm lượng loa" ở trang Đang phát chỉ **đọc** âm lượng thiết bị UPnP.
- Mục tiêu: Timer chỉ đếm giờ rồi gọi dừng phát; không ghi âm lượng ở bất kỳ nơi nào, kể cả khi hủy.
- Lưu ý: `cancel()` hiện được gọi cả khi chưa từng chạy fade → vẫn ghi `volume = 1f`; đây cũng là một vi phạm yêu cầu "không khôi phục âm lượng cũ".

### 2.2 Ảnh bìa trên mạng đang có sẵn (`Probe.kt`, object `Covers`)
- Hiện tại, mặc định **bật**, app tự tìm ảnh bìa trên Deezer (`api.deezer.com/search/album`, `/search`) rồi iTunes (`itunes.apple.com/search`) bằng tên nghệ sĩ + album, tải ảnh và **lưu vào `filesDir/webcovers`**, không có bước xem trước/xác nhận.
- Xung đột với quyết định đã chốt "Artwork: CONFIRM" và nguyên tắc "Deezer chỉ dùng khi có quyền API hợp lệ".
- Câu hỏi thường gặp dành cho nhà phát triển của Deezer nêu "Deezer images can't be cached" (không được lưu đệm ảnh Deezer); tên nghệ sĩ/bài hát thì được lưu cục bộ. Cần xác minh lại điều khoản chính thức trước khi giữ hành vi hiện tại.
- Hành động ở Giai đoạn 4: tắt tự động tải/lưu mặc định, chuyển sang luồng "đề xuất → xem trước → xác nhận → ghi", bỏ ảnh Deezer khỏi bộ nhớ lưu nếu không có quyền hợp lệ. **Không tự ý xóa tính năng**: giữ lựa chọn bật/tắt, đổi sang chế độ xác nhận.

### 2.3 Nhận diện Shazam từ luồng PCM nội bộ
Khả thi về kỹ thuật, có các điểm cần giải quyết:
- Điểm lấy mẫu đã có: `ForwardingAudioSink.handleBuffer` (Playback.kt). Cần một "tap" chỉ-đọc, sao chép dữ liệu sang bộ đệm riêng, không đổi `ByteBuffer`, không chặn luồng âm thanh.
- Định dạng đầu vào thay đổi: PCM 16-bit hoặc float 32-bit (khi bật Hi-res), tần số tới 192 kHz+, DSD qua `DsdPcm`/`DopPcm`. ShazamKit cần **PCM 16-bit mono** ở 48000/44100/32000/16000 Hz → phải downmix + resample + đổi độ sâu bit ở luồng phụ.
- DoP: dữ liệu DoP không phải âm thanh nghe được → không dùng để nhận diện; với DSD cần lấy tại đường PCM đã chuyển (nếu có) hoặc từ giải mã phụ.
- Phát qua thiết bị ngoài (UPnP/Cast): không có PCM cục bộ. Phương án: giải mã một đoạn file riêng (đã có `Spectrogram`/`Decoders`) cho việc nhận diện, không ảnh hưởng luồng phát. Cần quyết định ở Giai đoạn 4.
- Nguồn: bài hát đã có trong thư viện thường đã có tag; Shazam hữu ích nhất khi bài thiếu/sai tag.

### 2.4 ShazamKit trên Android (đã kiểm tra tài liệu Apple)
- Phân phối bằng file **AAR** (không có trên Maven), phiên bản tài liệu hiện nêu 2.1.1, minSdk 21, cần quyền INTERNET.
- Cần **Developer Token của Apple** cấp qua `DeveloperTokenProvider` do ứng dụng tự cung cấp (tài liệu dẫn tới hướng dẫn "Create a media identifier and private key"). Trang tài liệu không mô tả yêu cầu tài khoản Apple Developer, cấu trúc JWT hay điều khoản giấy phép.
- Không được hardcode khóa; ký JWT ngay trên thiết bị đòi hỏi nhúng khóa riêng trong app (không an toàn). Cần chốt cách cấp token (người dùng tự nhập token, hoặc máy chủ trung gian của chính chủ dự án).
- Chưa kiểm chứng được: điều khoản giấy phép của SDK Android (một câu hỏi trên diễn đàn Apple về việc này tồn tại, chưa có câu trả lời đọc được), hạn mức gọi, thời hạn token.
- Kết luận: **chưa thể triển khai** cho đến khi có AAR hợp lệ + token + xác nhận điều khoản. Không tạo giao diện giả.

### 2.5 Deezer
- Tài liệu hướng dẫn chính thức nêu: truy cập dữ liệu đầy đủ nhưng âm thanh chỉ có đoạn trích 30 giây; không lưu trữ âm thanh; Deezer có thể chặn ứng dụng vi phạm điều khoản.
- Câu hỏi thường gặp: "không có API trả phí"; có hạn mức truy vấn; không whitelist nếu không có thỏa thuận thương mại; không cache ảnh.
- Chưa xác minh được: cách đăng ký ứng dụng mới hiện nay, endpoint nào dùng được không cần đăng nhập, điều khoản về hiển thị/attribution. Cần đọc Terms of Use chính thức ở Giai đoạn 4.
- Luật chơi: nếu không có quyền hợp lệ, Deezer bị tắt; ứng dụng vẫn phải chạy với nguồn khác (xem mục 4). Không scraping.

## 3. Kế hoạch từng giai đoạn

### Giai đoạn 1 — Music Library
- Rà `Library`, `AiIndex`, `TagFix`, `Favorites`, `Usage`: lỗi, hiệu năng thư viện lớn (đã có ~6,5 nghìn bài), nhóm album/nghệ sĩ.
- Chuẩn hóa hiển thị "Full Music Details" (khung dữ liệu: tiêu đề, nghệ sĩ, album, năm, thể loại, số bài, định dạng, bitrate, nguồn…) chỉ với dữ liệu có nguồn xác minh (tag trong file, nhà cung cấp metadata hợp pháp).
- Mô hình dữ liệu cho tag người dùng sửa/xác nhận, không làm mất `TagFix` hiện có.

### Giai đoạn 2 — Playback & Audio
- (Phase 2A — Completed) Gỡ hoàn toàn Fade Out khỏi Sleep Timer theo mục 2.1; giữ chức năng hẹn giờ (đếm → dừng).
- Rà ổn định phát: xử lý lỗi liên tiếp, gapless, chuyển đầu ra.
- Thiết kế tap PCM chỉ-đọc cho Shazam (không đổi dữ liệu đầu ra).
- Không thay Playback Engine.

### Giai đoạn 3 — AI Core & AI Features
- Đã có 5 nhà cung cấp, tự chuyển, tìm nhanh, playlist, DJ, tag, lời bài hát, buổi thử dàn, màn hình chờ.
- Việc cần làm: kiểm soát chất lượng/đúng ngôn ngữ đầu ra, giới hạn dữ liệu gửi đi, xử lý JSON bị cắt, kiểm thử với từng nhà cung cấp.

### Giai đoạn 4 — Shazam, Deezer & Music Discovery
- Điều kiện bắt đầu: có AAR ShazamKit, Developer Token, xác nhận điều khoản; xác minh điều khoản Deezer.
- Luồng "Artwork Confirm": đề xuất từ nhiều nguồn hợp pháp → xem trước → xác nhận → ghi; thay hành vi tự tải hiện tại (mục 2.2).
- Nguồn metadata/ảnh thay thế cần đánh giá điều khoản trước khi dùng: MusicBrainz + Cover Art Archive, iTunes Search API (hiện đã được dùng). Chưa xác minh gì về chúng ở giai đoạn 0.

### Giai đoạn 5 — Sources & Connectivity
- Rà SMB/WebDAV/DLNA/UPnP/Cast, OAuth (Google Drive, OneDrive), lỗi mất kết nối, quét lại.

### Giai đoạn 6 — UI Integration
- Giữ nguyên thiết kế hiện có (quyết định đã chốt); chỉ thêm các màn hình/nút cho tính năng mới ở những nơi sẵn có, đủ VI/EN.

### Giai đoạn 7 — Testing & Release
- Thiết lập test đơn vị (parser Sleep Timer, parseJson, Lang, nhóm thư viện), build thật trên máy có Android SDK, kiểm thử trên TV/điện thoại, nâng version 2.8.0.

## 4. Việc cần người dùng cung cấp (khi tới giai đoạn tương ứng)
1. Kết quả build/log từ Android Studio sau mỗi giai đoạn (môi trường làm việc không build được).
2. Giai đoạn 4: tài khoản Apple Developer và cách cấp Developer Token cho ShazamKit; file AAR ShazamKit; thông tin đăng ký ứng dụng Deezer (nếu muốn dùng).

## 5. Rủi ro đã biết
- Chưa có build/test tự động; mọi thay đổi gần đây chưa được biên dịch trong môi trường này.
- Hành vi tải/lưu ảnh bìa Deezer hiện tại có thể vi phạm điều khoản (mục 2.2).
- ShazamKit Android phụ thuộc điều khoản và token của Apple (mục 2.4).


## Phase 1 — chia nhỏ
| Phần | Nội dung | Trạng thái |
|---|---|---|
| 1A | Library Core & lưu trữ cache | Completed (mã nguồn, chỉ kiểm tra tĩnh; chưa build/chạy) |
| 1B | Metadata & TagFix | Completed (mã nguồn, tĩnh). TagFix vốn chỉ là ánh xạ hiển thị, không ghi file → không có bước ghi cần xác nhận |
| 1C | Search/Sort/Filter | Completed một phần: tìm kiếm mở rộng; KHÔNG thêm UI sắp xếp/lọc mới (giữ giao diện) |
| 1D | Favorites/History/AI Index | Completed (mã nguồn, tĩnh) |
| 1E | Tích hợp, rà soát, kiểm thử | Rà soát tĩnh xong; kiểm thử thực tế Not Started (cần Android Studio/thiết bị) |

## Các giai đoạn còn lại (tổng hợp, bản master task)
| Hạng mục | Trạng thái |
|---|---|
| Music Details & Artwork | Một phần: nguồn ảnh bìa web chỉ để hiển thị (cache riêng), KHÔNG ghi vào file/thư viện; chưa có luồng "đề xuất → xem trước → ghi vào file" vì app chưa ghi tag/ảnh vào file. Cần quyết định của bạn (xem RELEASE_NOTES) |
| AI Enhancement | Đã có sẵn từ 2.4–2.7: timeout, tự chuyển nhà cung cấp, salvage JSON bị cắt, hủy. Không đổi thêm |
| Shazam | Cancelled theo quyết định của chủ dự án (không đưa vào 2.8.0; trước đó BLOCKED do cần AAR + Developer Token) |
| Deezer / nguồn trực tuyến | Giữ nguyên (API công khai, chỉ hiển thị). Điều khoản lưu trữ artwork của Deezer chưa xác minh → rủi ro ghi nhận |
| Sources & Connectivity | Không đổi ngoài bảo toàn bản ghi khi quét lỗi (1A). Chưa thử trên NAS/Cast thật |
| Giao diện & ngôn ngữ | Không đổi giao diện; VI/EN giữ nguyên |
| Release | versionName 2.8.0, versionCode 84 đã đặt. KHÔNG có APK/AAB (không build được). Ký: do bạn |
