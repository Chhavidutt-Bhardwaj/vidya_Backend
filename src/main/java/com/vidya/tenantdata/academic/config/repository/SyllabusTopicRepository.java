package com.vidya.tenantdata.academic.config.repository;

import com.vidya.tenantdata.academic.config.entity.SyllabusTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, UUID> {

    List<SyllabusTopic> findBySyllabusIdOrderBySortOrderAsc(UUID syllabusId);

    List<SyllabusTopic> findBySyllabusIdInOrderBySortOrderAsc(Collection<UUID> syllabusIds);
}
