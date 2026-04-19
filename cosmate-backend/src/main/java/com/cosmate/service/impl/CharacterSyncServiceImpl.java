package com.cosmate.service.impl;

import com.cosmate.entity.Character;
import com.cosmate.repository.CharacterRepository;
import com.cosmate.service.CharacterSyncService; // Nhớ import interface
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterSyncServiceImpl implements CharacterSyncService {

    private static final String JIKAN_TOP_CHARACTERS_URL = "https://api.jikan.moe/v4/top/characters?page=1";
    private static final String DEFAULT_ANIME_NAME = "Top Anime Characters";

    private final CharacterRepository characterRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public int syncTopCharacters() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(JIKAN_TOP_CHARACTERS_URL, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                return 0;
            }

            List<Character> charactersToSave = new ArrayList<>();
            for (JsonNode node : data) {
                String name = node.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    continue;
                }

                String imageUrl = node.path("images").path("jpg").path("image_url").asText(null);
                String anime = DEFAULT_ANIME_NAME;

                if (!characterRepository.existsByNameAndAnime(name, anime)) {
                    charactersToSave.add(Character.builder()
                            .name(name)
                            .anime(anime)
                            .imageUrl(imageUrl)
                            .build());
                }
            }

            if (charactersToSave.isEmpty()) {
                return 0;
            }

            characterRepository.saveAll(charactersToSave);
            return charactersToSave.size();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Jikan API returned status " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sync top characters from Jikan API", e);
        }
    }
}