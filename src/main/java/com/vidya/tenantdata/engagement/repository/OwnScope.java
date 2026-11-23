package com.vidya.tenantdata.engagement.repository;

import java.util.Set;
import java.util.UUID;

/**
 * What a user without full (school-wide) access may see. The service builds it from the caller's own records and
 * passes {@code null} for full access; repositories turn it into SQL. Each search uses the parts it needs.
 *
 * @param actorId    the signed-in tenant user (author of drafts and activities)
 * @param staffIds   staff records of the user (teacher of homework)
 * @param sectionIds sections the user may see
 * @param classIds   classes the user may see
 */
public record OwnScope(UUID actorId, Set<UUID> staffIds, Set<UUID> sectionIds, Set<UUID> classIds) {
}
