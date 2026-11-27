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
- Các biến thể giao diện chủ đề (Alu/SciFi) giữ danh sách hàng đợi cũ (chỉ nhảy bài), chưa có menu quản lý.
- AudD: chưa gọi với khoá hợp lệ và âm thanh thật; emulator không có micro thật.
- Chưa sửa: CRC16 FLAC, khung hỏng; quét tự động lúc khởi động có thể bị bỏ qua khi `LibraryProvider` nạp cache trước (nguyên nhân khả dĩ, chưa xác nhận); khoá AI/nguồn NAS còn plaintext (SecretBox mới áp dụng cho khoá AudD).
