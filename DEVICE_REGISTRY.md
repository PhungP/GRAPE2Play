# Built-in Device Registry / Device Catalog (2.9.9)

Ngày nghiên cứu: **2026-11-25** · phiên bản dữ liệu: 2.9.9 · schema: 1 · tệp: `app/src/main/assets/device_catalog.tsv` · mã: `DeviceCatalog.java`, giao diện `DeviceCatalogUi.kt`.

## Độ phủ (nói thẳng)
35 thiết bị, 80 cấu hình kết nối (UPnP, USB, HDMI, S/PDIF, Bluetooth, I2S, Roon Ready). Gồm streamer/UPnP (Yamaha WXC-50, JBL L75ms, WiiM Ultra/Mini/Pro/Amp, Cambridge MXN10/CXN V2, LUMIN U2/U2 mini, Aurender N150, Linn Klimax DSM, Matrix element X2), USB DAC (FiiO K9 Pro ESS/K11 R2R/K13 R2R/KA17, Chord Mojo 2/Qutest, RME ADI-2 DAC FS, SMSL D-6/D200, Cambridge DacMagic 200M, Questyle CMA Twelve, Shanling UA5) và AVR/ampli (Denon AVR-X3800H/X1800H, Marantz Cinema 50/Cinema 50 S2/ND8006/PM7000N, Cambridge CXA81/AXR100, Yamaha RX-V6A, Sony STR-AN1000).
**Đây không phải toàn bộ thiết bị trên thế giới.** Chỉ đưa vào model có trang thông số chính thức đã đọc được; hãng/model bị chặn (403/429/JS) bị bỏ qua, không đoán: Topping, iFi, Gustard, Holo, Denafrips, Eversolo, Bluesound, NAD, Naim, Onkyo, Pioneer, Anthem, Arcam, Audiolab, Rotel, HiFi Rose, Pro-Ject, Cocktail Audio, Technics, T+A, Advance Paris, Primare, DCS, AURALiC, Cayin… cần bổ sung ở lần nghiên cứu sau.

## Bốn mức xác minh (mỗi cấu hình một mức)
| Mức | Ý nghĩa | Hiện có |
|---|---|---|
| 1 | Đã nhận diện đúng model | 10 cấu hình |
| 2 | Khả năng định dạng/giao thức được tài liệu hoá (hoặc người dùng báo) | 70 cấu hình |
| 3 | Cấu hình có thể bit-perfect được xác minh (DSP, âm lượng, resampling, DSD đều được biết rõ + bằng chứng) | **0** |
| 4 | Đo dữ liệu thật tại đầu nhận (end-to-end) | **0** |

Nhận diện model ≠ khả năng định dạng ≠ cấu hình bit-perfect ≠ end-to-end. Mức 2 **không** có nghĩa bit-perfect. Gần mức 3 nhất là WiiM Mini quang (WiiM nêu điều kiện Fixed Volume, tắt RoomFit/EQ/Mono) nhưng resampling không được tài liệu hoá nên vẫn mức 2.

## Trạng thái hiển thị (bốn kiểu, không gộp)
`CONFIG_VERIFIED` (cấu hình bit-perfect đã xác minh) · `CAPABILITY_VERIFIED` (khả năng định dạng/giao thức) · `CANDIDATE` (chưa xác minh đầy đủ) · `NOT_BIT_PERFECT` (có bằng chứng khẳng định thay đổi dữ liệu trong cấu hình đã ghi: WiiM Ultra DSD→PCM qua DLNA; Bluetooth codec nén của CXA81). Trạng thái này độc lập với 4 trạng thái phiên phát (VERIFIED/NOT_VERIFIED/NOT_BIT_PERFECT/UNKNOWN) của `BitPerfect.evaluate` — thiếu bằng chứng không bao giờ bị coi là bằng chứng bị đổi.

## Chính sách bằng chứng (`DeviceCatalog.validate`, ép khi nạp)
Mức ≥2 cần nguồn (URL hoặc nguồn người dùng) + ngày bằng chứng; kết quả người dùng thử (`USER_TEST`) tối đa mức 2; `CONFIG_VERIFIED` cần mức ≥3 và DSP/âm lượng/resampling + điều kiện + định dạng được ghi rõ; mức 4 cần phương pháp `MEASUREMENT` có URL; DSD→PCM và Bluetooth không thể `CONFIG_VERIFIED`; `NOT_BIT_PERFECT` cần bằng chứng. Vi phạm → bị hạ và ghi vào `Catalog.problems`, không bị loại.

