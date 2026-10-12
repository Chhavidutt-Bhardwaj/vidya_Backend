package com.vidya.tenantdata.subject.repository;

import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    @Query("SELECT s FROM Subject s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Subject> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM Subject s WHERE s.subjectCode = :subjectCode AND s.deletedAt IS NULL")
    Optional<Subject> findBySubjectCodeAndNotDeleted(@Param("subjectCode") String subjectCode);

    boolean existsBySubjectCode(String subjectCode);

    @Query("SELECT s FROM Subject s WHERE s.deletedAt IS NULL " +
           "AND (:subjectType IS NULL OR s.subjectType = :subjectType) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (CAST(:query AS string) IS NULL OR :query = '' OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR " +
           "     LOWER(s.shortName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR " +
           "     LOWER(s.subjectCode) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))")
    Page<Subject> searchSubjects(
            @Param("subjectType") SubjectType subjectType,
            @Param("status") SubjectStatus status,
            @Param("query") String query,
            Pageable pageable
    );
}
