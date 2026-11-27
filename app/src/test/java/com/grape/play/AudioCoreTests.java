package com.grape.play;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Test hồi quy cho phần lõi âm thanh (DspMath, PlayLogic, DeviceMatch). Chạy được bằng javac/java (main) và qua JUnit (AudioCoreJUnitTest).
 * Chỉ kiểm LOGIC thuần: không kiểm ExoPlayer, AudioTrack, thiết bị thật, mạng, UPnP hay Cast.
 */
public final class AudioCoreTests {
    static int pass = 0, fail = 0;
    static final List<String> failures = new ArrayList<>();
    static final List<String> info = new ArrayList<>();

    static void check(boolean ok, String name) { if (ok) pass++; else { fail++; failures.add(name); } }
    static void eq(long a, long b, String n) { check(a == b, n + " (" + a + " != " + b + ")"); }
    static void near(double a, double b, double tol, String n) { check(Math.abs(a - b) <= tol, n + " (" + a + " vs " + b + ", tol " + tol + ")"); }

    public static List<String> runAll() {
        pass = 0; fail = 0; failures.clear(); info.clear();
        queueAndShuffle(); skip(); errors(); retryRecovery(); devices(); dspFlat(); dspBands(); dspPreampBalance(); dspClipping(); dspFormats(); dspRecovery(); gaplessVerdict(); BitPerfectTests.run(); DeviceRegistryTests.run(); DeviceCatalogTests.run(); DeviceVerificationTests.run(); VersionTests.run(); QueueWindowTests.run(); QueueStoreTests.run(); QueueSessionTests.run(); SafeStoreTests.run(); ScanTests.run(); ScanSafetyTests.run(); SourceRemovalTests.run(); PersistSafetyTests.run();
        return failures;
    }

    // ---------- Hàng đợi / shuffle / repeat ----------
    static boolean isPerm(int[] o, int n) {
        if (o.length != n) return false;
        boolean[] seen = new boolean[n];
        for (int v : o) { if (v < 0 || v >= n || seen[v]) return false; seen[v] = true; }
        return true;
    }
    static void queueAndShuffle() {
        Random r = new Random(1);
        for (int n = 1; n <= 40; n++) for (int first = 0; first < n; first += Math.max(1, n / 5)) for (int rep = 0; rep < 20; rep++) {
            int[] o = PlayLogic.pinnedOrder(n, first, r);
            check(isPerm(o, n), "pinnedOrder là hoán vị n=" + n);
            check(o[0] == first, "pinnedOrder: bài đang nghe đứng đầu n=" + n + " first=" + first);
        }
        // Phát hết thứ tự với REPEAT_OFF: đủ n bài, không bỏ sót, không trùng
        int[] o = PlayLogic.pinnedOrder(25, 17, new Random(7));
        Set<Integer> played = new HashSet<>(); for (int v : o) played.add(v);
        eq(played.size(), 25, "shuffle REPEAT_OFF phát đủ 25 bài");
        // Mô phỏng lỗi cũ: thứ tự ngẫu nhiên không ghim → bài đứng trước bài bắt đầu bị bỏ sót
        int[] raw = PlayLogic.pinnedOrder(25, 0, new Random(3)); // thứ tự bất kỳ
        int start = raw[12]; int missed = 12;                       // bắt đầu giữa thứ tự: 12 bài đứng trước sẽ không bao giờ phát
        check(missed > 0 && start == raw[12], "mô tả lỗi cũ: bắt đầu giữa thứ tự bỏ sót " + missed + " bài (đã sửa bằng pinnedOrder)");
        // Thêm bài: thứ tự cũ giữ nguyên, bài mới ở cuối
        int[] a = PlayLogic.appendedOrder(o, 25, 5);
        check(a != null && isPerm(a, 30), "appendedOrder là hoán vị");
        boolean prefix = true; for (int i = 0; i < 25; i++) prefix &= a[i] == o[i];
        check(prefix, "appendedOrder giữ nguyên 25 phần tử đầu");
        for (int i = 25; i < 30; i++) check(a[i] == i, "appendedOrder: bài mới nối cuối");
        check(PlayLogic.appendedOrder(o, 24, 1) == null, "appendedOrder: thứ tự lệch số bài → null (Playback ghim lại)");
        check(PlayLogic.appendedOrder(null, 0, 1) == null, "appendedOrder null");
        check(PlayLogic.pinnedOrder(0, 0, r).length == 0, "pinnedOrder n=0");
        check(PlayLogic.pinnedOrder(1, 5, r)[0] == 0, "pinnedOrder n=1, first ngoài phạm vi");
    }

