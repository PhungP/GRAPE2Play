# TEST REPORT — 2.9.4

## Môi trường
Linux container, JDK có sẵn (javac/java). **Không có Android SDK, Gradle không tải được thư viện → BUILD NOT VERIFIED.** Không có thiết bị Android. Không có kết quả giả: mọi số dưới đây là đầu ra thật của lệnh `./run_core_tests.sh`.

## Đã chạy (logic thuần Java, biên dịch bằng javac và chạy thật)
Lệnh: `./run_core_tests.sh`
```
INFO  Sin đầy thang 20 Hz–20 kHz: số mẫu cắt đỉnh khi KHÔNG headroom = 13770229; có headroom = 0; đỉnh lớn nhất sau xử lý (có headroom) = 0.96844 FS
INFO  Giới hạn đã biết (tín hiệu KHÔNG phải sin, đầy thang, có headroom; số mẫu vượt 0 dBFS): bass → vuông 0, xung 0, nhiễu trắng 0; vocal → vuông 4836, xung 0, nhiễu trắng 0; +12 dB mọi băng → vuông 3065, xung 0, nhiễu trắng 0; +12 dB 31/62 Hz → vuông 0, xung 0, nhiễu trắng 0; Headroom dựa trên đáp tuyến sin nên KHÔNG chống clipping tuyệt đối cho mọi tín hiệu.
INFO  Bit-perfect: đã vét cạn 8640 tổ hợp đầu vào, trong đó không tổ hợp nào VERIFIED khi pathProof=false.
passed=9703 failed=0
```
| Nhóm | Mã được test | Nội dung |
|---|---|---|
| Hàng đợi/index/shuffle/repeat | PlayLogic | hoán vị + bài đầu (n=1..40), phát hết thứ tự không bỏ sót, thêm bài giữ thứ tự, skipTarget cho ONE/ALL/OFF |
| Lỗi bài hiện tại / preload | PlayLogic.decide | dừng đúng sau 4 lỗi, hàng 1 bài, preload không bỏ qua bài đang nghe |
| Retry mất mạng / AudioTrack | PlayLogic.decide/Retries | 1,5 s rồi 5 s; 2 lần AudioTrack; hết lượt thì bỏ qua; chuỗi lỗi kết thúc hữu hạn; DSD hạ tốc độ; USB sao chép |
| Chọn/đổi đầu ra | DeviceMatch | nhiều USB DAC cùng loại, rút/cắm lại địa chỉ mới, trùng tên, Android 8 (không địa chỉ), khoá cũ, tên chứa '|', 8 tổ hợp cắm/rút |
| DSP Flat/EQ | DspMath | bypass byte-exact 16/24/32/float; đáp tuyến 10 băng × 4 tần số lấy mẫu; preamp −12…+6; cân bằng −1…+1; 1/2/6/8 kênh; chia đoạn; reset trạng thái |
| Clipping | DspMath | có/không headroom, sin đầy thang, sóng vuông/xung/nhiễu (giới hạn đã biết được in ra, không khẳng định bằng 0) |
| Chẩn đoán chuyển bài | PlayLogic.transitionKind | 3 mức phân loại |
| Bit-perfect | BitPerfect | 14 tình huống yêu cầu + DSD + không bài + vét cạn 8.640 tổ hợp |

Kiểm tra độ nhạy (đối chứng): test chứng minh không headroom thì có cắt đỉnh, không reset trạng thái lọc thì có "đuôi" cũ.

## Chưa chạy / không thể chạy ở đây
- Biên dịch Kotlin/Compose, JUnit trong Gradle (`AudioCoreJUnitTest` chỉ gọi lại cùng bộ test).
- Thiết bị thật: ExoPlayer, AudioTrack, USB DAC, HDMI/quang, Bluetooth, NAS, UPnP (kể cả SetNextAVTransportURI và khôi phục 2.9.2), Cast, DSD/DoP, Android TV.
- Lỗi preload thật, mất mạng thật, rút thiết bị thật khi đang phát.
- Giao diện (hộp thoại Bit-perfect, trang Đầu ra).
- Khoảng lặng thực tế giữa các bài.

## Tình trạng
Test logic: ĐẠT. Build: **BUILD NOT VERIFIED**. Kiểm thử thiết bị: **chưa thực hiện**.
