# CHANGELOG

Mỗi mục: phiên bản mới nằm dưới phiên bản cũ. Tất cả bản dưới đây CHƯA được biên dịch hay chạy trên thiết bị bởi người soạn (môi trường không có Android SDK); chỉ phần logic thuần Java có test chạy thật (xem TEST_REPORT.md).

## 2.9.0 — Thư viện nhạc (cải tiến của 2.8.0)
Quét tăng dần theo kích thước/ngày sửa, giữ bản ghi khi ổ vắng, cache ghi an toàn, thẻ mở rộng, relink Yêu thích/Gần đây khi file đổi chỗ. Chi tiết trong README.md.

## 2.9.1 — Chọn ảnh bìa bằng AI (có xác nhận)
Deezer / MusicBrainz+Cover Art Archive / iTunes → đo cục bộ → AI chọn → người dùng xem trước và đồng ý mới lưu (chỉ trong app).

## 2.9.2
Sửa nhạc tự phát ra loa UPnP khi bật lại Mibox: loa báo STOPPED giữa bài không còn bị hiểu là "hết bài".

## 2.9.3 — Củng cố phát nhạc
Ghim bài đang nghe lên đầu thứ tự xáo trộn; thử lại lỗi AudioTrack/mất nguồn; lỗi preload không tự phát; EQ tự giảm độ lợi tránh cắt đỉnh; chẩn đoán chuyển bài.

## 2.9.4 — Audio Core Correction & Bit-perfect
### Đầu ra âm thanh (AudioSettings.kt, OutputPage.kt)
- Bỏ `distinctBy { type }`: nhiều USB DAC / HDMI / Bluetooth cùng loại hiển thị riêng, chọn đúng thiết bị.
- Khoá thiết bị `loại|địa chỉ|tên` (không dùng `AudioDeviceInfo.id` — đổi sau mỗi lần cắm; không chỉ dựa vào `TYPE_*`). Khớp lại khi cắm lại với địa chỉ mới nếu chỉ có một thiết bị cùng loại và tên. Cấu hình cũ (chỉ lưu loại) tự đọc được.
- Thiết bị đã chọn bị rút → tự về mặc định hệ thống, hiện trong danh sách là "chưa kết nối" và tự chuyển lại khi cắm. Danh sách trang Đầu ra tự cập nhật bằng `AudioDeviceCallback` (callback của Playback vẫn giữ nguyên).
- Giới hạn tốc độ DSD→PCM lưu theo khoá thiết bị.
### Hi-res
Mô tả đúng: PCM 16-bit = chế độ tương thích; PCM float 32-bit = yêu cầu ExoPlayer dùng đầu ra float; không bảo đảm độ sâu bit vật lý tại DAC.
### Chẩn đoán chuyển bài (Playback.kt)
Phân biệt chuyển tự động / định dạng PCM đổi / `configure()` của bộ xuất âm thanh. Không còn khẳng định "gapless/liền mạch": chỉ ghi "không phát hiện thay đổi định dạng đầu ra", kèm "chưa đo được khoảng lặng thực tế". UPnP: bài nạp sẵn (SetNextAVTransportURI) được cập nhật khi đổi Trộn/Lặp/thêm bài. Cast không nạp sẵn (mỗi bài LOAD riêng).
### DSP/EQ (Dsp.kt + DspMath.java)
Toán DSP tách ra DspMath.java (dùng chung với test). Headroom tự động có dư 0,3 dB, chỉ áp dụng khi EQ/preamp thực sự nâng; EQ phẳng/tắt vẫn chép nguyên byte. Xoá trạng thái lọc khi vào lại EQ sau đoạn phẳng.
### Playback.kt
Logic quyết định lỗi/xáo trộn tách ra PlayLogic.java (có test). Phát hiện và sửa: trộn khi bật xáo trộn; thêm bài (AI DJ) không còn chèn ngẫu nhiên trước bài đang nghe; Phát/Tạm dừng khi đang chờ tự phục hồi = huỷ phục hồi; quay về TV không tự phát nếu đang tạm dừng trên loa ngoài; `loadRemote` không còn lỗi khi chỉ số ngoài phạm vi.
### Bit-perfect (BitPerfect.java, BitPerfectProbe.kt, BitPerfectUi.kt, Signal.kt)
Bốn trạng thái VERIFIED / NOT VERIFIED / NOT BIT-PERFECT / UNKNOWN; tám trường thông tin độc lập; bấm ⓘ ở trạng thái để xem giải thích. Chi tiết BITPERFECT.md. Không còn nhãn "BIT-PERFECT" thiếu căn cứ.
### Test
`./run_core_tests.sh` (JDK) và JUnit (`app/src/test`). Thêm `testImplementation junit`.

## 2.9.5 — Final Bit-perfect Accuracy Fix, Device Registry, Rollback (versionCode 98)
### File thay đổi / mới
- Mới: `DeviceRegistry.java`, `Versions.java` (thuần Java, có test); `DeviceProfiles.kt`, `DeviceRegistryUi.kt`; test `DeviceRegistryTests.java`, `VersionTests.java`.
- Sửa: `BitPerfect.java` (viết lại), `BitPerfectProbe.kt`, `BitPerfectUi.kt`, `Signal.kt`, `Updater.kt` (rollback; `isNewer` dùng `Versions`), `MainActivity.kt` (khởi tạo DeviceProfiles, gọi `check` ở AudioDeviceCallback hiện có, 2 hộp thoại mới), `OutputPage.kt` (một lời gọi `check` sau khi đổi đầu ra), `BitPerfectTests.java`, `AudioCoreTests.java`, `run_core_tests.sh`, `app/build.gradle.kts` (98 / 2.9.5), tài liệu.
- Không sửa: `Playback.kt`, Audio Engine, decoder, DSP/EQ, USB/NAS, UPnP, Cast.
### Bit-perfect
- Khả năng thiết bị (AudioDeviceInfo) và tần số mixer chỉ là gợi ý; NOT BIT-PERFECT chỉ với bằng chứng (DSP, app hạ 16-bit, DSD→PCM, codec BT lossy đã biết, `rateEvidence`). Resampling YES chỉ với bằng chứng; NO chỉ với pathProof; còn lại Unknown.
- Active output: `getAudioDevicesForAttributes` (API 33+) + `BitPerfect.activeIndex`; ngược lại "System Default – Route Unknown". Thiết bị người dùng chọn tách riêng. Route `OPTICAL` đổi thành `SPDIF` (Android không phân biệt quang/đồng trục).
- PCM: encoding lấy từ `Playback.sinkFmt.encoding` (16/24/32/Float); bỏ suy luận Float từ `AudioSettings.hires`; thêm "Android Active Output Format = không xác định", "DAC/Receiver Accepted Format = không có bằng chứng".
- VERIFIED có hai mức: CONFIGURATION (đủ điều kiện cấu hình, kèm hồ sơ danh mục đã xác nhận, đúng thiết bị đang định tuyến) và END-TO-END (chưa có phương pháp).
### Device Registry
Danh mục tích hợp (FiiO K9 Pro ESS: USB / S/PDIF / Bluetooth), hồ sơ người dùng lưu cục bộ, hộp thoại xác nhận, màn quản lý. Chi tiết: BITPERFECT.md.
### Rollback
Danh sách bản cũ từ GitHub Releases → tải (kiểm kích thước + đúng package) → lưu Download/GRAPE2Play → hướng dẫn gỡ + cài. Android từ chối cài đè bản thấp hơn nên không thể rollback "tại chỗ"; gỡ app xóa dữ liệu app.
