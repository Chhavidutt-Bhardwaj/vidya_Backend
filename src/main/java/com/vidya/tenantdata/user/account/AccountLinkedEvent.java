package com.vidya.tenantdata.user.account;

import java.util.UUID;

/** A person of the school now has a login (published inside the tenant transaction). */
public record AccountLinkedEvent(UUID tenantUserId, UUID masterUserId, UUID schoolId) {
}
