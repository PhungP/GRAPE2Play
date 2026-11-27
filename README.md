# GRAPEPlay

Build: mở thư mục bằng Android Studio (Koala+) → Sync → Run / Build APK. Package: `com.grape.play` (đổi tên từ NHOPlay, cài như app mới).

## Bản 0.2.1 — sửa lỗi FLAC
Nguyên nhân: Android 8.0 không có decoder FLAC trong MediaCodec (chỉ có từ Android 8.1/10), nên ExoPlayer đọc được header FLAC nhưng không giải mã được.
Cách sửa: `FlacPcm.java` giải mã FLAC bằng phần mềm (thuần Java, hỗ trợ 8–32 bit, mono → đa kênh, mọi tần số), xuất ra luồng WAV PCM ảo để ExoPlayer phát.
- Áp dụng cho mọi nguồn: bộ nhớ TV, USB, SMB, WebDAV, DLNA.
- Tua được (lưu điểm đặt trong lúc giải mã).
- Đọc thẻ (tiêu đề, nghệ sĩ, album, thể loại, số bài), thông số thật và ảnh bìa nhúng trực tiếp từ header FLAC.
- Đã test bit-exact với ffmpeg: 16/44.1, 24/96, 24/192, mono, 5.1, nén mức 0/5/8, kèm 60 phép tua ngẫu nhiên mỗi file.
- Thư viện được quét lại một lần (đổi tên file cache sang library_v4.json) để nhận thẻ FLAC mới.

Giai đoạn 2 (NAS: SMB, WebDAV, UPnP/DLNA) giữ nguyên.

## Bản 0.2.2
Icon mới: chùm nho cách điệu kèm tam giác play (`drawable/ic_launcher.xml` dạng vector; banner TV `drawable-nodpi/banner.png` 640x360 có chữ GRAPEPlay).

## Bản 0.2.3
Sửa lỗi biên dịch: thiếu khai báo `Playback.outName` (tên thiết bị đầu ra hiển thị ở màn Đang phát).

## Bản 0.3.0 — Giai đoạn 3: Google Drive & OneDrive, sửa lỗi UPnP/DLNA

### Sửa lỗi UPnP/DLNA (WiiM Ultra "cứ báo đang quét")
Nguyên nhân khả dĩ nhất (chưa thử được trên máy thật): bản cũ quét đệ quy *toàn bộ* máy chủ trước khi hiện bài nào, đi cả vào Ảnh/Video và các view trùng lặp (Album/Artist/Genre…), mỗi lần Browse chờ tới 30 giây, một thư mục lỗi làm hỏng cả lần quét.
- Quét không đệ quy, bỏ Ảnh/Video, ưu tiên cây "Browse Folders" nếu có, chống lặp thư mục/bài, giới hạn 12 phút / 5000 thư mục / 30000 bài.
- Một thư mục lỗi chỉ bị bỏ qua và được báo ở cuối; Browse có timeout 20 giây, thử lại, tự hạ số lượng mỗi trang.
- Tiến độ hiện thật: "Đang quét WiiM Ultra: 120 bài · 14 thư mục · Tên thư mục".
- Nút **Dừng quét** (giữ lại những bài đã tìm được).
- **Duyệt trực tiếp**: bấm tên thiết bị DLNA ở thanh nguồn sẽ mở ngay cây thư mục (vào được từng thư mục, phát luôn, không cần quét). Lỗi (nếu có) hiện nguyên văn kèm nút Thử lại — gửi lại dòng lỗi đó nếu vẫn không mở được.

### Google Drive & OneDrive (cá nhân + doanh nghiệp)
Chọn tab **Google Drive** / **OneDrive** trong "Thêm nguồn" → quét mã QR bằng điện thoại → chọn thư mục → app quét và phát (FLAC giải mã bằng phần mềm như các nguồn khác; tua được).
Cần tạo Client ID của chính mày (miễn phí) rồi điền vào `OAuthConfig.kt`:

**OneDrive (Microsoft)** — dùng "device code": nhập mã trên điện thoại.
1. portal.azure.com ▸ Microsoft Entra ID ▸ App registrations ▸ New registration.
2. Supported account types: *Accounts in any organizational directory and personal Microsoft accounts*.
3. Authentication ▸ bật **Allow public client flows = Yes**.
4. API permissions: Microsoft Graph ▸ Delegated ▸ `Files.Read`, `User.Read` (offline_access tự thêm).
5. Copy *Application (client) ID* vào `MS_CLIENT_ID`.
Tài khoản trường/công ty: nếu tổ chức chặn app ngoài, cần quản trị viên cho phép.

