package com.vidya.tenantdata.security;

import com.vidya.security.access.SeedRoleMatrix;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataAccessService — data-level (row) access: ALL vs OWN records")
class DataAccessServiceTest {

    @Mock private OwnRecordsResolver resolver;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private PlatformUserRepository userRepository;

    private DataAccessService service;
    private final UUID schoolId = UUID.randomUUID();
    private final UUID myChild = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID mySection = UUID.randomUUID();
    private final UUID otherSection = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DataAccessService(resolver, effectiveAccessService, userRepository);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("school admin (STUDENT_SCOPE_ALL) reaches any student without resolving own records")
    void admin_all() {
        Authentication admin = auth(PlatformRole.SCHOOL_ADMIN);

        assertThat(service.canAccessStudent(admin, otherStudent, "STUDENT")).isTrue();
        assertThat(service.hasFullScope(admin, "STUDENT")).isTrue();
        assertThat(service.canAccessSection(admin, otherSection, "ATTENDANCE")).isTrue();
        verify(resolver, never()).resolve(any());
    }

    @Test
    @DisplayName("parent: only linked children; never a whole section; no full student list")
    void parent_ownChildrenOnly() {
        Authentication parent = auth(PlatformRole.PARENT);
        when(resolver.resolve(userId(parent))).thenReturn(OwnRecords.builder().childStudentIds(Set.of(myChild)).build());

        assertThat(service.canAccessStudent(parent, myChild, "STUDENT")).isTrue();
        assertThat(service.canAccessStudent(parent, otherStudent, "STUDENT")).isFalse();
        assertThat(service.canAccessStudent(parent, myChild, "ATTENDANCE")).isTrue();
        assertThat(service.canAccessSection(parent, mySection, "ATTENDANCE")).isFalse();
        assertThat(service.hasFullScope(parent, "STUDENT")).isFalse();
        assertThat(service.ownStudentIds(parent)).containsExactly(myChild);
    }

    @Test
    @DisplayName("teacher: students and sections they teach, including whole-class subject assignments")
    void teacher_taughtSections() {
        Authentication teacher = auth(PlatformRole.TEACHER);
        UUID wholeClass = UUID.randomUUID();
        UUID sectionOfWholeClass = UUID.randomUUID();
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().staffIds(Set.of(UUID.randomUUID())).sectionIds(Set.of(mySection)).wholeClassIds(Set.of(wholeClass)).teachingClassIds(Set.of(wholeClass)).taughtStudentIds(Set.of(myChild)).build());
        when(resolver.classOfSection(sectionOfWholeClass)).thenReturn(Optional.of(wholeClass));
        when(resolver.classOfSection(otherSection)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(service.canAccessSection(teacher, mySection, "ATTENDANCE")).isTrue();
        assertThat(service.canAccessSection(teacher, sectionOfWholeClass, "ATTENDANCE")).isTrue();
        assertThat(service.canAccessSection(teacher, otherSection, "ATTENDANCE")).isFalse();
        assertThat(service.canAccessStudent(teacher, myChild, "MARKS")).isTrue();
        assertThat(service.canAccessStudent(teacher, otherStudent, "MARKS")).isFalse();
    }

    @Test
    @DisplayName("bulk operations fail if any one student is outside the user's records")
    void bulk_allOrNothing() {
        Authentication teacher = auth(PlatformRole.TEACHER);
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().sectionIds(Set.of(mySection)).taughtStudentIds(Set.of(myChild)).build());

