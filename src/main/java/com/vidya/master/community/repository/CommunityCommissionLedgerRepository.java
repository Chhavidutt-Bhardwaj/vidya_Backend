package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommissionLedgerStatus;
import com.vidya.master.community.entity.CommissionType;
import com.vidya.master.community.entity.CommunityCommissionLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface CommunityCommissionLedgerRepository extends JpaRepository<CommunityCommissionLedgerEntry, UUID> {

    Page<CommunityCommissionLedgerEntry> findByCommunityId(UUID communityId, Pageable pageable);

    Page<CommunityCommissionLedgerEntry> findBySchoolId(UUID schoolId, Pageable pageable);

    Page<CommunityCommissionLedgerEntry> findByCommunityIdAndCommissionType(
            UUID communityId, CommissionType commissionType, Pageable pageable);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM CommunityCommissionLedgerEntry l " +
           "WHERE l.community.id = :communityId AND l.status <> 'CANCELLED'")
    BigDecimal sumTotalEarnedByCommunity(@Param("communityId") UUID communityId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM CommunityCommissionLedgerEntry l " +
           "WHERE l.community.id = :communityId AND l.commissionType = :type AND l.status <> 'CANCELLED'")
    BigDecimal sumEarnedByCommunityAndType(@Param("communityId") UUID communityId,
                                          @Param("type") CommissionType type);
}
