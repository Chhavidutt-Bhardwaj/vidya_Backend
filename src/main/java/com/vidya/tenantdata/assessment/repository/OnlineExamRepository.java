package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.OnlineExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamRepository extends JpaRepository<OnlineExam, UUID> {

    Optional<OnlineExam> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT e FROM OnlineExam e WHERE e.deletedAt IS NULL AND (:classId IS NULL OR e.academicClassId = :classId) "
            + "AND (:sectionId IS NULL OR e.sectionId IS NULL OR e.sectionId = :sectionId) ORDER BY e.opensAt DESC")
    List<OnlineExam> search(@Param("classId") UUID classId, @Param("sectionId") UUID sectionId);
}
