package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Class Student Rosters", description = "Student roster retrieval by class and section")
public class ClassStudentController {

    private final StudentEnrollmentService enrollmentService;

    @GetMapping({
            "/api/v1/tenant/classes/{classId}/sections/{sectionId}/students",
            "/api/v1/classes/{classId}/sections/{sectionId}/students"
    })
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student roster for class and section", description = "Lists students enrolled in a specific class and section with search and pagination")
    public ResponseEntity<PagedApiResponse<ClassSectionStudentResponse>> getSectionStudents(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) StudentEnrollmentStatus status,
            @RequestParam(required = false) String q,
            @Parameter(hidden = true)
            @PageableDefault(size = 50, sort = "rollNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: Get students for class [{}] section [{}] year [{}]", classId, sectionId, academicYearId);
        Page<ClassSectionStudentResponse> page = enrollmentService.getClassSectionStudents(classId, sectionId, academicYearId, status, q, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("CLASS_SECTION_STUDENTS_FETCHED", "Class section students retrieved successfully", page.getContent(), page));
    }

    @GetMapping({
            "/api/v1/tenant/classes/{classId}/students",
            "/api/v1/classes/{classId}/students"
    })
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student roster for class", description = "Lists all students enrolled in a class across all sections with search and pagination")
    public ResponseEntity<PagedApiResponse<ClassSectionStudentResponse>> getClassStudents(
            @PathVariable UUID classId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) StudentEnrollmentStatus status,
            @RequestParam(required = false) String q,
            @Parameter(hidden = true)
            @PageableDefault(size = 50, sort = "rollNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: Get students for class [{}] year [{}]", classId, academicYearId);
        Page<ClassSectionStudentResponse> page = enrollmentService.getClassSectionStudents(classId, null, academicYearId, status, q, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("CLASS_STUDENTS_FETCHED", "Class students retrieved successfully", page.getContent(), page));
    }
}
