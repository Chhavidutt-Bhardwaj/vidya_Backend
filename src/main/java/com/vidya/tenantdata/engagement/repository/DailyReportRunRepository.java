package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.DailyReportRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyReportRunRepository extends JpaRepository<DailyReportRun, UUID> {

    Optional<DailyReportRun> findByReportDate(LocalDate reportDate);

    List<DailyReportRun> findAllByOrderByReportDateDesc(Pageable pageable);

    /**
     * Claims the day for this server: 1 = claimed, 0 = another run already has the day.
     * Safe across servers because of the unique index on report_date.
     */
    @Modifying
    @Query(value = "INSERT INTO daily_report_run (id, report_date, run_trigger, status, started_by) "
            + "VALUES (gen_random_uuid(), :d, :trigger, 'RUNNING', :by) ON CONFLICT (report_date) DO NOTHING", nativeQuery = true)
    int claim(@Param("d") LocalDate date, @Param("trigger") String trigger, @Param("by") UUID startedBy);

    /** Re-claims a day whose run is not RUNNING (a manual re-send, or a retry after FAILED). 1 = claimed. */
    @Modifying
    @Query(value = "UPDATE daily_report_run SET status = 'RUNNING', run_trigger = :trigger, started_by = :by, started_at = NOW(), "
            + "finished_at = NULL, error = NULL, skip_reason = NULL WHERE report_date = :d AND status <> 'RUNNING'", nativeQuery = true)
    int reclaim(@Param("d") LocalDate date, @Param("trigger") String trigger, @Param("by") UUID startedBy);
}
