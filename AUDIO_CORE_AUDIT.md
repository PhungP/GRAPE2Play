# AUDIO CORE AUDIT — 2.9.3 → 2.9.4

Phạm vi: rà soát bằng ĐỌC MÃ + test logic thuần. Không có build Android, không có thiết bị thật.

## 1. Đầu ra âm thanh
| Phát hiện | Xử lý |
|---|---|
| `sinks()` dùng `distinctBy { it.type }` → nhiều USB DAC/HDMI/BT cùng loại bị gộp, chỉ chọn được một | Bỏ; sắp xếp ổn định; tiêu đề thêm tên sản phẩm khi trùng loại |
| Lưu lựa chọn chỉ bằng `TYPE_*` | Khoá `loại|địa chỉ|tên` (DeviceMatch.java); địa chỉ chỉ có từ Android 9 (API 28), Android 8 dùng loại+tên |
| `OutputPage` lấy danh sách một lần (`remember { sinks }`) | `DisposableEffect` đăng ký `AudioDeviceCallback`, cập nhật khi cắm/rút |
| Callback ở Playback.kt (`reapplyDevice`) | Vẫn còn từ trước (không bị mất ở 2.9.3), giữ nguyên |
| Thiết bị bị rút | `match()` trả null → `setPreferredAudioDevice(null)` → mặc định hệ thống; khoá vẫn lưu nên tự quay lại khi cắm lại |
Giới hạn: hai thiết bị cùng tên và (trên Android 8) không có địa chỉ không phân biệt được; khi thiết bị cắm lại nhưng có nhiều thiết bị trùng tên, app không đoán mà dùng mặc định.

## 2. Hi-res
Văn bản giao diện và nhãn đã sửa (OutputPage). Cơ chế phát không đổi.

## 3. Chẩn đoán chuyển bài
Tín hiệu quan sát được: `configure()` có được gọi không; định dạng PCM vào sink có đổi không. KHÔNG quan sát được: AudioTrack có bị tạo lại, khoảng lặng thực tế. Nhật ký nay có ba mức câu và luôn thêm "Chưa đo được khoảng lặng thực tế". Đã bỏ phép so sánh `snapshot` dễ chạy đua; `gaplessJob` huỷ lần trước nếu chuyển bài dồn dập.
UPnP: `arm()` gọi `SetNextAVTransportURI`; phát hiện **lỗi thật**: đổi Trộn/Lặp/thêm bài sau khi đã nạp sẵn → loa vẫn phát bài cũ. Sửa bằng `refreshNext()` (nạp lại, hoặc cố xoá bài nạp sẵn). Cast: không có API nạp sẵn trong mã → mỗi bài LOAD riêng, có khoảng chờ; đã ghi trong mô tả.

## 4. DSP/EQ
Đáp tuyến 10 băng đúng tại tâm (±0,05 dB, 44,1/48/96/192 kHz). Preamp −12…+6 dB: dương bị headroom đưa về −0,3 dB (không còn nâng quá 0 dBFS). Cân bằng L/R đúng. Phát hiện thực nghiệm: bản 2.9.3 (headroom đúng bằng đỉnh) vẫn vọt tới +1,4 % (0,12 dB) khi tín hiệu bắt đầu đột ngột → thêm dư 0,3 dB. Sau đó sin đầy thang 20 Hz–20 kHz × 12 cấu hình × 3 preamp: 0 mẫu cắt đỉnh (không headroom: 13.770.229). **Không chống clipping tuyệt đối**: sóng vuông đầy thang qua preset "vocal" vẫn vượt 0 dBFS 4.836 mẫu; "+12 dB mọi băng" 3.065 mẫu. Hành vi thay đổi âm thanh: EQ nâng nghe nhỏ hơn (preset bass ≈ −6,5 dB). EQ phẳng/tắt không đổi (chép nguyên byte; test xác nhận cho 16/24/32-bit/float).

## 5. Playback.kt (hàm đã rà)
| Hàm | Kết quả |
|---|---|
| `pinShuffle` | Đúng (hoán vị, bài đầu); lưu bản sao `shuffleIdx` để không quên thứ tự khi thêm bài |
| `retryHere` → `scheduleRetry` | Sửa: luôn hết trạng thái chờ; kiểm `playWhenReady` nên không tự phát khi Pause; người dùng bấm Phát/Tạm dừng khi chờ = huỷ |
| `isTransientIo` | Chỉ IO_NETWORK_CONNECTION_FAILED/TIMEOUT/UNSPECIFIED |
| `skipNext` | Dùng `PlayLogic.skipTarget` (có test) |
| `handleError` | Viết lại thành bảng quyết định `PlayLogic.decide` (có test, kể cả không kẹt vòng lặp); USB copy không ép phát sau khi sao chép |
| `onMediaItemTransition` | Bỏ qua PLAYLIST_CHANGED/khi phát từ xa để khỏi đếm trùng và khỏi đặt lại vị trí |
| `applySettings` | Giữ index/vị trí; **giới hạn**: thứ tự xáo trộn còn lại bị xáo lại (bài đã nghe có thể nghe lại) |
| `loadRemote` | Thêm kiểm tra chỉ số |
| `advanceRemote` | Không đổi; `updateNext` nay nhắc loa cập nhật bài kế |
| `setTarget` | Sửa: quay về TV khi đang tạm dừng trên loa ngoài không còn tự phát; luồng cục bộ dừng hẳn khi sang loa ngoài (không phát trùng) |
Hành vi phục hồi UPnP 2.9.2 (`refreshRenderer`, STOPPED giữa bài) không bị đụng tới.

## 6. Bit-perfect
Xem BITPERFECT.md.

## Còn tồn tại / chưa kiểm
- Toàn bộ mã Kotlin chưa biên dịch (BUILD NOT VERIFIED).
- ExoPlayer, AudioTrack, USB DAC, NAS, UPnP, Cast, DSD: chưa kiểm trên thiết bị.
- `ShuffleOrder.DefaultShuffleOrder(int[], long)` và `ExoPlayer.setShuffleOrder` dùng theo API media3 1.3.1 — cần biên dịch để xác nhận.
- Khoảng lặng thực tế giữa các bài chưa đo được.
