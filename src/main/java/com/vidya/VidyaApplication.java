package com.vidya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;

/**
 * Vidya — School Management System
 *
 * <p>Modular monolith with database-per-tenant multi-tenancy.
 *
 * <p>Excluded auto-configurations (Spring Boot 4.x module packages):
 * <ul>
 *   <li>FlywayAutoConfiguration — managed manually via MasterFlywayConfig and TenantFlywayConfig</li>
 *   <li>MongoAutoConfiguration — MongoDB reserved for future use; not active in Phase 01</li>
 *   <li>DataMongoAutoConfiguration — no tenant Mongo repositories in Phase 01</li>
 * </ul>
 */
@SpringBootApplication(exclude = {
        FlywayAutoConfiguration.class,
        MongoAutoConfiguration.class,
        DataMongoAutoConfiguration.class
})
public class VidyaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VidyaApplication.class, args);
    }
}
