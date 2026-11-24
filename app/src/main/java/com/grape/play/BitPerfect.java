package com.grape.play;

import java.util.ArrayList;
import java.util.List;

/**
 * Phân loại trạng thái Bit-perfect của đường phát — thuần Java, không đụng tới luồng âm thanh (chỉ đọc dữ liệu đã thu thập).
 *
 * Nguyên tắc: VERIFIED chỉ khi có {@link Input#pathProof} (bằng chứng xác minh toàn đường truyền). Hiện app KHÔNG có phương pháp nào như vậy
 * (Android không cho đọc ngược dữ liệu tại HAL/DAC/renderer) nên không đường phát nào được VERIFIED. NOT_BIT_PERFECT chỉ khi có BẰNG CHỨNG dữ liệu bị đổi;
 * thiếu thông tin chỉ cho NOT_VERIFIED hoặc UNKNOWN.
 */
public final class BitPerfect {
    private BitPerfect() {}

    public enum Status { VERIFIED, NOT_VERIFIED, NOT_BIT_PERFECT, UNKNOWN }
    public enum Route { LOCAL_DEFAULT, SPEAKER, HDMI, OPTICAL, USB, BLUETOOTH, WIRED_ANALOG, OTHER, UPNP, CAST }
    public enum Tri { YES, NO, UNKNOWN }

    /** Lý do / bằng chứng; Kotlin (BitPerfectText) ánh xạ sang câu chữ VI/EN. */
    public enum Code {
        // Bằng chứng dữ liệu bị đổi → NOT_BIT_PERFECT
        DSP_ON, DEPTH_REDUCED, DSD_TO_PCM, DEVICE_RATE_UNSUPPORTED, DEVICE_FORMAT_UNSUPPORTED, ANDROID_RATE_DIFFERS, BT_LOSSY_CODEC,
        // Điều đã xác minh được (không đồng nghĩa VERIFIED)
        DSP_OFF, DSP_NOT_APPLIED_REMOTE, SENDS_ORIGINAL_FILE, APP_NO_RESAMPLE, DEVICE_RATE_LISTED,
        // Chưa xác minh được
        PATH_UNVERIFIED, CAPABILITY_ONLY, RENDERER_NO_OUTPUT_INFO, CAST_NO_OUTPUT_INFO, TV_PATH_UNKNOWN, USB_ACTUAL_UNVERIFIED,
        BT_CODEC_UNKNOWN, BT_CODEC_LOSSLESS_UNVERIFIED, DOP_RENDERER_UNVERIFIED, VOLUME_NOT_VERIFIED, VOLUME_BELOW_MAX, NO_SINK_INFO, NO_TRACK, DEVICE_DISCONNECTED
    }

    public static final class Input {
        public boolean hasTrack;
        // Nguồn
        public String srcExt = ""; public boolean srcDsd; public boolean srcLossy; public int srcBits, srcRate, srcChannels;
        // Sau giải mã (đầu vào bộ xuất âm thanh)
        public boolean hasDecoder; public int decBits, decRate; public boolean decFloat;
        // Định dạng app yêu cầu cho AudioTrack
        public boolean hasOut; public int outBits, outRate; public boolean outFloat;
        // DSP/EQ (chỉ áp dụng khi phát cục bộ)
        public boolean dspActive;
        // Đường phát và thiết bị
        public Route route = Route.LOCAL_DEFAULT; public String deviceName = "", deviceModel = "";
        public boolean deviceDisconnected;                         // thiết bị đã chọn không còn kết nối, đang dùng mặc định hệ thống
        // Khả năng do thiết bị công bố (AudioDeviceInfo). Rỗng/false = không biết.
        public int[] capRates = new int[0];
        public boolean capEncKnown, cap16, cap24, cap32, capFloat;
        public int mixerRate;                                      // AudioManager PROPERTY_OUTPUT_SAMPLE_RATE (0 = không biết)
        public String btCodec = "";                                // "" = không biết
        public boolean btCodecLossy;
        public boolean dopActive;                                  // DSD gửi dạng DoP tới UPnP
        public boolean volumeFixed; public int volumeIdx, volumeMax;
        /** Bằng chứng đã xác minh toàn đường truyền. Hiện KHÔNG có mã nào đặt true (không có phương pháp kiểm chứng). */
        public boolean pathProof;
    }

    public static final class Result {
        public Status status = Status.UNKNOWN;
        public Tri resampling = Tri.UNKNOWN;
        public final List<Code> changes = new ArrayList<>();      // bằng chứng dữ liệu bị đổi
        public final List<Code> confirmed = new ArrayList<>();    // đã xác minh được
        public final List<Code> unverified = new ArrayList<>();   // chưa xác minh được
    }

