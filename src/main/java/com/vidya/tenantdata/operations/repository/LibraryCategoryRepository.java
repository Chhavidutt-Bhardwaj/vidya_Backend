package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.LibraryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryCategoryRepository extends JpaRepository<LibraryCategory, UUID> {

    List<LibraryCategory> findByDeletedAtIsNullOrderByNameAsc();

    Optional<LibraryCategory> findByIdAndDeletedAtIsNull(UUID id);
}
