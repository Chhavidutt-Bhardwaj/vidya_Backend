package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Homework;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, UUID> {

    Optional<Homework> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByHomeworkCode(String homeworkCode);
}
