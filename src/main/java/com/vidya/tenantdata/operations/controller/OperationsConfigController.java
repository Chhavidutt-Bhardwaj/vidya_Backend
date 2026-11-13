package com.vidya.tenantdata.operations.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CategoryRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CategoryResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.ConcessionRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.ConcessionResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyResult;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyStructuresRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeHeadRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeHeadResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.LibrarySettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.LibrarySettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.OperationsOverview;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.StructureStatusRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.VehicleRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.VehicleResponse;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.service.AttendancePolicyService;
import com.vidya.tenantdata.operations.service.CommunicationConfigService;
import com.vidya.tenantdata.operations.service.FeeConfigService;
import com.vidya.tenantdata.operations.service.LibraryConfigService;
import com.vidya.tenantdata.operations.service.OperationsOverviewService;
import com.vidya.tenantdata.operations.service.TransportConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Operational configuration: fee setup, attendance rules, transport, library rules and communication, plus a readiness overview. */
@RestController
@RequestMapping("/api/v1/operations-config")
@RequiredArgsConstructor
@Tag(name = "Operational Configuration", description = "Fee setup, attendance rules, transport, library rules, communication and a readiness overview. "
        + "Timetable setup: /api/v1/bell-schedules, /api/v1/rooms, /api/v1/timetables; examination setup: /api/v1/exams/*")
public class OperationsConfigController {

    private final OperationsOverviewService overviewService;
    private final FeeConfigService feeService;
    private final AttendancePolicyService attendanceService;
    private final TransportConfigService transportService;
    private final LibraryConfigService libraryService;
    private final CommunicationConfigService communicationService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('OPERATIONS_CONFIG_READ')")
    @Operation(summary = "How far operations are set up",
            description = "One entry each for fees, attendance, timetable, examinations, transport, library and communication: state, counts and what is "
                    + "still missing. Areas the user cannot read come back with available=false. Default: the current year.")
    public ResponseEntity<ApiResponse<OperationsOverview>> overview(@RequestParam(required = false) UUID academicYearId) {
        return ok("OPERATIONS_OVERVIEW", "Operational configuration", overviewService.overview(academicYearId));
    }

    // ── Fees ─────────────────────────────────────────────────────────────────

    @GetMapping("/fees/heads")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Fee heads")
    public ResponseEntity<ApiResponse<List<FeeHeadResponse>>> feeHeads() {
        return ok("FEE_HEADS", "Fee heads", feeService.heads());
    }

