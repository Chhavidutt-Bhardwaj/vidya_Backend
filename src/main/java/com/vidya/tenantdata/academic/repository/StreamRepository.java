package com.vidya.tenantdata.academic.repository;

import com.vidya.tenantdata.academic.entity.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StreamRepository extends JpaRepository<Stream, UUID> {

    @Query("SELECT s FROM Stream s WHERE s.deletedAt IS NULL ORDER BY s.sortOrder, s.name")
    List<Stream> findAllNotDeleted();

    @Query("SELECT s FROM Stream s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Stream> findByIdAndNotDeleted(UUID id);

    @Query("SELECT s FROM Stream s WHERE upper(s.code) = upper(:code) AND s.deletedAt IS NULL")
    Optional<Stream> findByCode(String code);

    @Query("SELECT count(s) > 0 FROM Stream s WHERE lower(s.name) = lower(:name) AND s.deletedAt IS NULL AND s.id <> :excludeId")
    boolean nameTaken(String name, UUID excludeId);
}
