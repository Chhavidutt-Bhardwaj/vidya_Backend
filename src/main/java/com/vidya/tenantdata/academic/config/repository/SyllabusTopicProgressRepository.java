package com.vidya.tenantdata.academic.config.repository;

import com.vidya.tenantdata.academic.config.entity.SyllabusTopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyllabusTopicProgressRepository extends JpaRepository<SyllabusTopicProgress, UUID> {

    List<SyllabusTopicProgress> findByTopicIdIn(Collection<UUID> topicIds);

    Optional<SyllabusTopicProgress> findByTopicIdAndSectionId(UUID topicId, UUID sectionId);
}
