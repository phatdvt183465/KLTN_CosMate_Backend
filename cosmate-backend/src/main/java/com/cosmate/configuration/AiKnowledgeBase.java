package com.cosmate.configuration;

import com.cosmate.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
@org.springframework.context.annotation.DependsOn("databaseSeeder") // Đảm bảo Seeder chạy trước file này
@RequiredArgsConstructor
public class AiKnowledgeBase {

    private final ObjectMapper objectMapper;
    private final SystemConfigRepository systemConfigRepository;

    private String promptModDemo;
    private String promptModCostume;
    private String promptTagsMulti;
    private String promptTagsSingle;
    private String promptAnalyzeAnswers;
    private String promptAnalyzeFeedback;
    private String promptRecommendation;
    private String promptScorePose;

    // Lưu dữ liệu trên RAM
    private JsonNode archetypes;
    private JsonNode stage1Survey;
    private JsonNode stage2Survey;
    private String wcsRules;

    @PostConstruct
    public void loadKnowledgeBase() {
        try {
            // Lôi dữ liệu TỪ DATABASE (chứ không phải từ file nữa)
            archetypes = loadJsonFromDb("ARCHETYPES_DATA");
            stage1Survey = loadJsonFromDb("QUIZ_STAGE_1");
            stage2Survey = loadJsonFromDb("QUIZ_STAGE_2");

            wcsRules = systemConfigRepository.findById("WCS_RULES")
                    .map(config -> config.getConfigValue() == null ? "" : config.getConfigValue())
                    .orElse("");

            promptModDemo = loadStringFromDb("PROMPT_MOD_DEMO");
            promptModCostume = loadStringFromDb("PROMPT_MOD_COSTUME");
            promptTagsMulti = loadStringFromDb("PROMPT_TAGS_MULTI");
            promptTagsSingle = loadStringFromDb("PROMPT_TAGS_SINGLE");
            promptAnalyzeAnswers = loadStringFromDb("PROMPT_ANALYZE_ANSWERS");
            promptAnalyzeFeedback = loadStringFromDb("PROMPT_ANALYZE_FEEDBACK");
            promptRecommendation = loadStringFromDb("PROMPT_RECOMMENDATION");
            promptScorePose = loadStringFromDb("PROMPT_SCORE_POSE");

            log.info("🔥 Đã nạp thành công Knowledge Base (Từ Database) lên RAM!");
        } catch (Exception e) {
            log.error("❌ Lỗi khi nạp dữ liệu AI từ Database: {}", e.getMessage());
        }
    }

    // Hàm Helper: Móc chuỗi JSON từ DB và ép kiểu sang JsonNode
    private JsonNode loadJsonFromDb(String configKey) throws Exception {
        String jsonString = systemConfigRepository.findById(configKey)
                .map(config -> config.getConfigValue())
                .orElse("[]"); // Trả về mảng rỗng nếu không tìm thấy để tránh lỗi Null

        return objectMapper.readTree(jsonString);
    }

    private String loadStringFromDb(String configKey) {
        return systemConfigRepository.findById(configKey)
                .map(config -> config.getConfigValue() != null ? config.getConfigValue() : "")
                .orElse("");
    }

    @Getter
    private final java.util.Map<String, int[]> archetypeCoordinates = java.util.Map.ofEntries(
            java.util.Map.entry("ARCH_01", new int[]{15, 5, 5}),   // The Hero
            java.util.Map.entry("ARCH_02", new int[]{10, -15, 15}), // The Rebel
            java.util.Map.entry("ARCH_03", new int[]{-10, 0, 20}),  // The Sage
            java.util.Map.entry("ARCH_04", new int[]{0, 15, -10}),  // The Innocent
            java.util.Map.entry("ARCH_05", new int[]{20, 5, 15}),   // The Jester
            java.util.Map.entry("ARCH_06", new int[]{-5, 20, 0}),   // The Caregiver
            java.util.Map.entry("ARCH_07", new int[]{5, 0, 20}),    // The Explorer
            java.util.Map.entry("ARCH_08", new int[]{10, 15, 10}),  // The Lover
            java.util.Map.entry("ARCH_09", new int[]{0, -5, 20}),   // The Creator
            java.util.Map.entry("ARCH_10", new int[]{15, -10, 0}),  // The Ruler
            java.util.Map.entry("ARCH_11", new int[]{0, -10, 15}),  // The Magician
            java.util.Map.entry("ARCH_12", new int[]{0, 10, -10})   // The Everyman
    );
}