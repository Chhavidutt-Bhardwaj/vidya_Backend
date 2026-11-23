package com.vidya.tenantdata.operations;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyResult;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyStructuresRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeHeadRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeItemInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.StopInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.VehicleRequest;
import com.vidya.tenantdata.operations.entity.AttendanceSettings;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeInstallment;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.entity.FeeStructure;
import com.vidya.tenantdata.operations.entity.FeeStructureItem;
import com.vidya.tenantdata.operations.entity.NotificationTemplate;
import com.vidya.tenantdata.operations.entity.TransportRoute;
import com.vidya.tenantdata.operations.entity.TransportStop;
import com.vidya.tenantdata.operations.entity.TransportVehicle;
import com.vidya.tenantdata.operations.enums.FeeAppliesTo;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.FeeStructureStatus;
import com.vidya.tenantdata.operations.enums.LateCountsAs;
import com.vidya.tenantdata.operations.enums.LateFineType;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.AttendanceSettingsRepository;
import com.vidya.tenantdata.operations.repository.CommunicationSettingsRepository;
import com.vidya.tenantdata.operations.repository.FeeConcessionTypeRepository;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import com.vidya.tenantdata.operations.repository.FeeInstallmentRepository;
import com.vidya.tenantdata.operations.repository.FeeSettingsRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureItemRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureRepository;
import com.vidya.tenantdata.operations.repository.NotificationTemplateRepository;
import com.vidya.tenantdata.operations.repository.TransportRouteRepository;
import com.vidya.tenantdata.operations.repository.TransportStopRepository;
import com.vidya.tenantdata.operations.repository.TransportVehicleRepository;
import com.vidya.tenantdata.operations.service.AttendancePolicyService;
import com.vidya.tenantdata.operations.service.CommunicationConfigService;
import com.vidya.tenantdata.operations.service.FeeConfigService;
import com.vidya.tenantdata.operations.service.TransportConfigService;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Operational configuration — fees, attendance rules, transport, communication")
class OperationsConfigServiceTest {

    @Mock private FeeHeadRepository headRepository;
    @Mock private FeeSettingsRepository feeSettingsRepository;
    @Mock private FeeInstallmentRepository installmentRepository;
    @Mock private FeeStructureRepository structureRepository;
    @Mock private FeeStructureItemRepository itemRepository;
    @Mock private FeeConcessionTypeRepository concessionRepository;
    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SchoolContext context;
    @Mock private AttendanceSettingsRepository attendanceSettingsRepository;
    @Mock private CommunicationSettingsRepository communicationSettingsRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private TransportVehicleRepository vehicleRepository;
    @Mock private TransportRouteRepository routeRepository;
    @Mock private TransportStopRepository stopRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private TeachingScope names;

    private FeeConfigService fees;
    private AcademicYear year;
    private AcademicClass classV;
    private AcademicClass classVI;
    private FeeHead tuition;
    private FeeHead admission;
    private FeeHead transport;
    private final List<FeeStructure> structures = new ArrayList<>();
    private final List<FeeStructureItem> items = new ArrayList<>();
    private final List<FeeInstallment> plan = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fees = new FeeConfigService(headRepository, feeSettingsRepository, installmentRepository, structureRepository, itemRepository,
                concessionRepository, yearRepository, classRepository, context);

