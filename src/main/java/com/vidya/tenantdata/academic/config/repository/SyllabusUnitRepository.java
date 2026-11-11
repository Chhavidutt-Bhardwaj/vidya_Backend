package com.vidya.tenantdata.academic.config.repository;

import com.vidya.tenantdata.academic.config.entity.SyllabusUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SyllabusUnitRepository extends JpaRepository<SyllabusUnit, UUID> {

    List<SyllabusUnit> findBySyllabusIdOrderByUnitNoAsc(UUID syllabusId);

    List<SyllabusUnit> findBySyllabusIdInOrderByUnitNoAsc(Collection<UUID> syllabusIds);
}
