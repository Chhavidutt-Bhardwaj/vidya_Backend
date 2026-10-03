package com.vidya.common.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Utility for generating unique, human-readable codes.
 *
 * <p>Codes are used for:
 * <ul>
 *   <li>{@code tenant_code} — e.g. {@code abc12345}</li>
 *   <li>{@code school_code} — e.g. {@code SCH-XYZ789}</li>
 *   <li>{@code onboarding_code} — e.g. {@code ONB-A1B2C3D4}</li>
 *   <li>{@code subscription_code} — e.g. {@code SUB-20260926-XXXXX}</li>
 *   <li>{@code transaction_code} — e.g. {@code TXN-20260926-XXXXX}</li>
 * </ul>
 *
 * <p>Uses {@link SecureRandom} for unpredictability. Not UUID-based because
 * codes are user-facing identifiers that should be readable and copyable.
 */
public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars

    private CodeGenerator() {}

    /**
     * Generates a random alphanumeric code of the specified length.
     * Characters: uppercase A-Z (excluding I, O) and digits 2-9 (excluding 0, 1).
     */
    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a tenant code — 8 lowercase alphanumeric characters.
     * Used as part of the database name: {@code vidya_tenant_<tenantCode>}.
     *
     * <p>Example: {@code abc12345}
     */
    public static String tenantCode() {
        return generate(8).toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a school code with prefix.
     * Example: {@code SCH-A1B2C3D4}
     */
    public static String schoolCode() {
        return "SCH-" + generate(8);
    }

    /**
     * Generates an onboarding code with prefix.
     * Example: {@code ONB-A1B2C3D4}
     */
    public static String onboardingCode() {
        return "ONB-" + generate(8);
    }

    /**
     * Generates a community code with prefix.
     * Example: {@code COM-A1B2C3D4}
     */
    public static String communityCode() {
        return "COM-" + generate(8);
    }

    /**
     * Generates a subscription code with prefix.
     * Example: {@code SUB-A1B2C3D4}
     */
    public static String subscriptionCode() {
        return "SUB-" + generate(10);
    }

    /**
     * Generates a payment transaction code with prefix.
     * Example: {@code TXN-A1B2C3D4E5}
     */
    public static String transactionCode() {
        return "TXN-" + generate(12);
    }

    /**
     * Generates a school branch code with prefix.
     * Example: {@code BRN-A1B2C3D4}
     */
    public static String branchCode() {
        return "BRN-" + generate(8);
    }

    /**
     * Generates a community member code with prefix.
     * Example: {@code MEM-A1B2C3D4}
     */
    public static String memberCode() {
        return "MEM-" + generate(8);
    }

    /**
     * Generates a user code with prefix.
     * Example: {@code USR-A1B2C3D4}
     */
    public static String userCode() {
        return "USR-" + generate(8);
    }

    /**
     * Generates a provisioning code with prefix.
     * Example: {@code PRV-A1B2C3D4}
     */
    public static String provisioningCode() {
        return "PRV-" + generate(10);
    }

    /**
     * Generates a student code with prefix.
     * Example: {@code STU-A1B2C3D4}
     */
    public static String studentCode() {
        return "STU-" + generate(8);
    }

    /**
     * Generates a staff code with prefix.
     * Example: {@code STF-A1B2C3D4}
     */
    public static String staffCode() {
        return "STF-" + generate(8);
    }

    /**
     * Generates a subject code with prefix.
     * Example: {@code SUB-A1B2C3D4}
     */
    public static String subjectCode() {
        return "SUB-" + generate(8);
    }

    /**
     * Generates a curriculum code with prefix.
     * Example: {@code CUR-A1B2C3D4}
     */
    public static String curriculumCode() {
        return "CUR-" + generate(8);
    }

    /**
     * Generates a teacher subject assignment code with prefix.
     * Example: {@code TSA-A1B2C3D4}
     */
    public static String teacherAssignmentCode() {
        return "TSA-" + generate(8);
    }

    /**
     * Generates a class subject assignment code with prefix.
     * Example: {@code CSA-A1B2C3D4}
     */
    public static String classSubjectAssignmentCode() {
        return "CSA-" + generate(8);
    }

    /**
     * Generates a room code with prefix.
     * Example: {@code ROM-A1B2C3D4}
     */
    public static String roomCode() {
        return "ROM-" + generate(8);
    }

    /**
     * Generates a bell schedule code with prefix.
     * Example: {@code BSC-A1B2C3D4}
     */
    public static String bellScheduleCode() {
        return "BSC-" + generate(8);
    }

    /**
     * Generates a period code with prefix.
     * Example: {@code PRD-A1B2C3D4}
     */
    public static String periodCode() {
        return "PRD-" + generate(8);
    }

    /**
     * Generates a master timetable code with prefix.
     * Example: {@code TTB-A1B2C3D4}
     */
    public static String timetableCode() {
        return "TTB-" + generate(8);
    }

    /**
     * Generates a timetable entry code with prefix.
     * Example: {@code TTE-A1B2C3D4}
     */
    public static String timetableEntryCode() {
        return "TTE-" + generate(8);
    }

    /**
     * Generates an enrollment code with prefix.
     * Example: {@code ENR-A1B2C3D4}
     */
    public static String enrollmentCode() {
        return "ENR-" + generate(8);
    }

    /**
     * Generates a student subject enrollment code with prefix.
     * Example: {@code SSE-A1B2C3D4}
     */
    public static String studentSubjectEnrollmentCode() {
        return "SSE-" + generate(8);
    }

    /**
     * Generates a student guardian code with prefix.
     * Example: {@code GRD-A1B2C3D4}
     */
    public static String guardianCode() {
        return "GRD-" + generate(8);
    }

    /**
     * Generates a class teacher assignment code with prefix.
     * Example: {@code CTA-A1B2C3D4}
     */
    public static String classTeacherAssignmentCode() {
        return "CTA-" + generate(8);
    }

    /**
     * Generates a student attendance record code with prefix.
     * Example: {@code ATT-A1B2C3D4}
     */
    public static String attendanceCode() {
        return "ATT-" + generate(8);
    }
}
