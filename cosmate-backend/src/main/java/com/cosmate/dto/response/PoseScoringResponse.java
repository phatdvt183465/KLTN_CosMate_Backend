package com.cosmate.dto.response;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PoseScoringResponse {
    private Integer id;             // Thêm ID để Frontend gán vào nút Sửa/Xóa
    private int score;              // Điểm số (0 - 100)
    private String comment;         // Lời nhận xét của AI
    private String characterName;   // Tên nhân vật (VD: Naruto)
    private String imageUrl;        // Link ảnh đã lưu trên Firebase
}