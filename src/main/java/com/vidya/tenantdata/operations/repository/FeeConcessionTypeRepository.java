package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.FeeConcessionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeConcessionTypeRepository extends JpaRepository<FeeConcessionType, UUID> {

    List<FeeConcessionType> findByDeletedAtIsNullOrderByNameAsc();

    Optional<FeeConcessionType> findByIdAndDeletedAtIsNull(UUID id);
}
