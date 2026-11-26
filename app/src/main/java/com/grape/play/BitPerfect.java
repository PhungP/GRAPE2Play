package com.grape.play;

import java.util.ArrayList;
import java.util.List;

/**
 * Phân loại trạng thái Bit-perfect — thuần Java, chỉ đọc dữ liệu đã thu thập, không đụng tới luồng âm thanh.
 *
 * Nguyên tắc (2.9.5): khả năng thiết bị công bố (AudioDeviceInfo), tần số mixer Android báo và thiết bị người dùng CHỌN đều chỉ là gợi ý/khả năng,
 * KHÔNG phải bằng chứng dữ liệu bị đổi. NOT_BIT_PERFECT chỉ khi có bằng chứng thật: app tự sửa dữ liệu (DSP, hạ độ sâu bit, DSD→PCM),
 * codec Bluetooth mất dữ liệu đã biết, hoặc {@link Input#rateEvidence}.
 * VERIFIED có hai mức: END_TO_END (cần {@link Input#pathProof}, hiện không có phương pháp nào) và CONFIGURATION (thiết bị + phương thức kết nối khớp danh mục tích hợp,
 * đúng thiết bị Android đang định tuyến, định dạng nằm trong thông số danh mục, không DSP, không bằng chứng đổi dữ liệu) — KHÔNG phải phép đo toàn tuyến.
 */
public final class BitPerfect {
    private BitPerfect() {}

    public enum Status { VERIFIED, NOT_VERIFIED, NOT_BIT_PERFECT, UNKNOWN }
    /** ROUTE_UNKNOWN = "System Default – Route Unknown": không xác định được thiết bị Android thực sự đang định tuyến. */
    public enum Route { ROUTE_UNKNOWN, SPEAKER, HDMI, SPDIF, USB, BLUETOOTH, WIRED_ANALOG, OTHER, UPNP, CAST }
    public enum Tri { YES, NO, UNKNOWN }
    /** Mức xác minh của phiên phát hiện tại (VERIFIED có hai mức). */
    public enum VerLevel { NONE, CONFIGURATION, END_TO_END, USER_CONFIGURATION }
    /** Trạng thái xác minh của TỔ HỢP thiết bị + phương thức kết nối (tách khỏi trạng thái phiên phát). */
    public enum DevLevel { NONE, USER_DECLARED, REGISTRY_UNVERIFIED, REGISTRY_CONFIG, REGISTRY_E2E }

    /** Loại PCM của AudioSink (đúng theo Playback.sinkFmt.encoding). */
    public static final int ENC_UNKNOWN = 0, ENC_16 = 16, ENC_24 = 24, ENC_32 = 32, ENC_FLOAT = -1;

    public enum Code {
        // Bằng chứng dữ liệu bị đổi → NOT_BIT_PERFECT
        DSP_ON, DEPTH_REDUCED, DSD_TO_PCM, BT_LOSSY_CODEC, RATE_CONVERSION_EVIDENCE,
        // Đã xác minh được (không đồng nghĩa VERIFIED)
        USER_VERIFIED_CONFIG, DSP_OFF, DSP_NOT_APPLIED_REMOTE, SENDS_ORIGINAL_FILE, APP_NO_RESAMPLE,
        // Chưa xác minh / chỉ là gợi ý
        ROUTE_UNKNOWN, PATH_UNVERIFIED, DEV_NOT_VERIFIED, DEV_ROUTE_MISMATCH, DEV_FORMAT_OUTSIDE_REGISTRY, SRC_INCOMPLETE, SOURCE_SINK_RATE_DIFFERS, CONFIG_CRITERIA_MET, CAPABILITY_ONLY, CAPABILITY_MISMATCH_HINT, MIXER_RATE_HINT, SELECTED_NOT_CONFIRMED,
        RENDERER_NO_OUTPUT_INFO, CAST_NO_OUTPUT_INFO, TV_PATH_UNKNOWN, USB_ACTUAL_UNVERIFIED,
        BT_CODEC_UNKNOWN, BT_CODEC_LOSSLESS_UNVERIFIED, DOP_RENDERER_UNVERIFIED, VOLUME_NOT_VERIFIED, VOLUME_BELOW_MAX, NO_SINK_INFO, NO_TRACK, DEVICE_DISCONNECTED
    }

