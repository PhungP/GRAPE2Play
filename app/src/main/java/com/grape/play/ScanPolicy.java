package com.grape.play;

/** Chính sách tin cậy kết quả quét (thuần Java, có test). */
public final class ScanPolicy {
    private ScanPolicy() {}

    /**
     * Kết quả RỖNG của nguồn từ xa có đáng tin không? KHÔNG, với mọi loại nguồn: thư mục chia sẻ có thể trả rỗng khi ổ đĩa bị tháo/chưa mount,
     * DLNA đang lập chỉ mục, đám mây hết quyền… Liệt kê thành công không chứng minh nguồn thật sự rỗng. Cache cũ được giữ nguyên.
     */
    public static boolean remoteEmptyVerified(String type) { return false; }

    /**
     * Đánh giá độ tin cậy của MỘT ổ lưu trữ cục bộ sau khi quét.
     *  - không đọc được → không tin, giữ toàn bộ bài cũ.
     *  - có lỗi/thư mục con không đọc được → không tin, chỉ xoá bài được kiểm tra độc lập là đã xoá (từng file).
     *  - quét sạch nhưng KHÔNG thấy bài nào trong khi trước đó có bài → chỉ tin khi có bằng chứng ổ đang hoạt động thật:
     *    đã gắn (và có quyền) và thư mục gốc có nội dung khác (ổ chưa sẵn sàng thường liệt kê rỗng hoàn toàn).
     *  - còn lại → tin: bài cũ không còn trong kết quả bị xoá.
     */
    public static ScanMerge.RootState judgeRoot(String rootPath, boolean readable, int enumErrors, boolean mounted, boolean hasOtherContent, int found, int oldCount) {
        String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        if (!readable) return new ScanMerge.RootState(prefix, false, false);
        if (enumErrors > 0) return new ScanMerge.RootState(prefix, false, true);
        if (found == 0 && oldCount > 0 && !(mounted && hasOtherContent)) return new ScanMerge.RootState(prefix, false, false);
        return new ScanMerge.RootState(prefix, true, false);
    }
}
