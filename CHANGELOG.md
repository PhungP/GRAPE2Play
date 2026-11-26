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

## 2.9.6 — Tích hợp GRAPE2Launcher (versionCode 99)
MediaSession (metadata + ảnh bìa + trạng thái + hàng đợi + actions), thông báo kiểu media, `LibraryProvider` (`content://com.grape.play.library/albums`), extra `grape_open=library`. Playback.kt không đổi. Chi tiết và giả định: LAUNCHER_INTEGRATION.md.

## 2.9.7 — Sửa lỗi biên dịch (versionCode 100)
`BitPerfectUi.kt` import nhầm `androidx.media3.common.C` che object màu `C` của app (lỗi "Unresolved reference: Panel/Line/Muted/Gold/Text" ở lần build thật đầu tiên). Đã bỏ import. Các lỗi biên dịch khác (nếu còn) chưa biết: cần log đầy đủ.

## 2.9.8 — Sửa lỗi biên dịch (versionCode 101)
`MediaSessionBridge.kt`: `MediaSession` của framework không có `setActiveQueueItemId` (chỉ có ở MediaSessionCompat) → chuyển sang `PlaybackState.Builder.setActiveQueueItemId`.

## 2.9.9 — Bit-perfect Verified Devices (versionCode 102)
- **Danh mục thiết bị công khai trong app** ("Bit-perfect Verified Devices"; mở từ trang Đầu ra hoặc hộp thoại Bit-perfect): 35 thiết bị / 80 cấu hình kết nối, dữ liệu offline `assets/device_catalog.tsv`. Tìm kiếm + lọc (loại, kết nối, PCM, DSD, mức, trạng thái), màn chi tiết, bằng chứng + URL + ngày xem xét.
- **Mỗi cấu hình theo từng kết nối** (UPnP / USB / HDMI / S/PDIF…) có mức xác minh 1–4 và trạng thái riêng; `DeviceCatalog.validate` hạ mọi tuyên bố vượt bằng chứng. Hiện KHÔNG có cấu hình nào ở mức 3–4 (chưa có phép đo).
- **Nhận diện tự động** theo VID:PID → hãng + model → bí danh (chỉ là bằng chứng phụ, luôn xin xác nhận); WXC-50 ≠ WXC-500; trùng điểm → mơ hồ. Renderer UPnP nay lưu manufacturer/modelName/modelDescription từ device description.
- **Thiết bị âm thanh của tôi**: đầu ra đang dùng, thiết bị UPnP đã thấy, hồ sơ cục bộ (xác nhận/sửa danh tính/xoá), trạng thái Bit-perfect của phiên phát hiện tại — tách khỏi danh mục toàn cục. Hồ sơ lưu thêm `catalogId`, `udn` (tương thích ngược với hồ sơ cũ).
- Ba thiết bị bắt buộc: Yamaha WXC-50 (DSD có trong tài liệu nhưng KHÔNG coi là Native qua UPnP), JBL L75ms (Native DSD qua mconnect = bằng chứng do người dùng cung cấp, mức 2), WiiM Ultra (DSD qua DLNA → PCM, không Native).
- **Bỏ** dòng dedication cá nhân ở màn Về ứng dụng.
- **Bỏ** "Quay về phiên bản trước" (Android không cho cài đè bản thấp hơn): xoá `Updater.loadOlder/downloadOlder/uninstallSelf`, `RollbackDialog`, `Versions.olderThan` và test tương ứng.
- Không đổi: Playback.kt, Audio Engine, PCM/DSD/DoP, DSP/EQ, USB/NAS, UPnP phát nhạc, Cast, TV, điều khiển từ xa. Quy tắc `BitPerfect.evaluate` và `DeviceRegistry.builtin()` giữ nguyên.
- Hoàn thiện (audit): schema catalog được kiểm tra (schema lạ/thiếu tiêu đề → danh mục rỗng, app vẫn chạy); khớp hãng cần ≥3 ký tự; thông báo "danh mục chưa đầy đủ / vắng mặt ≠ không hỗ trợ" trên mọi màn catalog và hộp thoại Bit-perfect; nhãn mức 2 ghi rõ "chưa phải bit-perfect"; sửa lỗi biên dịch có sẵn của `AudioCoreTests` với `android.jar`.
- Build debug thật + unit test Gradle đạt; lint có 149 lỗi đã tồn tại từ trước (ngoài phạm vi); release APK ký chưa tạo; chưa chạy trên thiết bị (xem TEST_REPORT.md).