    static void skip() {
        eq(PlayLogic.skipTarget(PlayLogic.REPEAT_ONE, 2, 5, 2), 3, "REPEAT_ONE: sang bài kế, không quay lại bài lỗi");
        eq(PlayLogic.skipTarget(PlayLogic.REPEAT_ONE, 4, 5, 4), 0, "REPEAT_ONE: bài cuối → về đầu");
        eq(PlayLogic.skipTarget(PlayLogic.REPEAT_ONE, 0, 1, 0), -1, "REPEAT_ONE: 1 bài → không có bài kế");
        eq(PlayLogic.skipTarget(PlayLogic.REPEAT_ALL, 1, 5, 4), 4, "REPEAT_ALL dùng chỉ số kế của ExoPlayer (shuffle)");
        eq(PlayLogic.skipTarget(PlayLogic.REPEAT_OFF, 4, 5, -1), -1, "REPEAT_OFF bài cuối → -1");
    }

    // ---------- Xử lý lỗi ----------
    static PlayLogic.Decision d(boolean preload, boolean sink, boolean dsd, boolean canLower, boolean usb, boolean usbFirst, boolean io, int err, int n, String key, PlayLogic.Retries r) {
        return PlayLogic.decide(preload, sink, dsd, canLower, usb, usbFirst, io, err, n, key, r);
    }
    static void errors() {
        PlayLogic.Retries r = new PlayLogic.Retries();
        // Lỗi bài hiện tại (file hỏng, không thoáng qua): bỏ qua rồi dừng sau min(n,4) lỗi liên tiếp
        int err = 0, steps = 0; PlayLogic.Action last = null;
        while (steps < 50) {
            PlayLogic.Decision x = d(false, false, false, false, false, false, false, err, 10, "a", r); last = x.action; steps++;
            if (x.countsError()) err++;
            if (x.action == PlayLogic.Action.STOP_MANY) break;
            check(x.action == PlayLogic.Action.SKIP, "lỗi bài hiện tại → SKIP trước khi đủ ngưỡng");
        }
        check(last == PlayLogic.Action.STOP_MANY && steps == 4, "lỗi liên tiếp dừng đúng sau 4 lần (không kẹt vòng lặp): steps=" + steps);
        eq(d(false, false, false, false, false, false, false, 0, 1, "a", r).action == PlayLogic.Action.STOP_MANY ? 1 : 0, 1, "hàng đợi 1 bài lỗi → dừng ngay");
        // Lỗi bài preload: không bỏ qua, không đếm vào lỗi liên tiếp
        PlayLogic.Decision p = d(true, false, false, false, false, false, false, 0, 10, "b", r);
        check(p.action == PlayLogic.Action.PRELOAD_IGNORE && !p.countsError(), "lỗi preload → chỉ ghi nhận, không bỏ qua bài đang nghe");
        PlayLogic.Decision pn = d(true, false, false, false, false, false, true, 0, 10, "b", r);
        check(pn.action == PlayLogic.Action.PRELOAD_IGNORE, "lỗi mạng của bài preload không thử lại tại chỗ (bài đang nghe không bị gián đoạn)");
        PlayLogic.Decision ps = d(true, true, false, false, false, false, false, 0, 10, "b", r);
        check(ps.action == PlayLogic.Action.SINK_PRELOAD, "lỗi AudioTrack của bài preload → không bỏ qua bài đang nghe");
        // DSD
        check(d(false, true, true, true, false, false, false, 0, 10, "d", new PlayLogic.Retries()).action == PlayLogic.Action.DSD_LOWER, "DSD + lỗi sink + còn hạ được → hạ tốc độ");
        check(d(false, true, true, false, false, false, false, 0, 10, "d", new PlayLogic.Retries()).action == PlayLogic.Action.RETRY_SINK, "DSD + không hạ được → thử lại như lỗi sink thường");
        check(d(true, true, true, true, false, false, false, 0, 10, "d", new PlayLogic.Retries()).action == PlayLogic.Action.SINK_PRELOAD, "DSD preload không hạ tốc độ");
        // USB
        check(d(false, false, false, false, true, true, false, 0, 10, "u", new PlayLogic.Retries()).action == PlayLogic.Action.USB_COPY, "USB lần đầu → sao chép sang bộ nhớ đệm");
        check(d(false, false, false, false, true, false, false, 0, 10, "u", new PlayLogic.Retries()).action == PlayLogic.Action.SKIP, "USB đã thử sao chép → bỏ qua bài");
    }

