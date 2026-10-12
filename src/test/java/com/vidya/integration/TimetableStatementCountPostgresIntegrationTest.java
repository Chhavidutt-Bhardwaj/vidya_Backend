package com.vidya.integration;

import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.calendar.mapper.WorkingDayConfigMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.repository.WorkingDayConfigRepository;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableEntryMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository;
import com.vidya.tenantdata.timetable.service.TimetableConflictValidator;
import com.vidya.tenantdata.timetable.service.TimetableServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Timetable — Statement-Count Invariance Integration Tests (Real Repositories & Services with Testcontainers)")
class TimetableStatementCountPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static Statistics statistics;

    private static TimetableServiceImpl timetableService;

    @BeforeAll
    static void initJpaAndServices() {
        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(dataSource);
        emfb.setPackagesToScan("com.vidya.tenantdata", "com.vidya.master");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emfb.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.generate_statistics", "true");
        props.put("hibernate.show_sql", "false");
        emfb.setJpaProperties(props);
        emfb.afterPropertiesSet();

        emf = emfb.getObject();
        em = emf.createEntityManager();

        SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(em);
        TimetableRepository timetableRepo = repoFactory.getRepository(TimetableRepository.class);
        TimetableEntryRepository entryRepo = repoFactory.getRepository(TimetableEntryRepository.class);
        TimetableSubstitutionRepository substitutionRepo = repoFactory.getRepository(TimetableSubstitutionRepository.class);
        BellScheduleRepository bellScheduleRepo = repoFactory.getRepository(BellScheduleRepository.class);
        BellSchedulePeriodRepository periodRepo = repoFactory.getRepository(BellSchedulePeriodRepository.class);
        AcademicYearRepository academicYearRepo = repoFactory.getRepository(AcademicYearRepository.class);
        AcademicClassRepository academicClassRepo = repoFactory.getRepository(AcademicClassRepository.class);
        SectionRepository sectionRepo = repoFactory.getRepository(SectionRepository.class);
        SubjectRepository subjectRepo = repoFactory.getRepository(SubjectRepository.class);
        StaffRepository staffRepo = repoFactory.getRepository(StaffRepository.class);
        RoomRepository roomRepo = repoFactory.getRepository(RoomRepository.class);
        TeacherSubjectAssignmentRepository teacherAssignmentRepo = repoFactory.getRepository(TeacherSubjectAssignmentRepository.class);
        ClassSubjectAssignmentRepository classSubjectRepo = repoFactory.getRepository(ClassSubjectAssignmentRepository.class);

        BellScheduleMapper bellScheduleMapper = new BellScheduleMapper();
        TimetableEntryMapper timetableEntryMapper = new TimetableEntryMapper();
        TimetableMapper timetableMapper = new TimetableMapper(bellScheduleMapper, timetableEntryMapper);
        TimetableSubstitutionMapper substitutionMapper = new TimetableSubstitutionMapper();
        WorkingDayConfigRepository workingDayConfigRepo = repoFactory.getRepository(WorkingDayConfigRepository.class);
        SchoolHolidayRepository schoolHolidayRepo = repoFactory.getRepository(SchoolHolidayRepository.class);
        WorkingDayConfigMapper workingDayConfigMapper = new WorkingDayConfigMapper();
        com.vidya.tenantdata.calendar.service.CalendarService calendarService = new com.vidya.tenantdata.calendar.service.CalendarServiceImpl(
                academicYearRepo,
                workingDayConfigRepo,
                schoolHolidayRepo,
                workingDayConfigMapper
        );

        TimetableConflictValidator conflictValidator = new com.vidya.tenantdata.timetable.service.TimetableConflictValidatorImpl(
                timetableRepo,
                entryRepo,
                periodRepo,
                roomRepo,
                staffRepo,
                academicClassRepo,
                sectionRepo,
                subjectRepo,
                teacherAssignmentRepo,
                classSubjectRepo,
                calendarService
        );

        timetableService = new TimetableServiceImpl(
                timetableRepo,
                entryRepo,
                bellScheduleRepo,
                periodRepo,
                academicYearRepo,
                academicClassRepo,
                sectionRepo,
                subjectRepo,
                staffRepo,
                roomRepo,
                teacherAssignmentRepo,
                classSubjectRepo,
                conflictValidator,
                timetableMapper,
                timetableEntryMapper,
                substitutionRepo,
                substitutionMapper,
                calendarService
        );
    }

    @AfterAll
    static void closeJpa() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    @DisplayName("Assert prepared statement counts are identical for 5 vs 50 entries across all read endpoints")
    void statementCountInvariance_5vs50Entries_realDatabase() {
        // 1. Seed Master Entities via JDBC
        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, true, 'ACTIVE')",
                yearId, "AY-STMT-01", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)
        );

        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 10)",
                classId, "CLS-STMT-10", "Class 10"
        );

        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-STMT-A", "Section A"
        );

        UUID subjectId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO subject (id, subject_code, name, subject_type, status) VALUES (?, ?, ?, 'CORE', 'ACTIVE')",
                subjectId, "SUB-STMT-PHY", "Physics"
        );

        UUID tenantUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type, status) VALUES (?, ?, ?, 'STAFF', 'ACTIVE')",
                tenantUserId, "TU-STMT-01", "teacher.stmt@vidya.io"
        );

        UUID teacherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff (id, staff_code, tenant_user_id, employee_code, joining_date, employment_status, staff_type) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'TEACHER')",
                teacherId, "STF-STMT-01", tenantUserId, "EMP-STMT-01", LocalDate.of(2025, 1, 1)
        );

        UUID subTenantUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type, status) VALUES (?, ?, ?, 'STAFF', 'ACTIVE')",
                subTenantUserId, "TU-STMT-02", "subteacher.stmt@vidya.io"
        );

        UUID subTeacherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff (id, staff_code, tenant_user_id, employee_code, joining_date, employment_status, staff_type) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'TEACHER')",
                subTeacherId, "STF-STMT-02", subTenantUserId, "EMP-STMT-02", LocalDate.of(2025, 1, 1)
        );

        UUID roomId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, 'LAB', 40, 'ACTIVE')",
                roomId, "ROM-STMT-101", "Physics Lab"
        );

        UUID teacherAssignmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO teacher_subject_assignment (id, assignment_code, teacher_id, subject_id, academic_year_id, academic_class_id, section_id, is_primary, status) VALUES (?, ?, ?, ?, ?, ?, ?, true, 'ACTIVE')",
                teacherAssignmentId, "TSA-STMT-01", teacherId, subjectId, yearId, classId, sectionId
        );

        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, true, 'ACTIVE')",
                scheduleId, "BSC-STMT-01", "Main Schedule"
        );

        List<UUID> periodIds = new ArrayList<>();
        for (int p = 1; p <= 8; p++) {
            UUID pId = UUID.randomUUID();
            LocalTime start = LocalTime.of(8, 0).plusMinutes((p - 1) * 45L);
            LocalTime end = start.plusMinutes(40);
            jdbcTemplate.update(
                    "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) VALUES (?, ?, ?, ?, ?, ?, ?, 'TEACHING')",
                    pId, "PRD-STMT-0" + p, scheduleId, "Period " + p, p, start, end
            );
            periodIds.add(pId);
        }

        UUID timetableId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                timetableId, "TTB-STMT-01", yearId, scheduleId, "Master Timetable 2026", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)
        );

        // 2. Seed 5 Entries initially
        for (int i = 0; i < 5; i++) {
            DayOfWeek day = DayOfWeek.of((i % 5) + 1);
            UUID pId = periodIds.get(0);
            jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, subject_id, teacher_assignment_id, teacher_id, room_id, entry_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REGULAR')",
                    UUID.randomUUID(), "TTE-STMT-" + i, timetableId, yearId, pId, day.name(), classId, sectionId, subjectId, teacherAssignmentId, teacherId, roomId
            );
        }

        // Seed 1 active substitution for the section/teacher on 2026-10-05 (Monday, Period 1)
        LocalDate weekMonday = LocalDate.of(2026, 10, 5);
        UUID subId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, original_teacher_id, original_subject_id, substitute_teacher_id, reason, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'TEACHER_SUBSTITUTE', ?, ?, ?, 'Medical leave', 'ACTIVE')",
                subId, "SUB-STMT-01", timetableId, weekMonday, periodIds.get(0), classId, sectionId, teacherId, subjectId, subTeacherId
        );

        em.clear();

        // 3. Measure Prepared Statement Counts for 5 entries
        statistics.clear();
        timetableService.getSectionTimetableGrid(timetableId, sectionId, null, null);
        long sectionGrid5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherTimetableGrid(timetableId, teacherId, null, null);
        long teacherGrid5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getRoomOccupancy(timetableId, roomId, null);
        long roomOccupancy5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherLoad(timetableId, teacherId, null);
        long teacherLoad5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getActiveSectionTimetable(yearId, sectionId, null);
        long activeTimetable5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getSectionDaySchedule(timetableId, sectionId, weekMonday);
        long sectionDay5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherDaySchedule(timetableId, teacherId, weekMonday);
        long teacherDay5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getSectionTimetableGrid(timetableId, sectionId, null, weekMonday);
        long sectionGridWeekOf5 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherTimetableGrid(timetableId, teacherId, null, weekMonday);
        long teacherGridWeekOf5 = statistics.getPrepareStatementCount();

        // 4. Seed 45 more entries (Total = 50 entries)
        int count = 5;
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SUNDAY) continue;
            for (int pIdx = 0; pIdx < periodIds.size(); pIdx++) {
                if (count >= 50) break;
                if (pIdx == 0 && day.getValue() <= 5) continue; // skip already seeded period 1 on Mon-Fri

                UUID pId = periodIds.get(pIdx);
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, subject_id, teacher_assignment_id, teacher_id, room_id, entry_type) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REGULAR')",
                        UUID.randomUUID(), "TTE-STMT-" + count, timetableId, yearId, pId, day.name(), classId, sectionId, subjectId, teacherAssignmentId, teacherId, roomId
                );
                count++;
            }
        }

        em.clear();

        // 5. Measure Prepared Statement Counts for 50 entries
        statistics.clear();
        timetableService.getSectionTimetableGrid(timetableId, sectionId, null, null);
        long sectionGrid50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherTimetableGrid(timetableId, teacherId, null, null);
        long teacherGrid50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getRoomOccupancy(timetableId, roomId, null);
        long roomOccupancy50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherLoad(timetableId, teacherId, null);
        long teacherLoad50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getActiveSectionTimetable(yearId, sectionId, null);
        long activeTimetable50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getSectionDaySchedule(timetableId, sectionId, weekMonday);
        long sectionDay50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherDaySchedule(timetableId, teacherId, weekMonday);
        long teacherDay50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getSectionTimetableGrid(timetableId, sectionId, null, weekMonday);
        long sectionGridWeekOf50 = statistics.getPrepareStatementCount();

        statistics.clear();
        timetableService.getTeacherTimetableGrid(timetableId, teacherId, null, weekMonday);
        long teacherGridWeekOf50 = statistics.getPrepareStatementCount();

        // 6. Assert exact equality (O(1) database queries with respect to entry count)
        System.out.println("=== HIBERNATE PREPARED STATEMENT COUNTS (5 vs 50 entries) ===");
        System.out.println("Section Grid: 5 entries = " + sectionGrid5 + ", 50 entries = " + sectionGrid50);
        System.out.println("Teacher Grid: 5 entries = " + teacherGrid5 + ", 50 entries = " + teacherGrid50);
        System.out.println("Room Occupancy: 5 entries = " + roomOccupancy5 + ", 50 entries = " + roomOccupancy50);
        System.out.println("Teacher Load: 5 entries = " + teacherLoad5 + ", 50 entries = " + teacherLoad50);
        System.out.println("Active Timetable by Year: 5 entries = " + activeTimetable5 + ", 50 entries = " + activeTimetable50);
        System.out.println("Section Day View: 5 entries = " + sectionDay5 + ", 50 entries = " + sectionDay50);
        System.out.println("Teacher Day View: 5 entries = " + teacherDay5 + ", 50 entries = " + teacherDay50);
        System.out.println("Section Grid with weekOf: 5 entries = " + sectionGridWeekOf5 + ", 50 entries = " + sectionGridWeekOf50);
        System.out.println("Teacher Grid with weekOf: 5 entries = " + teacherGridWeekOf5 + ", 50 entries = " + teacherGridWeekOf50);

        assertThat(sectionGrid50).isEqualTo(sectionGrid5);
        assertThat(teacherGrid50).isEqualTo(teacherGrid5);
        assertThat(roomOccupancy50).isEqualTo(roomOccupancy5);
        assertThat(teacherLoad50).isEqualTo(teacherLoad5);
        assertThat(activeTimetable50).isEqualTo(activeTimetable5);
        assertThat(sectionDay50).isEqualTo(sectionDay5);
        assertThat(teacherDay50).isEqualTo(teacherDay5);
        assertThat(sectionGridWeekOf50).isEqualTo(sectionGridWeekOf5);
        assertThat(teacherGridWeekOf50).isEqualTo(teacherGridWeekOf5);
    }

    @Test
    @DisplayName("Assert calendar-related queries are identical when validating 5 vs 200 timetable entries")
    void statementCountInvariance_validation_5vs200Entries() {
        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-VAL-STMT", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)
        );

        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                scheduleId, "BSC-VAL-STMT", "Schedule Val", false
        );

        UUID timetable5Id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?)",
                timetable5Id, "TTB-VAL-5", yearId, scheduleId, "TTB 5", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)
        );

        UUID timetable200Id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?)",
                timetable200Id, "TTB-VAL-200", yearId, scheduleId, "TTB 200", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)
        );

        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 10)",
                classId, "CLS-VAL-10", "Class 10"
        );

        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-VAL-A", "Section A"
        );

        UUID pId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) VALUES (?, ?, ?, ?, 1, '08:00:00', '08:45:00', 'TEACHING')",
                pId, "PRD-VAL-01", scheduleId, "Period 1"
        );

        // Seed 5 entries for timetable 5
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, entry_type) " +
                    "VALUES (?, ?, ?, ?, ?, 'MONDAY', ?, ?, 'REGULAR')",
                    UUID.randomUUID(), "TTE-V5-" + i, timetable5Id, yearId, pId, classId, sectionId
            );
        }

        // Seed 200 entries for timetable 200
        for (int i = 0; i < 200; i++) {
            jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, entry_type) " +
                    "VALUES (?, ?, ?, ?, ?, 'MONDAY', ?, ?, 'REGULAR')",
                    UUID.randomUUID(), "TTE-V200-" + i, timetable200Id, yearId, pId, classId, sectionId
            );
        }

        em.clear();

        statistics.clear();
        timetableService.validateTimetable(timetable5Id);
        long queries5 = statistics.getPrepareStatementCount();

        em.clear();

        statistics.clear();
        timetableService.validateTimetable(timetable200Id);
        long queries200 = statistics.getPrepareStatementCount();

        System.out.println("Timetable validation statement counts: 5 entries = " + queries5 + ", 200 entries = " + queries200);
        // Calendar queries are loaded once per validateTimetable invocation (O(1) calendar queries)
        assertThat(queries200).isEqualTo(queries5);
    }
}
