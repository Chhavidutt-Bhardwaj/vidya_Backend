package com.vidya.master.school.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.master.school.dto.SchoolCampusesResponse;
import com.vidya.master.school.service.SchoolCampusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard header: the signed-in school and its campuses. Lives in master because schools and branches are master data. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "School overview: KPIs, school snapshot, live operations, quick counts, trends and action items")
public class SchoolCampusController {

    private final SchoolCampusService campusService;

    @GetMapping("/campuses")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "School and campuses for the header switcher",
            description = "Name, code, board and status of the signed-in school and its branches (main branch first).")
    public ResponseEntity<ApiResponse<SchoolCampusesResponse>> campuses() {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_CAMPUSES", "School campuses", campusService.currentSchoolCampuses()));
    }
}
