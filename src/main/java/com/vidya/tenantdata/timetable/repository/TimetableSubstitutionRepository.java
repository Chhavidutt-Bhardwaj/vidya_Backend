package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.TimetableSubstitution;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimetableSubstitutionRepository extends JpaRepository<TimetableSubstitution, UUID> {

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<TimetableSubstitution> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.substitutionCode = :code AND s.deletedAt IS NULL")
    Optional<TimetableSubstitution> findBySubstitutionCodeAndNotDeleted(@Param("code") String code);

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.timetable.id = :timetableId AND s.substitutionDate = :date AND s.period.id = :periodId AND s.academicClass.id = :classId AND (s.section.id = :sectionId OR (:sectionId IS NULL AND s.section IS NULL)) AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    List<TimetableSubstitution> findActiveSlotSubstitutions(
            @Param("timetableId") UUID timetableId,
            @Param("date") LocalDate date,
            @Param("periodId") UUID periodId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId
    );

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.substitutionDate = :date AND s.period.id = :periodId AND s.substituteTeacher.id = :teacherId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL AND (:excludeId IS NULL OR s.id != :excludeId)")
    List<TimetableSubstitution> findActiveTeacherSubstitutions(
            @Param("date") LocalDate date,
            @Param("periodId") UUID periodId,
            @Param("teacherId") UUID teacherId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.substitutionDate = :date AND s.period.id = :periodId AND s.substituteRoom.id = :roomId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL AND (:excludeId IS NULL OR s.id != :excludeId)")
    List<TimetableSubstitution> findActiveRoomSubstitutions(
            @Param("date") LocalDate date,
            @Param("periodId") UUID periodId,
            @Param("roomId") UUID roomId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.timetable.id = :timetableId AND s.substitutionDate = :date AND s.period.id = :periodId AND s.originalTeacher.id = :teacherId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL AND (s.substitutionType = 'CLASS_CANCELLED' OR s.substitutionType = 'TEACHER_SUBSTITUTE')")
    List<TimetableSubstitution> findActiveFreedTeacherEntries(
            @Param("timetableId") UUID timetableId,
            @Param("date") LocalDate date,
            @Param("periodId") UUID periodId,
            @Param("teacherId") UUID teacherId
    );

    @Query("SELECT s FROM TimetableSubstitution s WHERE s.deletedAt IS NULL " +
            "AND (:dateFrom IS NULL OR s.substitutionDate >= :dateFrom) " +
            "AND (:dateTo IS NULL OR s.substitutionDate <= :dateTo) " +
            "AND (:sectionId IS NULL OR s.section.id = :sectionId) " +
            "AND (:teacherId IS NULL OR s.substituteTeacher.id = :teacherId OR s.originalTeacher.id = :teacherId) " +
            "AND (:status IS NULL OR s.status = :status)")
    Page<TimetableSubstitution> searchSubstitutions(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("sectionId") UUID sectionId,
            @Param("teacherId") UUID teacherId,
            @Param("status") SubstitutionStatus status,
            Pageable pageable
    );

    @Query("SELECT s FROM TimetableSubstitution s " +
            "WHERE s.timetable.id = :timetableId " +
            "AND s.section.id = :sectionId " +
            "AND s.substitutionDate >= :startDate " +
            "AND s.substitutionDate <= :endDate " +
            "AND s.status = 'ACTIVE' " +
            "AND s.deletedAt IS NULL")
    List<TimetableSubstitution> findActiveForSectionInRange(
            @Param("timetableId") UUID timetableId,
            @Param("sectionId") UUID sectionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM TimetableSubstitution s " +
            "WHERE s.timetable.id = :timetableId " +
            "AND (s.originalTeacher.id = :teacherId OR s.substituteTeacher.id = :teacherId) " +
            "AND s.substitutionDate >= :startDate " +
            "AND s.substitutionDate <= :endDate " +
            "AND s.status = 'ACTIVE' " +
            "AND s.deletedAt IS NULL")
    List<TimetableSubstitution> findActiveForTeacherInRange(
            @Param("timetableId") UUID timetableId,
            @Param("teacherId") UUID teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM TimetableSubstitution s " +
            "WHERE s.timetable.id = :timetableId " +
            "AND s.section.id = :sectionId " +
            "AND s.substitutionDate = :date " +
            "AND s.status = 'ACTIVE' " +
            "AND s.deletedAt IS NULL")
    List<TimetableSubstitution> findActiveForSectionOnDate(
            @Param("timetableId") UUID timetableId,
            @Param("sectionId") UUID sectionId,
            @Param("date") LocalDate date
    );

    @Query("SELECT s FROM TimetableSubstitution s " +
            "WHERE s.timetable.id = :timetableId " +
            "AND (s.originalTeacher.id = :teacherId OR s.substituteTeacher.id = :teacherId) " +
            "AND s.substitutionDate = :date " +
            "AND s.status = 'ACTIVE' " +
            "AND s.deletedAt IS NULL")
    List<TimetableSubstitution> findActiveForTeacherOnDate(
            @Param("timetableId") UUID timetableId,
            @Param("teacherId") UUID teacherId,
            @Param("date") LocalDate date
    );
}
