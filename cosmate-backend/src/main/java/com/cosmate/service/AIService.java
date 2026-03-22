package com.cosmate.service;

import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AIService {

    // --- TÌM KIẾM & GỢI Ý ---
    List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request);
    List<SearchResponse> recommendCosplay(RecommendationRequest request);

    // --- XỬ LÝ VECTOR (TỐI ƯU API) ---
    void generateAndSaveVector(Integer costumeImageId);

    /**
     * Tạo vector đại diện từ văn bản (Tên + Mô tả trang phục).
     * Dùng chung 1 vector cho tất cả ảnh của cùng 1 bộ trang phục để tiết kiệm request AI.
     */
    String generateVectorForText(String text);

    // --- KIỂM DUYỆT NỘI DUNG (BATCH PROCESSING) ---
    /**
     * Kiểm duyệt hàng loạt ảnh cùng lúc. Ném Exception nếu có bất kỳ ảnh nào vi phạm (18+, bạo lực,...).
     */
    void validateMultipleImageContents(List<MultipartFile> files);

    // --- CHẤM ĐIỂM ---
    PoseScoringResponse scorePose(PoseScoringRequest request);
}