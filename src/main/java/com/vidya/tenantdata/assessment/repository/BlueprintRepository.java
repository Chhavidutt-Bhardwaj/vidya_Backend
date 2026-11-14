package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.Blueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlueprintRepository extends JpaRepository<Blueprint, UUID> {

    Optional<Blueprint> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT b FROM Blueprint b WHERE b.deletedAt IS NULL AND (:subjectId IS NULL OR b.subjectId = :subjectId) "
            + "AND (:classId IS NULL OR b.academicClassId = :classId) ORDER BY b.name")
    List<Blueprint> search(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId);
}
