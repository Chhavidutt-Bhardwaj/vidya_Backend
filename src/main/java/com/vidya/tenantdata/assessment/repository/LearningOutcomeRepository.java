package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.LearningOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningOutcomeRepository extends JpaRepository<LearningOutcome, UUID> {

    Optional<LearningOutcome> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT o FROM LearningOutcome o WHERE o.deletedAt IS NULL AND (:subjectId IS NULL OR o.subjectId = :subjectId) "
            + "AND (:classId IS NULL OR o.academicClassId IS NULL OR o.academicClassId = :classId) "
            + "AND (:competencyId IS NULL OR o.competencyId = :competencyId) ORDER BY o.sortOrder, o.code")
    List<LearningOutcome> search(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId, @Param("competencyId") UUID competencyId);

    @Query("SELECT count(o) > 0 FROM LearningOutcome o WHERE lower(o.code) = lower(:code) AND o.deletedAt IS NULL")
    boolean codeTaken(@Param("code") String code);

    List<LearningOutcome> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
