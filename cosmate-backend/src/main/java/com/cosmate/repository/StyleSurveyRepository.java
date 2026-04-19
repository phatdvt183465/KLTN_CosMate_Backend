package com.cosmate.repository;

import com.cosmate.entity.StyleSurvey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StyleSurveyRepository extends JpaRepository<StyleSurvey, Integer> {
}