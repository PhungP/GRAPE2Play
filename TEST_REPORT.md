# TEST REPORT — 3.0.6

## 3.0.6 — kết quả (2026-11-27)
| Loại | Kết quả |
|---|---|
| Unit test lõi (`./run_core_tests.sh`) | `passed=10275 failed=0` [UNIT] |
| Build + JUnit Gradle | `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` BUILD SUCCESSFUL (release không ký, chỉ để kiểm cấu hình) |
| Lint | 149 lỗi đã có từ 3.0.5 (media3 `@UnstableApi`, RestrictedApi — không phải lỗi chạy); 3 lỗi thật đã sửa |
| Mức kiểm chứng tính năng mới | Hàng đợi/playlist, tự tạm dừng khi rút đầu ra, launcher nhảy bài qua MediaSession, AudD, mã hoá Keystore, FLAC CRC16: chỉ [UNIT] (logic thuần Java) và một phần [EMU]; **không có [REAL]** |
| **Chưa kiểm tra** | Toàn bộ phần cứng/thiết bị thật (TV, remote, USB DAC, HDMI, Cast, UPnP, gapless, AudD với micro thật, cài đè bằng khoá release) — xem docs/REAL_DEVICE_TEST_PLAN.md |

---

## 3.0.5 — kết quả thực tế (2026-11-27)
| Loại | Kết quả |
|---|---|
| Unit test lõi | `passed=9909 failed=0` |
| Build + JUnit Gradle | BUILD SUCCESSFUL |
| **Real-device (đọc nhật ký TV Mibox 4 qua adb, chỉ đọc)** | Bản 3.0.4 trên TV: quét unicast **9 phản hồi / 7,2 s**, **"SSDP: tìm thấy JBL L75ms tại 192.168.1.41:34605"**; người dùng xác nhận đã thấy loa JBL L75ms UPnP. (Lượt đó mDNS Cast vẫn không thấy JBL → lý do thêm dò Cast bằng HTTP ở 3.0.5) |
| Đo từ Mac | `GET :8008/setup/eureka_info` của JBL trả JSON có `name` và `ssdp_udn` |
| Emulator (điện thoại) | Hộp thoại trạng thái (ⓘ): mục "XÁC MINH NHANH THIẾT BỊ NÀY" ở đầu hộp thoại; bấm ① → hiện "✓ Đã xác nhận danh tính" và nút ② → bấm ② mở form Verify (các ô firmware/giao thức/phần mềm/ghi chú thu gọn). Trang Đầu ra thấy JBL UPnP + Cast |
| **Chưa kiểm tra** | Khối xác minh nhanh với thiết bị Cast/UPnP đang phát thật (emulator không có thiết bị ngoài đang phát, nên chỉ thử nhánh đầu ra cục bộ "Loa tích hợp"); dò Cast bằng HTTP trên TV; luồng "xác minh → VERIFIED" với loa thật; remote TV thật |

---

# TEST REPORT — 3.0.4

## 3.0.4 — bằng chứng và kết quả (2026-11-27)
- **Đọc nhật ký TV (adb chỉ đọc, Mibox 4 / Android 12 / 192.168.1.29, app 3.0.3):** quét 07:13:30: multicast 21 phản hồi; quét unicast **1 phản hồi, mất 9,4 s** (lượt 2: 0 phản hồi, 9,6 s); UPnP chỉ thấy Samsung TV; Cast chỉ thấy 2 thiết bị (không có JBL). Cùng thời điểm từ máy Mac: ping JBL 10 ms, cổng Cast 8009 mở, SSDP unicast 4/6.
- **Mô phỏng quét dồn dập từ Mac (Python):** JBL trả lời 4/4 lần ở cả kiểu dồn và kiểu giãn cách, và thấy thêm 5 thiết bị khác ⇒ loa không bỏ gói khi bị dồn; khác biệt nằm ở thời gian gửi trên TV (đọc từ nhật ký) khiến phản hồi không được đọc.
- Unit test lõi `passed=9909 failed=0`; build + JUnit Gradle đạt.
- **Chưa kiểm tra:** bản 3.0.4 trên TV (chưa cài; chưa quét lại); phần mềm sửa này chỉ được kiểm bằng biên dịch và lập luận từ nhật ký.

