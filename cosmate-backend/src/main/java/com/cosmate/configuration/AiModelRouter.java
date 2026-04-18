package com.cosmate.configuration;

import org.springframework.stereotype.Component;

@Component
public class AiModelRouter {

    // Tách riêng prefix URL
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

    /**
     * Dành cho tính năng Tạo Vector (Dual-Vector Search)
     * Model Chính: text-embedding-004 (Chuẩn mới)
     * Model Backup: gemini-embedding-001
     */
    public String getEmbeddingModelName(boolean isBackup) {
        return isBackup ? "models/gemini-embedding-001" : "models/text-embedding-004";
    }

    /**
     * Dành cho Tác vụ Nhanh: Bóc Tags, Moderation, Mô tả
     * Model Chính: gemini-3.1-flash-lite (Rất nhẹ, nhanh, rẻ)
     * Model Backup: gemini-3-flash
     */
    public String getFastModelName(boolean isBackup) {
        return isBackup ? "models/gemini-3-flash" : "models/gemini-3.1-flash-lite";
    }

    /**
     * Dành cho Tác vụ Phức tạp: Pose Battle, Quiz Logic
     * Model Chính: gemini-3-flash
     * Model Backup: gemini-2.5-flash
     */
    public String getReasoningModelName(boolean isBackup) {
        return isBackup ? "models/gemini-2.5-flash" : "models/gemini-3-flash";
    }

    // Tiện ích lấy Full URL
    public String buildUrl(String modelName, String action) {
        return BASE_URL + modelName + ":" + action;
    }
}