package com.vidya.master.school.entity;

/**
 * Educational board affiliation.
 *
 * <p>The values intentionally differ from {@link BoardAffiliationType} for historical reasons
 * ({@code STATE} vs {@code STATE_BOARD}, {@code IGCSE} vs {@code CAMBRIDGE_IGCSE}). Always convert
 * with {@link #toAffiliationType()}; never with {@code BoardAffiliationType.valueOf(board.name())},
 * which silently loses State-board and IGCSE affiliations.
 */
public enum SchoolBoard {
    CBSE,
    ICSE,
    HBSE,
    STATE,
    IB,
    IGCSE,
    OTHER;

    /** Explicit, total mapping to the affiliation-record board type. */
    public BoardAffiliationType toAffiliationType() {
        return switch (this) {
            case CBSE -> BoardAffiliationType.CBSE;
            case ICSE -> BoardAffiliationType.ICSE;
            case HBSE -> BoardAffiliationType.HBSE;
            case STATE -> BoardAffiliationType.STATE_BOARD;
            case IB -> BoardAffiliationType.IB;
            case IGCSE -> BoardAffiliationType.CAMBRIDGE_IGCSE;
            case OTHER -> BoardAffiliationType.OTHER;
        };
    }
}
