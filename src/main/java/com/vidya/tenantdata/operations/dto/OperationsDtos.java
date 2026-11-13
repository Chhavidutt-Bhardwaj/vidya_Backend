package com.vidya.tenantdata.operations.dto;

import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.enums.FeeAppliesTo;
import com.vidya.tenantdata.operations.enums.FeeCategory;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.FeeStructureStatus;
import com.vidya.tenantdata.operations.enums.LateCountsAs;
import com.vidya.tenantdata.operations.enums.LateFineType;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.enums.VehicleStatus;
import com.vidya.tenantdata.operations.enums.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requests and responses of /api/v1/operations-config. */
public final class OperationsDtos {

    private OperationsDtos() {
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    /** severity: ERROR (blocks use), WARNING (should be fixed), INFO. */
    public record Issue(String code, String severity, String message, String api) {
    }

    /**
     * One area of operations. {@code available} is false when the signed-in user has no access to it
     * (not in the school's plan, or not their job); counts and issues are then empty.
     * state: NOT_STARTED, IN_PROGRESS, READY (only INFO notes left) or NOT_AVAILABLE.
     */
    public record Area(String key, String label, boolean available, String state, Map<String, Long> counts, List<Issue> issues) {
    }

    public record OperationsOverview(UUID academicYearId, String academicYearName, List<Area> areas) {
    }

    // ── Fees ─────────────────────────────────────────────────────────────────

    public record FeeHeadRequest(@NotBlank @Size(max = 150) String name,
                                 @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "letters, digits, - and _ only") String code,
                                 FeeCategory category, FeeFrequency frequency, Boolean optional, Boolean refundable,
                                 @Size(max = 255) String description, Integer sortOrder, ConfigStatus status) {
    }

    public record FeeHeadResponse(UUID id, String code, String name, FeeCategory category, FeeFrequency frequency, boolean optional,
                                  boolean refundable, String description, int sortOrder, ConfigStatus status, long structuresUsing) {
    }