## Ba thiết bị bắt buộc
- **Yamaha WXC-50 / UPnP:** trang Yamaha liệt kê DSD 5.6 MHz, FLAC/WAV/AIFF 192/24, ALAC 96/24, DLNA. Cách xử lý DSD (native/DoP/PCM) **không được tài liệu hoá** → `UNDOCUMENTED`, không coi là Native DSD.
- **JBL L75ms / UPnP:** *Native DSD xác nhận bằng mconnect* — bằng chứng do người dùng (người duy trì) cung cấp, `USER_TEST`, mức 2, không phải DSD→PCM; chưa tái lập, chưa đo tại DAC. Trang JBL chính thức không đọc được khi nghiên cứu (403/PDF nhị phân) nên không ghi thông số của hãng.
- **WiiM Ultra:** UPnP PCM (mức 2, theo trang spec); UPnP DSD = `DSD_TO_PCM` (người dùng báo; FAQ WiiM cũng nói không hỗ trợ native DSD) → `NOT_BIT_PERFECT`; USB/S-PDIF là cấu hình riêng.

## Dự phòng khi dữ liệu lỗi
`DeviceCatalog.parse` không bao giờ ném lỗi. Thiếu dòng tiêu đề hoặc schema ≠ `DeviceCatalog.SCHEMA` ("1") → danh mục **rỗng** + cảnh báo (không đoán dữ liệu); dòng hỏng/cụt/nhị phân bị bỏ qua và báo; id trùng bị loại; cấu hình mồ côi bị bỏ qua. Nếu không đọc được tệp assets, app hiển thị "Không đọc được danh mục thiết bị" và vẫn chạy bình thường.

## Hiển thị trong app (2.9.9 hoàn thiện)
Cả ba màn (Danh mục, Chi tiết, Thiết bị của tôi) và hộp thoại Bit-perfect đều có thông báo: danh mục chưa đầy đủ; thiết bị không có trong danh mục KHÔNG có nghĩa là không hỗ trợ Bit-perfect; có trong danh mục cũng không có nghĩa là đã bit-perfect. Nhãn mức 2 ghi rõ "khả năng theo tài liệu/báo cáo (chưa phải bit-perfect)".

## Định dạng tệp
TSV UTF-8. Dòng đầu `GRAPE-CATALOG<TAB>schema<TAB>version<TAB>researchDate`. `D`: id, hãng, model, bí danh(|), loại, mô tả, mã sản phẩm, tên UPnP(|), VID:PID(|), ngày xem xét, phiên bản hồ sơ. `C` (sau `D` tương ứng): id, kết nối, giao thức, định dạng(|), PCM tối đa, tần số(|), bit(|), DSD(NATIVE/DOP/DSD_TO_PCM/NONE/UNDOCUMENTED), DSD tối đa, bypass DSP, âm lượng, resampling, mức, trạng thái, phương pháp, điều kiện, giới hạn, nguồn(|), URL(|), ghi chú bằng chứng, ngày bằng chứng, cần xác nhận(Y/N). Id trùng bị loại; dòng hỏng bị bỏ qua và báo. Tương thích tương lai với cập nhật online có kiểm soát: chỉ cần thay tệp cùng schema (chưa triển khai online; không cần mạng/dịch vụ trả phí).

## Nhận diện và hồ sơ cục bộ
Thứ tự: VID:PID → hãng + model (UPnP modelName/mô tả/mã sản phẩm) → bí danh/tên thân thiện (chỉ bằng chứng phụ, luôn xin xác nhận). Tên khác model không bị gộp (token liền kề; WXC-50 ≠ WXC-500). Cùng điểm cho nhiều model → mơ hồ. Người dùng xác nhận → `Profile.catalogId`, `udn`, đúng phương thức kết nối (lưu `DeviceRegistry.Store`). Không bao giờ nâng mức xác minh; thiết bị người dùng thêm không vào danh mục tích hợp.

## Cập nhật dữ liệu
Dữ liệu sinh từ nghiên cứu thủ công; thêm thiết bị = thêm dòng `D`/`C` rồi chạy `./run_core_tests.sh` (phát hiện id trùng, thiếu bằng chứng, mức sai). Mỗi hồ sơ có `lastReviewed` và `profileVersion`.
