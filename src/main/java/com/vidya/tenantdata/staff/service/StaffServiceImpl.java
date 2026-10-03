package com.vidya.tenantdata.staff.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffRequest;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.mapper.StaffMapper;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of Staff business operations in the tenant database schema.
 *
 * <p>All transactions are routed through {@code tenantTransactionManager}, ensuring
 * database-per-tenant isolation via the existing {@code TenantRoutingDataSource}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;
    private final TenantUserRepository tenantUserRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StaffDetailResponse createStaff(CreateStaffRequest request) {
        log.info("Creating staff for tenantUserId: [{}] with staffType: [{}]",
                request.getTenantUserId(), request.getStaffType());

        // 1. Resolve TenantUser from THIS tenant's DB (cross-tenant attack prevention)
        TenantUser tenantUser = tenantUserRepository.findByIdAndNotDeleted(request.getTenantUserId())
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", request.getTenantUserId()));

        // 2. Verify TenantUser is not already a Student (user type integrity check)
        if (tenantUser.getUserType() == TenantUserType.STUDENT) {
            throw new BusinessException("TenantUser with type STUDENT cannot be registered as Staff");
        }

        // 3. Prevent duplicate active Staff for the same TenantUser
        if (staffRepository.existsActiveByTenantUserId(request.getTenantUserId(), null)) {
            throw new DuplicateResourceException("Staff", "tenantUserId", request.getTenantUserId());
        }

        // 4. Validate employee code uniqueness if provided
        if (request.getEmployeeCode() != null && !request.getEmployeeCode().isBlank()) {
            String empCode = request.getEmployeeCode().trim();
            if (staffRepository.existsActiveByEmployeeCode(empCode, null)) {
                throw new DuplicateResourceException("Staff", "employeeCode", empCode);
            }
        }

        // 5. Update TenantUser type to STAFF if not already a staff/teacher type
        if (tenantUser.getUserType() != TenantUserType.STAFF && tenantUser.getUserType() != TenantUserType.TEACHER) {
            tenantUser.setUserType(TenantUserType.STAFF);
            tenantUserRepository.save(tenantUser);
        }

        // 6. Generate unique staff code (collision-safe loop)
        String staffCode = CodeGenerator.staffCode();
        while (staffRepository.existsByStaffCode(staffCode)) {
            staffCode = CodeGenerator.staffCode();
        }

        // 7. Persist staff record
        Staff staff = StaffMapper.toEntity(request, tenantUser, staffCode);
        Staff saved = staffRepository.save(staff);

        log.info("Successfully created staff ID: [{}] with code: [{}] for tenantUser: [{}]",
                saved.getId(), saved.getStaffCode(), saved.getTenantUser().getId());
        return StaffMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StaffDetailResponse getStaffById(UUID id) {
        Staff staff = staffRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));
        return StaffMapper.toDetailResponse(staff);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StaffDetailResponse getStaffByStaffCode(String staffCode) {
        Staff staff = staffRepository.findByStaffCodeAndNotDeleted(staffCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", staffCode));
        return StaffMapper.toDetailResponse(staff);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StaffDetailResponse getStaffByTenantUserId(UUID tenantUserId) {
        Staff staff = staffRepository.findByTenantUserIdAndNotDeleted(tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", tenantUserId));
        return StaffMapper.toDetailResponse(staff);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StaffDetailResponse updateStaff(UUID id, UpdateStaffRequest request) {
        log.info("Updating staff ID: [{}]", id);

        Staff staff = staffRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));

        // Validate employee code uniqueness on change (exclude self)
        if (request.getEmployeeCode() != null && !request.getEmployeeCode().isBlank()) {
            String newEmpCode = request.getEmployeeCode().trim();
            if (staff.getEmployeeCode() == null || !newEmpCode.equalsIgnoreCase(staff.getEmployeeCode())) {
                if (staffRepository.existsActiveByEmployeeCode(newEmpCode, id)) {
                    throw new DuplicateResourceException("Staff", "employeeCode", newEmpCode);
                }
            }
            staff.setEmployeeCode(newEmpCode);
        }

        if (request.getJoiningDate() != null) {
            staff.setJoiningDate(request.getJoiningDate());
        }
        if (request.getStaffType() != null) {
            staff.setStaffType(request.getStaffType());
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            staff.setDesignation(request.getDesignation().trim());
        }
        if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            staff.setDepartment(request.getDepartment().trim());
        }

        Staff saved = staffRepository.save(staff);
        log.info("Successfully updated staff ID: [{}]", saved.getId());
        return StaffMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StaffDetailResponse updateStaffStatus(UUID id, UpdateStaffStatusRequest request) {
        log.info("Updating staff ID: [{}] status to: [{}]", id, request.getStatus());

        Staff staff = staffRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));

        staff.updateStatus(request.getStatus());
        Staff saved = staffRepository.save(staff);

        log.info("Successfully transitioned staff ID: [{}] to employment status: [{}]",
                saved.getId(), saved.getEmploymentStatus());
        return StaffMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteStaff(UUID id) {
        log.info("Soft-deleting staff ID: [{}]", id);

        Staff staff = staffRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));

        staff.softDelete();
        staffRepository.save(staff);
        log.info("Successfully soft-deleted staff ID: [{}]", id);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<StaffResponse> searchStaff(EmploymentStatus status, StaffType staffType,
                                            String department, String designation,
                                            String searchQuery, Pageable pageable) {
        return staffRepository.searchStaff(status, staffType, department, designation, searchQuery, pageable)
                .map(StaffMapper::toResponse);
    }
}
