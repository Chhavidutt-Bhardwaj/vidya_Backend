package com.vidya.tenantdata.reportcard.repository;

import com.vidya.tenantdata.reportcard.entity.ReportCardRemark;
import com.vidya.tenantdata.reportcard.enums.AuthorRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportCardRemarkRepository extends JpaRepository<ReportCardRemark, UUID> {

    List<ReportCardRemark> findByReportCardId(UUID reportCardId);

    Optional<ReportCardRemark> findByReportCardIdAndAuthorRole(UUID reportCardId, AuthorRole authorRole);
}