    @PostMapping("/fees/heads")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Add a fee head", description = "code is made from the name when left out")
    public ResponseEntity<ApiResponse<FeeHeadResponse>> createFeeHead(@Valid @RequestBody FeeHeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("FEE_HEAD_CREATED", "Fee head added", feeService.createHead(request)));
    }

    @PostMapping("/fees/heads/standard")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Add the standard fee heads", description = "Tuition, admission, annual charges, exam, lab, library, activity, transport and "
            + "caution deposit. Heads whose code or name already exists are skipped. Returns all heads.")
    public ResponseEntity<ApiResponse<List<FeeHeadResponse>>> standardFeeHeads() {
        return ok("FEE_HEADS", "Standard fee heads added", feeService.addStandardHeads());
    }

    @PutMapping("/fees/heads/{id}")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Change a fee head", description = "The frequency cannot change while fee structures use the head")
    public ResponseEntity<ApiResponse<FeeHeadResponse>> updateFeeHead(@PathVariable UUID id, @Valid @RequestBody FeeHeadRequest request) {
        return ok("FEE_HEAD_UPDATED", "Fee head saved", feeService.updateHead(id, request));
    }

    @DeleteMapping("/fees/heads/{id}")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Delete a fee head", description = "Refused while a fee structure or concession type uses it")
    public ResponseEntity<ApiResponse<Void>> deleteFeeHead(@PathVariable UUID id) {
        feeService.deleteHead(id);
        return ok("FEE_HEAD_DELETED", "Fee head deleted", null);
    }

    @GetMapping("/fees/settings")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Fee settings of a year", description = "The defaults (saved=false) until the school saves its own")
    public ResponseEntity<ApiResponse<FeeSettingsResponse>> feeSettings(@RequestParam UUID academicYearId) {
        return ok("FEE_SETTINGS", "Fee settings", feeService.settings(academicYearId));
    }

    @PutMapping("/fees/settings")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Save the fee settings of a year", description = "Collection frequency, due day, grace days, late fine, receipt prefix")
    public ResponseEntity<ApiResponse<FeeSettingsResponse>> saveFeeSettings(@Valid @RequestBody FeeSettingsRequest request) {
        return ok("FEE_SETTINGS_SAVED", "Fee settings saved", feeService.saveSettings(request));
    }

    @GetMapping("/fees/installments")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Instalment plan of a year")
    public ResponseEntity<ApiResponse<List<InstallmentResponse>>> installments(@RequestParam UUID academicYearId) {
        return ok("FEE_INSTALLMENTS", "Instalments", feeService.installments(academicYearId));
    }

    @PutMapping("/fees/installments")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Replace the instalment plan", description = "Due dates in rising order; percents add up to 100")
    public ResponseEntity<ApiResponse<List<InstallmentResponse>>> saveInstallments(@Valid @RequestBody InstallmentsRequest request) {
        return ok("FEE_INSTALLMENTS_SAVED", "Instalments saved", feeService.saveInstallments(request));
    }

    @PostMapping("/fees/installments/generate")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Generate the instalment plan", description = "Equal shares, one per collection period of the year's fee settings, due on the due day. Replaces the current plan.")
    public ResponseEntity<ApiResponse<List<InstallmentResponse>>> generateInstallments(@RequestParam UUID academicYearId) {
        return ok("FEE_INSTALLMENTS_SAVED", "Instalments generated", feeService.generateInstallments(academicYearId));
    }

    @GetMapping("/fees/structures")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Fee structures of a year", description = "With totals and what falls due on each instalment; optionally one class")
    public ResponseEntity<ApiResponse<List<FeeStructureResponse>>> feeStructures(@RequestParam UUID academicYearId, @RequestParam(required = false) UUID classId) {
        return ok("FEE_STRUCTURES", "Fee structures", feeService.structures(academicYearId, classId));
    }

    @PutMapping("/fees/structures")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Save a fee structure for one or more classes",
            description = "Each class gets its own structure with the heads and amounts given; an existing one for the same students is replaced. "
                    + "Amounts are per occurrence of the head's frequency.")
    public ResponseEntity<ApiResponse<List<FeeStructureResponse>>> saveFeeStructure(@Valid @RequestBody FeeStructureRequest request) {
        return ok("FEE_STRUCTURES_SAVED", "Fee structure saved", feeService.saveStructure(request));
    }

    @PatchMapping("/fees/structures/{id}/status")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Make a fee structure DRAFT, ACTIVE or ARCHIVED")
    public ResponseEntity<ApiResponse<FeeStructureResponse>> feeStructureStatus(@PathVariable UUID id, @Valid @RequestBody StructureStatusRequest request) {
        return ok("FEE_STRUCTURE_STATUS", "Fee structure is now " + request.status(), feeService.setStructureStatus(id, request.status()));
    }

    @DeleteMapping("/fees/structures/{id}")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Delete a fee structure")
    public ResponseEntity<ApiResponse<Void>> deleteFeeStructure(@PathVariable UUID id) {
        feeService.deleteStructure(id);
        return ok("FEE_STRUCTURE_DELETED", "Fee structure deleted", null);
    }

    @PostMapping("/fees/structures/copy")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Copy fee structures from another year",
            description = "Classes that already have a structure in the target year are skipped. Copies are DRAFT. increasePercent raises every amount; "
                    + "roundTo rounds it (e.g. 50 → nearest 50).")
    public ResponseEntity<ApiResponse<CopyResult>> copyFeeStructures(@Valid @RequestBody CopyStructuresRequest request) {
        return ok("FEE_STRUCTURES_COPIED", "Fee structures copied", feeService.copyStructures(request));
    }

    @GetMapping("/fees/concessions")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Concession types")
    public ResponseEntity<ApiResponse<List<ConcessionResponse>>> concessions() {
        return ok("FEE_CONCESSIONS", "Concession types", feeService.concessions());
    }

    @PostMapping("/fees/concessions")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Add a concession type", description = "A percent or fixed amount off one fee head, or off all when feeHeadId is left out")
    public ResponseEntity<ApiResponse<ConcessionResponse>> createConcession(@Valid @RequestBody ConcessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("FEE_CONCESSION_CREATED", "Concession type added", feeService.saveConcession(null, request)));
    }

    @PutMapping("/fees/concessions/{id}")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Change a concession type")
    public ResponseEntity<ApiResponse<ConcessionResponse>> updateConcession(@PathVariable UUID id, @Valid @RequestBody ConcessionRequest request) {
        return ok("FEE_CONCESSION_UPDATED", "Concession type saved", feeService.saveConcession(id, request));
    }

    @DeleteMapping("/fees/concessions/{id}")
    @PreAuthorize("hasAuthority('FEE_CONFIGURE')")
    @Operation(summary = "Delete a concession type")
    public ResponseEntity<ApiResponse<Void>> deleteConcession(@PathVariable UUID id) {
        feeService.deleteConcession(id);
        return ok("FEE_CONCESSION_DELETED", "Concession type deleted", null);
    }

    // ── Attendance ───────────────────────────────────────────────────────────

    @GetMapping("/attendance/settings")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ')")
    @Operation(summary = "Attendance rules", description = "The defaults (saved=false) until the school saves its own")
    public ResponseEntity<ApiResponse<AttendanceSettingsResponse>> attendanceSettings() {
        return ok("ATTENDANCE_SETTINGS", "Attendance rules", attendanceService.get());
    }

    @PutMapping("/attendance/settings")
    @PreAuthorize("hasAuthority('ATTENDANCE_CONFIGURE')")
    @Operation(summary = "Save the attendance rules",
            description = "backdateDays: how far back a teacher may mark or correct (null = no limit; whole-school attendance staff are never limited). "
                    + "lateCountsAs: how LATE counts in the percentage. minimumPercent: below it a student is flagged in the attendance summary.")
    public ResponseEntity<ApiResponse<AttendanceSettingsResponse>> saveAttendanceSettings(@Valid @RequestBody AttendanceSettingsRequest request) {
        return ok("ATTENDANCE_SETTINGS_SAVED", "Attendance rules saved", attendanceService.save(request));
    }

    // ── Transport ────────────────────────────────────────────────────────────

    @GetMapping("/transport/vehicles")
    @PreAuthorize("hasAuthority('TRANSPORT_READ')")
    @Operation(summary = "Vehicles", description = "With crew, routes and papers that have expired or expire within 30 days")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> vehicles() {
        return ok("TRANSPORT_VEHICLES", "Vehicles", transportService.vehicles());
    }

    @PostMapping("/transport/vehicles")
    @PreAuthorize("hasAuthority('TRANSPORT_MANAGE')")
    @Operation(summary = "Add a vehicle", description = "The driver is a staff member (driverStaffId) or, for an outside driver, a name and phone")
    public ResponseEntity<ApiResponse<VehicleResponse>> createVehicle(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("TRANSPORT_VEHICLE_CREATED", "Vehicle added", transportService.saveVehicle(null, request)));
    }

    @PutMapping("/transport/vehicles/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_MANAGE')")
    @Operation(summary = "Change a vehicle")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateVehicle(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return ok("TRANSPORT_VEHICLE_UPDATED", "Vehicle saved", transportService.saveVehicle(id, request));
    }

    @DeleteMapping("/transport/vehicles/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_MANAGE')")
    @Operation(summary = "Delete a vehicle", description = "Refused while a route uses it")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable UUID id) {
        transportService.deleteVehicle(id);
        return ok("TRANSPORT_VEHICLE_DELETED", "Vehicle deleted", null);
    }

    @GetMapping("/transport/routes")
    @PreAuthorize("hasAuthority('TRANSPORT_READ')")
    @Operation(summary = "Routes with their stops")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> routes() {
        return ok("TRANSPORT_ROUTES", "Routes", transportService.routes());
    }

    @PutMapping("/transport/routes")
    @PreAuthorize("hasAuthority('TRANSPORT_MANAGE')")
    @Operation(summary = "Save a route with its stops",
            description = "No id creates the route. Stops are kept in the order sent; send a stop's id to keep it, leave a stop out to remove it.")
    public ResponseEntity<ApiResponse<RouteResponse>> saveRoute(@Valid @RequestBody RouteRequest request) {
        return ok("TRANSPORT_ROUTE_SAVED", "Route saved", transportService.saveRoute(request));
    }

    @DeleteMapping("/transport/routes/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_MANAGE')")
    @Operation(summary = "Delete a route")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable UUID id) {
        transportService.deleteRoute(id);
        return ok("TRANSPORT_ROUTE_DELETED", "Route deleted", null);
    }

    // ── Library ──────────────────────────────────────────────────────────────

    @GetMapping("/library/settings")
    @PreAuthorize("hasAuthority('LIBRARY_READ')")
    @Operation(summary = "Lending rules", description = "The defaults (saved=false) until the school saves its own")
    public ResponseEntity<ApiResponse<LibrarySettingsResponse>> librarySettings() {
        return ok("LIBRARY_SETTINGS", "Lending rules", libraryService.settings());
    }

    @PutMapping("/library/settings")
    @PreAuthorize("hasAuthority('LIBRARY_CONFIGURE')")
    @Operation(summary = "Save the lending rules", description = "Books and days per student / staff member, renewals, fine per day and its cap, lost-book charge")
    public ResponseEntity<ApiResponse<LibrarySettingsResponse>> saveLibrarySettings(@Valid @RequestBody LibrarySettingsRequest request) {
        return ok("LIBRARY_SETTINGS_SAVED", "Lending rules saved", libraryService.saveSettings(request));
    }

    @GetMapping("/library/categories")
    @PreAuthorize("hasAuthority('LIBRARY_READ')")
    @Operation(summary = "Book categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categories() {
        return ok("LIBRARY_CATEGORIES", "Book categories", libraryService.categories());
    }

    @PostMapping("/library/categories")
    @PreAuthorize("hasAuthority('LIBRARY_CONFIGURE')")
    @Operation(summary = "Add a book category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("LIBRARY_CATEGORY_CREATED", "Category added", libraryService.saveCategory(null, request)));
    }

    @PutMapping("/library/categories/{id}")
    @PreAuthorize("hasAuthority('LIBRARY_CONFIGURE')")
    @Operation(summary = "Change a book category")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ok("LIBRARY_CATEGORY_UPDATED", "Category saved", libraryService.saveCategory(id, request));
    }

    @DeleteMapping("/library/categories/{id}")
    @PreAuthorize("hasAuthority('LIBRARY_CONFIGURE')")
    @Operation(summary = "Delete a book category")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        libraryService.deleteCategory(id);
        return ok("LIBRARY_CATEGORY_DELETED", "Category deleted", null);
    }

    // ── Communication ────────────────────────────────────────────────────────

    @GetMapping("/communication/settings")
    @PreAuthorize("hasAuthority('COMMUNICATION_READ')")
    @Operation(summary = "Channels and sender", description = "The defaults (saved=false, in-app only) until the school saves its own")
    public ResponseEntity<ApiResponse<CommunicationSettingsResponse>> communicationSettings() {
        return ok("COMMUNICATION_SETTINGS", "Communication settings", communicationService.settings());
    }

    @PutMapping("/communication/settings")
    @PreAuthorize("hasAuthority('COMMUNICATION_CONFIGURE')")
    @Operation(summary = "Save channels and sender", description = "Channels on or off, sender name, reply-to email, SMS sender id, quiet hours")
    public ResponseEntity<ApiResponse<CommunicationSettingsResponse>> saveCommunicationSettings(@Valid @RequestBody CommunicationSettingsRequest request) {
        return ok("COMMUNICATION_SETTINGS_SAVED", "Communication settings saved", communicationService.saveSettings(request));
    }

    @GetMapping("/communication/templates")
    @PreAuthorize("hasAuthority('COMMUNICATION_READ')")
    @Operation(summary = "Message templates", description = "One per event, with the placeholders it may use; built-in wording until customised")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> templates() {
        return ok("COMMUNICATION_TEMPLATES", "Message templates", communicationService.templates());
    }

    @PutMapping("/communication/templates/{event}")
    @PreAuthorize("hasAuthority('COMMUNICATION_CONFIGURE')")
    @Operation(summary = "Save the template of an event", description = "Wording, channels and on/off. Unknown {{placeholders}} are refused.")
    public ResponseEntity<ApiResponse<TemplateResponse>> saveTemplate(@PathVariable NotificationEvent event, @Valid @RequestBody TemplateRequest request) {
        return ok("COMMUNICATION_TEMPLATE_SAVED", "Template saved", communicationService.saveTemplate(event, request));
    }

    @PostMapping("/communication/templates/{event}/reset")
    @PreAuthorize("hasAuthority('COMMUNICATION_CONFIGURE')")
    @Operation(summary = "Reset a template to the built-in wording")
    public ResponseEntity<ApiResponse<TemplateResponse>> resetTemplate(@PathVariable NotificationEvent event) {
        return ok("COMMUNICATION_TEMPLATE_RESET", "Template reset", communicationService.resetTemplate(event));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(code, message, data));
    }
}
