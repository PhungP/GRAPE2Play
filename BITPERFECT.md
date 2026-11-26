# Cơ chế Bit-perfect và Device Registry (2.9.5; danh mục thiết bị 2.9.9)

> Trạng thái: mã nguồn viết xong, **BUILD NOT VERIFIED**, **chưa kiểm thử thiết bị thật**. Chỉ phần Java thuần có test chạy thật (TEST_REPORT.md).

## 1. Ba lớp thông tin được tách riêng
| Lớp | Là gì | Nguồn | Có thể kết luận "đổi dữ liệu"? |
|---|---|---|---|
| Device Capability | Thiết bị **công bố** hỗ trợ gì | `AudioDeviceInfo.getSampleRates()/getEncodings()` | **Không** — chỉ là khả năng/gợi ý |
| Runtime Audio Format | Định dạng tại thời điểm phát mà app quan sát được | `Playback.sinkFmt` (AudioSink nhận) | Chỉ phần app (hạ 16-bit do cấu hình app) |
| Actual Conversion Evidence | Bằng chứng dữ liệu bị đổi | DSP bật, app hạ 16-bit, DSD→PCM, codec BT mất dữ liệu đã biết, `rateEvidence` | **Có** |

`Resampling = YES` **chỉ** khi `rateEvidence` (hiện chưa có nguồn nào đặt true). `NO` **chỉ** khi có `pathProof` (xác minh toàn tuyến — chưa có phương pháp). Còn lại luôn là **Unknown**: không có bằng chứng ≠ chắc chắn không có resampling.

Những thứ trước đây (2.9.4) bị coi là bằng chứng NOT BIT-PERFECT và nay **chỉ còn là gợi ý (NOT VERIFIED)**: tần số không có trong danh sách của `AudioDeviceInfo`; độ sâu bit công bố thấp hơn cần; `PROPERTY_OUTPUT_SAMPLE_RATE` khác tần số bài.

## 2. Thiết bị người dùng chọn ≠ thiết bị Android đang định tuyến
- `AudioSettings.current()` (thiết bị người dùng chọn trong app) chỉ hiện ở dòng "Thiết bị bạn chọn trong app … chưa được Android xác nhận".
- Thiết bị đang định tuyến chỉ được xác định bằng `AudioManager.getAudioDevicesForAttributes` (Android 13+/API 33), theo `BitPerfect.activeIndex`: Android báo **đúng một** thiết bị, và nếu người dùng đã chọn thiết bị thì phải đúng thiết bị đó. Ngược lại → **"System Default – Route Unknown"**.
- Không suy HDMI/Optical/Bluetooth từ tên thiết bị hay từ danh sách thiết bị đang kết nối. Khi route chưa biết, mọi khả năng của thiết bị bị bỏ qua và không mô tả HDMI/USB/Bluetooth.
- **Hệ quả:** trên Android < 13 (kể cả Mibox Android 9/10) route luôn Unknown ⇒ không bao giờ đạt VERIFIED. Đây là cố ý (không đoán).
- `AudioDeviceCallback` (MainActivity) gọi lại `DeviceProfiles.check()` khi cắm/rút; cơ chế chọn thiết bị và phát nhạc không đổi. S/PDIF: Android chỉ có `TYPE_LINE_DIGITAL`, không phân biệt quang/đồng trục nên route tên là `SPDIF`.

## 3. Định dạng PCM
- `BitPerfectProbe.sinkEnc()` ánh xạ **chính xác** `Playback.sinkFmt.encoding` → PCM 16-bit / 24-bit / 32-bit / Float; không có dữ liệu hoặc encoding lạ → không hiển thị gì và trạng thái UNKNOWN.
- **Không** suy Float từ `AudioSettings.hires`; `hires` chỉ hiển thị là "Cấu hình app: yêu cầu đầu ra float" (cấu hình, không phải định dạng thực tế).
- Hiển thị tách: Decoder Output / AudioSink Format (cùng một quan sát tại `ForwardingAudioSink.configure`, nên hai trường cùng số liệu) · **Android Active Output Format = Không xác định** (app không đọc được AudioTrack/mixer) · **DAC/Receiver Accepted Format = Không có bằng chứng**.
- `DEPTH_REDUCED` (NOT BIT-PERFECT) vẫn là bằng chứng do chính app quyết định: sink nhận >16-bit mà cấu hình app không bật float ⇒ ExoPlayer hạ xuống 16-bit.

