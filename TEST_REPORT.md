# TEST REPORT — 2.9.9 (gồm 2.9.5–2.9.8)

## Môi trường
macOS (Apple Silicon), JBR/OpenJDK 21, Android SDK (platform 33/34, build-tools 36) và Gradle 8.7 có sẵn trên máy → **build thật đã chạy**. Không dùng thiết bị thật của người dùng (192.168.1.3) để cài thử.

## Phân biệt mức hoàn thành
| Mức | Trạng thái |
|---|---|
| Source implementation completed | Có (Kotlin/Compose chưa được biên dịch) |
| Core tests passed | Có — logic thuần Java |
| Android build successful | **KHÔNG xác minh được** |
| Hardware validation completed | **Chưa thực hiện** |

## Đã chạy
Lệnh: `./run_core_tests.sh` (biên dịch DspMath, PlayLogic, DeviceMatch, BitPerfect, DeviceRegistry, Versions + test, rồi chạy)
```
INFO  Sin đầy thang 20 Hz–20 kHz: số mẫu cắt đỉnh khi KHÔNG headroom = 13770229; có headroom = 0; đỉnh lớn nhất sau xử lý (có headroom) = 0.96844 FS
INFO  Giới hạn đã biết (tín hiệu KHÔNG phải sin, đầy thang, có headroom; số mẫu vượt 0 dBFS): bass → vuông 0, xung 0, nhiễu trắng 0; vocal → vuông 4836, xung 0, nhiễu trắng 0; +12 dB mọi băng → vuông 3065, xung 0, n
INFO  Bit-perfect: đã vét cạn 345600 tổ hợp đầu vào (route, DSP, sink, khả năng, mixer, codec BT, bằng chứng, mức hồ sơ, active, âm lượng, float).
INFO  Device Registry: 3 bản ghi tích hợp (chỉ FiiO K9 Pro ESS theo thông số nhà sản xuất; chưa có thiết bị thật nào được kiểm thử).
passed=9788 failed=0
```
Số test: 9.782 đạt, 0 lỗi (9.703 ở 2.9.4; 2.9.5 thay các assertion theo quy tắc mới và thêm: Bit-perfect classification/active output/PCM/Verified–Configuration, Device Registry, Versions).

| Nhóm | Mã được test | Nội dung |
|---|---|---|
| Hàng đợi/shuffle/repeat, lỗi, retry, chọn đầu ra, DSP, clipping, chẩn đoán chuyển bài | PlayLogic, DeviceMatch, DspMath | như 2.9.4, không đổi |
| Bit-perfect – phân loại | BitPerfect | tần số trong/ngoài danh sách thiết bị, thiếu thông tin, có/không có bằng chứng resampling, mixer Android khác nguồn (chỉ NOT VERIFIED), DSP bật/tắt |
| Active output | BitPerfect.activeIndex | Android<13, một/nhiều/không thiết bị, thiết bị chọn ≠ thiết bị định tuyến, thiết bị chọn đã ngắt, "System Default – Route Unknown" (không mô tả HDMI/USB/BT, bỏ qua khả năng), không VERIFIED khi route Unknown |
| PCM | BitPerfect.pcmName, DEPTH_REDUCED | 16/24/32/Float; hires không biến 16-bit thành float; hạ 16-bit khi không bật float; không có sink → UNKNOWN |
| Tuyến | BitPerfect | USB, HDMI, S/PDIF, Bluetooth (codec lossy / chưa biết / tự khai lossless), UPnP, Cast, DSD cục bộ/UPnP/DoP, không bài |
| Verified – Configuration | BitPerfect | đủ điều kiện; từng điều kiện bị phá vỡ riêng lẻ (DSP, resampling evidence, hạ 16-bit, hồ sơ tự khai/chưa có căn cứ/không có, không phải thiết bị đang định tuyến, khác phương thức, tần số/độ sâu ngoài thông số, âm lượng, nguồn thiếu, sink thiếu…); pathProof → END_TO_END |
| Vét cạn | BitPerfect | 345.600 tổ hợp: NOT_BIT_PERFECT ⇔ có bằng chứng; VERIFIED chỉ khi pathProof hoặc đủ điều kiện cấu hình; Resampling YES ⇔ bằng chứng; khả năng/mixer/âm lượng/hồ sơ không tạo bằng chứng |
| Device Registry | DeviceRegistry | khớp danh mục; thiết bị lạ; trùng tên khác VID/PID; nhiều phương thức kết nối; xác nhận đúng/sai; khai báo không cấp xác minh; lưu/tải (ký tự đặc biệt, dữ liệu hỏng/rỗng); ngắt kết nối; đổi phương thức; không hỏi lặp; không hỏi loa/UPnP/Cast; bản ghi danh mục biến mất; tích hợp với BitPerfect.evaluate |
| Cửa sổ hàng đợi MediaSession | QueueWindow | rỗng, ngắn, giữa/đầu/cuối danh sách dài, chỉ số ngoài phạm vi |
| Versions | Versions | so sánh phiên bản, tiền tố v/V (đã bỏ phần lọc rollback) bản cũ hơn, bỏ trùng/tag rác |

