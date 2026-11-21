package com.vidya.security.access;

/**
 * Which records of a {@link DataDomain} a user may touch (on top of holding the operation's permission).
 * <ul>
 *   <li>{@code ALL}: every record of the school. Platform and community accounts (whose school access is
 *       checked separately) and school users holding {@code <DOMAIN>_SCOPE_ALL}.</li>
 *   <li>{@code OWN}: only records linked to the user. A student: themself. A parent: their linked children.
 *       A teacher or staff member: students of the sections they are class teacher of or teach.</li>
 * </ul>
 * School users default to {@code OWN}, so a new custom role never sees the whole school by accident.
 */
public enum DataScope {
    ALL,
    OWN
}