**Google Drive** — Google không cho "device flow" đọc file có sẵn trong Drive (chỉ cho quyền `drive.file`), nên app dùng ghép nối qua điện thoại: TV mở trang web nhỏ trong mạng LAN, điện thoại đăng nhập Google, trình duyệt dừng ở địa chỉ `127.0.0.1?code=…` (bình thường), dán địa chỉ đó vào trang của TV. Không cần máy chủ trung gian.
1. console.cloud.google.com ▸ tạo project ▸ APIs & Services ▸ bật **Google Drive API**.
2. OAuth consent screen: loại External, thêm scope `.../auth/drive.readonly`. **Đặt Publishing status = In production** (nếu để Testing, refresh token hết hạn sau 7 ngày). Google sẽ hiện cảnh báo "chưa xác minh" khi đăng nhập: Nâng cao ▸ Tiếp tục (app dùng cá nhân, dưới 100 người thì không cần xác minh).
3. Credentials ▸ Create OAuth client ID ▸ loại **Desktop app**. Copy Client ID và Client secret vào `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.

Lưu ý: refresh token lưu trong SharedPreferences của app (giống mật khẩu NAS hiện tại).

## Bản 0.4.0 — Giai đoạn 4: Stream không dây + sửa giao diện
- **Stream tới WiiM Ultra / thiết bị UPnP (AVTransport) và Google Cast**: mở "Đầu ra âm thanh" ▸ mục STREAM KHÔNG DÂY ▸ "Tìm thiết bị stream" ▸ chọn thiết bị. Bài đang nghe được chuyển sang thiết bị, giữ vị trí; chọn "Phát ra đầu ra của TV" để quay lại.
- TV chạy máy chủ HTTP nhỏ (cổng 8899–8920) phát **file gốc** (không chuyển mã, có Range) cho thiết bị kéo về; nguồn USB/SMB/WebDAV/Drive/OneDrive đều đi qua đó. Bài đã là URL http (DLNA, ổ USB của WiiM) được đưa thẳng cho thiết bị.
- Tự chuyển bài, ngẫu nhiên, lặp lại vẫn do app điều khiển (ExoPlayer chỉ làm bộ sắp thứ tự). Chưa hỗ trợ gapless khi stream; âm lượng chỉnh ở thiết bị nhận.
- Cast: tự cài đặt giao thức CASTV2, dùng Default Media Receiver; Cast thường không phát được DSD/ALAC tuỳ thiết bị.
- Sửa: hàng chọn nguồn hiển thị đủ OneDrive (2 hàng); **độ sâu bit** và bitrate đọc từ header FLAC/WAV; logo con cáo + chùm nho trong app, icon launcher, banner TV; tên app **GRAPE2Play**.
- Chưa biên dịch trong môi trường soạn mã — nếu Gradle báo lỗi, gửi lại log.

## Bản 0.5.0 — Giai đoạn 5 + hoàn thiện
- **Giao diện mới** theo bản thiết kế: thanh điều hướng icon mỏng bên trái (Đang phát · Thư viện · Nguồn nhạc · Đầu ra · Âm thanh), nền tím than, lưới album có nhãn định dạng (FLAC 24/96, DSD64…), bìa tạo sẵn khi file không có ảnh bìa, ô thông số có viền.
- **Tiếng Việt / English**: Cài đặt ▸ NGÔN NGỮ. Chữ cố định và các thông báo thường gặp được dịch; tên bài/nghệ sĩ và thông báo lỗi kỹ thuật giữ nguyên.
- **Nhiều tài khoản đám mây**: thêm bao nhiêu tài khoản Google Drive / OneDrive tuỳ ý (Thêm nguồn ▸ đăng nhập lần nữa bằng tài khoản khác). Mỗi tài khoản là một nguồn riêng, hiện thành một nút lọc trong Thư viện và quét/duyệt độc lập. Đăng nhập lại cùng một tài khoản sẽ cập nhật nguồn cũ. Google luôn hiện màn hình chọn tài khoản.
- **4K**: giao diện co giãn theo màn hình thật (720p / 1080p / 4K) từ canvas 1920×1080; ảnh bìa giải mã tới 720 px.
- **DSD (DSF / DFF)**: giải mã phần mềm → PCM 24-bit (44.1 / 88.2 / 176.4 kHz, chọn ở Cài đặt ▸ PHÁT DSD). Lọc FIR Blackman, giảm mẫu theo bảng tra. Không có DoP / DSD gốc vì Android 8 không hỗ trợ ổn định; DFF nén DST chưa hỗ trợ.
- **EQ 10 băng + cân bằng trái/phải + preamp** (Âm thanh). Khi EQ phẳng và cân bằng ở giữa, âm thanh đi thẳng không xử lý. Điều khiển bằng remote: ◂ ▸ chọn băng, bấm OK rồi ▴ ▾ chỉnh.
- Chọn **đầu ra quang / analogue / HDMI**: Đầu ra âm thanh (đã có từ 0.2). Cổng quang S/PDIF thường chỉ nhận ≤ 48 kHz — nếu mất tiếng hãy chọn DSD 44.1 kHz và chế độ PCM Tương thích.
- **Về ứng dụng** (Cài đặt ▸ Về ứng dụng).
- Microsoft Application (client) ID đã điền sẵn trong OAuthConfig.kt. Trong Azure cần bật “Allow public client flows” và loại tài khoản “cá nhân + cơ quan/trường học”; quyền Files.Read, User.Read, offline_access.
- Chưa có: gapless, ReplayGain, DoP. Chưa biên dịch trong môi trường soạn mã — nếu Gradle báo lỗi, gửi lại log.

## Bản 0.5.1
- Cài đặt ▸ HIỂN THỊ: Tự động / FHD (chữ to hơn, mặc định cho TV Full HD) / 4K (gọn, nhiều nội dung).
- Màn Đang phát: dòng tín hiệu có nhãn BIT-PERFECT — xanh lá = đạt (lossless, không EQ, không hạ bit), vàng = gần đạt (EQ bật, hạ 24→16-bit, DSD→PCM, chưa rõ codec), đỏ = nén mất dữ liệu (MP3/AAC/OGG/Opus). Chỉ đánh giá phần app kiểm soát; TV/hệ điều hành vẫn có thể đổi tần số lấy mẫu ở tầng dưới.
- About: thêm thông tin nhà phát triển và liên hệ phankimphung@gmail.com.

## Bản 0.5.2
- FHD: canvas 1560×878 (chữ to hơn ~23% so với 1080p gốc); ô thông số bài hát cho phép 2 dòng, không còn bị cắt "Stereo 2…" / "Google…".
- About: bố cục 2 cột, đủ lời giới thiệu, nhà phát triển Phan Kim Phụng và email.
- Dấu "Phiên bản giới hạn" (chữ serif nghiêng vàng chuyển sắc) ở góc dưới thanh điều hướng trái.

## Bản 0.5.3
- Đang phát: tên bài 44sp (hiện đủ 2 dòng), nút điều khiển to hơn (Play 124, các nút 96), 6 ô thông số cùng kích thước (Nguồn chỉ hiện tên dịch vụ), danh sách phát chữ nhỏ hơn nên thấy được nhiều bài hơn.

## Bản 0.5.4
- Sửa hộp thoại đăng nhập OneDrive/Google: mã đăng nhập đưa lên đầu, QR nhỏ lại, hộp thoại Thêm nguồn cuộn được nên không bị cắt trên TV FHD.

## Bản 0.6.0
- **Yêu thích**: bấm ♥ ở màn Đang phát, hoặc giữ nút OK (hay nút Menu) trên bất kỳ bài nào trong danh sách để thêm/bỏ. Tab **Yêu thích** trong Thư viện: phát tuần tự / ngẫu nhiên, bài mới thêm lên đầu. Bài yêu thích có icon ♥ trong mọi danh sách.
- **Đầu ra mặc định khi mở app**: Đầu ra ▸ “ĐẦU RA MẶC ĐỊNH KHI MỞ APP” (giữ lần trước / mặc định hệ thống / một thiết bị cụ thể). Thiết bị chưa cắm thì tạm dùng mặc định và tự chuyển khi cắm (USB DAC, HDMI…).
- **Chạy ngầm**: Android không cho xin quyền ngay lúc cài, nên app hỏi ngay lần mở đầu tiên (miễn tối ưu pin + thông báo). Có dịch vụ nền (foreground, mediaPlayback) + wake lock để nhạc không bị ngắt khi thoát ra màn hình chính. Xin lại ở Cài đặt ▸ Chạy ngầm.
- **Trang chủ TV**: Cài đặt ▸ Trang chủ TV ▸ Bật → hàng “GRAPEPlay” (yêu thích + phát gần đây, có ảnh bìa); bấm bài là phát luôn. Có thể phải bật kênh trong “Tuỳ chỉnh kênh” của màn hình chính. Cần thư viện tvprovider và provider ảnh bìa `com.grape.play.covers`.
- **Cảnh báo méo tiếng khi chỉnh EQ**: khung cảnh báo trong bảng EQ (xám/vàng/đỏ; đỏ khi đỉnh tăng ròng > 0 dB kèm gợi ý mức Preamp) và dòng cảnh báo ở màn Đang phát.
- **Giao diện**: Cài đặt ▸ Giao diện: Nho tím (mặc định), Xanh đêm, Rừng xanh, Hoàng hôn, Đen OLED, Sáng ngà. Thêm giao diện mới: thêm một `Palette` vào `Themes.ALL` (Themes.kt).
- Chưa biên dịch trong môi trường soạn mã — nếu Gradle báo lỗi, gửi lại log.

## Bản 0.6.1 — 5 bố cục màn Đang phát (theo 5 concept thiết kế)
Cài đặt ▸ Bố cục màn Đang phát: Mặc định · Tối giản (Purist) · Đĩa than (Vinyl) · Dữ liệu (Roon) · Tranh (Gallery) · Rạp tối (Noir Theatre). Toàn màn hình; Back = về Thư viện, OK = phát/dừng, ◂ ▸ = bài trước/sau (Rạp tối: tua ±10 giây), Menu = yêu thích; Tranh: ▴ đổi khung (trắng / gỗ / không viền), thông tin tự ẩn sau 5 giây. Code: NowStyles.kt. Chưa có: lời bài hát đồng bộ, credits, DR (cần dữ liệu ngoài).

## Bản 0.6.2
- **Chạy ngầm**: nút Bật/Tắt ở Cài đặt ▸ Chạy ngầm có hiệu lực ngay (giữ dịch vụ nền luôn chạy, trạng thái sáng "✓ Đang bật chạy ngầm"); xin thêm quyền thông báo / miễn tối ưu pin nếu TV có màn hình đó, nếu không thì báo rõ.
- Huy hiệu **Limited Edition** giữ nguyên tiếng Anh ở cả tiếng Việt và English; menu "Âm thanh" đổi thành **Cài đặt**.
- **Ảnh bìa**: lấy từ DLNA, ảnh nhúng trong file hoặc cover/folder/front.jpg|png cùng thư mục. Từ bản sau 3.0.6, app **không còn tự động tải ảnh bìa từ mạng**; chỉ khi bạn bấm "Chọn ảnh bìa bằng AI" mới tìm, AI chọn và bạn xem trước, đồng ý thì ảnh mới được lưu (chỉ trong app).

## Bản 0.6.3
- Nút **Thoát** ở góc dưới bên trái thanh điều hướng (có hộp thoại xác nhận): dừng nhạc, tắt dịch vụ nền / chạy ngầm, đóng app.
- Nút Cài đặt đổi sang biểu tượng **bánh răng**.
- Nút **Phổ tần**: hiện phổ tần số cố định của cả bài đang phát như ảnh Spek (trục x thời gian, trục y 0 → Nyquist, màu = cường độ dBFS, nhạc stereo vẽ kênh L trên / R dưới). Giải mã toàn bộ bài (FLAC, WAV, MP3/AAC/M4A/OGG/Opus qua MediaCodec) rồi FFT 2048 điểm, vẽ dần trong lúc phân tích; kết quả được nhớ cho 3 bài gần nhất. File DSD hiện thông báo lý do không có phổ. Code: `Spectrogram.kt`, `SpectrumDialog.kt`.

## Bản 0.6.4
- Phổ tần chuyển từ dạng thời gian thực sang spectrogram tĩnh kiểu Spek (bỏ bộ ghi mẫu trong `LevelProcessor`).
- Hộp thoại "Về ứng dụng" thiết kế lại gọn hơn: cột thương hiệu bên trái, nội dung và thẻ thông tin nhà phát triển bên phải, chữ nhỏ hơn.

## Bản 0.7.0 — chạy trên điện thoại và máy tính bảng
- Một APK chạy cả TV, điện thoại và máy tính bảng Android 8.0+ (không còn bắt buộc leanback, không khoá xoay ngang). App tự nhận diện: TV giữ nguyên giao diện điều khiển bằng remote; điện thoại / máy tính bảng dùng giao diện cảm ứng mới (`MobileApp.kt`).
- Giao diện cảm ứng: thanh điều hướng dưới (dọc) hoặc bên trái (ngang), mini-player, Thư viện (lọc nguồn, Album/Nghệ sĩ/Thể loại/Thư mục/Bài hát/Yêu thích), Đang phát (bìa, thanh tua kéo được, phát/dừng/trước/sau, ngẫu nhiên, lặp, yêu thích, đầu ra, phổ tần, danh sách phát), Cài đặt (giao diện màu, DSD, EQ 10 băng bằng thanh trượt, chạy ngầm, ảnh bìa mạng, ngôn ngữ, About, Thoát).
- Các hộp thoại (thêm nguồn, đầu ra, phổ tần, About, chạy ngầm, thoát) tự co theo màn hình và xếp dọc trên điện thoại.
- Android 13+: xin quyền READ_MEDIA_AUDIO thay cho READ_EXTERNAL_STORAGE.

## Bản 0.7.1
- Phổ tần: dùng bảng màu như Spek (tím/đen → xanh dương → xanh lơ → xanh lá → vàng → cam → đỏ), thang mặc định -20 → -120 dB (chọn thêm 0 → -120 dB).
- Hai kênh L/R được gộp thành một bảng duy nhất (trung bình công suất), thay vì hai bảng xếp chồng.

## Bản 0.7.2
- Biểu đồ phổ bố cục y hệt Spek: tiêu đề codec nằm trong khung và cắt "…" khi dài ("FLAC (Free Lossless Audio Codec), 44100 Hz, 16 bits, 2 channels"), trục tần số "0 / 5 / 10 / 15 / 20 kHz" kèm mép trên bằng Nyquist ("22 kHz"), trục thời gian có mốc cuối bài ("4:45"), khung viền trắng, thang màu "-20 dB … -120 dB" bên phải.

## Bản 0.7.3
- **Phổ tần**: khung phổ tự co vừa hộp thoại (TV không còn cuộn), nên luôn thấy tiêu đề, trục tần số tới mép trên **Nyquist (22 kHz với file 44,1 kHz)**, trục thời gian và nút điều khiển; không còn mất đoạn trên 20 kHz.
- **Đăng nhập OneDrive/Google**: mã đăng nhập hiển thị đầy đủ ở hàng riêng (chữ lớn, không bị "…"); nhãn đổi thành "Mã của bạn".

## Bản 0.7.4
- Sửa nhãn tín hiệu khi phát tới thiết bị UPnP: DSD được gửi nguyên file DSF/DFF (không chuyển PCM trên TV) nên hiển thị "DSD gốc, không chuyển mã"; chuỗi tín hiệu không còn ghi "→ PCM" / "→ EQ" trong chế độ này.

## Bản 0.7.5
- **DoP khi phát tới thiết bị UPnP** (Cài đặt → Phát DSD → "Khi phát tới thiết bị UPnP"): bài DSD64 (DSF/DFF, ≤ 2 kênh) được gói nguyên bit thành WAV PCM 24-bit/176,4 kHz theo chuẩn DoP 1.1 (nhãn 0x05/0xFA luân phiên) rồi gửi cho thiết bị, để WiiM chuyển tiếp cho DAC ngoài hỗ trợ DoP. Mặc định tắt (vẫn gửi file DSD gốc). Cần tắt EQ, âm lượng thiết bị 100%. DSD128 trở lên và Google Cast vẫn gửi file gốc.

## Bản 0.8.0
- **Điện thoại**: thanh điều hướng Thư viện · Đang phát · Cài đặt chuyển lên đầu màn hình (dạng 3 phân đoạn), tránh bấm nhầm với thanh điều hướng hệ thống; ô điều khiển ở màn Đang phát không còn bị che.
- **Bố cục Đang phát**: bỏ 5 bố cục cũ (Tối giản, Đĩa than, Dữ liệu, Tranh, Rạp tối). Giữ **Mặc định** và thêm 3 bố cục kiểu Symfonium dùng chung cho điện thoại, máy tính bảng và TV: **Cổ điển**, **Toàn ảnh**, **Nền màu** (nền chuyển sắc theo màu ảnh bìa). Chọn ở Cài đặt ▸ Bố cục màn Đang phát (lựa chọn cũ được đặt lại về Mặc định).
- **Nguồn nhạc** và **Đầu ra âm thanh** thành trang riêng (không còn hộp thoại): danh sách thẻ bên trái, bảng chi tiết bên phải; thêm bộ thành phần giao diện dùng chung (`Ui.kt`: PageHeader, OptionCard, DetailPanel, nút chính/phụ, thẻ nhãn, dòng thông số…). Đăng nhập Google Drive/OneDrive có chip các bước, mã QR và mã ghép nối cỡ lớn. Thanh bên trái (rail) dùng biểu tượng đường nét mảnh, gọn hơn.

## Bản 0.8.1 — Cập nhật ứng dụng + tách khoá bí mật khỏi mã nguồn

### Khoá đăng nhập đám mây (đổi chỗ)
`OAuthConfig.kt` không còn chứa Client ID / Secret. Điền vào `local.properties` (xem `local.properties.example`):
```
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-...
```
Gradle đọc file này (hoặc biến môi trường cùng tên) và sinh `BuildConfig`. `local.properties` đã nằm trong `.gitignore`, nên không bị GitHub chặn (Push Protection) khi push. Mọi chỗ trong README cũ nói "điền vào OAuthConfig.kt" nay hiểu là "điền vào local.properties".

### Cập nhật ứng dụng (Cài đặt ▸ CẬP NHẬT ỨNG DỤNG)
Nguồn: GitHub Releases của `PhungP/GRAPE2Play` (repo phải **public**). App tự kiểm tra một lần khi mở Cài đặt; có nút "Kiểm tra cập nhật". Khi có bản mới: "Tải và cài đặt" → tải APK vào bộ nhớ đệm, kiểm tra đúng package `com.grape.play`, rồi mở trình cài đặt của Android. Lần đầu Android sẽ hỏi cho phép cài từ nguồn không xác định.

Phát hành một bản mới:
1. Tăng `versionCode` và `versionName` trong `app/build.gradle.kts` (ví dụ 23 / "0.8.2").
2. Build APK release, **ký bằng cùng một keystore** với các bản trước (khác khoá thì Android từ chối cài đè).
3. Trên GitHub: Releases ▸ Draft a new release ▸ tag `v0.8.2` (phải trùng versionName, có thể có tiền tố `v`) ▸ đính kèm đúng một file `.apk` ▸ Publish. Phần mô tả release hiện 4 dòng đầu trong hộp cập nhật.

Lưu ý: bản 0.8.1 phải cài thủ công một lần; từ đó trở đi các bản sau tự cập nhật được.

## Bản 0.8.2 — Dò loa UPnP kỹ hơn (JBL L75ms…) và phát DSD qua PCM 24-bit

### Dò thiết bị UPnP
- Gửi SSDP M-SEARCH ra **từng card mạng** (Wi-Fi/Ethernet) với 4 thẻ tìm kiếm (`MediaRenderer:1`, `AVTransport:1`, `upnp:rootdevice`, `ssdp:all`), lặp 3 lần.
- Loa nào Google Cast đã thấy nhưng SSDP đa hướng chưa thấy thì app **hỏi thẳng IP đó** (unicast, cổng 1900).
- Dưới danh sách ở trang "Đầu ra âm thanh" có dòng ghi chú lần quét (số phản hồi SSDP, số thiết bị UPnP, số Cast) để chẩn đoán khi không thấy loa.
- Một loa có cả UPnP lẫn Google Cast sẽ hiện hai mục; chọn mục **UPnP** để phát DSD (Cast không hỗ trợ DSD).

### Phát DSD tới thiết bị UPnP — 3 chế độ (Cài đặt ▸ PHÁT DSD)
1. **Gửi file DSD gốc** (mặc định): thiết bị tự xử lý. Nếu thiết bị từ chối / không phát trong ~15 giây, app **tự chuyển sang PCM 24-bit** cho bài đó và các bài sau cho tới khi đổi thiết bị.
2. **DoP** (chỉ DSD64): dành cho DAC ngoài hỗ trợ DoP.
3. **PCM 24-bit**: app giải mã DSF/DFF thành WAV 24-bit (tốc độ theo mục "Chuyển DSD sang PCM", nên chọn 176.4 kHz) rồi gửi qua HTTP, tua được. Phù hợp JBL L75ms (hãng ghi "DSD (chuyển PCM)", PCM tới 192 kHz).

## Bản 0.8.3

- **Phát DSD tới thiết bị UPnP (JBL L75ms…)**: bỏ chế độ "PCM 24-bit" (còn "Gửi file DSD gốc" và "DoP"). Trước khi nạp bài mới app đưa thiết bị về trạng thái nghỉ (Stop, chờ STOPPED), chờ hết TRANSITIONING rồi mới Play và tự thử lại khi thiết bị báo bận (701/705). Đổi chế độ khi đang phát thì bài hiện tại nạp lại ngay, giữ nguyên vị trí; đang tạm dừng thì nạp lại ở lần bấm Phát kế tiếp. Bấm Phát sau khi thiết bị báo lỗi sẽ nạp lại cả bài (không chỉ gửi lệnh Play) nên không còn kẹt "không phát được" sau khi đổi chế độ.
- **Cài đặt**: bỏ kiểu "Toàn ảnh"; thêm mục **Chống lưu ảnh màn hình OLED** (bật/tắt: dịch nhẹ giao diện mỗi phút + tự giảm sáng sau 5 phút không thao tác); trang Về ứng dụng chữ to hơn, thay email bằng link GitHub, nút "Đóng" ở giữa phía dưới.
- **Đang phát (4K)**: canvas 4K nhỏ lại 1760 → 1640 dp (toàn giao diện to hơn ~7%); riêng màn Đang phát được phóng đúng tỷ lệ bản FHD (ảnh bìa lớn, khoảng trống giữa hộp tín hiệu và sóng âm gọn lại); hộp tín hiệu có viền/khối giống các hộp Định dạng, Độ sâu bit…
- **Thư viện**: mục Thư mục hiển thị dạng ô thư mục có ảnh bìa (nếu có); nút **⚙ Nguồn hiển thị** để hiện/ẩn từng nguồn (nguồn ẩn không lẫn vào "Tất cả"). Có trên cả TV và điện thoại.
- **Phổ tần**: tiêu đề nhỏ lại (30 → 24) cho đồng bộ với các trang cài đặt.

## Bản 0.8.4

- Cập nhật ứng dụng: chịu được tag có ký tự lạ ở đầu (`v.0.8.3`, `V0.8.3`…); mọi ký tự không phải số ở đầu tag đều được bỏ trước khi so sánh phiên bản. Thẻ cập nhật hiện thêm dòng "Bản mới nhất trên GitHub" và báo rõ khi GitHub chưa có Release hợp lệ.
- Khuyến nghị đặt tag đúng dạng `v0.8.4` (không có dấu chấm sau chữ v).

## Bản 0.8.5
- Quét đầu ra: không phụ thuộc DSD gốc/DoP. Google Cast tìm bằng NsdManager + mDNS tự viết (hợp hai kết quả), UPnP bằng SSDP trên mọi card mạng + hỏi thẳng IP; tự quét lượt 2 nếu thiếu thiết bị đã biết; nhớ thiết bị từng thấy và kiểm tra bằng kết nối TCP nên loa bận/rơi gói UDP không biến mất.
- Chống lưu ảnh: thu nhỏ nhẹ thay vì phóng to, nên khi dịch hình menu không bị đẩy sát viền.
- Cửa sổ Ứng dụng, Phổ tần số, Thoát thu nhỏ (~1/4 màn hình), chữ giảm đồng bộ.
- "Quét lại" nằm dưới "Cài đặt", "Thoát" ở cuối thanh trái; cả hai cùng cỡ với các nút khác.

## Bản 0.8.6
- Cửa sổ "Về ứng dụng" và "Nguồn hiển thị" thu nhỏ còn ~1/4 màn hình (kích thước theo tỉ lệ màn hình); sửa chữ "Nhà phát triển" bị cắt.
- Các trang cuộn (Đầu ra, Nguồn nhạc, Cài đặt) có dải cuối nhận tiêu điểm D-pad để cuộn hết, không mất chữ ở mép dưới.
- Phát UPnP (Bravia…): TV tự phục vụ ảnh bìa (`/a/…jpg`) và gửi `albumArtURI` + DLNA protocolInfo; nếu TV từ chối metadata thì thử lại không metadata; chọn đúng IP Wi-Fi/LAN trên điện thoại (tránh card 4G).
- Điều khiển TV từ điện thoại: TV mở máy chủ điều khiển (PIN 6 số ở Cài đặt ▸ Điều khiển từ điện thoại); điện thoại bấm biểu tượng TV ở Thư viện để dò TV, nhập PIN, điều khiển phát/tạm dừng/bài trước-sau/tua/xáo/lặp và tìm bài trong thư viện TV để phát.
- Cửa sổ thông báo có bản cập nhật mới (tự hiện sau khi mở app): Cập nhật ngay / Để sau.

## Bản 0.8.7
- Bỏ tính năng điều khiển app trên TV bằng điện thoại.
- Cài đặt: thêm lựa chọn Gapless (bộ đệm nạp trước 90 giây để nối bài liền mạch khi phát trên chính TV/máy).
- Đầu ra âm thanh: rê con trỏ tới thiết bị nào thì khung bên phải hiện thông tin thiết bị đó; bấm vào thiết bị hiện hộp thoại Chọn / Huỷ; bỏ nút "Dùng đầu ra này".
- Đo thời gian chuyển bài (từ lúc bấm đến lúc có tiếng); quá 3 giây hiện thông báo "Tốc độ đường truyền không ổn định".
- Về ứng dụng: nhãn "Nhà phát triển" xếp dọc, chữ nhỏ và mảnh hơn trên điện thoại để hiện đủ chữ.

## Bản 0.8.8
- Phát tới thiết bị từ xa (UPnP/Cast): app giữ khoá thức CPU + Wi-Fi hiệu suất cao khi đang phát, tránh bị hệ thống làm chậm/ngắt khi tắt màn hình (không cứu được TV đã vào standby thật).
- UPnP gapless: nạp sẵn bài kế tiếp bằng SetNextAVTransportURI; thiết bị hỗ trợ sẽ tự qua bài, app nhận biết (TrackURI đổi) và cập nhật hiển thị. Thiết bị không hỗ trợ tự quay về cách cũ (app nạp bài khi hết bài).
- Cửa sổ "Đã cập nhật lên bản X" hiện một lần sau khi cài bản mới, kèm ghi chú phát hành nếu có.

## Bản 0.8.9
- UPnP gapless: thêm cách nhận biết dự phòng khi loa tự qua bài nhưng viết lại URL (bài trước gần hết, vị trí về gần 0 và đang phát) để app không nạp lặp lại bài.

## Bản 0.9.0
- **Màn Đang phát mới** (giao diện mặc định): ảnh bìa lớn, đáy thẳng hàng hộp "Đường tín hiệu"; dải sóng âm thấp (46 dp); phát trên TV có 2 đồng hồ VU kim (-40…+3 dB); phát tới UPnP/Cast có hộp "Thiết bị nhận" và "Kết nối & phát liên tục" (độ trễ, trạng thái, bài kế đã nạp sẵn, lần chuyển bài gần nhất, số lần mất kết nối).
- **Nhật ký lỗi** (Cài đặt → Nhật ký lỗi): ghi lỗi phát nhạc, loa/NAS, cập nhật; bắt cả lỗi làm app dừng. Điện thoại: nút Gửi (email/chia sẻ). TV: hiện địa chỉ `http://IP:cổng/log` để mở bằng trình duyệt máy khác. Đã lọc mật khẩu/token trong URL.
- **Thông báo bản mới trên TV** kèm danh sách tính năng (lấy từ ghi chú Release trên GitHub); tự thử lại 5 s/20 s/60 s nếu lúc mở app mạng chưa sẵn sàng. Bỏ thông báo "đã cập nhật".
- **Sửa kẹt khi phát DSD ra đầu ra không tương thích** (cổng quang, TV): tự hạ tốc độ DSD→PCM cho riêng đầu ra đó (176,4 → 88,2 → 44,1 kHz) rồi phát lại; sau lỗi, chọn bài/thư mục khác, bấm Phát hoặc đổi đầu ra đều nạp lại bộ phát ngay và xoá thông báo lỗi cũ.
- **Nhiều máy cùng một loa UPnP**: hỏi trạng thái thưa hơn và lệch pha, giãn dần khi loa chậm, timeout ngắn; phát hiện máy khác giành quyền (URL lạ 3 lần liên tiếp) thì ngừng gửi lệnh, không tự chuyển bài, báo rõ và chờ bấm Phát để giành lại.
- Dọn mã: gom 3 đoạn trùng lặp trong Playback (`startProbe`), bỏ hàm `Vu` không dùng, bỏ `UpdatedDialog`.

