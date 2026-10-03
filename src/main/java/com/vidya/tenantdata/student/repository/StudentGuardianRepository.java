package com.vidya.tenantdata.student.repository;

import com.vidya.tenantdata.student.entity.StudentGuardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, UUID> {

    @Query("SELECT g FROM StudentGuardian g WHERE g.id = :id AND g.deletedAt IS NULL")
    Optional<StudentGuardian> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT g FROM StudentGuardian g WHERE g.guardianCode = :code AND g.deletedAt IS NULL")
    Optional<StudentGuardian> findByGuardianCodeAndNotDeleted(@Param("code") String code);

    boolean existsByGuardianCode(String guardianCode);

    @Query("SELECT g FROM StudentGuardian g WHERE g.student.id = :studentId AND g.deletedAt IS NULL ORDER BY g.isPrimary DESC, g.createdAt ASC")
    List<StudentGuardian> findAllByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT g FROM StudentGuardian g WHERE g.student.id = :studentId AND g.isPrimary = TRUE AND g.status = 'ACTIVE' AND g.deletedAt IS NULL")
    Optional<StudentGuardian> findPrimaryGuardian(@Param("studentId") UUID studentId);
}
