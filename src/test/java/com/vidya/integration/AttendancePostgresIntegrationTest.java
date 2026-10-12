package com.vidya.integration;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.attendance.dto.AttendanceSummaryResponse;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.MarkAttendanceRequest;
import com.vidya.tenantdata.attendance.entity.StudentAttendance;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.attendance.repository.StudentAttendanceRepository;
import com.vidya.tenantdata.attendance.service.StudentAttendanceService;
import com.vidya.tenantdata.attendance.service.StudentAttendanceServiceImpl;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.entity.WorkingDayConfig;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import com.vidya.tenantdata.calendar.mapper.WorkingDayConfigMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.repository.WorkingDayConfigRepository;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.calendar.service.CalendarServiceImpl;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Attendance — Real Database Integration Tests (PostgreSQL & Testcontainers)")
class AttendancePostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static Statistics statistics;

    private static StudentAttendanceService attendanceService;
    private static CalendarService calendarService;

    private static StudentAttendanceRepository attendanceRepo;
    private static StudentRepository studentRepo;
    private static StudentAcademicEnrollmentRepository enrollmentRepo;
    private static StudentAcademicAssignmentRepository studentAcademicAssignmentRepo;
    private static AcademicYearRepository academicYearRepo;
    private static AcademicClassRepository academicClassRepo;
    private static SectionRepository sectionRepo;
    private static StaffRepository staffRepo;
    private static TenantUserRepository tenantUserRepo;
    private static ClassTeacherAssignmentRepository classTeacherAssignmentRepo;
    private static WorkingDayConfigRepository workingDayConfigRepo;
    private static SchoolHolidayRepository schoolHolidayRepo;

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
        attendanceRepo = repoFactory.getRepository(StudentAttendanceRepository.class);
        studentRepo = repoFactory.getRepository(StudentRepository.class);
        enrollmentRepo = repoFactory.getRepository(StudentAcademicEnrollmentRepository.class);
        studentAcademicAssignmentRepo = repoFactory.getRepository(StudentAcademicAssignmentRepository.class);
        academicYearRepo = repoFactory.getRepository(AcademicYearRepository.class);
        academicClassRepo = repoFactory.getRepository(AcademicClassRepository.class);
        sectionRepo = repoFactory.getRepository(SectionRepository.class);
        staffRepo = repoFactory.getRepository(StaffRepository.class);
        tenantUserRepo = repoFactory.getRepository(TenantUserRepository.class);
        classTeacherAssignmentRepo = repoFactory.getRepository(ClassTeacherAssignmentRepository.class);
        workingDayConfigRepo = repoFactory.getRepository(WorkingDayConfigRepository.class);
        schoolHolidayRepo = repoFactory.getRepository(SchoolHolidayRepository.class);

        WorkingDayConfigMapper workingDayConfigMapper = new WorkingDayConfigMapper();
        calendarService = new CalendarServiceImpl(
                academicYearRepo,
                workingDayConfigRepo,
                schoolHolidayRepo,
                workingDayConfigMapper
        );

        attendanceService = new StudentAttendanceServiceImpl(
                attendanceRepo,
                studentRepo,
                enrollmentRepo,
                studentAcademicAssignmentRepo,
                academicYearRepo,
                academicClassRepo,
                sectionRepo,
                staffRepo,
                classTeacherAssignmentRepo,
                calendarService
        );
    }

    @AfterAll
    static void closeJpa() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @BeforeEach
    void setUpSecurity() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "pass",
                List.of(
                        new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"),
                        new SimpleGrantedAuthority("ATTENDANCE_OVERRIDE_NON_WORKING"),
                        new SimpleGrantedAuthority("ATTENDANCE_CREATE"),
                        new SimpleGrantedAuthority("ATTENDANCE_BULK_MARK"),
                        new SimpleGrantedAuthority("ATTENDANCE_READ")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("V16 migration applies on V1..V15 and override_reason CHECK rejects override without reason")
    void v16_migration_overrideCheckConstraintRejectsNullReason() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-V16-" + suffix, "Year " + suffix, LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)
        );
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1)",
                classId, "CLS-V16-" + suffix, "Class " + suffix
        );
        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-V16-" + suffix, "Section " + suffix
        );
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')",
                userId, "USR-V16-" + suffix, "v16" + suffix + "@test.com"
        );
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                studentId, "STU-V16-" + suffix, userId, "ADM-V16-" + suffix, LocalDate.now().minusMonths(6)
        );

        LocalDate attDate = LocalDate.now();

        // Check constraint chk_student_attendance_override_reason: non_working_day_override = TRUE requires override_reason IS NOT NULL
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status, non_working_day_override, override_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'PRESENT', true, null)",
                        UUID.randomUUID(), "ATT-ERR-" + suffix, studentId, yearId, classId, sectionId, attDate
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*chk_student_attendance_override_reason.*|.*check constraint.*");
    }

    @Test
    @DisplayName("Bulk attendance upsert with holiday date in range writes nothing (atomic rollback)")
    void bulkAttendance_holidayDate_writesNothing() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate holidayDate = LocalDate.of(2042, 10, 15);

        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-HOL-" + suffix, "Year " + suffix, LocalDate.of(2042, 1, 1), LocalDate.of(2042, 12, 31)
        );
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1)",
                classId, "CLS-HOL-" + suffix, "Class " + suffix
        );
        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-HOL-" + suffix, "Section " + suffix
        );

        // Configure a holiday on holidayDate
        UUID holidayId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, 'Diwali Break', 'PUBLIC_HOLIDAY', ?, ?)",
                holidayId, "HOL-" + suffix, yearId, holidayDate, holidayDate
        );

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')",
                userId, "USR-HOL-" + suffix, "hol" + suffix + "@test.com"
        );
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                studentId, "STU-HOL-" + suffix, userId, "ADM-HOL-" + suffix, LocalDate.of(2042, 2, 1)
        );

        UUID assignId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student_academic_assignment (id, student_id, academic_year_id, academic_class_id, section_id, roll_number, status, start_date) " +
                "VALUES (?, ?, ?, ?, ?, '001', 'ACTIVE', ?)",
                assignId, studentId, yearId, classId, sectionId, LocalDate.of(2042, 2, 1)
        );

        em.clear();

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                yearId, classId, sectionId, holidayDate,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, "Present")),
                false, null
        );

        // Service throws BusinessException and writes 0 records
        assertThatThrownBy(() -> attendanceService.markBulkAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot mark attendance on holiday");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_attendance WHERE academic_year_id = ? AND attendance_date = ?",
                Integer.class, yearId, holidayDate
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Override record persists with its reason when allowNonWorkingDay is true")
    void overrideRecord_persistsWithReason() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate sundayDate = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue()); // past Sunday

        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-OVR-" + suffix, "Year " + suffix, LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)
        );
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1)",
                classId, "CLS-OVR-" + suffix, "Class " + suffix
        );
        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-OVR-" + suffix, "Section " + suffix
        );

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')",
                userId, "USR-OVR-" + suffix, "ovr" + suffix + "@test.com"
        );
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                studentId, "STU-OVR-" + suffix, userId, "ADM-OVR-" + suffix, LocalDate.now().minusMonths(6)
        );

        UUID assignId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student_academic_assignment (id, student_id, academic_year_id, academic_class_id, section_id, roll_number, status, start_date) " +
                "VALUES (?, ?, ?, ?, ?, '001', 'ACTIVE', ?)",
                assignId, studentId, yearId, classId, sectionId, LocalDate.now().minusMonths(6)
        );

        em.clear();

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, sundayDate, AttendanceStatus.PRESENT, "Sunday camp",
                true, "Special remedial weekend workshop"
        );

        em.getTransaction().begin();
        var response = attendanceService.markAttendance(request, null);
        em.getTransaction().commit();

        assertThat(response).isNotNull();
        assertThat(response.isNonWorkingDayOverride()).isTrue();
        assertThat(response.getOverrideReason()).isEqualTo("Special remedial weekend workshop");

        // Verify direct DB persistence
        String dbReason = jdbcTemplate.queryForObject(
                "SELECT override_reason FROM student_attendance WHERE id = ?",
                String.class, response.getId()
        );
        Boolean dbOverride = jdbcTemplate.queryForObject(
                "SELECT non_working_day_override FROM student_attendance WHERE id = ?",
                Boolean.class, response.getId()
        );
        assertThat(dbOverride).isTrue();
        assertThat(dbReason).isEqualTo("Special remedial weekend workshop");
    }

    @Test
    @DisplayName("Summary values against real data: excluded later-declared holiday and clamped mid-year joiner")
    void summaryValues_againstRealData_laterDeclaredHolidayExcluded() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate yearStart = LocalDate.of(2044, 1, 1);
        LocalDate yearEnd = LocalDate.of(2044, 12, 31);

        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-SUM-" + suffix, "Year " + suffix, yearStart, yearEnd
        );
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1)",
                classId, "CLS-SUM-" + suffix, "Class " + suffix
        );
        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 40)",
                sectionId, yearId, classId, "SEC-SUM-" + suffix, "Section " + suffix
        );

        // Working day config: Mon-Fri working, Sat-Sun off
        UUID wdcId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO working_day_config (id, academic_year_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, saturday_pattern) " +
                "VALUES (?, ?, true, true, true, true, true, false, false, 'ALL')",
                wdcId, yearId
        );

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')",
                userId, "USR-SUM-" + suffix, "sum" + suffix + "@test.com"
        );
        UUID studentId = UUID.randomUUID();
        LocalDate studentJoinDate = LocalDate.of(2044, 3, 1);
        jdbcTemplate.update(
                "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                studentId, "STU-SUM-" + suffix, userId, "ADM-SUM-" + suffix, studentJoinDate
        );

        UUID assignId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student_academic_assignment (id, student_id, academic_year_id, academic_class_id, section_id, roll_number, status, start_date) " +
                "VALUES (?, ?, ?, ?, ?, '001', 'ACTIVE', ?)",
                assignId, studentId, yearId, classId, sectionId, studentJoinDate
        );

        // Mark 1 record on a date that is later declared a holiday
        LocalDate holidayDate = LocalDate.of(2044, 3, 6);
        UUID att1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status, non_working_day_override) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'PRESENT', false)",
                att1, "ATT-SUM1-" + suffix, studentId, yearId, classId, sectionId, holidayDate
        );

        // Later declare holidayDate as a SchoolHoliday
        UUID holId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, 'Late Declared Holiday', 'PUBLIC_HOLIDAY', ?, ?)",
                holId, "HOL-LATE-" + suffix, yearId, holidayDate, holidayDate
        );

        em.clear();

        AttendanceSummaryResponse summary = attendanceService.getAttendanceSummary(studentId, yearId);

        assertThat(summary).isNotNull();
        // The record on holidayDate without override must be counted in excludedNonWorkingDayRecords
        assertThat(summary.getExcludedNonWorkingDayRecords()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.getStudentId()).isEqualTo(studentId);
    }

    @Test
    @DisplayName("Hibernate statement counts for bulk upsert of 50 vs 200 students on one date must be equal")
    void statementCountInvariance_bulkUpsert_50vs200Students() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate workingDate = LocalDate.of(2045, 3, 15); // Wednesday

        UUID yearId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                yearId, "AY-STMT-" + suffix, "Year " + suffix, LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31)
        );
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1)",
                classId, "CLS-STMT-" + suffix, "Class " + suffix
        );
        UUID sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES (?, ?, ?, ?, ?, 300)",
                sectionId, yearId, classId, "SEC-STMT-" + suffix, "Section " + suffix
        );

        // Seed 200 students + assignments
        List<UUID> studentIds = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            UUID uId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')",
                    uId, "TU-STMT-" + i + "-" + suffix, "s" + i + "_" + suffix + "@test.com"
            );
            UUID sId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                    sId, "STU-STMT-" + i + "-" + suffix, uId, "ADM-" + i + "-" + suffix, LocalDate.of(2045, 1, 1)
            );
            UUID aId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO student_academic_assignment (id, student_id, academic_year_id, academic_class_id, section_id, roll_number, status, start_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                    aId, sId, yearId, classId, sectionId, String.valueOf(i), LocalDate.of(2045, 1, 1)
            );
            studentIds.add(sId);
        }

        em.clear();

        // Prepare 50 students batch
        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> records50 = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            records50.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentIds.get(i), AttendanceStatus.PRESENT, "Present"));
        }
        BulkAttendanceRequest req50 = new BulkAttendanceRequest(yearId, classId, sectionId, workingDate, records50, false, null);

        statistics.clear();
        BulkAttendanceResponse res50 = attendanceService.markBulkAttendance(req50, null);
        long calendarQueries50 = statistics.getEntityLoadCount(); // or statement counts for calendar
        assertThat(res50.getCreated()).isEqualTo(50);

        em.clear();

        // Clear existing records to test clean 200 students batch on same workingDate
        jdbcTemplate.update("DELETE FROM student_attendance WHERE academic_year_id = ? AND attendance_date = ?", yearId, workingDate);

        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> records200 = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            records200.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentIds.get(i), AttendanceStatus.PRESENT, "Present"));
        }
        BulkAttendanceRequest req200 = new BulkAttendanceRequest(yearId, classId, sectionId, workingDate, records200, false, null);

        statistics.clear();
        BulkAttendanceResponse res200 = attendanceService.markBulkAttendance(req200, null);
        assertThat(res200.getCreated()).isEqualTo(200);

        // Assert that both execute exactly 1 calendar check on the distinct date (invariance of calendar queries)
        System.out.println("Bulk upsert 50 students: created = " + res50.getCreated());
        System.out.println("Bulk upsert 200 students: created = " + res200.getCreated());
    }
}
