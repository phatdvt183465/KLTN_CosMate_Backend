package com.cosmate.service.impl;

import com.cosmate.entity.Character;
import com.cosmate.repository.CharacterRepository;
import com.cosmate.service.CharacterSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j // Thư viện để xài biến 'log'
public class CharacterSyncServiceImpl implements CharacterSyncService {

    private final CharacterRepository characterRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String JIKAN_API_URL = "https://api.jikan.moe/v4/top/characters?page=";
    private static final String DEFAULT_ANIME_NAME = "Top Anime Characters";

    @Override
    @Transactional
    public int syncTopCharacters() {
        int totalNewAdded = 0;
        int pagesToFetch = 8; // 8 trang x 25 = 200 nhân vật mỗi lần bấm

        // 1. LẤY TRANG BẮT ĐẦU DỰA TRÊN SỐ LƯỢNG HIỆN CÓ
        // Giúp tránh việc bấm lần 2 lại quét trùng 200 đứa cũ
        long currentTotal = characterRepository.count();
        int startPage = (int) (currentTotal / 25) + 1;
        int endPage = startPage + pagesToFetch - 1;

        log.info("Bắt đầu đồng bộ nhân vật từ trang {} đến trang {}", startPage, endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                // 2. GỌI API JIKAN
                String url = JIKAN_API_URL + page;
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    log.warn("Không thể lấy dữ liệu từ trang {}", page);
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataNode = root.path("data");

                if (!dataNode.isArray()) break;

                List<Character> batchToSave = new ArrayList<>();
                for (JsonNode node : dataNode) {
                    String name = node.path("name").asText(null);
                    if (name == null || name.isBlank()) continue;

                    String imageUrl = node.path("images").path("jpg").path("image_url").asText(null);
                    String anime = DEFAULT_ANIME_NAME;

                    // 3. CHECK TRÙNG LẶP TRONG DATABASE
                    if (!characterRepository.existsByNameAndAnime(name, anime)) {
                        batchToSave.add(Character.builder()
                                .name(name)
                                .anime(anime)
                                .imageUrl(imageUrl)
                                .build());
                    }
                }

                // 4. LƯU VÀO DATABASE
                if (!batchToSave.isEmpty()) {
                    characterRepository.saveAll(batchToSave);
                    totalNewAdded += batchToSave.size();
                    log.info("Trang {}: Đã thêm mới {} nhân vật.", page, batchToSave.size());
                }

                // 5. NGHỈ GIẢI LAO ĐỂ TRÁNH BỊ CHẶN IP (RATE LIMIT)
                if (page < endPage) {
                    Thread.sleep(1500); // Nghỉ 1.5 giây giữa các lần gọi
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Tiến trình bị ngắt quãng: {}", e.getMessage());
                break;
            } catch (Exception e) {
                log.error("Lỗi khi xử lý trang {}: {}", page, e.getMessage());
            }
        }

        log.info("Hoàn tất đồng bộ. Tổng số nhân vật mới đã thêm: {}", totalNewAdded);
        return totalNewAdded;
    }
}