## Bản 0.9.1
- Hai hộp "Thiết bị nhận" / "Kết nối & phát liên tục" cao 150 dp, hiện đủ thông tin (hàng nút điều khiển thấp lại 108 dp để vừa).
- Chế độ hiển thị "Tự động" đọc độ phân giải thật của màn hình (Display.Mode) nên Mi Box xuất 4K nhưng giao diện hệ thống 1080p vẫn nhận ra 4K.
- Email nhận nhật ký: grape2play@gmail.com.

## Bản 0.9.2
- Sửa đồng hồ VU đứng yên (-∞ dB) khi phát file 24-bit (FLAC 24-bit, DSD→PCM) ra cổng quang / chế độ Tương thích 16-bit: đo mức tại cửa vào bộ xuất âm thanh (ForwardingAudioSink) thay vì AudioProcessor.
- Dòng "Đường tín hiệu" giờ luôn có định dạng PCM thật; file 24-bit hạ về 16-bit sẽ hiện mức vàng "GẦN BIT-PERFECT · hạ 24→16-bit" thay vì xanh sai.

## Bản 1.0.0 (trước đó là 0.10.0)
- Màn Đang phát: ảnh bìa và hộp thông số chia đôi bằng nhau, đường giữa trùng với đường giữa hai hộp VU / hai hộp Thiết bị nhận–Kết nối; nút Phát/Dừng nằm đúng trên đường giữa đó (hai bên mỗi bên hai nút; nút Yêu thích chuyển lên góc hộp thông số).
- Hộp "Thiết bị nhận": thêm thanh âm lượng loa (RenderingControl GetVolume, hỏi ~4 giây/lần; loa không hỗ trợ thì ẩn).
- Hộp "Kết nối & phát liên tục": thanh đệm của loa (giây nhạc TV đã gửi trước, ước tính), phản hồi/mất kết nối/trạng thái/bài kế, đồ thị tốc độ TV gửi nhạc (kbit/s). Ẩn phần đo khi loa kéo thẳng từ nguồn hoặc dùng Cast.
- Giao diện mới "Nhôm phay xước" (Cài đặt → Bố cục màn Đang phát): tấm nhôm vân xước, ô đen, đồng hồ VU nền vàng kim đen, nút Phát đen viền vàng, bốn con ốc. Điện thoại dùng bố cục mặc định.

