package com.cosmate.service;

import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.PoseScore;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AIService {

    // --- TÌM KIẾM & GỢI Ý ---
    List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request);
    List<SearchResponse> recommendCosplay(RecommendationRequest request);

    // --- XỬ LÝ VECTOR (TỐI ƯU API) ---
    // Nâng cấp: Cho phép chọn cập nhật Text hay Image hay cả 2
    void generateAndSaveVector(Integer costumeId, boolean updateText, boolean updateImage);

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

    // API dọn dẹp: Cập nhật vector cho toàn bộ ảnh cũ bị thiếu
    void generateVectorsForMissingImages();

    // Tiện ích: AI tự động viết mô tả chi tiết cho trang phục dựa trên Tên và Ảnh
    String generateCostumeDescription(String costumeName, String customPrompt, List<MultipartFile> files);

    String extractFeaturesFromMultipleImages(List<MultipartFile> files);

    // Hàm tìm kiếm dự phòng khi AI sập
    List<SearchResponse> fallbackSearch(String keyword);

    // Lấy lịch sử chấm điểm Pose
    List<PoseScore> getMyPoseHistory(Integer userId, String keyword);

    // Thêm tên nhân vật cosplay Pose
    PoseScore updatePoseCharacterName(Integer scoreId, Integer userId, String newName);

    // Xóa lịch sử chấm điểm
    void deletePoseScore(Integer scoreId, Integer userId);

    // Phân tích câu trả lời tự nhập của người dùng (Style Quiz)
    // Thay thế hàm cũ bằng hàm này
    List<com.cosmate.dto.response.CustomAnswerResponse> analyzeCustomAnswersBatch(List<com.cosmate.dto.request.CustomAnswerRequest> requests);

    void refreshArchetypeCache();

    String submitStyleQuiz(Integer userId, com.cosmate.dto.request.QuizSubmitRequest request);
}