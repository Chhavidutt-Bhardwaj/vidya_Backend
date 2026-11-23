package com.vidya.tenantdata.academic.config;

import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.DeliverRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ProgressRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ProgressRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ReviewDecision;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ReviewRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TopicInput;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.UnitInput;
import com.vidya.tenantdata.academic.config.entity.LessonPlan;
import com.vidya.tenantdata.academic.config.entity.Syllabus;
import com.vidya.tenantdata.academic.config.entity.SyllabusTopic;
import com.vidya.tenantdata.academic.config.entity.SyllabusTopicProgress;
import com.vidya.tenantdata.academic.config.entity.SyllabusUnit;
import com.vidya.tenantdata.academic.config.enums.LessonPlanStatus;
import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
import com.vidya.tenantdata.academic.config.enums.TopicProgressStatus;
import com.vidya.tenantdata.academic.config.repository.LessonPlanRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusTopicProgressRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusTopicRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusUnitRepository;
import com.vidya.tenantdata.academic.config.service.LessonPlanService;
import com.vidya.tenantdata.academic.config.service.SyllabusService;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Syllabus and lesson plans")
class SyllabusAndLessonPlanServiceTest {

    @Mock private SyllabusRepository syllabusRepository;
    @Mock private SyllabusUnitRepository unitRepository;
    @Mock private SyllabusTopicRepository topicRepository;
    @Mock private SyllabusTopicProgressRepository progressRepository;
    @Mock private LessonPlanRepository planRepository;
    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private TeachingScope scope;

    private SyllabusService syllabusService;
    private LessonPlanService planService;

    private final Map<UUID, Syllabus> syllabi = new LinkedHashMap<>();
    private final Map<UUID, SyllabusUnit> units = new LinkedHashMap<>();
    private final Map<UUID, SyllabusTopic> topics = new LinkedHashMap<>();
    private final List<SyllabusTopicProgress> progress = new ArrayList<>();
    private final Map<UUID, LessonPlan> plans = new LinkedHashMap<>();

    private AcademicYear year;
    private AcademicClass classV;
    private Section sectionA;
    private Section sectionB;
    private Subject maths;
    private Staff asha;

