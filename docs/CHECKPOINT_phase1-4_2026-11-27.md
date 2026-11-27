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

## Đợt kiểm tra chất lượng / chuẩn bị nghiệm thu (cùng ngày)
- **FlacPcm đối chiếu đặc tả/libFLAC**: phát hiện và sửa điểm yếu thật — mã đồng bộ giả trong payload (tiêu đề giả hợp lệ CRC8) từng được chấp nhận làm mốc và làm lệch âm thanh phía sau (tái hiện bằng test đối kháng). Nay ứng viên tìm được khi resync chỉ được chấp nhận nếu CRC16 cả khung đúng; tiêu đề được kiểm chặt (bit dành riêng, mã tần số 15, UTF-8 số khung). Có test luồng độ dài khối thay đổi (sync 0xFFF9, số mẫu UTF-8 nhiều byte). Giới hạn đã biết: luồng cố định mà STREAMINFO min≠max/không rõ thì không neo được theo số khung (khung mất làm đầu ra ngắn hơn như trước).
- **Di trú Keystore**: trình tự ghi → đọc lại → giải mã đối chiếu → mới xoá `.bak` plaintext; dọn `.bak` còn sót nếu lần trước dừng dở; bản mã không giải mã được (mất khoá) được GIỮ NGUYÊN qua các lần lưu và báo trên giao diện/nhật ký (nguồn NAS, khoá AI, khoá AudD). `allowBackup=false`. Đã kiểm trên emulator với Keystore thật: di trú thường, dừng dở, mất khoá.
- **Git (18 commit local)**: không có file nhị phân/dữ liệu thử/khoá/mật khẩu/email cá nhân/đường dẫn máy; tác giả dùng email noreply của GitHub; giá trị giả trong test chỉ là chuỗi giả.
- **Cài đè**: cấu hình ký, `applicationId`, `versionCode/Name` (108 / 3.0.5) và manifest (kể cả quyền) KHÔNG đổi so với commit phát hành `f1bf769`. Đã cài đè bản mới lên build của mã nguồn 3.0.5 (cùng khoá debug) trên emulator: dữ liệu do app cũ ghi (yêu thích, gần đây) và dữ liệu cũ gieo vào (playlist, sửa tag, nguồn, khoá AI, hồ sơ thiết bị) đều giữ nguyên và hiển thị đúng; khoá/mật khẩu được mã hoá. CHƯA kiểm bằng khoá release thật (cần keystore của chủ dự án). Trước khi phát hành phải tăng version.
- **Hướng dẫn nghiệm thu thực tế**: `docs/ACCEPTANCE_GUIDE.md`.

## Trạng thái bằng chứng (không tuyên bố PASS cho thiết bị thật)
| Hạng mục | [AUTO] | [EMU] | [REAL] |
|---|---|---|---|
| FLAC giải mã/chính sách hư hỏng/anchoring | có (10k+ test, fuzz) | phát file tổng hợp | chưa |
| Quét khởi động, kho dữ liệu an toàn, quét thư viện | có | có | chưa |
| Mã hoá khoá/mật khẩu, di trú, mất khoá | logic có | có (Keystore thật) | chưa |
| Hàng đợi/playlist/lịch sử/tìm kiếm | logic có | có (phone) | chưa (TV/D-pad) |
| AudD | phân tích phản hồi + chính sách | đường thu (im lặng) | chưa (khoá + micro thật) |
| Bit-perfect, gapless, USB DAC, HDMI, UPnP, Cast | logic có | không áp dụng | chưa |
| Cài đè OTA | cấu hình không đổi | có (khoá debug) | chưa (khoá release) |

## Đợt nghiệm thu / chốt phát hành (cùng ngày)
- **FLAC đối chiếu đặc tả**: đọc trực tiếp RFC 9639 (§8.2 STREAMINFO, §9.1 tiêu đề khung, §9.1.5 số khung/số mẫu, CRC-8/CRC-16) và `stream_decoder.c` của libFLAC (`read_frame_header_`, `frame_sync_`, xử lý CRC16 sai → `FRAME_CRC_MISMATCH` + quay lại sau mã đồng bộ, lấp im lặng khi thiếu khung chỉ với số mẫu).
- Kết luận: vị trí mẫu của khung cố định (`0xFFF8`) suy ra được theo đặc tả **chỉ khi STREAMINFO min == max**. Với min != max hoặc thiếu, không khôi phục tin cậy được (libFLAC dùng quy ước cũ hoặc báo UNPARSEABLE). **Giữ giới hạn**, không đoán; nhưng mất khung không neo được và file cụt nay **được đếm là hư hại** (→ NOT_BIT_PERFECT, không bao giờ "giải mã hoàn chỉnh"). Thêm kiểm cỡ khối khi neo. Test FP14b/c/d [UNIT].
- `docs/ACCEPTANCE_GUIDE.md`: thêm giới hạn FLAC, quy trình OTA bằng khoá release (chủ dự án), quy ước bằng chứng. `scripts/verify_ota.sh` (chỉ đọc APK; cùng chứng chỉ, cùng applicationId, versionCode > 108, versionName đổi).
- Chưa làm (việc của chủ dự án): tăng version, ký release bằng keystore, chạy script, cài đè trên máy thật, toàn bộ mục thiết bị thật trong ACCEPTANCE_GUIDE. Không có mục nào được ghi VERIFIED.
- Kiểm tra: 10275 test lõi đạt; `gradle clean :app:assembleDebug :app:testDebugUnitTest` thành công; không có file nhị phân/keystore/local.properties trong Git; không có mẫu bí mật trong diff so với f1bf769; applicationId/versionCode/versionName/cấu hình ký không đổi.
