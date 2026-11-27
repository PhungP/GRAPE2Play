package com.grape.play;

import java.io.IOException;

/** Luồng WAV PCM "ảo": header 44 byte + dữ liệu PCM giải mã theo yêu cầu, có thể đọc tại vị trí bất kỳ. */
public interface PcmStream {
    long size();
    int read(long pos, byte[] b, int off, int len) throws IOException;
    void close();
    /** Số khung dữ liệu bị phát hiện hỏng và được thay bằng im lặng trong luồng này (0 = dữ liệu nguyên vẹn). */
    default long damagedFrames() { return 0; }
}
