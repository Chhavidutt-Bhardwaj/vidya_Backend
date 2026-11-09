package com.vidya.tenantdata.academic.setup;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.service.AcademicYearService;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.PromotionAction;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.StudentOverride;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult.StudentLine;
import com.vidya.tenantdata.academic.setup.service.PromotionService;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PromotionService — year-end promotion")
class PromotionServiceTest {

    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private StudentAcademicEnrollmentRepository enrollmentRepository;
    @Mock private StudentSubjectEnrollmentService subjectEnrollmentService;
    @Mock private AcademicYearService academicYearService;

    private PromotionService service;
    private AcademicYear from;
    private AcademicYear to;
    private AcademicClass class1;
    private AcademicClass class2;
    private AcademicClass class3;
    private Section to2A;
    private final List<StudentAcademicEnrollment> enrollments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PromotionService(yearRepository, classRepository, sectionRepository, enrollmentRepository,
                subjectEnrollmentService, academicYearService);
        from = year("AY26", LocalDate.of(2026, 4, 1), AcademicYearStatus.ACTIVE);
        to = year("AY27", LocalDate.of(2027, 4, 1), AcademicYearStatus.DRAFT);
        class1 = clazz("C1", 1);
        class2 = clazz("C2", 2);
        class3 = clazz("C3", 3);
        when(classRepository.findByDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(List.of(class1, class2, class3));
        to2A = section(to, class2, "A", null);
        when(sectionRepository.findActiveByAcademicYearId(to.getId())).thenReturn(List.of(to2A));
        when(enrollmentRepository.findActiveByAcademicYearId(from.getId())).thenReturn(enrollments);
        when(enrollmentRepository.save(any(StudentAcademicEnrollment.class))).thenAnswer(inv -> {
            StudentAcademicEnrollment e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
    }

    @Test
    @DisplayName("default: next class by sort order, same section code; last class graduates")
    void defaults() {
        Section from1A = section(from, class1, "A", null);
        StudentAcademicEnrollment a = enroll(class1, from1A, "7");
        StudentAcademicEnrollment b = enroll(class3, null, null);

        PromotionResult r = service.promote(from.getId(), request(null));

        StudentLine la = line(r, a);
        assertThat(la.action()).isEqualTo(PromotionAction.PROMOTE);
        assertThat(la.toClassId()).isEqualTo(class2.getId());
        assertThat(la.toSectionId()).isEqualTo(to2A.getId());
        assertThat(a.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.PROMOTED);

        assertThat(line(r, b).action()).isEqualTo(PromotionAction.GRADUATE);
        assertThat(b.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.COMPLETED);
        assertThat(b.getStudent().getStatus()).isEqualTo(StudentStatus.GRADUATED);

        ArgumentCaptor<StudentAcademicEnrollment> saved = ArgumentCaptor.forClass(StudentAcademicEnrollment.class);
        verify(enrollmentRepository, atLeastOnce()).save(saved.capture());
        StudentAcademicEnrollment created = saved.getAllValues().stream()
                .filter(e -> e.getAcademicYear() == to).findFirst().orElseThrow();
        assertThat(created.getAcademicClass()).isEqualTo(class2);
        assertThat(created.getEnrollmentDate()).isEqualTo(to.getStartDate());
        assertThat(created.getRollNumber()).as("roll numbers not kept by default").isNull();
        verify(subjectEnrollmentService).autoEnrollCompulsorySubjects(created);
    }

    @Test
    @DisplayName("preview changes nothing and matches what execute would do")
    void previewIsReadOnly() {
        StudentAcademicEnrollment a = enroll(class1, section(from, class1, "A", null), null);

        PromotionResult r = service.preview(from.getId(), request(null));

        assertThat(r.dryRun()).isTrue();
        assertThat(line(r, a).action()).isEqualTo(PromotionAction.PROMOTE);
        assertThat(a.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("overrides: detain repeats the class, leave withdraws, skip leaves it alone")
    void overrides() {
        StudentAcademicEnrollment detained = enroll(class1, null, null);
        StudentAcademicEnrollment leaving = enroll(class1, null, null);
        StudentAcademicEnrollment skipped = enroll(class1, null, null);

        PromotionResult r = service.promote(from.getId(), request(List.of(
                new StudentOverride(detained.getStudent().getId(), PromotionAction.DETAIN, null, null, null),
                new StudentOverride(leaving.getStudent().getId(), PromotionAction.LEAVE, null, null, "Moved city"),
                new StudentOverride(skipped.getStudent().getId(), PromotionAction.SKIP, null, null, null))));

        assertThat(line(r, detained).toClassId()).isEqualTo(class1.getId());
        assertThat(detained.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.COMPLETED);
        assertThat(leaving.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(leaving.getStudent().getStatus()).isEqualTo(StudentStatus.WITHDRAWN);
        assertThat(skipped.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("students already enrolled in the target year are skipped (safe to re-run)")
    void rerunSafe() {
        StudentAcademicEnrollment a = enroll(class1, null, null);
        when(enrollmentRepository.findStudentIdsWithActiveEnrollment(to.getId())).thenReturn(List.of(a.getStudent().getId()));

        PromotionResult r = service.promote(from.getId(), request(null));

        assertThat(line(r, a).action()).isEqualTo(PromotionAction.SKIP);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("full section: student is enrolled without a section and a warning is returned")
    void fullSection() {
        to2A.setCapacity(1);
        when(enrollmentRepository.countActiveBySection(to.getId())).thenReturn(List.<Object[]>of(new Object[]{to2A.getId(), 1L}));
        StudentAcademicEnrollment a = enroll(class1, section(from, class1, "A", null), null);

        PromotionResult r = service.preview(from.getId(), request(null));

        assertThat(line(r, a).toSectionId()).isNull();
        assertThat(line(r, a).note()).contains("full");
        assertThat(r.withoutSection()).isEqualTo(1);
    }

    @Test
    @DisplayName("students of an inactive class are never guessed to graduate")
    void inactiveClassSkipped() {
        AcademicClass old = clazz("OLD", 9);
        StudentAcademicEnrollment a = enroll(old, null, null);

        PromotionResult r = service.preview(from.getId(), request(null));

        assertThat(line(r, a).action()).isEqualTo(PromotionAction.SKIP);
    }

    @Test
    @DisplayName("target year must be later than the source year")
    void wrongDirection() {
        assertThatThrownBy(() -> service.preview(to.getId(), new PromotionRequest(from.getId(), null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("can make the target current and close the source year")
    void finishYear() {
        service.promote(from.getId(), new PromotionRequest(to.getId(), null, null, null, true, true, null, null));

        verify(academicYearService).setCurrentAcademicYear(to.getId());
        verify(academicYearService).closeAcademicYear(from.getId());
    }

    // -----------------------------------------------------------------------------------------

    private PromotionRequest request(List<StudentOverride> overrides) {
        return new PromotionRequest(to.getId(), null, null, null, null, null, null, overrides);
    }

    private static StudentLine line(PromotionResult r, StudentAcademicEnrollment e) {
        return r.students().stream().filter(l -> l.enrollmentId().equals(e.getId())).findFirst().orElseThrow();
    }

    private AcademicYear year(String code, LocalDate start, AcademicYearStatus status) {
        AcademicYear y = new AcademicYear(code, code, start, start.plusYears(1).minusDays(1), false, status);
        y.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(y.getId())).thenReturn(Optional.of(y));
        return y;
    }

    private static AcademicClass clazz(String code, int order) {
        AcademicClass c = new AcademicClass(code, code, null, order);
        c.setId(UUID.randomUUID());
        return c;
    }

    private static Section section(AcademicYear y, AcademicClass c, String code, Integer capacity) {
        Section s = new Section(y, c, code, code, capacity);
        s.setId(UUID.randomUUID());
        return s;
    }

    private StudentAcademicEnrollment enroll(AcademicClass c, Section s, String roll) {
        Student student = new Student("STU-" + UUID.randomUUID().toString().substring(0, 6), null, null, null);
        student.setId(UUID.randomUUID());
        StudentAcademicEnrollment e = new StudentAcademicEnrollment("ENR-" + enrollments.size(), student, from, c, s, roll,
                StudentEnrollmentStatus.ACTIVE, null, from.getStartDate(), null);
        e.setId(UUID.randomUUID());
        enrollments.add(e);
        return e;
    }
}