## Bản 1.0.1 — giao diện Nhôm phay xước hoàn chỉnh
- Mặt nhôm bao trọn màn hình (4 ốc ở góc); menu trái là màn hình đen lõm chỉ gồm Đang phát → Quét lại.
- Logo cáo–chùm nho và huy hiệu Limited Edition khắc laser chìm trên mặt nhôm.
- Nút xáo trộn / lùi / Play-Pause / tới / lặp là nút nhôm tròn nổi khối (đèn vàng khi bật); nút Thoát thành nút Bật/Tắt nổi khối.
- Danh sách phát, ảnh bìa, thông tin, VU, sóng nhạc là màn hình đen lõm trong khối nhôm.

## Bản 1.0.2 — Nhôm phay xước: kính hổ phách
- Cửa sổ VU liền khối kiểu máy thật (một tấm kính hổ phách chứa hai đồng hồ L/R, thang dB, vạch đỏ, "PEAK POWER LEVEL", kim đỏ).
- Menu, thông tin bài, hộp tín hiệu, sóng nhạc, hộp thiết bị nhận và danh sách phát cùng tông kính hổ phách, chữ nâu đậm.
- Nút nhôm đang kích hoạt sáng viền vàng + quầng sáng + đèn LED như phím cơ có đèn.

## Bản 1.0.3 — Nhôm phay xước: điện thoại + sửa lỗi TV
- Điện thoại: giao diện Nhôm phay xước riêng (nav 5 mục đặt trên cùng, ảnh bìa, VU kính hổ phách, sóng nhạc chạm để tua, nút nhôm có đèn); không hiện thông tin chỉ của TV (cổng quang/HDMI/mặc định hệ thống). Các trang khác nằm trong màn hình đen lõm của khối nhôm.
- TV: mục menu/bài hát khi trỏ tới chuyển nền tối chữ vàng sáng (đọc được); nút Play thu nhỏ (80dp) không chạm khung sóng nhạc; nút cơ vát cạnh thật hơn, bỏ quầng sáng — chỉ còn chấm vàng (bật) / chấm đen (tắt).