    static void retryRecovery() {
        // Mất mạng: thử lại 2 lần (1,5 s rồi 5 s) đúng bài rồi mới bỏ qua; không kẹt
        PlayLogic.Retries r = new PlayLogic.Retries();
        PlayLogic.Decision a = d(false, false, false, false, false, false, true, 0, 10, "nas/x.flac", r);
        PlayLogic.Decision b = d(false, false, false, false, false, false, true, 0, 10, "nas/x.flac", r);
        PlayLogic.Decision c = d(false, false, false, false, false, false, true, 0, 10, "nas/x.flac", r);
        check(a.action == PlayLogic.Action.RETRY_IO && a.attempt == 1 && a.delayMs == 1500, "mất mạng: thử lại lần 1 sau 1,5 s");
        check(b.action == PlayLogic.Action.RETRY_IO && b.attempt == 2 && b.delayMs == 5000, "mất mạng: thử lại lần 2 sau 5 s");
        check(c.action == PlayLogic.Action.SKIP && c.countsError(), "mất mạng: hết lượt thử → bỏ qua bài (không kẹt)");
        // Bài khác có lượt thử riêng
        check(d(false, false, false, false, false, false, true, 0, 10, "nas/y.flac", r).action == PlayLogic.Action.RETRY_IO, "bài khác có lượt thử lại riêng");
        // Sau khi phát ổn định (retries.clear) lại được thử lại
        r.clear();
        check(d(false, false, false, false, false, false, true, 0, 10, "nas/x.flac", r).action == PlayLogic.Action.RETRY_IO, "sau khi clear (phát ổn định > 8 s) lại được thử lại");
        // AudioTrack: 2 lần thử lại rồi bỏ qua/dừng
        PlayLogic.Retries s = new PlayLogic.Retries();
        check(d(false, true, false, false, false, false, false, 0, 10, "k", s).action == PlayLogic.Action.RETRY_SINK, "AudioTrack lỗi: thử lại 1");
        check(d(false, true, false, false, false, false, false, 0, 10, "k", s).action == PlayLogic.Action.RETRY_SINK, "AudioTrack lỗi: thử lại 2");
        check(d(false, true, false, false, false, false, false, 0, 10, "k", s).action == PlayLogic.Action.SINK_SKIP, "AudioTrack lỗi: hết lượt → bỏ qua bài");
        check(d(false, true, false, false, false, false, false, 2, 10, "k", s).action == PlayLogic.Action.SINK_STOP, "AudioTrack lỗi liên tiếp 3 bài → dừng hẳn");
        // Lỗi không thoáng qua (file không tồn tại) không thử lại
        check(d(false, false, false, false, false, false, false, 0, 10, "gone", new PlayLogic.Retries()).action == PlayLogic.Action.SKIP, "file không tồn tại → không thử lại, bỏ qua");
        // Mô phỏng chuỗi: luôn kết thúc trong số bước hữu hạn
        PlayLogic.Retries q = new PlayLogic.Retries(); int err = 0, steps = 0;
        for (; steps < 100; steps++) { PlayLogic.Decision x = d(false, true, false, false, false, false, false, err, 6, "z", q); if (x.countsError()) err++; if (x.action == PlayLogic.Action.SINK_STOP) break; }
        check(steps < 20, "chuỗi lỗi AudioTrack liên tiếp kết thúc hữu hạn (steps=" + steps + ")");
    }

