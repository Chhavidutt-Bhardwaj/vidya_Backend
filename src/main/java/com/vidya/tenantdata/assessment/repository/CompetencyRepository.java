package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.Competency;
import com.vidya.tenantdata.assessment.enums.CompetencyCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

    Optional<Competency> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT c FROM Competency c WHERE c.deletedAt IS NULL AND (:subjectId IS NULL OR c.subjectId = :subjectId) "
            + "AND (:category IS NULL OR c.category = :category) ORDER BY c.code")
    List<Competency> search(@Param("subjectId") UUID subjectId, @Param("category") CompetencyCategory category);

    @Query("SELECT count(c) > 0 FROM Competency c WHERE lower(c.code) = lower(:code) AND c.deletedAt IS NULL")
    boolean codeTaken(@Param("code") String code);
}
