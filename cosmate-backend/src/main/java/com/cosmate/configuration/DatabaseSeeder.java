package com.cosmate.configuration;

import com.cosmate.entity.SystemConfig;
import com.cosmate.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("databaseSeeder")
@RequiredArgsConstructor
public class DatabaseSeeder {

    private final SystemConfigRepository systemConfigRepository;

    @PostConstruct
    public void seedData() {
        // 1. Seed luật WCS
        if (!systemConfigRepository.existsByConfigKey("WCS_RULES")) {
            String wcsText = """
                WORLD COSPLAY SUMMIT (WCS) - OFFICIAL JUDGING CRITERIA FOR AI EVALUATION
                Hệ thống AI đóng vai trò là Ban Giám Khảo, đánh giá hình ảnh dựa trên 3 tiêu chuẩn cốt lõi:
                1. COSTUME PRECISION & QUALITY (Độ chính xác và Chất lượng trang phục - 40/100 điểm):
                - So sánh trang phục thực tế với thiết kế gốc của nhân vật anime/manga.
                - Đánh giá sự vừa vặn, độ chi tiết của hoa văn, màu sắc, phụ kiện đi kèm.
                - Kỹ thuật chế tác (craftsmanship): Sự khéo léo trong việc sử dụng vật liệu, may mặc, hoặc giả kim loại.
                2. ACTING & STAGE PROFICIENCY (Diễn xuất và Thần thái - 40/100 điểm):
                - Biểu cảm khuôn mặt (Facial Expression) có truyền tải đúng tính cách nhân vật hay không (ví dụ: lạnh lùng, vui vẻ, điên rồ).
                - Ngôn ngữ cơ thể (Body Language) và tư thế tạo dáng (Pose) phải khớp với các tư thế signature (đặc trưng) của nhân vật trong tác phẩm gốc.
                3. COSTUME STAGE PRESENCE & X-FACTOR (Ấn tượng thị giác và Sự sáng tạo - 20/100 điểm):
                - Tác động thị giác tổng thể của bức ảnh.
                - Khả năng tận dụng góc chụp, ánh sáng hoặc các cách "chế" đồ thông minh (low-cost cosplay) nhưng vẫn mang lại cảm giác chân thực.
                """;
            systemConfigRepository.save(SystemConfig.builder().configKey("WCS_RULES").configValue(wcsText).description("Luật chấm điểm Pose AI theo chuẩn WCS").build());
            log.info("Đã tự động khởi tạo WCS_RULES vào Database.");
        }

        // 2. Seed Prompt cho AI Description (Persona 1: Sale)
        if (!systemConfigRepository.existsByConfigKey("PROMPT_PERSONA_SALE")) {
            String salePrompt = "Phong cách chuyên nghiệp, tập trung vào chất liệu vải, đường may, form dáng và tính ứng dụng để thuyết phục khách hàng thuê đồ.";
            systemConfigRepository.save(SystemConfig.builder().configKey("PROMPT_PERSONA_SALE").configValue(salePrompt).description("Văn mẫu cho AI Description - Phong cách Sale").build());
        }

        // 3. Seed Prompt cho AI Description (Persona 2: Cute)
        if (!systemConfigRepository.existsByConfigKey("PROMPT_PERSONA_CUTE")) {
            String cutePrompt = "Phong cách hài hước, đáng yêu, dùng nhiều icon, ngôn ngữ Gen Z, nhấn mạnh vào sự dễ thương và nổi bật khi mặc đi fes.";
            systemConfigRepository.save(SystemConfig.builder().configKey("PROMPT_PERSONA_CUTE").configValue(cutePrompt).description("Văn mẫu cho AI Description - Phong cách Cute").build());
        }

        // 4. Seed Prompt cho AI Description (Persona 3: Deep)
        if (!systemConfigRepository.existsByConfigKey("PROMPT_PERSONA_DEEP")) {
            String deepPrompt = "Phong cách cổ trang, hoa mỹ, từ ngữ bay bổng (hán việt), nhấn mạnh vào cốt truyện và khí chất của nhân vật.";
            systemConfigRepository.save(SystemConfig.builder().configKey("PROMPT_PERSONA_DEEP").configValue(deepPrompt).description("Văn mẫu cho AI Description - Phong cách Cổ trang").build());
        }
    }
}