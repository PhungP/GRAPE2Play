# GRAPE2Play — Hướng dẫn nghiệm thu thực tế (TV, remote, USB DAC, HDMI, UPnP, Cast, FLAC lỗi, AudD, cài đè)

Mục đích: kiểm tra các hạng mục **chưa thể xác minh bằng test tự động hoặc emulator**. Mỗi mục ghi rõ bước làm, kết quả mong đợi và ô ghi kết quả. **Không điền PASS nếu chưa tự thực hiện trên thiết bị thật.**

## 0. Chuẩn bị
- Cài APK debug: `adb install -r GRAPE2Play-3.0.5-debug-<commit>.apk` (cùng package `com.grape.play`; ký bằng khoá debug nên KHÔNG cài đè được lên bản release ký bằng khoá chủ dự án — xem mục 9).
- Bật nhật ký: Cài đặt → Nhật ký lỗi → Xem/gửi nhật ký; hoặc `adb logcat -s GRAPEPlay`.
- Chuẩn bị nhạc thử: PCM 16/44.1, 24/96, 24/192 (FLAC/WAV), một file DSD (DSF), một FLAC nguyên vẹn và bản sao bị hỏng (mục 7).
- Phân loại bằng chứng khi báo cáo: **[AUTO]** test tự động · **[EMU]** emulator · **[REAL]** thiết bị thật. Chỉ [REAL] mới được coi là nghiệm thu thiết bị.

Bảng ghi: `Kết quả` = PASS / FAIL / N/A · `Bằng chứng` = ảnh chụp, log, thiết bị + firmware.

## 1. Android TV và remote (D-pad)
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 1.1 | Mở app, di chuyển tiêu điểm bằng ▲▼◀▶ qua thanh tab (kể cả Playlist, Lịch sử, Mới thêm, Nghệ sĩ album, Năm) | Tiêu điểm đi hết các tab, không nhảy sang menu khác, cuộn theo | | |
| 1.2 | Ở tab Bài hát, chọn ô tìm, bấm OK, gõ bằng bàn phím trên màn hình/remote | Ô nhập mở, danh sách lọc, ◀ thoát không treo | | |
| 1.3 | Chọn một bài, **giữ OK** (hoặc phím Menu) | Mở menu hành động; tiêu điểm vào mục đầu tiên | | |
| 1.4 | Trong menu: Yêu thích, Phát tiếp theo, Thêm vào cuối, Thêm vào playlist… | Mỗi mục chạy đúng; Back đóng menu | | |
| 1.5 | Mở hàng đợi (Đang phát → Danh sách), giữ OK trên một bài | Menu có: Chuyển lên/xuống, Xoá bài này, Xoá phía trên/phía dưới, Xoá tất cả trừ bài đang phát | | |
| 1.6 | Lặp 1.5 ở các chủ đề: mặc định, Alu, Robot/SciFi, các chủ đề hãng | Cùng menu ở mọi chủ đề | | |
| 1.7 | Xoá bài **đang phát** khỏi hàng đợi | Phát tiếp bài kế, không giật, không crash | | |
| 1.8 | Tạo playlist, đổi tên, thêm/xoá/sắp xếp bài, xoá playlist (2 bước) bằng remote | Mọi nút với tới được và hoạt động | | |
| 1.9 | Tab Lịch sử, Mới thêm; Xoá lịch sử (2 bước) | Danh sách đúng, cuộn mượt | | |
| 1.10 | Thoát app bằng Home rồi mở lại | Hàng đợi khôi phục ở trạng thái **tạm dừng** đúng bài/vị trí | | |
| 1.11 | Launcher TV: hàng GRAPEPlay, bấm một mục trong hàng đợi MediaSession | Nhảy tới bài đó | | |

## 2. USB DAC
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 2.1 | Cắm DAC, Đầu ra → chọn DAC | Tên đầu ra đổi; phát bình thường | | |
| 2.2 | Phát 16/44.1, 24/96, 24/192 | DAC báo đúng tần số/độ sâu (đèn/màn hình DAC); ghi lại | | |
| 2.3 | Mở trạng thái Bit-perfect | Trung thực: không "Verified" nếu app không có bằng chứng; hiện các mã chưa xác minh | | |
| 2.4 | **Rút DAC khi đang phát** | App **tạm dừng**, báo "Thiết bị đầu ra đã chọn bị rút"; không tự phát ra loa | | |
| 2.5 | Cắm lại DAC, bấm phát | Phát trên DAC, trạng thái phục hồi | | |
| 2.6 | DSD: phát DSF tới DAC | Ghi nhận DSD→PCM (nhãn trung thực), không tuyên bố native | | |
| 2.7 | Thiết bị → Verify Playback Capability: tự ghi kết quả thử | Lưu được; mức độ tin cậy đúng ("nghe" ≠ Confirmed) | | |