    @BeforeEach
    void setUp() {
        syllabusService = new SyllabusService(syllabusRepository, unitRepository, topicRepository, progressRepository, yearRepository,
                classRepository, subjectRepository, scope);
        planService = new LessonPlanService(planRepository, topicRepository, unitRepository, syllabusService, yearRepository, classRepository,
                sectionRepository, subjectRepository, staffRepository, scope);

        year = new AcademicYear("AY2026-27", "2026-27", LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(8), true, AcademicYearStatus.ACTIVE);
        year.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));
        classV = new AcademicClass("V", "Class V", "Class V", 5);
        classV.setId(UUID.randomUUID());
        when(classRepository.findByIdAndNotDeleted(classV.getId())).thenReturn(Optional.of(classV));
        sectionA = section("A");
        sectionB = section("B");
        maths = new Subject("SUB-MATH", "Mathematics", "Maths", null, SubjectType.CORE, 1);
        maths.setId(UUID.randomUUID());
        when(subjectRepository.findByIdAndNotDeleted(maths.getId())).thenReturn(Optional.of(maths));
        TenantUser u = new TenantUser("USR-1", "asha@school.in", null, TenantUserType.TEACHER);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, "Asha", "Rao"));
        asha = new Staff("STF-1", u, null, LocalDate.of(2020, 6, 1), StaffType.TEACHER, null, null);
        asha.setId(UUID.randomUUID());
        when(staffRepository.findByIdAndNotDeleted(asha.getId())).thenReturn(Optional.of(asha));

        // signed in as Asha, who teaches Mathematics in Class V (both sections)
        when(scope.all()).thenReturn(false);
        when(scope.ownStaffIds()).thenReturn(Set.of(asha.getId()));
        when(scope.teaches(anyCollection(), eq(year.getId()), eq(classV.getId()), any(), eq(maths.getId()))).thenReturn(true);
        Map<UUID, String> sections = new LinkedHashMap<>();
        sections.put(sectionA.getId(), "A");
        sections.put(sectionB.getId(), "B");
        when(scope.sectionsOfYear(year.getId())).thenReturn(Map.of(classV.getId(), sections));
        when(scope.classNames(any())).thenReturn(Map.of(classV.getId(), "Class V"));
        when(scope.subjectNames(any())).thenReturn(Map.of(maths.getId(), "Mathematics"));

        // in-memory repositories
        when(syllabusRepository.save(any())).thenAnswer(i -> store(syllabi, i.getArgument(0), Syllabus::getId, Syllabus::setId));
        when(syllabusRepository.findByIdAndDeletedAtIsNull(any())).thenAnswer(i -> Optional.ofNullable(syllabi.get(i.<UUID>getArgument(0))));
        when(syllabusRepository.findByAcademicYearIdAndAcademicClassIdAndSubjectIdAndDeletedAtIsNull(any(), any(), any()))
                .thenAnswer(i -> syllabi.values().stream().findFirst());
        when(unitRepository.save(any())).thenAnswer(i -> store(units, i.getArgument(0), SyllabusUnit::getId, SyllabusUnit::setId));
        when(unitRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(units.get(i.<UUID>getArgument(0))));
        when(unitRepository.findBySyllabusIdOrderByUnitNoAsc(any())).thenAnswer(i -> units.values().stream()
                .sorted(Comparator.comparingInt(SyllabusUnit::getUnitNo)).toList());
        when(unitRepository.findBySyllabusIdInOrderByUnitNoAsc(any())).thenAnswer(i -> units.values().stream()
                .sorted(Comparator.comparingInt(SyllabusUnit::getUnitNo)).toList());
        doAnswer(i -> {
            i.<Collection<SyllabusUnit>>getArgument(0).forEach(x -> units.remove(x.getId()));
            return null;
        }).when(unitRepository).deleteAll(any());
        when(topicRepository.save(any())).thenAnswer(i -> store(topics, i.getArgument(0), SyllabusTopic::getId, SyllabusTopic::setId));
        when(topicRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(topics.get(i.<UUID>getArgument(0))));
        when(topicRepository.findBySyllabusIdOrderBySortOrderAsc(any())).thenAnswer(i -> new ArrayList<>(topics.values()));
        when(topicRepository.findBySyllabusIdInOrderBySortOrderAsc(any())).thenAnswer(i -> new ArrayList<>(topics.values()));
        doAnswer(i -> {
            i.<Collection<SyllabusTopic>>getArgument(0).forEach(x -> topics.remove(x.getId()));
            return null;
        }).when(topicRepository).deleteAll(any());
        when(progressRepository.save(any())).thenAnswer(i -> {
            SyllabusTopicProgress p = i.getArgument(0);
            if (!progress.contains(p)) {
                progress.add(p);
            }
            return p;
        });
        when(progressRepository.findByTopicIdAndSectionId(any(), any())).thenAnswer(i -> progress.stream()
                .filter(p -> p.getTopicId().equals(i.getArgument(0)) && p.getSectionId().equals(i.getArgument(1))).findFirst());
        when(progressRepository.findByTopicIdIn(any())).thenAnswer(i -> new ArrayList<>(progress));
        doAnswer(i -> progress.remove(i.<SyllabusTopicProgress>getArgument(0))).when(progressRepository).delete(any());
        when(planRepository.save(any())).thenAnswer(i -> store(plans, i.getArgument(0), LessonPlan::getId, LessonPlan::setId));
        when(planRepository.findByIdAndDeletedAtIsNull(any())).thenAnswer(i -> Optional.ofNullable(plans.get(i.<UUID>getArgument(0))));
    }

    @Test
    @DisplayName("syllabus: saved as one document; a re-save keeps the units and topics sent with ids and drops the rest")
    void syllabusSave() {
        SyllabusResponse first = syllabusService.save(request(null, List.of(
                unit(null, "Numbers", topic(null, "Place value"), topic(null, "Roman numerals")),
                unit(null, "Fractions", topic(null, "Like fractions")))));

        assertThat(first.title()).isEqualTo("Mathematics – Class V");
        assertThat(first.status()).isEqualTo(SyllabusStatus.DRAFT);
        assertThat(first.unitCount()).isEqualTo(2);
        assertThat(first.topicCount()).isEqualTo(3);
        assertThat(first.plannedPeriods()).isEqualTo(3 * 4);
        UUID numbers = first.units().get(0).id();
        UUID placeValue = first.units().get(0).topics().get(0).id();

        SyllabusResponse second = syllabusService.save(request(SyllabusStatus.PUBLISHED, List.of(
                unit(numbers, "Numbers up to 10 lakh", topic(placeValue, "Place value"), topic(null, "Comparing numbers")))));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo(SyllabusStatus.PUBLISHED);
        assertThat(second.units()).hasSize(1);
        assertThat(second.units().get(0).id()).isEqualTo(numbers);
        assertThat(second.units().get(0).topics()).extracting("title").containsExactly("Place value", "Comparing numbers");
        assertThat(second.units().get(0).topics().get(0).id()).isEqualTo(placeValue);

        assertThatThrownBy(() -> syllabusService.save(request(null, List.of(unit(UUID.randomUUID(), "Stray")))))
                .hasMessageContaining("not a unit of this syllabus");
        assertThatThrownBy(() -> syllabusService.save(request(SyllabusStatus.PUBLISHED, List.of())))
                .hasMessageContaining("at least one unit");
    }

    @Test
    @DisplayName("syllabus: coverage is per section; only the subject's teachers may mark it")
    void coverage() {
        SyllabusResponse s = syllabusService.save(request(SyllabusStatus.PUBLISHED, List.of(
                unit(null, "Numbers", topic(null, "Place value"), topic(null, "Roman numerals")))));
        UUID t1 = s.units().get(0).topics().get(0).id();
        UUID t2 = s.units().get(0).topics().get(1).id();

        SyllabusResponse a = syllabusService.updateProgress(s.id(), new ProgressRequest(sectionA.getId(), List.of(
                new ProgressRow(t1, TopicProgressStatus.COMPLETED, null, null), new ProgressRow(t2, TopicProgressStatus.IN_PROGRESS, null, "half done"))));

        assertThat(a.coverage()).extracting("section", "completed", "inProgress", "percent")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("A", 1, 1, 50), org.assertj.core.groups.Tuple.tuple("B", 0, 0, 0));
        assertThat(a.coveragePercent()).isEqualTo(25);
        assertThat(a.units().get(0).topics().get(0).status()).isEqualTo(TopicProgressStatus.COMPLETED);
        assertThat(a.units().get(0).topics().get(0).completedOn()).isEqualTo(LocalDate.now());

        SyllabusResponse cleared = syllabusService.updateProgress(s.id(), new ProgressRequest(sectionA.getId(),
                List.of(new ProgressRow(t1, TopicProgressStatus.NOT_STARTED, null, null))));
        assertThat(cleared.coverage().get(0).completed()).isZero();

        doThrow(new AccessDeniedException("only what you teach")).when(scope)
                .requireTeaches(eq(year.getId()), eq(classV.getId()), eq(sectionB.getId()), eq(maths.getId()), anyString());
        assertThatThrownBy(() -> syllabusService.updateProgress(s.id(), new ProgressRequest(sectionB.getId(),
                List.of(new ProgressRow(t1, TopicProgressStatus.COMPLETED, null, null))))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("lesson plan: draft → submitted → returned → resubmitted → approved → delivered, which completes the topic in every section taught")
    void lessonPlanFlow() {
        SyllabusResponse s = syllabusService.save(request(SyllabusStatus.PUBLISHED, List.of(unit(null, "Numbers", topic(null, "Place value")))));
        UUID topicId = s.units().get(0).topics().get(0).id();

        LessonPlanResponse p = planService.create(plan(null, topicId, false));
        assertThat(p.status()).isEqualTo(LessonPlanStatus.DRAFT);
        assertThat(p.teacherId()).isEqualTo(asha.getId());
        assertThat(p.syllabusUnitId()).isEqualTo(s.units().get(0).id());

        assertThat(planService.submit(p.id()).status()).isEqualTo(LessonPlanStatus.SUBMITTED);
        assertThatThrownBy(() -> planService.review(p.id(), new ReviewRequest(ReviewDecision.APPROVE, null)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("your own");

        when(scope.all()).thenReturn(true); // the coordinator
        assertThatThrownBy(() -> planService.review(p.id(), new ReviewRequest(ReviewDecision.RETURN, " "))).hasMessageContaining("what should be changed");
        assertThat(planService.review(p.id(), new ReviewRequest(ReviewDecision.RETURN, "Add an activity")).status()).isEqualTo(LessonPlanStatus.RETURNED);

        when(scope.all()).thenReturn(false); // Asha again
        assertThatThrownBy(() -> planService.deliver(p.id(), null)).hasMessageContaining("returned");
        assertThat(planService.update(p.id(), plan(null, topicId, true)).status()).isEqualTo(LessonPlanStatus.SUBMITTED);

        when(scope.all()).thenReturn(true);
        assertThat(planService.review(p.id(), new ReviewRequest(ReviewDecision.APPROVE, null)).status()).isEqualTo(LessonPlanStatus.APPROVED);

        when(scope.all()).thenReturn(false);
        assertThatThrownBy(() -> planService.update(p.id(), plan(null, topicId, false))).hasMessageContaining("APPROVED");
        LessonPlanResponse delivered = planService.deliver(p.id(), new DeliverRequest(null, "Went well", null));
        assertThat(delivered.status()).isEqualTo(LessonPlanStatus.DELIVERED);
        assertThat(delivered.deliveredOn()).isEqualTo(LocalDate.now());
        assertThat(progress).hasSize(2).allMatch(x -> x.getTopicId().equals(topicId) && x.getStatus() == TopicProgressStatus.COMPLETED);
    }

    @Test
    @DisplayName("lesson plan: only for what the teacher teaches, only for oneself, only inside the year")
    void lessonPlanRules() {
        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> planService.create(new LessonPlanRequest(year.getId(), classV.getId(), null, maths.getId(), someoneElse, null, null,
                "Place value", LocalDate.now(), 1, null, null, null, null, null, false))).isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> planService.create(new LessonPlanRequest(year.getId(), classV.getId(), null, maths.getId(), null, null, null,
                "Place value", year.getEndDate().plusDays(1), 1, null, null, null, null, null, false))).hasMessageContaining("outside");

        when(scope.teaches(anyCollection(), any(), any(), any(), any())).thenReturn(false);
        when(scope.staffNames(any())).thenReturn(Map.of(asha.getId(), "Asha Rao"));
        assertThatThrownBy(() -> planService.create(plan(sectionA.getId(), null, false)))
                .hasMessageContaining("Asha Rao does not teach Mathematics in Class V A");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SyllabusRequest request(SyllabusStatus status, List<UnitInput> unitInputs) {
        return new SyllabusRequest(year.getId(), classV.getId(), maths.getId(), null, null, null, status, unitInputs);
    }

    private static UnitInput unit(UUID id, String title, TopicInput... topicInputs) {
        return new UnitInput(id, title, null, null, null, null, null, null, List.of(topicInputs));
    }

    private static TopicInput topic(UUID id, String title) {
        return new TopicInput(id, title, 4);
    }

    private LessonPlanRequest plan(UUID sectionId, UUID topicId, boolean submit) {
        return new LessonPlanRequest(year.getId(), classV.getId(), sectionId, maths.getId(), null, null, topicId, "Place value with an abacus",
                LocalDate.now(), 2, "Read 6-digit numbers", "Abacus activity", "Abacus", "Ex 1.2", "Oral quiz", submit);
    }

    private Section section(String code) {
        Section s = new Section(year, classV, code, code, 40);
        s.setId(UUID.randomUUID());
        when(sectionRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private static <T> T store(Map<UUID, T> map, T entity, java.util.function.Function<T, UUID> getId, java.util.function.BiConsumer<T, UUID> setId) {
        if (getId.apply(entity) == null) {
            setId.accept(entity, UUID.randomUUID());
        }
        map.put(getId.apply(entity), entity);
        return entity;
    }
}
