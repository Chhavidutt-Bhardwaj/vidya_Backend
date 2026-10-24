package com.vidya.master.support;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.support.dto.CreateSupportQueryRequest;
import com.vidya.master.support.dto.EscalateQueryRequest;
import com.vidya.master.support.dto.ResolveQueryRequest;
import com.vidya.master.support.dto.SupportQueryResponse;
import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.entity.SchoolSupportQuery;
import com.vidya.master.support.repository.SchoolQueryCommentRepository;
import com.vidya.master.support.repository.SchoolQueryEscalationLogRepository;
import com.vidya.master.support.repository.SchoolSupportQueryRepository;
import com.vidya.master.support.service.SchoolSupportQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolSupportQueryService — 3-Tier Escalation Workflow Tests")
class SchoolSupportQueryServiceTest {

    @Mock
    private SchoolSupportQueryRepository queryRepository;

    @Mock
    private SchoolQueryEscalationLogRepository escalationLogRepository;

    @Mock
    private SchoolQueryCommentRepository commentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private com.vidya.master.community.repository.CommunityCommissionLedgerRepository commissionLedgerRepository;

    @InjectMocks
    private SchoolSupportQueryServiceImpl queryService;

    private UUID schoolId;
    private UUID communityId;
    private UUID hubId;
    private UUID hubManagerId;
    private School school;
    private Community community;
    private Hub hub;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        communityId = UUID.randomUUID();
        hubId = UUID.randomUUID();
        hubManagerId = UUID.randomUUID();

        community = new Community(
                "COM-DEL001",
                "Delhi Community RM",
                "Regional agency",
                CommunityType.NGO,
                CommunityStatus.ACTIVE
        );

        hub = new Hub(
                "HUB-DEL-01",
                "Delhi Central Hub",
                "Delhi",
                "North India",
                "Central Delhi Hub",
                community,
                hubManagerId,
                "Delhi Hub Manager",
                UUID.randomUUID(),
                "Delhi Sub-Manager",
                "delhi@vidya.io",
                "+91-11-23456789"
        );