## 4. Bốn trạng thái phiên phát + hai mức VERIFIED
| Trạng thái | Khi nào |
|---|---|
| **NOT BIT-PERFECT** | Có bằng chứng đổi dữ liệu (bảng mục 1). Thiếu thông tin không bao giờ dẫn tới đây. |
| **VERIFIED – END-TO-END** | Có `pathProof`. Hiện không có mã nào đặt. |
| **VERIFIED – CONFIGURATION** | Mọi điều kiện ở mục 5 đều đạt. **Không phải phép đo toàn tuyến**; Resampling vẫn là Unknown. |
| **NOT VERIFIED** | Mặc định cho đường đã biết một phần. |
| **UNKNOWN** | Không có bài, hoặc phát cục bộ chưa có `Playback.sinkFmt` PCM 16/24/32/Float. |

## 5. Điều kiện Verified – Configuration (`BitPerfect.configCriteria`)
Thiết bị + phương thức kết nối khớp bản ghi danh mục mức `REGISTRY_CONFIG` và người dùng đã xác nhận · Android xác định đúng thiết bị đó đang định tuyến (`devIsActive`) và phương thức kết nối trong hồ sơ = route đang chạy · nguồn có độ sâu bit và tần số · tần số vào AudioSink = tần số nguồn · tần số và độ sâu bit nằm trong thông số của **bản ghi danh mục** (không phải của AudioDeviceInfo, không phải thông số người dùng khai) · không DSP, không bằng chứng đổi dữ liệu, không DSD, không UPnP/Cast · âm lượng hệ thống tối đa (hoặc âm lượng cố định).
**Device Verification Status** (hồ sơ thiết bị) và **Playback Bit-perfect Status** (phiên phát) là hai giá trị độc lập: thiết bị có hồ sơ tốt nhưng route Unknown vẫn NOT VERIFIED.

## 6. Device Registry (DeviceRegistry.java, DeviceProfiles.kt, DeviceRegistryUi.kt)
Ba lớp dữ liệu: **A** danh mục tích hợp `DeviceRegistry.builtin()` · **B** User Device Profile (SharedPreferences `devices`, định dạng dòng có escape, dòng hỏng bị bỏ qua) · **C** Community Registry: **chưa triển khai** (chỉ chừa `Entry.source`).

Bản ghi: manufacturer, model, id, tên nhận diện, VID/PID (nếu có), connection, PCM bits, sample rates, DSD, direct/exclusive, điều kiện, nguồn xác minh, mức (`REGISTRY_CONFIG` / `REGISTRY_UNVERIFIED`), ngày cập nhật. Chưa có trường hardware revision / firmware (Android không cung cấp).

Danh mục khởi tạo (cố ý nhỏ) — **FiiO K9 Pro ESS**, nguồn: trang thông số nhà sản xuất `fiio.com/k9proess_parameters` (tra 2026-11-24):
- USB: `REGISTRY_CONFIG` — PCM 16/24/32-bit, tối đa 384 kHz, DSD256. Trang không liệt kê từng tần số trung gian: danh sách là các tần số chuẩn ≤ mức tối đa (**giả định**, đã ghi vào `source`).
- S/PDIF: `REGISTRY_UNVERIFIED` — có thông số cổng quang (96 kHz/24-bit) nhưng chưa có căn cứ cho đường Android → S/PDIF.
- Bluetooth: `REGISTRY_UNVERIFIED` — không mặc nhiên bit-perfect.
GRAPE2Play **chưa tự kiểm thử** thiết bị này. Không có VID/PID vì chưa có nguồn đáng tin.

Nhận diện: `BitPerfectProbe.detectedOf` lấy tên sản phẩm, loại kết nối (`routeOf`), VID/PID chỉ khi đúng **một** thiết bị USB (`UsbManager`) trùng tên sản phẩm (nếu không: không biết). Đối chiếu (`DeviceRegistry.match`): khớp theo model **và** phương thức kết nối; khác phương thức thì chỉ liệt kê ở `otherConnections`, không áp dụng kết quả; cùng tên khác VID/PID → không khớp; đúng VID/PID → `EXACT_ID`; chỉ tên → `NAME_ONLY` (hộp thoại nói rõ "khớp theo tên"); nhập nhằng → không đoán.

Xác nhận (`decide`): chỉ hỏi với USB/HDMI/S/PDIF/Bluetooth. Khớp danh mục → "Đúng/Không đúng thiết bị"; lạ → "Khai báo/Để sau". Không hỏi lại thiết bị đã lưu, đã từ chối hoặc đã bỏ qua; đổi phương thức kết nối (khoá hồ sơ chứa `conn`) thì hỏi lại. "Không đúng" → không đăng ký, không gán model khác, chuyển sang khai báo. Người dùng khai báo = `USER_DECLARED`: lưu ở hồ sơ cá nhân, **không** vào danh mục chung, **không** cấp mức xác minh, thông số tự khai **không** đi vào đánh giá. Xác nhận thiết bị chỉ liên kết hồ sơ; không phải bằng chứng dữ liệu được bảo toàn.

