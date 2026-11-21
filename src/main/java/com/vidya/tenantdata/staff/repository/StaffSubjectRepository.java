package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.StaffSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StaffSubjectRepository extends JpaRepository<StaffSubject, UUID> {

    List<StaffSubject> findByStaffId(UUID staffId);

    List<StaffSubject> findBySubjectId(UUID subjectId);

    List<StaffSubject> findByStaffIdIn(Collection<UUID> staffIds);

    @Modifying
    @Query("DELETE FROM StaffSubject s WHERE s.staffId = :staffId")
    void deleteByStaffId(UUID staffId);
}