        school = new School(
                "SCH-DEL001",
                "Delhi Modern Public School",
                "Delhi Modern Education Society",
                "REG-DEL-101",
                SchoolBoard.CBSE,
                SchoolType.ALL,
                "principal@delhimodern.edu",
                "+919876543210",
                "https://delhimodern.edu",
                SchoolStatus.ACTIVE
        );
        school.setAssignedCommunity(community);
        school.setHub(hub);
    }

    @Test
    @DisplayName("createQuery: school creates ticket routed to Level 1 (COMMUNITY_RM)")
    void createQuery_Success() {
        CreateSupportQueryRequest request = new CreateSupportQueryRequest(
                schoolId,
                QueryCategory.PAYMENT_GATEWAY,
                QueryPriority.HIGH,
                "Fee payment transaction failure",
                "Parents getting 500 error on checkout for Term 2 fees"
        );

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(queryRepository.save(any(SchoolSupportQuery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportQueryResponse response = queryService.createQuery(
                request,
                UUID.randomUUID(),
                "Principal Sharma",
                "PRINCIPAL"
        );

        assertThat(response).isNotNull();
        assertThat(response.category()).isEqualTo(QueryCategory.PAYMENT_GATEWAY);
        assertThat(response.priority()).isEqualTo(QueryPriority.HIGH);
        assertThat(response.currentLevel()).isEqualTo(EscalationLevel.COMMUNITY_RM);
        assertThat(response.status()).isEqualTo(QueryStatus.OPEN);
        assertThat(response.assignedCommunityName()).isEqualTo("Delhi Community RM");
        assertThat(response.hubName()).isEqualTo("Delhi Central Hub");
    }

    @Test
    @DisplayName("escalateQuery: Community RM escalates to Level 2 (HUB_MANAGER)")
    void escalateQuery_ToHubManager_Success() {
        UUID queryId = UUID.randomUUID();
        SchoolSupportQuery query = new SchoolSupportQuery(
                "TKT-1001",
                school,
                community,
                hub,
                QueryCategory.PAYMENT_GATEWAY,
                QueryPriority.HIGH,
                "Fee payment error",
                "Payment error description",
                UUID.randomUUID(),
                "School Admin",
                "SCHOOL_ADMIN",
                null
        );

        when(queryRepository.findById(queryId)).thenReturn(Optional.of(query));
        when(queryRepository.save(any(SchoolSupportQuery.class))).thenReturn(query);

        EscalateQueryRequest request = new EscalateQueryRequest(
                EscalationLevel.HUB_MANAGER,
                "Requires regional banking coordinator authorization",
                "Gateway configuration at bank end needs reset",
                hubManagerId
        );

        SupportQueryResponse response = queryService.escalateQuery(
                queryId,
                request,
                UUID.randomUUID(),
                "Community RM Agent",
                "COMMUNITY_MANAGER"
        );

        assertThat(response).isNotNull();
        assertThat(query.getCurrentLevel()).isEqualTo(EscalationLevel.HUB_MANAGER);
        assertThat(query.getStatus()).isEqualTo(QueryStatus.ESCALATED_TO_HUB);
        assertThat(query.getEscalatedToHubManagerId()).isEqualTo(hubManagerId);
        verify(escalationLogRepository).save(any());
        verify(commentRepository).save(any());
    }

    @Test
    @DisplayName("escalateQuery: Hub Manager escalates to Level 3 (PLATFORM_ADMIN)")
    void escalateQuery_ToPlatformAdmin_Success() {
        UUID queryId = UUID.randomUUID();
        SchoolSupportQuery query = new SchoolSupportQuery(
                "TKT-1002",
                school,
                community,
                hub,
                QueryCategory.SOFTWARE_BUG,
                QueryPriority.CRITICAL,
                "Attendance export CSV broken",
                "Stack trace on export",
                UUID.randomUUID(),
                "Teacher",
                "TEACHER",
                null
        );
        query.setCurrentLevel(EscalationLevel.HUB_MANAGER);
        query.setStatus(QueryStatus.ESCALATED_TO_HUB);

        when(queryRepository.findById(queryId)).thenReturn(Optional.of(query));
        when(queryRepository.save(any(SchoolSupportQuery.class))).thenReturn(query);

        UUID platformUserId = UUID.randomUUID();
        EscalateQueryRequest request = new EscalateQueryRequest(
                EscalationLevel.PLATFORM_ADMIN,
                "Requires software engineer hotfix in backend codebase",
                "NullPointer in CSV writer",
                platformUserId
        );

        SupportQueryResponse response = queryService.escalateQuery(
                queryId,
                request,
                hubManagerId,
                "Delhi Hub Lead Manager",
                "HUB_MANAGER"
        );

        assertThat(response).isNotNull();
        assertThat(query.getCurrentLevel()).isEqualTo(EscalationLevel.PLATFORM_ADMIN);
        assertThat(query.getStatus()).isEqualTo(QueryStatus.ESCALATED_TO_PLATFORM);
        assertThat(query.getEscalatedToPlatformUserId()).isEqualTo(platformUserId);
    }

    @Test
    @DisplayName("escalateQuery: invalid jump from Level 1 directly to Level 3 throws BusinessException")
    void escalateQuery_InvalidDirectJump_ThrowsException() {
        UUID queryId = UUID.randomUUID();
        SchoolSupportQuery query = new SchoolSupportQuery(
                "TKT-1003",
                school,
                community,
                hub,
                QueryCategory.PAYMENT_GATEWAY,
                QueryPriority.HIGH,
                "Payment error",
                "Description",
                UUID.randomUUID(),
                "Admin",
                "ADMIN",
                null
        );

        when(queryRepository.findById(queryId)).thenReturn(Optional.of(query));

        EscalateQueryRequest request = new EscalateQueryRequest(
                EscalationLevel.PLATFORM_ADMIN,
                "Bypassing Hub Manager",
                null,
                null
        );

        assertThatThrownBy(() -> queryService.escalateQuery(
                queryId, request, UUID.randomUUID(), "Agent", "AGENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must go through Hub Manager");
    }

    @Test
    @DisplayName("resolveQuery: marks ticket RESOLVED with resolution summary")
    void resolveQuery_Success() {
        UUID queryId = UUID.randomUUID();
        SchoolSupportQuery query = new SchoolSupportQuery(
                "TKT-1004",
                school,
                community,
                hub,
                QueryCategory.PAYMENT_GATEWAY,
                QueryPriority.HIGH,
                "Payment error",
                "Description",
                UUID.randomUUID(),
                "Admin",
                "ADMIN",
                null
        );

        when(queryRepository.findById(queryId)).thenReturn(Optional.of(query));
        when(queryRepository.save(any(SchoolSupportQuery.class))).thenReturn(query);

        ResolveQueryRequest request = new ResolveQueryRequest(
                "Updated webhook URL in Cashfree merchant console. Re-verified callback.",
                false
        );

        SupportQueryResponse response = queryService.resolveQuery(
                queryId, request, UUID.randomUUID(), "Tech Support", "PLATFORM_ADMIN");

        assertThat(response).isNotNull();
        assertThat(query.getStatus()).isEqualTo(QueryStatus.RESOLVED);
        assertThat(query.getResolutionSummary()).contains("Cashfree merchant console");
        assertThat(query.getResolvedAt()).isNotNull();
        verify(commissionLedgerRepository).save(any());
    }
}