Quản lý (BitPerfect ⓘ → "Quản lý thiết bị Bit-perfect"): xem thiết bị, phương thức đã xác nhận, VID:PID, thông số/nguồn danh mục; sửa ghi chú/hãng/model **chỉ của hồ sơ người dùng khai báo**; xóa khỏi hồ sơ cá nhân; phân biệt "app phát hiện, bạn xác nhận" với "bạn khai báo".

## 7. Giới hạn chưa xử lý
- Android < 13: route luôn Unknown (không có API đáng tin). Android 14 có `AudioMixerAttributes` bit-perfect cho USB nhưng app chưa dùng (sẽ thay đổi đường phát, nằm ngoài phạm vi).
- Không đọc được: HAL/DAC, TV/ampli sau HDMI/S/PDIF, định dạng USB đang chạy, mọi thứ trong renderer UPnP/Cast, codec Bluetooth, độ lợi thật của âm lượng.
- Verified – Configuration **không loại trừ** việc mixer Android đổi tần số/trộn; do đó Resampling vẫn "Unknown".
- Không đọc được VID/PID khi có nhiều thiết bị USB cùng tên hoặc tên Android báo khác tên USB → chỉ khớp theo tên.
- Danh mục mới có một model; cần mở rộng khi có căn cứ.
- Chưa có kiểm thử thiết bị thật nào (USB DAC, HDMI, quang, BT, UPnP, Cast, Android TV).

## 8. Không thay đổi
`Playback.kt`, Audio Engine, decoder, DSD/DoP/PCM, DSP/EQ, USB/NAS, UPnP/Cast, buffering, chuyển bài, thuật toán chọn thiết bị. Chỉ thêm các lệnh **đọc** (`Playback.sinkFmt`, `Playback.context`, `AudioManager`, `UsbManager`) và các lời gọi `DeviceProfiles.check()` ở `MainActivity`/`OutputPage` (không làm thay đổi việc chọn/phát).

## 9. Danh mục thiết bị Bit-perfect Verified Devices (2.9.9)
Chi tiết ở DEVICE_REGISTRY.md. Tóm tắt phương pháp:
- **Bốn khái niệm tách biệt:** (1) *nhận diện thiết bị* — biết đúng model; (2) *xác minh khả năng định dạng/giao thức* — tài liệu/thử nghiệm cho thấy thiết bị nhận định dạng đó qua kết nối đó; (3) *xác minh cấu hình có thể bit-perfect* — thiết bị + kết nối + cấu hình giữ nguyên dữ liệu, kèm điều kiện DSP/âm lượng/resampling/DSD và bằng chứng; (4) *bit-perfect đầu cuối* — đo được dữ liệu không đổi tại đầu nhận. Chỉ (3)–(4) mới là kết luận về bit-perfect; hiện danh mục chỉ đạt (1)–(2).
- Không xếp "Verified" chỉ vì: hỗ trợ FLAC/WAV/Hi-Res/DSD, có cổng USB/quang/đồng trục/HDMI, người dùng xác nhận tên model, hay ứng dụng khác phát được.
- Khả năng theo từng kết nối: cấu hình UPnP của một thiết bị không áp dụng cho USB/HDMI/quang của chính nó.
- Danh mục (tra cứu) và `BitPerfect.evaluate` (phiên phát) độc lập: danh mục không làm phiên phát thành VERIFIED; luật `VERIFIED – CONFIGURATION` giữ nguyên, vẫn dựa vào `DeviceRegistry.builtin()`.
- Ngày nghiên cứu cuối: 2026-11-25. Nguồn: trang thông số/manual chính thức đã đọc được; nguồn người dùng (JBL L75ms Native DSD qua mconnect; WiiM Ultra DSD→PCM qua DLNA) ghi rõ là `USER_TEST`, tối đa mức 2.
- Giới hạn: độ phủ nhỏ (35 thiết bị), nhiều thông số chỉ là trang hãng (không đo), tên UPnP modelName và USB VID:PID chưa được xác minh cho hầu hết model (nên khớp chủ yếu theo hãng+model), không có thiết bị nào đạt mức 3–4.
- Dự phòng dữ liệu: schema lạ hoặc tệp lỗi → danh mục rỗng + cảnh báo trong app; phiên phát và `BitPerfect.evaluate` không bị ảnh hưởng. Trạng thái kiểm thử: xem TEST_REPORT.md (giao diện chưa được chạy trên thiết bị).
