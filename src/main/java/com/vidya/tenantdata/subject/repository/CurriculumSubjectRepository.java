package com.vidya.tenantdata.subject.repository;

import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurriculumSubjectRepository extends JpaRepository<CurriculumSubject, UUID> {

    @Query("SELECT cs FROM CurriculumSubject cs WHERE cs.id = :id AND cs.deletedAt IS NULL")
    Optional<CurriculumSubject> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT cs FROM CurriculumSubject cs " +
           "WHERE cs.id = :id AND cs.curriculum.id = :curriculumId AND cs.deletedAt IS NULL")
    Optional<CurriculumSubject> findByIdAndCurriculumIdAndNotDeleted(
            @Param("id") UUID id,
            @Param("curriculumId") UUID curriculumId
    );

    @Query("SELECT COUNT(cs) > 0 FROM CurriculumSubject cs " +
           "WHERE cs.curriculum.id = :curriculumId " +
           "AND cs.subject.id = :subjectId " +
           "AND cs.academicClass.id = :academicClassId " +
           "AND cs.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR cs.id <> :excludeId)")
    boolean existsActiveByCurriculumSubjectClass(
            @Param("curriculumId") UUID curriculumId,
            @Param("subjectId") UUID subjectId,
            @Param("academicClassId") UUID academicClassId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT cs FROM CurriculumSubject cs " +
           "WHERE cs.curriculum.id = :curriculumId AND cs.deletedAt IS NULL " +
           "AND (:academicClassId IS NULL OR cs.academicClass.id = :academicClassId) " +
           "AND (:subjectType IS NULL OR cs.subject.subjectType = :subjectType) " +
           "AND (:isCompulsory IS NULL OR cs.isCompulsory = :isCompulsory) " +
           "ORDER BY cs.displayOrder ASC, cs.createdAt ASC")
    Page<CurriculumSubject> searchCurriculumSubjects(
            @Param("curriculumId") UUID curriculumId,
            @Param("academicClassId") UUID academicClassId,
            @Param("subjectType") SubjectType subjectType,
            @Param("isCompulsory") Boolean isCompulsory,
            Pageable pageable
    );

    @Query("SELECT cs FROM CurriculumSubject cs " +
           "WHERE cs.curriculum.id = :curriculumId AND cs.deletedAt IS NULL " +
           "ORDER BY cs.displayOrder ASC")
    List<CurriculumSubject> findByCurriculumIdAndNotDeleted(@Param("curriculumId") UUID curriculumId);
}
