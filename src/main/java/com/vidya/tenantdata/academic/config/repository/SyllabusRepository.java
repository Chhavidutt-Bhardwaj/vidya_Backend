package com.vidya.tenantdata.academic.config.repository;

import com.vidya.tenantdata.academic.config.entity.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyllabusRepository extends JpaRepository<Syllabus, UUID> {

    Optional<Syllabus> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Syllabus> findByAcademicYearIdAndAcademicClassIdAndSubjectIdAndDeletedAtIsNull(UUID academicYearId, UUID academicClassId, UUID subjectId);

    List<Syllabus> findByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);

    boolean existsBySyllabusCode(String syllabusCode);
}
