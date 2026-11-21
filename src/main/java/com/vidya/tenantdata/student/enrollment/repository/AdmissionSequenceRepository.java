package com.vidya.tenantdata.student.enrollment.repository;

import com.vidya.tenantdata.student.enrollment.entity.AdmissionSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionSequenceRepository extends JpaRepository<AdmissionSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AdmissionSequence s WHERE s.academicYearId = :yearId")
    Optional<AdmissionSequence> lockByYear(@Param("yearId") UUID yearId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AdmissionSequence s WHERE s.academicYearId IS NULL")
    Optional<AdmissionSequence> lockSchoolWide();

    Optional<AdmissionSequence> findByAcademicYearId(UUID yearId);

    @Query("SELECT s FROM AdmissionSequence s WHERE s.academicYearId IS NULL")
    Optional<AdmissionSequence> findSchoolWide();

    List<AdmissionSequence> findAllByOrderByPrefixAsc();
}