        assertThat(service.canAccessStudents(teacher, List.of(myChild), "ATTENDANCE")).isTrue();
        assertThat(service.canAccessStudents(teacher, List.of(myChild, otherStudent), "ATTENDANCE")).isFalse();
    }

    @Test
    @DisplayName("attendance/mark records are checked through the student they belong to")
    void recordsCheckedViaStudent() {
        Authentication student = auth(PlatformRole.STUDENT);
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        when(resolver.resolve(userId(student))).thenReturn(OwnRecords.builder().selfStudentIds(Set.of(myChild)).build());
        when(resolver.studentIdOfAttendance(mine)).thenReturn(Optional.of(myChild));
        when(resolver.studentIdOfAttendance(theirs)).thenReturn(Optional.of(otherStudent));
        when(resolver.studentIdOfMarkEntry(theirs)).thenReturn(Optional.empty());

        assertThat(service.canAccessAttendanceRecord(student, mine)).isTrue();
        assertThat(service.canAccessAttendanceRecord(student, theirs)).isFalse();
        assertThat(service.canAccessMarkEntry(student, theirs)).as("unknown record").isFalse();
    }

    @Test
    @DisplayName("exam paper marks: OWN teacher only for a subject they teach in that class")
    void examConfig() {
        Authentication teacher = auth(PlatformRole.SUBJECT_TEACHER);
        UUID subject = UUID.randomUUID();
        UUID clazz = UUID.randomUUID();
        UUID myPaper = UUID.randomUUID();
        UUID otherPaper = UUID.randomUUID();
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().subjectClassPairs(Set.of(subject + ":" + clazz)).build());
        when(resolver.subjectClassOfExamConfig(myPaper)).thenReturn(Optional.of(subject + ":" + clazz));
        when(resolver.subjectClassOfExamConfig(otherPaper)).thenReturn(Optional.of(UUID.randomUUID() + ":" + clazz));

        assertThat(service.canAccessExamSubjectConfig(teacher, myPaper)).isTrue();
        assertThat(service.canAccessExamSubjectConfig(teacher, otherPaper)).isFalse();
    }

    @Test
    @DisplayName("own records are resolved once per request")
    void cachedPerRequest() {
        Authentication parent = auth(PlatformRole.PARENT);
        when(resolver.resolve(userId(parent))).thenReturn(OwnRecords.builder().childStudentIds(Set.of(myChild)).build());

        service.canAccessStudent(parent, myChild, "STUDENT");
        service.canAccessStudent(parent, otherStudent, "ATTENDANCE");
        service.ownStudentIds(parent);

        verify(resolver, times(1)).resolve(userId(parent));
    }

    @Test
    @DisplayName("platform accounts are ALL (school isolation is enforced by tenant resolution)")
    void platform_all() {
        assertThat(service.canAccessStudent(auth(PlatformRole.PLATFORM_ADMIN), otherStudent, "MARKS")).isTrue();
    }

    @Test
    @DisplayName("no authentication → denied")
    void anonymous_denied() {
        assertThat(service.canAccessStudent(null, myChild, "STUDENT")).isFalse();
        assertThat(service.hasFullScope(null, "STUDENT")).isFalse();
    }

    @Test
    @DisplayName("timetable: teacher sees own grid and taught sections; parent sees child's section only")
    void timetable() {
        Authentication teacher = auth(PlatformRole.TEACHER);
        UUID myStaff = UUID.randomUUID();
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().staffIds(Set.of(myStaff))
                .sectionIds(Set.of(mySection)).build());

        assertThat(service.canViewTeacherTimetable(teacher, myStaff)).isTrue();
        assertThat(service.canViewTeacherTimetable(teacher, UUID.randomUUID())).isFalse();
        assertThat(service.canViewSectionTimetable(teacher, mySection)).isTrue();
        assertThat(service.canViewSectionTimetable(teacher, otherSection)).isFalse();
        assertThat(service.canQueryTimetableEntries(teacher, null, null, null)).as("unfiltered search").isFalse();
        assertThat(service.canQueryTimetableEntries(teacher, null, myStaff, null)).isTrue();
        assertThat(service.hasFullScope(teacher, "TIMETABLE")).as("cannot edit the timetable").isFalse();

        Authentication parent = auth(PlatformRole.PARENT);
        when(resolver.resolve(userId(parent))).thenReturn(OwnRecords.builder().childStudentIds(Set.of(myChild))
                .studentSectionIds(Set.of(mySection)).build());
        assertThat(service.canViewSectionTimetable(parent, mySection)).isTrue();
        assertThat(service.canViewSectionTimetable(parent, otherSection)).isFalse();
        assertThat(service.canAccessSection(parent, mySection, "ATTENDANCE")).as("roster still hidden").isFalse();
    }

    @Test
    @DisplayName("substitutions and timetable entries: visible to the teachers involved")
    void substitutionsAndEntries() {
        Authentication teacher = auth(PlatformRole.TEACHER);
        UUID myStaff = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().staffIds(Set.of(myStaff)).build());
        when(resolver.substitution(mine)).thenReturn(Optional.of(new UUID[]{UUID.randomUUID(), myStaff, otherSection}));
        when(resolver.substitution(theirs)).thenReturn(Optional.of(new UUID[]{UUID.randomUUID(), UUID.randomUUID(), otherSection}));
        when(resolver.timetableEntry(mine)).thenReturn(Optional.of(new UUID[]{myStaff, otherSection, null}));

        assertThat(service.canAccessSubstitution(teacher, mine)).isTrue();
        assertThat(service.canAccessSubstitution(teacher, theirs)).isFalse();
        assertThat(service.canAccessTimetableEntry(teacher, mine)).isTrue();
    }

    @Test
    @DisplayName("staff and assignments: OWN users see only their own; admins see all")
    void staff() {
        Authentication teacher = auth(PlatformRole.TEACHER);
        UUID myStaff = UUID.randomUUID();
        UUID myAssignment = UUID.randomUUID();
        UUID otherAssignment = UUID.randomUUID();
        when(resolver.resolve(userId(teacher))).thenReturn(OwnRecords.builder().staffIds(Set.of(myStaff)).build());
        when(resolver.teacherSubjectAssignment(myAssignment, null)).thenReturn(Optional.of(new UUID[]{myStaff, null, UUID.randomUUID()}));
        when(resolver.teacherSubjectAssignment(otherAssignment, null)).thenReturn(Optional.of(new UUID[]{UUID.randomUUID(), null, UUID.randomUUID()}));

        assertThat(service.canAccessStaff(teacher, myStaff, "STAFF")).isTrue();
        assertThat(service.canAccessStaff(teacher, UUID.randomUUID(), "STAFF")).isFalse();
        assertThat(service.canAccessTeacherSubjectAssignment(teacher, myAssignment, null)).isTrue();
        assertThat(service.canAccessTeacherSubjectAssignment(teacher, otherAssignment, null)).isFalse();
        assertThat(service.canQueryAssignments(teacher, null, null, null)).isFalse();
        assertThat(service.hasFullScope(auth(PlatformRole.SCHOOL_ADMIN), "STAFF")).isTrue();
        assertThat(service.hasFullScope(auth(PlatformRole.COORDINATOR), "STAFF")).isTrue();
    }

    @Test
    @DisplayName("enrollments follow the student; user accounts are own-only below admin")
    void enrollmentsAndUsers() {
        Authentication parent = auth(PlatformRole.PARENT);
        UUID myTenantUser = UUID.randomUUID();
        UUID childEnrollment = UUID.randomUUID();
        UUID otherEnrollment = UUID.randomUUID();
        when(resolver.resolve(userId(parent))).thenReturn(OwnRecords.builder().tenantUserIds(Set.of(myTenantUser))
                .childStudentIds(Set.of(myChild)).build());
        when(resolver.studentIdOfEnrollment(childEnrollment)).thenReturn(Optional.of(myChild));
        when(resolver.studentIdOfEnrollment(otherEnrollment)).thenReturn(Optional.of(otherStudent));

        assertThat(service.canAccessEnrollment(parent, childEnrollment, "STUDENT")).isTrue();
        assertThat(service.canAccessEnrollment(parent, otherEnrollment, "STUDENT")).isFalse();
        assertThat(service.canQueryStudents(parent, myChild, null, "STUDENT")).isTrue();
        assertThat(service.canQueryStudents(parent, null, null, "STUDENT")).isFalse();
        assertThat(service.canAccessTenantUser(parent, myTenantUser, "USER")).isTrue();
        assertThat(service.canAccessTenantUser(parent, UUID.randomUUID(), "USER")).isFalse();
        assertThat(service.hasFullScope(auth(PlatformRole.SCHOOL_ADMIN), "USER")).isTrue();
    }

    private Authentication auth(PlatformRole role) {
        PlatformUser user = new PlatformUser("USR-" + role, role.name().toLowerCase() + "@x.io", "h", "F", "L", null, role,
                role.getScope().isSchoolBound() ? schoolId : null);
        user.setId(UUID.randomUUID());
        EffectiveAccess access = SeedRoleMatrix.accessFor(user);
        PlatformUserDetails principal = new PlatformUserDetails(user, null, access, UUID.randomUUID());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static UUID userId(Authentication a) {
        return ((PlatformUserDetails) a.getPrincipal()).getUserId();
    }
}
