package com.grape.play;

import static com.grape.play.BitPerfect.Code;
import static com.grape.play.BitPerfect.Route;
import static com.grape.play.BitPerfect.Status;
import static com.grape.play.BitPerfect.Tri;

import com.grape.play.BitPerfect.DevLevel;

/** Test phân loại Bit-perfect (BitPerfect.java, 2.9.5). Chỉ kiểm LOGIC phân loại, không đo tín hiệu thật. */
final class BitPerfectTests {
    private BitPerfectTests() {}

    static final int[] USB_RATES = {44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000};

    /** srcBits = bits của file; sinkEnc suy từ srcBits (PCM 16/24/32); floatOut = cấu hình app yêu cầu đầu ra float. Route mặc định là ĐÃ biết (route truyền vào). */
    static BitPerfect.Input base(Route route, int srcBits, int srcRate, boolean floatOut) {
        BitPerfect.Input i = new BitPerfect.Input();
        i.hasTrack = true; i.srcExt = "flac"; i.srcBits = srcBits; i.srcRate = srcRate; i.srcChannels = 2;
        i.hasSink = true; i.sinkEnc = srcBits <= 16 ? BitPerfect.ENC_16 : srcBits <= 24 ? BitPerfect.ENC_24 : BitPerfect.ENC_32; i.sinkRate = srcRate; i.sinkChannels = 2;
        i.floatOutputEnabled = floatOut;
        i.route = route; i.volumeMax = 15; i.volumeIdx = 15;
        return i;
    }
    /** Phiên phát USB đã đủ điều kiện Verified – Configuration (bản ghi danh mục + đúng thiết bị đang định tuyến + định dạng trong thông số + âm lượng tối đa). */
    static BitPerfect.Input cfg(int srcBits, int srcRate) {
        BitPerfect.Input i = base(Route.USB, srcBits, srcRate, true);
        i.devLevel = DevLevel.REGISTRY_CONFIG; i.devConn = Route.USB; i.devIsActive = true; i.devRates = USB_RATES; i.devBits = new int[]{16, 24, 32}; i.devNeedsMaxVolume = true;
        return i;
    }
    static BitPerfect.Result ev(BitPerfect.Input i) { return BitPerfect.evaluate(i); }
    static void st(BitPerfect.Result r, Status s, String n) { AudioCoreTests.check(r.status == s, n + " (got " + r.status + ", want " + s + ")"); }
    static boolean u(BitPerfect.Result r, Code c) { return r.unverified.contains(c); }

    static void run() {
        classification(); activeOutput(); pcmFormat(); routes(); configVerification(); userVerified(); exhaustive();
    }

    // ---------- 3.0.2 User Verified cho phiên phát tới thiết bị ngoài ----------
    static void userVerified() {
        BitPerfect.Input c = base(Route.CAST, 24, 96000, true); c.userCfgVerified = true;
        BitPerfect.Result r = ev(c);
        AudioCoreTests.check(r.status == Status.VERIFIED && r.level == BitPerfect.VerLevel.USER_CONFIGURATION && r.confirmed.contains(Code.USER_VERIFIED_CONFIG), "UV1. Cast + người dùng đã tự kiểm tra đúng tổ hợp → VERIFIED – USER-VERIFIED CONFIGURATION");
        BitPerfect.Input n = base(Route.CAST, 24, 96000, true);
        st(ev(n), Status.NOT_VERIFIED, "UV2. Cast không có xác minh của người dùng → NOT_VERIFIED (không đổi)");
        AudioCoreTests.check(ev(n).level == BitPerfect.VerLevel.NONE, "UV2b. mức NONE khi chưa xác minh");
        BitPerfect.Input u = base(Route.UPNP, 16, 44100, false); u.userCfgVerified = true;
        st(ev(u), Status.VERIFIED, "UV3. UPnP + người dùng đã tự kiểm tra → VERIFIED (user)");
        BitPerfect.Input ev = base(Route.CAST, 24, 96000, true); ev.userCfgVerified = true; ev.rateEvidence = true;
        st(ev(ev), Status.NOT_BIT_PERFECT, "UV4. có bằng chứng đổi tần số → NOT_BIT_PERFECT, xác minh của người dùng không lấn át");
        BitPerfect.Input dsd = base(Route.CAST, 1, 2822400, false); dsd.srcDsd = true; dsd.srcExt = "dsf"; dsd.userCfgVerified = true;
        st(ev(dsd), Status.VERIFIED, "UV5. DSD gửi file gốc tới thiết bị ngoài + người dùng đã tự kiểm tra Native → VERIFIED (user)");
        BitPerfect.Input inc = base(Route.CAST, 0, 0, false); inc.userCfgVerified = true; inc.hasSink = false;
        st(ev(inc), Status.NOT_VERIFIED, "UV6. nguồn thiếu thông tin định dạng → không VERIFIED dù người dùng đã xác minh");
        BitPerfect.Input local = cfg(24, 96000); local.devLevel = DevLevel.NONE; local.userCfgVerified = true;
        st(ev(local), Status.NOT_VERIFIED, "UV7. cờ người dùng KHÔNG có hiệu lực với đầu ra cục bộ (USB) — vẫn cần danh mục + định tuyến xác định");
        BitPerfect.Input pp = base(Route.CAST, 24, 96000, true); pp.userCfgVerified = true; pp.pathProof = true;
        AudioCoreTests.check(ev(pp).level == BitPerfect.VerLevel.END_TO_END, "UV8. bằng chứng toàn đường truyền vẫn ưu tiên cao nhất");
        BitPerfect.Input noTrack = new BitPerfect.Input(); noTrack.userCfgVerified = true; noTrack.route = Route.CAST;
        st(ev(noTrack), Status.UNKNOWN, "UV9. chưa phát bài nào → UNKNOWN");
    }

