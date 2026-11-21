package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentSchemeRepository extends JpaRepository<AssessmentScheme, UUID> {

    Optional<AssessmentScheme> findByIdAndDeletedAtIsNull(UUID id);

    List<AssessmentScheme> findByAcademicYearIdAndDeletedAtIsNullOrderByName(UUID academicYearId);

    @Query(value = "SELECT s.* FROM assessment_scheme s JOIN assessment_scheme_class c ON c.scheme_id = s.id "
            + "WHERE c.academic_year_id = :yearId AND c.academic_class_id = :classId AND s.deleted_at IS NULL AND s.status = 'ACTIVE'", nativeQuery = true)
    Optional<AssessmentScheme> findActiveFor(@Param("yearId") UUID yearId, @Param("classId") UUID classId);
}
