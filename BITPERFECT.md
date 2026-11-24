# Cơ chế Bit-perfect (2.9.4)

## Vì sao đổi
Trước 2.9.4, `signalGrade()` (Signal.kt) trả "BIT-PERFECT" (màu xanh, dấu ✓) chỉ dựa trên: EQ phẳng, định dạng không phải m4a/lossy, và — với UPnP/Cast — **mọi file không phải m4a** hay DSD gửi file gốc. Không có bằng chứng nào về mixer Android, HAL, DAC, TV hay renderer. Nay app chỉ nêu điều nó có căn cứ.

## Bốn trạng thái (BitPerfect.java)
| Trạng thái | Khi nào |
|---|---|
| **BIT-PERFECT VERIFIED** | Chỉ khi `Input.pathProof = true` (có phương pháp xác minh toàn đường truyền) và không có bằng chứng đổi dữ liệu. **Hiện không có mã nào đặt `pathProof`, nên VERIFIED không bao giờ xuất hiện.** |
| **NOT VERIFIED** | Mặc định cho đường phát đã biết một phần nhưng chưa đủ chứng minh (loa mặc định, HDMI/quang, USB, UPnP, Cast, Bluetooth chưa biết codec). Không có nghĩa là lỗi. |
| **NOT BIT-PERFECT** | Chỉ khi có BẰNG CHỨNG dữ liệu bị đổi (bảng dưới). Thiếu thông tin không bao giờ dẫn tới trạng thái này. |
| **UNKNOWN** | Không có bài đang phát, hoặc phát cục bộ nhưng chưa có thông tin bộ xuất âm thanh. |

### Bằng chứng NOT BIT-PERFECT (đã được code kiểm tra)
- DSP/EQ/Preamp/cân bằng đang bật khi phát cục bộ (`DspParams.isFlat()` sai).
- Chế độ tương thích hạ 24/32-bit xuống 16-bit (`AudioSettings.hires` tắt, nguồn > 16-bit).
- DSD phát cục bộ (chuyển sang PCM).
- Thiết bị đã chọn (AudioDeviceInfo) tự công bố danh sách tần số không có tần số của bài → Android phải resample.
- Thiết bị công bố độ sâu bit hẹp hơn dữ liệu cần giữ (chuyển rộng hơn như 16→24-bit hay 24-bit→float32→24-bit không tính là đổi dữ liệu).
- Android báo tần số mixer (`PROPERTY_OUTPUT_SAMPLE_RATE`) khác tần số bài khi đi qua mixer mặc định/loa/analogue/Bluetooth (không áp dụng cho thiết bị có danh sách tần số riêng).
- Bluetooth có codec mất dữ liệu (chỉ khi biết codec — hiện app không đọc được codec nên trường hợp này chưa xảy ra thực tế).

## Tám trường độc lập (hộp thoại khi bấm ⓘ ở trạng thái)
1 Source Format · 2 Decoder Output · 3 DSP Processing · 4 Output Device · 5 Output Capability · 6 Actual Output Format · 7 Resampling Status (Yes/No/Unknown) · 8 Bit-perfect Status. Output Capability (khả năng thiết bị công bố) không bao giờ thay cho Actual Output Format; "Actual Output Format" của app chỉ là định dạng app **yêu cầu** AudioTrack và không phải bằng chứng duy nhất.

## Nguồn dữ liệu (BitPerfectProbe.kt — chỉ đọc)
| Dữ liệu | API | Độ tin cậy |
|---|---|---|
| Định dạng file | Track / đầu dò file | Tốt |
| Định dạng PCM sau giải mã | `Playback.sinkFmt` (quan sát `AudioSink.configure`) | Chắc chắn (phần app) |
| Hạ 16-bit hay float | `AudioSettings.hires` | Chắc chắn (quyết định của app) |
| Tần số/độ sâu bit thiết bị công bố | `AudioDeviceInfo.getSampleRates()/getEncodings()` | Chỉ là khả năng |
| Tần số mixer | `AudioManager.getProperty(PROPERTY_OUTPUT_SAMPLE_RATE)` | Android báo, không do app đo |
| Âm lượng | `AudioManager.getStreamVolume/isVolumeFixed` | Chỉ gợi ý (không biết độ lợi thật) |

## Không thể xác minh
Dữ liệu tại HAL/DAC; TV/ampli có đổi tín hiệu HDMI/quang hay không; định dạng đang chạy của USB DAC (descriptors cho khả năng, không cho cấu hình đang dùng); mọi thứ bên trong renderer UPnP và thiết bị Cast; codec Bluetooth (cần quyền và API hệ thống); hệ số khuếch đại âm thanh do âm lượng hệ thống.

## Trường hợp VERIFIED thực sự được hỗ trợ
**Không có.** Android 14 có `AudioMixerAttributes` (MIXER_BEHAVIOR_BIT_PERFECT) nhưng đó là cách *yêu cầu* đường bit-perfect (thay đổi đường phát), không phải cách *đo*; yêu cầu bảo toàn engine nên không dùng. Khi nào có phương pháp xác minh thực tế, chỉ cần đặt `pathProof` ở BitPerfectProbe — logic phân loại đã sẵn và có test.

## Không thay đổi
Playback Engine, decoder, DSD/DoP/PCM, DSP/EQ/Preamp, USB/NAS, UPnP/Cast, buffering, audio sink, chuyển bài. Chỉ thêm một luồng đọc `Playback.sinkFmt` và `Playback.context` (chỉ đọc).
