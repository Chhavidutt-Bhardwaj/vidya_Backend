package com.vidya.tenantdata.operations.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CategoryRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CategoryResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.LibrarySettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.LibrarySettingsResponse;
import com.vidya.tenantdata.operations.entity.LibraryCategory;
import com.vidya.tenantdata.operations.entity.LibrarySettings;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.repository.LibraryCategoryRepository;
import com.vidya.tenantdata.operations.repository.LibrarySettingsRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Library setup: lending rules and book categories. The catalogue and issue / return are not part of this service. */
@Service
@RequiredArgsConstructor
public class LibraryConfigService {

    private static final short ID = 1;

    private final LibrarySettingsRepository settingsRepository;
    private final LibraryCategoryRepository categoryRepository;
    private final SchoolContext context;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public LibrarySettingsResponse settings() {
        return settingsRepository.findById(ID).map(s -> response(s, true)).orElseGet(() -> response(new LibrarySettings(), false));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public LibrarySettingsResponse saveSettings(LibrarySettingsRequest r) {
        LibrarySettings s = settingsRepository.findById(ID).orElseGet(LibrarySettings::new);
        LibrarySettings d = new LibrarySettings();
        s.setStudentMaxBooks(r.studentMaxBooks() != null ? r.studentMaxBooks() : d.getStudentMaxBooks());
        s.setStudentLoanDays(r.studentLoanDays() != null ? r.studentLoanDays() : d.getStudentLoanDays());
        s.setStaffMaxBooks(r.staffMaxBooks() != null ? r.staffMaxBooks() : d.getStaffMaxBooks());
        s.setStaffLoanDays(r.staffLoanDays() != null ? r.staffLoanDays() : d.getStaffLoanDays());
        s.setMaxRenewals(r.maxRenewals() != null ? r.maxRenewals() : d.getMaxRenewals());
        s.setFinePerDay(r.finePerDay() != null ? r.finePerDay() : d.getFinePerDay());
        s.setFineGraceDays(r.fineGraceDays() != null ? r.fineGraceDays() : 0);
        s.setMaxFine(r.maxFine());
        s.setLostBookChargePercent(r.lostBookChargePercent() != null ? r.lostBookChargePercent() : d.getLostBookChargePercent());
        s.setAllowReservation(r.allowReservation() == null || r.allowReservation());
        s.setUpdatedBy(context.actorId());
        return response(settingsRepository.save(s), true);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<CategoryResponse> categories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(LibraryConfigService::response).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CategoryResponse saveCategory(UUID id, CategoryRequest r) {
        LibraryCategory c = id == null ? new LibraryCategory()
                : categoryRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("LibraryCategory", id));
        String name = r.name().trim();
        String code = FeeConfigService.code(r.code(), name);
        for (LibraryCategory other : categoryRepository.findByDeletedAtIsNullOrderByNameAsc()) {
            if (other.getId().equals(c.getId())) {
                continue;
            }
            if (other.getCode().equalsIgnoreCase(code)) {
                throw new DuplicateResourceException("LibraryCategory", "code", code);
            }
            if (other.getName().equalsIgnoreCase(name)) {
                throw new DuplicateResourceException("LibraryCategory", "name", name);
            }
        }
        c.setName(name);
        c.setCode(code);
        c.setDescription(FeeConfigService.blank(r.description()) ? null : r.description().trim());
        c.setStatus(r.status() != null ? r.status() : ConfigStatus.ACTIVE);
        return response(categoryRepository.save(c));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteCategory(UUID id) {
        LibraryCategory c = categoryRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("LibraryCategory", id));
        c.setDeletedAt(Instant.now());
        c.setStatus(ConfigStatus.INACTIVE);
        categoryRepository.save(c);
    }

    private static LibrarySettingsResponse response(LibrarySettings s, boolean saved) {
        return new LibrarySettingsResponse(s.getStudentMaxBooks(), s.getStudentLoanDays(), s.getStaffMaxBooks(), s.getStaffLoanDays(), s.getMaxRenewals(),
                s.getFinePerDay(), s.getFineGraceDays(), s.getMaxFine(), s.getLostBookChargePercent(), s.isAllowReservation(), saved);
    }

    private static CategoryResponse response(LibraryCategory c) {
        return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.getStatus());
    }
}
