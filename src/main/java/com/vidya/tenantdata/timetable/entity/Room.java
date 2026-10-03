package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Physical room/facility resource in the school tenant database.
 */
@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true, length = 50, updatable = false)
    private String roomCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 30)
    private RoomType roomType = RoomType.CLASSROOM;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "building", length = 100)
    private String building;

    @Column(name = "floor", length = 50)
    private String floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RoomStatus status = RoomStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Room(String roomCode, String name, RoomType roomType, Integer capacity, String building, String floor) {
        this.roomCode = roomCode;
        this.name = name != null ? name.trim() : null;
        this.roomType = roomType != null ? roomType : RoomType.CLASSROOM;
        this.capacity = capacity;
        this.building = building != null ? building.trim() : null;
        this.floor = floor != null ? floor.trim() : null;
        this.status = RoomStatus.ACTIVE;
    }

    public void updateStatus(RoomStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == RoomStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == RoomStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(RoomStatus.DELETED);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
