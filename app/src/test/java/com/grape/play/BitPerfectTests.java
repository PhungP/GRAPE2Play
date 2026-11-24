package com.grape.play;

import static com.grape.play.BitPerfect.Code;
import static com.grape.play.BitPerfect.Route;
import static com.grape.play.BitPerfect.Status;
import static com.grape.play.BitPerfect.Tri;

/** Test phân loại Bit-perfect (BitPerfect.java). Chỉ kiểm LOGIC phân loại, không đo tín hiệu thật. */
final class BitPerfectTests {
    private BitPerfectTests() {}

    static BitPerfect.Input base(Route route, int srcBits, int srcRate, boolean hires) {
        BitPerfect.Input i = new BitPerfect.Input();
        i.hasTrack = true; i.srcExt = "flac"; i.srcBits = srcBits; i.srcRate = srcRate; i.srcChannels = 2;
        i.hasDecoder = true; i.decBits = srcBits; i.decRate = srcRate; i.decFloat = false;
        i.hasOut = true; i.outRate = srcRate; i.outFloat = hires && srcBits > 16; i.outBits = (hires && srcBits > 16) ? 32 : 16;
        i.route = route; i.volumeMax = 15; i.volumeIdx = 15;
        return i;
    }
    static BitPerfect.Result ev(BitPerfect.Input i) { return BitPerfect.evaluate(i); }
    static void st(BitPerfect.Result r, Status s, String n) { AudioCoreTests.check(r.status == s, n + " (got " + r.status + ", want " + s + ")"); }