## Chưa chạy / không thể chạy ở đây
- Biên dịch Kotlin/Compose (BitPerfectProbe, DeviceProfiles, DeviceRegistryUi, BitPerfectUi, Updater/rollback, MainActivity, OutputPage); JUnit trong Gradle.
- Thiết bị thật: `getAudioDevicesForAttributes` trên Android 13+, đọc VID/PID qua UsbManager, USB DAC (FiiO K9 Pro ESS hay thiết bị khác), HDMI, S/PDIF, Bluetooth, NAS, UPnP, Cast, DSD/DoP, Android TV/remote.
- Hộp thoại xác nhận/quản lý thiết bị, hộp thoại Bit-perfect, màn hình rollback (tải về Download, gỡ app, cài APK cũ), việc Android từ chối cài đè bản thấp hơn.
- Hồi quy phát nhạc (local/USB/NAS/UPnP/Cast, đổi/ngắt đầu ra, playlist/shuffle/repeat/gapless, DSP/EQ): chỉ có phần logic thuần của 2.9.3–2.9.4 chạy lại và vẫn đạt; không sửa `Playback.kt` nên không kỳ vọng thay đổi, nhưng **chưa kiểm chứng trên thiết bị**.

## Tình trạng
Test logic: ĐẠT (9788/0). Build: **BUILD NOT VERIFIED**. Kiểm thử thiết bị: **chưa thực hiện**.

## 2.9.9 — kết quả chạy thật
| Hạng mục | Lệnh | Kết quả |
|---|---|---|
| Test lõi thuần Java | `./run_core_tests.sh` | `passed=9854 failed=0` (trước thay đổi: 9785/0) |
| Biên dịch debug (clean) | `gradle clean :app:assembleDebug` | **BUILD SUCCESSFUL** (Kotlin/Java/Compose/resource/dependency); `app-debug.apk` ≈ 23,4 MB |
| Unit test Gradle | `gradle :app:testDebugUnitTest` | **BUILD SUCCESSFUL**, `AudioCoreJUnitTest` tests=1 failures=0 (chạy toàn bộ AudioCoreTests gồm DeviceCatalogTests) |
| Lint | `gradle :app:lintDebug` | **FAILED: 149 lỗi, 42 cảnh báo — đều đã có từ trước**, nằm ở file không thuộc thay đổi này (Playback/Remote/Decoders/Dsp/LevelProcessor… : `UnsafeOptInUsageError` ×133, `RestrictedApi` ×15, `UnspecifiedRegisterReceiverFlag` ×1). Không sửa vì ngoài phạm vi (không đụng Playback/Audio Engine). Không có lỗi lint mới ở file của 2.9.9 (chỉ cảnh báo `ObsoleteSdkInt`, `StaticFieldLeak` ở Updater/Renderers đã có từ trước). |
| Release APK | — | **KHÔNG build**: `app/build.gradle.kts` không có `signingConfig`; APK 2.9.8 đã phát hành ký bằng khoá riêng (CN=Peter Phan, SHA-256 853ecf48…) khác khoá debug. Không tự tạo/đổi khoá. |
| Chạy trên emulator/thiết bị | — | **KHÔNG xác minh**: cài debug APK lên emulator API 37 thành công nhưng emulator bị treo ("Process system isn't responding") nên không kiểm tra được giao diện. |

Sửa trong lúc kiểm tra: `AudioCoreTests.java` dùng `out.flip().order(...)` (chạy được với JDK 21 nhưng không biên dịch với `android.jar`) khiến `testDebugUnitTest` lỗi biên dịch từ trước → đổi thành ép kiểu `ByteBuffer`. Test đọc catalog nay thử cả `app/src/main/assets` lẫn `src/main/assets` (Gradle chạy với thư mục `app/`).

Bộ `DeviceCatalogTests` kiểm: nạp dữ liệu thật (≥30 thiết bị, không cảnh báo); không mục nào mức ≥3/Verified khi thiếu bằng chứng; WXC-50 / L75ms / WiiM Ultra; Native/DoP/DSD→PCM; chính sách hạ mức; **schema lạ/thiếu tiêu đề/dữ liệu cụt/nhị phân → danh mục rỗng, không lỗi**; khớp chính xác, bí danh, hãng, VID:PID, mơ hồ, tên hãng quá ngắn; **WXC-50 ≠ WXC-500 (modelName, biến thể khoảng trắng, tên thân thiện)**; K9 Pro ≠ K9 Pro ESS; kết nối khác không mượn kết quả; tìm kiếm/lọc/sắp xếp; hồ sơ cục bộ (xác nhận, sửa, gỡ liên kết, lưu/tải); di trú hồ sơ cũ (13 trường, dòng hỏng, trường mới); xác nhận danh tính không làm phiên phát thành VERIFIED.

## Chưa thực hiện / chưa xác minh
Giao diện Compose chưa được chạy (chỉ biên dịch + rà mã) · phát nhạc, UPnP, Cast, USB, TV trên thiết bị thật · release APK ký · GitHub Release · kiểm tra lại từng URL nguồn sau ngày nghiên cứu.

## Chạy build trên máy phát triển
```
export ANDROID_HOME=~/Library/Android/sdk
gradle clean :app:assembleDebug :app:testDebugUnitTest      # hoặc ./gradlew nếu có wrapper
./run_core_tests.sh
```
Release: thêm `signingConfig` (đọc mật khẩu từ biến môi trường, không commit) rồi `gradle :app:assembleRelease`, kiểm tra bằng `apksigner verify --print-certs`.
