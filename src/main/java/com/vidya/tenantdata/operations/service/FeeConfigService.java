package com.vidya.tenantdata.operations.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.operations.dto.OperationsDtos.ConcessionRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.ConcessionResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyResult;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CopyStructuresRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeHeadRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeHeadResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeItemInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeItemResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.FeeStructureResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentAmount;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.InstallmentsRequest;
import com.vidya.tenantdata.operations.entity.FeeConcessionType;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeInstallment;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.entity.FeeStructure;
import com.vidya.tenantdata.operations.entity.FeeStructureItem;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.enums.FeeAppliesTo;
import com.vidya.tenantdata.operations.enums.FeeCategory;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.FeeStructureStatus;
import com.vidya.tenantdata.operations.enums.LateFineType;
import com.vidya.tenantdata.operations.repository.FeeConcessionTypeRepository;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import com.vidya.tenantdata.operations.repository.FeeInstallmentRepository;
import com.vidya.tenantdata.operations.repository.FeeSettingsRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureItemRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fee setup: what is charged (heads), when (instalments), how much per class (structures), the late fine
 * and the kinds of concession. Charging students and collecting payments are not part of this service.
 */
@Service
@RequiredArgsConstructor
public class FeeConfigService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Heads most schools start with: name, code, category, frequency, optional, refundable. */
    private static final List<Object[]> STANDARD_HEADS = List.of(
            new Object[]{"Tuition Fee", "TUITION", FeeCategory.TUITION, FeeFrequency.MONTHLY, false, false},
            new Object[]{"Admission Fee", "ADMISSION", FeeCategory.ADMISSION, FeeFrequency.ONE_TIME, false, false},
            new Object[]{"Annual Charges", "ANNUAL", FeeCategory.OTHER, FeeFrequency.ANNUAL, false, false},
            new Object[]{"Examination Fee", "EXAM", FeeCategory.EXAMINATION, FeeFrequency.HALF_YEARLY, false, false},
            new Object[]{"Laboratory Fee", "LAB", FeeCategory.LABORATORY, FeeFrequency.ANNUAL, false, false},
            new Object[]{"Library Fee", "LIBRARY", FeeCategory.LIBRARY, FeeFrequency.ANNUAL, false, false},
            new Object[]{"Activity Fee", "ACTIVITY", FeeCategory.ACTIVITY, FeeFrequency.ANNUAL, false, false},
            new Object[]{"Transport Fee", "TRANSPORT", FeeCategory.TRANSPORT, FeeFrequency.MONTHLY, true, false},
            new Object[]{"Caution Deposit", "DEPOSIT", FeeCategory.DEPOSIT, FeeFrequency.ONE_TIME, false, true});

    private final FeeHeadRepository headRepository;
    private final FeeSettingsRepository settingsRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final FeeStructureRepository structureRepository;
    private final FeeStructureItemRepository itemRepository;
    private final FeeConcessionTypeRepository concessionRepository;
    private final AcademicYearRepository yearRepository;
    private final AcademicClassRepository classRepository;
    private final SchoolContext context;

    // ── Fee heads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<FeeHeadResponse> heads() {
        Map<UUID, Long> usage = headUsage();
        return headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc().stream()
                .map(h -> head(h, usage.getOrDefault(h.getId(), 0L))).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public FeeHeadResponse createHead(FeeHeadRequest request) {
        List<FeeHead> existing = headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
        FeeHead head = new FeeHead();
        head.setSortOrder(existing.stream().mapToInt(FeeHead::getSortOrder).max().orElse(0) + 10);
        applyHead(head, request, existing);
        return head(headRepository.save(head), 0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public FeeHeadResponse updateHead(UUID id, FeeHeadRequest request) {
        FeeHead head = headRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeHead", id));
        long used = headUsage().getOrDefault(id, 0L);
        if (used > 0 && request.frequency() != null && request.frequency() != head.getFrequency()) {
            throw new BusinessException("FEE_HEAD_IN_USE", head.getName() + " is used in " + used
                    + " fee structure(s); changing how often it is charged would change their totals. Add a new head instead.");
        }
        applyHead(head, request, headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc());
        return head(headRepository.save(head), used);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteHead(UUID id) {
        FeeHead head = headRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeHead", id));
        long used = headUsage().getOrDefault(id, 0L);
        if (used > 0) {
            throw new BusinessException("FEE_HEAD_IN_USE", head.getName() + " is used in " + used
                    + " fee structure(s); remove it from them first, or set it INACTIVE");
        }
        if (concessionRepository.findByDeletedAtIsNullOrderByNameAsc().stream().anyMatch(c -> id.equals(c.getFeeHeadId()))) {
            throw new BusinessException("FEE_HEAD_IN_USE", head.getName() + " has a concession type; delete that first");
        }
        head.setDeletedAt(Instant.now());
        head.setStatus(ConfigStatus.INACTIVE);
        headRepository.save(head);
    }

    /** Adds the usual heads a school starts with, skipping any whose code or name already exists. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<FeeHeadResponse> addStandardHeads() {
        List<FeeHead> existing = headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
        int order = existing.stream().mapToInt(FeeHead::getSortOrder).max().orElse(0);
        for (Object[] s : STANDARD_HEADS) {
            String name = (String) s[0];
            String code = (String) s[1];
            if (existing.stream().anyMatch(h -> h.getCode().equalsIgnoreCase(code) || h.getName().equalsIgnoreCase(name))) {
                continue;
            }
            FeeHead head = new FeeHead();
            head.setName(name);
            head.setCode(code);
            head.setCategory((FeeCategory) s[2]);
            head.setFrequency((FeeFrequency) s[3]);
            head.setOptional((Boolean) s[4]);
            head.setRefundable((Boolean) s[5]);
            order += 10;
            head.setSortOrder(order);
            headRepository.save(head);
        }
        return heads();
    }

    // ── Year settings ────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public FeeSettingsResponse settings(UUID academicYearId) {
        year(academicYearId);
        return settingsRepository.findById(academicYearId).map(s -> settings(s, true))
                .orElseGet(() -> settings(new FeeSettings(academicYearId), false));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public FeeSettingsResponse saveSettings(FeeSettingsRequest r) {
        editableYear(r.academicYearId());
        FeeSettings s = settingsRepository.findById(r.academicYearId()).orElseGet(() -> new FeeSettings(r.academicYearId()));
        FeeSettings defaults = new FeeSettings(r.academicYearId());
        FeeFrequency frequency = r.collectionFrequency() != null ? r.collectionFrequency() : defaults.getCollectionFrequency();
        if (frequency == FeeFrequency.ONE_TIME) {
            throw new BusinessException("Fees are collected ANNUAL, HALF_YEARLY, QUARTERLY or MONTHLY");
        }
        LateFineType fineType = r.lateFineType() != null ? r.lateFineType() : LateFineType.NONE;
        BigDecimal fine = r.lateFineValue() != null ? r.lateFineValue() : BigDecimal.ZERO;
        if (fineType == LateFineType.NONE) {
            fine = BigDecimal.ZERO;
        } else if (fine.signum() <= 0) {
            throw new BusinessException("lateFineValue must be more than 0 for a " + fineType + " late fine");
        } else if (fineType == LateFineType.PERCENT && fine.compareTo(HUNDRED) > 0) {
            throw new BusinessException("A PERCENT late fine cannot be more than 100");
        }
        s.setCollectionFrequency(frequency);
        s.setDueDay(r.dueDay() != null ? r.dueDay() : defaults.getDueDay());
        s.setGraceDays(r.graceDays() != null ? r.graceDays() : 0);
        s.setLateFineType(fineType);
        s.setLateFineValue(fine);
        s.setLateFineCap(fineType == LateFineType.NONE ? null : r.lateFineCap());
        s.setAllowPartialPayment(r.allowPartialPayment() == null || r.allowPartialPayment());
        s.setAllowOnlinePayment(Boolean.TRUE.equals(r.allowOnlinePayment()));
        s.setReceiptPrefix(blank(r.receiptPrefix()) ? defaults.getReceiptPrefix() : r.receiptPrefix().trim().toUpperCase(Locale.ROOT));
        s.setCurrency(blank(r.currency()) ? defaults.getCurrency() : r.currency().trim().toUpperCase(Locale.ROOT));
        s.setUpdatedBy(context.actorId());
        return settings(settingsRepository.save(s), true);
    }

    // ── Instalments ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<InstallmentResponse> installments(UUID academicYearId) {
        year(academicYearId);
        return installmentRepository.findByAcademicYearIdOrderByInstallmentNoAsc(academicYearId).stream().map(FeeConfigService::installment).toList();
    }

    /** Replaces the year's instalment plan. Due dates must rise and the percents must add up to 100. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<InstallmentResponse> saveInstallments(InstallmentsRequest request) {
        AcademicYear year = editableYear(request.academicYearId());
        BigDecimal sum = BigDecimal.ZERO;
        LocalDate previous = null;
        for (InstallmentInput in : request.installments()) {
            sum = sum.add(in.percent());
            if (previous != null && !in.dueDate().isAfter(previous)) {
                throw new BusinessException("Due dates must be in rising order: " + in.name() + " (" + in.dueDate() + ") is not after " + previous);
            }
            if (year.getEndDate() != null && in.dueDate().isAfter(year.getEndDate())) {
                throw new BusinessException(in.name() + " is due on " + in.dueDate() + ", after the academic year ends (" + year.getEndDate() + ")");
            }
            previous = in.dueDate();
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BusinessException("INSTALLMENT_PERCENT", "The instalment percents add up to " + sum.stripTrailingZeros().toPlainString() + "; they must add up to 100");
        }
        return replaceInstallments(year.getId(), request.installments());
    }

    /** Builds an equal-share plan from the year's settings: one instalment per collection period, due on the due day. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<InstallmentResponse> generateInstallments(UUID academicYearId) {
        AcademicYear year = editableYear(academicYearId);
        if (year.getStartDate() == null) {
            throw new BusinessException("The academic year has no start date");
        }
        FeeSettings s = settingsRepository.findById(academicYearId).orElseGet(() -> new FeeSettings(academicYearId));
        int n = s.getCollectionFrequency().timesPerYear();
        BigDecimal share = HUNDRED.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        List<InstallmentInput> plan = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            LocalDate month = year.getStartDate().plusMonths((long) k * 12 / n);
            LocalDate due = month.withDayOfMonth(s.getDueDay());
            if (due.isBefore(year.getStartDate())) {
                due = year.getStartDate();
            }
            String name = switch (s.getCollectionFrequency()) {
                case MONTHLY -> month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
                case QUARTERLY -> "Quarter " + (k + 1);
                case HALF_YEARLY -> "Half year " + (k + 1);
                default -> "Annual fee";
            };
            // the last instalment takes what rounding left over, so the plan always adds up to 100
            BigDecimal percent = k < n - 1 ? share : HUNDRED.subtract(share.multiply(BigDecimal.valueOf(n - 1L)));
            plan.add(new InstallmentInput(name, due, percent));
        }
        return replaceInstallments(academicYearId, plan);
    }

    private List<InstallmentResponse> replaceInstallments(UUID yearId, List<InstallmentInput> plan) {
        // deleted in the database first: (year, instalment no) is unique
        installmentRepository.deleteAllInBatch(installmentRepository.findByAcademicYearIdOrderByInstallmentNoAsc(yearId));
        List<InstallmentResponse> out = new ArrayList<>();
        int no = 0;
        for (InstallmentInput in : plan) {
            FeeInstallment i = new FeeInstallment();
            i.setAcademicYearId(yearId);
            i.setInstallmentNo(++no);
            i.setName(in.name().trim());
            i.setDueDate(in.dueDate());
            i.setPercent(in.percent().setScale(2, RoundingMode.HALF_UP));
            out.add(installment(installmentRepository.save(i)));
        }
        return out;
    }

    // ── Fee structures ───────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<FeeStructureResponse> structures(UUID academicYearId, UUID classId) {
        year(academicYearId);
        List<FeeStructure> structures = structureRepository.findByAcademicYearIdAndDeletedAtIsNull(academicYearId).stream()
                .filter(s -> classId == null || classId.equals(s.getAcademicClassId())).toList();
        return respond(academicYearId, structures);
    }

    /**
     * Saves one fee structure definition for every class given: a class that already has a structure for the
     * same students gets it replaced, the others get a new one.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<FeeStructureResponse> saveStructure(FeeStructureRequest request) {
        AcademicYear year = editableYear(request.academicYearId());
        FeeAppliesTo appliesTo = request.appliesTo() != null ? request.appliesTo() : FeeAppliesTo.ALL;

        Map<UUID, FeeHead> heads = headsById();
        Map<UUID, BigDecimal> amounts = new LinkedHashMap<>();
        for (FeeItemInput in : request.items()) {
            FeeHead head = heads.get(in.feeHeadId());
            if (head == null) {
                throw new ResourceNotFoundException("FeeHead", in.feeHeadId());
            }
            if (head.getStatus() != ConfigStatus.ACTIVE) {
                throw new BusinessException(head.getName() + " is inactive and cannot be added to a fee structure");
            }
            if (amounts.put(in.feeHeadId(), in.amount().setScale(2, RoundingMode.HALF_UP)) != null) {
                throw new BusinessException(head.getName() + " is listed twice");
            }
        }

        List<FeeStructure> ofYear = structureRepository.findByAcademicYearIdAndDeletedAtIsNull(year.getId());
        UUID actor = context.actorId();
        List<FeeStructure> saved = new ArrayList<>();
        for (UUID classId : new java.util.LinkedHashSet<>(request.classIds())) {
            AcademicClass cls = classRepository.findByIdAndNotDeleted(classId).orElseThrow(() -> new ResourceNotFoundException("AcademicClass", classId));
            List<FeeStructure> ofClass = ofYear.stream().filter(s -> classId.equals(s.getAcademicClassId())).toList();
            // "ALL" and "NEW_ADMISSION / EXISTING" cannot both exist: it would be unclear which one a student pays
            if (ofClass.stream().anyMatch(s -> (s.getAppliesTo() == FeeAppliesTo.ALL) != (appliesTo == FeeAppliesTo.ALL))) {
                throw new BusinessException("FEE_STRUCTURE_CONFLICT", cls.getName() + " already has a fee structure for "
                        + (appliesTo == FeeAppliesTo.ALL ? "new / existing students separately" : "all students")
                        + "; delete it before adding one for " + appliesTo);
            }
            FeeStructure structure = ofClass.stream().filter(s -> s.getAppliesTo() == appliesTo).findFirst().orElse(null);
            boolean isNew = structure == null;
            if (isNew) {
                structure = new FeeStructure();
                structure.setStructureCode(CodeGenerator.feeStructureCode());
                structure.setAcademicYearId(year.getId());
                structure.setAcademicClassId(classId);
                structure.setAppliesTo(appliesTo);
                structure.setCreatedBy(actor);
            }
            structure.setName(blank(request.name()) ? cls.getName() + " fees " + year.getCode() : request.name().trim());
            structure.setRemarks(blank(request.remarks()) ? null : request.remarks().trim());
            if (request.status() != null) {
                structure.setStatus(request.status());
            }
            structure.setUpdatedBy(actor);
            structure = structureRepository.save(structure);

            Map<UUID, FeeStructureItem> current = new HashMap<>();
            if (!isNew) {
                itemRepository.findByFeeStructureId(structure.getId()).forEach(i -> current.put(i.getFeeHeadId(), i));
            }
            List<FeeStructureItem> removed = current.values().stream().filter(i -> !amounts.containsKey(i.getFeeHeadId())).toList();
            if (!removed.isEmpty()) {
                itemRepository.deleteAllInBatch(removed);
            }
            for (Map.Entry<UUID, BigDecimal> e : amounts.entrySet()) {
                FeeStructureItem item = current.get(e.getKey());
                if (item == null) {
                    item = new FeeStructureItem(structure.getId(), e.getKey());
                }
                item.setAmount(e.getValue());
                itemRepository.save(item);
            }
            saved.add(structure);
        }
        return respond(year.getId(), saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public FeeStructureResponse setStructureStatus(UUID id, FeeStructureStatus status) {
        FeeStructure s = structureRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeStructure", id));
        editableYear(s.getAcademicYearId());
        if (status == FeeStructureStatus.ACTIVE && itemRepository.findByFeeStructureId(id).isEmpty()) {
            throw new BusinessException("A fee structure without fee heads cannot be made ACTIVE");
        }
        s.setStatus(status);
        s.setUpdatedBy(context.actorId());
        return respond(s.getAcademicYearId(), List.of(structureRepository.save(s))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteStructure(UUID id) {
        FeeStructure s = structureRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeStructure", id));
        editableYear(s.getAcademicYearId());
        s.setDeletedAt(Instant.now());
        s.setStatus(FeeStructureStatus.ARCHIVED);
        s.setUpdatedBy(context.actorId());
        structureRepository.save(s);
    }

    /** Carries last year's structures into a new year as DRAFT, optionally with a fee increase. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public CopyResult copyStructures(CopyStructuresRequest r) {
        if (r.sourceAcademicYearId().equals(r.targetAcademicYearId())) {
            throw new BusinessException("Source and target academic year are the same");
        }
        year(r.sourceAcademicYearId());
        AcademicYear target = editableYear(r.targetAcademicYearId());
        BigDecimal factor = BigDecimal.ONE.add((r.increasePercent() != null ? r.increasePercent() : BigDecimal.ZERO).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        Map<UUID, FeeHead> heads = headsById();
        Map<UUID, AcademicClass> classes = classRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .collect(Collectors.toMap(AcademicClass::getId, c -> c));
        Set<String> taken = structureRepository.findByAcademicYearIdAndDeletedAtIsNull(target.getId()).stream()
                .map(s -> s.getAcademicClassId() + "|" + s.getAppliesTo()).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> targetHasAll = new HashSet<>();
        Set<UUID> targetHasSplit = new HashSet<>();
        structureRepository.findByAcademicYearIdAndDeletedAtIsNull(target.getId())
                .forEach(s -> (s.getAppliesTo() == FeeAppliesTo.ALL ? targetHasAll : targetHasSplit).add(s.getAcademicClassId()));

        UUID actor = context.actorId();
        List<String> notes = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        for (FeeStructure src : structureRepository.findByAcademicYearIdAndDeletedAtIsNull(r.sourceAcademicYearId())) {
            AcademicClass cls = classes.get(src.getAcademicClassId());
            boolean all = src.getAppliesTo() == FeeAppliesTo.ALL;
            if (src.getStatus() == FeeStructureStatus.ARCHIVED || cls == null || taken.contains(src.getAcademicClassId() + "|" + src.getAppliesTo())
                    || (all ? targetHasSplit : targetHasAll).contains(src.getAcademicClassId())) {
                skipped++;
                continue;
            }
            List<FeeStructureItem> items = itemRepository.findByFeeStructureId(src.getId()).stream().filter(i -> {
                FeeHead h = heads.get(i.getFeeHeadId());
                if (h == null || h.getStatus() != ConfigStatus.ACTIVE) {
                    notes.add(cls.getName() + ": a fee head that is no longer active was left out");
                    return false;
                }
                return true;
            }).toList();
            if (items.isEmpty()) {
                skipped++;
                continue;
            }
            FeeStructure copy = new FeeStructure();
            copy.setStructureCode(CodeGenerator.feeStructureCode());
            copy.setAcademicYearId(target.getId());
            copy.setAcademicClassId(src.getAcademicClassId());
            copy.setAppliesTo(src.getAppliesTo());
            copy.setName(cls.getName() + " fees " + target.getCode());
            copy.setRemarks(src.getRemarks());
            copy.setCreatedBy(actor);
            copy.setUpdatedBy(actor);
            copy = structureRepository.save(copy);
            for (FeeStructureItem i : items) {
                FeeStructureItem item = new FeeStructureItem(copy.getId(), i.getFeeHeadId());
                item.setAmount(round(i.getAmount().multiply(factor), r.roundTo()));
                itemRepository.save(item);
            }
            (all ? targetHasAll : targetHasSplit).add(src.getAcademicClassId());
            created++;
        }
        return new CopyResult(created, skipped, notes.stream().distinct().toList());
    }

    // ── Concession types ─────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ConcessionResponse> concessions() {
        Map<UUID, FeeHead> heads = headsById();
        return concessionRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(c -> concession(c, heads)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ConcessionResponse saveConcession(UUID id, ConcessionRequest r) {
        FeeConcessionType c = id == null ? new FeeConcessionType()
                : concessionRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeConcessionType", id));
        if (r.kind() == ConcessionKind.PERCENT && r.value().compareTo(HUNDRED) > 0) {
            throw new BusinessException("A PERCENT concession cannot be more than 100");
        }
        Map<UUID, FeeHead> heads = headsById();
        if (r.feeHeadId() != null && !heads.containsKey(r.feeHeadId())) {
            throw new ResourceNotFoundException("FeeHead", r.feeHeadId());
        }
        String code = code(r.code(), r.name());
        if (concessionRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .anyMatch(o -> !o.getId().equals(c.getId()) && o.getCode().equalsIgnoreCase(code))) {
            throw new DuplicateResourceException("FeeConcessionType", "code", code);
        }
        c.setCode(code);
        c.setName(r.name().trim());
        c.setKind(r.kind());
        c.setValue(r.value().setScale(2, RoundingMode.HALF_UP));
        c.setFeeHeadId(r.feeHeadId());
        c.setRequiresApproval(r.requiresApproval() == null || r.requiresApproval());
        c.setDescription(blank(r.description()) ? null : r.description().trim());
        c.setStatus(r.status() != null ? r.status() : ConfigStatus.ACTIVE);
        return concession(concessionRepository.save(c), heads);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteConcession(UUID id) {
        FeeConcessionType c = concessionRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("FeeConcessionType", id));
        c.setDeletedAt(Instant.now());
        c.setStatus(ConfigStatus.INACTIVE);
        concessionRepository.save(c);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyHead(FeeHead head, FeeHeadRequest r, List<FeeHead> existing) {
        String name = r.name().trim();
        String code = code(r.code(), name);
        for (FeeHead other : existing) {
            if (other.getId() != null && other.getId().equals(head.getId())) {
                continue;
            }
            if (other.getCode().equalsIgnoreCase(code)) {
                throw new DuplicateResourceException("FeeHead", "code", code);
            }
            if (other.getName().equalsIgnoreCase(name)) {
                throw new DuplicateResourceException("FeeHead", "name", name);
            }
        }
        head.setName(name);
        head.setCode(code);
        if (r.category() != null) {
            head.setCategory(r.category());
        }
        if (r.frequency() != null) {
            head.setFrequency(r.frequency());
        }
        if (r.optional() != null) {
            head.setOptional(r.optional());
        }
        if (r.refundable() != null) {
            head.setRefundable(r.refundable());
        }
        head.setDescription(blank(r.description()) ? null : r.description().trim());
        if (r.sortOrder() != null) {
            head.setSortOrder(r.sortOrder());
        }
        if (r.status() != null) {
            head.setStatus(r.status());
        }
    }

    /** Fee head id → number of live fee structures that charge it. */
    private Map<UUID, Long> headUsage() {
        Set<UUID> live = structureRepository.findAll().stream().filter(s -> s.getDeletedAt() == null).map(FeeStructure::getId).collect(Collectors.toSet());
        if (live.isEmpty()) {
            return Map.of();
        }
        return itemRepository.findByFeeStructureIdIn(live).stream().collect(Collectors.groupingBy(FeeStructureItem::getFeeHeadId, Collectors.counting()));
    }

    private Map<UUID, FeeHead> headsById() {
        return headRepository.findByDeletedAtIsNullOrderBySortOrderAscNameAsc().stream().collect(Collectors.toMap(FeeHead::getId, h -> h));
    }

    private List<FeeStructureResponse> respond(UUID yearId, List<FeeStructure> structures) {
        if (structures.isEmpty()) {
            return List.of();
        }
        // deleted heads still resolve, so an old structure keeps showing what it charged
        Map<UUID, FeeHead> heads = headRepository.findAll().stream().collect(Collectors.toMap(FeeHead::getId, h -> h));
        Map<UUID, AcademicClass> classes = classRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .collect(Collectors.toMap(AcademicClass::getId, c -> c));
        Map<UUID, List<FeeStructureItem>> items = itemRepository.findByFeeStructureIdIn(structures.stream().map(FeeStructure::getId).toList())
                .stream().collect(Collectors.groupingBy(FeeStructureItem::getFeeStructureId));
        List<FeeInstallment> plan = installmentRepository.findByAcademicYearIdOrderByInstallmentNoAsc(yearId);

        Comparator<FeeStructure> byClass = Comparator.comparingInt(s -> classes.containsKey(s.getAcademicClassId())
                ? classes.get(s.getAcademicClassId()).getSortOrder() : Integer.MAX_VALUE);
        return structures.stream().sorted(byClass.thenComparing(FeeStructure::getAppliesTo)).map(s -> {
            BigDecimal recurring = BigDecimal.ZERO;
            BigDecimal oneTime = BigDecimal.ZERO;
            BigDecimal optional = BigDecimal.ZERO;
            List<FeeItemResponse> lines = new ArrayList<>();
            for (FeeStructureItem i : items.getOrDefault(s.getId(), List.of())) {
                FeeHead h = heads.get(i.getFeeHeadId());
                if (h == null) {
                    continue;
                }
                BigDecimal annual = i.getAmount().multiply(BigDecimal.valueOf(h.getFrequency().timesPerYear()));
                if (h.isOptional()) {
                    optional = optional.add(annual);
                } else if (h.getFrequency() == FeeFrequency.ONE_TIME) {
                    oneTime = oneTime.add(annual);
                } else {
                    recurring = recurring.add(annual);
                }
                lines.add(new FeeItemResponse(h.getId(), h.getCode(), h.getName(), h.getCategory(), h.getFrequency(), h.isOptional(), i.getAmount(), annual));
            }
            lines.sort(Comparator.comparingInt((FeeItemResponse l) -> heads.get(l.feeHeadId()).getSortOrder()).thenComparing(FeeItemResponse::headName));
            AcademicClass cls = classes.get(s.getAcademicClassId());
            return new FeeStructureResponse(s.getId(), s.getStructureCode(), s.getAcademicYearId(), s.getAcademicClassId(),
                    cls != null ? cls.getName() : null, s.getAppliesTo(), s.getName(), s.getRemarks(), s.getStatus(), lines,
                    recurring.add(oneTime), optional, schedule(plan, recurring, oneTime));
        }).toList();
    }

    /** Recurring fees are split by each instalment's percent (the last takes the rounding); one-time fees fall due with the first. */
    static List<InstallmentAmount> schedule(List<FeeInstallment> plan, BigDecimal recurring, BigDecimal oneTime) {
        List<InstallmentAmount> out = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        for (int k = 0; k < plan.size(); k++) {
            FeeInstallment i = plan.get(k);
            BigDecimal part = k < plan.size() - 1
                    ? recurring.multiply(i.getPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                    : recurring.subtract(given);
            given = given.add(part);
            out.add(new InstallmentAmount(i.getInstallmentNo(), i.getName(), i.getDueDate(), (k == 0 ? part.add(oneTime) : part).setScale(2, RoundingMode.HALF_UP)));
        }
        return out;
    }

    static BigDecimal round(BigDecimal amount, BigDecimal roundTo) {
        if (roundTo == null || roundTo.signum() <= 0) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.divide(roundTo, 0, RoundingMode.HALF_UP).multiply(roundTo).setScale(2, RoundingMode.HALF_UP);
    }

    /** The code given, or one made from the name ("Tuition Fee" → TUITION_FEE). */
    static String code(String given, String name) {
        String base = blank(given) ? name.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "") : given.trim();
        if (base.isEmpty()) {
            base = CodeGenerator.generate(8);
        }
        return base.substring(0, Math.min(30, base.length())).toUpperCase(Locale.ROOT);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private AcademicYear year(UUID id) {
        return yearRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
    }

    private AcademicYear editableYear(UUID id) {
        AcademicYear year = year(id);
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        return year;
    }

    private static FeeHeadResponse head(FeeHead h, long used) {
        return new FeeHeadResponse(h.getId(), h.getCode(), h.getName(), h.getCategory(), h.getFrequency(), h.isOptional(), h.isRefundable(),
                h.getDescription(), h.getSortOrder(), h.getStatus(), used);
    }

    private static FeeSettingsResponse settings(FeeSettings s, boolean saved) {
        return new FeeSettingsResponse(s.getAcademicYearId(), s.getCollectionFrequency(), s.getDueDay(), s.getGraceDays(), s.getLateFineType(),
                s.getLateFineValue(), s.getLateFineCap(), s.isAllowPartialPayment(), s.isAllowOnlinePayment(), s.getReceiptPrefix(), s.getCurrency(), saved);
    }

    private static InstallmentResponse installment(FeeInstallment i) {
        return new InstallmentResponse(i.getId(), i.getInstallmentNo(), i.getName(), i.getDueDate(), i.getPercent());
    }

    private static ConcessionResponse concession(FeeConcessionType c, Map<UUID, FeeHead> heads) {
        FeeHead h = c.getFeeHeadId() == null ? null : heads.get(c.getFeeHeadId());
        return new ConcessionResponse(c.getId(), c.getCode(), c.getName(), c.getKind(), c.getValue(), c.getFeeHeadId(), h != null ? h.getName() : null,
                c.isRequiresApproval(), c.getDescription(), c.getStatus());
    }
}
