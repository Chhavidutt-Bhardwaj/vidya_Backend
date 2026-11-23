package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT a FROM Assessment a WHERE a.deletedAt IS NULL AND a.examTermId = :termId AND a.academicClassId = :classId "
            + "AND (:sectionId IS NULL OR a.sectionId IS NULL OR a.sectionId = :sectionId) AND (:subjectId IS NULL OR a.subjectId = :subjectId) "
            + "ORDER BY a.assessmentDate, a.title")
    List<Assessment> search(@Param("termId") UUID termId, @Param("classId") UUID classId, @Param("sectionId") UUID sectionId, @Param("subjectId") UUID subjectId);

    boolean existsByComponentIdAndDeletedAtIsNull(UUID componentId);
}
