package com.vidya.tenantdata.student.enrollment;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.attendance.repository.StudentAttendanceRepository;
import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitStudentRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.GuardianRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.Family;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeavingType;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ReadmitRequest;
import com.vidya.tenantdata.student.enrollment.entity.AdmissionSequence;
import com.vidya.tenantdata.student.enrollment.entity.StudentProfileDetail;
import com.vidya.tenantdata.student.enrollment.repository.AdmissionSequenceRepository;
import com.vidya.tenantdata.student.enrollment.repository.StudentProfileDetailRepository;
import com.vidya.tenantdata.student.enrollment.service.AdmissionNumberService;
import com.vidya.tenantdata.student.enrollment.service.ParentLinkService;
import com.vidya.tenantdata.student.enrollment.service.StudentEnrollmentSetupService;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentGuardianRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.tenantdata.user.account.TenantUserAccountService;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Student & parent enrollment")
class StudentEnrollmentSetupServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private StudentGuardianRepository guardianRepository;
    @Mock private StudentAcademicEnrollmentRepository enrollmentRepository;
    @Mock private StudentProfileDetailRepository detailRepository;
    @Mock private AdmissionSequenceRepository sequenceRepository;
    @Mock private AcademicYearRepository academicYearRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private StudentAttendanceRepository attendanceRepository;
    @Mock private StudentEnrollmentService enrollmentService;
    @Mock private StudentSubjectEnrollmentService subjectEnrollmentService;
    @Mock private TenantUserAccountService accounts;
    @Mock private SchoolContext context;

    private StudentEnrollmentSetupService setup;
    private ParentLinkService parents;

    private final UUID school = UUID.randomUUID();
    private AcademicYear year;
    private AcademicClass cls;
    private Section section;

    @BeforeEach
    void setUp() {
        AdmissionNumberService numbers = new AdmissionNumberService(sequenceRepository, academicYearRepository, studentRepository);
        parents = new ParentLinkService(guardianRepository, studentRepository, enrollmentRepository, tenantUserRepository, accounts, context);
        setup = new StudentEnrollmentSetupService(studentRepository, tenantUserRepository, guardianRepository, enrollmentRepository, detailRepository,
                academicYearRepository, classRepository, sectionRepository, attendanceRepository, enrollmentService, subjectEnrollmentService,
                numbers, parents, accounts, context);

        year = new AcademicYear("AY-2026", "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        year.setId(UUID.randomUUID());
        cls = new AcademicClass("C5", "Class V", "V", 5);
        cls.setId(UUID.randomUUID());
        section = new Section(year, cls, "A", "A", 40);
        section.setId(UUID.randomUUID());
        when(academicYearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));
        when(classRepository.findByIdAndNotDeleted(cls.getId())).thenReturn(Optional.of(cls));
        when(sectionRepository.findByIdAndNotDeleted(section.getId())).thenReturn(Optional.of(section));
        when(sequenceRepository.lockByYear(year.getId())).thenReturn(Optional.of(new AdmissionSequence(year.getId(), "2026/", 7, 4)));
        when(sequenceRepository.findByAcademicYearId(year.getId())).thenReturn(Optional.of(new AdmissionSequence(year.getId(), "2026/", 7, 4)));
        when(context.schoolId()).thenReturn(school);
        when(context.linkedMasterUserIds()).thenReturn(Set.of());

        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(i -> {
            TenantUser u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> {
            Student s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(guardianRepository.save(any(StudentGuardian.class))).thenAnswer(i -> {
            StudentGuardian g = i.getArgument(0);
            if (g.getId() == null) {
                g.setId(UUID.randomUUID());
            }
            return g;
        });
        when(enrollmentService.createEnrollmentEntity(any(), any())).thenAnswer(i -> {
            Student s = i.getArgument(0);
            StudentAcademicEnrollment e = new StudentAcademicEnrollment("ENR-1", s, year, cls, section, "12", StudentEnrollmentStatus.ACTIVE,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1), null);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(accounts.attach(any(), any(), anyString(), any(), any(), any(), any())).thenAnswer(i ->
                new LoginInfo(UUID.randomUUID(), "USR-L", i.getArgument(2), i.getArgument(3), UserStatus.ACTIVE, true, "Vtemp12345@", List.of()));
    }

    @Test
    @DisplayName("admit: number from the year's sequence, guardian linked to the existing parent of a sibling, one parent login")
    void admitLinksExistingParent() {
        TenantUser dad = parent("ramesh@gmail.com");
        when(tenantUserRepository.findActiveByTypeAndEmail(TenantUserType.PARENT, "ramesh@gmail.com")).thenReturn(List.of(dad));
        Student sibling = student("2024/0101", StudentStatus.ACTIVE);
        StudentGuardian sg = guardian(sibling, dad.getId());
        when(guardianRepository.findActiveByGuardianUserIdIn(any())).thenReturn(List.of(sg));

        AdmitResult r = setup.admit(request("Aarav", List.of(
                new GuardianRequest("Ramesh Kumar", GuardianRelationshipType.FATHER, "9876543210", "Ramesh@Gmail.com", null, null, null, null, null,
                        null, null, true, true, null, null, null, true))));

        assertThat(r.student().getAdmissionNumber()).isEqualTo("2026/0007");
        assertThat(r.guardians()).hasSize(1);
        assertThat(r.guardians().get(0).parentUserId()).isEqualTo(dad.getId());
        assertThat(r.guardians().get(0).parentLogin().role()).isEqualTo(PlatformRole.PARENT);
        assertThat(r.guardians().get(0).parentLogin().temporaryPassword()).isNotBlank();
        assertThat(r.siblings()).extracting("admissionNumber").containsExactly("2024/0101");
        assertThat(r.warnings()).anyMatch(w -> w.contains("linked to existing parent"));
        verify(accounts).attach(eq(dad), eq(school), eq("ramesh@gmail.com"), eq(PlatformRole.PARENT), any(), any(), any());
        verify(context).requireAuthority(eq("USER_CREATE"), anyString());
        // only the student person was created; the parent record was reused
        verify(tenantUserRepository, times(1)).save(any(TenantUser.class));
        verify(subjectEnrollmentService).autoEnrollCompulsorySubjects(any());
    }

    @Test
    @DisplayName("admit: a guardian with a phone but no match gets a new parent record; no login unless asked")
    void admitCreatesParentRecord() {
        AdmitResult r = setup.admit(request("Diya", List.of(
                new GuardianRequest("Sunita Devi", GuardianRelationshipType.MOTHER, "+91 98765-00000", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null))));

        ArgumentCaptor<TenantUser> saved = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository, times(2)).save(saved.capture());
        TenantUser parentRecord = saved.getAllValues().stream().filter(u -> u.getUserType() == TenantUserType.PARENT).findFirst().orElseThrow();
        assertThat(parentRecord.getPhone()).isEqualTo("919876500000");
        assertThat(parentRecord.getProfile().getFirstName()).isEqualTo("Sunita");
        assertThat(r.guardians().get(0).parentUserId()).isEqualTo(parentRecord.getId());
        assertThat(r.guardians().get(0).parentHasLogin()).isFalse();
        verify(accounts, never()).attach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("admit refusals: duplicate admission number, section of another class, login without email, implausible birth date")
    void admitRefusals() {
        when(studentRepository.existsByAdmissionNumber("2025/0001")).thenReturn(true);
        AdmitStudentRequest dup = new AdmitStudentRequest("Kabir", "Singh", "MALE", LocalDate.of(2016, 5, 5), null, null, null, null, null, null, null,
                "2025/0001", null, year.getId(), cls.getId(), section.getId(), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> setup.admit(dup)).isInstanceOf(BusinessException.class).hasMessageContaining("2025/0001 already exists");

        AcademicClass other = new AcademicClass("C6", "Class VI", "VI", 6);
        other.setId(UUID.randomUUID());
        when(classRepository.findByIdAndNotDeleted(other.getId())).thenReturn(Optional.of(other));
        AdmitStudentRequest wrongSection = new AdmitStudentRequest("Kabir", null, null, LocalDate.of(2016, 5, 5), null, null, null, null, null, null,
                null, null, null, year.getId(), other.getId(), section.getId(), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> setup.admit(wrongSection)).hasMessageContaining("does not belong to Class VI");

        AdmitStudentRequest loginNoMail = new AdmitStudentRequest("Kabir", null, null, LocalDate.of(2016, 5, 5), null, null, null, null, null, null,
                null, null, null, year.getId(), cls.getId(), null, null, null, null, null, null, null, true);
        assertThatThrownBy(() -> setup.admit(loginNoMail)).hasMessageContaining("student's own email");

        AdmitStudentRequest baby = new AdmitStudentRequest("Kabir", null, null, LocalDate.now(), null, null, null, null, null, null,
                null, null, null, year.getId(), cls.getId(), null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> setup.admit(baby)).hasMessageContaining("not plausible");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("bulk: dry run validates all rows and saves nothing; an invalid row blocks the whole batch")
    void bulkDryRun() {
        when(studentRepository.existsByAdmissionNumber("2026/0007")).thenReturn(false);
        AdmitStudentRequest ok = request("Aarav", null);
        AdmitStudentRequest sameMail = new AdmitStudentRequest("Isha", null, null, LocalDate.of(2016, 1, 1), "isha@x.in", null, null, null, null, null,
                null, "2026/0100", null, year.getId(), cls.getId(), section.getId(), null, null, null, null, null, null, null);
        AdmitStudentRequest sameMail2 = new AdmitStudentRequest("Isha Two", null, null, LocalDate.of(2016, 1, 1), "isha@x.in", null, null, null, null,
                null, null, "2026/0100", null, year.getId(), cls.getId(), section.getId(), null, null, null, null, null, null, null);

        BulkAdmitResult preview = setup.bulkAdmit(new BulkAdmitRequest(List.of(ok, sameMail, sameMail2), true));

        assertThat(preview.dryRun()).isTrue();
        assertThat(preview.valid()).isFalse();
        assertThat(preview.rows()).hasSize(3);
        assertThat(preview.rows().get(0).error()).isNull();
        assertThat(preview.rows().get(0).admissionNumber()).isEqualTo("2026/0007 (next)");
        assertThat(preview.rows().get(2).error()).contains("appears twice").contains("isha@x.in");

        BulkAdmitResult real = setup.bulkAdmit(new BulkAdmitRequest(List.of(ok, sameMail, sameMail2), false));
        assertThat(real.valid()).isFalse();
        verify(studentRepository, never()).save(any());

        BulkAdmitResult done = setup.bulkAdmit(new BulkAdmitRequest(List.of(ok, sameMail), false));
        assertThat(done.valid()).isTrue();
        assertThat(done.admitted()).isEqualTo(2);
        assertThat(done.rows().get(0).studentId()).isNotNull();
        verify(studentRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("leave: enrollments closed, TC recorded, student login off, parent login kept only while another child is active")
    void leave() {
        Student s = student("2024/0101", StudentStatus.ACTIVE);
        s.getTenantUser().setMasterUserId(UUID.randomUUID());
        TenantUser mum = parent("m@x.in");
        mum.setMasterUserId(UUID.randomUUID());
        TenantUser dad = parent("d@x.in");
        dad.setMasterUserId(UUID.randomUUID());
        StudentGuardian g1 = guardian(s, mum.getId());
        StudentGuardian g2 = guardian(s, dad.getId());
        when(guardianRepository.findAllByStudentId(s.getId())).thenReturn(List.of(g1, g2));
        when(tenantUserRepository.findAllActiveByIdIn(any())).thenReturn(List.of(mum, dad));
        Student sibling = student("2025/0050", StudentStatus.ACTIVE);
        when(guardianRepository.findActiveByGuardianUserIdIn(List.of(mum.getId()))).thenReturn(List.of(g1, guardian(sibling, mum.getId())));
        when(guardianRepository.findActiveByGuardianUserIdIn(List.of(dad.getId()))).thenReturn(List.of(g2));
        StudentAcademicEnrollment e = new StudentAcademicEnrollment("ENR-1", s, year, cls, section, "1", StudentEnrollmentStatus.ACTIVE,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1), null);
        when(enrollmentRepository.findAllByStudentId(s.getId())).thenReturn(List.of(e));
        when(detailRepository.findById(s.getId())).thenReturn(Optional.of(new StudentProfileDetail(s.getId())));

        LeaveResult r = setup.leave(s.getId(), new LeaveRequest(LeavingType.TRANSFERRED, LocalDate.of(2026, 9, 30), "Family moved", "TC/2026/18",
                null, null));

        assertThat(r.status()).isEqualTo(StudentStatus.TRANSFERRED);
        assertThat(r.enrollmentsClosed()).isEqualTo(1);
        assertThat(e.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(r.tcNumber()).isEqualTo("TC/2026/18");
        assertThat(r.studentLoginDisabled()).isTrue();
        assertThat(r.parentLoginsKept()).hasSize(1).first().asString().contains(mum.getUserCode());
        assertThat(r.parentLoginsDisabled()).hasSize(1).first().asString().contains(dad.getUserCode());
        verify(accounts).disable(s.getTenantUser());
        verify(accounts).disable(dad);
        verify(accounts, never()).disable(mum);
        verify(context).requireAuthority(eq("USER_UPDATE"), anyString());

        assertThatThrownBy(() -> setup.readmit(s.getId(), new ReadmitRequest(year.getId(), cls.getId(), null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("TRANSFERRED");
    }

    @Test
    @DisplayName("readmit: a withdrawn student comes back on the same record; logins re-enabled on request")
    void readmit() {
        Student s = student("2024/0101", StudentStatus.ACTIVE);
        s.updateStatus(StudentStatus.WITHDRAWN);
        s.getTenantUser().setMasterUserId(UUID.randomUUID());
        when(guardianRepository.findAllByStudentId(s.getId())).thenReturn(List.of());

        AdmitResult r = setup.readmit(s.getId(), new ReadmitRequest(year.getId(), cls.getId(), section.getId(), "7", null, null, null, true));

        assertThat(s.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(r.enrollment()).isNotNull();
        verify(accounts).setActive(s.getTenantUser().getId(), true);
        verify(subjectEnrollmentService).autoEnrollCompulsorySubjects(any());
    }

    @Test
    @DisplayName("admission numbers: default prefix is the start year; taken numbers are skipped")
    void admissionNumbers() {
        AdmissionSequence s = new AdmissionSequence(null, "GPS-", 12, 5);
        assertThat(s.peek()).isEqualTo("GPS-00012");
        assertThat(s.take()).isEqualTo("GPS-00012");
        assertThat(s.peek()).isEqualTo("GPS-00013");

        AdmissionNumberService numbers = new AdmissionNumberService(sequenceRepository, academicYearRepository, studentRepository);
        when(sequenceRepository.lockByYear(year.getId())).thenReturn(Optional.empty());
        when(sequenceRepository.lockSchoolWide()).thenReturn(Optional.empty());
        when(sequenceRepository.saveAndFlush(any(AdmissionSequence.class))).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.existsByAdmissionNumber("2026/0001")).thenReturn(true);
        assertThat(numbers.next(year)).isEqualTo("2026/0002");
    }

    @Test
    @DisplayName("families: two parents sharing three children are one household; an unrelated parent is another")
    void families() {
        TenantUser mum = parent("mum@x.in");
        TenantUser dad = parent("dad@x.in");
        dad.setMasterUserId(UUID.randomUUID());
        TenantUser other = parent("other@x.in");
        Student a = student("2024/0001", StudentStatus.ACTIVE);
        Student b = student("2025/0002", StudentStatus.ACTIVE);
        Student c = student("2026/0003", StudentStatus.ACTIVE);
        Student z = student("2026/0009", StudentStatus.ACTIVE);
        List<StudentGuardian> links = List.of(guardian(a, mum.getId()), guardian(a, dad.getId()), guardian(b, mum.getId()),
                guardian(c, dad.getId()), guardian(z, other.getId()));
        when(guardianRepository.findAllActiveWithStudent()).thenReturn(links);
        when(tenantUserRepository.findAllActiveByType(TenantUserType.PARENT)).thenReturn(List.of(mum, dad, other));

        List<Family> fams = parents.families(null);

        assertThat(fams).hasSize(2);
        Family big = fams.get(0);
        assertThat(big.parents()).extracting("email").containsExactlyInAnyOrder("mum@x.in", "dad@x.in");
        assertThat(big.children()).extracting("admissionNumber").containsExactlyInAnyOrder("2024/0001", "2025/0002", "2026/0003");
        assertThat(big.logins()).isEqualTo(1);
        assertThat(fams.get(1).children()).extracting("admissionNumber").containsExactly("2026/0009");
        assertThat(parents.families("0009")).hasSize(1);
    }

    @Test
    @DisplayName("merge: guardian rows move to the kept record, the duplicate's login moves when the kept one has none, duplicate is deleted")
    void merge() {
        TenantUser keep = parent("keep@x.in");
        keep.setPhone(null);
        TenantUser dup = parent("dup@x.in");
        UUID dupLogin = UUID.randomUUID();
        dup.setMasterUserId(dupLogin);
        Student a = student("2024/0001", StudentStatus.ACTIVE);
        Student b = student("2025/0002", StudentStatus.ACTIVE);
        StudentGuardian ka = guardian(a, keep.getId());
        StudentGuardian da = guardian(a, dup.getId());
        StudentGuardian db = guardian(b, dup.getId());
        when(guardianRepository.findByGuardianUserId(keep.getId())).thenReturn(List.of(ka));
        when(guardianRepository.findByGuardianUserId(dup.getId())).thenReturn(List.of(da, db));
        when(tenantUserRepository.saveAndFlush(any(TenantUser.class))).thenAnswer(i -> i.getArgument(0));
        when(tenantUserRepository.findAllActiveByType(TenantUserType.PARENT)).thenReturn(List.of(keep));
        when(guardianRepository.findAllActiveWithStudent()).thenReturn(List.of(ka, db));

        MergeParentsResult r = parents.merge(keep.getId(), new MergeParentsRequest(dup.getId()));

        assertThat(r.guardianRowsMoved()).isEqualTo(1);
        assertThat(db.getGuardianUserId()).isEqualTo(keep.getId());
        assertThat(da.getGuardianUserId()).isNull();
        assertThat(r.warnings()).anyMatch(w -> w.contains("already had a guardian row"));
        assertThat(r.loginMoved()).isTrue();
        assertThat(keep.getMasterUserId()).isEqualTo(dupLogin);
        assertThat(keep.getPhone()).isEqualTo("9000000000");
        assertThat(keep.getEmail()).isEqualTo("keep@x.in");
        assertThat(dup.isDeleted()).isTrue();
        assertThat(dup.getEmail()).isNull();
        assertThat(r.parent().children()).extracting("admissionNumber").containsExactlyInAnyOrder("2024/0001", "2025/0002");
        verify(context).requireAuthority(eq("USER_UPDATE"), anyString());
        verify(accounts, never()).disable(any());

        assertThatThrownBy(() -> parents.merge(keep.getId(), new MergeParentsRequest(keep.getId()))).hasMessageContaining("different");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private AdmitStudentRequest request(String first, List<GuardianRequest> guardians) {
        return new AdmitStudentRequest(first, "Sharma", "MALE", LocalDate.of(2016, 8, 15), null, null, "12 MG Road", null, "Pune", "MH", "411001",
                null, LocalDate.of(2026, 4, 1), year.getId(), cls.getId(), section.getId(), null, null, null, null, null, guardians, null);
    }

    private TenantUser parent(String email) {
        TenantUser u = new TenantUser("USR-P" + email.charAt(0), email, "9000000000", TenantUserType.PARENT);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, "Parent", email.charAt(0) + ""));
        when(tenantUserRepository.findByIdAndNotDeleted(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private Student student(String admissionNumber, StudentStatus status) {
        TenantUser u = new TenantUser("USR-S" + admissionNumber, "student." + admissionNumber.replace('/', '.') + "@school.vidya.internal", null,
                TenantUserType.STUDENT);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, "Kid", admissionNumber));
        Student s = new Student("STU-" + admissionNumber, u, admissionNumber, LocalDate.of(2024, 4, 1));
        s.setId(UUID.randomUUID());
        if (status != StudentStatus.ACTIVE) {
            s.updateStatus(status);
        }
        when(studentRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private StudentGuardian guardian(Student s, UUID parentUserId) {
        StudentGuardian g = new StudentGuardian("GRD-" + UUID.randomUUID().toString().substring(0, 4), s, "Parent", GuardianRelationshipType.FATHER,
                "9000000000", null, null, null, null, null, null, null, null, true, true);
        g.setId(UUID.randomUUID());
        g.setGuardianUserId(parentUserId);
        return g;
    }
}