    public static final class Input {
        public boolean hasTrack;
        // Nguồn
        public String srcExt = ""; public boolean srcDsd; public boolean srcLossy; public int srcBits, srcRate, srcChannels;
        // Định dạng AudioSink NHẬN (sau giải mã) — từ Playback.sinkFmt, KHÔNG phải định dạng AudioTrack/Android/DAC thực tế
        public boolean hasSink; public int sinkEnc = ENC_UNKNOWN; public int sinkRate, sinkChannels;
        /** Cài đặt của app: yêu cầu ExoPlayer dùng đầu ra float (AudioSettings.hires). Chỉ là cấu hình, không nói AudioTrack thực sự dùng gì. */
        public boolean floatOutputEnabled;
        public boolean dspActive;                                   // chỉ áp dụng khi phát cục bộ
        // Thiết bị Android THỰC SỰ định tuyến (chỉ khi xác định được); nếu không: route = ROUTE_UNKNOWN
        public Route route = Route.ROUTE_UNKNOWN; public String deviceName = "";
        public int[] capRates = new int[0];                         // khả năng của thiết bị đang định tuyến (chỉ khi route đã biết)
        public int capDepth;                                        // độ sâu bit lớn nhất thiết bị công bố (0 = không biết)
        public int mixerRate;                                       // AudioManager PROPERTY_OUTPUT_SAMPLE_RATE (gợi ý)
        // Thiết bị người dùng CHỌN (có thể khác với thiết bị thực sự đang phát)
        public String selectedName = ""; public boolean selectedDisconnected;
        public int[] selCapRates = new int[0]; public int selCapDepth;
        public String btCodec = ""; public boolean btCodecLossy;
        public boolean dopActive;
        public boolean volumeFixed; public int volumeIdx, volumeMax;
        // Kết quả đối chiếu Device Registry / User Device Profile (do DeviceRegistry tính; BitPerfect chỉ đọc)
        public DevLevel devLevel = DevLevel.NONE;
        public Route devConn = Route.ROUTE_UNKNOWN;        // phương thức kết nối ĐÃ xác nhận trong hồ sơ
        public boolean devIsActive;                        // thiết bị trong hồ sơ chính là thiết bị Android đang định tuyến (đã xác định)
        public int[] devRates = new int[0], devBits = new int[0];   // thông số của bản ghi danh mục (không phải của AudioDeviceInfo)
        public boolean devNeedsMaxVolume = true;           // điều kiện bắt buộc của bản ghi
        /** Bằng chứng ĐÁNG TIN CẬY rằng có chuyển đổi tần số (hiện không có nguồn nào đặt true). */
        public boolean rateEvidence;
        /** Bằng chứng đã xác minh toàn đường truyền. Hiện KHÔNG có mã nào đặt true. */
        public boolean pathProof;
        /**
         * 3.0.2: người dùng đã tự kiểm tra ĐÚNG tổ hợp thiết bị + kết nối + định dạng + điều kiện đang phát (User Verified: kết quả "dữ liệu không đổi",
         * phương pháp ít nhất là báo hiệu trên thiết bị, đủ điều kiện) — do DeviceProfiles tính, BitPerfect chỉ đọc. Chỉ có hiệu lực với phiên phát tới thiết bị ngoài (UPnP/Cast),
         * nơi app không thể tự quan sát đường ra; KHÔNG bao giờ lấn át bằng chứng dữ liệu bị đổi.
         */
        public boolean userCfgVerified;
    }

    public static final class Result {
        public Status status = Status.UNKNOWN;
        public Tri resampling = Tri.UNKNOWN;
        public VerLevel level = VerLevel.NONE;
        public final List<Code> changes = new ArrayList<>();
        public final List<Code> confirmed = new ArrayList<>();
        public final List<Code> unverified = new ArrayList<>();
    }

