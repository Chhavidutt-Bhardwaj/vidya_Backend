package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksRequest;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksResponse;
import com.vidya.tenantdata.examination.dto.StudentMarkResponse;
import com.vidya.tenantdata.examination.dto.SubmitStudentMarkRequest;
import com.vidya.tenantdata.examination.entity.ExamSchedule;
import com.vidya.tenantdata.examination.entity.ExamSubjectConfig;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.entity.StudentMarkEntry;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
import com.vidya.tenantdata.examination.mapper.StudentMarkMapper;
import com.vidya.tenantdata.examination.repository.ExamSubjectConfigRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleTierRepository;
import com.vidya.tenantdata.examination.repository.StudentMarkEntryRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentMarkServiceImpl Unit Tests")
class StudentMarkServiceImplTest {

    @Mock
    private StudentMarkEntryRepository studentMarkEntryRepository;

    @Mock
    private ExamSubjectConfigRepository examSubjectConfigRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private GradingScaleTierRepository gradingScaleTierRepository;

    @Spy
    private StudentMarkMapper studentMarkMapper = new StudentMarkMapper();

    @InjectMocks
    private StudentMarkServiceImpl studentMarkService;

    private UUID configId;
    private UUID studentId;
    private UUID scaleId;
    private ExamSubjectConfig config;
    private Student student;
    private GradingScale scale;
    private GradingScaleTier tierA;

    @BeforeEach
    void setUp() {
        configId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        scaleId = UUID.randomUUID();

        scale = new GradingScale("GSC-12345678", "Standard 10-point", "CBSE Scale", true);
        scale.setId(scaleId);

        tierA = new GradingScaleTier(scale, "A1", new BigDecimal("90.00"), new BigDecimal("100.00"),
                new BigDecimal("10.00"), "Outstanding", true, 1);

        ExamSchedule schedule = new ExamSchedule(
                "SCH-12345678",
                null,
                null,
                scale,
                "Grade 10 Midterms"
        );
        schedule.setStatus(ExamScheduleStatus.DRAFT);

        Subject subject = new Subject("SUB-12345678", "Mathematics", "MATH", "Desc", SubjectType.CORE, 1);

        config = new ExamSubjectConfig(
                "CFG-12345678",
                schedule,
                subject,
                LocalDate.of(2026, 9, 5),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                new BigDecimal("100.00"),
                new BigDecimal("35.00"),
                new BigDecimal("80.00"),
                new BigDecimal("20.00"),
                null
        );
        config.setId(configId);

        student = new Student("STU-12345678", null, "ADM-2026-001", LocalDate.of(2026, 4, 1));
        student.setId(studentId);
    }

    @Test
    @DisplayName("submitStudentMark successfully computes grade from scale and persists entry")
    void submitStudentMark_Success() {
        SubmitStudentMarkRequest request = SubmitStudentMarkRequest.builder()
                .studentId(studentId)
                .marksObtained(new BigDecimal("95.00"))
                .theoryMarksObtained(new BigDecimal("75.00"))
                .practicalMarksObtained(new BigDecimal("20.00"))
                .remarks("Excellent")
                .build();

        when(examSubjectConfigRepository.findActiveById(configId)).thenReturn(Optional.of(config));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentMarkEntryRepository.findByConfigAndStudent(configId, studentId)).thenReturn(Optional.empty());
        when(gradingScaleTierRepository.findMatchingTier(scaleId, new BigDecimal("95.00")))
                .thenReturn(Optional.of(tierA));
        when(studentMarkEntryRepository.save(any(StudentMarkEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentMarkResponse response = studentMarkService.submitStudentMark(configId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.getMarksObtained()).isEqualByComparingTo("95.00");
        assertThat(response.getGrade()).isEqualTo("A1");
        assertThat(response.getGradePoint()).isEqualByComparingTo("10.00");
        assertThat(response.isPassing()).isTrue();
        verify(studentMarkEntryRepository).save(any(StudentMarkEntry.class));
    }

    @Test
    @DisplayName("submitStudentMark throws BusinessException when marks exceed max marks")
    void submitStudentMark_MarksExceedMax_ThrowsException() {
        SubmitStudentMarkRequest request = SubmitStudentMarkRequest.builder()
                .studentId(studentId)
                .marksObtained(new BigDecimal("105.00"))
                .build();

        when(examSubjectConfigRepository.findActiveById(configId)).thenReturn(Optional.of(config));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentMarkEntryRepository.findByConfigAndStudent(configId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentMarkService.submitStudentMark(configId, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed maximum marks");
    }

    @Test
    @DisplayName("submitStudentMark throws BusinessException when entry is locked")
    void submitStudentMark_LockedEntry_ThrowsException() {
        SubmitStudentMarkRequest request = SubmitStudentMarkRequest.builder()
                .studentId(studentId)
                .marksObtained(new BigDecimal("80.00"))
                .build();

        StudentMarkEntry lockedEntry = new StudentMarkEntry("ENT-12345678", config, student);
        lockedEntry.setStatus(MarkEntryStatus.LOCKED);

        when(examSubjectConfigRepository.findActiveById(configId)).thenReturn(Optional.of(config));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentMarkEntryRepository.findByConfigAndStudent(configId, studentId)).thenReturn(Optional.of(lockedEntry));

        assertThatThrownBy(() -> studentMarkService.submitStudentMark(configId, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot modify locked student marks");
    }

    @Test
    @DisplayName("submitBulkMarks processes multiple student mark items")
    void submitBulkMarks_Success() {
        SubmitStudentMarkRequest item = SubmitStudentMarkRequest.builder()
                .studentId(studentId)
                .marksObtained(new BigDecimal("92.00"))
                .build();

        BulkStudentMarksRequest request = BulkStudentMarksRequest.builder()
                .examSubjectConfigId(configId)
                .entries(List.of(item))
                .build();

        when(examSubjectConfigRepository.findActiveById(configId)).thenReturn(Optional.of(config));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentMarkEntryRepository.findByConfigAndStudent(configId, studentId)).thenReturn(Optional.empty());
        when(gradingScaleTierRepository.findMatchingTier(scaleId, new BigDecimal("92.00")))
                .thenReturn(Optional.of(tierA));
        when(studentMarkEntryRepository.save(any(StudentMarkEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BulkStudentMarksResponse response = studentMarkService.submitBulkMarks(request, null);

        assertThat(response).isNotNull();
        assertThat(response.getTotalProcessed()).isEqualTo(1);
        assertThat(response.getCreatedCount()).isEqualTo(1);
        assertThat(response.getUpdatedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("lockMarks locks marks for completed subject evaluation")
    void lockMarks_Success() {
        StudentMarkEntry entry = new StudentMarkEntry("ENT-12345678", config, student);
        entry.setStatus(MarkEntryStatus.SUBMITTED);

        when(studentMarkEntryRepository.findByConfigId(configId)).thenReturn(List.of(entry));

        studentMarkService.lockMarks(configId);

        assertThat(entry.getStatus()).isEqualTo(MarkEntryStatus.LOCKED);
        verify(studentMarkEntryRepository).save(any());
    }
}
