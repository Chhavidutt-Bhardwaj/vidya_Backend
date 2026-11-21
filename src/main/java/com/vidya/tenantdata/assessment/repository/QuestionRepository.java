package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.Question;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Optional<Question> findByIdAndDeletedAtIsNull(UUID id);

    List<Question> findByParentIdAndDeletedAtIsNullOrderByCreatedAt(UUID parentId);

    List<Question> findByParentIdInAndDeletedAtIsNullOrderByCreatedAt(Collection<UUID> parentIds);

    List<Question> findByIdIn(Collection<UUID> ids);

    @Query("SELECT q FROM Question q WHERE q.deletedAt IS NULL AND q.parentId IS NULL AND q.subjectId = :subjectId "
            + "AND (q.academicClassId IS NULL OR q.academicClassId = :classId) AND q.status = com.vidya.tenantdata.assessment.enums.QuestionStatus.APPROVED")
    List<Question> approvedPool(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId);

    @Query("SELECT q FROM Question q WHERE q.deletedAt IS NULL AND q.parentId IS NULL AND (:subjectId IS NULL OR q.subjectId = :subjectId) "
            + "AND (:classId IS NULL OR q.academicClassId = :classId) AND (:type IS NULL OR q.questionType = :type) "
            + "AND (:difficulty IS NULL OR q.difficulty = :difficulty) AND (:status IS NULL OR q.status = :status) "
            + "AND (:unitId IS NULL OR q.syllabusUnitId = :unitId) "
            + "AND (:text IS NULL OR lower(q.stem) LIKE lower(concat('%', :text, '%')) OR lower(q.questionCode) = lower(:text))")
    Page<Question> search(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId, @Param("type") QuestionType type,
                          @Param("difficulty") Difficulty difficulty, @Param("status") QuestionStatus status, @Param("unitId") UUID unitId,
                          @Param("text") String text, Pageable pageable);
}
