package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.TransportStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransportStopRepository extends JpaRepository<TransportStop, UUID> {

    List<TransportStop> findByRouteIdOrderByStopNoAsc(UUID routeId);

    List<TransportStop> findByRouteIdInOrderByStopNoAsc(Collection<UUID> routeIds);
}
