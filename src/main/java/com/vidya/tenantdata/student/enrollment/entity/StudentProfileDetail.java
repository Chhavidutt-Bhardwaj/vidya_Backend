package com.vidya.tenantdata.student.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** School-specific details of a student (1:1 with {@code student}); personal data lives in the person profile. */
@Entity
@Table(name = "student_profile_detail")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfileDetail {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(length = 60)
    private String nationality = "Indian";

    @Column(name = "mother_tongue", length = 60)
    private String motherTongue;

    @Column(length = 50)
    private String house;

    @Column(name = "previous_school", length = 200)
    private String previousSchool;

    @Column(name = "previous_class", length = 50)
    private String previousClass;

    @Column(name = "tc_number_in", length = 60)
    private String tcNumberIn;

    @Column(name = "tc_number_out", length = 60)
    private String tcNumberOut;

    @Column(name = "medical_notes", length = 500)
    private String medicalNotes;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "leaving_type", length = 20)
    private String leavingType;

    @Column(name = "leaving_date")
    private LocalDate leavingDate;

    @Column(name = "leaving_reason", length = 255)
    private String leavingReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;

    public StudentProfileDetail(UUID studentId) {
        this.studentId = studentId;
    }
}