## Bản 1.0.4 — Nhôm phay xước TV: căn chỉnh & mặt nhôm mới
- Mép trên Menu / Ảnh bìa / Thông số / Danh sách phát thẳng hàng; Menu cao bằng màn Đang phát; mép dưới Danh sách = mép dưới sóng nhạc; mọi khe giữa các màn hình bằng nhau (16dp).
- Logo cáo–nho chuyển xuống trên Limited Edition; bỏ chữ "Đang phát"; chữ "Đầu ra · …" cam sáng trên dải nhôm.
- Thiết bị nhận + Kết nối gộp thành một cửa sổ kính liền khối như VU, hiển thị đủ chữ (xuống dòng thay vì cắt).
- Mặt nhôm phay xước mới kiểu champagne hi-end: vệt xước ngang ngẫu nhiên, dải phản chiếu, mép vát.

## Bản 1.0.5
- TV: khung giao diện Đang phát tự co/giãn đều để luôn vừa trọn màn hình (sửa lỗi bị to hơn màn hình, mất mép/ốc); lề an toàn rộng hơn.
- Nhôm: ảnh bìa + thông số + VU/thiết bị nhận gộp thành một màn hình, ngăn bằng vạch mực đen; chữ "Đầu ra" về góc trên phải, màu đen; logo cáo-nho nằm giữa menu và huy hiệu; Limited Edition màu vàng gold.
- Điện thoại: đầu ra hiển thị "Loa điện thoại" (không còn "Loa TV"), ẩn mục thiết bị điện thoại (telephony), luôn có lựa chọn Bluetooth; bỏ ghi chú cổng quang trên điện thoại.

## Bản 1.1.0 — Nhôm phay xước: màu kính + chất liệu
- Cài đặt có thẻ chọn **màu kính hiển thị** (Hổ phách, Đỏ hồng ngọc, Xanh olive, Tím nho, Xanh sapphire, Lục bảo, Trắng băng, Đen chữ vàng) và **chất liệu mặt máy** (Nhôm phay xước, bóng, nhám, đen, Titan, Nhôm xám); lưu lại khi mở lại app.
- Vạch phân cách trong màn hình gộp là vạch mờ tan dần ở hai đầu, cùng phong cách với vạch giữa Thiết bị nhận | Kết nối.
- Logo cáo-nho nguyên bản có màu + chữ GRAPE2Play bên dưới.
- Cửa sổ Thiết bị nhận/Kết nối cao thêm để hiển thị đủ chữ.

## Bản 1.1.1 — Nhôm phay xước: bố cục lại
- TV: màn hình menu kéo cao bằng màn hình Danh sách phát; logo + tên app + Limited Edition ở trên cùng, các mục menu xếp tiếp bên dưới; bỏ dòng "Đầu ra" phía trên; hàng nút cứng ở dưới gồm Bật/Tắt (không còn chữ) | xáo trộn, lùi, Play, tới, lặp | Đầu ra, nằm giữa mép dưới màn hình và mép ngoài dải nhôm.
- Điện thoại: ảnh bìa, thông số, VU/thiết bị nhận và thanh tua gộp một màn hình kính, ngăn bằng vạch mờ.

## Bản 1.2.0 — Giao diện Robot / Sci-Fi
- Cài đặt → Bố cục màn hình: thêm lựa chọn **Robot / Sci-Fi** (TV và điện thoại). HUD: nền lưới + vân quét, khung góc vát có ngoặc góc sáng, chữ đơn cách, VU vòng cung 40 vạch (5 vạch cuối đỏ), nút lục giác có đèn (bật = chấm xanh), nút Play có vòng tiến độ.
- Thẻ **Robot / Sci-Fi — màu giao diện**: Cyan Tron, Cam Alien, Đỏ HAL, Tím neon, Lục Matrix. Màu đổi toàn bộ app (mọi màn hình dùng chung bảng màu `Themes.eff`).
- TV: menu, thông tin bài + 6 thông số + đường tín hiệu, VU / thiết bị nhận, danh sách phát, hàng điều khiển dưới (Power | sóng + nút | Đầu ra). Điện thoại: thanh điều hướng trên, màn Đang phát gộp, không còn thông tin riêng của TV.
- Chưa biên dịch/thử trên thiết bị — nếu Gradle báo lỗi, gửi log để sửa.

## Bản 1.3.0 — Giao diện đồng bộ toàn app
- Cài đặt → **Giao diện**: một nơi duy nhất chọn Mặc định / Nhôm phay xước / Robot-Sci-Fi / Cổ điển; thẻ tuỳ biến bên dưới đổi theo giao diện (màu nho/xanh…, màu kính + chất liệu, màu HUD).
- Mỗi giao diện áp cho mọi màn hình: nền, thanh menu, thẻ cài đặt, nút/ô chọn (hình dạng, chữ, đèn), nút điện thoại. Nhôm: khung kim loại + màn kính tối đúng màu kính. Robot: nền lưới, khung góc vát, chữ đơn cách, nút góc vát. Cổ điển: bo tròn lớn.
- Đã bỏ giao diện "Nền màu" (bản đã lưu tự chuyển về Mặc định).

## Bản 1.4.0 — Giao diện theo phong cách hãng + Limited Edition dưới logo
- 5 giao diện mới (Cài đặt → Giao diện): Kiểu Denon, Marantz, Tannoy, McIntosh, Luxman. Mỗi bộ đồng bộ toàn app: mặt máy (kim loại/gỗ), màn hình tối có viền, đồng hồ (vòng cung số hoặc kim analog), núm xoay, nút tròn có đèn, bảng màu và phông riêng; tuỳ biến 3 màu chữ/đèn màn hình mỗi hãng. Chỉ lấy cảm hứng, không dùng logo hãng.
- "Limited Edition" nay nằm ngay dưới logo con cáo ở mọi giao diện: menu TV, thanh điều hướng điện thoại, Giới thiệu, Nhôm, Robot, các bộ hãng.

## Bản 1.5.0 — Limited Edition bằng hình + 5 hãng mới
- "Limited Edition" dùng đúng hình chữ viết tay bạn cung cấp (res/drawable-nodpi/limited_edition.png), không khung, đổi màu theo màu nhấn của từng giao diện; nằm ngay dưới logo con cáo ở mọi menu (TV, điện thoại, Nhôm, Robot, các bộ hãng, Giới thiệu).
- Bỏ chữ GRAPE2Play ở trang Thư viện (TV và điện thoại).
- Thêm 5 giao diện hãng: Kiểu Technics, Accuphase, Yamaha, Pioneer, JBL (tổng 10 hãng), mỗi bộ có 3 màu màn hình tuỳ biến.

## Bản 1.5.1
- Chữ GRAPE2Play nằm ngay dưới logo, trên chữ Limited Edition, ở mọi menu (TV, điện thoại, Nhôm, Robot, các hãng, Giới thiệu); header TV Robot/hãng đã có tên bên cạnh logo nên giữ nguyên.

## Bản 1.6.0
- Bỏ 10 giao diện theo hãng (bản đã lưu tự về Mặc định).
- Robot / Sci-Fi: GRAPE·2·PLAY và LIMITED EDITION dùng phông đơn cách của giao diện; màn Đang phát: Limited Edition dưới GRAPE·2·PLAY, bỏ dòng AUDIO CORE // MK-II, hết che dòng thông tin dưới // SYSTEM.

## Bản 1.6.1
- Sửa: phát DSD từ thư viện DLNA của chính WiiM tới WiiM bị lỗi 714 (Invalid MIME-type) ở cả DoP lẫn DSD gốc. Nguyên nhân: loại file DSD từ DLNA bị nhận sai (đuôi "dsd"/MIME chung) nên app coi là nén (lossy), khai MIME "octet-stream" và bỏ qua DoP. Nay nhận đúng dsf/dff, giữ MIME gốc của máy chủ khi gửi lại chính URL đó.

## Bản 1.6.2
- Nhật ký cho thấy file DSD từ thư viện WiiM bị coi là đuôi "dsd" (đã sửa ở 1.6.1). Thêm: nếu WiiM vẫn từ chối URL của chính nó (714), app tự thử lại qua TV làm trung gian với MIME audio/x-dsf.

## Bản 1.7.0 — Thư viện thông minh
- Thư viện nhớ chỗ đứng (tab, nguồn, thư mục, thư mục DLNA): từ Đang phát bấm Thư viện sẽ về đúng thư mục vừa mở. Tab mặc định là Thư mục.
- Thứ tự ưu tiên nguồn: Thư viện → ⚙ Nguồn hiển thị → ▲ ▼ (áp cho chip nguồn và thư mục gốc).
- Thư mục hiện dạng ô thư mục (khung có tai, ảnh bìa hoặc biểu tượng thư mục), khác ô Album; duyệt thiết bị DLNA cũng dạng lưới thư mục.
- Tab "Thói quen": thời gian nghe hôm nay + 7 ngày, Nhạc hay phát (kèm nút thêm vào Yêu thích), Nhạc ít phát, Chưa nghe bao giờ (kèm thư mục chưa nghe để mở). Gợi ý 💡 ở thư mục gốc.
- Trộn bài (xáo thứ tự rồi phát) và Phát ngẫu nhiên: ở Thư viện, thư mục, thư mục DLNA, Thói quen; nút ⤮ Trộn trên danh sách phát đang nghe (mọi giao diện, cả điện thoại).

## Bản 1.7.1
- Robot/SciFi: chữ BIT‑PERFECT ở Signal Path hiển thị đủ; khối // SYSTEM·LIB·QUEUE·MODE không còn chắn focus giữa nút Power và "Quét lại" (nhảy qua trực tiếp); trang Cài đặt: cột cài đặt và cột EQ có mép dưới bằng cột menu.
- Biểu tượng Cài đặt ở menu đổi thành bánh răng.
- Thư mục trong Thư viện: ô vuông 1:1, nền màu phẳng tối dịu, một icon trắng + một chữ cái lớn (Bolero nâu đỏ/guitar/B, Jazz vàng đồng/sax/J, Pop hồng/micro/P, 24bit tím/tai nghe/2, DSD xanh/đĩa than/DSD); thư mục khác tự chọn màu/icon theo tên.

