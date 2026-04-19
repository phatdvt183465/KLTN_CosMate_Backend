package com.cosmate.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class AiKnowledgeBase {

    private final ObjectMapper objectMapper;

    // Lưu dữ liệu trên RAM
    private JsonNode archetypes;
    private JsonNode stage1Survey;
    private JsonNode stage2Survey;
    private JsonNode surveyEnd;
    private String wcsRules;

    @PostConstruct
    public void loadKnowledgeBase() {
        try {
            archetypes = loadJson("ai-data/jungian_archetypes_extended.json");
            stage1Survey = loadJson("ai-data/survey_stage_1.json");
            stage2Survey = loadJson("ai-data/survey_stage_2.json");
            try (InputStream is = new ClassPathResource("ai-data/wcs_scoring_rules.txt").getInputStream()) {
                wcsRules = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            log.info("🔥 Đã nạp thành công Knowledge Base (RAG) và WCS Rules lên RAM!");
        } catch (Exception e) {
            log.error("Lỗi khi nạp dữ liệu AI từ resources: {}", e.getMessage());
        }
    }

    private JsonNode loadJson(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(is);
        }
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