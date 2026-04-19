package com.cosmate.repository;

import com.cosmate.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Integer> {
    boolean existsByNameAndAnime(String name, String anime);
}