## Bản 1.7.2
- Sửa lỗi biên dịch FolderBrowser.kt (rotate); ô thư mục hiển thị lại số bài và ảnh bìa nếu có (không có ảnh thì dùng nền màu + icon + chữ cái).

## Bản 1.7.3
- Robot/SciFi: hộp Thiết bị nhận / Kết nối cao hơn, chữ gọn để hiện đủ; bỏ vạch ngang trên ảnh bìa; trang Cài đặt có lề trong để chữ không bị cắt sát mép.
- Thư mục: kèn saxophone vẽ lại, DSD ưu tiên hơn Jazz, 24bit hiện "24"; ẩn số bài khi không biết; thư mục chưa rõ thể loại được tra qua Deezer (cache trong máy).

## Bản 1.7.4
- Robot/SciFi: Signal Path gọn lại để hiện đủ; "Bài kế tiếp"; ô thông tin bài và hộp VU/Thiết bị nhận thấp hơn.
- Hộp Thiết bị nhận: thanh âm lượng loa chuyển sang dọc (mọi giao diện).
- Thư mục: kèn sax vẽ lại; mọi thư mục (trừ DSD, 24) hiện chữ cái đầu của tên.

## Bản 1.7.5
- Kèn sax vẽ lại theo ảnh mẫu (loa kèn bên trái, ống thổi bên phải). Âm lượng loa trở lại thanh ngang, thông tin trong hộp Thiết bị nhận dàn đều. Robot/SciFi: Signal Path chỉ hiện BIT-PERFECT / mức gần.

## Bản 1.7.6
- Robot/SciFi: Signal Path đầy đủ trở lại (chữ chạy khi dài); hộp Kết nối & phát liên tục dùng chữ chạy, đủ mọi dòng; hạ mép dưới giao diện Đang phát.

## Bản 1.7.7
- Sửa lỗi biên dịch basicMarquee (repeatDelayMillis không có trong phiên bản Compose đang dùng).

## Bản 1.7.8
- Robot/SciFi: bỏ chữ chạy; nâng toàn bộ giao diện (cả logo) lên 18dp để hộp Kết nối & phát liên tục cao hơn (160dp).

## Bản 1.8.0 — Trợ lý AI (Gemini)
Tất cả tính năng AI chỉ chạy khi người dùng bấm; app vẫn dùng bình thường khi không có khóa hoặc mất mạng.

**Nhập khóa API không cần gõ trên TV:** Cài đặt ▸ thẻ "AI · GEMINI" ▸ "Ghép khóa bằng điện thoại" (hoặc nút ✨ AI ở Thư viện, lần đầu tự mở bước này). TV hiện mã QR; điện thoại cùng Wi-Fi quét mã, mở trang, dán khóa (tạo miễn phí tại aistudio.google.com/apikey) rồi bấm "Lưu khóa vào TV". TV kiểm tra khóa ngay. Máy chủ tạm (cổng 8790–8799, kèm mã ngẫu nhiên trong địa chỉ) chỉ chạy khi hộp thoại đang mở. Trên điện thoại có thể dán trực tiếp hoặc dùng "Dán từ bộ nhớ tạm". Khóa lưu riêng trong app (SharedPreferences "ai").

**Bốn tính năng** (nút ✨ AI trong Thư viện, hoặc Cài đặt):
1. Hỏi / tìm nhạc bằng lời nói (🎤, nhận dạng giọng nói của hệ thống), bằng chữ, hoặc nhập bằng điện thoại (QR). AI chỉ chuyển câu hỏi thành bộ lọc (nghệ sĩ, từ khoá, thể loại, định dạng, sắp xếp theo thói quen); việc lọc chạy cục bộ nên thư viện không bị gửi lên mạng.
2. Gợi ý hôm nay: dựa vào thứ/giờ, nghệ sĩ hay nghe và danh sách album mẫu, tạo 3 danh sách nghe.
3. Giới thiệu bài / album / nghệ sĩ đang phát (có ghi chú "có thể chưa chính xác").
4. Làm sạch thư viện: tên hiển thị gọn, có dấu tiếng Việt và thể loại cho từng thư mục (xử lý theo lô 25, lưu cache, có thể dừng/tiếp tục/khôi phục). Không đổi tên file thật. Thể loại do AI xác định quyết định màu và biểu tượng của ô thư mục.

Model mặc định: `gemini-flash-latest` (đổi trong Ai.kt nếu muốn). Gói miễn phí có giới hạn yêu cầu/phút nên làm sạch thư viện lớn sẽ chạy chậm theo lô.

## Bản 1.8.1
- Khóa Gemini: TV hiện 2 mã QR (① trang tạo khóa của Google, ② trang gửi khóa về TV); app điện thoại có nút mở thẳng trang tạo khóa Google.

## Bản 1.8.2
- Thêm mục "Trợ lý AI" (✨) vào thanh menu bên trái của TV ở các giao diện Mặc định/Cổ điển, Nhôm phay xước và Robot/SciFi, mở được từ mọi trang.

## Bản 1.8.3
- Khóa Gemini: kiểm tra khóa có giới hạn 20 giây và báo rõ lỗi mạng (trước đây có thể treo ở "Đang kiểm tra khóa…"); khóa được lưu ngay, thêm nút "Kiểm tra khóa"; kết nối Gemini dùng HTTP/1.1 và ưu tiên IPv4 cho TV Android cũ; lỗi mạng ghi vào nhật ký (thẻ AI).

## Bản 1.8.4
- Sửa lỗi biên dịch Ai.kt (Dns phải khai báo bằng object).

## Bản 1.8.5
- Khóa Gemini: thêm nút "Chẩn đoán mạng" đo thời gian từng bước (DNS, TCP, Internet, API + khóa, tạo nội dung) để biết TV kẹt ở đâu.

## Bản 1.8.6
- Gemini: kiểm tra khóa bằng việc liệt kê model (không phụ thuộc tải của model tạo nội dung); khi model quá tải (503) hoặc hết hạn mức (429) tự thử lại rồi chuyển sang model dự phòng (flash-latest → flash-lite-latest → 2.5-flash → 2.5-flash-lite).

## Bản 1.9.0
- Robot/SciFi: mép dưới ảnh bìa và hộp Signal Path thẳng hàng.
- Hộp thoại Trợ lý AI nhỏ gọn hơn, cùng phong cách thẻ Cài đặt của từng giao diện.
- Mới "Gợi ý theo bối cảnh": thời tiết thực tế (Open-Meteo, vị trí ước lượng theo IP), mùa, lễ hội/phong tục (có âm lịch, đếm ngày tới Tết), và chọn tâm trạng (Buồn, Vui, Chill, Thư giãn…).

## Bản 2.0.0 (trước đó là 1.10.0)
- Chữ chạy: tên bài ở trang đang phát (Mặc định, Robot/SciFi, điện thoại) tự chạy khi dài; ở danh sách chỉ chạy khi chọn/di chuột vào dòng.
- AI: tạo playlist từ câu nói (có thể nói thời lượng, vd. "khoảng 1 giờ") và lưu lại; mục "Playlist đã lưu".
- AI DJ: bật trong menu AI, khi sắp hết danh sách sẽ tự thêm 3 album hợp mạch.
- Sửa tag: gom tên nghệ sĩ/album viết nhiều kiểu, thêm dấu tiếng Việt (AI) — duyệt rồi mới áp dụng, chỉ đổi hiển thị, có nút hoàn tác.
- Album bị bỏ quên: AI chọn album chất lượng cao ít nghe, hợp hôm nay.
- Lời bài hát: đọc file .lrc/.txt cùng tên hoặc lời nhúng tag (MP3/FLAC); dịch và giải nghĩa bằng AI. Không có lời thì không bịa.

## Bản 2.1.0
- Thanh sóng âm kiêm tua: mặc định bị khóa; chỉ tua khi bấm OK/chuột vào dải sóng (hiện khung sáng và gợi ý), bấm OK/Quay lại để khóa lại. Rời khỏi dải sóng tự khóa.
- Ra lệnh bằng giọng nói: thêm quyền micro (xin khi bấm 🎤), nhận giọng nói ngay trong app; nút micro trên remote (nếu hệ thống chuyển phím cho app) mở trợ lý AI và nghe luôn.
- Nhiều dịch vụ AI miễn phí: Gemini, Groq, OpenRouter, Mistral, Cerebras. Chọn AI chính, công tắc tự chuyển AI khi hết hạn mức hoặc không phản hồi sau 25 giây, ghi chú dịch vụ đang trả lời ở chân hộp thoại. Nhập khóa: TV quét QR bằng điện thoại, điện thoại dán trực tiếp.

## Bản 2.2.0
- Mới "⚡ Tìm nhanh": tìm bài hát và thư mục trong mọi nguồn đã quét (bộ nhớ, USB, NAS/DLNA, đám mây), không dấu cũng tìm được, kết quả hiện ngay khi gõ/nói, không cần khóa AI.
- AI tìm chính xác hơn: lọc cục bộ trên chỉ mục toàn thư viện, rồi AI đối chiếu lại các album ứng viên trước khi trả kết quả.
- Sửa app tự thoát khi AI chạy nặng: mọi phần quét thư viện chuyển sang luồng nền với chỉ mục dựng sẵn (trước đây chạy trên luồng chính, biên dịch lại regex hàng chục nghìn lần), bắt lỗi hết bộ nhớ thành thông báo.
- Hộp hỏi AI trên TV: ẩn ô gõ chữ và nút Tìm; dùng 🎤 Nói hoặc 📱 nhập bằng điện thoại.

