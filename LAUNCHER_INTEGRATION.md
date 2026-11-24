# Tích hợp với GRAPE2Launcher (2.9.6) — BUILD NOT VERIFIED, chưa thử trên TV

Launcher (`com.grape.launcher`) chỉ dùng kênh chuẩn của Android: MediaSession đang hoạt động, ContentProvider và intent extra.

## Đã làm
| File | Nội dung |
|---|---|
| `MediaSessionBridge.kt` (mới) | `android.media.session.MediaSession` (framework, không thêm thư viện). Metadata: TITLE, ARTIST, ALBUM, DURATION, ALBUM_ART (bitmap ≤300 px, nạp bất đồng bộ rồi gắn lại). PlaybackState PLAYING/PAUSED kèm vị trí + tốc độ (1f/0f). Actions: PLAY, PAUSE, PLAY_PAUSE, SKIP_TO_NEXT, SKIP_TO_PREVIOUS, SEEK_TO, STOP. Hàng đợi ≤100 bài quanh bài hiện tại + `activeQueueItemId`. Chỉ đọc StateFlow của `Playback` và gọi `toggle/next/prev/seekTo`; **không sửa Playback.kt**. Phản ánh cả phát tới UPnP/Cast. |
| `QueueWindow.java` (mới, có test) | Tính cửa sổ hàng đợi công bố. |
| `BackgroundAccess.kt` (`PlaybackService`) | Khởi tạo bridge trong `onCreate`; thông báo thành kiểu media (`Notification.MediaStyle` gắn token, nút Trước / Phát-Tạm dừng / Sau / Dừng); cập nhật thông báo khi phát/tạm dừng. Dịch vụ foreground mediaPlayback và quyền FOREGROUND_SERVICE(_MEDIA_PLAYBACK), POST_NOTIFICATIONS đã có sẵn từ trước. |
| `LibraryProvider.kt` (mới) + `AndroidManifest.xml` | `content://com.grape.play.library/albums`, exported, chỉ đọc. Cột `_id, title, artist, cover` (BLOB JPEG ≤256 px). ≤12 dòng: phát gần đây → yêu thích → phần còn lại của thư viện, mỗi album một dòng. Thumbnail lưu `filesDir/libcovers`; chưa có thì tạo từ `Covers.get` với giới hạn 1,2 s/ảnh và 3,5 s/lần query (quá hạn → cover NULL). Không trả đường dẫn file/nguồn. |
| `Library.kt` | Thêm `loadCacheOnly` (đọc cache, không quét) để provider dùng khi tiến trình mới được đánh thức. |
| `MainActivity.kt` | `handleOpen`: extra `grape_open = "library"` → mở màn thư viện, xử lý ở `onCreate` và `onNewIntent`. Khởi tạo bridge khi mở app. |
| `app/build.gradle.kts` | versionCode 99, versionName 2.9.6. |

## Giả định / việc bạn cần tự kiểm tra
1. Tôi không có file `GRAPE2Play_TichHop_Launcher.md` (không có trong đính kèm); làm theo mô tả trong tin nhắn. Nếu đặc tả quy định khác (tên cột, kích thước, key metadata) hãy cho tôi biết.
2. Trước đó **chưa có MediaSession**, nên đây là thêm mới, không phải sửa chỗ thiếu.
3. Khi bật Trộn, thứ tự hàng đợi công bố là thứ tự danh sách, **không phải thứ tự xáo trộn thực tế** (thứ tự xáo nằm trong Playback.kt, tôi không sửa). "Bài sau bài đang phát" trên launcher có thể khác bài thực sự phát tiếp khi Trộn bật.
4. Đang phát UPnP/Cast: vị trí/thời lượng lấy từ `Playback`; chưa kiểm chứng độ chính xác.
5. Không khai báo `ACTION_SKIP_TO_QUEUE_ITEM` (chọn bài trong hàng đợi từ launcher chưa hỗ trợ) vì sẽ phải gọi lại `Playback.play` và làm lại hàng đợi.
6. `exported="true"` cho provider: bất kỳ app nào cũng đọc được 12 album (tên, nghệ sĩ, ảnh nhỏ). Đúng yêu cầu, nhưng là thông tin về thư viện của bạn.
7. Launcher cần quyền đọc phiên media (thường là Notification Listener) — việc của launcher.
8. Chưa biên dịch: các lớp framework (`MediaSession`, `Notification.MediaStyle`, `MatrixCursor`) cần kiểm tra khi `assembleDebug`.

## Cách kiểm tra trên TV
Phát một bài trong GRAPE2Play → về launcher → Cài đặt ▸ Hệ thống ▸ Kiểm tra nhạc. Mong đợi cho `com.grape.play`: trạng thái đang phát; tên bài/nghệ sĩ/album "có"; thời lượng có số giây; vị trí "có"; ảnh bìa "có"; hàng đợi > 0; hỗ trợ tiếp/trước/tua "có". Dòng nào "KHÔNG" là chưa đạt — gửi tôi dòng đó.
Kiểm provider: `adb shell content query --uri content://com.grape.play.library/albums` (cột cover là BLOB). Kiểm mở thư viện: `adb shell am start -n com.grape.play/.MainActivity --es grape_open library` (cả khi app đang chạy).
Kiểm session: `adb shell dumpsys media_session | grep -A20 GRAPE2Play`.
