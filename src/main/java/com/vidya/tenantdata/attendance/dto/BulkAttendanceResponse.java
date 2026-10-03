package com.vidya.tenantdata.attendance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for bulk attendance upsert operations.
 *
 * <p>Reports the overall counts (created, updated, total) and per-student operation results.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkAttendanceResponse {

    private int created;
    private int updated;
    private int total;
    private List<BulkAttendanceRecordResult> records;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BulkAttendanceRecordResult {
        private UUID studentId;
        private UUID attendanceId;
        private String attendanceCode;
        private String operation; // "CREATED" or "UPDATED"
        private AttendanceStatus status;
        private String remarks;
    }
}