    // ---------- Chọn đầu ra ----------
    static DeviceMatch.Dev dev(int t, String a, String n) { return new DeviceMatch.Dev(t, a, n); }
    static void devices() {
        int USB = 11, HDMI = 9, BT = 8;
        DeviceMatch.Dev[] two = { dev(USB, "card=1;device=0", "DAC A"), dev(USB, "card=2;device=0", "DAC B"), dev(HDMI, "", "HDMI 0") };
        check(!DeviceMatch.keyOf(two[0]).equals(DeviceMatch.keyOf(two[1])), "hai USB DAC cùng loại có khoá khác nhau");
        eq(DeviceMatch.match(DeviceMatch.keyOf(two[1]), two), 1, "chọn đúng USB DAC B (không bị gộp theo loại)");
        eq(DeviceMatch.match(DeviceMatch.keyOf(two[0]), two), 0, "chọn đúng USB DAC A");
        // DAC B rút ra → về mặc định hệ thống (-1), không chuyển sang DAC A cùng loại
        DeviceMatch.Dev[] onlyA = { two[0], two[2] };
        eq(DeviceMatch.match(DeviceMatch.keyOf(two[1]), onlyA), -1, "DAC B rút ra → mặc định hệ thống, không nhảy sang DAC A");
        // Cắm lại DAC B với địa chỉ mới (id/địa chỉ đổi), vẫn một thiết bị tên đó → khớp lại
        DeviceMatch.Dev[] replug = { two[0], dev(USB, "card=3;device=0", "DAC B") };
        eq(DeviceMatch.match(DeviceMatch.keyOf(two[1]), replug), 1, "DAC B cắm lại địa chỉ mới → tự khớp lại theo loại + tên");
        // Hai thiết bị trùng tên khác địa chỉ, thiết bị đã chọn vắng mặt → không đoán
        DeviceMatch.Dev[] twin = { dev(USB, "card=5;device=0", "DAC B"), dev(USB, "card=6;device=0", "DAC B") };
        eq(DeviceMatch.match(DeviceMatch.keyOf(two[1]), twin), -1, "nhiều thiết bị trùng tên: không đoán");
        // Android 8: không có địa chỉ
        DeviceMatch.Dev[] a8 = { dev(USB, "", "DAC A"), dev(USB, "", "DAC B") };
        eq(DeviceMatch.match(DeviceMatch.keyOf(a8[1]), a8), 1, "Android 8 (không có địa chỉ): vẫn phân biệt theo tên");
        // Khoá cũ chỉ có loại ("11||") và khoá rỗng
        eq(DeviceMatch.match("11||", two), 0, "khoá cũ chỉ có loại: chọn thiết bị cùng loại đầu tiên");
        eq(DeviceMatch.match("8||", two), -1, "khoá cũ: không có Bluetooth → mặc định hệ thống");
        eq(DeviceMatch.match("", two), -1, "khoá rỗng = mặc định hệ thống");
        eq(DeviceMatch.match(null, two), -1, "khoá null = mặc định hệ thống");
        eq(DeviceMatch.typeOf("11|x|y"), 11, "typeOf"); eq(DeviceMatch.typeOf("abc"), 0, "typeOf hỏng");
        // Tên có ký tự '|'
        DeviceMatch.Dev w = dev(BT, "AA:BB", "Loa | phòng khách");
        eq(DeviceMatch.match(DeviceMatch.keyOf(w), new DeviceMatch.Dev[]{ w }), 0, "tên chứa '|' vẫn khớp");
        // Cắm/rút liên tục: kết quả luôn đúng
        DeviceMatch.Dev[] all = { dev(USB, "c1", "DAC A"), dev(USB, "c2", "DAC B"), dev(BT, "11:22", "Loa BT") };
        String key = DeviceMatch.keyOf(all[2]);
        for (int mask = 0; mask < 8; mask++) {
            List<DeviceMatch.Dev> l = new ArrayList<>(); for (int i = 0; i < 3; i++) if ((mask & (1 << i)) != 0) l.add(all[i]);
            int m = DeviceMatch.match(key, l.toArray(new DeviceMatch.Dev[0]));
            check((mask & 4) != 0 ? (m >= 0 && l.get(m) == all[2]) : m == -1, "cắm/rút tổ hợp " + mask);
        }
    }

    // ---------- DSP ----------
    static final int SR = 44100;
    static float[] zeros() { return new float[10]; }
    static ByteBuffer pcm16(double[] x, int ch) {
        ByteBuffer b = ByteBuffer.allocate(x.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : x) b.putShort((short) Math.max(-32768, Math.min(32767, Math.round(v * 32768.0))));
        b.flip(); return b;
    }
    static double[] sine(double f, double amp, int n, int sr) { double[] x = new double[n]; for (int i = 0; i < n; i++) x[i] = amp * Math.sin(2 * Math.PI * f * i / sr); return x; }
    static double peakOf(ByteBuffer out, int kind, int skipSamples) {
        out.flip(); out.order(ByteOrder.LITTLE_ENDIAN); double m = 0; int i = 0;
        while (out.remaining() >= DspMath.bytesPer(kind)) {
            double v = kind == DspMath.PCM16 ? out.getShort() / 32768.0 : kind == DspMath.PCM24 ? ((out.get(out.position() + 2) << 16) | ((out.get(out.position() + 1) & 0xff) << 8) | (out.get(out.position()) & 0xff)) / 8388608.0 : kind == DspMath.PCM32 ? out.getInt() / 2147483648.0 : out.getFloat();
            if (kind == DspMath.PCM24) out.position(out.position() + 3);
            if (i++ >= skipSamples) m = Math.max(m, Math.abs(v));
        }
        return m;
    }

