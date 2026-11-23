package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.entity.FeeConcessionRequest;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeeConcessionRequestRepository extends JpaRepository<FeeConcessionRequest, UUID> {

    List<FeeConcessionRequest> findByAcademicYearIdAndStudentIdInAndStatus(UUID academicYearId, Collection<UUID> studentIds,
                                                                          ConcessionRequestStatus status);

    boolean existsByStudentIdAndAcademicYearIdAndConcessionTypeIdAndStatusIn(UUID studentId, UUID academicYearId, UUID concessionTypeId,
                                                                             Collection<ConcessionRequestStatus> statuses);

    List<FeeConcessionRequest> findByStudentIdAndAcademicYearIdOrderByCreatedAtAsc(UUID studentId, UUID academicYearId);
}
