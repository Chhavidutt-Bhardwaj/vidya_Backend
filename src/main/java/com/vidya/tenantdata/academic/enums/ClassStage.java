package com.vidya.tenantdata.academic.enums;

/** School stage of a class (NEP / CBSE bands). */
public enum ClassStage {
    /** Pre-Nursery, Nursery, LKG, UKG */
    PRE_PRIMARY,
    /** Classes I–V */
    PRIMARY,
    /** Classes VI–VIII */
    MIDDLE,
    /** Classes IX–X */
    SECONDARY,
    /** Classes XI–XII */
    SENIOR_SECONDARY;

    public static ClassStage forGradeLevel(int level) {
        if (level <= 0) {
            return PRE_PRIMARY;
        }
        if (level <= 5) {
            return PRIMARY;
        }
        if (level <= 8) {
            return MIDDLE;
        }
        return level <= 10 ? SECONDARY : SENIOR_SECONDARY;
    }
}
