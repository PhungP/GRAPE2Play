# GRAPE2Play 3.0.6 — Ghi chú phát hành

Tập trung vào **an toàn dữ liệu**, **quản lý hàng đợi/playlist** và **độ tin cậy giải mã**. Ứng dụng được thiết kế để bảo toàn và di trú dữ liệu từ phiên bản trước, dùng cùng applicationId `com.grape.play` và cùng khoá ký. Việc cài đè bằng APK release ký bằng keystore chính thức **chưa được kiểm chứng**.

## Mới
- **Hàng đợi phát được lưu lại**: mở lại app vẫn còn hàng đợi, bài đang nghe, vị trí, shuffle/repeat.
- **Quản lý hàng đợi và playlist**: thêm/bớt/sắp xếp, menu bài hát (có trong các giao diện Alu và SciFi), lịch sử đã phát, "Mới thêm", các tab duyệt mới, tìm kiếm thư viện và sắp xếp tự nhiên (Track 2 trước Track 10).
- **Nhận diện nhạc môi trường (AudD)**: tuỳ chọn, dùng khoá API của chính bạn; chỉ ghi âm khi bạn bấm nút, không tự gửi gì khi không thao tác.
- Launcher có thể nhảy tới bài trong hàng đợi qua MediaSession.

## An toàn dữ liệu
- Yêu thích, playlist, nguồn NAS, hồ sơ thiết bị và sửa tag được ghi **nguyên tử** kèm bản sao dự phòng; dữ liệu hỏng được phục hồi thay vì bị xoá.
- Quét thư viện không còn làm thu hẹp/xoá thư viện khi nguồn tạm vắng (NAS/USB), có thể dừng giữa chừng; luôn quét khi khởi động.
- Mật khẩu NAS và khoá API (AI, AudD) được **mã hoá bằng Android Keystore**; bản rõ chỉ bị xoá sau khi bản mã hoá đã ghi và đọc lại đúng. Nếu khoá bảo mật của máy bị mất, app báo "cần nhập lại" thay vì mất dữ liệu âm thầm.

## Âm thanh
- FLAC: kiểm **CRC-16** từng khung; khung hỏng thay bằng im lặng cùng độ dài (không phát dữ liệu sai), tìm lại đồng bộ không bị đánh lừa bởi mã giả trong dữ liệu; file cụt/hỏng được báo và không bao giờ được gắn nhãn Bit-perfect.
- Tự tạm dừng khi thiết bị đầu ra đang chọn bị rút.
- Nhãn DSD thống nhất trên mọi đường phát. Không thay đổi đường xử lý âm thanh (không DSP mới).

## Sửa lỗi
- Nhật ký chẩn đoán hệ thống (Home/HDMI/thiết bị âm thanh) không đăng ký được trên Android 14 do thiếu cờ receiver; đã sửa.

## Giới hạn đã biết
- FLAC khối cố định mà STREAMINFO `min != max`: nếu mất khung, không xác định được vị trí mẫu chính xác (theo đặc tả); app báo hư hại thay vì đoán.
- **Mức kiểm chứng:** các chức năng mới và các sửa lỗi trong bản này — hàng đợi/playlist, tự tạm dừng khi rút đầu ra, launcher nhảy bài qua MediaSession, nhận diện AudD, mã hoá Keystore, giải mã FLAC kiểm CRC — mới được kiểm tra bằng unit test (và emulator ở một số hạng mục), **chưa** thử trên thiết bị thật.
- Các hạng mục phần cứng (Android TV và remote, USB DAC, HDMI, optical, Cast, UPnP, gapless, Bit-perfect, AudD với micro thật) **chưa được kiểm trên thiết bị thật**; không hạng mục nào được coi là Passed. Trạng thái "Verified" chỉ áp dụng cho thiết bị đã được đo.
- Không hỗ trợ hạ cấp từ 3.0.6 về bản cũ (bí mật đã mã hoá).

---
# GRAPE2Play 3.0.5 — Ghi chú phát hành

**Xác minh nhanh ngay trong hộp thoại trạng thái:** bấm ⓘ ở màn Đang phát, mục "XÁC MINH NHANH THIẾT BỊ NÀY" ở đầu hộp thoại có nút **① Confirm Device Identity** và **② Verify Playback Capability** (điền sẵn theo bài đang phát) cho đúng thiết bị đang phát — không cần đi tìm ở menu khác. Xác nhận danh tính vẫn là thao tác của bạn, không tự động.

**Dò Cast bằng HTTP (mới):** thiết bị đã thấy qua UPnP mà mDNS Cast không thấy (ví dụ JBL L75ms) được hỏi thẳng qua API HTTP của Cast, nên cả UPnP lẫn Google Cast cùng hiện ổn định hơn. Bản 3.0.4 đã xác nhận trên TV Mibox: JBL L75ms UPnP được tìm thấy.

Không đổi: phát nhạc, Audio Engine, DSP/EQ, ký và cập nhật OTA. Dò Cast bằng HTTP và khối xác minh nhanh chưa được thử trên TV/loa thật.

