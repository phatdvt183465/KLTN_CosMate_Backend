package com.cosmate.repository;

import com.cosmate.entity.Costume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostumeRepository extends JpaRepository<Costume, Integer> {
    List<Costume> findByProviderIdAndStatusNotIgnoreCase(Integer providerId, String status);
    List<Costume> findByNameContainingIgnoreCaseAndStatusNot(String keyword, String status);

    @Query("SELECT c FROM Costume c WHERE COALESCE(TRIM(c.textVector), '') <> '' AND COALESCE(TRIM(c.imageVector), '') <> ''")
    List<Costume> findAllWithVector();

    @Query("SELECT c FROM Costume c WHERE COALESCE(TRIM(c.textVector), '') = '' OR COALESCE(TRIM(c.imageVector), '') = ''")
    List<Costume> findCostumesMissingVector();

    @Query("SELECT DISTINCT c FROM Costume c JOIN c.characters ch WHERE ch IN (SELECT ch2 FROM Costume c2 JOIN c2.characters ch2 WHERE c2.id IN :costumeIds) AND c.id NOT IN :costumeIds")
    List<Costume> findCostumesByCharacterFallback(@org.springframework.data.repository.query.Param("costumeIds") List<Integer> costumeIds);

    @Query("SELECT ch.id FROM Costume c JOIN c.characters ch WHERE c.id IN :costumeIds")
    java.util.Set<Integer> findCharactersByCostumeIds(@org.springframework.data.repository.query.Param("costumeIds") java.util.List<Integer> costumeIds);

    @Query("SELECT c.id FROM Costume c JOIN c.characters ch WHERE ch.id IN :characterIds AND c.id NOT IN :excludeCostumeIds")
    java.util.List<Integer> findCostumeIdsByCharacterIds(@org.springframework.data.repository.query.Param("characterIds") java.util.List<Integer> characterIds, @org.springframework.data.repository.query.Param("excludeCostumeIds") java.util.List<Integer> excludeCostumeIds);
}
