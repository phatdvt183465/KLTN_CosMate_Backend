package com.cosmate.repository;

import com.cosmate.entity.CharacterRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRequestRepository extends JpaRepository<CharacterRequest, Integer> {
}
