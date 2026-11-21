package com.vidya.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layering rule: only classes in a {@code ..repository..} package may use EntityManager / JdbcTemplate.
 * Services call repositories; they never build SQL or touch the persistence context themselves.
 *
 * <p>{@link #LEGACY} lists files that broke the rule before it was introduced. When you move a file's
 * queries into a repository, delete it from the list; the test fails if a listed file is already clean,
 * so the list can only shrink. Never add new entries.
 */
class DataAccessLayeringTest {

    private static final Path SRC = Path.of("src/main/java");
    private static final Pattern RAW_ACCESS = Pattern.compile("\\b(EntityManager|JdbcTemplate|NamedParameterJdbcTemplate)\\b");

    /** Infrastructure that legitimately needs raw access (datasource wiring, tenant DB provisioning). */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "com/vidya/config/",
            "com/vidya/tenant/provisioning/");

    private static final Set<String> LEGACY = Set.of(
            "com/vidya/tenantdata/academic/config/service/LessonPlanService.java",
            "com/vidya/tenantdata/academic/config/service/TeacherAllocationService.java",
            "com/vidya/tenantdata/academic/config/service/TeachingScope.java",
            "com/vidya/tenantdata/academic/setup/service/AcademicSetupService.java",
            "com/vidya/tenantdata/academic/setup/service/TimetableCarryOverService.java",
            "com/vidya/tenantdata/academic/setup/service/YearSetupActionService.java",
            "com/vidya/tenantdata/academic/structure/service/AcademicStructureService.java",
            "com/vidya/tenantdata/academic/structure/service/StructureTemplateService.java",
            "com/vidya/tenantdata/assessment/result/ReportCardEngine.java",
            "com/vidya/tenantdata/operations/service/OperationsOverviewService.java",
            "com/vidya/tenantdata/readiness/service/ReadinessService.java",
            "com/vidya/tenantdata/security/OwnRecordsResolver.java",
            "com/vidya/tenantdata/staff/setup/service/StaffOnboardingService.java",
            "com/vidya/tenantdata/staff/setup/service/StaffOrgService.java",
            "com/vidya/tenantdata/user/account/SchoolContext.java");

    @Test
    @DisplayName("EntityManager / JdbcTemplate are used only in repository packages")
    void rawDataAccessOnlyInRepositories() throws IOException {
        Set<String> offenders = offenders();

        Set<String> newOffenders = new TreeSet<>(offenders);
        newOffenders.removeAll(LEGACY);
        assertThat(newOffenders)
                .as("Move these queries into a @Repository class under a ..repository.. package")
                .isEmpty();

        Set<String> fixed = new TreeSet<>(LEGACY);
        fixed.removeAll(offenders);
        assertThat(fixed)
                .as("These files no longer use raw data access; remove them from DataAccessLayeringTest.LEGACY")
                .isEmpty();
    }

    private static Set<String> offenders() throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String rel = SRC.relativize(p).toString().replace('\\', '/');
                if (rel.contains("/repository/") || ALLOWED_PREFIXES.stream().anyMatch(rel::startsWith)) {
                    continue;
                }
                if (RAW_ACCESS.matcher(Files.readString(p)).find()) {
                    out.add(rel);
                }
            }
        }
        return out;
    }
}