---
# GRAPE2Play 3.0.4 — Ghi chú phát hành

**Sửa dò loa trên TV (JBL L75ms UPnP):** đọc nhật ký của Mibox cho thấy lượt quét unicast quá chậm trên TV (gửi mất ~9 giây, vượt cửa sổ nghe 4,5 giây) nên phản hồi của loa bị bỏ sót. Nay gửi song song và nghe đủ lâu. App cũng không còn "quên" loa sau một lần quét hụt (giữ trong danh sách nhớ tối đa 8 lần quét liên tiếp và tự hồi sinh khi loa trả lời lại).

Không đổi: phát nhạc, Audio Engine, DSP/EQ, ký và cập nhật OTA. Chưa kiểm thử trên TV sau bản sửa này.

---
# GRAPE2Play 3.0.3 — Ghi chú phát hành

**Ô nhập mặc định đóng:** ô tìm kiếm của danh mục, các ô firmware/giao thức/phần mềm/ghi chú và ô tìm model chỉ mở khi bạn bấm vào — không còn chặn con trỏ khi bạn không định gõ.

**"Xem chi tiết trạng thái phát" cuộn lên được:** dùng khung cuộn chung; nút "← Đóng" ở đầu trang; ▲ cuộn ngược lên đọc toàn bộ nội dung.

**Thiết bị Cast/UPnP và "Manufacturer Verified":** nhãn này chỉ là tài liệu của hãng nêu khả năng, không đổi trạng thái khi phát nhạc (vẫn NOT VERIFIED vì app không quan sát được bên trong thiết bị nhận). Giao diện nay giải thích rõ và form "Verify Playback Capability" tự điền sẵn theo bài đang phát để bạn tự xác minh đúng định dạng.

**Dò loa:** bộ đọc mô tả thiết bị về lại như bản ≤2.9.8 (phần hãng/model đọc riêng); mọi lỗi dò loa được ghi vào Nhật ký, và dưới danh sách ở trang Đầu ra có dòng "Chi tiết" cho biết số phản hồi theo từng card mạng. Nếu loa JBL L75ms UPnP vẫn không hiện, hãy quét một lần rồi gửi dòng "Chi tiết" đó hoặc Nhật ký để tìm nguyên nhân.

Không đổi: phát nhạc, Audio Engine, DSP/EQ, ký và cập nhật OTA. Chưa kiểm thử trên TV/loa thật.

---
# GRAPE2Play 3.0.2 — Ghi chú phát hành

**Sửa lỗi con trỏ D-pad (TV):** mở "Bit-perfect Verified Devices" / "Thiết bị âm thanh của tôi" không còn làm con trỏ nhảy sang menu khác — nguyên nhân gốc là khung hộp thoại điều khiển tiêu điểm của cửa sổ chính thay vì của chính hộp thoại.

**Dò loa JBL L75ms UPnP:** đo thật cho thấy loa không trả lời gói tìm kiếm multicast (nhưng trả lời gói gửi thẳng IP). App nay quét thêm unicast toàn mạng con nên thấy cả UPnP lẫn Google Cast.

**Cast/UPnP và trạng thái Bit-perfect:** app không thể quan sát bên trong loa/thiết bị nhận nên mặc định "NOT VERIFIED". Nay bạn có thể xác nhận thiết bị (mục "Thiết bị âm thanh của tôi" liệt kê cả Google Cast) rồi "Verify Playback Capability"; nếu bạn tự kiểm tra đúng tổ hợp đang phát (thiết bị, kết nối, định dạng, tần số/bit) với phương pháp ít nhất là báo hiệu trên thiết bị, trạng thái sẽ là "VERIFIED – USER-VERIFIED CONFIGURATION" — kết quả của bạn, không phải phép đo của app.

**Chạy ngầm:** chữ xanh "Đang bật chạy ngầm" nay chỉ phản ánh nút Bật/Tắt; quyền miễn tối ưu pin hiển thị riêng.

Không đổi: Audio Engine, DSP/EQ, ký và cập nhật OTA. Chưa kiểm thử trên TV/loa thật.

---
# GRAPE2Play 3.0.1 — Ghi chú phát hành

**Sửa lỗi TV/D-pad:** mở "Bit-perfect Verified Devices", "Thiết bị âm thanh của tôi", Chi tiết thiết bị và form Verify không còn làm con trỏ nhảy ra các menu khác của app.

**Dò loa UPnP chắc hơn:** app nhớ URL mô tả của loa đã thấy và tải lại trực tiếp khi lần quét SSDP không thấy (loa chờ, gói UDP rơi, cổng UPnP đổi sau khi khởi động lại); loa có cả Google Cast và UPnP (ví dụ JBL L75ms) ít bị chỉ hiện Cast hơn. Nếu loa vẫn không hiện, nguyên nhân có thể ở loa/mạng Wi-Fi — chưa loại trừ được.

Không đổi: phát nhạc, Audio Engine, DSP/EQ, ký và cập nhật OTA. Chưa kiểm thử trên TV/loa thật.

---
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