    static void run() {
        // 1. FLAC 16-bit/44,1 kHz
        BitPerfect.Input a = base(Route.LOCAL_DEFAULT, 16, 44100, false);
        st(ev(a), Status.NOT_VERIFIED, "1. FLAC 16/44.1 phát mặc định, DSP tắt, không biết tần số mixer → NOT_VERIFIED (không VERIFIED)");
        a.mixerRate = 44100; st(ev(a), Status.NOT_VERIFIED, "1b. mixer 44,1 kHz trùng nguồn vẫn chỉ NOT_VERIFIED");
        a.mixerRate = 48000; st(ev(a), Status.NOT_BIT_PERFECT, "1c. Android báo mixer 48 kHz ≠ nguồn 44,1 kHz → NOT_BIT_PERFECT");
        AudioCoreTests.check(ev(a).resampling == Tri.YES, "1c. resampling = YES");
        // 2. FLAC 24-bit/96 kHz
        BitPerfect.Input b = base(Route.LOCAL_DEFAULT, 24, 96000, false);
        BitPerfect.Result rb = ev(b);
        st(rb, Status.NOT_BIT_PERFECT, "2. FLAC 24/96 ở chế độ tương thích (hạ 16-bit) → NOT_BIT_PERFECT");
        AudioCoreTests.check(rb.changes.contains(Code.DEPTH_REDUCED), "2. lý do: hạ độ sâu bit");
        BitPerfect.Input b2 = base(Route.LOCAL_DEFAULT, 24, 96000, true);
        st(ev(b2), Status.NOT_VERIFIED, "2b. FLAC 24/96 yêu cầu float 32-bit: không còn bằng chứng đổi dữ liệu → NOT_VERIFIED");
        // 3. PCM 24-bit/192 kHz trên USB DAC hỗ trợ
        BitPerfect.Input c = base(Route.USB, 24, 192000, true); c.srcExt = "wav";
        c.deviceName = "USB DAC"; c.capRates = new int[]{44100, 48000, 96000, 192000}; c.capEncKnown = true; c.cap16 = true; c.cap24 = true; c.cap32 = true; c.capFloat = true;
        BitPerfect.Result rc = ev(c);
        st(rc, Status.NOT_VERIFIED, "3. PCM 24/192 USB DAC công bố hỗ trợ → NOT_VERIFIED (khả năng ≠ thực tế)");
        AudioCoreTests.check(rc.unverified.contains(Code.CAPABILITY_ONLY) && rc.unverified.contains(Code.USB_ACTUAL_UNVERIFIED), "3. ghi rõ chỉ là khả năng, chưa xác minh thực tế");
        // 4/5. DSP bật / tắt
        BitPerfect.Input d = base(Route.USB, 16, 44100, false); d.capRates = new int[]{44100}; d.dspActive = true;
        st(ev(d), Status.NOT_BIT_PERFECT, "4. DSP bật → NOT_BIT_PERFECT");
        d.dspActive = false;
        AudioCoreTests.check(ev(d).status != Status.NOT_BIT_PERFECT && ev(d).status != Status.VERIFIED, "5. DSP tắt → không NOT_BIT_PERFECT và cũng không VERIFIED");
        AudioCoreTests.check(ev(d).confirmed.contains(Code.DSP_OFF), "5. đã xác minh: DSP tắt");
        // 6/7. USB DAC hỗ trợ / không hỗ trợ định dạng nguồn
        BitPerfect.Input e = base(Route.USB, 24, 96000, true); e.capRates = new int[]{44100, 48000}; e.capEncKnown = true; e.cap16 = true; e.cap24 = true;
        BitPerfect.Result re = ev(e);
        st(re, Status.NOT_BIT_PERFECT, "7. USB DAC không hỗ trợ 96 kHz → NOT_BIT_PERFECT");
        AudioCoreTests.check(re.resampling == Tri.YES && re.changes.contains(Code.DEVICE_RATE_UNSUPPORTED), "7. resampling YES, lý do: tần số không được thiết bị hỗ trợ");
        BitPerfect.Input e2 = base(Route.USB, 24, 96000, true); e2.capRates = new int[]{96000}; e2.capEncKnown = true; e2.cap16 = true;
        st(ev(e2), Status.NOT_BIT_PERFECT, "7b. thiết bị chỉ nhận 16-bit mà nguồn 24-bit (hạ độ sâu) → NOT_BIT_PERFECT");
        BitPerfect.Input e2b = base(Route.USB, 24, 96000, true); e2b.capRates = new int[]{96000}; e2b.capEncKnown = true; e2b.cap16 = true; e2b.cap24 = true;
        st(ev(e2b), Status.NOT_VERIFIED, "7c. thiết bị nhận 24-bit, app gửi float32 của nguồn 24-bit: chuyển float→24-bit không đổi giá trị → không kết luận NOT_BIT_PERFECT");
        BitPerfect.Input e3 = base(Route.USB, 24, 96000, true); e3.capRates = new int[]{96000}; e3.capEncKnown = true; e3.capFloat = true;
        st(ev(e3), Status.NOT_VERIFIED, "6. USB DAC hỗ trợ đúng định dạng → NOT_VERIFIED");
        // 8. UPnP không cung cấp thông tin đầu ra (kể cả DSP đang bật vì không áp dụng khi gửi file)
        BitPerfect.Input f = base(Route.UPNP, 24, 96000, false); f.dspActive = true; f.deviceName = "WiiM Ultra"; f.deviceModel = "WiiM Ultra (bit-perfect, 24-bit/192 kHz)";
        BitPerfect.Result rf = ev(f);
        st(rf, Status.NOT_VERIFIED, "8. UPnP không có thông tin đầu ra → NOT_VERIFIED (tên model không giúp VERIFIED)");
        AudioCoreTests.check(rf.resampling == Tri.UNKNOWN && rf.unverified.contains(Code.RENDERER_NO_OUTPUT_INFO), "8. resampling UNKNOWN");
        AudioCoreTests.check(rf.confirmed.contains(Code.DSP_NOT_APPLIED_REMOTE), "8. đã xác minh: DSP của app không áp dụng khi gửi file");
        // 9. Cast không xác minh được DAC
        BitPerfect.Input g = base(Route.CAST, 16, 44100, false); g.deviceName = "Chromecast Audio";
        BitPerfect.Result rg = ev(g);
        st(rg, Status.NOT_VERIFIED, "9. Google Cast → NOT_VERIFIED");
        AudioCoreTests.check(rg.unverified.contains(Code.CAST_NO_OUTPUT_INFO), "9. lý do: Cast không báo đầu ra");
        // 10. HDMI / Optical không có xác nhận từ TV
        for (Route rt : new Route[]{Route.HDMI, Route.OPTICAL}) {
            BitPerfect.Input h = base(rt, 16, 48000, false);
            BitPerfect.Result rh = ev(h);
            st(rh, Status.NOT_VERIFIED, "10. " + rt + " không có xác nhận từ TV → NOT_VERIFIED");
            AudioCoreTests.check(rh.unverified.contains(Code.TV_PATH_UNKNOWN), "10. " + rt + " ghi rõ không biết TV xử lý gì");
        }
        // 11. Android báo sample rate khác nguồn (thiết bị mặc định/loa)
        BitPerfect.Input k = base(Route.SPEAKER, 16, 44100, false); k.mixerRate = 48000;
        st(ev(k), Status.NOT_BIT_PERFECT, "11. Android báo tần số khác nguồn → NOT_BIT_PERFECT");
        // 12. Bluetooth
        BitPerfect.Input m = base(Route.BLUETOOTH, 16, 44100, false); m.btCodec = "LDAC"; m.btCodecLossy = true;
        st(ev(m), Status.NOT_BIT_PERFECT, "12. Bluetooth codec mất dữ liệu → NOT_BIT_PERFECT");
        m.btCodec = ""; m.btCodecLossy = false;
        BitPerfect.Result rm = ev(m);
        st(rm, Status.NOT_VERIFIED, "12b. Bluetooth chưa biết codec → NOT_VERIFIED (không kết luận NOT_BIT_PERFECT vì thiếu thông tin)");
        AudioCoreTests.check(rm.unverified.contains(Code.BT_CODEC_UNKNOWN), "12b. lý do: không biết codec");
        m.btCodec = "AptX Lossless"; m.btCodecLossy = false;
        st(ev(m), Status.NOT_VERIFIED, "12c. codec tự khai không mất dữ liệu vẫn chỉ NOT_VERIFIED");
        // 13. Thiết bị bị ngắt giữa phiên phát
        BitPerfect.Input u1 = base(Route.USB, 16, 44100, false); u1.capRates = new int[]{44100};
        BitPerfect.Input u2 = base(Route.LOCAL_DEFAULT, 16, 44100, false); u2.deviceDisconnected = true;
        BitPerfect.Result r2 = ev(u2);
        st(ev(u1), Status.NOT_VERIFIED, "13. trước khi ngắt: NOT_VERIFIED");
        st(r2, Status.NOT_VERIFIED, "13. sau khi ngắt: dùng mặc định hệ thống, NOT_VERIFIED");
        AudioCoreTests.check(r2.unverified.contains(Code.DEVICE_DISCONNECTED) && !ev(u1).unverified.contains(Code.DEVICE_DISCONNECTED), "13. trạng thái cập nhật: ghi nhận thiết bị đã ngắt, bỏ khả năng USB cũ");
        AudioCoreTests.check(!r2.unverified.contains(Code.USB_ACTUAL_UNVERIFIED), "13. không còn mô tả USB sau khi ngắt");
        // 14. Đổi đầu ra khi đang phát: local DSP bật → UPnP → BT lossy → mặc định
        BitPerfect.Input p = base(Route.USB, 24, 96000, true); p.capRates = new int[]{96000}; p.dspActive = true;
        st(ev(p), Status.NOT_BIT_PERFECT, "14. USB + DSP bật");
        p.route = Route.UPNP; p.capRates = new int[0];
        st(ev(p), Status.NOT_VERIFIED, "14. đổi sang UPnP: DSP không áp dụng → NOT_VERIFIED");
        p.route = Route.BLUETOOTH; p.btCodec = "SBC"; p.btCodecLossy = true;
        st(ev(p), Status.NOT_BIT_PERFECT, "14. đổi sang Bluetooth SBC");
        p.route = Route.LOCAL_DEFAULT; p.dspActive = false; p.btCodec = ""; p.btCodecLossy = false; p.hasOut = true;
        AudioCoreTests.check(ev(p).status != Status.VERIFIED, "14. về mặc định hệ thống không VERIFIED");
        // DSD
        BitPerfect.Input s = base(Route.LOCAL_DEFAULT, 24, 176400, true); s.srcDsd = true; s.srcExt = "dsf";
        st(ev(s), Status.NOT_BIT_PERFECT, "DSD cục bộ chuyển sang PCM → NOT_BIT_PERFECT");
        BitPerfect.Input s2 = base(Route.UPNP, 1, 2822400, false); s2.srcDsd = true; s2.srcExt = "dsf";
        st(ev(s2), Status.NOT_VERIFIED, "DSD file gốc gửi UPnP → NOT_VERIFIED");
        s2.dopActive = true;
        AudioCoreTests.check(ev(s2).unverified.contains(Code.DOP_RENDERER_UNVERIFIED) && ev(s2).status == Status.NOT_VERIFIED, "DoP tới UPnP → NOT_VERIFIED (thiết bị nhận không xác minh được)");
        // Không có bài / thiếu thông tin định dạng
        BitPerfect.Input n = new BitPerfect.Input();
        st(ev(n), Status.UNKNOWN, "không có bài → UNKNOWN");
        BitPerfect.Input n2 = base(Route.LOCAL_DEFAULT, 16, 44100, false); n2.hasOut = false; n2.hasDecoder = false;
        st(ev(n2), Status.UNKNOWN, "phát cục bộ nhưng chưa có thông tin bộ xuất → UNKNOWN (khác NOT_VERIFIED)");
        // pathProof: chỉ khi có bằng chứng toàn đường truyền
        BitPerfect.Input v = base(Route.USB, 16, 44100, false); v.pathProof = true;
        st(ev(v), Status.VERIFIED, "có pathProof và không có bằng chứng đổi dữ liệu → VERIFIED");
        AudioCoreTests.check(ev(v).resampling == Tri.NO, "VERIFIED → resampling NO");
        v.dspActive = true; st(ev(v), Status.NOT_BIT_PERFECT, "pathProof nhưng DSP bật → vẫn NOT_BIT_PERFECT");

        // Kiểm tra vét cạn: không bao giờ VERIFIED khi chưa có pathProof; NOT_BIT_PERFECT ⇔ có bằng chứng đổi; UNKNOWN/NOT_VERIFIED không bị nhầm
        int bad = 0, total = 0;
        for (Route rt : Route.values()) for (int dsp = 0; dsp < 2; dsp++) for (int hasOut = 0; hasOut < 2; hasOut++) for (int caps = 0; caps < 3; caps++)
            for (int mixer : new int[]{0, 44100, 48000}) for (int bt = 0; bt < 3; bt++) for (int model = 0; model < 2; model++) for (int track = 0; track < 2; track++) for (int proof = 0; proof < 2; proof++) {
                BitPerfect.Input i = base(rt, 24, 96000, dsp == 0); i.hasTrack = track == 1; i.hasOut = hasOut == 1; i.dspActive = dsp == 1; i.mixerRate = mixer; i.pathProof = proof == 1;
                i.deviceModel = model == 1 ? "Model hỗ trợ bit-perfect 32-bit/384 kHz (nhà sản xuất công bố)" : "";
                if (caps == 1) { i.capRates = new int[]{96000}; i.capEncKnown = true; i.cap16 = i.cap24 = i.cap32 = i.capFloat = true; }
                if (caps == 2) { i.capRates = new int[]{44100}; }
                if (bt == 1) { i.btCodec = "SBC"; i.btCodecLossy = true; } else if (bt == 2) { i.btCodec = "X"; i.btCodecLossy = false; }
                BitPerfect.Result r = ev(i); total++;
                boolean ok = true;
                if (r.status == Status.VERIFIED && !(i.pathProof && i.hasTrack && r.changes.isEmpty())) ok = false;          // VERIFIED chỉ với pathProof
                if ((r.status == Status.NOT_BIT_PERFECT) != (i.hasTrack && !r.changes.isEmpty())) ok = false;                 // NBP ⇔ có bằng chứng
                if (i.hasTrack && r.changes.isEmpty() && !i.pathProof && r.status == Status.NOT_BIT_PERFECT) ok = false;
                if (!i.hasTrack && r.status != Status.UNKNOWN) ok = false;
                if (!i.pathProof && r.status == Status.VERIFIED) ok = false;
                if (!ok) bad++;
            }
        AudioCoreTests.check(bad == 0, "vét cạn " + total + " tổ hợp: VERIFIED chỉ khi có pathProof; NOT_BIT_PERFECT chỉ khi có bằng chứng; tên model không ảnh hưởng (lỗi=" + bad + ")");
        AudioCoreTests.info.add("Bit-perfect: đã vét cạn " + total + " tổ hợp đầu vào, trong đó không tổ hợp nào VERIFIED khi pathProof=false.");
    }
}
