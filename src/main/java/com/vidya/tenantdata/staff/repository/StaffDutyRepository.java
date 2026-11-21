package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.StaffDuty;
import com.vidya.tenantdata.staff.enums.DutyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StaffDutyRepository extends JpaRepository<StaffDuty, UUID> {

    List<StaffDuty> findByStaffIdOrderByStartDateDesc(UUID staffId);

    List<StaffDuty> findByStaffIdAndStatus(UUID staffId, DutyStatus status);

    List<StaffDuty> findByDutyTypeIdAndStatus(UUID dutyTypeId, DutyStatus status);

    List<StaffDuty> findByStatus(DutyStatus status);

    List<StaffDuty> findByStaffIdInAndStatus(Collection<UUID> staffIds, DutyStatus status);

    long countByDutyTypeId(UUID dutyTypeId);

    boolean existsByDutyTypeIdAndStatus(UUID dutyTypeId, DutyStatus status);
}
