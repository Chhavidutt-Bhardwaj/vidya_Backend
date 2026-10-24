package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.ExamSchedule;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, UUID> {

    @Query("SELECT s FROM ExamSchedule s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<ExamSchedule> findActiveById(@Param("id") UUID id);

    @Query("SELECT s FROM ExamSchedule s WHERE s.scheduleCode = :code AND s.deletedAt IS NULL")
    Optional<ExamSchedule> findActiveByCode(@Param("code") String code);

    @Query("SELECT s FROM ExamSchedule s WHERE s.examTerm.id = :termId AND s.deletedAt IS NULL")
    List<ExamSchedule> findByExamTermId(@Param("termId") UUID termId);

    @Query("SELECT s FROM ExamSchedule s WHERE s.examTerm.id = :termId " +
           "AND s.academicClass.id = :classId AND s.deletedAt IS NULL")
    Optional<ExamSchedule> findByTermAndClass(@Param("termId") UUID termId,
                                              @Param("classId") UUID classId);

    @Query("SELECT s FROM ExamSchedule s WHERE s.deletedAt IS NULL " +
           "AND (:termId IS NULL OR s.examTerm.id = :termId) " +
           "AND (:classId IS NULL OR s.academicClass.id = :classId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<ExamSchedule> searchSchedules(@Param("termId") UUID termId,
                                       @Param("classId") UUID classId,
                                       @Param("status") ExamScheduleStatus status,
                                       Pageable pageable);

    @Query("SELECT COUNT(s) > 0 FROM ExamSchedule s WHERE s.examTerm.id = :termId " +
           "AND s.academicClass.id = :classId AND s.deletedAt IS NULL")
    boolean existsByTermAndClass(@Param("termId") UUID termId, @Param("classId") UUID classId);
}
