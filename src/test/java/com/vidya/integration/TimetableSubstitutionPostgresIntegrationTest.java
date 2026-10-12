package com.vidya.integration;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository;
import com.vidya.tenantdata.timetable.service.TimetableSubstitutionServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@DisplayName("TimetableSubstitution — PostgreSQL Database Constraint & Concurrency Integration Tests")
class TimetableSubstitutionPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static TimetableSubstitutionServiceImpl substitutionService;
    private static CalendarService calendarService;

    @BeforeAll
    static void initService() {
        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(dataSource);
        emfb.setPackagesToScan("com.vidya.tenantdata", "com.vidya.master");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emfb.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", "false");
        emfb.setJpaProperties(props);
        emfb.afterPropertiesSet();

        emf = emfb.getObject();
        em = emf.createEntityManager();

        JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(em);
        TimetableSubstitutionRepository subRepo = repoFactory.getRepository(TimetableSubstitutionRepository.class);
        TimetableRepository timetableRepo = repoFactory.getRepository(TimetableRepository.class);
        TimetableEntryRepository entryRepo = repoFactory.getRepository(TimetableEntryRepository.class);
        BellSchedulePeriodRepository periodRepo = repoFactory.getRepository(BellSchedulePeriodRepository.class);
        AcademicClassRepository classRepo = repoFactory.getRepository(AcademicClassRepository.class);
        SectionRepository sectionRepo = repoFactory.getRepository(SectionRepository.class);
        StaffRepository staffRepo = repoFactory.getRepository(StaffRepository.class);
        RoomRepository roomRepo = repoFactory.getRepository(RoomRepository.class);
        TeacherSubjectAssignmentRepository teacherAssignmentRepo = repoFactory.getRepository(TeacherSubjectAssignmentRepository.class);

        TimetableSubstitutionMapper mapper = new TimetableSubstitutionMapper();
        calendarService = Mockito.mock(CalendarService.class);
        Mockito.lenient().when(calendarService.getDayStatus(any())).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            return new DayStatusResponse(d, d.getDayOfWeek(), DayStatus.WORKING);
        });

        substitutionService = new TimetableSubstitutionServiceImpl(
                subRepo,
                timetableRepo,
                entryRepo,
                periodRepo,
                classRepo,
                sectionRepo,
                staffRepo,
                roomRepo,
                teacherAssignmentRepo,
                mapper,
                calendarService
        );
    }

    @AfterAll
    static void closeEmf() {
        if (em != null) {
            em.close();
        }
        if (emf != null) {
            emf.close();
        }
    }

    private UUID createAcademicYear(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, name, LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6), false, "ACTIVE"
        );
        return id;
    }

    private UUID createAcademicClass(String code, String name, int order) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, ?)",
                id, code, name, order
        );
        return id;
    }

    private UUID createSection(UUID yearId, UUID classId, String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                id, yearId, classId, code, name, 40
        );
        return id;
    }

    private UUID createTenantUser(String code, String email, String type) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, phone, user_type) VALUES (?, ?, ?, ?, ?)",
                id, code, email, "9876543210", type
        );
        return id;
    }

    private UUID createStaff(UUID userId, String code, String employeeNo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff (id, staff_code, tenant_user_id, employee_code, joining_date, employment_status, staff_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, userId, employeeNo, LocalDate.now().minusMonths(12), "ACTIVE", "TEACHER"
        );
        return id;
    }

    private UUID createSubject(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO subject (id, subject_code, name, short_name, subject_type, status) " +
                "VALUES (?, ?, ?, ?, 'CORE', 'ACTIVE')",
                id, code, name, code
        );
        return id;
    }

    private UUID createRoom(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, ?, ?, ?)",
                id, code, name, "CLASSROOM", 40, "ACTIVE"
        );
        return id;
    }

    private UUID createBellSchedule(String code, String name, boolean isDefault) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) " +
                "VALUES (?, ?, ?, ?, ?)",
                id, code, name, isDefault, "ACTIVE"
        );
        return id;
    }

    private UUID createBellSchedulePeriod(UUID scheduleId, String code, String name, int order, LocalTime start, LocalTime end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, scheduleId, name, order, start, end, "TEACHING"
        );
        return id;
    }

    private UUID createTimetable(UUID yearId, UUID scheduleId, String code, String name, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, yearId, scheduleId, name, status, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(5)
        );
        return id;
    }

    private UUID createTimetableEntry(UUID timetableId, UUID yearId, UUID periodId, DayOfWeek dow, UUID classId, UUID sectionId, UUID subjectId, UUID teacherId, UUID roomId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, subject_id, teacher_id, room_id, entry_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "TTE-" + UUID.randomUUID().toString().substring(0, 8), timetableId, yearId, periodId, dow.name(), classId, sectionId, subjectId, teacherId, roomId, "REGULAR"
        );
        return id;
    }

    @Test
    @DisplayName("V14 Schema & Partial Unique Index idx_sub_slot_active — Duplicate active substitution for same slot is blocked")
    void substitution_slotActivePartialIndex_blocksDuplicates() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SB1-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-SB1-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-SB1-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SB1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SB1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SB1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID user1 = createTenantUser("USR-SB1-" + suffix, "sb1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-SB1-" + suffix, "EMP-SB1-" + suffix);
        UUID user2 = createTenantUser("USR-SB2-" + suffix, "sb2_" + suffix + "@test.com", "TEACHER");
        UUID teacher2 = createStaff(user2, "STF-SB2-" + suffix, "EMP-SB2-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(1);

        // First active substitution succeeds
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-1-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        );

        // Second active substitution for same slot fails with unique constraint violation
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-2-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher2, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);

        // Cancelling first substitution allows creating a new active substitution for the slot
        jdbcTemplate.update("UPDATE timetable_substitution SET status = 'CANCELLED' WHERE substitution_code = ?", "SUB-1-" + suffix);

        int rows = jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-3-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher2, "ACTIVE"
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Partial Unique Index idx_sub_teacher_active — Duplicate booking of substitute teacher at date and period is blocked")
    void substitution_teacherActivePartialIndex_blocksDoubleBooking() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-ST1-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-ST1-" + suffix, "Class 1 " + suffix, 1);
        UUID class2 = createAcademicClass("CLS-ST2-" + suffix, "Class 2 " + suffix, 2);
        UUID scheduleId = createBellSchedule("SCH-ST1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-ST1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-ST1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID user1 = createTenantUser("USR-ST1-" + suffix, "st1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-ST1-" + suffix, "EMP-ST1-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(2);

        // First booking for teacher1 in class1
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-T1-" + suffix, timetableId, subDate, periodId, class1, null, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        );

        // Second booking for same teacher1 in class2 at same date and period fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-T2-" + suffix, timetableId, subDate, periodId, class2, null, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Partial Unique Index idx_sub_room_active — Duplicate booking of substitute room at date and period is blocked")
    void substitution_roomActivePartialIndex_blocksDoubleBooking() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SR1-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-SR1-" + suffix, "Class 1 " + suffix, 1);
        UUID class2 = createAcademicClass("CLS-SR2-" + suffix, "Class 2 " + suffix, 2);
        UUID scheduleId = createBellSchedule("SCH-SR1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SR1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SR1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID room1 = createRoom("ROM-SR1-" + suffix, "Room " + suffix);
        LocalDate subDate = LocalDate.now().plusDays(3);

        // First booking for room1 in class1
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_room_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-R1-" + suffix, timetableId, subDate, periodId, class1, null, "ROOM_CHANGE", room1, "ACTIVE"
        );

        // Second booking for same room1 in class2 at same date and period fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_room_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-R2-" + suffix, timetableId, subDate, periodId, class2, null, "ROOM_CHANGE", room1, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Five Concurrent Creates via Service — Same substitute teacher and slot: exactly 1 wins, 4 get DuplicateResourceException")
    void concurrentSubstitutionCreates_5Threads_exactlyOneWins() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SC5-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-SC5-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-SC5-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SC5-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SC5-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SC5-" + suffix, "Timetable " + suffix, "ACTIVE");
        UUID subjectId = createSubject("SUB-SC5-" + suffix, "Subject " + suffix);
        UUID room1 = createRoom("ROM-SC5-" + suffix, "Room " + suffix);

        UUID user1 = createTenantUser("USR-SC5A-" + suffix, "sc5a_" + suffix + "@test.com", "TEACHER");
        UUID origTeacher = createStaff(user1, "STF-SC5A-" + suffix, "EMP-SC5A-" + suffix);

        UUID user2 = createTenantUser("USR-SC5B-" + suffix, "sc5b_" + suffix + "@test.com", "TEACHER");
        UUID substituteTeacher = createStaff(user2, "STF-SC5B-" + suffix, "EMP-SC5B-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(4);
        DayOfWeek dow = subDate.getDayOfWeek();

        // Create base timetable entry for the slot
        createTimetableEntry(timetableId, yearId, periodId, dow, classId, sectionId, subjectId, origTeacher, room1);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateExceptionCount = new AtomicInteger(0);
        List<Throwable> otherExceptions = Collections.synchronizedList(new ArrayList<>());

        CreateSubstitutionRequest request = new CreateSubstitutionRequest(
                timetableId,
                subDate,
                periodId,
                classId,
                sectionId,
                SubstitutionType.TEACHER_SUBSTITUTE,
                substituteTeacher,
                null,
                "Concurrent test substitution"
        );

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                try {
                    JpaRepositoryFactory threadRepoFactory = new JpaRepositoryFactory(threadEm);
                    TimetableSubstitutionRepository threadSubRepo = threadRepoFactory.getRepository(TimetableSubstitutionRepository.class);
                    TimetableRepository threadTimetableRepo = threadRepoFactory.getRepository(TimetableRepository.class);
                    TimetableEntryRepository threadEntryRepo = threadRepoFactory.getRepository(TimetableEntryRepository.class);
                    BellSchedulePeriodRepository threadPeriodRepo = threadRepoFactory.getRepository(BellSchedulePeriodRepository.class);
                    AcademicClassRepository threadClassRepo = threadRepoFactory.getRepository(AcademicClassRepository.class);
                    SectionRepository threadSectionRepo = threadRepoFactory.getRepository(SectionRepository.class);
                    StaffRepository threadStaffRepo = threadRepoFactory.getRepository(StaffRepository.class);
                    RoomRepository threadRoomRepo = threadRepoFactory.getRepository(RoomRepository.class);
                    TeacherSubjectAssignmentRepository threadTeacherAssignmentRepo = threadRepoFactory.getRepository(TeacherSubjectAssignmentRepository.class);

                    TimetableSubstitutionServiceImpl threadSubService = new TimetableSubstitutionServiceImpl(
                            threadSubRepo,
                            threadTimetableRepo,
                            threadEntryRepo,
                            threadPeriodRepo,
                            threadClassRepo,
                            threadSectionRepo,
                            threadStaffRepo,
                            threadRoomRepo,
                            threadTeacherAssignmentRepo,
                            new TimetableSubstitutionMapper(),
                            calendarService
                    );

                    latch.await();
                    threadEm.getTransaction().begin();
                    threadSubService.createSubstitution(request);
                    threadEm.getTransaction().commit();
                    successCount.incrementAndGet();
                } catch (DuplicateResourceException e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                    duplicateExceptionCount.incrementAndGet();
                } catch (Throwable t) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                    otherExceptions.add(t);
                } finally {
                    threadEm.close();
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(otherExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateExceptionCount.get()).isEqualTo(4);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_substitution WHERE timetable_id = ? AND substitution_date = ? AND period_id = ? AND academic_class_id = ? AND section_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL",
                Integer.class, timetableId, subDate, periodId, classId, sectionId
        );
        assertThat(activeCount).isEqualTo(1);
    }
}
