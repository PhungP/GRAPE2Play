# TEST REPORT — 2.9.6 (gồm 2.9.5)

## Môi trường
Linux container, JDK có sẵn (javac/java). **Không có Android SDK, Gradle không tải được thư viện → BUILD NOT VERIFIED.** Không có thiết bị Android. Mọi con số dưới đây là đầu ra thật của `./run_core_tests.sh`.

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
| Versions (rollback) | Versions | so sánh phiên bản, tiền tố v/V, lọc bản cũ hơn, bỏ trùng/tag rác |

## Chưa chạy / không thể chạy ở đây
- Biên dịch Kotlin/Compose (BitPerfectProbe, DeviceProfiles, DeviceRegistryUi, BitPerfectUi, Updater/rollback, MainActivity, OutputPage); JUnit trong Gradle.
- Thiết bị thật: `getAudioDevicesForAttributes` trên Android 13+, đọc VID/PID qua UsbManager, USB DAC (FiiO K9 Pro ESS hay thiết bị khác), HDMI, S/PDIF, Bluetooth, NAS, UPnP, Cast, DSD/DoP, Android TV/remote.
- Hộp thoại xác nhận/quản lý thiết bị, hộp thoại Bit-perfect, màn hình rollback (tải về Download, gỡ app, cài APK cũ), việc Android từ chối cài đè bản thấp hơn.
- Hồi quy phát nhạc (local/USB/NAS/UPnP/Cast, đổi/ngắt đầu ra, playlist/shuffle/repeat/gapless, DSP/EQ): chỉ có phần logic thuần của 2.9.3–2.9.4 chạy lại và vẫn đạt; không sửa `Playback.kt` nên không kỳ vọng thay đổi, nhưng **chưa kiểm chứng trên thiết bị**.

## Tình trạng
Test logic: ĐẠT (9788/0). Build: **BUILD NOT VERIFIED**. Kiểm thử thiết bị: **chưa thực hiện**.
