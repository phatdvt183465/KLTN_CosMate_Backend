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

    @PostConstruct
    public void loadKnowledgeBase() {
        try {
            // Đọc từ thư mục src/main/resources/ai-data/
            archetypes = loadJson("ai-data/jungian_archetypes_extended.json");
            stage1Survey = loadJson("ai-data/survey_stage_1.json");
            stage2Survey = loadJson("ai-data/survey_stage_2.json");
            log.info("🔥 Đã nạp thành công Knowledge Base (RAG) lên RAM! Hệ thống AI sẵn sàng.");
        } catch (Exception e) {
            log.error("❌ Lỗi khi nạp dữ liệu AI từ resources: {}", e.getMessage());
        }
    }

    private JsonNode loadJson(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(is);
        }
    }
}