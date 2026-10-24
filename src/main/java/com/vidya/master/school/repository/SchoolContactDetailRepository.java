package com.vidya.master.school.repository;

import com.vidya.master.school.entity.SchoolContactDetail;
import com.vidya.master.school.entity.SchoolContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolContactDetailRepository extends JpaRepository<SchoolContactDetail, UUID> {

    List<SchoolContactDetail> findBySchoolId(UUID schoolId);

    List<SchoolContactDetail> findBySchoolIdAndContactType(UUID schoolId, SchoolContactType contactType);

    Optional<SchoolContactDetail> findBySchoolIdAndPrimaryContactTrue(UUID schoolId);
}
