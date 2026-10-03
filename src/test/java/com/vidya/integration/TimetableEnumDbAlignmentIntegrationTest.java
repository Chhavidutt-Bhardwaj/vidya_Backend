package com.vidya.integration;

import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying all Java enum values in com.vidya.tenantdata.timetable.enums
 * match the database CHECK constraints in PostgreSQL.
 */
@DisplayName("Timetable Enums — DB CHECK Constraint Alignment Integration Tests")
class TimetableEnumDbAlignmentIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    @Test
    @DisplayName("Verify all RoomStatus enum values satisfy chk_room_status constraint")
    void testAllRoomStatusEnumValues() {
        for (RoomStatus status : RoomStatus.values()) {
            UUID id = UUID.randomUUID();
            String code = "RM-ST-" + status.name().substring(0, Math.min(4, status.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO room (id, room_code, name, status) VALUES (?, ?, ?, ?)",
                    id, code, "Room Status " + status.name() + " " + id, status.name()
            );
            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all RoomType enum values satisfy chk_room_type constraint")
    void testAllRoomTypeEnumValues() {
        for (RoomType roomType : RoomType.values()) {
            UUID id = UUID.randomUUID();
            String code = "RM-TY-" + roomType.name().substring(0, Math.min(4, roomType.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO room (id, room_code, name, room_type, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                    id, code, "Room Type " + roomType.name() + " " + id, roomType.name()
            );
            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all BellScheduleStatus enum values satisfy chk_bell_schedule_status constraint")
    void testAllBellScheduleStatusEnumValues() {
        for (BellScheduleStatus status : BellScheduleStatus.values()) {
            UUID id = UUID.randomUUID();
            String code = "SCH-ST-" + status.name().substring(0, Math.min(4, status.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, false, ?)",
                    id, code, "Schedule Status " + status.name() + " " + id, status.name()
            );
            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all PeriodType enum values satisfy chk_bsp_period_type constraint")
    void testAllPeriodTypeEnumValues() {
        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, false, 'ACTIVE')",
                scheduleId, "SCH-PT-" + UUID.randomUUID().toString().substring(0, 8), "Period Type Test Schedule"
        );

        int seq = 1;
        for (PeriodType periodType : PeriodType.values()) {
            UUID periodId = UUID.randomUUID();
            String code = "PER-PT-" + periodType.name().substring(0, Math.min(4, periodType.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            LocalTime start = LocalTime.of(7, 0).plusMinutes((seq - 1) * 45L);
            LocalTime end = start.plusMinutes(40);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    periodId, code, scheduleId, "Period " + periodType.name(), seq++, start, end, periodType.name()
            );
            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all TimetableStatus enum values satisfy chk_timetable_status constraint")
    void testAllTimetableStatusEnumValues() {
        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-TTS-" + UUID.randomUUID().toString().substring(0, 8), "AY for TT Status Test", LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)
        );

        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, false, 'ACTIVE')",
                scheduleId, "SCH-TTS-" + UUID.randomUUID().toString().substring(0, 8), "Schedule for TT Status Test"
        );

        for (TimetableStatus status : TimetableStatus.values()) {
            UUID ttId = UUID.randomUUID();
            String code = "TT-ST-" + status.name().substring(0, Math.min(4, status.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status) VALUES (?, ?, ?, ?, ?, ?)",
                    ttId, code, yearId, scheduleId, "TT Status " + status.name() + " " + ttId, status.name()
            );
            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all TimetableEntryType and DayOfWeek enum values satisfy chk_tte_entry_type and chk_tte_day")
    void testAllTimetableEntryTypeAndDayOfWeekValues() {
        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-TET-" + UUID.randomUUID().toString().substring(0, 8), "AY for Entry Test", LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)
        );

        UUID classId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, ?)",
                classId, "CLS-TET-" + UUID.randomUUID().toString().substring(0, 8), "Class for Entry Test", 1);

        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, false, 'ACTIVE')",
                scheduleId, "SCH-TET-" + UUID.randomUUID().toString().substring(0, 8), "Schedule for Entry Test"
        );

        UUID periodId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                periodId, "PER-TET-" + UUID.randomUUID().toString().substring(0, 8), scheduleId, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), "TEACHING"
        );

        UUID ttId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status) VALUES (?, ?, ?, ?, ?, 'DRAFT')",
                ttId, "TT-TET-" + UUID.randomUUID().toString().substring(0, 8), yearId, scheduleId, "TT for Entry Test"
        );

        // Test entry types
        for (TimetableEntryType entryType : TimetableEntryType.values()) {
            UUID entryId = UUID.randomUUID();
            String code = "TTE-TY-" + entryType.name().substring(0, Math.min(4, entryType.name().length())) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, entry_type) " +
                    "VALUES (?, ?, ?, ?, ?, 'MONDAY', ?, ?)",
                    entryId, code, ttId, yearId, periodId, classId, entryType.name()
            );
            assertThat(inserted).isEqualTo(1);
            // Soft delete immediately to avoid clash in subsequent loop iterations on same class/period
            jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId);
        }

        // Test day of week
        for (DayOfWeek day : DayOfWeek.values()) {
            UUID entryId = UUID.randomUUID();
            String code = "TTE-DAY-" + day.name().substring(0, 3) + "-" + UUID.randomUUID().toString().substring(0, 6);
            int inserted = jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, entry_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'REGULAR')",
                    entryId, code, ttId, yearId, periodId, day.name(), classId
            );
            assertThat(inserted).isEqualTo(1);
            jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId);
        }
    }
}