    // ---------- 2.1 Phân loại: khả năng thiết bị ≠ bằng chứng chuyển đổi ----------
    static void classification() {
        // tần số nằm trong danh sách thiết bị công bố
        BitPerfect.Input a = base(Route.USB, 24, 96000, true); a.capRates = new int[]{44100, 96000}; a.capDepth = 24;
        BitPerfect.Result ra = ev(a);
        st(ra, Status.NOT_VERIFIED, "C1. tần số có trong danh sách khả năng → NOT_VERIFIED (không tự động VERIFIED)");
        AudioCoreTests.check(ra.resampling == Tri.UNKNOWN && u(ra, Code.CAPABILITY_ONLY), "C1. resampling UNKNOWN, ghi chỉ là khả năng");
        // tần số KHÔNG nằm trong danh sách: chỉ là gợi ý, không kết luận NOT_BIT_PERFECT, không đặt resampling YES
        BitPerfect.Input b = base(Route.USB, 24, 96000, true); b.capRates = new int[]{44100, 48000}; b.capDepth = 24;
        BitPerfect.Result rb = ev(b);
        st(rb, Status.NOT_VERIFIED, "C2. tần số không có trong danh sách AudioDeviceInfo → NOT_VERIFIED, KHÔNG phải NOT_BIT_PERFECT");
        AudioCoreTests.check(rb.resampling == Tri.UNKNOWN && rb.changes.isEmpty() && u(rb, Code.CAPABILITY_MISMATCH_HINT), "C2. resampling UNKNOWN, chỉ ghi gợi ý chưa khớp");
        // độ sâu bit công bố thấp hơn cần: cũng chỉ là gợi ý
        BitPerfect.Input b2 = base(Route.USB, 24, 96000, true); b2.capRates = new int[]{96000}; b2.capDepth = 16;
        st(ev(b2), Status.NOT_VERIFIED, "C2b. độ sâu bit công bố 16 < 24 → NOT_VERIFIED, không NOT_BIT_PERFECT");
        // thiếu thông tin tần số
        BitPerfect.Input c = base(Route.USB, 24, 96000, true);
        BitPerfect.Result rc = ev(c);
        st(rc, Status.NOT_VERIFIED, "C3. thiếu danh sách tần số → NOT_VERIFIED");
        AudioCoreTests.check(rc.resampling == Tri.UNKNOWN, "C3. resampling UNKNOWN");
        // có bằng chứng resampling đáng tin
        BitPerfect.Input d = base(Route.USB, 24, 96000, true); d.rateEvidence = true;
        BitPerfect.Result rd = ev(d);
        st(rd, Status.NOT_BIT_PERFECT, "C4. có bằng chứng resampling → NOT_BIT_PERFECT");
        AudioCoreTests.check(rd.resampling == Tri.YES && rd.changes.contains(Code.RATE_CONVERSION_EVIDENCE), "C4. resampling YES");
        // không có bằng chứng ≠ chắc chắn không resampling
        BitPerfect.Result re = ev(base(Route.USB, 24, 96000, true));
        AudioCoreTests.check(re.resampling == Tri.UNKNOWN && re.resampling != Tri.NO, "C5. không có bằng chứng → resampling UNKNOWN (không phải NO)");
        // tần số mixer Android báo khác nguồn: chỉ gợi ý
        BitPerfect.Input m = base(Route.SPEAKER, 16, 44100, false); m.mixerRate = 48000;
        BitPerfect.Result rm = ev(m);
        st(rm, Status.NOT_VERIFIED, "C6. Android báo mixer 48 kHz ≠ nguồn 44,1 kHz → NOT_VERIFIED (chỉ gợi ý)");
        AudioCoreTests.check(rm.resampling == Tri.UNKNOWN && u(rm, Code.MIXER_RATE_HINT), "C6. resampling UNKNOWN, ghi gợi ý mixer");
        // DSP
        BitPerfect.Input p = base(Route.USB, 16, 44100, false); p.dspActive = true;
        st(ev(p), Status.NOT_BIT_PERFECT, "C7. DSP bật → NOT_BIT_PERFECT");
        p.dspActive = false;
        AudioCoreTests.check(ev(p).status == Status.NOT_VERIFIED && ev(p).confirmed.contains(Code.DSP_OFF), "C7b. DSP tắt → NOT_VERIFIED (không tự VERIFIED), ghi đã xác minh DSP tắt");
    }

