
## Bản 1.8.6
- Gemini: kiểm tra khóa bằng việc liệt kê model (không phụ thuộc tải của model tạo nội dung); khi model quá tải (503) hoặc hết hạn mức (429) tự thử lại rồi chuyển sang model dự phòng (flash-latest → flash-lite-latest → 2.5-flash → 2.5-flash-lite).
