package com.vidya.master.community.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.CommunityPersonalDetailRequest;
import com.vidya.master.community.dto.CommunityPersonalDetailResponse;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityAddress;
import com.vidya.master.community.entity.CommunityBankDetail;
import com.vidya.master.community.entity.CommunityCommission;
import com.vidya.master.community.entity.CommunityLoginHistory;
import com.vidya.master.community.entity.CommunityPayroll;
import com.vidya.master.community.entity.CommunityPersonalDetail;
import com.vidya.master.community.entity.CommunityProfessionalDetail;
import com.vidya.master.community.entity.LoginStatus;
import com.vidya.master.community.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing community detail tables (personal, professional, address, bank, payroll, commission, login history).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityDetailService {

    private final CommunityRepository communityRepository;
    private final CommunityPersonalDetailRepository personalDetailRepository;
    private final CommunityProfessionalDetailRepository professionalDetailRepository;
    private final CommunityAddressRepository addressRepository;
    private final CommunityBankDetailRepository bankDetailRepository;
    private final CommunityPayrollRepository payrollRepository;
    private final CommunityCommissionRepository commissionRepository;
    private final CommunityLoginHistoryRepository loginHistoryRepository;

    // ==================== Personal Details ====================

    @Transactional
    public CommunityPersonalDetailResponse createPersonalDetail(UUID communityId, CommunityPersonalDetailRequest request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        if (personalDetailRepository.existsByCommunityId(communityId)) {
            throw new BusinessException("Personal details already exist for this community");
        }

        CommunityPersonalDetail detail = new CommunityPersonalDetail(
                community,
                request.getContactPersonName(),
                request.getDesignation(),
                request.getAlternateEmail(),
                request.getAlternatePhone(),
                request.getDateOfBirth(),
                request.getGender(),
                request.getPanNumber(),
                request.getAadhaarNumber()
        );

        CommunityPersonalDetail saved = personalDetailRepository.save(detail);
        log.info("Created personal details for community: {}", communityId);

        return mapToPersonalDetailResponse(saved);
    }

    @Transactional
    public CommunityPersonalDetailResponse updatePersonalDetail(UUID communityId, CommunityPersonalDetailRequest request) {
        CommunityPersonalDetail detail = personalDetailRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityPersonalDetail", communityId));

        detail.setContactPersonName(request.getContactPersonName());
        detail.setDesignation(request.getDesignation());
        detail.setAlternateEmail(request.getAlternateEmail());
        detail.setAlternatePhone(request.getAlternatePhone());
        detail.setDateOfBirth(request.getDateOfBirth());
        detail.setGender(request.getGender());
        detail.setPanNumber(request.getPanNumber());
        detail.setAadhaarNumber(request.getAadhaarNumber());

        CommunityPersonalDetail saved = personalDetailRepository.save(detail);
        log.info("Updated personal details for community: {}", communityId);

        return mapToPersonalDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public CommunityPersonalDetailResponse getPersonalDetail(UUID communityId) {
        CommunityPersonalDetail detail = personalDetailRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityPersonalDetail", communityId));
        return mapToPersonalDetailResponse(detail);
    }

    // ==================== Professional Details ====================

    @Transactional
    public CommunityProfessionalDetail createProfessionalDetail(UUID communityId, Map<String, Object> request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        if (professionalDetailRepository.existsByCommunityId(communityId)) {
            throw new BusinessException("Professional details already exist for this community");
        }

        CommunityProfessionalDetail detail = new CommunityProfessionalDetail(
                community,
                (String) request.get("organizationType"),
                (String) request.get("industry"),
                (String) request.get("businessRegistrationNumber"),
                (String) request.get("gstNumber"),
                (String) request.get("companyWebsite"),
                (String) request.get("linkedinProfile"),
                (Integer) request.get("yearsInOperation"),
                (Integer) request.get("employeeCount"),
                com.vidya.master.community.entity.AnnualRevenueRange.valueOf((String) request.get("annualRevenueRange"))
        );

        CommunityProfessionalDetail saved = professionalDetailRepository.save(detail);
        log.info("Created professional details for community: {}", communityId);

        return saved;
    }

    @Transactional(readOnly = true)
    public CommunityProfessionalDetail getProfessionalDetail(UUID communityId) {
        return professionalDetailRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityProfessionalDetail", communityId));
    }

    // ==================== Address Management ====================

    @Transactional
    public CommunityAddress addAddress(UUID communityId, Map<String, Object> request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityAddress address = new CommunityAddress(
                community,
                com.vidya.master.community.entity.AddressType.valueOf((String) request.get("addressType")),
                (String) request.get("addressLine1"),
                (String) request.get("addressLine2"),
                (String) request.get("landmark"),
                (String) request.get("city"),
                (String) request.get("state"),
                (String) request.get("pincode"),
                (String) request.get("country"),
                (Boolean) request.get("isPrimary")
        );

        CommunityAddress saved = addressRepository.save(address);
        log.info("Added address for community: {}", communityId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CommunityAddress> getAddresses(UUID communityId) {
        return addressRepository.findByCommunityId(communityId);
    }

    // ==================== Bank Details ====================

    @Transactional
    public CommunityBankDetail addBankDetail(UUID communityId, Map<String, Object> request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityBankDetail bankDetail = new CommunityBankDetail(
                community,
                (String) request.get("bankName"),
                (String) request.get("accountNumber"),
                com.vidya.master.community.entity.BankAccountType.valueOf((String) request.get("accountType")),
                (String) request.get("ifscCode"),
                (String) request.get("branchName"),
                (String) request.get("accountHolderName"),
                (Boolean) request.get("isPrimary")
        );

        CommunityBankDetail saved = bankDetailRepository.save(bankDetail);
        log.info("Added bank detail for community: {}", communityId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CommunityBankDetail> getBankDetails(UUID communityId) {
        return bankDetailRepository.findByCommunityId(communityId);
    }

    // ==================== Payroll Management ====================

    @Transactional
    public CommunityPayroll createPayroll(UUID communityId, Map<String, Object> request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityPayroll payroll = new CommunityPayroll(
                community,
                com.vidya.master.community.entity.PayrollFrequency.valueOf((String) request.get("payrollFrequency")),
                (Integer) request.get("payrollDay"),
                (Integer) request.get("payrollCycleStartDay"),
                (Integer) request.get("payrollCycleEndDay"),
                (Map<String, Object>) request.get("baseSalaryStructure"),
                (Map<String, Object>) request.get("deductionConfig"),
                (Map<String, Object>) request.get("allowanceConfig"),
                (Map<String, Object>) request.get("taxConfig"),
                LocalDate.parse((String) request.get("effectiveFrom")),
                request.get("effectiveTo") != null ? LocalDate.parse((String) request.get("effectiveTo")) : null
        );

        CommunityPayroll saved = payrollRepository.save(payroll);
        log.info("Created payroll for community: {}", communityId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CommunityPayroll> getPayrolls(UUID communityId) {
        return payrollRepository.findByCommunityId(communityId);
    }

    // ==================== Commission Management ====================

    @Transactional
    public CommunityCommission createCommission(UUID communityId, Map<String, Object> request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityCommission commission = new CommunityCommission(
                community,
                com.vidya.master.community.entity.CommissionType.valueOf((String) request.get("commissionType")),
                new BigDecimal(request.get("commissionRate").toString()),
                com.vidya.master.community.entity.CommissionBasis.valueOf((String) request.get("commissionBasis")),
                (Map<String, Object>) request.get("tierConfig"),
                request.get("minimumThreshold") != null ? new BigDecimal(request.get("minimumThreshold").toString()) : null,
                request.get("maximumCap") != null ? new BigDecimal(request.get("maximumCap").toString()) : null,
                (String) request.get("paymentTerms"),
                LocalDate.parse((String) request.get("effectiveFrom")),
                request.get("effectiveTo") != null ? LocalDate.parse((String) request.get("effectiveTo")) : null
        );

        CommunityCommission saved = commissionRepository.save(commission);
        log.info("Created commission for community: {}", communityId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CommunityCommission> getCommissions(UUID communityId) {
        return commissionRepository.findByCommunityId(communityId);
    }

    // ==================== Login History ====================

    @Transactional
    public CommunityLoginHistory recordLogin(UUID communityId, UUID platformUserId, Map<String, Object> metadata) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityLoginHistory loginHistory = new CommunityLoginHistory(
                community,
                platformUserId,
                Instant.now(),
                null,
                (String) metadata.get("ipAddress"),
                (String) metadata.get("userAgent"),
                (String) metadata.get("deviceType"),
                (String) metadata.get("browser"),
                (String) metadata.get("location"),
                LoginStatus.SUCCESS,
                null,
                (String) metadata.get("sessionId")
        );

        CommunityLoginHistory saved = loginHistoryRepository.save(loginHistory);
        log.info("Recorded login for user: {} in community: {}", platformUserId, communityId);

        return saved;
    }

    @Transactional
    public void recordLogout(UUID communityId, UUID platformUserId, String sessionId) {
        List<CommunityLoginHistory> recentLogins = loginHistoryRepository
                .findByPlatformUserIdOrderByLoginTimeDesc(platformUserId);

        recentLogins.stream()
                .filter(login -> login.getSessionId() != null && login.getSessionId().equals(sessionId))
                .filter(login -> login.getLogoutTime() == null)
                .findFirst()
                .ifPresent(login -> {
                    login.setLogoutTime(Instant.now());
                    loginHistoryRepository.save(login);
                    log.info("Recorded logout for user: {} in community: {}", platformUserId, communityId);
                });
    }

    @Transactional(readOnly = true)
    public List<CommunityLoginHistory> getLoginHistory(UUID communityId) {
        return loginHistoryRepository.findByCommunityIdOrderByLoginTimeDesc(communityId);
    }

    private CommunityPersonalDetailResponse mapToPersonalDetailResponse(CommunityPersonalDetail detail) {
        return new CommunityPersonalDetailResponse(
                detail.getId(),
                detail.getCommunity().getId(),
                detail.getContactPersonName(),
                detail.getDesignation(),
                detail.getAlternateEmail(),
                detail.getAlternatePhone(),
                detail.getDateOfBirth(),
                detail.getGender(),
                detail.getPanNumber(),
                detail.getAadhaarNumber(),
                detail.getCreatedAt(),
                detail.getUpdatedAt(),
                detail.getCreatedBy(),
                detail.getUpdatedBy()
        );
    }
}
