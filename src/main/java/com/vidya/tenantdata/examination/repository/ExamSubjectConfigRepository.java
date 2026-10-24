package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.ExamSubjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSubjectConfigRepository extends JpaRepository<ExamSubjectConfig, UUID> {

    @Query("SELECT c FROM ExamSubjectConfig c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<ExamSubjectConfig> findActiveById(@Param("id") UUID id);

    @Query("SELECT c FROM ExamSubjectConfig c WHERE c.configCode = :code AND c.deletedAt IS NULL")
    Optional<ExamSubjectConfig> findActiveByCode(@Param("code") String code);

    @Query("SELECT c FROM ExamSubjectConfig c WHERE c.examSchedule.id = :scheduleId AND c.deletedAt IS NULL ORDER BY c.examDate ASC, c.startTime ASC")
    List<ExamSubjectConfig> findByExamScheduleId(@Param("scheduleId") UUID scheduleId);

    @Query("SELECT c FROM ExamSubjectConfig c WHERE c.examSchedule.id = :scheduleId AND c.subject.id = :subjectId AND c.deletedAt IS NULL")
    Optional<ExamSubjectConfig> findByScheduleAndSubject(@Param("scheduleId") UUID scheduleId, @Param("subjectId") UUID subjectId);

    @Query("SELECT COUNT(c) > 0 FROM ExamSubjectConfig c WHERE c.examSchedule.id = :scheduleId AND c.subject.id = :subjectId AND c.deletedAt IS NULL")
    boolean existsByScheduleAndSubject(@Param("scheduleId") UUID scheduleId, @Param("subjectId") UUID subjectId);

    @Query("SELECT c FROM ExamSubjectConfig c WHERE c.examSchedule.id = :scheduleId AND c.examDate = :date AND c.deletedAt IS NULL")
    List<ExamSubjectConfig> findByScheduleAndDate(@Param("scheduleId") UUID scheduleId, @Param("date") LocalDate date);
}
