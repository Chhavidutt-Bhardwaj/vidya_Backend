package com.vidya.master.hub.repository;

import com.vidya.master.hub.entity.Hub;
import com.vidya.master.hub.entity.HubStatus;
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
public interface HubRepository extends JpaRepository<Hub, UUID> {

    Optional<Hub> findByHubCode(String hubCode);

    boolean existsByHubCode(String hubCode);

    List<Hub> findByStateIgnoreCaseAndStatus(String state, HubStatus status);

    List<Hub> findByStatus(HubStatus status);

    @Query("SELECT h FROM Hub h WHERE " +
           "(CAST(:state AS string) IS NULL OR UPPER(h.state) = UPPER(CAST(:state AS string))) AND " +
           "(CAST(:status AS string) IS NULL OR h.status = :status)")
    Page<Hub> findFiltered(@Param("state") String state,
                           @Param("status") HubStatus status,
                           Pageable pageable);

    @Query("SELECT COUNT(c) FROM Community c WHERE c.hub.id = :hubId")
    long countCommunitiesInHub(@Param("hubId") UUID hubId);

    @Query("SELECT COUNT(s) FROM School s WHERE s.hub.id = :hubId")
    long countSchoolsInHub(@Param("hubId") UUID hubId);
}
