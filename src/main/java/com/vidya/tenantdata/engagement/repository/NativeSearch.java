package com.vidya.tenantdata.engagement.repository;

import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builder for the engagement repositories' dynamic native searches: optional filters appended as {@code AND} clauses. */
final class NativeSearch {

    /** Placeholder for an empty {@code IN (...)} list, which PostgreSQL rejects; matches nothing. */
    private static final UUID NONE = new UUID(0, 0);

    private final StringBuilder sql;
    private final Map<String, Object> params = new HashMap<>();

    NativeSearch(String base) {
        this.sql = new StringBuilder(base);
    }

    /** Appends {@code AND clause} and binds {@code value} to {@code name}, only when the value is non-null. */
    NativeSearch filter(String clause, String name, Object value) {
        if (value != null) {
            sql.append(" AND ").append(clause);
            params.put(name, value);
        }
        return this;
    }

    NativeSearch and(String clause) {
        sql.append(" AND ").append(clause);
        return this;
    }

    NativeSearch param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    /** Binds an id set for an {@code IN (:name)} clause; an empty set matches nothing. */
    NativeSearch ids(String name, Set<UUID> ids) {
        params.put(name, ids == null || ids.isEmpty() ? Set.of(NONE) : ids);
        return this;
    }

    @SuppressWarnings("unchecked")
    <T> List<T> list(EntityManager em, Class<T> type, String orderBy, int limit) {
        var q = em.createNativeQuery(sql + " ORDER BY " + orderBy, type);
        params.forEach(q::setParameter);
        q.setMaxResults(limit);
        return q.getResultList();
    }
}
