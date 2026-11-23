package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.FeeStructureItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeeStructureItemRepository extends JpaRepository<FeeStructureItem, UUID> {

    List<FeeStructureItem> findByFeeStructureId(UUID feeStructureId);

    List<FeeStructureItem> findByFeeStructureIdIn(Collection<UUID> feeStructureIds);
}