---

# TEST REPORT — 3.0.3

## 3.0.3 — kết quả thực tế (2026-11-27)
| Loại | Kết quả |
|---|---|
| Unit test lõi (`./run_core_tests.sh`) | `passed=9909 failed=0` |
| Build + JUnit Gradle | BUILD SUCCESSFUL |
| Đo thật trên LAN (Python, máy Mac) | JBL L75ms: multicast M-SEARCH **0/24** lần được trả lời (MediaRenderer:1 MX2, MX3, rootdevice); unicast **8/8**. Thiết bị khác (192.168.1.8) vẫn trả lời multicast ⇒ multicast trên mạng chạy, riêng JBL không trả lời. Mô tả XML của JBL đơn giản (1 device, 4 service, không có `modelDescription`), regex đọc đúng hãng "Harman Luxury Audio", model "JBL L75ms" |
| Emulator | (1) Ô tìm kiếm danh mục mặc định thu gọn ("trống — bấm để nhập"). (2) "Xem chi tiết trạng thái phát": ▼ ×13 xuống cuối (nút Đóng có viền), ▲ về đầu: "← Đóng" có viền và toàn bộ phần đầu đọc được. (3) Trang Đầu ra hiện dòng "Chi tiết: multicast … · unicast …" (emulator có 2 card ảo 10.0.2.x: multicast 6+6 phản hồi, unicast 0 — mạng ảo NAT). Thấy JBL UPnP + Cast |
| Real-device | **KHÔNG thực hiện**. Chưa xác định được vì sao TV của người dùng không thấy JBL UPnP: cần dòng "Chi tiết"/Nhật ký từ TV |
| Chưa kiểm tra | Form Verify với ô thu gọn (cùng thành phần `DevField`, chỉ kiểm bằng ô tìm kiếm); remote TV thật |

---

# TEST REPORT — 3.0.2

## 3.0.2 — kết quả thực tế (2026-11-27)
| Loại | Kết quả |
|---|---|
| Unit test lõi (`./run_core_tests.sh`) | `passed=9909 failed=0` (thêm UV1–UV9 cho User Verified trên thiết bị ngoài, MAT18) |
| Build + JUnit Gradle (`clean :app:assembleDebug :app:testDebugUnitTest`) | BUILD SUCCESSFUL |
| Lint | không chạy lại ở 3.0.2 (3.0.1: 149 lỗi cũ, không có lỗi mới ở file thay đổi) |
| Đo thật trên mạng LAN (Python, từ máy Mac cùng mạng với loa) | JBL L75ms (192.168.1.41): **0/10** biến thể M-SEARCH multicast được trả lời; **unicast** tới IP được trả lời đầy đủ (MediaRenderer 1/2, AVTransport 1/2, QPlay); cổng UPnP ngẫu nhiên (34605 lúc đo) |
| Emulator (điện thoại, xoay ngang), thao tác `adb` + chụp màn hình | (1) Cài đặt: nút "Tắt" + chữ "Chưa bật chạy ngầm" không còn xanh, dòng "✓ Đã miễn tối ưu pin" riêng. (2) Đầu ra: thấy JBL L75ms UPnP/OpenHome **và** Google Cast, thêm Samsung TV, "Quét 1 lượt · UPnP 2 · Google Cast 2". (3) Mở danh mục rồi nhấn ▼ bằng `adb input keyevent`: tiêu điểm đi trong hộp thoại (← Đóng → ô tìm kiếm có viền vàng), trang bên dưới không bị di chuyển |
| Real-device | **KHÔNG thực hiện**: chưa thử trên TV/remote thật, chưa phát nhạc qua Cast/UPnP, chưa kiểm tra luồng "xác minh → VERIFIED" trong app với loa thật (chỉ có unit test của `BitPerfect.evaluate` và `DeviceVerification.appliesTo`) |

