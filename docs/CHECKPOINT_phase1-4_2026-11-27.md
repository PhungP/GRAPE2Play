# GRAPE2Play — Checkpoint sau đợt Phase 1–4 (2026-11-27)

Bổ sung cho `CHECKPOINT_3.0.5_2026-11-27.md` (vẫn đúng với các ràng buộc và quyết định kiến trúc). Chỉ ghi sự thật đã kiểm chứng; chưa kiểm được thì ghi rõ.

## Đã làm (commit trên `main`, chưa push; không đổi version/ký/OTA)
- **Hàng đợi bền**: `QueueStore/QueueSession/QueuePersist` — lưu/khôi phục (tạm dừng), ghi nguyên tử, giữ bài chưa tìm thấy, trạng thái rỗng tường minh.
- **Kho dữ liệu an toàn**: `SafeJsonStore/SafePrefStore/LatestWriter/PersistCodec` cho Favorites, Playlist, Recents, Nguồn (kèm mật khẩu), Hồ sơ/xác minh thiết bị, Sửa tag, Lịch sử nghe. Dữ liệu hỏng không bị ghi đè bằng rỗng; có `.bak`, `v.corrupt`, khoá ghi.
- **Quét thư viện an toàn**: `ScanResult/ScanMerge/IncrementalView/ScanGuard/ScanPolicy` — COMPLETE/PARTIAL/FAILED/CANCELLED, publish tăng dần không thu hẹp, rỗng không đáng tin thì giữ cache, an toàn theo từng ổ cục bộ, một lần quét/nguồn, hủy theo thế hệ, xoá nguồn không chặn UI.
- **Quản lý hàng đợi/playlist**: menu hành động theo bài (nhấn giữ cảm ứng, giữ OK/Menu trên remote); `QueueOps/PlaylistOps` (thuần Java); `Playback.removeFromQueue/moveInQueue/moveToNext/playNext`.
- **Thư viện**: tìm kiếm (`SearchIndex`, bỏ dấu, 60k bài), thứ tự đĩa/số bài/tên tự nhiên (`TrackOrder`), Lịch sử nghe (`PlayHistory`), Mới thêm (theo mtime, nguồn cục bộ), tab Nghệ sĩ album / Năm.
- **Nhận diện nhạc (AudD, khoá người dùng, micro)**: `MusicId/MusicIdCodec/SecretBox` — đối chiếu docs.audd.io, phản hồi thật làm fixture, khoá mã hoá Keystore. Không chạm Audio Engine.
- **Thiết bị**: tạm dừng khi thiết bị đầu ra đã chọn bị rút; nhãn DSD cho Cast không còn là NATIVE; MediaSession nhảy tới mục hàng đợi.

## Bằng chứng Phase 2 (không đổi đường âm thanh)
- FLAC (`FlacPcmTests`): giải mã đúng từng bit 16/24-bit, 44,1–192 kHz, mọi kiểu ghép kênh stereo, FIXED bậc 0–4, CONSTANT; header WAV giữ tần số/độ sâu; tua nhất quán. Hành vi khung hỏng (đặc tả, CHƯA sửa): CRC16 không được kiểm (bit hỏng đi qua âm thầm); khung có tiêu đề hỏng bị bỏ qua → đầu ra ngắn hơn khai báo, các khung sau tự đồng bộ lại.
- DSD→PCM (`DsdPcmTests`, DSD tổng hợp): độ lợi đơn vị (0,5000), phẳng 0,007 dB tới 15 kHz, không cắt đỉnh ở 0,7, toàn bit 1 = full-scale. Mối lo "thiếu headroom" không xảy ra với DSD thông thường.
- Chưa kiểm chứng được (cần thiết bị/nghe/đo): dither 24→16 (phụ thuộc Media3), gapless thực tế, UPnP/Cast/USB DAC thật, Bit-perfect end-to-end.

## Chưa làm / giới hạn
- Giao diện TV (D-pad) cho menu/playlist/lịch sử: biên dịch và logic đã test, chưa kiểm bằng remote thật; emulator chỉ chạy bố cục điện thoại.
- AudD: chưa gọi với khoá hợp lệ và âm thanh thật; emulator không có micro thật.
- (Các mục này đã được xử lý ở phần "Đợt tiếp theo" bên dưới.)

## Đợt tiếp theo (cùng ngày) — đã làm
- **Quét khởi động** (`StartupScan`, `Library.startupScanAsync`): nguyên nhân gốc = `MainActivity` chỉ quét khi thư viện rỗng; khi `LibraryProvider` nạp cache trước thì quét bị bỏ qua. Nay luôn kiểm tra đúng một lần/tiến trình, chạy trên scope IO của Library (không chặn UI, không gắn vòng đời Activity), nạp cache có mutex, quét dở được thử lại. Đã tái hiện và kiểm chứng trên emulator (Provider nạp cache → thêm file → mở Activity → file được phát hiện).
- **FLAC**: CRC16 được kiểm theo chính sách libFLAC (khung sai → im lặng đúng độ dài, báo + đếm); khung mất đồng bộ được neo bằng số khung/số mẫu của khung hợp lệ kế tiếp và lấp im lặng (đầu ra giữ đúng độ dài); sau CRC sai vị trí đọc không được tin (quay lại sau mã đồng bộ). File hợp lệ vẫn đúng từng bit (16/24-bit, 44,1–192 kHz, mọi ghép kênh stereo). Fuzz 400 ca. Hư hỏng → `DECODE_DAMAGE` → NOT_BIT_PERFECT (không bao giờ Verified); log có giới hạn tần suất.
- **Bí mật lúc lưu**: khoá AI, mật khẩu nguồn (SMB/WebDAV/cloud) và khoá AudD mã hoá bằng Keystore (`SecretBox`, "enc1:", kiểm giải mã lại, di trú lười, xoá `.bak` plaintext). Hạ cấp về bản cũ sẽ không đọc được giá trị đã mã hoá.
- **Xác minh thiết bị**: nhãn DSD của phiên (`DeviceVerification.sessionDsdMode`) là nguồn duy nhất cho so khớp và form xác minh (trước đó Cast+DSD bị lệch nhãn nên không bao giờ khớp EXACT).
- **Giao diện**: menu quản lý hàng đợi/playlist có ở mọi bố cục, kể cả hàng đợi TV của chủ đề Alu và SciFi.
- **Hiệu năng (emulator, 8000 file)**: quét đầu 19,4 s, quét lại không đổi 4,0 s, PSS 173 MB, tìm kiếm tức thì ("0777" → 11 kết quả đúng), không ANR.
- **AudD** đã đối chiếu docs.audd.io + phản hồi thật.

## Cần kiểm tra trên thiết bị thật (chưa làm được)
- **TV/remote (D-pad)**: menu hành động (giữ OK / phím Menu), playlist, lịch sử, hàng đợi ở chủ đề mặc định, Alu, SciFi.
- **USB DAC / HDMI / S/PDIF**: tạm dừng khi rút thiết bị đã chọn; kết nối lại; trạng thái Bit-perfect thật; gapless; dither 24→16 (phụ thuộc Media3).
- **UPnP / Cast thật**: gửi DSD gốc, DoP, FLAC/WAV/ALAC; kết quả tự xác minh.
- **AudD**: một lần nhận diện thật với khoá hợp lệ và micro thật; quyền micro trên TV.
- **FLAC hỏng thật** (khác file tổng hợp): xác nhận chính sách im lặng/lấp khoảng trống là hợp lý khi nghe.
- Cập nhật cài đè lên bản đang có dữ liệu thật (di trú khoá/mật khẩu, hàng đợi, playlist).
