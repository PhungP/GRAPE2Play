package com.grape.play

import java.util.concurrent.ConcurrentHashMap

/**
 * Trạng thái toàn vẹn của dữ liệu đang giải mã, theo đường dẫn file: số khung hỏng đã bị thay bằng im lặng ([PcmStream.damagedFrames]).
 * Chỉ để QUAN SÁT/báo cáo (Bit-perfect, nhật ký); không ảnh hưởng đường phát nhạc.
 */
object DecodeHealth {
    private val damaged = ConcurrentHashMap<String, Long>()
    fun clear(path: String) { damaged.remove(path) }
    fun report(path: String, frames: Long) { if (frames > 0) damaged[path] = frames else damaged.remove(path) }
    fun damaged(path: String): Long = damaged[path] ?: 0L

    private val lastLog = ConcurrentHashMap<String, Long>()
    /** ExoPlayer mở lại nguồn nhiều lần (tua, nạp trước, dò thông số) nên cùng một cảnh báo có thể lặp: chỉ ghi mỗi khoá một lần trong [windowMs]. */
    fun shouldLog(key: String, now: Long = System.currentTimeMillis(), windowMs: Long = 60_000L): Boolean {
        if (lastLog.size > 200) lastLog.clear()
        val prev = lastLog[key]
        if (prev != null && now - prev < windowMs) return false
        lastLog[key] = now; return true
    }
}
