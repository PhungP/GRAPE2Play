# GRAPE2Play 2.9.4 — Ghi chú phát hành (chưa biên dịch/kiểm thử trên thiết bị)

**Bit-perfect trung thực hơn:** trạng thái nay là VERIFIED / NOT VERIFIED / NOT BIT-PERFECT / UNKNOWN. App không còn tự nhận "BIT-PERFECT" khi chỉ tắt EQ hoặc dùng USB/UPnP/Cast. Hiện app không có cách đọc dữ liệu tại DAC/TV/renderer nên **sẽ không hiện VERIFIED**; phần lớn đường phát sẽ hiện NOT VERIFIED (mặc định) và NOT BIT-PERFECT khi có bằng chứng (EQ bật, hạ 16-bit, DSD→PCM, resampling…). Bấm ⓘ cạnh trạng thái ở màn hình Đang phát để xem lý do.

**Đầu ra âm thanh:** chọn được từng USB DAC/HDMI/Bluetooth cùng loại; tự cập nhật khi cắm/rút; thiết bị bị rút → về mặc định hệ thống, cắm lại thì tự quay lại.

**Phát nhạc:** Trộn không còn bỏ sót bài khi tắt lặp; thêm bài không chèn ngẫu nhiên trước bài đang nghe; thử lại lỗi AudioTrack/mất mạng; không tự phát khi đang Pause; loa UPnP cập nhật bài nạp sẵn khi đổi Trộn/Lặp.

**EQ:** tự giảm độ lợi để không cắt đỉnh (nghe nhỏ hơn khi nâng EQ nhiều); EQ phẳng vẫn chép nguyên dữ liệu. Không chống clipping tuyệt đối với mọi tín hiệu.

**Nhật ký chuyển bài:** chỉ nêu "không phát hiện thay đổi định dạng"; không khẳng định gapless.

Giới hạn: xem AUDIO_CORE_AUDIT.md, TEST_REPORT.md, BITPERFECT.md. Cast không nạp sẵn bài kế.
