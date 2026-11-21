package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.BlueprintSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlueprintSectionRepository extends JpaRepository<BlueprintSection, UUID> {

    List<BlueprintSection> findByBlueprintIdOrderByPosition(UUID blueprintId);

    @Modifying
    @Query("DELETE FROM BlueprintSection s WHERE s.blueprintId = :blueprintId")
    void deleteByBlueprint(@Param("blueprintId") UUID blueprintId);
}