    public record FeeSettingsRequest(@NotNull UUID academicYearId, FeeFrequency collectionFrequency,
                                     @Min(1) @Max(28) Integer dueDay, @Min(0) @Max(90) Integer graceDays,
                                     LateFineType lateFineType, @DecimalMin("0") BigDecimal lateFineValue, @DecimalMin("0") BigDecimal lateFineCap,
                                     Boolean allowPartialPayment, Boolean allowOnlinePayment,
                                     @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9/-]*$", message = "letters, digits, / and - only") String receiptPrefix,
                                     @Size(min = 3, max = 3) String currency) {
    }

    /** {@code saved} is false while the year still runs on the defaults shown. */
    public record FeeSettingsResponse(UUID academicYearId, FeeFrequency collectionFrequency, int dueDay, int graceDays, LateFineType lateFineType,
                                      BigDecimal lateFineValue, BigDecimal lateFineCap, boolean allowPartialPayment, boolean allowOnlinePayment,
                                      String receiptPrefix, String currency, boolean saved) {
    }

    public record InstallmentInput(@NotBlank @Size(max = 100) String name, @NotNull LocalDate dueDate,
                                   @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal percent) {
    }

    public record InstallmentsRequest(@NotNull UUID academicYearId, @NotEmpty @Size(max = 24) @Valid List<InstallmentInput> installments) {
    }

    public record InstallmentResponse(UUID id, int installmentNo, String name, LocalDate dueDate, BigDecimal percent) {
    }

    public record FeeItemInput(@NotNull UUID feeHeadId, @NotNull @DecimalMin("0") BigDecimal amount) {
    }

    /** One definition saved for every class in {@code classIds} (each class gets its own structure). */
    public record FeeStructureRequest(@NotNull UUID academicYearId, @NotEmpty List<UUID> classIds, FeeAppliesTo appliesTo,
                                      @Size(max = 150) String name, @Size(max = 500) String remarks, FeeStructureStatus status,
                                      @NotEmpty @Valid List<FeeItemInput> items) {
    }

    /** {@code amount} is per occurrence; {@code annualAmount} = amount × times a year. */
    public record FeeItemResponse(UUID feeHeadId, String headCode, String headName, FeeCategory category, FeeFrequency frequency,
                                  boolean optional, BigDecimal amount, BigDecimal annualAmount) {
    }

    public record InstallmentAmount(int installmentNo, String name, LocalDate dueDate, BigDecimal amount) {
    }

    /**
     * {@code annualTotal} covers compulsory heads, {@code optionalTotal} the optional ones. {@code schedule} is what a
     * student taking only compulsory heads pays on each due date (empty until the year has instalments).
     */
    public record FeeStructureResponse(UUID id, String code, UUID academicYearId, UUID classId, String className, FeeAppliesTo appliesTo,
                                       String name, String remarks, FeeStructureStatus status, List<FeeItemResponse> items,
                                       BigDecimal annualTotal, BigDecimal optionalTotal, List<InstallmentAmount> schedule) {
    }

    public record StructureStatusRequest(@NotNull FeeStructureStatus status) {
    }

    /** Copies every structure of the source year that the target year lacks, as DRAFT, optionally raising amounts. */
    public record CopyStructuresRequest(@NotNull UUID sourceAcademicYearId, @NotNull UUID targetAcademicYearId,
                                        @DecimalMin("-50") @DecimalMax("200") BigDecimal increasePercent,
                                        @DecimalMin(value = "0", inclusive = false) BigDecimal roundTo) {
    }

    public record CopyResult(int created, int skipped, List<String> notes) {
    }

    public record ConcessionRequest(@NotBlank @Size(max = 150) String name,
                                    @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "letters, digits, - and _ only") String code,
                                    @NotNull ConcessionKind kind, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal value,
                                    UUID feeHeadId, Boolean requiresApproval, @Size(max = 255) String description, ConfigStatus status) {
    }

    public record ConcessionResponse(UUID id, String code, String name, ConcessionKind kind, BigDecimal value, UUID feeHeadId, String feeHeadName,
                                     boolean requiresApproval, String description, ConfigStatus status) {
    }

    // ── Attendance ───────────────────────────────────────────────────────────

    /** Replaces the rules: a null time or {@code backdateDays} clears it; other nulls fall back to the default. */
    public record AttendanceSettingsRequest(LocalTime markingDeadline, LocalTime lateAfter, @Min(0) @Max(365) Integer backdateDays,
                                            LateCountsAs lateCountsAs, @DecimalMin("0") @DecimalMax("100") BigDecimal minimumPercent,
                                            Boolean notifyGuardianOnAbsence) {
    }

    public record AttendanceSettingsResponse(LocalTime markingDeadline, LocalTime lateAfter, Integer backdateDays, LateCountsAs lateCountsAs,
                                             BigDecimal minimumPercent, boolean notifyGuardianOnAbsence, boolean saved) {
    }

    // ── Transport ────────────────────────────────────────────────────────────

    public record VehicleRequest(@NotBlank @Size(max = 30) String registrationNo, VehicleType vehicleType,
                                 @NotNull @Min(1) @Max(200) Integer capacity, UUID driverStaffId, UUID attendantStaffId,
                                 @Size(max = 150) String driverName, @Size(max = 20) String driverPhone,
                                 LocalDate insuranceValidTill, LocalDate fitnessValidTill, LocalDate permitValidTill, LocalDate pucValidTill,
                                 VehicleStatus status) {
    }

    /** {@code papers}: documents already expired or expiring within 30 days, e.g. "Insurance expired on 2026-10-01". */
    public record VehicleResponse(UUID id, String code, String registrationNo, VehicleType vehicleType, int capacity,
                                  UUID driverStaffId, String driverName, String driverPhone, UUID attendantStaffId, String attendantName,
                                  LocalDate insuranceValidTill, LocalDate fitnessValidTill, LocalDate permitValidTill, LocalDate pucValidTill,
                                  VehicleStatus status, List<String> papers, List<String> routes) {
    }

    public record StopInput(UUID id, @NotBlank @Size(max = 150) String name, LocalTime pickupTime, LocalTime dropTime,
                            @DecimalMin("0") BigDecimal distanceKm, @DecimalMin("0") BigDecimal monthlyFee) {
    }

    /** The whole route: stops are saved in the order given; a stop left out is removed. */
    public record RouteRequest(UUID id, @NotBlank @Size(max = 150) String name, UUID vehicleId, @Size(max = 255) String description,
                               ConfigStatus status, @Size(max = 100) @Valid List<StopInput> stops) {
    }

    public record StopResponse(UUID id, int stopNo, String name, LocalTime pickupTime, LocalTime dropTime, BigDecimal distanceKm, BigDecimal monthlyFee) {
    }

    public record RouteResponse(UUID id, String code, String name, UUID vehicleId, String vehicleRegistrationNo, Integer vehicleCapacity,
                                String description, ConfigStatus status, List<StopResponse> stops) {
    }

    // ── Library ──────────────────────────────────────────────────────────────

    /** Nulls fall back to the default, except {@code maxFine} where null means no cap. */
    public record LibrarySettingsRequest(@Min(0) @Max(50) Integer studentMaxBooks, @Min(1) @Max(365) Integer studentLoanDays,
                                         @Min(0) @Max(50) Integer staffMaxBooks, @Min(1) @Max(365) Integer staffLoanDays,
                                         @Min(0) @Max(20) Integer maxRenewals, @DecimalMin("0") BigDecimal finePerDay,
                                         @Min(0) @Max(90) Integer fineGraceDays, @DecimalMin("0") BigDecimal maxFine,
                                         @DecimalMin("0") @DecimalMax("500") BigDecimal lostBookChargePercent, Boolean allowReservation) {
    }

    public record LibrarySettingsResponse(int studentMaxBooks, int studentLoanDays, int staffMaxBooks, int staffLoanDays, int maxRenewals,
                                          BigDecimal finePerDay, int fineGraceDays, BigDecimal maxFine, BigDecimal lostBookChargePercent,
                                          boolean allowReservation, boolean saved) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "letters, digits, - and _ only") String code,
                                  @Size(max = 255) String description, ConfigStatus status) {
    }

    public record CategoryResponse(UUID id, String code, String name, String description, ConfigStatus status) {
    }

    // ── Communication ────────────────────────────────────────────────────────

    /** Nulls: a flag keeps its default, text and quiet hours are cleared. Quiet hours need both times. */
    public record CommunicationSettingsRequest(Boolean inAppEnabled, Boolean emailEnabled, Boolean smsEnabled, Boolean whatsappEnabled,
                                               @Size(max = 100) String senderName, @Email @Size(max = 255) String replyToEmail,
                                               @Pattern(regexp = "^[A-Za-z0-9]{3,11}$", message = "3 to 11 letters or digits") String smsSenderId,
                                               LocalTime quietFrom, LocalTime quietTo, @Size(max = 10) String defaultLanguage) {
    }

    public record CommunicationSettingsResponse(boolean inAppEnabled, boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled,
                                                String senderName, String replyToEmail, String smsSenderId, LocalTime quietFrom, LocalTime quietTo,
                                                String defaultLanguage, boolean saved) {
    }

    public record TemplateRequest(Boolean enabled, @NotEmpty List<NotificationChannel> channels,
                                  @NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 2000) String body) {
    }

    /**
     * {@code customised} is false while the built-in wording is in use. {@code channelsOff}: channels the template
     * uses that are switched off in the settings, so nothing goes out on them.
     */
    public record TemplateResponse(NotificationEvent event, String label, String description, String audience, boolean enabled,
                                   List<NotificationChannel> channels, String subject, String body, List<String> placeholders,
                                   boolean customised, List<NotificationChannel> channelsOff) {
    }
}