Ghi chú: emulator không chứng minh được quét unicast "đổi kết quả" vì emulator vốn đã thấy JBL UPnP ở các bản trước (qua dò IP của thiết bị Cast); bằng chứng cho nguyên nhân là phép đo multicast/unicast ở trên. Lỗi con trỏ: nguyên nhân gốc được xác định bằng đọc mã (FocusManager lấy ngoài Dialog) và kiểm tra hành vi ▼ trong hộp thoại; chưa thử bằng remote TV thật.

---

# TEST REPORT — 3.0.0 (gồm 2.9.5–2.9.9)

## 3.0.0 — kết quả thực tế (2026-11-26)
Môi trường: macOS (Apple Silicon), JBR/OpenJDK 21, Android SDK 33/34, Gradle 8.7. Phân loại kiểm thử:

| Loại | Lệnh / cách làm | Kết quả |
|---|---|---|
| **Unit test (logic Java thuần)** | `./run_core_tests.sh` | `passed=9898 failed=0` (trước 3.0.0: 9854/0). Gồm DeviceCatalogTests, DeviceVerificationTests |
| **Unit test Gradle (JUnit)** | `gradle :app:testDebugUnitTest` | `AudioCoreJUnitTest` tests=1 failures=0 (chạy toàn bộ AudioCoreTests) |
| **Build** | `gradle clean :app:assembleDebug` | BUILD SUCCESSFUL |
| **Lint** | `gradle :app:lintDebug` | FAILED: **149 lỗi, 42 cảnh báo đều có từ trước** (Playback 47, Remote 27, Decoders 22, Dsp 19, LevelProcessor 13, TvHome 10, MainActivity 6, BitPerfectProbe 4); **không có lỗi lint mới ở file của 3.0.0** |
| **Integration test** | — | Chưa có (không có khung test tích hợp trong dự án) |
| **Emulator test (thủ công)** | AVD `Medium_Phone_API_37.0` (arm64, điện thoại), cài APK debug, thao tác bằng `adb input` + chụp màn hình | Xem bên dưới |
| **Real-device test** | — | **KHÔNG thực hiện**: chưa cài/chạy trên TV, điện thoại hay thiết bị âm thanh thật; chưa phát nhạc qua USB/UPnP/Cast/HDMI/quang |

### Emulator (đã xem trực tiếp, bằng chứng là ảnh chụp)
- Về ứng dụng: nội dung mới trung lập, hiện "Phiên bản 3.0.0"; không còn câu chuyện cá nhân.
- Trang Đầu ra → "Bit-perfect Verified Devices": đầu trang đọc đầy đủ; thông báo "danh mục chưa đầy đủ" hiển thị; 91 thiết bị; huy hiệu Manufacturer / User Verified cùng kích thước; **cuộn cảm ứng xuống cuối và lên đầu được**; mở chi tiết thiết bị, cuộn nội dung dài, nút "Về danh mục" ở đầu trang nhận tiêu điểm đầu tiên; ba nguồn Manufacturer / User / Community luôn hiển thị đủ kèm độ chắc chắn kỹ thuật, phạm vi, firmware, ngày, URL.
- Thiết bị của tôi: nhận diện thiết bị UPnP thật trên mạng LAN của người dùng (JBL L75ms) — lúc đầu **KHÔNG khớp** (thiết bị khai manufacturer "Harman Luxury Audio", modelName "JBL L75ms") → **đã sửa matcher** (tên hãng mẹ + bỏ tiền tố hãng) và kiểm lại: "Khớp danh mục: JBL L75ms · khớp chắc chắn". Confirm Device Identity lưu hồ sơ; form Verify Playback Capability hiển thị độ chắc chắn dự kiến theo lựa chọn (Unverified → Supported khi chọn đủ định dạng + điều kiện + chỉ báo), lưu kết quả User Verified; **hồ sơ và kết quả còn nguyên sau khi tắt hẳn (force-stop) và mở lại app**.
- **Phím D-pad:** thử bằng `adb input keyevent`: ▲/▼ di chuyển/cuộn được, nút đầu trang giữ tiêu điểm đầu; **không phải remote TV thật** và chuỗi phím nhanh không cho kết luận chắc về mọi thứ tự tiêu điểm — cần thử trên TV thật.
- **Sự cố khi kiểm thử:** hai lần chạm mù (toạ độ lệch do danh sách đổi chiều cao khi quét) đã mở hộp thoại "Chọn đầu ra này?"; một lần chọn nhầm JBL L75ms (UPnP) làm đầu ra TRONG BẢN APP TRÊN EMULATOR. Đã chuyển về "Mặc định hệ thống". Thư viện emulator trống, không có bài nào được gửi tới loa; app có thể đã gửi lệnh điều khiển UPnP/trạng thái tới loa lúc chọn (chưa kiểm).
- **Chưa kiểm trên emulator:** màn Quản lý hồ sơ, hộp thoại Bit-perfect (mục "Xác minh cho tổ hợp này"), màn hình lớn/xoay/TV, tab khác.

