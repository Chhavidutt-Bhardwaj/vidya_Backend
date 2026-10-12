package com.vidya.tenantdata.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.attendance.dto.AttendanceDetailResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceSummaryResponse;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.DailySectionAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.MarkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceStatusRequest;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.attendance.service.StudentAttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAttendanceController — REST API Unit Tests")
class StudentAttendanceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentAttendanceService service;

    @InjectMocks
    private StudentAttendanceController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID attendanceId = UUID.randomUUID();
    private final UUID studentId    = UUID.randomUUID();
    private final UUID yearId       = UUID.randomUUID();
    private final UUID classId      = UUID.randomUUID();
    private final UUID sectionId    = UUID.randomUUID();
    private final LocalDate TODAY   = LocalDate.now();

    private AttendanceDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        sampleDetail = AttendanceDetailResponse.builder()
                .id(attendanceId)
                .attendanceCode("ATT-A1B2C3D4")
                .studentId(studentId)
                .studentCode("STU-A1B2C3D4")
                .studentName("Aarav Sharma")
                .academicYearId(yearId)
                .academicYearCode("AY2026-27")
                .academicClassId(classId)
                .academicClassName("Class 5")
                .sectionId(sectionId)
                .sectionName("Section A")
                .attendanceDate(TODAY)
                .status(AttendanceStatus.PRESENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    // ── POST /attendance ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /attendance — 201 Created")
    void markAttendance_returns201() throws Exception {
        when(service.markAttendance(any(), any())).thenReturn(sampleDetail);

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_MARKED"))
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.attendanceCode").value("ATT-A1B2C3D4"));
    }

    @Test
    @DisplayName("POST /attendance — missing studentId — 400")
    void markAttendance_missingStudentId_returns400() throws Exception {
        MarkAttendanceRequest request = new MarkAttendanceRequest(
                null, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /attendance — missing attendanceDate — 400")
    void markAttendance_missingDate_returns400() throws Exception {
        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, null, AttendanceStatus.PRESENT, null);

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /attendance/bulk ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /attendance/bulk — 200 OK Upsert")
    void markBulkAttendance_returns200() throws Exception {
        BulkAttendanceResponse bulkResponse = BulkAttendanceResponse.builder()
                .created(1)
                .updated(0)
                .total(1)
                .records(List.of(BulkAttendanceResponse.BulkAttendanceRecordResult.builder()
                        .studentId(studentId)
                        .attendanceId(sampleDetail.getId())
                        .attendanceCode(sampleDetail.getAttendanceCode())
                        .operation("CREATED")
                        .status(AttendanceStatus.PRESENT)
                        .build()))
                .build();
        when(service.markBulkAttendance(any(), any())).thenReturn(bulkResponse);

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, null))
        );

        mockMvc.perform(post("/api/v1/attendance/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BULK_ATTENDANCE_PROCESSED"))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].operation").value("CREATED"))
                .andExpect(jsonPath("$.data.records[0].status").value("PRESENT"));
    }

    @Test
    @DisplayName("POST /attendance/bulk — empty records — 400")
    void markBulkAttendance_emptyRecords_returns400() throws Exception {
        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY, List.of()
        );

        mockMvc.perform(post("/api/v1/attendance/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /attendance/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /attendance/{id} — 200 OK")
    void getAttendance_returns200() throws Exception {
        when(service.getAttendance(attendanceId)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/attendance/{id}", attendanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(attendanceId.toString()));
    }

    @Test
    @DisplayName("GET /attendance/{id} — not found — 404")
    void getAttendance_notFound_returns404() throws Exception {
        when(service.getAttendance(attendanceId)).thenThrow(new ResourceNotFoundException("StudentAttendance", attendanceId));

        mockMvc.perform(get("/api/v1/attendance/{id}", attendanceId))
                .andExpect(status().isNotFound());
    }

    // ── PUT /attendance/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /attendance/{id} — 200 OK")
    void updateAttendance_returns200() throws Exception {
        AttendanceDetailResponse updated = AttendanceDetailResponse.builder()
                .id(attendanceId).attendanceCode("ATT-A1B2C3D4").status(AttendanceStatus.LATE)
                .attendanceDate(TODAY).version(1L).build();
        when(service.updateAttendance(eq(attendanceId), any(), any())).thenReturn(updated);

        UpdateAttendanceRequest request = new UpdateAttendanceRequest(AttendanceStatus.LATE, "Was late");

        mockMvc.perform(put("/api/v1/attendance/{id}", attendanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_UPDATED"))
                .andExpect(jsonPath("$.data.status").value("LATE"));
    }

    @Test
    @DisplayName("PUT /attendance/{id} — missing status — 400")
    void updateAttendance_missingStatus_returns400() throws Exception {
        UpdateAttendanceRequest request = new UpdateAttendanceRequest(null, "No status");

        mockMvc.perform(put("/api/v1/attendance/{id}", attendanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /attendance/{id}/status ─────────────────────────────────────────

    @Test
    @DisplayName("PATCH /attendance/{id}/status — 200 OK")
    void updateAttendanceStatus_returns200() throws Exception {
        AttendanceDetailResponse patched = AttendanceDetailResponse.builder()
                .id(attendanceId).attendanceCode("ATT-A1B2C3D4").status(AttendanceStatus.EXCUSED)
                .attendanceDate(TODAY).version(2L).build();
        when(service.updateAttendanceStatus(eq(attendanceId), any(), any())).thenReturn(patched);

        UpdateAttendanceStatusRequest request = new UpdateAttendanceStatusRequest(AttendanceStatus.EXCUSED);

        mockMvc.perform(patch("/api/v1/attendance/{id}/status", attendanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXCUSED"));
    }

    // ── DELETE /attendance/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /attendance/{id} — 200 OK")
    void deleteAttendance_returns200() throws Exception {
        doNothing().when(service).deleteAttendance(attendanceId);

        mockMvc.perform(delete("/api/v1/attendance/{id}", attendanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_DELETED"));
    }

    @Test
    @DisplayName("DELETE /attendance/{id} — not found — 404")
    void deleteAttendance_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("StudentAttendance", attendanceId))
                .when(service).deleteAttendance(attendanceId);

        mockMvc.perform(delete("/api/v1/attendance/{id}", attendanceId))
                .andExpect(status().isNotFound());
    }

    // ── GET /attendance/students/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /attendance/students/{id} — 200 OK")
    void getStudentAttendanceHistory_returns200() throws Exception {
        AttendanceResponse summary = AttendanceResponse.builder()
                .id(attendanceId).studentId(studentId).status(AttendanceStatus.PRESENT).attendanceDate(TODAY).build();
        when(service.getStudentAttendanceHistory(eq(studentId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/attendance/students/{studentId}", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDENT_ATTENDANCE_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].status").value("PRESENT"));
    }

    // ── GET /attendance/students/{id}/summary ─────────────────────────────────

    @Test
    @DisplayName("GET /attendance/students/{id}/summary — 200 OK")
    void getAttendanceSummary_returns200() throws Exception {
        AttendanceSummaryResponse summaryResponse = AttendanceSummaryResponse.builder()
                .studentId(studentId)
                .studentCode("STU-A1B2C3D4")
                .academicYearId(yearId)
                .totalDays(10L)
                .presentCount(9L)
                .absentCount(1L)
                .lateCount(0L)
                .halfDayCount(0L)
                .excusedCount(0L)
                .attendancePercentage(BigDecimal.valueOf(90.00))
                .build();
        when(service.getAttendanceSummary(eq(studentId), eq(yearId))).thenReturn(summaryResponse);

        mockMvc.perform(get("/api/v1/attendance/students/{studentId}/summary", studentId)
                        .param("academicYearId", yearId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_SUMMARY_RETRIEVED"))
                .andExpect(jsonPath("$.data.totalDays").value(10))
                .andExpect(jsonPath("$.data.presentCount").value(9));
    }

    // ── GET /attendance/sections/{id}/daily ───────────────────────────────────

    @Test
    @DisplayName("GET /attendance/sections/{id}/daily — 200 OK")
    void getDailySectionAttendance_returns200() throws Exception {
        DailySectionAttendanceResponse daily = DailySectionAttendanceResponse.builder()
                .sectionId(sectionId)
                .sectionName("Section A")
                .academicYearId(yearId)
                .attendanceDate(TODAY)
                .totalStudents(30)
                .markedCount(28)
                .entries(List.of())
                .build();
        when(service.getDailySectionAttendance(eq(yearId), eq(sectionId), eq(TODAY))).thenReturn(daily);

        mockMvc.perform(get("/api/v1/attendance/sections/{sectionId}/daily", sectionId)
                        .param("academicYearId", yearId.toString())
                        .param("date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DAILY_ATTENDANCE_RETRIEVED"))
                .andExpect(jsonPath("$.data.totalStudents").value(30))
                .andExpect(jsonPath("$.data.markedCount").value(28));
    }

    // ── GET /attendance/sections/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /attendance/sections/{id} — 200 OK")
    void getSectionAttendanceHistory_returns200() throws Exception {
        AttendanceResponse entry = AttendanceResponse.builder()
                .id(attendanceId).sectionId(sectionId).status(AttendanceStatus.ABSENT).attendanceDate(TODAY).build();
        when(service.getSectionAttendanceHistory(eq(sectionId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/api/v1/attendance/sections/{sectionId}", sectionId)
                        .param("fromDate", TODAY.minusDays(7).toString())
                        .param("toDate", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SECTION_ATTENDANCE_RETRIEVED"));
    }

    // ── GET /attendance/missing ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /attendance/missing — 200 OK")
    void getMissingAttendanceDates_returns200() throws Exception {
        when(service.getMissingAttendanceDates(eq(sectionId), eq(yearId), eq(TODAY.minusDays(5)), eq(TODAY)))
                .thenReturn(List.of(TODAY.minusDays(3), TODAY.minusDays(1)));

        mockMvc.perform(get("/api/v1/attendance/missing")
                        .param("sectionId", sectionId.toString())
                        .param("academicYearId", yearId.toString())
                        .param("from", TODAY.minusDays(5).toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSING_ATTENDANCE_DATES_RETRIEVED"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value(TODAY.minusDays(3).toString()));
    }
}
