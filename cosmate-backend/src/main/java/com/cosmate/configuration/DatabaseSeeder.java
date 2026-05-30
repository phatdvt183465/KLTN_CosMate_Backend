package com.cosmate.configuration;

import com.cosmate.entity.SystemConfig;
import com.cosmate.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

        seedTextToDatabase("PROMPT_MOD_DEMO", "Bạn là một hệ thống kiểm duyệt hình ảnh cosplay. Hãy phân tích ảnh đầu vào. Trả về SAFE nếu bức ảnh an toàn hoặc chỉ mang tính chất quyến rũ, gợi cảm nhẹ (ví dụ: mặc bikini, cosplay hở vai, hở ngực nhẹ, hở đùi, hở eo của nhân vật anime/game). CHỈ trả về UNSAFE nếu bức ảnh chứa nội dung 18+ dung tục, khỏa thân hoàn toàn hoặc để lộ các vùng đặc biệt nhạy cảm (như bộ phận sinh dục, núm vú nữ). Hãy phản hồi ĐÚNG 1 TỪ duy nhất: SAFE hoặc UNSAFE.", "Prompt kiểm duyệt ảnh chung");
        seedTextToDatabase("PROMPT_MOD_COSTUME", "Phân tích ảnh đầu vào. Trả về 'SAFE' nếu ảnh có chứa người hóa trang, trang phục nguyên bộ, hoặc cận cảnh sản phẩm (đường may, chất liệu, nút, vũ khí, tóc giả). Cho phép ảnh nhân vật gợi cảm, hở eo, hở vai, đùi, mặc bikini/swimsuit, chỉ cần không khỏa thân hoàn toàn hay hở vùng nhạy cảm 18+. Trả về 'UNSAFE_VIOLATION' nếu ảnh chứa nội dung 18+ dung tục, bạo lực máu me. Trả về 'UNSAFE_IRRELEVANT' CHỈ KHI bức ảnh hoàn toàn không liên quan đến thời trang/cosplay (phong cảnh, sổ đỏ, màn hình chat, meme).", "Prompt kiểm duyệt ảnh trang phục");
        seedTextToDatabase("PROMPT_TAGS_MULTI", "Trích xuất các từ khóa đặc trưng nhất của bộ trang phục xuất hiện trong TẤT CẢ các bức ảnh đính kèm. Tập trung vào: loại trang phục, màu sắc, họa tiết, và tên nhân vật. Chỉ trả về một chuỗi các từ khóa ngăn cách bằng dấu phẩy.", "Prompt bóc tag nhiều ảnh");
        seedTextToDatabase("PROMPT_TAGS_SINGLE", "Hãy phân tích hình ảnh bộ trang phục này và liệt kê các từ khóa (tags) đặc trưng nhất. Tập trung vào: loại trang phục, màu sắc, họa tiết, chất liệu, phong cách, và tên nhân vật nếu nhận diện được. Chỉ trả về chuỗi các từ khóa ngăn cách bằng dấu phẩy, không giải thích dài dòng.", "Prompt bóc tag 1 ảnh");
        seedTextToDatabase("PROMPT_ANALYZE_ANSWERS", "You are an expert psychological evaluator trained on Pennebaker LIWC and Big Five trait analysis.\nI will provide a JSON array containing user essays.\nINPUT:\n{answers}\n\nTASKS:\n1. You MUST evaluate ALL items in the array. The number of output IDs MUST strictly match the input IDs.\n2. For each essay, check its validity. If it is nonsense or contains profanity, set isValid = false.\n3. If valid, score E, A, O traits from -2 to 2 based on Linguistic markers.\n\nCRITICAL CONSTRAINTS:\n- Your final output MUST be a SINGLE JSON ARRAY.\n- No markdown formatting, no explanations.\n- The 'reason' field MUST be written in Vietnamese.\nFormat: [{\"id\": [corresponding id], \"isValid\": true, \"reason\": \"[Lý do bằng Tiếng Việt]\", \"scores\": {\"E\": 0, \"A\": 0, \"O\": 0}}]", "Prompt phân tích câu trả lời trắc nghiệm");
        seedTextToDatabase("PROMPT_ANALYZE_FEEDBACK", "Bạn là một AI Data Scientist cho giải đấu Cosplay. Dưới đây là danh sách các Feedback của người dùng: {feedback}\nNHIỆM VỤ CỦA BẠN:\n1. Lọc bỏ: Bỏ qua các feedback nhảm nhí, chửi thề, hoặc không liên quan đến kỹ thuật Cosplay.\n2. Gom cụm (Clustering): Gom các feedback có ý nghĩa giống nhau lại. Đếm số lượng 'userId' độc lập cho mỗi cụm.\n3. Tính trọng số động: Luật WCS Gốc luôn chiếm tối thiểu 50% trọng số. Các feedback cộng đồng chiếm tối đa 50%. Mỗi 1 user đóng góp tối đa 5% trọng số cho một tiêu chí cụm. (Ví dụ: Cụm A có 2 user nói giống nhau -> Trọng số 10%).\n4. Tóm tắt bộ luật: Dựa vào các cụm hợp lệ, hãy viết một đoạn 'LUẬT BỔ SUNG' (Supplementary Rules) để hướng dẫn AI cách cộng/trừ điểm.\n\nĐẦU RA BẮT BUỘC: Trả về ĐÚNG MỘT JSON có cấu trúc: {\"valid_feedbacks\": 10, \"supplementary_rules\": \"[Đoạn luật bổ sung...]\"}", "Prompt phân tích Feedback");
        seedTextToDatabase("PROMPT_RECOMMENDATION", "Trang phục dành cho nguyên mẫu {name}. Khát vọng cốt lõi: {desire}. Phong cách: {style}. Màu sắc: {color}. Nhân vật tiêu biểu: {characters}.", "Prompt recommend trang phục");
        seedTextToDatabase("PROMPT_SCORE_POSE", """
                Hãy so sánh ảnh user chụp với nhân vật '{characterName}'. {referenceText}
                BƯỚC 1 - KIỂM DUYỆT CƠ BẢN: Nếu trong ảnh người dùng KHÔNG CÓ NGƯỜI, hoặc hoàn toàn không giống ảnh hóa trang/tạo dáng, BẮT BUỘC trả về chính xác chuỗi JSON: {"score": 0, "comment": "NOT_COSPLAY"}
                BƯỚC 2 - KIỂM TRA TÍNH ĐỒNG NHẤT (QUAN TRỌNG): Hãy đối chiếu chéo 3 dữ liệu: Tên nhân vật '{characterName}', Ảnh mẫu gốc (nếu có), và Ảnh người dùng cosplay. Nếu phát hiện sự sai lệch nghiêm trọng (Ví dụ: Tên là Naruto, nhưng ảnh mẫu là Sasuke, hoặc người dùng lại mặc đồ Luffy), bạn BẮT BUỘC PHẢI PHẠT ĐIỂM NẶNG. Tổng điểm tối đa không được vượt quá 30/100.
                BƯỚC 3 - CHẤM ĐIỂM: Nếu hợp lệ, chấm theo thang điểm 100 dựa trên luật WCS.
                ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (Chỉ trả về JSON, không giải thích thêm): {"score": [Điểm 1-100], "pose_score": [1-40], "expression_score": [1-40], "costume_score": [1-20], "comment": "[Nhận xét kỹ thuật. NẾU VI PHẠM BƯỚC 2, bắt buộc bóc phốt lỗi sai lệch nhân vật này ở câu đầu tiên. Nếu hợp lệ thì nhận xét bình thường.]"}""", "Prompt chấm điểm Pose");
        seedTextToDatabase("PROMPT_ANALYZE_REVIEW", "Phân tích đánh giá của người dùng. Nếu có chửi thề, lăng mạ, spam vô nghĩa -> is_toxic = true. Phân loại cảm xúc (POSITIVE/NEGATIVE/NEUTRAL). Viết 1 câu tóm tắt ngắn gọn. BẮT BUỘC AI phải trả về định dạng JSON: {\"sentiment\": \"...\", \"is_toxic\": true/false, \"summary\": \"...\"}.", "Prompt AI phân tích review");

        // SMART MIGRATION: Chỉ nâng cấp tự động nếu prompt trong DB là bản gốc (khắt khe / con bò sữa / UNSAFE_VIOLATION gốc)
        systemConfigRepository.findById("PROMPT_MOD_DEMO").ifPresent(config -> {
            if (config.getConfigValue() != null && (config.getConfigValue().contains("khắt khe") || config.getConfigValue().contains("CON BÒ SỮA"))) {
                config.setConfigValue("Bạn là một hệ thống kiểm duyệt hình ảnh cosplay. Hãy phân tích ảnh đầu vào. Trả về SAFE nếu bức ảnh an toàn hoặc chỉ mang tính chất quyến rũ, gợi cảm nhẹ (ví dụ: mặc bikini, cosplay hở vai, hở ngực nhẹ, hở đùi, hở eo của nhân vật anime/game). CHỈ trả về UNSAFE nếu bức ảnh chứa nội dung 18+ dung tục, khỏa thân hoàn toàn hoặc để lộ các vùng đặc biệt nhạy cảm (như bộ phận sinh dục, núm vú nữ). Hãy phản hồi ĐÚNG 1 TỪ duy nhất: SAFE hoặc UNSAFE.");
                systemConfigRepository.save(config);
                log.info("✅ [Smart Migration] Đã nâng cấp PROMPT_MOD_DEMO lên phiên bản mềm dẻo hơn.");
            }
        });

        systemConfigRepository.findById("PROMPT_MOD_COSTUME").ifPresent(config -> {
            if (config.getConfigValue() != null && config.getConfigValue().contains("UNSAFE_VIOLATION' nếu ảnh chứa nội dung 18+, phản cảm, bạo lực")) {
                config.setConfigValue("Phân tích ảnh đầu vào. Trả về 'SAFE' nếu ảnh có chứa người hóa trang, trang phục nguyên bộ, hoặc cận cảnh sản phẩm (đường my, chất liệu, nút, vũ khí, tóc giả). Cho phép ảnh nhân vật gợi cảm, hở eo, hở vai, đùi, mặc bikini/swimsuit, chỉ cần không khỏa thân hoàn toàn hay hở vùng nhạy cảm 18+. Trả về 'UNSAFE_VIOLATION' nếu ảnh chứa nội dung 18+ dung tục, bạo lực máu me. Trả về 'UNSAFE_IRRELEVANT' CHỈ KHI bức ảnh hoàn toàn không liên quan đến thời trang/cosplay (phong cảnh, sổ đỏ, màn hình chat, meme).");
                systemConfigRepository.save(config);
                log.info("✅ [Smart Migration] Đã nâng cấp PROMPT_MOD_COSTUME lên phiên bản mềm dẻo hơn.");
            }
        });

        seedJsonToDatabase("ARCHETYPES_DATA", "ai-data/jungian_archetypes_extended.json", "Dữ liệu 12 Archetypes cho AI");
        seedJsonToDatabase("QUIZ_STAGE_1", "ai-data/survey_stage_1.json", "Bộ câu hỏi trắc nghiệm Stage 1");
        seedJsonToDatabase("QUIZ_STAGE_2", "ai-data/survey_stage_2.json", "Bộ câu hỏi trắc nghiệm Stage 2");
    }

    private void seedJsonToDatabase(String configKey, String filePath, String description) {
        if (!systemConfigRepository.existsByConfigKey(configKey)) {
            try (InputStream is = new ClassPathResource(filePath).getInputStream()) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                SystemConfig config = SystemConfig.builder()
                        .configKey(configKey)
                        .configValue(jsonContent)
                        .description(description)
                        .build();

                systemConfigRepository.save(config);
                log.info("✅ Đã tự động seed {} vào Database từ file {}.", configKey, filePath);
            } catch (Exception e) {
                log.error("❌ Lỗi khi seed dữ liệu {}: {}", configKey, e.getMessage());
            }
        }
    }

    private void seedTextToDatabase(String configKey, String textContent, String description) {
        if (!systemConfigRepository.existsByConfigKey(configKey)) {
            SystemConfig config = SystemConfig.builder()
                    .configKey(configKey)
                    .configValue(textContent)
                    .description(description)
                    .build();
            systemConfigRepository.save(config);
            log.info("✅ Đã tự động seed văn bản {} vào Database.", configKey);
        }
    }
}