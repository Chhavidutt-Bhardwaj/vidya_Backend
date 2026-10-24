package com.vidya.tenantdata.reportcard.repository;

import com.vidya.tenantdata.reportcard.entity.ReportCardSubjectResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportCardSubjectResultRepository extends JpaRepository<ReportCardSubjectResult, UUID> {

    List<ReportCardSubjectResult> findByReportCardId(UUID reportCardId);
}