## Bản 2.2.1
- TV, hộp hỏi AI / Tìm nhanh: ô gõ chữ chuyển xuống cuối hộp thoại và mặc định ẩn; bật/tắt bằng nút "⌨ Hiện/Ẩn ô gõ" (nhớ lựa chọn).

## Bản 2.3.0
- Dịch vụ AI: bỏ Mistral (hết miễn phí) và Cerebras (đòi thẻ tín dụng); thêm Cloudflare AI (Account ID + API token) và NVIDIA AI (build.nvidia.com).
- OpenRouter: các model cũ (gpt-oss…:free) không còn; dùng bộ định tuyến "openrouter/free" và tự hỏi danh sách model miễn phí hiện có (nhớ 6 giờ) để không lỗi thời.
- Trang nhập khóa Cloudflare trên điện thoại có hai ô riêng (Account ID, token).

## Bản 2.4.0
- 🎚 Buổi thử dàn: AI chọn tối đa 8 bài thử âm (giọng hát, trầm, động lực, không gian, chi tiết dải cao…) từ chính thư viện, kèm lời nhắc nghe gì; phát cả buổi hoặc lưu playlist.
- 😴 Hẹn giờ ngủ thông minh: 15/30/45/60/90 phút, "hết bài này", hoặc nói/gõ tự nhiên ("ru ngủ 30 phút"); âm lượng nhỏ dần ở cuối rồi tạm dừng; có thể kèm phát nhạc ru ngủ do AI chọn.
- 🌙 Màn hình chờ thông minh: nền đen, ảnh bìa + tên bài + thời tiết + âm lịch/ngày lễ + câu giới thiệu album do AI viết, nội dung dịch chỗ chậm (chống in bóng OLED).

## Bản 2.4.1
- Sửa lỗi "Unterminated array" (Cloudflare/AI trả JSON bị cắt): thêm max_completion_tokens, cứu các mục còn nguyên vẹn thay vì báo lỗi, rút gọn lời nhắc của AI.
- Thông báo lỗi AI được rút gọn một dòng; cửa sổ AI nhỏ gọn hơn (rộng 560, nút và lề gọn hơn).

## Bản 2.5.0
- Cửa sổ AI mặc định trong suốt 85%; trong "Dịch vụ AI & khóa API" chọn Đặc 100% / Trong 85% / Trong 70%.
- Thêm làm mờ nền phía sau cửa sổ AI (Tắt / Nhẹ / Vừa / Mạnh), cần Android 12+.

## Bản 2.6.0
- Giao diện mới **Liquid Glass** (Cài đặt → Giao diện): nền cực quang nhiều màu, thanh bên/thẻ/hộp thoại là kính mờ bo tròn lớn có viền sáng chéo, chữ trắng, màn Đang phát dùng bố cục mặc định với khung kính.

## Bản 2.6.1
- Nút "Quét lại" chuyển từ menu trái vào đầu trang Thư viện (menu có thêm chỗ, nút Thoát không còn bị khuất).
- Liquid Glass: ô nhập, khung Đường tín hiệu, khung phổ tần đổi từ đen sang kính trong suốt; panel nhạt hơn; hộp thoại nhỏ dùng nền tối đặc để dễ đọc.
- Trang Cài đặt: nội dung cuộn tới sát mép màn hình, không còn bị cắt ngang; bỏ dòng "DSD, đầu ra và tinh chỉnh chỉ dùng cho Sony Bravia".

## Bản 2.6.2
- Liquid Glass: các cửa sổ AI là kính trắng mờ (vẫn theo mức 100/85/70% và làm mờ nền).

## Bản 2.6.3
- Cửa sổ AI trong Liquid Glass: thêm lựa chọn Kính trắng / Kính tối trong "Dịch vụ AI & khóa API"; mặc định kính tối trên Android dưới 12 (không làm mờ nền được).

## Bản 2.7.0
- Liquid Glass có chế độ sáng / tối (Cài đặt → Giao diện → thẻ Liquid Glass). Chế độ tối: nền gần đen, cực quang dịu, kính nhạt hơn.

## Bản 2.7.1
- Trang Cài đặt: thêm điểm dừng đầu trang để di chuyển lên tới tiêu đề "Cài đặt" thì cuộn hẳn về đầu; bỏ dòng chữ về Sony Bravia.
- English: rà soát và dịch thêm ~350 chuỗi + ~140 mẫu (menu Trợ lý AI, Hẹn giờ ngủ, Màn hình chờ, Buổi thử dàn, Cài đặt, lỗi kết nối…); tiêu đề cửa sổ AI dịch trước khi viết hoa; Lang.tr dịch cả chuỗi có biểu tượng/emoji đầu dòng và các phần ghép "A · B".

## Bản 2.7.2
- English: dịch nhãn ô thông số trang Đang phát (Format, Bit depth, Frequency, Channels, Source), dòng Tín hiệu "(file gốc, thiết bị tự giải mã)" và nhãn "Làm mờ nền phía sau (cần Android 12…)".

## Bản 2.7.3
- Giao diện English: AI được yêu cầu trả lời mọi nội dung hiển thị bằng tiếng Anh (trước đây nhiều tác vụ chỉ có lời nhắc tiếng Việt nên AI trả tiếng Việt).

## Bản 2.8.3
- Sửa lỗi giao diện Robot/Sci-Fi trên TV: bấm phím lên từ nút Power (thoát) tới nút Cài đặt làm app tự thoát. Nguyên nhân: nút Power chỉ tới "mục menu cuối" bằng một FocusRequester chỉ được gắn khi thư viện đang quét; lúc không quét, Compose báo FocusRequester chưa khởi tạo và app dừng. Nay Cài đặt luôn được gắn FocusRequester đó.

## Bản 2.8.4
- Bỏ tính năng **Hẹn giờ ngủ (sleep timer)** khỏi menu Trợ lý AI (xoá mã, trang hẹn giờ và các chuỗi dịch liên quan).
- Phát tới thiết bị UPnP/Cast: bấm **Home** (hoặc Recents) khi đang phát từ xa nay đóng giao diện giống hệt bấm **Back** (dịch vụ nền giữ app và nhạc phát tiếp), vì trường hợp Back đã chạy ổn còn Home làm nhạc ngừng trên TV của người dùng. Nhật ký có thêm các dòng onStop/onDestroy/dịch vụ nền để truy nguyên nhân thật nếu còn lỗi.

## Bản 2.8.5
- **Nhật ký sử dụng ứng dụng** (giao diện English) hiển thị bằng English: các dòng nhật ký, phần thông tin máy ở đầu, file gửi đi, trang xem qua LAN, email. Nhật ký vẫn được ghi bằng tiếng Việt và chỉ dịch lúc hiển thị/xuất (`LogLang.kt`). Thông báo lỗi ghép động hiện trên giao diện cũng được dịch.
- Phiên bản 2.8.5 (versionCode 88) để người dùng cập nhật trực tuyến từ bản 2.8.4 (87).

## Bản 2.8.6
- Cài đặt: dời **Kiểm tra cập nhật** và **Nhật ký lỗi** xuống ngay trên mục **Về ứng dụng** (cả TV lẫn điện thoại).
- Bỏ cách "đóng giao diện khi bấm Home" của bản 2.8.4/2.8.5: nhật ký cho thấy Activity bị đóng giống hệt Back mà nhạc vẫn ngừng, nên đó không phải nguyên nhân.
- Nhật ký chẩn đoán nhạc ngừng khi rời app lúc phát tới thiết bị UPnP: ghi mọi lần đổi trạng thái của thiết bị (PLAYING/PAUSED/STOPPED…), lỗi liên lạc, và "nhịp tim" mỗi 4 giây (tối đa ~4 phút) khi giao diện đã ẩn: trạng thái phát, vị trí, số byte đã gửi, khoá CPU/Wi-Fi.

## Bản 2.8.7
- Thử tự gửi lại lệnh Phát khi loa UPnP tự tạm dừng lúc app ở nền (đã bỏ ở bản 2.8.8 vì không phù hợp).
- README: sắp xếp lại các bản theo thứ tự tăng dần (bản mới nằm dưới).

## Bản 2.8.8
- Bỏ cách tự động phát lại của 2.8.7 (che mất nguyên nhân thật). Khi nhạc UPnP bị tạm dừng lúc bấm Home, app không còn tự gửi lệnh nào.
- Nhật ký chẩn đoán để xác định chính xác ai làm loa tạm dừng: mỗi lần loa chuyển sang PAUSED/STOPPED, ghi TransportStatus của loa, thời điểm lệnh Tạm dừng cuối của app, của phím media/nút thông báo, và giao diện đã ẩn được bao lâu; ghi các sự kiện hệ thống quanh lúc bấm Home (phím Home, cắm/rút HDMI, thiết bị âm thanh thay đổi). Chỉ ghi nhật ký, không đổi hành vi phát.
- Kết luận đến nay từ nhật ký: app KHÔNG gửi lệnh Tạm dừng nào khi bấm Home (lệnh duy nhất đến từ loa chuyển trạng thái), nên nguyên nhân nằm ngoài lệnh của app (loa/HDMI-CEC/mạng) — cần nhật ký mới để khẳng định.

## Bản 2.8.9
- Loa UPnP (ví dụ JBL L75ms) tắt rồi bật lại: loa thường nhận IP/cổng điều khiển mới nhưng app vẫn dùng địa chỉ cũ nên báo lỗi cho tới khi phải chọn lại đầu ra mặc định để quét lại. Nay khi không liên lạc được với loa lúc nạp bài (và khi mất kết nối lúc đang theo dõi), app tự quét lại, tìm đúng loa theo mã nhận dạng/tên và đổi sang địa chỉ điều khiển mới rồi thử lại, không cần chọn lại đầu ra.
- Nhật ký ghi "địa chỉ điều khiển đã đổi" hoặc "quét lại chưa thấy loa" để biết cách xử lý có tác dụng hay không.