## 3. HDMI (TV/AVR)
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 3.1 | Chọn đầu ra HDMI, phát PCM 24/96, 24/192 | AVR hiển thị định dạng nhận; ghi lại | | |
| 3.2 | Bit-perfect | Báo "đường TV chưa biết" (không Verified) | | |
| 3.3 | Rút/cắm HDMI khi đang phát | Tạm dừng; cắm lại phát được | | |
| 3.4 | Nếu đầu ra không nhận tần số cao với DSD | Tự hạ tốc độ DSD→PCM có thông báo | | |

## 4. UPnP / DLNA renderer
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 4.1 | Đầu ra → quét, chọn loa UPnP | Thiết bị hiện (kể cả loa chỉ trả lời unicast) | | |
| 4.2 | Phát FLAC/WAV/ALAC | Phát được; loa báo định dạng | | |
| 4.3 | DSD: chế độ "DSD gốc" và "DoP" | Ghi nhận loa có phát được không; nhãn phiên = "gửi gốc"/DoP | | |
| 4.4 | Gapless giữa hai bài liền kề (SetNextAVTransportURI) | Không khoảng lặng nghe được (ghi chú chủ quan) | | |
| 4.5 | Tắt loa giữa chừng, bật lại | App báo lỗi gọn, phục hồi khi chọn lại | | |
| 4.6 | Verify Playback Capability cho đúng tổ hợp | Kết quả chỉ áp dụng đúng tổ hợp (EXACT) | | |

## 5. Google Cast
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 5.1 | Quét Cast, chọn thiết bị | Thiết bị hiện (mDNS hoặc dò HTTP) | | |
| 5.2 | Phát FLAC, WAV, ALAC | Phát được hoặc lỗi rõ ràng theo thiết bị | | |
| 5.3 | DSD tới Cast | Ghi nhận thực tế (app chỉ gửi file gốc); không tuyên bố native | | |
| 5.4 | Mất kết nối giữa chừng | Báo lỗi, không treo | | |

## 6. Mất kết nối thiết bị và khôi phục (tổng hợp)
USB DAC rút/cắm (2.4–2.5), HDMI (3.3), UPnP tắt/bật (4.5), Cast (5.4), Wi-Fi tắt khi phát từ NAS (app thử lại, không treo). Ghi thời gian phục hồi.

## 7. FLAC lỗi
Tạo file lỗi từ một FLAC nguyên vẹn (trên máy tính), **không** dùng file duy nhất của bạn:
1. Sao chép `nhac.flac` thành `nhac_hong.flac`.
2. Làm hỏng 1 byte ở giữa file: `printf '\x55' | dd of=nhac_hong.flac bs=1 seek=$(( $(stat -f%z nhac_hong.flac) / 2 )) conv=notrunc` (Linux: `stat -c%s`).
3. Làm bản cụt: `head -c $(( $(stat -f%z nhac.flac) * 3 / 4 )) nhac.flac > nhac_cut.flac`.

| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 7.1 | Phát `nhac.flac` (nguyên vẹn) | Không cảnh báo giải mã trong nhật ký; Bit-perfect không có mã "khung hỏng" | | |
| 7.2 | Phát `nhac_hong.flac` qua loa/DAC, nghe đoạn giữa | Có thể có một khoảng im lặng ngắn (≈ 1 khung) tại chỗ hỏng; **không** có tiếng nổ/nhiễu lớn kéo dài; bài vẫn phát tới hết, thời lượng không đổi | | |
| 7.3 | Nhật ký (`adb logcat -s GRAPEPlay`) | Có dòng "Decoder: … CRC16 sai …" hoặc "mất N mẫu … đã lấp bằng im lặng" (tối đa một dòng/phút/file) | | |
| 7.4 | Trạng thái Bit-perfect của bài hỏng | **NOT_BIT_PERFECT** kèm giải thích "file có khung dữ liệu hỏng" | | |
| 7.5 | Phát `nhac_cut.flac` | Không crash; kết thúc sớm ở chỗ file thật sự hết | | |
| 7.6 | Tua qua chỗ hỏng, tua ngược | Không treo, âm thanh khác chỗ hỏng bình thường | | |
Ghi ý kiến nghe: im lặng một khung có chấp nhận được không?

