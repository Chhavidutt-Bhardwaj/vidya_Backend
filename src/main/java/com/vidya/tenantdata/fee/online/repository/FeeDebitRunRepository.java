package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeDebitRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeDebitRunRepository extends JpaRepository<FeeDebitRun, UUID> {

    List<FeeDebitRun> findAllByOrderByStartedAtDesc(Pageable page);
}
