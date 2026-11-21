package com.vidya.tenantdata.examination.grading;

import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ready-made grading scales commonly used by Indian schools. They are starting points: the school
 * creates one, then edits bands or pass marks to match its board circular.
 */
public final class GradingPresets {

    public record Preset(String key, String name, String description, Set<ClassStage> suggestedFor, List<GradingScaleTierDto> tiers) {
    }

    private static final List<Preset> ALL = List.of(
            new Preset("CBSE_9_POINT", "CBSE 9-point scale (IX–XII)",
                    "A1–E with grade points; E (below 33) needs improvement / not passed.",
                    Set.of(ClassStage.SECONDARY, ClassStage.SENIOR_SECONDARY), tiers(
                            t("A1", 91, 100, 10, "Outstanding", true),
                            t("A2", 81, 90, 9, "Excellent", true),
                            t("B1", 71, 80, 8, "Very good", true),
                            t("B2", 61, 70, 7, "Good", true),
                            t("C1", 51, 60, 6, "Above average", true),
                            t("C2", 41, 50, 5, "Average", true),
                            t("D", 33, 40, 4, "Pass", true),
                            t("E", 0, 32, 0, "Needs improvement", false))),
            new Preset("CBSE_8_POINT", "8-point scale (I–VIII)",
                    "A1–E2 used for classes I–VIII; E1/E2 marked not passing, change if your school follows no-detention.",
                    Set.of(ClassStage.PRIMARY, ClassStage.MIDDLE), tiers(
                            t("A1", 91, 100, 10, "Outstanding", true),
                            t("A2", 81, 90, 9, "Excellent", true),
                            t("B1", 71, 80, 8, "Very good", true),
                            t("B2", 61, 70, 7, "Good", true),
                            t("C1", 51, 60, 6, "Above average", true),
                            t("C2", 41, 50, 5, "Average", true),
                            t("D", 33, 40, 4, "Pass", true),
                            t("E1", 21, 32, 0, "Needs improvement", false),
                            t("E2", 0, 20, 0, "Needs improvement", false))),
            new Preset("PRE_PRIMARY_DESCRIPTIVE", "Descriptive scale (pre-primary)",
                    "Descriptive levels instead of pass/fail for Nursery–UKG.",
                    Set.of(ClassStage.PRE_PRIMARY), tiers(
                            t("A+", 90, 100, 0, "Outstanding", true),
                            t("A", 75, 89, 0, "Excellent", true),
                            t("B", 60, 74, 0, "Very good", true),
                            t("C", 45, 59, 0, "Good", true),
                            t("D", 0, 44, 0, "Needs support", true))),
            new Preset("PERCENTAGE_DIVISION", "Percentage divisions",
                    "Distinction / First / Second / Third division, fail below 33 %.",
                    Set.of(), tiers(
                            t("DIST", 75, 100, 0, "Distinction", true),
                            t("I", 60, 74, 0, "First division", true),
                            t("II", 45, 59, 0, "Second division", true),
                            t("III", 33, 44, 0, "Third division", true),
                            t("F", 0, 32, 0, "Fail", false))),
            new Preset("IB_1_7", "IB 1–7 scale (MYP / DP)",
                    "IB achievement levels from the criteria total (MYP: out of 32, shown here as a percentage). "
                            + "Check the boundaries against the current IB guide.",
                    Set.of(), tiers(
                            t("7", 85, 100, 7, "Excellent", true),
                            t("6", 72, 84, 6, "Very good", true),
                            t("5", 57, 71, 5, "Good", true),
                            t("4", 44, 56, 4, "Satisfactory", true),
                            t("3", 29, 43, 3, "Mediocre", true),
                            t("2", 16, 28, 2, "Poor", false),
                            t("1", 0, 15, 1, "Very poor", false))),
            new Preset("IGCSE_A_STAR_G", "IGCSE A*–G scale",
                    "Indicative school-level boundaries; Cambridge sets real boundaries per paper and series. U = ungraded.",
                    Set.of(), tiers(
                            t("A*", 90, 100, 0, "", true),
                            t("A", 80, 89, 0, "", true),
                            t("B", 70, 79, 0, "", true),
                            t("C", 60, 69, 0, "", true),
                            t("D", 50, 59, 0, "", true),
                            t("E", 40, 49, 0, "", true),
                            t("F", 30, 39, 0, "", true),
                            t("G", 20, 29, 0, "", true),
                            t("U", 0, 19, 0, "Ungraded", false))));

    private GradingPresets() {
    }

    public static List<Preset> all() {
        return ALL;
    }

    public static Optional<Preset> find(String key) {
        return ALL.stream().filter(p -> p.key().equalsIgnoreCase(key)).findFirst();
    }

    /** Preset normally used for a stage. */
    public static Preset forStage(ClassStage stage) {
        return ALL.stream().filter(p -> p.suggestedFor().contains(stage)).findFirst().orElse(ALL.get(0));
    }

    private static GradingScaleTierDto t(String grade, int min, int max, double point, String description, boolean passing) {
        return GradingScaleTierDto.builder().grade(grade).minPercentage(new BigDecimal(min + ".00"))
                .maxPercentage(new BigDecimal(max + ".00")).gradePoint(BigDecimal.valueOf(point))
                .description(description).isPassing(passing).build();
    }

    private static List<GradingScaleTierDto> tiers(GradingScaleTierDto... t) {
        List<GradingScaleTierDto> out = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            t[i].setSortOrder(i + 1);
            out.add(t[i]);
        }
        return List.copyOf(out);
    }
}