        year = new AcademicYear("AY2026-27", "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        year.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));

        classV = new AcademicClass("V", "Class V", "Class V", 5);
        classV.setId(UUID.randomUUID());
        classVI = new AcademicClass("VI", "Class VI", "Class VI", 6);
        classVI.setId(UUID.randomUUID());
        when(classRepository.findByIdAndNotDeleted(classV.getId())).thenReturn(Optional.of(classV));
        when(classRepository.findByIdAndNotDeleted(classVI.getId())).thenReturn(Optional.of(classVI));
        when(classRepository.findByDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(List.of(classV, classVI));

        tuition = head("TUITION", "Tuition Fee", FeeFrequency.MONTHLY, false);
        admission = head("ADMISSION", "Admission Fee", FeeFrequency.ONE_TIME, false);
        transport = head("TRANSPORT", "Transport Fee", FeeFrequency.MONTHLY, true);
        when(headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc()).thenReturn(List.of(tuition, admission, transport));
        when(headRepository.findAll()).thenReturn(List.of(tuition, admission, transport));
        when(headRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // in-memory stand-ins for the structure, item and instalment tables
        when(structureRepository.save(any())).thenAnswer(i -> {
            FeeStructure s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
                structures.add(s);
            }
            return s;
        });
        when(structureRepository.findAll()).thenAnswer(i -> List.copyOf(structures));
        when(structureRepository.findByAcademicYearIdAndDeletedAtIsNull(any())).thenAnswer(i -> structures.stream()
                .filter(s -> s.getAcademicYearId().equals(i.getArgument(0)) && s.getDeletedAt() == null).toList());
        when(itemRepository.save(any())).thenAnswer(i -> {
            FeeStructureItem item = i.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
                items.add(item);
            }
            return item;
        });
        when(itemRepository.findByFeeStructureId(any())).thenAnswer(i -> items.stream().filter(x -> x.getFeeStructureId().equals(i.getArgument(0))).toList());
        when(itemRepository.findByFeeStructureIdIn(anyCollection())).thenAnswer(i -> items.stream()
                .filter(x -> ((java.util.Collection<?>) i.getArgument(0)).contains(x.getFeeStructureId())).toList());
        when(installmentRepository.save(any())).thenAnswer(i -> {
            plan.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(installmentRepository.findByAcademicYearIdOrderByInstallmentNoAsc(any())).thenAnswer(i -> List.copyOf(plan));
    }

    // ── Fees ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Instalments: quarterly plan is generated from the settings and adds up to 100")
    void generatesQuarterlyInstallments() {
        FeeSettings settings = new FeeSettings(year.getId());
        settings.setDueDay(10);
        when(feeSettingsRepository.findById(year.getId())).thenReturn(Optional.of(settings));

        List<InstallmentResponse> out = fees.generateInstallments(year.getId());

        assertThat(out).extracting(InstallmentResponse::name).containsExactly("Quarter 1", "Quarter 2", "Quarter 3", "Quarter 4");
        assertThat(out).extracting(InstallmentResponse::dueDate).containsExactly(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 10, 10), LocalDate.of(2027, 1, 10));
        assertThat(out.stream().map(InstallmentResponse::percent).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Instalments: monthly shares that do not divide evenly still add up to 100")
    void monthlyInstallmentsAddUpTo100() {
        FeeSettings settings = new FeeSettings(year.getId());
        settings.setCollectionFrequency(FeeFrequency.MONTHLY);
        when(feeSettingsRepository.findById(year.getId())).thenReturn(Optional.of(settings));

        List<InstallmentResponse> out = fees.generateInstallments(year.getId());

        assertThat(out).hasSize(12);
        assertThat(out.get(0).name()).isEqualTo("April 2026");
        assertThat(out.get(0).percent()).isEqualByComparingTo("8.33");
        assertThat(out.get(11).percent()).isEqualByComparingTo("8.37");
        assertThat(out.stream().map(InstallmentResponse::percent).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Instalments: percents must add up to 100 and due dates must rise")
    void rejectsBadInstallmentPlans() {
        InstallmentsRequest shortPlan = new InstallmentsRequest(year.getId(), List.of(
                new InstallmentInput("Term 1", LocalDate.of(2026, 4, 10), new BigDecimal("50")),
                new InstallmentInput("Term 2", LocalDate.of(2026, 10, 10), new BigDecimal("40"))));
        assertThatThrownBy(() -> fees.saveInstallments(shortPlan)).isInstanceOf(BusinessException.class).hasMessageContaining("add up to 90");

        InstallmentsRequest backwards = new InstallmentsRequest(year.getId(), List.of(
                new InstallmentInput("Term 1", LocalDate.of(2026, 10, 10), new BigDecimal("50")),
                new InstallmentInput("Term 2", LocalDate.of(2026, 4, 10), new BigDecimal("50"))));
        assertThatThrownBy(() -> fees.saveInstallments(backwards)).isInstanceOf(BusinessException.class).hasMessageContaining("rising order");
        verify(installmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fee structure: one definition is saved for each class, with totals and the amount due per instalment")
    void savesStructureForSeveralClasses() {
        plan.add(installment(1, "Term 1", LocalDate.of(2026, 4, 10), "50"));
        plan.add(installment(2, "Term 2", LocalDate.of(2026, 10, 10), "50"));

        List<FeeStructureResponse> out = fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId(), classVI.getId()), null, null, null,
                FeeStructureStatus.ACTIVE, List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2000")),
                new FeeItemInput(admission.getId(), new BigDecimal("5000")), new FeeItemInput(transport.getId(), new BigDecimal("800")))));

        assertThat(out).hasSize(2);
        FeeStructureResponse v = out.get(0);
        assertThat(v.className()).isEqualTo("Class V");
        assertThat(v.name()).isEqualTo("Class V fees AY2026-27");
        assertThat(v.appliesTo()).isEqualTo(FeeAppliesTo.ALL);
        assertThat(v.status()).isEqualTo(FeeStructureStatus.ACTIVE);
        // tuition 2000 × 12 + admission 5000 once; optional transport 800 × 12 is kept apart
        assertThat(v.annualTotal()).isEqualByComparingTo("29000");
        assertThat(v.optionalTotal()).isEqualByComparingTo("9600");
        // recurring 24000 is split 50/50; the one-time 5000 falls due with the first instalment
        assertThat(v.schedule().get(0).amount()).isEqualByComparingTo("17000");
        assertThat(v.schedule().get(1).amount()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("Fee structure: saving again replaces the heads of the class's structure instead of adding a second one")
    void savingAgainReplacesTheStructure() {
        fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId()), null, null, null, null,
                List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2000")), new FeeItemInput(admission.getId(), new BigDecimal("5000")))));

        List<FeeStructureResponse> out = fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId()), null, null, null, null,
                List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2200")))));

        assertThat(structures).hasSize(1);
        assertThat(items.stream().filter(i -> i.getFeeHeadId().equals(tuition.getId())).findFirst().orElseThrow().getAmount()).isEqualByComparingTo("2200");
        verify(itemRepository).deleteAllInBatch(any());
        assertThat(out.get(0).status()).isEqualTo(FeeStructureStatus.DRAFT);
    }

    @Test
    @DisplayName("Fee structure: a class cannot have one for ALL students and another for new admissions")
    void rejectsMixingAllWithNewAdmission() {
        List<FeeItemInput> tuitionOnly = List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2000")));
        fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId()), FeeAppliesTo.ALL, null, null, null, tuitionOnly));

        assertThatThrownBy(() -> fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId()), FeeAppliesTo.NEW_ADMISSION,
                null, null, null, tuitionOnly))).isInstanceOf(BusinessException.class).hasMessageContaining("already has a fee structure for all students");
    }

    @Test
    @DisplayName("Fee structure: copying to a new year raises amounts, rounds them and skips classes that already have one")
    void copiesStructuresWithIncrease() {
        AcademicYear next = new AcademicYear("AY2027-28", "2027-28", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false, AcademicYearStatus.DRAFT);
        next.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(next.getId())).thenReturn(Optional.of(next));
        List<FeeItemInput> tuitionOnly = List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2030")));
        fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId(), classVI.getId()), null, null, null, FeeStructureStatus.ACTIVE, tuitionOnly));
        fees.saveStructure(new FeeStructureRequest(next.getId(), List.of(classVI.getId()), null, null, null, null, tuitionOnly));

        CopyResult result = fees.copyStructures(new CopyStructuresRequest(year.getId(), next.getId(), new BigDecimal("10"), new BigDecimal("50")));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        FeeStructure copy = structures.stream().filter(s -> s.getAcademicYearId().equals(next.getId()) && s.getAcademicClassId().equals(classV.getId()))
                .findFirst().orElseThrow();
        assertThat(copy.getStatus()).isEqualTo(FeeStructureStatus.DRAFT);
        // 2030 + 10% = 2233 → nearest 50 = 2250
        assertThat(items.stream().filter(i -> i.getFeeStructureId().equals(copy.getId())).findFirst().orElseThrow().getAmount()).isEqualByComparingTo("2250");
    }

    @Test
    @DisplayName("Fee head: the frequency cannot change while fee structures use it; a duplicate name is refused")
    void protectsFeeHeadsInUse() {
        when(headRepository.findByIdAndDeletedAtIsNull(tuition.getId())).thenReturn(Optional.of(tuition));
        fees.saveStructure(new FeeStructureRequest(year.getId(), List.of(classV.getId()), null, null, null, null,
                List.of(new FeeItemInput(tuition.getId(), new BigDecimal("2000")))));

        assertThatThrownBy(() -> fees.updateHead(tuition.getId(), new FeeHeadRequest("Tuition Fee", null, null, FeeFrequency.QUARTERLY, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("used in 1 fee structure");
        assertThatThrownBy(() -> fees.deleteHead(tuition.getId())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fees.createHead(new FeeHeadRequest("admission fee", "ADM2", null, null, null, null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        // a code is made from the name when none is given
        assertThat(fees.createHead(new FeeHeadRequest("Smart Class Fee", null, null, null, null, null, null, null, null)).code()).isEqualTo("SMART_CLASS_FEE");
    }

    @Test
    @DisplayName("Fee settings: a late fine needs an amount, and NONE clears it")
    void validatesLateFine() {
        when(feeSettingsRepository.findById(year.getId())).thenReturn(Optional.empty());
        when(feeSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> fees.saveSettings(new FeeSettingsRequest(year.getId(), null, null, null, LateFineType.PER_DAY, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("lateFineValue");
        assertThatThrownBy(() -> fees.saveSettings(new FeeSettingsRequest(year.getId(), FeeFrequency.ONE_TIME, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class);

        var saved = fees.saveSettings(new FeeSettingsRequest(year.getId(), FeeFrequency.MONTHLY, 5, 3, LateFineType.NONE, new BigDecimal("20"), new BigDecimal("500"),
                null, true, "fee/26", "inr"));
        assertThat(saved.lateFineValue()).isEqualByComparingTo("0");
        assertThat(saved.lateFineCap()).isNull();
        assertThat(saved.receiptPrefix()).isEqualTo("FEE/26");
        assertThat(saved.currency()).isEqualTo("INR");
        assertThat(saved.allowPartialPayment()).isTrue();
        assertThat(saved.saved()).isTrue();
        assertThat(fees.settings(year.getId()).saved()).isFalse();
    }

    // ── Attendance rules ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Attendance rules: defaults until saved; saving keeps one row")
    void attendanceRules() {
        AttendancePolicyService service = new AttendancePolicyService(attendanceSettingsRepository, context);
        when(attendanceSettingsRepository.findById((short) 1)).thenReturn(Optional.empty());
        when(attendanceSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AttendanceSettingsResponse defaults = service.get();
        assertThat(defaults.saved()).isFalse();
        assertThat(defaults.backdateDays()).isNull();
        assertThat(defaults.lateCountsAs()).isEqualTo(LateCountsAs.PRESENT);
        assertThat(defaults.minimumPercent()).isEqualByComparingTo("75");
        assertThat(service.policy().backdateDays()).isNull();

        AttendanceSettingsResponse saved = service.save(new AttendanceSettingsRequest(LocalTime.of(10, 0), LocalTime.of(8, 15), 2, LateCountsAs.HALF_DAY,
                new BigDecimal("80"), true));
        assertThat(saved.saved()).isTrue();
        assertThat(saved.backdateDays()).isEqualTo(2);
        assertThat(saved.minimumPercent()).isEqualByComparingTo("80");
        org.mockito.ArgumentCaptor<AttendanceSettings> row = org.mockito.ArgumentCaptor.forClass(AttendanceSettings.class);
        verify(attendanceSettingsRepository).save(row.capture());
        assertThat(row.getValue().getId()).isEqualTo((short) 1);
    }

    // ── Transport ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Transport: the same registration number or driver cannot be on two vehicles; papers near expiry are reported")
    void vehicleRules() {
        TransportConfigService service = new TransportConfigService(vehicleRepository, routeRepository, stopRepository, staffRepository, names);
        UUID driver = UUID.randomUUID();
        TransportVehicle bus = new TransportVehicle();
        bus.setId(UUID.randomUUID());
        bus.setRegistrationNo("DL 1PC 1234");
        bus.setDriverStaffId(driver);
        when(vehicleRepository.findByDeletedAtIsNullOrderByRegistrationNoAsc()).thenReturn(List.of(bus));

        assertThatThrownBy(() -> service.saveVehicle(null, vehicle("dl1pc1234", null)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> service.saveVehicle(null, vehicle("DL 1PC 9999", driver)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already the driver of DL 1PC 1234");

        LocalDate today = LocalDate.of(2026, 11, 12);
        bus.setInsuranceValidTill(today.minusDays(1));
        bus.setFitnessValidTill(today.plusDays(30));
        bus.setPermitValidTill(today.plusDays(31));
        assertThat(TransportConfigService.papers(bus, today)).containsExactly("Insurance expired on 2026-11-11", "Fitness certificate expires on 2026-12-12");
    }

    @Test
    @DisplayName("Transport: a route is saved with its stops in order; removed stops go, foreign stops and backwards timings are refused")
    void routeWithStops() {
        TransportConfigService service = new TransportConfigService(vehicleRepository, routeRepository, stopRepository, staffRepository, names);
        TransportRoute route = new TransportRoute();
        route.setId(UUID.randomUUID());
        route.setName("North");
        TransportStop first = new TransportStop(route.getId());
        first.setId(UUID.randomUUID());
        first.setName("Old Market");
        TransportStop gone = new TransportStop(route.getId());
        gone.setId(UUID.randomUUID());
        gone.setName("Depot");
        when(routeRepository.findByIdAndDeletedAtIsNull(route.getId())).thenReturn(Optional.of(route));
        when(routeRepository.findByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(route));
        when(routeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stopRepository.findByRouteIdOrderByStopNoAsc(route.getId())).thenReturn(List.of(first, gone));
        List<TransportStop> savedStops = new ArrayList<>();
        when(stopRepository.save(any())).thenAnswer(i -> {
            savedStops.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(stopRepository.findByRouteIdInOrderByStopNoAsc(anyCollection())).thenAnswer(i -> List.copyOf(savedStops));

        RouteResponse out = service.saveRoute(new RouteRequest(route.getId(), "North Route", null, null, null, List.of(
                new StopInput(null, "City Gate", LocalTime.of(7, 0), null, null, new BigDecimal("900")),
                new StopInput(first.getId(), "Old Market", LocalTime.of(7, 15), null, null, new BigDecimal("700")))));

        assertThat(out.name()).isEqualTo("North Route");
        assertThat(out.stops()).extracting(s -> s.stopNo() + " " + s.name()).containsExactly("1 City Gate", "2 Old Market");
        verify(stopRepository).deleteAll(List.of(gone));

        assertThatThrownBy(() -> service.saveRoute(new RouteRequest(route.getId(), "North Route", null, null, null, List.of(
                new StopInput(UUID.randomUUID(), "Elsewhere", null, null, null, null))))).isInstanceOf(BusinessException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> service.saveRoute(new RouteRequest(route.getId(), "North Route", null, null, null, List.of(
                new StopInput(null, "A", LocalTime.of(7, 30), null, null, null), new StopInput(null, "B", LocalTime.of(7, 10), null, null, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("travel order");
    }

    // ── Communication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Communication: every event has a built-in template; a custom one may only use the event's placeholders")
    void templates() {
        CommunicationConfigService service = new CommunicationConfigService(communicationSettingsRepository, templateRepository, context);
        CommunicationSettings settings = new CommunicationSettings();
        settings.setSmsEnabled(false);
        when(communicationSettingsRepository.findById((short) 1)).thenReturn(Optional.of(settings));
        when(templateRepository.findAll()).thenReturn(List.of());
        when(templateRepository.findByEventCode(any())).thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<TemplateResponse> all = service.templates();
        assertThat(all).hasSize(NotificationEvent.values().length);
        assertThat(all).allSatisfy(t -> {
            assertThat(t.customised()).isFalse();
            assertThat(t.channels()).containsExactly(NotificationChannel.IN_APP);
            assertThat(t.channelsOff()).isEmpty();
        });
        // the built-in wording itself only uses allowed placeholders
        for (NotificationEvent e : NotificationEvent.values()) {
            assertThat(e.placeholders().size() + NotificationEvent.COMMON_PLACEHOLDERS.size()).isGreaterThan(0);
            service.saveTemplate(e, new TemplateRequest(true, List.of(NotificationChannel.IN_APP), e.defaultSubject(), e.defaultBody()));
        }

        assertThatThrownBy(() -> service.saveTemplate(NotificationEvent.STUDENT_ABSENT, new TemplateRequest(true, List.of(NotificationChannel.SMS),
                "Absent", "{{student_name}} owes {{amount}}"))).isInstanceOf(BusinessException.class).hasMessageContaining("{{amount}}");

        TemplateResponse saved = service.saveTemplate(NotificationEvent.STUDENT_ABSENT, new TemplateRequest(null, List.of(NotificationChannel.SMS, NotificationChannel.IN_APP),
                "Absent today", "{{ student_name }} was absent on {{date}}. - {{school_name}}"));
        assertThat(saved.customised()).isTrue();
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.channels()).containsExactly(NotificationChannel.IN_APP, NotificationChannel.SMS);
        // SMS is switched off in the settings, so the template says nothing will go out on it
        assertThat(saved.channelsOff()).containsExactly(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("Communication: quiet hours need both times")
    void quietHours() {
        CommunicationConfigService service = new CommunicationConfigService(communicationSettingsRepository, templateRepository, context);
        assertThatThrownBy(() -> service.saveSettings(new CommunicationSettingsRequest(null, null, null, null, null, null, null, LocalTime.of(21, 0), null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("both");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static FeeHead head(String code, String name, FeeFrequency frequency, boolean optional) {
        FeeHead h = new FeeHead();
        h.setId(UUID.randomUUID());
        h.setCode(code);
        h.setName(name);
        h.setFrequency(frequency);
        h.setOptional(optional);
        return h;
    }

    private FeeInstallment installment(int no, String name, LocalDate due, String percent) {
        FeeInstallment i = new FeeInstallment();
        i.setAcademicYearId(year.getId());
        i.setInstallmentNo(no);
        i.setName(name);
        i.setDueDate(due);
        i.setPercent(new BigDecimal(percent));
        return i;
    }

    private static VehicleRequest vehicle(String registration, UUID driver) {
        return new VehicleRequest(registration, null, 40, driver, null, null, null, null, null, null, null, null);
    }
}