    // ---------- 2.2 Active output ----------
    static void activeOutput() {
        // activeIndex: chỉ chắc khi Android báo đúng một thiết bị và khớp lựa chọn (nếu có)
        AudioCoreTests.eq(BitPerfect.activeIndex(32, false, -1, new int[]{7}), -1, "A1. Android 12 (API<33): không xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(33, false, -1, new int[]{7}), 0, "A2. API 33, mặc định, một thiết bị → xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(33, false, -1, new int[]{7, 8}), -1, "A3. Android báo hai thiết bị → không xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(33, false, -1, new int[0]), -1, "A4. danh sách rỗng → không xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(33, false, -1, null), -1, "A5. không có dữ liệu → không xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(34, true, 9, new int[]{9}), 0, "A6. người dùng chọn thiết bị 9 và Android định tuyến đúng 9 → xác định");
        AudioCoreTests.eq(BitPerfect.activeIndex(34, true, 9, new int[]{4}), -1, "A7. người dùng chọn 9 nhưng Android định tuyến 4 → KHÔNG coi lựa chọn là active");
        AudioCoreTests.eq(BitPerfect.activeIndex(34, true, -1, new int[]{4}), -1, "A8. thiết bị đã chọn không còn kết nối → không xác định");
        // route không rõ: không suy đoán HDMI/Optical/Bluetooth; mọi thông số khả năng bị bỏ qua
        BitPerfect.Input u = base(Route.ROUTE_UNKNOWN, 16, 44100, false);
        u.selectedName = "HDMI Output"; u.selCapRates = new int[]{44100}; u.deviceName = "";
        BitPerfect.Result r = ev(u);
        st(r, Status.NOT_VERIFIED, "A9. System Default – Route Unknown → NOT_VERIFIED");
        AudioCoreTests.check(u(r, Code.ROUTE_UNKNOWN) && u(r, Code.SELECTED_NOT_CONFIRMED), "A9. ghi Route Unknown và thiết bị chọn chưa được xác nhận là active");
        AudioCoreTests.check(!u(r, Code.TV_PATH_UNKNOWN) && !u(r, Code.USB_ACTUAL_UNVERIFIED) && !u(r, Code.BT_CODEC_UNKNOWN), "A9. không mô tả HDMI/USB/Bluetooth khi chưa biết route");
        AudioCoreTests.check(!u(r, Code.CAPABILITY_ONLY) && !u(r, Code.CAPABILITY_MISMATCH_HINT), "A9. không dùng khả năng của thiết bị chưa xác định");
        // Route Unknown không bao giờ VERIFIED dù có bản ghi khớp
        BitPerfect.Input u2 = cfg(24, 96000); u2.route = Route.ROUTE_UNKNOWN; u2.devIsActive = false;
        AudioCoreTests.check(ev(u2).status != Status.VERIFIED, "A10. Route Unknown không VERIFIED dù thiết bị đã xác nhận trong hồ sơ");
        // thiết bị bị ngắt
        BitPerfect.Input dis = base(Route.ROUTE_UNKNOWN, 16, 44100, false); dis.selectedName = "USB DAC"; dis.selectedDisconnected = true;
        BitPerfect.Result rd = ev(dis);
        AudioCoreTests.check(u(rd, Code.DEVICE_DISCONNECTED) && rd.status == Status.NOT_VERIFIED, "A11. thiết bị đã chọn bị ngắt: ghi nhận, NOT_VERIFIED");
    }

    // ---------- 2.3 Định dạng PCM ----------
    static void pcmFormat() {
        AudioCoreTests.check("PCM 16-bit".equals(BitPerfect.pcmName(BitPerfect.ENC_16)) && "PCM 24-bit".equals(BitPerfect.pcmName(BitPerfect.ENC_24))
                && "PCM 32-bit".equals(BitPerfect.pcmName(BitPerfect.ENC_32)) && "PCM Float".equals(BitPerfect.pcmName(BitPerfect.ENC_FLOAT)) && BitPerfect.pcmName(BitPerfect.ENC_UNKNOWN).isEmpty(),
                "P1. tên hiển thị đúng 16/24/32/Float, không có dữ liệu → rỗng");
        // PCM 16-bit: hires bật cũng KHÔNG biến thành float
        BitPerfect.Input a = base(Route.USB, 16, 44100, true);
        AudioCoreTests.check(a.sinkEnc == BitPerfect.ENC_16 && ev(a).changes.isEmpty(), "P2. sink 16-bit, hires bật: vẫn là PCM 16-bit, không có thay đổi dữ liệu");
        // 24-bit với/không float
        BitPerfect.Input b = base(Route.USB, 24, 96000, false);
        AudioCoreTests.check(ev(b).changes.contains(Code.DEPTH_REDUCED), "P3. sink 24-bit, app không bật float → app hạ 16-bit (bằng chứng do app quyết định)");
        b.floatOutputEnabled = true;
        AudioCoreTests.check(ev(b).changes.isEmpty(), "P3b. sink 24-bit, app bật float → không có thay đổi do app");
        // 32-bit số nguyên và Float
        BitPerfect.Input c = base(Route.USB, 32, 96000, false);
        AudioCoreTests.check(c.sinkEnc == BitPerfect.ENC_32 && ev(c).changes.contains(Code.DEPTH_REDUCED), "P4. sink PCM 32-bit, không float → hạ 16-bit");
        BitPerfect.Input d = base(Route.USB, 24, 96000, false); d.sinkEnc = BitPerfect.ENC_FLOAT;
        AudioCoreTests.check(ev(d).changes.contains(Code.DEPTH_REDUCED), "P5. sink Float, không bật float → hạ 16-bit");
        d.floatOutputEnabled = true;
        AudioCoreTests.check(ev(d).changes.isEmpty(), "P5b. sink Float, bật float → không đổi");
        // encoding không rõ: không kết luận gì về độ sâu, trạng thái UNKNOWN khi không có sink
        BitPerfect.Input e = base(Route.USB, 24, 96000, false); e.hasSink = false; e.sinkEnc = BitPerfect.ENC_UNKNOWN;
        BitPerfect.Result re = ev(e);
        AudioCoreTests.check(re.status == Status.UNKNOWN && re.changes.isEmpty() && u(re, Code.NO_SINK_INFO), "P6. không có Playback.sinkFmt → UNKNOWN, không suy luận encoding");
    }

    // ---------- Các tuyến ----------
    static void routes() {
        // USB DAC (route đã xác định, chưa có hồ sơ)
        BitPerfect.Result usb = ev(base(Route.USB, 24, 192000, true));
        AudioCoreTests.check(usb.status == Status.NOT_VERIFIED && u(usb, Code.USB_ACTUAL_UNVERIFIED) && u(usb, Code.DEV_NOT_VERIFIED), "R1. USB DAC chưa có hồ sơ xác minh → NOT_VERIFIED");
        for (Route rt : new Route[]{Route.HDMI, Route.SPDIF}) {
            BitPerfect.Result rh = ev(base(rt, 16, 48000, false));
            st(rh, Status.NOT_VERIFIED, "R2. " + rt + " → NOT_VERIFIED");
            AudioCoreTests.check(u(rh, Code.TV_PATH_UNKNOWN), "R2. " + rt + " ghi rõ không biết TV/ampli xử lý gì");
        }
        // Bluetooth
        BitPerfect.Input m = base(Route.BLUETOOTH, 16, 44100, false); m.btCodec = "LDAC"; m.btCodecLossy = true;
        st(ev(m), Status.NOT_BIT_PERFECT, "R3. Bluetooth codec mất dữ liệu đã biết → NOT_BIT_PERFECT");
        m.btCodec = ""; m.btCodecLossy = false;
        AudioCoreTests.check(ev(m).status == Status.NOT_VERIFIED && u(ev(m), Code.BT_CODEC_UNKNOWN), "R3b. Bluetooth chưa biết codec → NOT_VERIFIED");
        m.btCodec = "AptX Lossless";
        st(ev(m), Status.NOT_VERIFIED, "R3c. codec tự khai lossless vẫn chỉ NOT_VERIFIED");
        // UPnP / Cast (kể cả DSP bật)
        BitPerfect.Input f = base(Route.UPNP, 24, 96000, false); f.dspActive = true; f.deviceName = "WiiM Ultra";
        BitPerfect.Result rf = ev(f);
        AudioCoreTests.check(rf.status == Status.NOT_VERIFIED && rf.resampling == Tri.UNKNOWN && u(rf, Code.RENDERER_NO_OUTPUT_INFO) && rf.confirmed.contains(Code.DSP_NOT_APPLIED_REMOTE), "R4. UPnP/DLNA → NOT_VERIFIED, DSP app không áp dụng");
        BitPerfect.Input g = base(Route.CAST, 16, 44100, false);
        AudioCoreTests.check(ev(g).status == Status.NOT_VERIFIED && u(ev(g), Code.CAST_NO_OUTPUT_INFO), "R5. Google Cast → NOT_VERIFIED");
        // Remote không bao giờ VERIFIED – Configuration dù hồ sơ khớp
        BitPerfect.Input h = cfg(24, 96000); h.route = Route.UPNP; h.devConn = Route.UPNP;
        AudioCoreTests.check(ev(h).status == Status.NOT_VERIFIED, "R6. UPnP không đạt Verified – Configuration (không có thông tin đầu ra)");
        // DSD
        BitPerfect.Input s = base(Route.USB, 24, 176400, true); s.srcDsd = true; s.srcExt = "dsf";
        st(ev(s), Status.NOT_BIT_PERFECT, "R7. DSD cục bộ chuyển sang PCM → NOT_BIT_PERFECT");
        BitPerfect.Input s2 = base(Route.UPNP, 1, 2822400, false); s2.srcDsd = true; s2.srcExt = "dsf"; s2.dopActive = true;
        AudioCoreTests.check(ev(s2).status == Status.NOT_VERIFIED && u(ev(s2), Code.DOP_RENDERER_UNVERIFIED), "R8. DoP tới UPnP → NOT_VERIFIED");
        // không có bài
        st(ev(new BitPerfect.Input()), Status.UNKNOWN, "R9. không có bài → UNKNOWN");
    }

    // ---------- Verified – Configuration (4.2/4.3) ----------
    static void configVerification() {
        BitPerfect.Result r = ev(cfg(24, 96000));
        AudioCoreTests.check(r.status == Status.VERIFIED && r.level == BitPerfect.VerLevel.CONFIGURATION, "V1. đủ điều kiện → VERIFIED mức CONFIGURATION");
        AudioCoreTests.check(r.resampling == Tri.UNKNOWN, "V1. Verified – Configuration KHÔNG khẳng định resampling = No (UNKNOWN)");
        AudioCoreTests.check(r.level != BitPerfect.VerLevel.END_TO_END, "V1. không quảng bá như phép đo toàn tuyến");
        BitPerfect.Input p = cfg(24, 96000); p.pathProof = true;
        AudioCoreTests.check(ev(p).level == BitPerfect.VerLevel.END_TO_END && ev(p).resampling == Tri.NO, "V2. có pathProof → END_TO_END");
        BitPerfect.Input x;
        x = cfg(24, 96000); x.dspActive = true; st(ev(x), Status.NOT_BIT_PERFECT, "V3. DSP bật → NOT_BIT_PERFECT");
        x = cfg(24, 96000); x.rateEvidence = true; st(ev(x), Status.NOT_BIT_PERFECT, "V4. có bằng chứng resampling → NOT_BIT_PERFECT (mâu thuẫn với Verified)");
        x = cfg(24, 96000); x.floatOutputEnabled = false; st(ev(x), Status.NOT_BIT_PERFECT, "V5. app hạ 16-bit → NOT_BIT_PERFECT");
        x = cfg(24, 96000); x.devLevel = DevLevel.USER_DECLARED; st(ev(x), Status.NOT_VERIFIED, "V6. hồ sơ tự khai báo không cấp Verified");
        x = cfg(24, 96000); x.devLevel = DevLevel.REGISTRY_UNVERIFIED; st(ev(x), Status.NOT_VERIFIED, "V7. bản ghi 'chưa có căn cứ' không cấp Verified");
        x = cfg(24, 96000); x.devLevel = DevLevel.NONE; st(ev(x), Status.NOT_VERIFIED, "V8. chưa có hồ sơ → NOT_VERIFIED");
        x = cfg(24, 96000); x.devIsActive = false; st(ev(x), Status.NOT_VERIFIED, "V9. thiết bị trong hồ sơ không phải thiết bị Android đang định tuyến → NOT_VERIFIED");
        x = cfg(24, 96000); x.route = Route.SPDIF; st(ev(x), Status.NOT_VERIFIED, "V10. hồ sơ xác nhận USB nhưng đang phát qua S/PDIF → không suy rộng phương thức");
        AudioCoreTests.check(u(ev(x), Code.DEV_ROUTE_MISMATCH), "V10. ghi lý do khác phương thức kết nối");
        x = cfg(24, 96000); x.sinkRate = 48000; st(ev(x), Status.NOT_VERIFIED, "V11. tần số đầu vào sink khác nguồn → không Verified (nhưng không NOT_BIT_PERFECT vì chưa đủ bằng chứng)");
        x = cfg(24, 705600); x.sinkRate = 705600; st(ev(x), Status.NOT_VERIFIED, "V12. tần số ngoài thông số bản ghi → không Verified");
        AudioCoreTests.check(u(ev(x), Code.DEV_FORMAT_OUTSIDE_REGISTRY), "V12. ghi lý do ngoài thông số");
        x = cfg(24, 96000); x.volumeIdx = 10; st(ev(x), Status.NOT_VERIFIED, "V13. âm lượng hệ thống dưới tối đa → không Verified");
        x = cfg(24, 96000); x.volumeMax = 0; st(ev(x), Status.NOT_VERIFIED, "V13b. không đọc được âm lượng → không Verified");
        x = cfg(24, 96000); x.volumeIdx = 3; x.volumeFixed = true; st(ev(x), Status.VERIFIED, "V13c. âm lượng cố định (volumeFixed) → điều kiện đạt");
        x = cfg(24, 96000); x.srcBits = 0; st(ev(x), Status.NOT_VERIFIED, "V14. thiếu độ sâu bit nguồn → không Verified");
        x = cfg(24, 96000); x.hasSink = false; st(ev(x), Status.UNKNOWN, "V15. chưa có định dạng sink → UNKNOWN");
        x = cfg(16, 44100); st(ev(x), Status.VERIFIED, "V16. 16/44,1 trong thông số → VERIFIED (Configuration)");
        x = cfg(24, 96000); x.mixerRate = 48000; st(ev(x), Status.VERIFIED, "V17. tần số mixer chính Android báo chỉ là gợi ý, không phủ nhận Verified – Configuration");
        x = cfg(24, 96000); x.selectedName = "Khác"; x.selectedDisconnected = true; st(ev(x), Status.VERIFIED, "V18. thông tin 'thiết bị người dùng chọn' không làm đổi kết quả của thiết bị đang định tuyến");
    }

    // ---------- Vét cạn ----------
    static void exhaustive() {
        int bad = 0, total = 0;
        DevLevel[] lv = DevLevel.values();
        for (Route rt : Route.values()) for (int dsp = 0; dsp < 2; dsp++) for (int hasSink = 0; hasSink < 2; hasSink++) for (int caps = 0; caps < 3; caps++)
            for (int mixer : new int[]{0, 44100, 48000}) for (int bt = 0; bt < 3; bt++) for (int track = 0; track < 2; track++) for (int proof = 0; proof < 2; proof++)
            for (int rev = 0; rev < 2; rev++) for (DevLevel dl : lv) for (int act = 0; act < 2; act++) for (int vol = 0; vol < 2; vol++) for (int flt = 0; flt < 2; flt++) {
                BitPerfect.Input i = cfg(24, 96000); i.route = rt; i.floatOutputEnabled = flt == 0; i.hasTrack = track == 1; i.hasSink = hasSink == 1; i.dspActive = dsp == 1; i.mixerRate = mixer;
                i.pathProof = proof == 1; i.rateEvidence = rev == 1; i.devLevel = dl; i.devIsActive = act == 1; i.volumeIdx = vol == 1 ? 15 : 4;
                if (caps == 1) { i.capRates = new int[]{96000}; i.capDepth = 32; } else if (caps == 2) { i.capRates = new int[]{44100}; i.capDepth = 16; }
                if (bt == 1) { i.btCodec = "SBC"; i.btCodecLossy = true; } else if (bt == 2) { i.btCodec = "X"; }
                BitPerfect.Result r = ev(i); total++;
                boolean ok = true;
                if ((r.status == Status.NOT_BIT_PERFECT) != (i.hasTrack && !r.changes.isEmpty())) ok = false;                 // NBP ⇔ có bằng chứng
                if (!i.hasTrack && r.status != Status.UNKNOWN) ok = false;
                if (r.status == Status.VERIFIED && !(i.pathProof || r.level == BitPerfect.VerLevel.CONFIGURATION)) ok = false;
                if (r.level == BitPerfect.VerLevel.CONFIGURATION) {
                    boolean good = (dl == DevLevel.REGISTRY_CONFIG || dl == DevLevel.REGISTRY_E2E) && i.route == Route.USB && i.hasSink && i.devIsActive && i.volumeIdx >= i.volumeMax
                            && !i.dspActive && !i.rateEvidence && i.floatOutputEnabled && r.changes.isEmpty() && i.hasTrack;
                    if (!good) ok = false;
                }
                if (i.route == Route.ROUTE_UNKNOWN && r.status == Status.VERIFIED && !i.pathProof) ok = false;                // Route Unknown không bao giờ cấu hình-Verified
                if ((r.resampling == Tri.YES) != (i.hasTrack && i.rateEvidence)) ok = false;                                  // YES ⇔ bằng chứng đáng tin
                if (r.resampling == Tri.NO && !i.pathProof) ok = false;
                // khả năng/gợi ý không tạo bằng chứng: bỏ chúng đi thì tập bằng chứng không đổi
                BitPerfect.Input j = cfg(24, 96000); j.route = rt; j.floatOutputEnabled = i.floatOutputEnabled; j.hasTrack = i.hasTrack; j.hasSink = i.hasSink; j.dspActive = i.dspActive; j.rateEvidence = i.rateEvidence;
                j.btCodec = i.btCodec; j.btCodecLossy = i.btCodecLossy;
                if (!ev(j).changes.equals(r.changes)) ok = false;
                if (!ok) bad++;
            }
        AudioCoreTests.check(bad == 0, "vét cạn " + total + " tổ hợp: NOT_BIT_PERFECT ⇔ có bằng chứng; VERIFIED chỉ khi pathProof hoặc đủ điều kiện cấu hình; khả năng/mixer/âm lượng/hồ sơ không tạo bằng chứng (lỗi=" + bad + ")");
        AudioCoreTests.info.add("Bit-perfect: đã vét cạn " + total + " tổ hợp đầu vào (route, DSP, sink, khả năng, mixer, codec BT, bằng chứng, mức hồ sơ, active, âm lượng, float).");
    }
}