### Sửa/phát hiện trong 3.0.0
Lỗi cuộn (nguyên nhân gốc: tiêu điểm D-pad ban đầu ở nút cuối nội dung, văn bản phía trên không nhận tiêu điểm); mở/đóng chi tiết làm mất bộ lọc; tên hãng mẹ UPnP không khớp (JBL/Harman…); tên model lặp "Denon Denon Home 150"; nhãn "PASS · DEVICE_INDICATOR" thô.

## Chưa thực hiện / chưa xác minh
Real-device (TV, USB DAC, HDMI/quang, UPnP/Cast thật); phát nhạc và âm thanh (không đổi mã nhưng chưa nghe/đo); nâng cấp 2.9.9 → 3.0.0 trên máy đã cài; kiểm tra lại từng URL nguồn; Integration test.

---

# (Lịch sử) TEST REPORT — 2.9.9

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

## Release Preparation (trạng thái: RELEASE CANDIDATE — chưa có APK release ký)
- Git: commit `cd9d525` đã push lên `origin/main`.
- Ký release: keystore PKCS12 `~/my-release-key.jks` (alias `key0`) có trên máy nhưng **mật khẩu không có trong môi trường** (không có biến môi trường, không có `signingConfig`) nên **chưa ký và chưa build release**. Không đoán/ghi mật khẩu. Chưa đối chiếu được fingerprint với APK v2.9.8 (SHA-256 chứng chỉ 853ecf48f9adf43e61ceacf7b921ac27a800e4546912b2b67ccfa4ed668106f4, CN=Peter Phan).
- `app/build.gradle.kts` nay đọc khoá ký từ biến môi trường `GRAPE_KEYSTORE`, `GRAPE_KEYSTORE_PASSWORD`, `GRAPE_KEY_ALIAS`, `GRAPE_KEY_PASSWORD` (tuỳ chọn); không đặt thì hành vi cũ. Build debug sau thay đổi: BUILD SUCCESSFUL.
- Kiểm thử nâng cấp 2.9.8 → 2.9.9, hồi quy USB/UPnP/Cast/HDMI/quang, giao diện catalog, persistence sau restart, Updater, About: **chưa thực hiện** (không có thiết bị test được cho phép; emulator không ổn định).
- GitHub Release v2.9.9: **chưa tạo** (chưa có APK ký đúng khoá).

Để hoàn tất (trên máy có mật khẩu):
```
export GRAPE_KEYSTORE=~/my-release-key.jks GRAPE_KEY_ALIAS=key0
read -s GRAPE_KEYSTORE_PASSWORD && export GRAPE_KEYSTORE_PASSWORD
gradle clean :app:testDebugUnitTest :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk   # SHA-256 phải = 853ecf48…06f4
```