    public static String pcmName(int enc) {
        switch (enc) { case ENC_16: return "PCM 16-bit"; case ENC_24: return "PCM 24-bit"; case ENC_32: return "PCM 32-bit"; case ENC_FLOAT: return "PCM Float"; default: return ""; }
    }

    private static boolean has(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }
    private static boolean remote(Route r) { return r == Route.UPNP || r == Route.CAST; }

    /**
     * Chọn thiết bị đang định tuyến từ danh sách Android báo (getAudioDevicesForAttributes, API 33+). Trả về chỉ số hoặc -1 = không xác định.
     * Chỉ chắc chắn khi: Android báo đúng MỘT thiết bị và (người dùng để mặc định, hoặc thiết bị đã chọn chính là thiết bị đó). Không suy đoán theo tên hay danh sách thiết bị đang kết nối.
     */
    public static int activeIndex(int sdk, boolean userSelected, int selectedId, int[] policyIds) {
        if (sdk < 33 || policyIds == null || policyIds.length != 1) return -1;
        if (!userSelected) return 0;
        return policyIds[0] == selectedId ? 0 : -1;
    }

    public static Result evaluate(Input in) {
        Result r = new Result();
        if (!in.hasTrack) { r.unverified.add(Code.NO_TRACK); r.status = Status.UNKNOWN; return r; }
        boolean rem = remote(in.route);
        boolean rateConv = in.rateEvidence;

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
            if (in.hasSink) {
                // ExoPlayer (DefaultAudioSink) hạ PCM > 16-bit xuống 16-bit khi app KHÔNG yêu cầu đầu ra float: quyết định của chính app, không phải suy đoán về thiết bị
                boolean high = in.sinkEnc == ENC_24 || in.sinkEnc == ENC_32 || in.sinkEnc == ENC_FLOAT;
                if (!in.srcDsd && high && !in.floatOutputEnabled) r.changes.add(Code.DEPTH_REDUCED);
                if (!rateConv && !in.srcDsd) r.confirmed.add(Code.APP_NO_RESAMPLE);
            } else r.unverified.add(Code.NO_SINK_INFO);

            // Chỉ là KHẢ NĂNG / GỢI Ý — không bao giờ là bằng chứng đổi dữ liệu
            if (in.route != Route.ROUTE_UNKNOWN && in.hasSink) {
                if (in.capRates.length > 0) r.unverified.add(has(in.capRates, in.sinkRate) ? Code.CAPABILITY_ONLY : Code.CAPABILITY_MISMATCH_HINT);
                else if (in.capDepth > 0) r.unverified.add(Code.CAPABILITY_ONLY);
                if (in.capDepth > 0 && in.sinkEnc != ENC_UNKNOWN) {
                    int need = in.sinkEnc == ENC_16 ? 16 : 24;
                    if (in.capDepth < need) r.unverified.add(Code.CAPABILITY_MISMATCH_HINT);
                }
                if (in.mixerRate > 0 && in.sinkRate > 0 && in.mixerRate != in.sinkRate) r.unverified.add(Code.MIXER_RATE_HINT);
            } else if (in.route == Route.ROUTE_UNKNOWN && in.hasSink && in.mixerRate > 0 && in.sinkRate > 0 && in.mixerRate != in.sinkRate) r.unverified.add(Code.MIXER_RATE_HINT);

            switch (in.route) {
                case ROUTE_UNKNOWN: r.unverified.add(Code.ROUTE_UNKNOWN); r.unverified.add(Code.PATH_UNVERIFIED); break;
                case BLUETOOTH:
                    if (in.btCodec.isEmpty()) r.unverified.add(Code.BT_CODEC_UNKNOWN);
                    else if (in.btCodecLossy) r.changes.add(Code.BT_LOSSY_CODEC);
                    else r.unverified.add(Code.BT_CODEC_LOSSLESS_UNVERIFIED);
                    break;
                case HDMI: case SPDIF: r.unverified.add(Code.TV_PATH_UNKNOWN); break;
                case USB: r.unverified.add(Code.USB_ACTUAL_UNVERIFIED); break;
                default: r.unverified.add(Code.PATH_UNVERIFIED);
            }
            if (!in.selectedName.isEmpty() && in.route == Route.ROUTE_UNKNOWN) r.unverified.add(Code.SELECTED_NOT_CONFIRMED);
            if (in.selectedDisconnected) r.unverified.add(Code.DEVICE_DISCONNECTED);
            if (!in.volumeFixed && in.volumeMax > 0 && in.volumeIdx < in.volumeMax) r.unverified.add(Code.VOLUME_BELOW_MAX);
            else r.unverified.add(Code.VOLUME_NOT_VERIFIED);
        }
        if (rateConv) r.changes.add(Code.RATE_CONVERSION_EVIDENCE);

