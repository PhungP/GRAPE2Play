# Built-in Device Registry / Device Catalog (2.9.9)

Ngày nghiên cứu: **2026-11-26** (đợt đầu 2026-11-25, bổ sung 2026-11-26) · phiên bản dữ liệu: 2.9.10 · schema: 1 · tệp: `app/src/main/assets/device_catalog.tsv` · mã: `DeviceCatalog.java`, giao diện `DeviceCatalogUi.kt`.

## Độ phủ (nói thẳng)
**91 thiết bị, 197 cấu hình kết nối** (USB 60, S/PDIF 60, UPnP 37, HDMI 21, Bluetooth 10, BluOS/I2S/Roon 8, Cast 1), từ 27 hãng: Arcam, AudioQuest, Aurender, Bluesound, Cambridge Audio, Chord, Denon, Eversolo, FiiO, iFi, JBL, LUMIN, Linn, Marantz, Matrix Audio, Naim, Onkyo, Pro-Ject, Questyle, RME, Rotel, SMSL, Shanling, Sony, Technics, WiiM, Yamaha. Chỉ đưa vào model có trang thông số/hướng dẫn chính thức đã đọc được trong đợt nghiên cứu; không suy từ model cùng dòng.
**Đây không phải toàn bộ thiết bị trên thế giới.** Chưa có (trang bị chặn 403/429, JS-rendered hoặc 404 khi nghiên cứu; KHÔNG đoán): Topping, Gustard, Holo Audio, Denafrips, Cayin, NAD, Pioneer, Anthem, Audiolab, HiFi Rose, Cocktail Audio, T+A, AURALiC, Advance Paris, Primare, DCS, nhiều model Sony/Onkyo/Eversolo (DMP) và nhiều sản phẩm của các hãng đã có. Cần bổ sung ở lần nghiên cứu sau.

## Bốn mức xác minh (mỗi cấu hình một mức)
| Mức | Ý nghĩa | Hiện có |
|---|---|---|
| 1 | Đã nhận diện đúng model | 29 cấu hình |
| 2 | Khả năng định dạng/giao thức được tài liệu hoá (hoặc người dùng báo) | 149 cấu hình |
| 3 | Cấu hình có thể bit-perfect được xác minh (DSP, âm lượng, resampling, DSD đều được biết rõ + bằng chứng) | **0** |
| 4 | Đo dữ liệu thật tại đầu nhận (end-to-end) | **0** |

Nhận diện model ≠ khả năng định dạng ≠ cấu hình bit-perfect ≠ end-to-end. Mức 2 **không** có nghĩa bit-perfect. Gần mức 3 nhất là WiiM Mini quang (WiiM nêu điều kiện Fixed Volume, tắt RoomFit/EQ/Mono) nhưng resampling không được tài liệu hoá nên vẫn mức 2.

## Trạng thái hiển thị (bốn kiểu, không gộp)
`CONFIG_VERIFIED` (cấu hình bit-perfect đã xác minh) · `CAPABILITY_VERIFIED` (khả năng định dạng/giao thức) · `CANDIDATE` (chưa xác minh đầy đủ) · `NOT_BIT_PERFECT` (có bằng chứng khẳng định thay đổi dữ liệu trong cấu hình đã ghi: WiiM Ultra DSD→PCM qua DLNA; Bluetooth codec nén của CXA81). Trạng thái này độc lập với 4 trạng thái phiên phát (VERIFIED/NOT_VERIFIED/NOT_BIT_PERFECT/UNKNOWN) của `BitPerfect.evaluate` — thiếu bằng chứng không bao giờ bị coi là bằng chứng bị đổi.

## Chính sách bằng chứng (`DeviceCatalog.validate`, ép khi nạp)
Mức ≥2 cần nguồn (URL hoặc nguồn người dùng) + ngày bằng chứng; kết quả người dùng thử (`USER_TEST`) tối đa mức 2; `CONFIG_VERIFIED` cần mức ≥3 và DSP/âm lượng/resampling + điều kiện + định dạng được ghi rõ; mức 4 cần phương pháp `MEASUREMENT` có URL; DSD→PCM và Bluetooth không thể `CONFIG_VERIFIED`; `NOT_BIT_PERFECT` cần bằng chứng. Vi phạm → bị hạ và ghi vào `Catalog.problems`, không bị loại.

