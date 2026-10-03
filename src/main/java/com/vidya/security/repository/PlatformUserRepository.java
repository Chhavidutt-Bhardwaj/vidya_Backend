package com.vidya.security.repository;

import com.vidya.security.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByEmailIgnoreCase(String email);

    Optional<PlatformUser> findByUserCode(String userCode);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<PlatformUser> findBySchoolId(UUID schoolId);
}