        boolean cfgOk = false;
        if (!rem && !in.srcDsd && !in.pathProof && r.changes.isEmpty() && !in.dspActive) cfgOk = configCriteria(in, r);
        // Thiết bị ngoài: chỉ khi người dùng đã tự kiểm tra đúng tổ hợp này và nguồn có thông tin định dạng đầy đủ
        boolean userCfg = rem && in.userCfgVerified && !in.pathProof && r.changes.isEmpty() && (in.srcDsd || (in.srcBits > 0 && in.srcRate > 0));
        r.resampling = rateConv ? Tri.YES : (in.pathProof && r.changes.isEmpty() ? Tri.NO : Tri.UNKNOWN);
        if (!r.changes.isEmpty()) r.status = Status.NOT_BIT_PERFECT;
        else if (in.pathProof) { r.status = Status.VERIFIED; r.level = VerLevel.END_TO_END; }
        else if (cfgOk) { r.status = Status.VERIFIED; r.level = VerLevel.CONFIGURATION; r.confirmed.add(Code.CONFIG_CRITERIA_MET); }
        else if (userCfg) { r.status = Status.VERIFIED; r.level = VerLevel.USER_CONFIGURATION; r.confirmed.add(Code.USER_VERIFIED_CONFIG); }
        else if (!rem && !in.hasSink) r.status = Status.UNKNOWN;
        else r.status = Status.NOT_VERIFIED;
        return r;
    }

    /** Verified – Configuration: mọi điều kiện đều phải đạt; mỗi điều kiện không đạt ghi lý do vào r.unverified. */
    private static boolean configCriteria(Input in, Result r) {
        boolean ok = true;
        if (in.devLevel != DevLevel.REGISTRY_CONFIG && in.devLevel != DevLevel.REGISTRY_E2E) { r.unverified.add(Code.DEV_NOT_VERIFIED); return false; }
        if (in.route == Route.ROUTE_UNKNOWN) ok = false;                       // ROUTE_UNKNOWN đã được ghi
        else if (!in.devIsActive || in.devConn != in.route) { r.unverified.add(Code.DEV_ROUTE_MISMATCH); ok = false; }
        if (!in.hasSink) ok = false;                                           // NO_SINK_INFO đã được ghi
        if (in.srcBits <= 0 || in.srcRate <= 0) { r.unverified.add(Code.SRC_INCOMPLETE); ok = false; }
        if (in.hasSink && in.srcRate > 0 && in.sinkRate != in.srcRate) { r.unverified.add(Code.SOURCE_SINK_RATE_DIFFERS); ok = false; }
        if (in.hasSink && in.srcBits > 0 && in.srcRate > 0) {
            int need = in.srcBits <= 16 ? 16 : in.srcBits <= 24 ? 24 : 32;
            if (!has(in.devRates, in.sinkRate) || !has(in.devBits, need)) { r.unverified.add(Code.DEV_FORMAT_OUTSIDE_REGISTRY); ok = false; }
        }
        if (in.devNeedsMaxVolume && !in.volumeFixed && !(in.volumeMax > 0 && in.volumeIdx >= in.volumeMax)) ok = false;   // VOLUME_BELOW_MAX / NOT_VERIFIED đã được ghi
        return ok;
    }
}
