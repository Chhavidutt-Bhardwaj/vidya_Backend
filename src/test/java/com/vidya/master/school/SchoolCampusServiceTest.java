package com.vidya.master.school;

import com.vidya.common.exception.TenantNotFoundException;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolCampusesResponse;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.school.service.SchoolCampusService;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("School campuses for the dashboard")
class SchoolCampusServiceTest {

    private final TenantRegistryService registry = mock(TenantRegistryService.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final SchoolBranchRepository branches = mock(SchoolBranchRepository.class);
    private final SchoolMapper mapper = mock(SchoolMapper.class);
    private final SchoolCampusService service = new SchoolCampusService(registry, schools, branches, mapper);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("returns the current tenant's school with the main branch first")
    void mainBranchFirst() {
        UUID schoolId = UUID.randomUUID();
        TenantContext.setTenantCode("tn-1");
        TenantRegistration reg = mock(TenantRegistration.class);
        when(reg.schoolId()).thenReturn(schoolId);
        when(registry.findByTenantCode("tn-1")).thenReturn(Optional.of(reg));
        School school = mock(School.class);
        when(school.getId()).thenReturn(schoolId);
        when(school.getSchoolName()).thenReturn("Vidya Public School");
        when(schools.findById(schoolId)).thenReturn(Optional.of(school));
        SchoolBranch north = branch("North", false);
        SchoolBranch main = branch("Main", true);
        when(branches.findBySchoolId(schoolId)).thenReturn(List.of(north, main));
        when(mapper.toBranchResponse(any())).thenAnswer(i -> {
            SchoolBranch b = i.getArgument(0);
            return new SchoolBranchResponse(null, schoolId, null, null, b.getBranchName(), b.isMainBranch(), null, null, null, null, null, null,
                    null, null, null, null, null);
        });

        SchoolCampusesResponse r = service.currentSchoolCampuses();
        assertThat(r.schoolName()).isEqualTo("Vidya Public School");
        assertThat(r.campuses()).extracting(SchoolBranchResponse::branchName).containsExactly("Main", "North");
    }

    @Test
    @DisplayName("an unregistered tenant is an error")
    void unknownTenant() {
        TenantContext.setTenantCode("missing");
        when(registry.findByTenantCode("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(service::currentSchoolCampuses).isInstanceOf(TenantNotFoundException.class);
    }

    private static SchoolBranch branch(String name, boolean main) {
        SchoolBranch b = mock(SchoolBranch.class);
        when(b.getBranchName()).thenReturn(name);
        when(b.isMainBranch()).thenReturn(main);
        return b;
    }
}
