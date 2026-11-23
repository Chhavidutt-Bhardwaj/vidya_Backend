package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.QuestionPaper;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionPaperRepository extends JpaRepository<QuestionPaper, UUID> {

    Optional<QuestionPaper> findByIdAndDeletedAtIsNull(UUID id);

    List<QuestionPaper> findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(UUID parentPaperId);

    @Query("SELECT p FROM QuestionPaper p WHERE p.deletedAt IS NULL AND p.parentPaperId IS NULL AND (:subjectId IS NULL OR p.subjectId = :subjectId) "
            + "AND (:classId IS NULL OR p.academicClassId = :classId) AND (:status IS NULL OR p.status = :status) ORDER BY p.createdAt DESC")
    List<QuestionPaper> search(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId, @Param("status") PaperStatus status);
}
