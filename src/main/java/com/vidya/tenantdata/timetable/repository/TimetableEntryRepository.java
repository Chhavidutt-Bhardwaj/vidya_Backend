package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "LEFT JOIN FETCH e.teacher tea " +
           "LEFT JOIN FETCH e.room r " +
           "WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<TimetableEntry> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT e FROM TimetableEntry e WHERE e.entryCode = :entryCode AND e.deletedAt IS NULL")
    Optional<TimetableEntry> findByEntryCodeAndNotDeleted(@Param("entryCode") String entryCode);

    boolean existsByEntryCode(String entryCode);

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "LEFT JOIN FETCH e.teacher tea " +
           "LEFT JOIN FETCH e.room r " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.dayOfWeek ASC, p.periodSequence ASC")
    List<TimetableEntry> findAllByTimetableIdWithDetails(@Param("timetableId") UUID timetableId);

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "LEFT JOIN FETCH e.teacher tea " +
           "LEFT JOIN FETCH e.room r " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND e.section IS NULL) OR e.section.id = :sectionId OR e.section IS NULL) " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.dayOfWeek ASC, p.periodSequence ASC")
    List<TimetableEntry> findByTimetableAndClassAndSection(
            @Param("timetableId") UUID timetableId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "JOIN FETCH e.teacher tea " +
           "LEFT JOIN FETCH e.room r " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.teacher.id = :teacherId " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.dayOfWeek ASC, p.periodSequence ASC")
    List<TimetableEntry> findByTimetableAndTeacher(
            @Param("timetableId") UUID timetableId,
            @Param("teacherId") UUID teacherId
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "LEFT JOIN FETCH e.teacher tea " +
           "JOIN FETCH e.room r " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.room.id = :roomId " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.dayOfWeek ASC, p.periodSequence ASC")
    List<TimetableEntry> findByTimetableAndRoom(
            @Param("timetableId") UUID timetableId,
            @Param("roomId") UUID roomId
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "JOIN FETCH e.timetable t " +
           "JOIN FETCH e.academicYear ay " +
           "JOIN FETCH e.period p " +
           "JOIN FETCH e.academicClass ac " +
           "LEFT JOIN FETCH e.section sec " +
           "LEFT JOIN FETCH e.subject sub " +
           "LEFT JOIN FETCH e.teacher tea " +
           "LEFT JOIN FETCH e.room r " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.dayOfWeek = :dayOfWeek " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY p.periodSequence ASC")
    List<TimetableEntry> findByTimetableAndDay(
            @Param("timetableId") UUID timetableId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.deletedAt IS NULL " +
           "AND (:academicClassId IS NULL OR e.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR e.section.id = :sectionId) " +
           "AND (:teacherId IS NULL OR e.teacher.id = :teacherId) " +
           "AND (:subjectId IS NULL OR e.subject.id = :subjectId) " +
           "AND (:roomId IS NULL OR e.room.id = :roomId) " +
           "AND (:dayOfWeek IS NULL OR e.dayOfWeek = :dayOfWeek)")
    Page<TimetableEntry> searchEntries(
            @Param("timetableId") UUID timetableId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("teacherId") UUID teacherId,
            @Param("subjectId") UUID subjectId,
            @Param("roomId") UUID roomId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            Pageable pageable
    );

    // Conflict detection queries

    @Query("SELECT e FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.teacher.id = :teacherId " +
           "AND e.dayOfWeek = :dayOfWeek " +
           "AND e.period.id = :periodId " +
           "AND e.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR e.id <> :excludeId)")
    List<TimetableEntry> findTeacherClashes(
            @Param("timetableId") UUID timetableId,
            @Param("teacherId") UUID teacherId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("periodId") UUID periodId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND e.section IS NULL) OR (e.section.id = :sectionId) OR (:sectionId IS NULL) OR (e.section IS NULL)) " +
           "AND e.dayOfWeek = :dayOfWeek " +
           "AND e.period.id = :periodId " +
           "AND e.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR e.id <> :excludeId)")
    List<TimetableEntry> findClassClashes(
            @Param("timetableId") UUID timetableId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("periodId") UUID periodId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT e FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.room.id = :roomId " +
           "AND e.dayOfWeek = :dayOfWeek " +
           "AND e.period.id = :periodId " +
           "AND e.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR e.id <> :excludeId)")
    List<TimetableEntry> findRoomClashes(
            @Param("timetableId") UUID timetableId,
            @Param("roomId") UUID roomId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("periodId") UUID periodId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT COUNT(e) FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND e.section IS NULL) OR e.section.id = :sectionId) " +
           "AND e.subject.id = :subjectId " +
           "AND e.deletedAt IS NULL")
    long countWeeklyPeriodsForClassSubject(
            @Param("timetableId") UUID timetableId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("subjectId") UUID subjectId
    );

    @Query("SELECT COUNT(e) FROM TimetableEntry e " +
           "WHERE e.timetable.id = :timetableId " +
           "AND e.teacher.id = :teacherId " +
           "AND e.deletedAt IS NULL")
    long countWeeklyPeriodsForTeacher(
            @Param("timetableId") UUID timetableId,
            @Param("teacherId") UUID teacherId
    );
}