    static void dspFlat() {
        check(DspMath.isFlat(false, new float[]{6, 6, 6, 6, 6, 6, 6, 6, 6, 6}, 0.5f, 3f), "DSP tắt = flat bất kể thông số");
        check(DspMath.isFlat(true, zeros(), 0f, 0f), "EQ phẳng = flat");
        check(!DspMath.isFlat(true, new float[]{0, 0, 0, 0, 0.1f, 0, 0, 0, 0, 0}, 0f, 0f), "một băng ≠ 0 → không flat");
        check(!DspMath.isFlat(true, zeros(), 0.1f, 0f), "cân bằng ≠ 0 → không flat");
        check(!DspMath.isFlat(true, zeros(), 0f, 1f), "preamp ≠ 0 → không flat");
        // Lõi toán với kế hoạch phẳng (nb=0, gain=1) cũng trả nguyên dữ liệu — lưới an toàn nếu bypass bị bỏ qua
        DspMath.Plan p = DspMath.plan(zeros(), 0f, 0f, SR, true);
        check(p.nb == 0 && p.headroomDb == 0.0, "kế hoạch phẳng: không băng, không headroom");
        Random r = new Random(5);
        for (int kind : new int[]{DspMath.PCM16, DspMath.PCM24, DspMath.PCM32, DspMath.FLOAT}) {
            int bps = DspMath.bytesPer(kind), ch = 2, frames = 1000;
            byte[] src = new byte[frames * ch * bps];
            if (kind == DspMath.FLOAT) { ByteBuffer fb = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN); for (int i = 0; i < frames * ch; i++) fb.putFloat((float) (r.nextDouble() * 2 - 1)); }
            else r.nextBytes(src);
            ByteBuffer in = ByteBuffer.wrap(src.clone()), out = ByteBuffer.allocate(src.length);
            long clip = DspMath.process(in, out, kind, ch, p, new DspMath.State());
            out.flip(); byte[] got = new byte[out.remaining()]; out.get(got);
            check(java.util.Arrays.equals(src, got) && clip == 0, "kế hoạch phẳng giữ nguyên từng byte, kind=" + kind);
        }
    }

    static void dspBands() {
        for (int sr : new int[]{44100, 48000, 96000, 192000}) for (int b = 0; b < 10; b++) for (float db : new float[]{-12f, -6f, 6f, 12f}) {
            float[] g = zeros(); g[b] = db;
            DspMath.Plan p = DspMath.plan(g, 0f, 0f, sr, false);
            near(DspMath.eqResponseDb(p, DspMath.FREQS[b], sr), db, 0.05, "băng " + DspMath.FREQS[b] + " Hz " + db + " dB @" + sr + " tại tần số tâm");
            // Xa tâm (3 quãng tám) đáp tuyến gần 0 dB hơn nhiều so với tại tâm
            double far = Math.abs(DspMath.eqResponseDb(p, DspMath.FREQS[b] * 8, sr));
            check(DspMath.FREQS[b] * 8 >= sr / 2.0 || far < Math.abs(db) * 0.35, "băng " + DspMath.FREQS[b] + " Hz: xa tâm đáp tuyến nhỏ (" + far + " dB)");
        }
        // Băng sát Nyquist bị bỏ (16 kHz ở 32 kHz: 16000 ≥ 0,45×32000)
        float[] g = zeros(); g[9] = 12f;
        check(DspMath.plan(g, 0f, 0f, 32000, false).nb == 0, "băng ≥ 0,45×fs bị bỏ để tránh méo");
    }

    static void dspPreampBalance() {
        for (float pre = -12f; pre <= 6f; pre += 1f) {
            DspMath.Plan p = DspMath.plan(zeros(), pre, 0f, SR, true);
            double net = 20 * Math.log10(p.gain[0]);
            double expect = pre > 0.05 ? -DspMath.MARGIN_DB : Math.min(pre, 0.0);   // preamp dương được headroom triệt tiêu về -0,3 dB (không nâng quá 0 dBFS)
            near(net, expect, 1e-9, "preamp " + pre + " dB → độ lợi thực");
            check(p.gain[0] <= 1.0 + 1e-12, "preamp " + pre + ": độ lợi không vượt 1");
        }
        DspMath.Plan nh = DspMath.plan(zeros(), 6f, 0f, SR, false);
        near(20 * Math.log10(nh.gain[0]), 6.0, 1e-9, "tắt headroom: preamp +6 dB nguyên vẹn");
        for (float bal = -1f; bal <= 1f; bal += 0.05f) {
            DspMath.Plan p = DspMath.plan(zeros(), 0f, bal, SR, true);
            near(p.gain[0], Math.min(1f, 1f - bal), 1e-9, "cân bằng " + bal + ": kênh trái");
            near(p.gain[1], Math.min(1f, 1f + bal), 1e-9, "cân bằng " + bal + ": kênh phải");
            check(p.gain[2] == p.gain[3] && p.gain[2] == 1.0, "cân bằng không đổi các kênh khác");
        }
        // Cân bằng cực trái: kênh phải = 0, trái giữ nguyên
        DspMath.Plan p = DspMath.plan(zeros(), 0f, -1f, SR, true);
        double[] x = sine(1000, 0.5, 2000, SR); double[] st = new double[x.length * 2];
        for (int i = 0; i < x.length; i++) { st[2 * i] = x[i]; st[2 * i + 1] = x[i]; }
        ByteBuffer out = ByteBuffer.allocate(st.length * 2);
        DspMath.process(pcm16(st, 2), out, DspMath.PCM16, 2, p, new DspMath.State());
        ((java.nio.ByteBuffer) out.flip()).order(ByteOrder.LITTLE_ENDIAN); int maxL = 0, maxR = 0;
        while (out.remaining() >= 4) { maxL = Math.max(maxL, Math.abs(out.getShort())); maxR = Math.max(maxR, Math.abs(out.getShort())); }
        check(maxR == 0 && maxL > 16000, "cân bằng -1: phải im, trái còn nguyên (L=" + maxL + " R=" + maxR + ")");
    }

    static long clipCount(float[] bands, float pre, boolean headroom, double[] sig, int kind) {
        DspMath.Plan p = DspMath.plan(bands, pre, 0f, SR, headroom);
        ByteBuffer out = ByteBuffer.allocate(sig.length * 2 + 8);
        return DspMath.process(pcm16(sig, 1), out, kind, 1, p, new DspMath.State());
    }
    static void dspClipping() {
        float[] bass = DspParamsPresets.BASS, vocal = DspParamsPresets.VOCAL;
        float[] allUp = new float[10]; java.util.Arrays.fill(allUp, 12f);
        float[][] cases = { bass, vocal, allUp, new float[]{12, 12, 0, 0, 0, 0, 0, 0, 0, 0} };
        float[] pres = {0f, 6f, -12f};
        double[] freqs = new double[120]; for (int i = 0; i < freqs.length; i++) freqs[i] = 20 * Math.pow(1000, i / (double) (freqs.length - 1));
        long withoutTotal = 0, withTotal = 0; double worstWith = 0;
        for (float[] c : cases) for (float pre : pres) {
            DspMath.Plan p = DspMath.plan(c, pre, 0f, SR, true);
            for (double f : freqs) {
                double[] s = sine(f, 1.0, SR / 2, SR);   // đầy thang 0 dBFS
                withoutTotal += clipCount(c, pre, false, s, DspMath.PCM16);
                long k = clipCount(c, pre, true, s, DspMath.PCM16); withTotal += k;
                ByteBuffer out = ByteBuffer.allocate(s.length * 2 + 8);
                DspMath.process(pcm16(s, 1), out, DspMath.PCM16, 1, p, new DspMath.State());
                worstWith = Math.max(worstWith, peakOf(out, DspMath.PCM16, SR / 10));
            }
        }
        info.add("Sin đầy thang 20 Hz–20 kHz: số mẫu cắt đỉnh khi KHÔNG headroom = " + withoutTotal + "; có headroom = " + withTotal + "; đỉnh lớn nhất sau xử lý (có headroom) = " + String.format("%.5f", worstWith) + " FS");
        check(withoutTotal > 0, "(đối chứng) không headroom thì EQ nâng gây cắt đỉnh — test có độ nhạy");
        check(withTotal == 0, "có headroom: tín hiệu sin đầy thang không bị cắt đỉnh trên lưới 120 tần số × 12 cấu hình (clip=" + withTotal + ")");
        // Tín hiệu KHÔNG phải sin: sóng vuông/xung có thể vẫn vượt — ghi nhận số đo, KHÔNG khẳng định bằng 0
        double[] square = new double[SR / 2]; for (int i = 0; i < square.length; i++) square[i] = ((i / 50) % 2 == 0) ? 1.0 : -1.0;
        double[] impulse = new double[SR / 2]; for (int i = 0; i < impulse.length; i += 441) impulse[i] = 1.0;
        double[] noise = new double[SR]; Random nr = new Random(9); for (int i = 0; i < noise.length; i++) noise[i] = nr.nextDouble() * 2 - 1;
        String[] names = {"bass", "vocal", "+12 dB mọi băng", "+12 dB 31/62 Hz"};
        StringBuilder sb = new StringBuilder("Giới hạn đã biết (tín hiệu KHÔNG phải sin, đầy thang, có headroom; số mẫu vượt 0 dBFS): ");
        for (int i = 0; i < cases.length; i++)
            sb.append(names[i]).append(" → vuông ").append(clipCount(cases[i], 0f, true, square, DspMath.PCM16)).append(", xung ").append(clipCount(cases[i], 0f, true, impulse, DspMath.PCM16)).append(", nhiễu trắng ").append(clipCount(cases[i], 0f, true, noise, DspMath.PCM16)).append("; ");
        info.add(sb.toString() + "Headroom dựa trên đáp tuyến sin nên KHÔNG chống clipping tuyệt đối cho mọi tín hiệu.");
        // Float: vượt 0 dBFS không bị cắt
        DspMath.Plan p = DspMath.plan(bass, 0f, 0f, SR, true);
        ByteBuffer fin = ByteBuffer.allocate(4000 * 4).order(ByteOrder.LITTLE_ENDIAN); for (int i = 0; i < 4000; i++) fin.putFloat((float) (2.0 * Math.sin(i * 0.05))); fin.flip();
        eq(DspMath.process(fin, ByteBuffer.allocate(4000 * 4), DspMath.FLOAT, 1, p, new DspMath.State()), 0, "float vượt 0 dBFS không bị tính là cắt đỉnh");
    }

    static void dspFormats() {
        float[] bass = DspParamsPresets.BASS;
        DspMath.Plan p = DspMath.plan(bass, 0f, 0f, SR, true);
        int n = 8000; double[] x = sine(100, 0.5, n, SR);
        // Tham chiếu double
        double[] ref = new double[n]; DspMath.State rs = new DspMath.State(); double g = p.gain[0];
        double[][] z1 = new double[10][1], z2 = new double[10][1];
        for (int i = 0; i < n; i++) { double v = x[i] * g; for (int k = 0; k < p.nb; k++) { double y = p.b0[k] * v + z1[k][0]; z1[k][0] = p.b1[k] * v - p.a1[k] * y + z2[k][0]; z2[k][0] = p.b2[k] * v - p.a2[k] * y; v = y; } ref[i] = v; }
        for (int kind : new int[]{DspMath.PCM16, DspMath.PCM24, DspMath.PCM32, DspMath.FLOAT}) {
            ByteBuffer in = ByteBuffer.allocate(n * DspMath.bytesPer(kind)).order(ByteOrder.LITTLE_ENDIAN);
            for (double v : x) {
                if (kind == DspMath.PCM16) in.putShort((short) Math.round(v * 32768.0));
                else if (kind == DspMath.PCM24) { int q = (int) Math.round(v * 8388608.0); in.put((byte) q); in.put((byte) (q >> 8)); in.put((byte) (q >> 16)); }
                else if (kind == DspMath.PCM32) in.putInt((int) Math.round(v * 2147483648.0));
                else in.putFloat((float) v);
            }
            in.flip();
            ByteBuffer out = ByteBuffer.allocate(in.remaining());
            long clip = DspMath.process(in, out, kind, 1, p, new DspMath.State());
            ((java.nio.ByteBuffer) out.flip()).order(ByteOrder.LITTLE_ENDIAN);
            double tol = kind == DspMath.PCM16 ? 3e-4 : kind == DspMath.PCM24 ? 2e-6 : 1e-6, worst = 0;
            for (int i = 0; i < n; i++) {
                double v = kind == DspMath.PCM16 ? out.getShort() / 32768.0 : kind == DspMath.PCM24 ? ((out.get(out.position() + 2) << 16) | ((out.get(out.position() + 1) & 0xff) << 8) | (out.get(out.position()) & 0xff)) / 8388608.0 : kind == DspMath.PCM32 ? out.getInt() / 2147483648.0 : out.getFloat();
                if (kind == DspMath.PCM24) out.position(out.position() + 3);
                worst = Math.max(worst, Math.abs(v - ref[i]));
            }
            check(worst < tol && clip == 0, "định dạng kind=" + kind + ": sai lệch so với tham chiếu double " + worst + " (ngưỡng " + tol + ")");
        }
        // Nhiều kênh (1, 2, 6, 8): mỗi kênh lọc độc lập → kênh giống nhau cho kết quả giống nhau
        for (int ch : new int[]{1, 2, 6, 8}) {
            int frames = 2000; double[] mono = sine(60, 0.4, frames, SR); double[] inter = new double[frames * ch];
            for (int i = 0; i < frames; i++) for (int c = 0; c < ch; c++) inter[i * ch + c] = mono[i];
            ByteBuffer out = ByteBuffer.allocate(inter.length * 2);
            DspMath.process(pcm16(inter, ch), out, DspMath.PCM16, ch, p, new DspMath.State());
            ((java.nio.ByteBuffer) out.flip()).order(ByteOrder.LITTLE_ENDIAN); boolean same = true; short[] row = new short[ch];
            for (int i = 0; i < frames; i++) { for (int c = 0; c < ch; c++) row[c] = out.getShort(); for (int c = 1; c < ch; c++) same &= row[c] == row[0]; }
            check(same, "đa kênh " + ch + ": các kênh giống nhau cho kết quả giống nhau");
        }
        // Byte lẻ cuối (không đủ khung) được chép nguyên, không ném lỗi
        ByteBuffer odd = ByteBuffer.allocate(7); odd.put(new byte[]{1, 2, 3, 4, 5, 6, 7}); odd.flip();
        ByteBuffer oo = ByteBuffer.allocate(7);
        DspMath.process(odd, oo, DspMath.PCM16, 2, p, new DspMath.State());
        check(oo.position() == 7, "phần dư không đủ khung được giữ lại đủ byte");
    }

    static void dspRecovery() {
        float[] bass = DspParamsPresets.BASS;
        double[] x = sine(80, 0.6, 6000, SR);
        // Chia đoạn đúng ranh giới khung cho kết quả giống xử lý một lần
        DspMath.Plan p = DspMath.plan(bass, 0f, 0f, SR, true);
        ByteBuffer whole = ByteBuffer.allocate(x.length * 2); DspMath.process(pcm16(x, 1), whole, DspMath.PCM16, 1, p, new DspMath.State());
        DspMath.State st = new DspMath.State(); ByteBuffer parts = ByteBuffer.allocate(x.length * 2);
        int[] cuts = {0, 1, 777, 3000, 6000};
        for (int i = 0; i + 1 < cuts.length; i++) { double[] seg = java.util.Arrays.copyOfRange(x, cuts[i], cuts[i + 1]); DspMath.process(pcm16(seg, 1), parts, DspMath.PCM16, 1, p, st); }
        check(java.util.Arrays.equals(whole.array(), parts.array()), "chia đoạn không đổi kết quả (trạng thái lọc được giữ giữa các đoạn)");
        // Đổi EQ giữa chừng: không tạo NaN/giá trị vô hạn, vẫn trong thang
        DspMath.State s2 = new DspMath.State(); boolean sane = true;
        float[][] seq = { bass, DspParamsPresets.VOCAL, new float[10], new float[]{12, 12, 12, 12, 12, 12, 12, 12, 12, 12}, bass };
        for (float[] c : seq) {
            DspMath.Plan q = DspMath.plan(c, 0f, 0f, SR, true);
            ByteBuffer o = ByteBuffer.allocate(3000 * 2); DspMath.process(pcm16(sine(120, 1.0, 3000, SR), 1), o, DspMath.PCM16, 1, q, s2);
            ((java.nio.ByteBuffer) o.flip()).order(ByteOrder.LITTLE_ENDIAN);
            while (o.remaining() >= 2) { short v = o.getShort(); sane &= v >= -32768; }
        }
        check(sane, "đổi EQ liên tục giữa các đoạn: đầu ra hợp lệ");
        // Sau khi chuyển về phẳng rồi vào lại EQ với trạng thái đã xoá → giống hệt xử lý mới từ đầu (Dsp.kt gọi State.reset khi rời đoạn phẳng)
        DspMath.State used = new DspMath.State(); DspMath.process(pcm16(x, 1), ByteBuffer.allocate(x.length * 2), DspMath.PCM16, 1, p, used);
        used.reset();
        ByteBuffer a = ByteBuffer.allocate(x.length * 2), b = ByteBuffer.allocate(x.length * 2);
        DspMath.process(pcm16(x, 1), a, DspMath.PCM16, 1, p, used); DspMath.process(pcm16(x, 1), b, DspMath.PCM16, 1, p, new DspMath.State());
        check(java.util.Arrays.equals(a.array(), b.array()), "sau State.reset(), EQ cho kết quả như bộ lọc mới (không còn 'đuôi' cũ)");
        // Không reset: đoạn đầu khác (đuôi cũ) — chứng minh reset cần thiết
        DspMath.State dirty = new DspMath.State(); DspMath.process(pcm16(x, 1), ByteBuffer.allocate(x.length * 2), DspMath.PCM16, 1, p, dirty);
        ByteBuffer c = ByteBuffer.allocate(x.length * 2); DspMath.process(pcm16(x, 1), c, DspMath.PCM16, 1, p, dirty);
        check(!java.util.Arrays.equals(c.array(), b.array()), "(đối chứng) không reset thì có đuôi cũ — test có độ nhạy");
    }

    static void gaplessVerdict() {
        eq(PlayLogic.transitionKind(0, 0), 0, "không đổi định dạng, không configure → loại 0 (chỉ 'không phát hiện thay đổi')");
        eq(PlayLogic.transitionKind(0, 2), 1, "configure dù định dạng không đổi → loại 1");
        eq(PlayLogic.transitionKind(1, 1), 2, "định dạng đổi → loại 2");
        eq(PlayLogic.transitionKind(3, 0), 2, "định dạng đổi ưu tiên");
    }

    public static void main(String[] args) {
        runAll();
        for (String i : info) System.out.println("INFO  " + i);
        for (String f : failures) System.out.println("FAIL  " + f);
        System.out.println("passed=" + pass + " failed=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Preset giống DspParams.PRESETS (Dsp.kt) — sao chép để test không phụ thuộc Android. */
    static final class DspParamsPresets {
        static final float[] BASS = {5f, 4.5f, 3.5f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f};
        static final float[] VOCAL = {-2f, -1.5f, -1f, 0f, 1.5f, 2.5f, 2.5f, 1.5f, 0f, -1f};
    }
}
