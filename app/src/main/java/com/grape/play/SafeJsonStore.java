package com.grape.play;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kho dữ liệu JSON an toàn (thuần Java, không phụ thuộc Android — có unit test). Bảo vệ dữ liệu người dùng khỏi lỗi đọc/parse/ghi.
 *
 * Khoá trong backend: "v" (chính), "v.bak" (bản hợp lệ trước đó), "v.corrupt" (bản thô bị hỏng, giữ lại để chẩn đoán/khôi phục).
 *  - Chưa có dữ liệu → ABSENT (ghi được).  Chính hợp lệ → OK.  Chính hỏng/thiếu mà .bak hợp lệ → RECOVERED (dùng .bak).
 *  - Chính hỏng và KHÔNG có .bak hợp lệ → CORRUPT; không đọc được backend → UNREADABLE. Hai trạng thái này KHOÁ GHI: không bao giờ ghi đè dữ liệu thô.
 *  - Ghi: chỉ nhận dữ liệu mới đã qua kiểm tra hợp lệ; một lô nguyên tử {v=mới, v.bak=bản chính cũ (hoặc v.corrupt=bản hỏng)}; đọc lại để xác nhận.
 *  - Mọi thao tác đồng bộ hoá trên đối tượng này. Lỗi được báo qua giá trị trả về + [Sink] (không nuốt im lặng, không ném ra gọi).
 */
public final class SafeJsonStore {
    public static final String KEY = "v", BAK = "v.bak", CORRUPT_KEY = "v.corrupt";

    public interface Backend {
        /** null = chưa có. Có thể ném RuntimeException nếu không đọc được. */
        String get(String key);
        /** Ghi nguyên lô (tất cả hoặc không); giá trị null = XOÁ khoá đó. true nếu thành công. */
        boolean putAll(Map<String, String> kv);
    }
    public interface Validator { boolean isValid(String raw); }
    public interface Sink { void log(String msg); }
    public enum Status { ABSENT, OK, RECOVERED, CORRUPT, UNREADABLE }

    public static final class Loaded {
        public final Status status; public final String value;   // value = JSON hợp lệ đã đọc/khôi phục; null nếu ABSENT/CORRUPT/UNREADABLE
        Loaded(Status s, String v) { status = s; value = v; }
    }

    private final Backend backend; private final Validator validator; private final Sink sink; private final String tag;
    private Status status; private boolean loaded;
    private String mainRaw; private boolean mainValid;
    private String lastError; private boolean refusalLogged;

    public SafeJsonStore(Backend backend, Validator validator, Sink sink, String tag) {
        this.backend = backend; this.validator = validator; this.sink = sink == null ? m -> {} : sink; this.tag = tag;
    }

    private void log(String m) { try { sink.log(tag + ": " + m); } catch (RuntimeException ignored) { } }

    public synchronized Loaded load() {
        loaded = true; lastError = null; mainRaw = null; mainValid = false; refusalLogged = false;
        String main, bak;
        try { main = backend.get(KEY); bak = backend.get(BAK); }
        catch (RuntimeException e) {
            status = Status.UNREADABLE; lastError = "không đọc được kho dữ liệu: " + e;
            log(lastError + " — khoá ghi, giữ nguyên dữ liệu"); return new Loaded(status, null);
        }
        mainRaw = main; mainValid = main != null && validator.isValid(main);
        if (main == null && bak == null) { status = Status.ABSENT; return new Loaded(status, null); }
        if (mainValid) { status = Status.OK; return new Loaded(status, main); }
        boolean bakValid = bak != null && validator.isValid(bak);
        if (bakValid) {
            status = Status.RECOVERED;
            log(main == null ? "thiếu dữ liệu chính — khôi phục từ bản sao lưu" : "dữ liệu chính hỏng (" + main.length() + " ký tự) — khôi phục từ bản sao lưu, bản hỏng sẽ được giữ lại");
            return new Loaded(status, bak);
        }
        status = Status.CORRUPT; lastError = "dữ liệu hỏng và không có bản sao lưu hợp lệ";
        log(lastError + " — khoá ghi, giữ nguyên dữ liệu thô (chính=" + (main == null ? "thiếu" : main.length() + " ký tự") + ", sao lưu=" + (bak == null ? "thiếu" : "hỏng") + ")");
        return new Loaded(status, null);
    }

    /** Giá trị thô hiện có của khoá chính / sao lưu (đọc thẳng từ backend, không thay đổi trạng thái); null nếu không có hoặc không đọc được. Dùng để ĐỌC LẠI & XÁC MINH sau khi ghi. */
    public synchronized String peekMain() { try { return backend.get(KEY); } catch (RuntimeException e) { return null; } }
    public synchronized String peekBackup() { try { return backend.get(BAK); } catch (RuntimeException e) { return null; } }

    /** Xoá bản sao lưu — chỉ gọi SAU KHI bản chính mới đã được ghi, đọc lại và xác minh. Từ chối khi kho đang khoá ghi. */
    public synchronized boolean scrubBackup() {
        if (!loaded || !isWritable()) return false;
        Map<String, String> batch = new LinkedHashMap<>(); batch.put(BAK, null);
        try { return backend.putAll(batch) && backend.get(BAK) == null; } catch (RuntimeException e) { lastError = "lỗi khi xoá sao lưu: " + e; log(lastError); return false; }
    }

    public synchronized Status status() { return loaded ? status : null; }
    public synchronized boolean isWritable() { return loaded && status != Status.CORRUPT && status != Status.UNREADABLE; }
    public synchronized String lastError() { return lastError; }

    /** true = đã ghi và xác nhận (hoặc không có gì thay đổi). false = bị từ chối/thất bại; dữ liệu đã lưu không bị thay đổi. */
    public synchronized boolean write(String raw) { return write(raw, false); }

    /**
     * Như [write]; nếu [scrubBackup] thì XOÁ bản sao lưu cũ thay vì chuyển bản chính cũ sang .bak — dùng khi bản cũ chứa bí mật dạng plaintext vừa được mã hoá
     * (nếu không, bản plaintext vẫn nằm lại trong .bak). Bản chính mới đã được xác nhận hợp lệ nên không mất khả năng phục hồi.
     */
    public synchronized boolean write(String raw, boolean scrubBackup) {
        if (!loaded) load();
        if (!isWritable()) { lastError = "kho đang khoá ghi (" + status + ")"; if (!refusalLogged) { refusalLogged = true; log("từ chối ghi: " + lastError + " (các lần sau không ghi log lặp lại)"); } return false; }
        if (raw == null || !validator.isValid(raw)) { lastError = "dữ liệu mới không hợp lệ"; log("từ chối ghi: " + lastError); return false; }
        if (mainValid && raw.equals(mainRaw) && !scrubBackup) return true;
        Map<String, String> batch = new LinkedHashMap<>();
        batch.put(KEY, raw);
        if (mainValid) batch.put(BAK, scrubBackup ? null : mainRaw);
        else if (mainRaw != null) batch.put(CORRUPT_KEY, mainRaw);
        try {
            if (!backend.putAll(batch)) { lastError = "ghi thất bại"; log(lastError + " — dữ liệu cũ giữ nguyên"); return false; }
            String back = backend.get(KEY);
            if (!raw.equals(back)) { lastError = "đọc lại không khớp sau khi ghi"; log(lastError); return false; }
        } catch (RuntimeException e) { lastError = "lỗi khi ghi: " + e; log(lastError); return false; }
        mainRaw = raw; mainValid = true; status = Status.OK; lastError = null;
        return true;
    }
}