## Nguồn xác minh ≠ Độ chắc chắn kỹ thuật (2.9.10)
Hai thuộc tính **độc lập** trên từng cấu hình (`DeviceVerification.java`, dòng `E` trong tệp):
- **Verification Source:** `Manufacturer Verified` (đối chiếu tài liệu chính thức) · `User Verified` (người dùng tự kiểm tra, lưu cục bộ) · `Community Verified` (nhiều báo cáo độc lập, nhất quán). Ba huy hiệu cùng kích thước và kiểu, thứ tự hiển thị cố định, màu chỉ để phân biệt nguồn; một cấu hình có thể có nhiều nguồn, không nguồn nào ghi đè nguồn khác. Màn chi tiết luôn liệt kê đủ ba nguồn (kể cả "chưa có bằng chứng").
- **Technical Confidence:** `Confirmed` (đủ mạnh cho đúng thiết bị + kết nối + cấu hình) · `Supported` (phù hợp nhưng chưa đủ cho toàn chuỗi) · `Inconclusive` (chưa đủ/không thống nhất) · `Unverified`. Không có quy tắc nào suy độ chắc chắn từ nguồn: Manufacturer có thể chỉ Supported; User có thể Confirmed; Community cần đủ tiêu chí.
- **Danh mục tích hợp:** chỉ có bằng chứng Manufacturer (Supported/Unverified) và hai bằng chứng User do người duy trì cung cấp (JBL L75ms Native DSD qua mconnect; WiiM Ultra DSD→PCM qua DLNA) — hiện **không** có Confirmed hay Community tích hợp. `enforce`: Confirmed bị hạ về Supported nếu cấu hình < mức 3; bằng chứng Community nhúng trực tiếp vào danh mục bị hạ về Unverified.
- **User Verified (Verify Playback Capability):** người dùng chọn định dạng (PCM tần số/bit hoặc Native/DoP/DSD→PCM), điều kiện áp dụng (DSP tắt, âm lượng cố định, không resampling), phương pháp, kết quả, firmware, phần mềm, ghi chú; lưu kèm thời gian. Độ chắc chắn: chỉ nghe ⇒ Inconclusive; báo hiệu trên thiết bị + đủ điều kiện ⇒ Supported; bắt luồng số + so sánh bit + đủ điều kiện + mô tả ≥10 ký tự ⇒ Confirmed; thiếu kết nối/định dạng ⇒ Unverified. **Khác hẳn "Confirm Device Identity"** (chỉ liên kết hãng/model).
- **Community Verified:** `DeviceVerification.aggregate` — mỗi người báo cáo tính một lần (báo cáo mới nhất thay cũ), báo cáo không có bằng chứng bị loại, hai người nộp cùng đoạn bằng chứng chỉ tính một, PASS và FAIL cùng tồn tại ⇒ mâu thuẫn ⇒ Inconclusive; ≥3 độc lập nhất quán ⇒ Supported; ≥5 độc lập, ≥3 bắt luồng số ⇒ Confirmed. Nguồn dữ liệu: `assets/community_reports.tsv` (hiện RỖNG — không có báo cáo cộng đồng nào được phát hành; không bịa).
- **Đối chiếu phiên phát** (`appliesTo`): xác minh chỉ áp dụng khi cùng thiết bị, kết nối, loại định dạng, kiểu DSD, firmware (khác ⇒ không áp dụng, chưa rõ ⇒ một phần), tần số/bit và điều kiện; xác minh USB không đại diện cho UPnP. Hiển thị trong hộp thoại Bit-perfect ("Xác minh cho tổ hợp này") mà không đổi kết luận của `BitPerfect.evaluate`.

## Ba thiết bị bắt buộc
- **Yamaha WXC-50 / UPnP:** trang Yamaha liệt kê (DLNA 1.5, DSD 5.6 MHz, FLAC/WAV/AIFF 192/24, ALAC 96/24, DLNA. Cách xử lý DSD (native/DoP/PCM) **không được tài liệu hoá** → `UNDOCUMENTED`, không coi là Native DSD.
- **JBL L75ms / UPnP:** *Native DSD xác nhận bằng mconnect* — bằng chứng do người dùng (người duy trì) cung cấp (nguồn **User**, độ chắc chắn Supported), mức 2, không phải DSD→PCM; chưa tái lập, chưa đo tại DAC. Tài liệu chính thức (quick-specs PDF, đọc 2026-11-26) ghi Chromecast built-in, AirPlay 2, Bluetooth 4.2, tới 32-bit/192 kHz và **không** nhắc UPnP/DLNA hay DSD — vì vậy không xác nhận cũng không mâu thuẫn với kết quả của người dùng; cấu hình Cast ghi riêng.
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
