# GRAPE2Play 2.9.6 — Ghi chú phát hành (chưa biên dịch/kiểm thử trên thiết bị)

**Tích hợp launcher GRAPE2Launcher:** app công bố MediaSession (tên bài, nghệ sĩ, album, thời lượng, ảnh bìa, trạng thái/vị trí, hàng đợi, điều khiển phát/tạm dừng/tiếp/trước/tua), thông báo dạng media, và ContentProvider `content://com.grape.play.library/albums` (≤12 album). Launcher có thể mở thẳng thư viện bằng extra `grape_open=library`. Xem LAUNCHER_INTEGRATION.md (giả định và cách kiểm tra). Bản 2.9.6 gồm cả các thay đổi của 2.9.5 bên dưới (chưa phát hành riêng).

---
# GRAPE2Play 2.9.5 — Ghi chú phát hành (chưa biên dịch/kiểm thử trên thiết bị)

**Bit-perfect chính xác hơn**
- Danh sách tần số/độ sâu bit mà thiết bị công bố, và tần số mixer Android báo, nay chỉ là *gợi ý* (NOT VERIFIED), không còn kết luận NOT BIT-PERFECT. Resampling = Yes chỉ khi có bằng chứng đáng tin; còn lại là Unknown.
- Phân biệt thiết bị bạn chọn với thiết bị Android thực sự định tuyến. Không xác định được → "System Default – Route Unknown" (Android 13+ mới xác định được; app không đoán HDMI/quang/Bluetooth).
- Hiển thị đúng PCM 16-bit / 24-bit / 32-bit / Float theo định dạng AudioSink nhận; không suy ra Float từ cài đặt Hi-res. Định dạng Android/DAC thực tế ghi rõ là không xác định.

**Bit-perfect Device Registry (mới)**
- Khi phát hiện USB/HDMI/S/PDIF/Bluetooth, app đối chiếu danh mục tích hợp (hiện có FiiO K9 Pro ESS theo thông số nhà sản xuất) và hỏi bạn xác nhận; hồ sơ lưu cục bộ, không hỏi lại. Thiết bị lạ có thể tự khai báo (không thành Verified).
- Hai mức tách riêng: trạng thái xác minh của thiết bị và trạng thái Bit-perfect của phiên phát. "VERIFIED – CONFIGURATION" dựa trên cấu hình/thông số, **không phải phép đo toàn tuyến**.
- Quản lý hồ sơ: ⓘ ở trạng thái Bit-perfect → "Quản lý thiết bị Bit-perfect".

**Quay về phiên bản trước:** Cập nhật ▸ "Quay về phiên bản trước" tải bản cũ vào Download/GRAPE2Play. Android không cho cài đè bản có versionCode thấp hơn nên phải gỡ GRAPEPlay hiện tại rồi cài file đó — **gỡ app xóa dữ liệu của app** (nguồn nhạc đã thêm, cài đặt, hồ sơ thiết bị); file nhạc không bị xóa.

Không đổi: Playback.kt, Audio Engine, decoder, DSD/DoP/PCM, DSP/EQ, USB/NAS, UPnP, Cast, buffering. Sleep Timer / Fade Out vẫn đã gỡ.

Giới hạn: xem BITPERFECT.md (mục 7) và TEST_REPORT.md. Build chưa được xác minh; chưa thử trên thiết bị thật.
