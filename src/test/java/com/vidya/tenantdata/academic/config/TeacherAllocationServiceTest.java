package com.vidya.tenantdata.academic.config;

import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.AllocationResult;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassTeacherRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassTeachersRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectTeacherRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectTeachersRequest;
import com.vidya.tenantdata.academic.config.service.TeacherAllocationService;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.entity.StaffSubject;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.repository.StaffSubjectRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Teacher allocation — class teachers and subject teachers in bulk")
class TeacherAllocationServiceTest {

    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private StaffSubjectRepository staffSubjectRepository;
    @Mock private ClassTeacherAssignmentRepository classTeacherRepository;
    @Mock private TeacherSubjectAssignmentRepository subjectTeacherRepository;
    @Mock private EntityManager em;
    @Mock private Query query;

    private TeacherAllocationService service;
    private AcademicYear year;
    private AcademicClass classV;
    private Section sectionA;
    private Section sectionB;
    private Subject maths;
    private Staff asha;
    private Staff ravi;
    private long classSubjectCount = 1;

    @BeforeEach
    void setUp() {
        service = new TeacherAllocationService(yearRepository, classRepository, sectionRepository, subjectRepository, staffRepository,
                staffSubjectRepository, classTeacherRepository, subjectTeacherRepository);
        ReflectionTestUtils.setField(service, "em", em);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenAnswer(i -> classSubjectCount);
        when(query.getResultList()).thenReturn(List.of());

        year = new AcademicYear("AY2026-27", "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        year.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));
        classV = new AcademicClass("V", "Class V", "Class V", 5);
        classV.setId(UUID.randomUUID());
        classV.setGradeLevel(5);
        when(classRepository.findByIdAndNotDeleted(classV.getId())).thenReturn(Optional.of(classV));
        sectionA = section("A");
        sectionB = section("B");
        maths = new Subject("SUB-MATH", "Mathematics", "Maths", null, SubjectType.CORE, 1);
        maths.setId(UUID.randomUUID());
        when(subjectRepository.findByIdAndNotDeleted(maths.getId())).thenReturn(Optional.of(maths));
        asha = teacher("Asha", "Rao");
        ravi = teacher("Ravi", "Menon");
        when(classTeacherRepository.findActiveBySectionAndAcademicYear(any(), any())).thenReturn(Optional.empty());
        when(classTeacherRepository.findActiveByStaffId(any(), any())).thenReturn(List.of());
        when(subjectTeacherRepository.findActiveByClassAndSection(any(), any(), any())).thenReturn(List.of());
        when(staffSubjectRepository.findByStaffId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("class teachers: assign, change (old one completed, history kept), leave unchanged")
    void classTeachers() {
        ClassTeacherAssignment current = new ClassTeacherAssignment("CTA-1", ravi, year, classV, sectionB, LocalDate.of(2026, 4, 1), null);
        when(classTeacherRepository.findActiveBySectionAndAcademicYear(year.getId(), sectionB.getId())).thenReturn(Optional.of(current));

        AllocationResult r = service.setClassTeachers(new ClassTeachersRequest(year.getId(),
                List.of(new ClassTeacherRow(sectionA.getId(), asha.getId()), new ClassTeacherRow(sectionB.getId(), asha.getId())),
                LocalDate.of(2026, 9, 1), false));

        assertThat(r.valid()).isTrue();
        assertThat(r.assigned()).isEqualTo(1);
        assertThat(r.changed()).isEqualTo(1);
        assertThat(r.rows()).extracting("action").containsExactly("ASSIGNED", "CHANGED");
        assertThat(r.rows().get(1).warnings()).anyMatch(w -> w.contains("more than one section"));
        assertThat(current.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.COMPLETED);
        assertThat(current.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        ArgumentCaptor<ClassTeacherAssignment> created = ArgumentCaptor.forClass(ClassTeacherAssignment.class);
        verify(classTeacherRepository, org.mockito.Mockito.times(2)).save(created.capture());
        assertThat(created.getAllValues()).allMatch(a -> a.getStaff() == asha && a.getStartDate().equals(LocalDate.of(2026, 9, 1)));
    }

    @Test
    @DisplayName("class teachers: one invalid row saves nothing; dry run saves nothing; null staff removes")
    void classTeachersAllOrNothing() {
        ravi.setEmploymentStatus(EmploymentStatus.RESIGNED);
        AllocationResult bad = service.setClassTeachers(new ClassTeachersRequest(year.getId(),
                List.of(new ClassTeacherRow(sectionA.getId(), asha.getId()), new ClassTeacherRow(sectionB.getId(), ravi.getId())), null, false));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.rows().get(1).error()).contains("RESIGNED");
        verify(classTeacherRepository, never()).save(any());

        AllocationResult preview = service.setClassTeachers(new ClassTeachersRequest(year.getId(),
                List.of(new ClassTeacherRow(sectionA.getId(), asha.getId())), null, true));
        assertThat(preview.dryRun()).isTrue();
        assertThat(preview.assigned()).isEqualTo(1);
        verify(classTeacherRepository, never()).save(any());

        ClassTeacherAssignment current = new ClassTeacherAssignment("CTA-1", asha, year, classV, sectionA, LocalDate.of(2026, 4, 1), null);
        when(classTeacherRepository.findActiveBySectionAndAcademicYear(year.getId(), sectionA.getId())).thenReturn(Optional.of(current));
        AllocationResult removed = service.setClassTeachers(new ClassTeachersRequest(year.getId(),
                List.of(new ClassTeacherRow(sectionA.getId(), null)), null, false));
        assertThat(removed.removed()).isEqualTo(1);
        assertThat(current.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("subject teachers: new teacher replaces the primary; the old assignment is completed")
    void subjectTeachers() {
        TeacherSubjectAssignment old = new TeacherSubjectAssignment("TSA-1", ravi, maths, year, classV, sectionA, null, true, LocalDate.of(2026, 4, 1), null);
        when(subjectTeacherRepository.findActiveByClassAndSection(year.getId(), classV.getId(), sectionA.getId())).thenReturn(List.of(old));
        when(staffSubjectRepository.findByStaffId(asha.getId())).thenReturn(List.of(new StaffSubject(asha.getId(), UUID.randomUUID(), null, null, true)));

        AllocationResult r = service.setSubjectTeachers(new SubjectTeachersRequest(year.getId(), List.of(
                new SubjectTeacherRow(classV.getId(), sectionA.getId(), maths.getId(), asha.getId()),
                new SubjectTeacherRow(classV.getId(), sectionB.getId(), maths.getId(), asha.getId())), LocalDate.of(2026, 9, 1), false));

        assertThat(r.valid()).isTrue();
        assertThat(r.rows()).extracting("action").containsExactly("CHANGED", "ASSIGNED");
        assertThat(r.rows().get(0).warnings()).anyMatch(w -> w.contains("not among"));
        assertThat(old.getStatus()).isEqualTo(TeacherAssignmentStatus.COMPLETED);
        assertThat(old.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        List<TeacherSubjectAssignment> saved = new ArrayList<>();
        ArgumentCaptor<TeacherSubjectAssignment> c = ArgumentCaptor.forClass(TeacherSubjectAssignment.class);
        verify(subjectTeacherRepository, org.mockito.Mockito.times(2)).save(c.capture());
        saved.addAll(c.getAllValues());
        assertThat(saved).allMatch(a -> a.getTeacher() == asha && a.isPrimary() && a.getSubject() == maths);
    }

    @Test
    @DisplayName("subject teachers: a subject the class does not take is refused and nothing is saved")
    void subjectTeachersNeedClassSubject() {
        classSubjectCount = 0;
        AllocationResult r = service.setSubjectTeachers(new SubjectTeachersRequest(year.getId(), List.of(
                new SubjectTeacherRow(classV.getId(), null, maths.getId(), asha.getId()),
                new SubjectTeacherRow(classV.getId(), null, maths.getId(), ravi.getId())), null, false));

        assertThat(r.valid()).isFalse();
        assertThat(r.rows().get(0).error()).contains("not a subject of Class V");
        assertThat(r.rows().get(1).error()).isEqualTo("listed twice");
        verify(subjectTeacherRepository, never()).save(any());
    }

    private Section section(String code) {
        Section s = new Section(year, classV, code, code, 40);
        s.setId(UUID.randomUUID());
        when(sectionRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private Staff teacher(String first, String last) {
        TenantUser u = new TenantUser("USR-" + first, first.toLowerCase() + "@school.in", null, TenantUserType.TEACHER);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, first, last));
        Staff s = new Staff("STF-" + first, u, null, LocalDate.of(2020, 6, 1), StaffType.TEACHER, null, null);
        s.setId(UUID.randomUUID());
        s.setEmploymentStatus(EmploymentStatus.ACTIVE);
        when(staffRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }
}