## Bản 2.9.0 — Thư viện nhạc (cải tiến của 2.8.0)
- Quét thư viện chỉ đọc lại file khi kích thước hoặc ngày sửa đổi; lần quét đầu sau khi cập nhật đọc lại toàn bộ thẻ một lần (nếu đọc lỗi thì giữ thẻ cũ).
- Không làm mất bài khi USB rút / ổ không đọc được / quét NAS-DLNA-đám mây bị dừng, có thư mục lỗi hoặc nguồn trả về rỗng: giữ bản ghi cũ.
- Cache thư viện ghi an toàn (file tạm → sao lưu `.bak` → đổi tên); nạp cache từng bản ghi nên một bản ghi hỏng không làm mất cả nguồn; tự dùng `.bak` nếu file chính hỏng. Cache cũ đọc bình thường, không cần chuyển đổi.
- Thẻ mở rộng được lưu khi file có: năm, số đĩa, nghệ sĩ album, nhà soạn nhạc (FLAC và các định dạng đọc qua hệ thống). Chưa hiển thị ra giao diện.
- Tìm nhanh (Trợ lý AI) khớp thêm thể loại, nghệ sĩ album và năm.
- File bị di chuyển/đổi tên: Yêu thích, Gần đây và thống kê nghe tự chuyển sang đường dẫn mới khi đúng một file mới khớp (kích thước, thời lượng, tên, nghệ sĩ, album); mơ hồ thì không làm gì và không xóa gì.
- Thống kê nghe đọc từng bản ghi; lưu thêm MIME của nguồn DLNA vào cache.


## Bản 2.9.1 — Chọn ảnh bìa bằng AI (có xác nhận)
- Trợ lý AI → "Chọn ảnh bìa bằng AI": chỉ chạy khi bấm "Đồng ý, tìm ảnh bìa". App lấy ảnh từ Deezer (`album.cover_xl`), MusicBrainz/Cover Art Archive (`front-500`) và iTunes (`artworkUrl100` đổi thành 600x600), đo độ phân giải, tỉ lệ vuông và phát hiện ảnh trống ngay trên máy.
- AI (văn bản, không xem được ảnh) chỉ nhận tên bài/nghệ sĩ/album và các số đo, chọn 1 ảnh theo thứ tự: đúng nghệ sĩ/album → độ phân giải cao → vuông 1:1. Không có khóa AI hoặc AI trả kết quả sai thì dùng quy tắc tương tự trên máy. Cả ba đều là ảnh trống thì báo placeholder và không tạo ảnh mới.
- Người dùng xem trước, có thể xem ảnh của nguồn khác, rồi bấm "Đồng ý dùng ảnh này" thì ảnh mới được lưu; "Không dùng" thì không lưu gì. Ảnh chỉ lưu trong app (thư mục `usercovers`), không ghi vào file nhạc, có thể bỏ lại bất cứ lúc nào ("Bỏ ảnh đã chọn").
- Ảnh người dùng đã chọn được ưu tiên cao nhất khi hiển thị bìa.

## Bản 2.9.2
- Sửa lỗi nhạc tự phát ra loa UPnP khi bật lại Mibox dù không mở app: loa tắt/bật lại báo trạng thái "dừng" giữa bài, app hiểu nhầm là "hết bài" nên tự phát bài kế. Nay chỉ coi là hết bài khi vị trí đã gần cuối bài (còn ≤ 10 giây); dừng giữa bài thì chuyển sang trạng thái tạm dừng, không tự phát gì và ghi nhật ký "báo dừng giữa bài".

## Bản 2.9.3 — Củng cố phát nhạc (chưa kiểm thử trên thiết bị)
- Hàng đợi/shuffle: khi bật xáo trộn, bài đang nghe được ghim lên đầu thứ tự xáo — trước đây khi tắt lặp, các bài đứng trước nó trong thứ tự ngẫu nhiên sẽ không bao giờ được phát. Chỉ số bài ngoài phạm vi không còn làm app lỗi (Phát/Chuyển tới bài). Chế độ lặp 1 bài: sau lỗi sang đúng bài kế thay vì quay lại bài lỗi. Sửa danh sách (thêm bài, trộn) không còn bị tính là nghe lại bài (Gần đây/thống kê).
- Gapless: tách rõ "bộ đệm lớn" với gapless thật. Nhật ký ghi mỗi lần tự chuyển bài là liền mạch hay bộ xuất âm thanh phải cấu hình lại (khác tần số/độ sâu/số kênh nên có thể có khoảng lặng ngắn). Mô tả trong Cài đặt âm thanh đã sửa cho đúng.
- Phục hồi lỗi: lỗi AudioTrack thoáng qua (đổi/rút/cắm đầu ra) thử lại cùng bài, cùng vị trí 2 lần trước khi bỏ qua; mất nguồn thoáng qua (NAS/mạng) chờ 1,5 s rồi 5 s thử lại đúng chỗ đang nghe; lỗi bài kế nạp trước không còn tự bật phát khi bạn đang tạm dừng.
- DSP/EQ: tự tính đỉnh đáp tuyến (EQ + preamp) và tự hạ độ lợi tương ứng để tín hiệu đầy thang không bị cắt đỉnh (ví dụ preset "bass" nâng +5 dB sẽ tự hạ khoảng 6 dB; mức nghe nhỏ hơn một chút nhưng không méo). Bộ lọc tính bằng số thực 64-bit, xoá trạng thái khi tua/đổi bài, bỏ qua EQ nếu quá 8 kênh. Khi phẳng vẫn chép nguyên dữ liệu (bit-perfect).
- Không đổi: DSD/DoP, USB, NAS, UPnP, Cast.

## Bản 2.9.4 — Audio Core Correction & Bit-perfect (chưa biên dịch/kiểm thử trên thiết bị)
- Đầu ra âm thanh: không còn gộp thiết bị cùng loại; chọn đúng từng USB DAC/HDMI/Bluetooth; danh sách tự cập nhật khi cắm/rút; thiết bị bị rút tự về mặc định hệ thống và tự quay lại khi cắm lại.
- Mô tả Hi-res đúng: float 32-bit chỉ là yêu cầu cho ExoPlayer, không bảo đảm độ sâu bit tại DAC.
- Chẩn đoán chuyển bài chỉ ghi điều quan sát được ("không phát hiện thay đổi định dạng đầu ra", "chưa đo được khoảng lặng"), không khẳng định gapless.
- EQ: headroom tự động có dư 0,3 dB; EQ phẳng/tắt giữ nguyên dữ liệu. Không chống clipping tuyệt đối.
- Hàng đợi/lỗi: sửa Trộn bỏ sót bài, thêm bài chèn ngẫu nhiên, không tự phát khi Pause, bài nạp sẵn của loa UPnP cập nhật khi đổi Trộn/Lặp.
- Bit-perfect: bốn trạng thái VERIFIED / NOT VERIFIED / NOT BIT-PERFECT / UNKNOWN, tám trường thông tin, bấm ⓘ để xem giải thích. Hiện không có đường phát nào được VERIFIED vì app không có phương pháp xác minh toàn đường truyền. Xem BITPERFECT.md.
- Test logic thuần: `./run_core_tests.sh`. Tài liệu: CHANGELOG.md, AUDIO_CORE_AUDIT.md, TEST_REPORT.md, RELEASE_NOTES.md.

## Bản 2.9.5 — Final Bit-perfect Accuracy Fix, Device Registry, Rollback (chưa biên dịch/kiểm thử trên thiết bị)
- Bit-perfect: khả năng thiết bị và tần số mixer chỉ là gợi ý (NOT VERIFIED); chỉ có bằng chứng thật mới ra NOT BIT-PERFECT; Resampling chỉ "Yes" khi có bằng chứng, còn lại "Unknown".
- Thiết bị đang định tuyến tách khỏi thiết bị bạn chọn; không xác định được thì hiển thị "System Default – Route Unknown" (Android 13+ mới xác định được).
- Hiển thị đúng PCM 16/24/32-bit hoặc Float theo định dạng AudioSink nhận; không suy Float từ cài đặt Hi-res.
- Device Registry + hồ sơ thiết bị: hộp thoại xác nhận thiết bị USB/HDMI/S/PDIF/Bluetooth, danh mục tích hợp (hiện FiiO K9 Pro ESS), khai báo thiết bị lạ, quản lý hồ sơ. "VERIFIED – CONFIGURATION" là xác minh theo cấu hình, không phải phép đo toàn tuyến.
- Quay về phiên bản trước (Cập nhật ▸ Quay về phiên bản trước): tải bản cũ vào Download/GRAPE2Play; Android buộc gỡ app trước khi cài bản thấp hơn (mất dữ liệu app, không mất file nhạc).
- Test logic: `./run_core_tests.sh` (9782 đạt / 0 lỗi). Build Android và thiết bị thật: chưa xác minh. Chi tiết: BITPERFECT.md, TEST_REPORT.md, RELEASE_NOTES.md, CHANGELOG.md.

## Bản 2.9.6 — Tích hợp GRAPE2Launcher (chưa biên dịch/kiểm thử trên thiết bị)
- MediaSession công bố bài đang phát (tên, nghệ sĩ, album, thời lượng, ảnh bìa), trạng thái + vị trí, hàng đợi ≤100 bài và các hành động điều khiển; thông báo dạng media.
- `LibraryProvider`: `content://com.grape.play.library/albums` (≤12 album: `_id, title, artist, cover`).
- Mở thẳng thư viện khi launcher gửi extra `grape_open = library`.
- Giả định, giới hạn và cách kiểm tra trên TV: LAUNCHER_INTEGRATION.md.
