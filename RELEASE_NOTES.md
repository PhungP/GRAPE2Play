# GRAPE2Play 3.0.0 — Ghi chú phát hành

**Nguồn xác minh minh bạch:** mỗi cấu hình thiết bị nay hiển thị riêng *nguồn* (Manufacturer Verified / User Verified / Community Verified) và *độ chắc chắn kỹ thuật* (Confirmed / Supported / Inconclusive / Unverified) — hai thứ độc lập, không phải thứ hạng. **Verify Playback Capability**: tự ghi nhận kết quả bạn đã thử (định dạng, điều kiện, phương pháp, firmware) — lưu trên máy, tách khỏi xác nhận danh tính.

**Danh mục mở rộng:** 91 thiết bị / 197 cấu hình từ trang chính thức của hãng (ngày nghiên cứu 2026-11-26). Vẫn chưa đầy đủ; thiết bị không có trong danh mục không có nghĩa là không hỗ trợ Bit-perfect, và có trong danh mục không có nghĩa là đã bit-perfect. Chưa có cấu hình nào đạt Confirmed/mức 3–4.

**Sửa lỗi:** các màn Danh mục/Chi tiết/Thiết bị của tôi không cuộn lên được trên TV/D-pad; mở/đóng chi tiết không còn mất bộ lọc. **Về ứng dụng:** nội dung giới thiệu mới, trung lập.

Không đổi: phát nhạc, Audio Engine, DSP/EQ, UPnP, Cast, USB, TV, ký và cập nhật OTA.

---
# GRAPE2Play 2.9.9 — Ghi chú phát hành (build debug + unit test đạt; chưa kiểm thử trên thiết bị, chưa có APK release ký)

**Bit-perfect Verified Devices (mới):** danh mục 35 thiết bị / 80 cấu hình kết nối đã nghiên cứu từ trang chính thức của nhà sản xuất (ngày nghiên cứu 2026-11-25), dùng offline. Mở từ Đầu ra ▸ "Bit-perfect Verified Devices". Mỗi kết nối được đánh giá riêng, với bốn trạng thái hiển thị khác nhau: *cấu hình bit-perfect đã xác minh*, *khả năng định dạng/giao thức đã xác minh*, *ứng viên*, *không bit-perfect theo cấu hình đã ghi nhận*.

**Trung thực về bằng chứng:** hiện mọi mục chỉ ở mức 1–2 (nhận diện / khả năng theo tài liệu). Chưa có mục nào đạt mức 3–4 vì GRAPE2Play chưa đo dữ liệu tại đầu nhận. Có tên trong danh mục **không** có nghĩa là thiết bị bit-perfect. Danh mục chưa phải toàn bộ thiết bị trên thế giới (xem BITPERFECT.md, phần độ phủ).

**Thiết bị âm thanh của tôi (mới):** đầu ra đang dùng, thiết bị UPnP đã thấy, hồ sơ cục bộ; xác nhận/sửa/xoá danh tính. Xác nhận danh tính không phải bằng chứng bit-perfect.

**Lưu ý hiển thị:** danh mục chưa đầy đủ; thiết bị không có trong danh mục không có nghĩa là không hỗ trợ Bit-perfect.

**Đã bỏ:** dòng dedication ở màn Về ứng dụng; chức năng "Quay về phiên bản trước".

Không đổi: Playback.kt, Audio Engine, DSD/DoP/PCM, DSP/EQ, USB/NAS, UPnP, Cast, TV. Sleep Timer / Fade Out vẫn đã gỡ. Không tạo APK phát hành.

---
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
