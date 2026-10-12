package com.vidya.integration;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import com.vidya.tenantdata.calendar.mapper.HolidayMapper;
import com.vidya.tenantdata.calendar.mapper.WorkingDayConfigMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.repository.WorkingDayConfigRepository;
import com.vidya.tenantdata.calendar.service.CalendarServiceImpl;
import com.vidya.tenantdata.calendar.service.HolidayServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.time.LocalDate;
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

@DisplayName("AcademicCalendar — PostgreSQL Database Constraints & Concurrency Integration Tests")
class CalendarPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static HolidayServiceImpl holidayService;
    private static CalendarServiceImpl calendarService;

    @BeforeAll
    static void initServices() {
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
        SchoolHolidayRepository holidayRepo = repoFactory.getRepository(SchoolHolidayRepository.class);
        WorkingDayConfigRepository configRepo = repoFactory.getRepository(WorkingDayConfigRepository.class);
        AcademicYearRepository ayRepo = repoFactory.getRepository(AcademicYearRepository.class);

        HolidayMapper holidayMapper = new HolidayMapper();
        WorkingDayConfigMapper configMapper = new WorkingDayConfigMapper();

        holidayService = new HolidayServiceImpl(holidayRepo, ayRepo, holidayMapper);
        calendarService = new CalendarServiceImpl(ayRepo, configRepo, holidayRepo, configMapper);
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

    private UUID createAcademicYear(String code, String name, LocalDate start, LocalDate end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) " +
                "VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                id, code, name, start, end
        );
        return id;
    }

    @Test
    @DisplayName("V15 Schema & Check Constraint chk_holiday_dates — start_date > end_date is rejected")
    void holiday_checkConstraint_startDateAfterEndDate_isRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-H1-" + suffix, "AY " + suffix, LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "HOL-1-" + suffix, yearId, "Bad Dates", "PUBLIC_HOLIDAY",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 10)
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V15 Schema & Partial Unique Index idx_school_holiday_unique — Duplicate holiday name on same start date is blocked")
    void holiday_partialUniqueIndex_duplicateNameAndStartDate_isBlocked() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-H2-" + suffix, "AY " + suffix, LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));

        // First holiday succeeds
        jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "HOL-A-" + suffix, yearId, "Diwali", "PUBLIC_HOLIDAY",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)
        );

        // Duplicate name (case-insensitive) on same start date fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "HOL-B-" + suffix, yearId, "diwali", "PUBLIC_HOLIDAY",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V15 Schema & Partial Unique Index idx_working_day_config_ay — Only one config row per academic year")
    void workingDayConfig_partialUniqueIndex_singleConfigPerYear() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-C1-" + suffix, "AY " + suffix, LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));

        // First config succeeds
        jdbcTemplate.update(
                "INSERT INTO working_day_config (id, academic_year_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, saturday_pattern) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), yearId, true, true, true, true, true, true, false, "ALL"
        );

        // Second config for same academic year fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO working_day_config (id, academic_year_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, saturday_pattern) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), yearId, true, true, true, true, true, false, false, "ALL"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Concurrent Holiday Creation — Overlapping dates race: exactly 1 wins through service, others get DuplicateResourceException")
    void concurrentHolidayCreates_overlappingRace_exactlyOneWins() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-CR-" + suffix, "AY " + suffix, LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        List<Throwable> otherExceptions = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                try {
                    JpaRepositoryFactory threadRepoFactory = new JpaRepositoryFactory(threadEm);
                    SchoolHolidayRepository threadHolidayRepo = threadRepoFactory.getRepository(SchoolHolidayRepository.class);
                    AcademicYearRepository threadAyRepo = threadRepoFactory.getRepository(AcademicYearRepository.class);
                    HolidayServiceImpl threadHolidayService = new HolidayServiceImpl(threadHolidayRepo, threadAyRepo, new HolidayMapper());

                    latch.await();
                    threadEm.getTransaction().begin();
                    threadHolidayService.createHoliday(new CreateHolidayRequest(
                            yearId,
                            "Festival Break",
                            HolidayType.VACATION,
                            LocalDate.of(2026, 10, 15),
                            LocalDate.of(2026, 10, 20),
                            "Concurrent break"
                    ));
                    threadEm.getTransaction().commit();
                    successCount.incrementAndGet();
                } catch (DuplicateResourceException e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                    duplicateCount.incrementAndGet();
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
        assertThat(duplicateCount.get()).isEqualTo(3);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM school_holiday WHERE academic_year_id = ? AND deleted_at IS NULL",
                Integer.class, yearId
        );
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Saturday Pattern Calculation against Real Holidays in DB")
    void saturdayPattern_againstRealData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-PAT-" + suffix, "AY " + suffix, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Configure FIRST_THIRD_FIFTH Saturdays working via JDBC
        jdbcTemplate.update(
                "INSERT INTO working_day_config (id, academic_year_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, saturday_pattern) " +
                "VALUES (?, ?, true, true, true, true, true, true, false, 'FIRST_THIRD_FIFTH')",
                UUID.randomUUID(), yearId
        );

        // Create holiday on Aug 15 (3rd Saturday) via JDBC
        jdbcTemplate.update(
                "INSERT INTO school_holiday (id, holiday_code, academic_year_id, name, holiday_type, start_date, end_date) " +
                "VALUES (?, ?, ?, 'Independence Day', 'PUBLIC_HOLIDAY', ?, ?)",
                UUID.randomUUID(), "HOL-AUG15-" + suffix, yearId, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15)
        );

        em.clear();

        // Aug 1 (1st Saturday) -> WORKING
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 1)).status()).isEqualTo(DayStatus.WORKING);

        // Aug 8 (2nd Saturday) -> WEEKLY_OFF
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 8)).status()).isEqualTo(DayStatus.WEEKLY_OFF);

        // Aug 15 (3rd Saturday) -> HOLIDAY (overrides working Saturday)
        DayStatusResponse aug15 = calendarService.getDayStatus(LocalDate.of(2026, 8, 15));
        assertThat(aug15.status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(aug15.holidayName()).isEqualTo("Independence Day");

        // Aug 22 (4th Saturday) -> WEEKLY_OFF
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 22)).status()).isEqualTo(DayStatus.WEEKLY_OFF);

        // Aug 29 (5th Saturday) -> WORKING
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 29)).status()).isEqualTo(DayStatus.WORKING);
    }
}
