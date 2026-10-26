package com.vidya.master.support.repository;

import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.entity.SchoolSupportQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolSupportQueryRepository extends JpaRepository<SchoolSupportQuery, UUID> {

    Optional<SchoolSupportQuery> findByTicketNumber(String ticketNumber);

    boolean existsByTicketNumber(String ticketNumber);

    Page<SchoolSupportQuery> findBySchoolId(UUID schoolId, Pageable pageable);

    Page<SchoolSupportQuery> findByAssignedCommunityId(UUID communityId, Pageable pageable);

    Page<SchoolSupportQuery> findByHubId(UUID hubId, Pageable pageable);

    Page<SchoolSupportQuery> findByCurrentLevel(EscalationLevel currentLevel, Pageable pageable);

    @Query("SELECT q FROM SchoolSupportQuery q WHERE " +
           "(:schoolId IS NULL OR q.school.id = :schoolId) AND " +
           "(:communityId IS NULL OR q.assignedCommunity.id = :communityId) AND " +
           "(:hubId IS NULL OR q.hub.id = :hubId) AND " +
           "(:status IS NULL OR q.status = :status) AND " +
           "(:category IS NULL OR q.category = :category) AND " +
           "(:priority IS NULL OR q.priority = :priority) AND " +
           "(:currentLevel IS NULL OR q.currentLevel = :currentLevel)")
    Page<SchoolSupportQuery> findWithFilters(@Param("schoolId") UUID schoolId,
                                            @Param("communityId") UUID communityId,
                                            @Param("hubId") UUID hubId,
                                            @Param("status") QueryStatus status,
                                            @Param("category") QueryCategory category,
                                            @Param("priority") QueryPriority priority,
                                            @Param("currentLevel") EscalationLevel currentLevel,
                                            Pageable pageable);

    long countByAssignedCommunityIdAndStatusIn(UUID communityId, java.util.Collection<QueryStatus> statuses);

    long countByStatus(QueryStatus status);

    long countByStatusIn(java.util.Collection<QueryStatus> statuses);
}
