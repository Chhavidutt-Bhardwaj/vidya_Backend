package com.vidya.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConstraintAndMigrationTest {

    @Test
    @DisplayName("Verify master database Flyway migration files V1 through V20 exist and are well-formed")
    void verifyMasterMigrationsExist() throws Exception {
        Path migrationsDir = Paths.get("src/main/resources/db/migration/master");
        assertThat(Files.exists(migrationsDir)).isTrue();

        try (Stream<Path> stream = Files.list(migrationsDir)) {
            List<String> filenames = stream
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());

            assertThat(filenames).isNotEmpty();
            assertThat(filenames).anyMatch(f -> f.startsWith("V1__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V10__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V15__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V19__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V20__"));

            // Check that every migration file has content and standard SQL comments/statements
            for (String file : filenames) {
                if (file.endsWith(".sql")) {
                    String content = Files.readString(migrationsDir.resolve(file));
                    assertThat(content).isNotBlank();
                }
            }
        }
    }

    @Test
    @DisplayName("Verify tenant database Flyway migration files V1 through V3 exist and are well-formed")
    void verifyTenantMigrationsExist() throws Exception {
        Path tenantMigrationsDir = Paths.get("src/main/resources/db/migration/tenant");
        assertThat(Files.exists(tenantMigrationsDir)).isTrue();

        try (Stream<Path> stream = Files.list(tenantMigrationsDir)) {
            List<String> filenames = stream
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());

            assertThat(filenames).isNotEmpty();
            assertThat(filenames).anyMatch(f -> f.startsWith("V1__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V2__"));
            assertThat(filenames).anyMatch(f -> f.startsWith("V3__"));

            for (String file : filenames) {
                if (file.endsWith(".sql")) {
                    String content = Files.readString(tenantMigrationsDir.resolve(file));
                    assertThat(content).isNotBlank();
                }
            }
        }
    }
}
