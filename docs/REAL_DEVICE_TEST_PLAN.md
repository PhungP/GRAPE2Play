# GRAPE2Play — Kế hoạch kiểm tra trên thiết bị thật

**Trạng thái: TẤT CẢ hạng mục dưới đây là `NOT TESTED`.** Chưa có thiết bị thật nào được dùng. Không hạng mục phần cứng nào được ghi Passed cho tới khi chạy thật và có bằng chứng. Bảng chi tiết từng bước nằm ở `docs/ACCEPTANCE_GUIDE.md` (mục tham chiếu bên dưới); tệp này là danh mục tổng và tiêu chí đạt/không đạt.

Mức bằng chứng: [UNIT] test máy tính · [EMU] emulator · [REAL] thiết bị thật. Chỉ [REAL] mới cho phép ghi Passed/VERIFIED.

## Thiết bị cần có
Android TV (ghi hãng/model/firmware/Android), remote gốc, USB DAC (ghi model), TV/AVR qua HDMI, đường optical (nếu có), NAS SMB/WebDAV, một UPnP/DLNA renderer, một thiết bị Cast, micro (điện thoại/TV có micro) và khoá AudD hợp lệ, máy tính có `adb`.

## Danh mục
| ID | Hạng mục | Cách kiểm | Tiêu chí Passed | Hướng dẫn | Trạng thái |
|---|---|---|---|---|---|
| A1 | Cài mới trên TV | `adb install` APK release | Cài được, biểu tượng/banner hiện ở launcher | §0, §1 | NOT TESTED |
| A2 | Remote/D-pad | Duyệt mọi tab, menu, hộp thoại, bàn phím nhập | Mọi điều khiển tới được và thoát được bằng remote; không kẹt focus; Back hoạt động đúng | §1 | NOT TESTED |
| A3 | Hiệu năng/độ mượt | Cuộn thư viện lớn, mở tab, tìm kiếm | Không giật rõ rệt; ghi `adb shell dumpsys gfxinfo`, RAM `dumpsys meminfo` | §10 | NOT TESTED |
| A4 | Khởi động lại / chạy liên tục | Khởi động lại TV; để phát ≥ 8 giờ | Hàng đợi/playlist/yêu thích còn; không rò RAM tăng liên tục, không crash | §10 | NOT TESTED |
| B1 | Quét thư viện | Quét lần đầu, quét lại, thêm/xoá file, rút/cắm USB/NAS | Không mất bài khi nguồn vắng; "Dừng quét" không thu hẹp thư viện | §10 | NOT TESTED |
| B2 | Metadata | Tag tiếng Việt/Unicode, ảnh bìa, Album Artist/Year, sửa tag | Hiển thị đúng; sửa tag giữ sau khởi động lại | §10 | NOT TESTED |
| B3 | Phục hồi sau sự cố | Kill app khi phát/ghi; ngắt điện TV | Dữ liệu mở lại nguyên vẹn hoặc phục hồi từ bản sao, có thông báo | §10 | NOT TESTED |
| C1 | USB DAC | Cắm/rút khi phát; kiểm sample rate/bit depth bằng công cụ của DAC | Tần số/độ sâu khớp nguồn khi Bit-perfect; rút DAC → phát dừng an toàn | §2 | NOT TESTED |
| C2 | HDMI | Phát PCM/đa kênh tới TV/AVR; đọc định dạng trên AVR | Định dạng AVR hiển thị khớp; không gán nhãn Verified nếu chưa đo | §3 | NOT TESTED |
| C3 | Optical | Phát PCM 16/44.1 tới DAC/AVR optical | Phát được; AVR báo đúng định dạng | §2/§3 (ghi riêng) | NOT TESTED |
| C4 | Định dạng | FLAC, WAV, ALAC, MP3, AAC, OGG, DSF/DFF | Phát được hoặc lỗi rõ ràng | §2.x | NOT TESTED |
| C5 | Pause/resume/seek | Mọi định dạng, kể cả sau khi rút/cắm thiết bị | Vị trí đúng, không click/pop | §1 | NOT TESTED |
| C6 | Gapless | Album liền mạch (live/classical) | Không có khoảng im lặng đo được ở nối bài (ghi âm đường ra) | ghi riêng | NOT TESTED |
| C7 | FLAC hỏng/cụt | Tạo file lỗi theo §7 | Chỉ im lặng ≈1 khung; NOT_BIT_PERFECT + giải thích; không crash | §7 | NOT TESTED |
| C8 | Bit-perfect thật | Đo đường ra bằng DAC/loopback | Chỉ ghi Verified khi đo khớp từng bit | BITPERFECT.md | NOT TESTED |
| D1 | Device Registry | Thiết bị Verified / Not Verified / Needs Verification | Nhãn đúng; thiết bị chưa đo KHÔNG thành Verified | DEVICE_REGISTRY.md | NOT TESTED |
| D2 | Người dùng tự xác nhận | Xác nhận thủ công một thiết bị | Hiển thị là "người dùng xác nhận", khác với Verified do nhà phát hành; lưu sau khởi động lại | §2.x | NOT TESTED |
| E1 | UPnP/DLNA | Phát tới renderer, tắt/bật renderer | Phát được; khôi phục sau khi renderer về | §4 | NOT TESTED |
| E2 | Google Cast | Phát tới thiết bị Cast, ngắt mạng | Phát được; lỗi rõ ràng; nhãn DSD/PCM đúng | §5 | NOT TESTED |
| F1 | AudD | Khoá thật + micro thật; không bấm thì không ghi/gửi | Mọi mục §8 đạt; log/mạng không có hoạt động khi không thao tác; khoá lưu `enc1:` | §8 | NOT TESTED |
| G1 | OTA | Cài đè release ký đúng lên 3.0.5 có dữ liệu | Cùng chứng chỉ; dữ liệu đọc được sau nâng cấp; NAS/AI vẫn hoạt động | §9 | NOT TESTED |
| G2 | Updater trong app | Với bản mới trên GitHub Releases | App phát hiện, tải, mở trình cài đặt | RELEASE_READINESS.md | NOT TESTED |

## Thu thập log và số đo (adb)
```
adb logcat -c; adb logcat -d -s GRAPEPlay > log.txt
adb shell dumpsys meminfo com.grape.play > meminfo.txt
adb shell dumpsys gfxinfo com.grape.play > gfx.txt
adb shell dumpsys media.audio_flinger > audio_flinger.txt      # đường âm thanh thực tế
adb shell dumpsys audio > audio_policy.txt
adb shell dumpsys package com.grape.play | grep -E "versionCode|versionName|lastUpdateTime"
```
Đo thời gian khởi động: `adb shell am start -W -n com.grape.play/.MainActivity` (ghi TotalTime, lạnh và ấm).

## Quy tắc ghi kết quả
Mỗi dòng: ngày, thiết bị, kết quả (Passed / Failed / N/A), bằng chứng (tên tệp log/ảnh). Một hạng mục Failed về mất dữ liệu hoặc crash là **chặn phát hành**.