**Giới hạn đã biết (đối chiếu RFC 9639 + libFLAC, đã có test đơn vị [UNIT])**:
- Khung độ dài cố định (`0xFFF8`) chỉ định vị được mẫu chính xác khi STREAMINFO có `min == max` (số khung × cỡ khối). Nếu `min != max` hoặc STREAMINFO thiếu/0 thì sau khi mất khung **không thể** khôi phục vị trí mẫu theo đặc tả (libFLAC chỉ đoán theo quy ước cũ hoặc báo UNPARSEABLE_STREAM). App **không đoán**: không chèn im lặng, nhưng đếm là hư hại → trạng thái NOT_BIT_PERFECT, nhật ký ghi "không xác định được vị trí mẫu chính xác"; âm thanh phía sau có thể lệch thời gian.
- File cụt (ít mẫu hơn STREAMINFO) cũng được đếm là hư hại; không bao giờ được coi là giải mã hoàn chỉnh.
- Khung `0xFFF9` (khối thay đổi) mang số mẫu trực tiếp nên luôn neo được.

## 8. AudD (nhận diện nhạc)
Cần khoá API AudD hợp lệ và thiết bị có micro (đa số TV KHÔNG có micro: kỳ vọng "Thiết bị này không có micro").
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 8.1 | Mở app, **không bấm gì** 2 phút (theo dõi mạng/log) | Không ghi âm, không có kết nối tới api.audd.io, log không có "gửi … tới AudD" | | |
| 8.2 | Bấm "Nghe & nhận diện" khi chưa nhập khoá | Thông báo "Hãy nhập khoá", không ghi âm | | |
| 8.3 | Nhập khoá, bấm nút, **từ chối** quyền micro | Thông báo cần quyền; không ghi âm | | |
| 8.4 | Cấp quyền, phát nhạc gần micro, bấm nút | Đếm 0–10 s, "Đang gửi…", hiện tên bài/nghệ sĩ đúng | | |
| 8.5 | Âm thanh không nhận ra (tiếng ồn) | "Không nhận ra bài này" (không phải lỗi) | | |
| 8.6 | Khoá sai | Thông báo khoá không hợp lệ (mã 900) | | |
| 8.7 | Tắt mạng rồi bấm | Thông báo lỗi kết nối; thử lại tối đa 1 lần; không treo | | |
| 8.8 | Bấm Huỷ giữa lúc ghi | Dừng ngay, micro được giải phóng | | |
| 8.9 | Micro bị tắt/im lặng | "Micro không thu được âm thanh", **không gửi** | | |
| 8.10 | Trong lúc đang phát nhạc bằng app, nhận diện | Phát nhạc không bị gián đoạn/đổi chất lượng | | |
| 8.11 | Khoá được lưu mã hoá: `adb shell run-as com.grape.play cat shared_prefs/musicid.xml` (bản debug) | Giá trị bắt đầu bằng `enc1:`, không thấy khoá rõ | | |
| 8.12 | Nhật ký không chứa khoá API | `adb logcat` không có chuỗi khoá | | |

