package com.vidya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test verifying Room partial unique index (idx_room_name_active),
 * case-insensitivity, and soft-delete coexistence against real PostgreSQL.
 */
@DisplayName("Room — PostgreSQL Partial Unique Index and Soft Delete Integration Tests")
class RoomPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    @Test
    @DisplayName("Room — idx_room_name_active rejects case-insensitive duplicate active room name, allows re-creation after soft-delete")
    void roomUniqueName_caseInsensitiveActiveIndex_softDeleteBehavior() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String roomName = "Physics Lab " + suffix;
        UUID roomId1 = UUID.randomUUID();

        // 1. Create active room
        int inserted1 = jdbcTemplate.update(
                "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, ?, ?, ?)",
                roomId1, "RM-1-" + suffix, roomName, "SCIENCE_LAB", 35, "ACTIVE"
        );
        assertThat(inserted1).isEqualTo(1);

        // 2. Attempt to create another active room with same name in lowercase -> must fail with duplicate key
        UUID roomId2 = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, ?, ?, ?)",
                        roomId2, "RM-2-" + suffix, roomName.toLowerCase(), "SCIENCE_LAB", 40, "ACTIVE"
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_room_name_active.*|.*duplicate key value.*");

        // 3. Soft-delete the first room
        int softDeleted = jdbcTemplate.update(
                "UPDATE room SET deleted_at = NOW(), status = 'DELETED' WHERE id = ?", roomId1
        );
        assertThat(softDeleted).isEqualTo(1);

        // 4. Re-create a room with the exact same name -> must succeed
        UUID roomId3 = UUID.randomUUID();
        int inserted3 = jdbcTemplate.update(
                "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, ?, ?, ?)",
                roomId3, "RM-3-" + suffix, roomName, "SCIENCE_LAB", 45, "ACTIVE"
        );
        assertThat(inserted3).isEqualTo(1);

        // 5. Query active rooms - only roomId3 should be active
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE LOWER(name) = LOWER(?) AND deleted_at IS NULL",
                Integer.class, roomName
        );
        assertThat(activeCount).isEqualTo(1);

        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE LOWER(name) = LOWER(?)",
                Integer.class, roomName
        );
        assertThat(totalCount).isEqualTo(2);
    }
}
