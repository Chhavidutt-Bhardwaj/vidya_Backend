package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.TransportVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportVehicleRepository extends JpaRepository<TransportVehicle, UUID> {

    List<TransportVehicle> findByDeletedAtIsNullOrderByRegistrationNoAsc();

    Optional<TransportVehicle> findByIdAndDeletedAtIsNull(UUID id);
}
