package com.cosmate.configuration;

import org.springframework.stereotype.Component;

@Component
public class AiModelRouter {

    // Tách riêng prefix URL
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

    /**
     * Dành cho tính năng Tạo Vector (Dual-Vector Search)
     * Model Chính: Gemini Embedding 2 (Bản mới nhất, vector mượt hơn)
     * Model Backup: Gemini Embedding 1 (Bản cũ, an toàn)
     */
    public String getEmbeddingModelName(boolean isBackup) {
        // text-embedding-004 đã bị khai tử, dùng gemini-embedding-2-preview theo list của ông
        return isBackup ? "models/gemini-embedding-001" : "models/gemini-embedding-2-preview";
    }

    /**
     * Dành cho Tác vụ Nhanh: Bóc Tags, Moderation, Mô tả
     * Model Chính: Gemini 3.1 Flash Lite (Rất nhẹ, nhanh, rẻ, vượt trội 2.5)
     * Model Backup: Gemini 3 Flash
     */
    public String getFastModelName(boolean isBackup) {
        return isBackup ? "models/gemini-3-flash-preview" : "models/gemini-3.1-flash-lite-preview";
    }

    /**
     * Dành cho Tác vụ Phức tạp: Pose Battle, Quiz Logic
     * Model Chính: Gemini 3 Flash (Suy luận sâu)
     * Model Backup: Gemini 2.5 Flash
     */
    public String getReasoningModelName(boolean isBackup) {
        return isBackup ? "models/gemini-2.5-flash" : "models/gemini-3-flash-preview";
    }

    // Tiện ích lấy Full URL
    public String buildUrl(String modelName, String action) {
        return BASE_URL + modelName + ":" + action;
    }
}