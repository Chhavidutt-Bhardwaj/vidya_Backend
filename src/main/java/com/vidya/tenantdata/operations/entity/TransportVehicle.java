package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.VehicleStatus;
import com.vidya.tenantdata.operations.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A school vehicle with its crew and the dates its papers run out. */
@Entity
@Table(name = "transport_vehicle")
@Getter
@Setter
@NoArgsConstructor
public class TransportVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_code", nullable = false, updatable = false, length = 50)
    private String vehicleCode;

    @Column(name = "registration_no", nullable = false, length = 30)
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType = VehicleType.BUS;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "driver_staff_id")
    private UUID driverStaffId;

    @Column(name = "attendant_staff_id")
    private UUID attendantStaffId;

    @Column(name = "driver_name", length = 150)
    private String driverName;

    @Column(name = "driver_phone", length = 20)
    private String driverPhone;

    @Column(name = "insurance_valid_till")
    private LocalDate insuranceValidTill;

    @Column(name = "fitness_valid_till")
    private LocalDate fitnessValidTill;

    @Column(name = "permit_valid_till")
    private LocalDate permitValidTill;

    @Column(name = "puc_valid_till")
    private LocalDate pucValidTill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;
}
