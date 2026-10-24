package com.vidya.master.support.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.community.entity.Community;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.support.dto.AddQueryCommentRequest;
import com.vidya.master.support.dto.CreateSupportQueryRequest;
import com.vidya.master.support.dto.EscalateQueryRequest;
import com.vidya.master.support.dto.QueryCommentResponse;
import com.vidya.master.support.dto.QueryEscalationLogResponse;
import com.vidya.master.support.dto.ResolveQueryRequest;
import com.vidya.master.support.dto.SupportQueryResponse;
import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.entity.SchoolQueryComment;
import com.vidya.master.support.entity.SchoolQueryEscalationLog;
import com.vidya.master.support.entity.SchoolSupportQuery;
import com.vidya.master.community.entity.CommissionType;
import com.vidya.master.community.entity.CommunityCommissionLedgerEntry;
import com.vidya.master.community.repository.CommunityCommissionLedgerRepository;
import com.vidya.master.support.repository.SchoolQueryCommentRepository;
import com.vidya.master.support.repository.SchoolQueryEscalationLogRepository;
import com.vidya.master.support.repository.SchoolSupportQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolSupportQueryServiceImpl implements SchoolSupportQueryService {

    private final SchoolSupportQueryRepository queryRepository;
    private final SchoolQueryEscalationLogRepository escalationLogRepository;
    private final SchoolQueryCommentRepository commentRepository;
    private final SchoolRepository schoolRepository;
    private final CommunityCommissionLedgerRepository commissionLedgerRepository;

    @Override
    @Transactional("masterTransactionManager")
    public SupportQueryResponse createQuery(CreateSupportQueryRequest request,
                                            UUID raisedByUserId,
                                            String raisedByName,
                                            String raisedByRole) {
        School school = schoolRepository.findById(request.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School", request.schoolId()));

        Community community = school.getAssignedCommunity();
        Hub hub = school.getHub();
        String ticketNumber = CodeGenerator.ticketCode();

        SchoolSupportQuery query = new SchoolSupportQuery(
                ticketNumber,
                school,
                community,
                hub,
                request.category(),
                request.priority(),
                request.subject(),
                request.description(),
                raisedByUserId,
                raisedByName,
                raisedByRole,
                school.getAssignedRmMemberId()
        );

        query = queryRepository.save(query);

        // Record initial ticket creation in escalation history
        SchoolQueryEscalationLog initialLog = new SchoolQueryEscalationLog(
                query,
                EscalationLevel.COMMUNITY_RM,
                EscalationLevel.COMMUNITY_RM,
                raisedByUserId,
                raisedByName,
                raisedByRole,
                school.getAssignedRmMemberId(),
                "Ticket opened by school",
                "Assigned to Community RM: " + (community != null ? community.getName() : "UNASSIGNED")
        );
        escalationLogRepository.save(initialLog);

        log.info("Created support ticket [{}] for school [{}] at level [COMMUNITY_RM]",
                ticketNumber, school.getSchoolCode());

        return toResponse(query);
    }

    @Override
    @Transactional("masterTransactionManager")
    public SupportQueryResponse escalateQuery(UUID queryId,
                                              EscalateQueryRequest request,
                                              UUID actorUserId,
                                              String actorName,
                                              String actorRole) {
        SchoolSupportQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Support Query", queryId));

        if (query.getStatus() == QueryStatus.RESOLVED || query.getStatus() == QueryStatus.CLOSED) {
            throw new BusinessException("Cannot escalate a query that is already " + query.getStatus());
        }

        EscalationLevel current = query.getCurrentLevel();
        EscalationLevel target = request.toLevel();

        // Validate 3-tier sequence: COMMUNITY_RM -> HUB_MANAGER -> PLATFORM_ADMIN
        if (current == EscalationLevel.COMMUNITY_RM) {
            if (target != EscalationLevel.HUB_MANAGER) {
                throw new BusinessException(
                        "Queries at Level 1 (COMMUNITY_RM) can only be escalated to Level 2 (HUB_MANAGER). " +
                        "Escalation to PLATFORM_ADMIN must go through Hub Manager.");
            }
            query.setCurrentLevel(EscalationLevel.HUB_MANAGER);
            query.setStatus(QueryStatus.ESCALATED_TO_HUB);
            query.setEscalatedToHubManagerId(request.assignedTargetUserId() != null
                    ? request.assignedTargetUserId()
                    : (query.getHub() != null ? query.getHub().getHubManagerUserId() : null));

        } else if (current == EscalationLevel.HUB_MANAGER) {
            if (target != EscalationLevel.PLATFORM_ADMIN) {
                throw new BusinessException(
                        "Queries at Level 2 (HUB_MANAGER) can only be escalated to Level 3 (PLATFORM_ADMIN).");
            }
            query.setCurrentLevel(EscalationLevel.PLATFORM_ADMIN);
            query.setStatus(QueryStatus.ESCALATED_TO_PLATFORM);
            query.setEscalatedToPlatformUserId(request.assignedTargetUserId());

        } else if (current == EscalationLevel.PLATFORM_ADMIN) {
            throw new BusinessException(
                    "Query is already at the highest level (PLATFORM_ADMIN). It cannot be escalated further.");
        }

        query = queryRepository.save(query);

        // Record escalation log
        SchoolQueryEscalationLog escalationLog = new SchoolQueryEscalationLog(
                query,
                current,
                target,
                actorUserId,
                actorName,
                actorRole,
                request.assignedTargetUserId(),
                request.reason(),
                request.notes()
        );
        escalationLogRepository.save(escalationLog);

        // Add internal system comment recording the escalation
        SchoolQueryComment comment = new SchoolQueryComment(
                query,
                actorUserId,
                actorName,
                actorRole,
                "[ESCALATED from " + current + " to " + target + "] Reason: " + request.reason() +
                (request.notes() != null ? " | Notes: " + request.notes() : ""),
                true
        );
        commentRepository.save(comment);

        log.info("Support ticket [{}] escalated from [{}] to [{}] by [{}]",
                query.getTicketNumber(), current, target, actorName);

        return toResponse(query);
    }

    @Override
    @Transactional("masterTransactionManager")
    public SupportQueryResponse resolveQuery(UUID queryId,
                                             ResolveQueryRequest request,
                                             UUID actorUserId,
                                             String actorName,
                                             String actorRole) {
        SchoolSupportQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Support Query", queryId));

        query.setResolutionSummary(request.resolutionSummary());
        query.setResolvedAt(Instant.now());
        if (request.closeImmediately()) {
            query.setStatus(QueryStatus.CLOSED);
            query.setClosedAt(Instant.now());
        } else {
            query.setStatus(QueryStatus.RESOLVED);
        }

        query = queryRepository.save(query);

        // Record resolution comment
        SchoolQueryComment comment = new SchoolQueryComment(
                query,
                actorUserId,
                actorName,
                actorRole,
                "[RESOLVED] " + request.resolutionSummary(),
                false
        );
        commentRepository.save(comment);

        // Record resolution commission to Community B (the Relationship Manager servicing this query)
        if (query.getAssignedCommunity() != null) {
            BigDecimal queryCommissionAmount = BigDecimal.valueOf(250.00);
            CommunityCommissionLedgerEntry ledgerEntry = new CommunityCommissionLedgerEntry(
                    query.getAssignedCommunity(),
                    CommissionType.QUERY_RESOLUTION,
                    query.getSchool().getId(),
                    query.getSchool().getSchoolName(),
                    query,
                    query.getTicketNumber(),
                    queryCommissionAmount,
                    "INR",
                    "Query resolution incentive for ticket " + query.getTicketNumber() + " resolved by " + actorName
            );
            commissionLedgerRepository.save(ledgerEntry);
            log.info("Recorded QUERY_RESOLUTION commission of [{} INR] for RM Community [{}] on ticket [{}]",
                    queryCommissionAmount, query.getAssignedCommunity().getName(), query.getTicketNumber());
        }

        log.info("Support ticket [{}] resolved by [{}] with status [{}]",
                query.getTicketNumber(), actorName, query.getStatus());

        return toResponse(query);
    }

    @Override
    @Transactional("masterTransactionManager")
    public QueryCommentResponse addComment(UUID queryId,
                                           AddQueryCommentRequest request,
                                           UUID authorUserId,
                                           String authorName,
                                           String authorRole) {
        SchoolSupportQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Support Query", queryId));

        SchoolQueryComment comment = new SchoolQueryComment(
                query,
                authorUserId,
                authorName,
                authorRole,
                request.message(),
                request.internalNote()
        );

        comment = commentRepository.save(comment);
        return toCommentResponse(comment);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public SupportQueryResponse getQueryById(UUID queryId) {
        SchoolSupportQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Support Query", queryId));
        return toResponse(query);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public SupportQueryResponse getQueryByTicketNumber(String ticketNumber) {
        SchoolSupportQuery query = queryRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Support Query", ticketNumber));
        return toResponse(query);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public Page<SupportQueryResponse> listQueries(UUID schoolId,
                                                  UUID communityId,
                                                  UUID hubId,
                                                  QueryStatus status,
                                                  QueryCategory category,
                                                  QueryPriority priority,
                                                  EscalationLevel currentLevel,
                                                  Pageable pageable) {
        return queryRepository.findWithFilters(
                schoolId, communityId, hubId, status, category, priority, currentLevel, pageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public List<QueryCommentResponse> getComments(UUID queryId, boolean includeInternal) {
        List<SchoolQueryComment> list = includeInternal
                ? commentRepository.findAllByQueryIdOrderByCreatedAtAsc(queryId)
                : commentRepository.findAllByQueryIdAndInternalNoteFalseOrderByCreatedAtAsc(queryId);
        return list.stream().map(this::toCommentResponse).toList();
    }

    private SupportQueryResponse toResponse(SchoolSupportQuery q) {
        List<QueryEscalationLogResponse> escalationHistory = escalationLogRepository
                .findAllByQueryIdOrderByOccurredAtAsc(q.getId())
                .stream()
                .map(log -> new QueryEscalationLogResponse(
                        log.getId(),
                        log.getFromLevel(),
                        log.getToLevel(),
                        log.getEscalatedByUserId(),
                        log.getEscalatedByName(),
                        log.getEscalatedByRole(),
                        log.getEscalatedToUserId(),
                        log.getReason(),
                        log.getNotes(),
                        log.getOccurredAt()
                ))
                .toList();

        List<QueryCommentResponse> comments = commentRepository
                .findAllByQueryIdOrderByCreatedAtAsc(q.getId())
                .stream()
                .map(this::toCommentResponse)
                .toList();

        School school = q.getSchool();
        Community community = q.getAssignedCommunity();
        Hub hub = q.getHub();

        return new SupportQueryResponse(
                q.getId(),
                q.getTicketNumber(),
                school != null ? school.getId() : null,
                school != null ? school.getSchoolName() : null,
                school != null ? school.getSchoolCode() : null,
                community != null ? community.getId() : null,
                community != null ? community.getName() : null,
                hub != null ? hub.getId() : null,
                hub != null ? hub.getName() : null,
                hub != null ? hub.getState() : null,
                q.getCategory(),
                q.getPriority(),
                q.getStatus(),
                q.getCurrentLevel(),
                q.getSubject(),
                q.getDescription(),
                q.getRaisedByUserId(),
                q.getRaisedByName(),
                q.getRaisedByRole(),
                q.getAssignedRmMemberId(),
                q.getEscalatedToHubManagerId(),
                q.getEscalatedToPlatformUserId(),
                q.getResolutionSummary(),
                q.getResolvedAt(),
                q.getClosedAt(),
                q.getCreatedAt(),
                q.getUpdatedAt(),
                escalationHistory,
                comments
        );
    }

    private QueryCommentResponse toCommentResponse(SchoolQueryComment c) {
        return new QueryCommentResponse(
                c.getId(),
                c.getAuthorUserId(),
                c.getAuthorName(),
                c.getAuthorRole(),
                c.getMessage(),
                c.isInternalNote(),
                c.getCreatedAt()
        );
    }
}
