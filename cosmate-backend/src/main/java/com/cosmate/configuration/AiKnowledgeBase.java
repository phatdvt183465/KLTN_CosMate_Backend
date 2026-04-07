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
}