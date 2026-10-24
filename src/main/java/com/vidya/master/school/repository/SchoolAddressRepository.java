package com.vidya.master.school.repository;

import com.vidya.master.school.entity.SchoolAddress;
import com.vidya.master.school.entity.SchoolAddressType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolAddressRepository extends JpaRepository<SchoolAddress, UUID> {

    List<SchoolAddress> findBySchoolId(UUID schoolId);

    List<SchoolAddress> findBySchoolIdAndAddressType(UUID schoolId, SchoolAddressType addressType);

    Optional<SchoolAddress> findBySchoolIdAndPrimaryAddressTrue(UUID schoolId);

    List<SchoolAddress> findByStateIgnoreCase(String state);
}