## 9. Cài đè (OTA) từ bản đang phát hành
**Điều kiện bắt buộc (không tự làm trong repo):**
- Bản release phải ký bằng **khoá của chủ dự án** (chứng chỉ SHA-256 ghi trong checkpoint: `853ecf48…06f4`). APK debug dùng khoá debug → Android sẽ từ chối cài đè lên bản release (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Kiểm tra bản build release: `apksigner verify --print-certs app-release.apk` và so SHA-256 chứng chỉ.
- `applicationId` (`com.grape.play`) và cấu hình ký **không thay đổi** so với 3.0.5.
- Khi phát hành phải **tăng** `versionName`/`versionCode` (hiện vẫn 3.0.5 / 108): Updater chỉ đề nghị khi tag > phiên bản đang chạy, và Android cần versionCode lớn hơn để cập nhật.

**Kiểm tra chứng chỉ bằng script (chủ dự án chạy; script CHỈ đọc APK, không dùng keystore/mật khẩu):**
```
scripts/verify_ota.sh <apk_dang_phat_hanh_3.0.5> <apk_moi_da_ky>
```
Script kiểm: chữ ký hợp lệ cả hai; **cùng chứng chỉ SHA-256** (phải là `853ecf48…06f4`); cùng `applicationId`; `versionCode` mới **>** 108; `versionName` đã đổi. Thoát 0 = đạt. (Đã thử trên APK debug: các kiểm tra chữ ký/applicationId chạy đúng; kiểm tra tăng phiên bản báo FAIL đúng như mong đợi khi so một APK với chính nó.)

**Build bản release (chủ dự án, trên máy giữ keystore; mật khẩu chỉ nằm trong biến môi trường của phiên shell, không ghi vào repo/log/lệnh):**
1. Tăng `versionCode`/`versionName` trong `app/build.gradle.kts` (bước của chủ dự án, chưa làm trong repo).
2. `export GRAPE_KEYSTORE=… GRAPE_KEY_ALIAS=…` và nhập `GRAPE_KEYSTORE_PASSWORD` (`read -rs`) rồi `./gradlew :app:assembleRelease`.
3. Chạy `scripts/verify_ota.sh`, rồi `adb install -r app-release.apk` đè lên máy đang có 3.0.5 và điền bảng dưới. Nếu **không có keystore** trên máy: đây là bước chỉ chủ dự án làm được — không tạo khoá mới (khoá khác ⇒ không cài đè được).
Sau cài đè kiểm dữ liệu đọc được: `adb shell dumpsys package com.grape.play | grep -E "versionCode|versionName|lastUpdateTime"` và các mục 9.1–9.5.

Quy trình thử trên thiết bị thật có dữ liệu thật (SAO LƯU file nhạc trước; app đặt `allowBackup=false`):
| # | Bước | Mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| 9.1 | Ghi lại: số nguồn NAS, playlist, yêu thích, lịch sử, sửa tag, khoá AI, thiết bị đã xác minh | Danh sách "trước" | | |
| 9.2 | Cài bản mới (release ký đúng) đè lên 3.0.5 | Cài thành công, không gỡ app | | |
| 9.3 | Mở app | Nguồn NAS vẫn kết nối được (mật khẩu đã di trú sang mã hoá), playlist/yêu thích/sửa tag nguyên vẹn | | |
| 9.4 | Khoá AI còn hoạt động; hồ sơ/xác minh thiết bị còn | Đúng như "trước" | | |
| 9.5 | Quét khởi động | Thư viện được kiểm tra, không mất bài | | |
| 9.6 | Nếu NAS/AI báo "cần nhập lại" | Chỉ xảy ra khi khoá bảo mật của máy mất; nhập lại, dữ liệu khác không ảnh hưởng | | |
| 9.7 | Hạ cấp về bản cũ (nếu từng thử) | **Không hỗ trợ**: bản cũ không đọc được mật khẩu/khoá đã mã hoá | | |
Đã kiểm tra trước trên emulator [EMU] bằng build của đúng mã nguồn 3.0.5 (cùng khoá debug): cài đè giữ nguyên dữ liệu và di trú đúng. **Chưa** kiểm bằng khoá release thật.

## 10. Dữ liệu và thư viện lớn (thiết bị thật)
- Thư viện ≥ 20.000 bài: quét đầu, quét lại, tìm kiếm tức thì, cuộn mượt, RAM ổn.
- Rút USB/NAS rồi cắm lại: bài của ổ vắng được giữ, không bị xoá.
- Kill app khi đang phát: hàng đợi/playlist/yêu thích/lịch sử còn nguyên sau khi mở lại.
- Mở menu "Dừng quét" giữa lúc quét: thư viện không thu hẹp.

## 10b. Quy ước bằng chứng
Chỉ ghi **VERIFIED** khi đã thử trên đúng thiết bị thật và có bằng chứng (ảnh/log/ghi âm đo). Mức: [UNIT] test máy tính; [EMU] emulator; [REAL] thiết bị thật. Không có [REAL] thì giữ N/A/UNVERIFIED — kể cả Bit-perfect, Gapless, USB DAC, HDMI, UPnP, Cast.

## 11. Báo cáo kết quả
Nộp bảng đã điền + log (`adb logcat -d -s GRAPEPlay > log.txt`) + ghi rõ thiết bị (hãng, model, firmware, Android). Hạng mục nào chưa làm thì để **N/A**, không suy diễn.
