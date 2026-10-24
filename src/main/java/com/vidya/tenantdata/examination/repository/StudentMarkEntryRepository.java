package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.StudentMarkEntry;
import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
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
public interface StudentMarkEntryRepository extends JpaRepository<StudentMarkEntry, UUID> {

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<StudentMarkEntry> findActiveById(@Param("id") UUID id);

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.entryCode = :code AND m.deletedAt IS NULL")
    Optional<StudentMarkEntry> findActiveByCode(@Param("code") String code);

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.examSubjectConfig.id = :configId AND m.deletedAt IS NULL")
    List<StudentMarkEntry> findByConfigId(@Param("configId") UUID configId);

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.examSubjectConfig.id = :configId AND m.student.id = :studentId AND m.deletedAt IS NULL")
    Optional<StudentMarkEntry> findByConfigAndStudent(@Param("configId") UUID configId, @Param("studentId") UUID studentId);

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.student.id = :studentId AND m.deletedAt IS NULL")
    List<StudentMarkEntry> findByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT m FROM StudentMarkEntry m WHERE m.deletedAt IS NULL " +
           "AND m.examSubjectConfig.id = :configId " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<StudentMarkEntry> searchMarks(@Param("configId") UUID configId,
                                       @Param("status") MarkEntryStatus status,
                                       Pageable pageable);

    @Query("SELECT COUNT(m) > 0 FROM StudentMarkEntry m WHERE m.examSubjectConfig.id = :configId AND m.student.id = :studentId AND m.deletedAt IS NULL")
    boolean existsByConfigAndStudent(@Param("configId") UUID configId, @Param("studentId") UUID studentId);
}
