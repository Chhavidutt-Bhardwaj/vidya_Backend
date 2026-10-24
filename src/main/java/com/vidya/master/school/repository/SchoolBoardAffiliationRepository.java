package com.vidya.master.school.repository;

import com.vidya.master.school.entity.BoardAffiliationType;
import com.vidya.master.school.entity.SchoolBoardAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolBoardAffiliationRepository extends JpaRepository<SchoolBoardAffiliation, UUID> {

    List<SchoolBoardAffiliation> findBySchoolId(UUID schoolId);

    List<SchoolBoardAffiliation> findBySchoolIdAndBoardType(UUID schoolId, BoardAffiliationType boardType);

    Optional<SchoolBoardAffiliation> findBySchoolIdAndPrimaryBoardTrue(UUID schoolId);
}
