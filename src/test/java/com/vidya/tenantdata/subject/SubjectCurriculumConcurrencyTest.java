package com.vidya.tenantdata.subject;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.subject.service.CurriculumSubjectServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Subject & Curriculum — Concurrency & Duplicate Race Condition Tests")
class SubjectCurriculumConcurrencyTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private CurriculumSubjectRepository curriculumSubjectRepository;

    @InjectMocks
    private CurriculumSubjectServiceImpl curriculumSubjectService;

    @Test
    @DisplayName("Concurrent curriculum subject creation — Exactly one succeeds, second is rejected")
    void testConcurrentCurriculumSubjectCreation_DuplicateRace() throws Exception {
        UUID curriculumId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID academicClassId = UUID.randomUUID();

        AcademicYear academicYear = new AcademicYear("AY2026-27", "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        Curriculum curriculum = new Curriculum("CUR-CONC", academicYear, "Concurrent Curriculum", "Desc");
        curriculum.setId(curriculumId);
        curriculum.setStatus(CurriculumStatus.ACTIVE);

        Subject subject = new Subject("SUB-CONC", "Math", "M", "Desc", SubjectType.CORE, 1);
        subject.setId(subjectId);

        AcademicClass academicClass = new AcademicClass("CLS-10", "Class 10", "10", 1);
        academicClass.setId(academicClassId);

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(curriculum));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));

        // Atomic boolean to simulate the database partial unique index under race condition
        AtomicBoolean savedFirst = new AtomicBoolean(false);

        when(curriculumSubjectRepository.existsActiveByCurriculumSubjectClass(curriculumId, subjectId, academicClassId, null))
                .thenAnswer(inv -> savedFirst.get());

        when(curriculumSubjectRepository.save(any(CurriculumSubject.class))).thenAnswer(inv -> {
            if (!savedFirst.compareAndSet(false, true)) {
                // If another thread already saved first, simulate the DB unique constraint violation
                throw new DuplicateResourceException(
                        "CurriculumSubject",
                        "curriculumId, subjectId, academicClassId",
                        curriculumId + ", " + subjectId + ", " + academicClassId
                );
            }
            CurriculumSubject cs = inv.getArgument(0);
            cs.setId(UUID.randomUUID());
            return cs;
        });

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .isCompulsory(true)
                .displayOrder(1)
                .build();

        List<Future<CurriculumSubjectResponse>> futures = new ArrayList<>();
        List<Throwable> caughtExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    return curriculumSubjectService.addSubjectToCurriculum(curriculumId, request);
                } catch (Throwable t) {
                    caughtExceptions.add(t);
                    throw t;
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();

        int successes = 0;
        int failures = 0;

        for (Future<CurriculumSubjectResponse> future : futures) {
            try {
                CurriculumSubjectResponse res = future.get();
                if (res != null) {
                    successes++;
                }
            } catch (Exception e) {
                failures++;
            }
        }

        assertThat(successes).as("Exactly 1 concurrent addition should succeed").isEqualTo(1);
        assertThat(failures).as("Exactly 1 concurrent addition should be rejected").isEqualTo(1);
        assertThat(caughtExceptions).hasSize(1);
        assertThat(caughtExceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }
}