## Sau 2.9.9 — đổi tên repository
Repo GitHub đổi từ `PhungP/grape2play` thành `PhungP/GRAPE2Play`. Cập nhật `Updater.REPO`, liên kết GitHub ở màn Về ứng dụng, Referer của OpenRouter và README. Bản 2.9.9 đã phát hành vẫn dùng tên cũ; GitHub tự chuyển hướng liên kết/API cũ nên cập nhật OTA vẫn hoạt động (không được tạo lại repo mới tên `grape2play`, vì sẽ làm hỏng chuyển hướng). Các thay đổi này nằm trong bản kế tiếp.

## 3.0.0 — Nguồn xác minh, Verify Playback Capability, sửa cuộn, mở rộng danh mục (versionCode 103)
- **Về ứng dụng:** bỏ câu chuyện cá nhân/đùa cợt (hoàn cảnh tài chính, TV cũ…) khỏi mọi chuỗi (UI, bản dịch, README); thay bằng giới thiệu trung lập về phát nhạc, xử lý âm thanh, quản lý đầu ra và xác minh Bit-perfect.
- **Danh mục:** 91 thiết bị / 197 cấu hình (27 hãng; trước đó 35/80). JBL L75ms: đọc được quick-specs PDF chính thức → thêm cấu hình Cast, ghi rõ tài liệu không nhắc UPnP/DSD.
- **Ba nguồn xác minh** (Manufacturer / User / Community Verified) tách khỏi **Technical Confidence** (Confirmed / Supported / Inconclusive / Unverified); ba huy hiệu cùng kích thước, thứ tự cố định, hiển thị đủ nhiều nguồn trên mỗi cấu hình. `DeviceVerification.java` + dòng `E` trong tệp danh mục (cấu hình cũ tự suy bằng chứng khi nạp, tương thích ngược).
- **Verify Playback Capability** (Thiết bị của tôi): người dùng ghi nhận kết quả thử theo định dạng, điều kiện, phương pháp, firmware, phần mềm, ghi chú, thời gian → User Verified lưu cục bộ (dòng `V`, tương thích ngược). Tách khỏi Confirm Device Identity. Hồ sơ có thêm `firmware`.
- **Community Verified:** bộ gộp báo cáo (độc lập, trùng lặp, mâu thuẫn, ngưỡng); `assets/community_reports.tsv` hiện rỗng.
- **Hộp thoại Bit-perfect:** thêm mục "Xác minh cho tổ hợp này" (không đổi kết luận `evaluate`).
- **Sửa lỗi cuộn** các hộp thoại Danh mục/Chi tiết/Thiết bị của tôi/Quản lý hồ sơ: trên TV/D-pad, tiêu điểm ban đầu rơi vào nút cuối nội dung và văn bản phía trên không nhận tiêu điểm nên không cuộn lên được → phím ▲/▼ cuộn khi không còn phần tử tiêu điểm kế tiếp, khung giới hạn 92% màn hình, nút đầu trang nhận tiêu điểm đầu tiên; mở/đóng chi tiết không còn xoá tìm kiếm/bộ lọc.
- **Matcher UPnP:** nhận tên hãng mẹ (Harman→JBL, D&M/Sound United→Denon/Marantz, Panasonic→Technics) và bỏ tiền tố hãng khỏi tên model — phát hiện nhờ JBL L75ms thật trên LAN khai "Harman Luxury Audio" / "JBL L75ms" nhưng không khớp ở bản nháp; nay khớp chắc chắn.
- Không đổi: Playback.kt, Audio Engine, PCM/DSD/DoP, DSP/EQ, UPnP phát nhạc, Cast, BitPerfect.evaluate, applicationId, ký, cơ chế cập nhật.

## 3.0.1 — sửa tiêu điểm D-pad và dò loa UPnP (versionCode 104)
- **Tiêu điểm D-pad (TV):** mở "Bit-perfect Verified Devices"/"Thiết bị âm thanh của tôi"/Chi tiết/Verify làm con trỏ nhảy ra menu khác của app vì hộp thoại yêu cầu tiêu điểm trước khi cửa sổ dựng xong (tiêu điểm còn ở trang bên dưới). Nay chờ 150 ms như các hộp thoại khác của app.
- **Dò loa UPnP:** lưu URL device description của loa đã thấy; khi loa không trả lời SSDP lần quét này (loa chờ, UDP rơi gói, cổng UPnP đổi) app tải lại description theo URL, rồi thử cổng cũ, rồi SSDP unicast tới đúng IP; `describe` thử lại một lần. Trước đây chỉ thử cổng đã lưu (có thể đã đổi) nên loa có Cast + UPnP đôi khi chỉ hiện Cast. Chưa thử trên TV thật; nguyên nhân phía loa/mạng chưa loại trừ được.