    private static boolean has(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }
    private static boolean remote(Route r) { return r == Route.UPNP || r == Route.CAST; }

    public static Result evaluate(Input in) {
        Result r = new Result();
        if (!in.hasTrack) { r.unverified.add(Code.NO_TRACK); r.status = Status.UNKNOWN; return r; }
        boolean rem = remote(in.route);
        boolean rateChange = false;

        if (in.srcDsd) {
            if (!rem) r.changes.add(Code.DSD_TO_PCM);
            else if (in.dopActive) r.unverified.add(Code.DOP_RENDERER_UNVERIFIED);
        }

        if (rem) {
            r.confirmed.add(Code.DSP_NOT_APPLIED_REMOTE);
            r.confirmed.add(Code.SENDS_ORIGINAL_FILE);
            r.unverified.add(in.route == Route.CAST ? Code.CAST_NO_OUTPUT_INFO : Code.RENDERER_NO_OUTPUT_INFO);
        } else {
            if (in.dspActive) r.changes.add(Code.DSP_ON); else r.confirmed.add(Code.DSP_OFF);
            if (!in.srcDsd) {
                if (in.hasDecoder && in.hasOut) {
                    if (!in.outFloat && in.decBits > 16 && in.outBits > 0 && in.outBits < in.decBits) r.changes.add(Code.DEPTH_REDUCED);
                    if (in.decRate > 0 && in.outRate > 0 && in.decRate != in.outRate) { r.changes.add(Code.ANDROID_RATE_DIFFERS); rateChange = true; }
                    else r.confirmed.add(Code.APP_NO_RESAMPLE);
                } else r.unverified.add(Code.NO_SINK_INFO);
            } else if (!in.hasOut) r.unverified.add(Code.NO_SINK_INFO);

            if (in.hasOut) {
                if (in.capRates.length > 0) {
                    if (!has(in.capRates, in.outRate)) { r.changes.add(Code.DEVICE_RATE_UNSUPPORTED); rateChange = true; }
                    else { r.confirmed.add(Code.DEVICE_RATE_LISTED); r.unverified.add(Code.CAPABILITY_ONLY); }
                }
                if (in.capEncKnown) {
                    // Chuyển sang định dạng RỘNG HƠN không đổi giá trị mẫu (16→24/32-bit, 24-bit→float32→24/32-bit). Chỉ khi thiết bị hẹp hơn dữ liệu cần giữ mới là bằng chứng đổi dữ liệu.
                    int src = in.decBits > 0 ? in.decBits : in.srcBits;
                    int need = in.outFloat ? Math.min(src > 0 ? src : 24, 24) : 16;
                    int depth = in.capFloat || in.cap32 ? 32 : in.cap24 ? 24 : in.cap16 ? 16 : 0;
                    if (depth < need) r.changes.add(Code.DEVICE_FORMAT_UNSUPPORTED);
                }
                boolean mixerPath = in.capRates.length == 0 && (in.route == Route.LOCAL_DEFAULT || in.route == Route.SPEAKER || in.route == Route.WIRED_ANALOG || in.route == Route.BLUETOOTH);
                if (mixerPath && in.mixerRate > 0 && in.outRate > 0 && in.mixerRate != in.outRate) { r.changes.add(Code.ANDROID_RATE_DIFFERS); rateChange = true; }
            }

            switch (in.route) {
                case BLUETOOTH:
                    if (in.btCodec.isEmpty()) r.unverified.add(Code.BT_CODEC_UNKNOWN);
                    else if (in.btCodecLossy) r.changes.add(Code.BT_LOSSY_CODEC);
                    else r.unverified.add(Code.BT_CODEC_LOSSLESS_UNVERIFIED);
                    break;
                case HDMI: case OPTICAL: r.unverified.add(Code.TV_PATH_UNKNOWN); break;
                case USB: r.unverified.add(Code.USB_ACTUAL_UNVERIFIED); break;
                default: r.unverified.add(Code.PATH_UNVERIFIED);
            }
            if (in.deviceDisconnected) r.unverified.add(Code.DEVICE_DISCONNECTED);
            if (!in.volumeFixed && in.volumeMax > 0 && in.volumeIdx < in.volumeMax) r.unverified.add(Code.VOLUME_BELOW_MAX);
            else r.unverified.add(Code.VOLUME_NOT_VERIFIED);
        }

        r.resampling = rateChange ? Tri.YES : (in.pathProof && r.changes.isEmpty() ? Tri.NO : Tri.UNKNOWN);
        if (!r.changes.isEmpty()) r.status = Status.NOT_BIT_PERFECT;
        else if (in.pathProof) r.status = Status.VERIFIED;
        else if (!rem && !in.hasOut) r.status = Status.UNKNOWN;
        else r.status = Status.NOT_VERIFIED;
        return r;
    }
}
