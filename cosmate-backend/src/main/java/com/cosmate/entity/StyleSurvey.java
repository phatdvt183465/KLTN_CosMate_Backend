package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Style_Surveys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StyleSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Tham chiếu đến người dùng thực hiện bài trắc nghiệm (Cosplayer)
     * Dựa trên Foreign Key cosplayer_id -> Users(id) trong DB
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cosplayer_id")
    private User cosplayerId;

    /**
     * Lưu trữ toàn bộ câu trả lời dưới dạng JSON
     * Sử dụng NVARCHAR(MAX) để chứa dữ liệu không giới hạn độ dài
     */
    @Column(name = "answers_json", columnDefinition = "NVARCHAR(MAX)")
    private String answersJson;

    /**
     * Các tag gợi ý ban đầu dựa trên kết quả khảo sát
     */
    @Column(name = "recommended_tags", length = 255)
    private String recommendedTags;
}