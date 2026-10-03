/**
 * Tenant operational data domain — Phase 02+.
 *
 * <p>This package contains JPA entities and repositories that live in
 * each tenant's dedicated PostgreSQL database (not the master DB).
 *
 * <p>All repositories here must use:
 * <pre>
 *   {@code @Transactional(transactionManager = "tenantTransactionManager")}
 * </pre>
 *
 * <p>Phase 01 entities are deliberately absent. The {@link com.vidya.config.database.TenantDataSourceConfig}
 * scans this package and will find entities as they are added in Phase 02:
 *
 * <ul>
 *   <li>Phase 02: academic, student, staff</li>
 *   <li>Phase 03: attendance, timetable</li>
 *   <li>Phase 04: fees, examination, results</li>
 *   <li>Phase 05+: transport, library, communication</li>
 * </ul>
 */
package com.vidya.tenantdata;
