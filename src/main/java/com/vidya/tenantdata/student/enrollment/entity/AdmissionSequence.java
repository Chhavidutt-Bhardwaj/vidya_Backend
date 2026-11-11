package com.vidya.tenantdata.student.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Next admission number for an academic year (or the whole school when {@code academicYearId} is null). */
@Entity
@Table(name = "admission_sequence")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(nullable = false, length = 20)
    private String prefix = "";

    @Column(name = "next_number", nullable = false)
    private int nextNumber = 1;

    @Column(nullable = false)
    private int padding = 4;

    @Version
    private Long version = 0L;

    public AdmissionSequence(UUID academicYearId, String prefix, int nextNumber, int padding) {
        this.academicYearId = academicYearId;
        this.prefix = prefix != null ? prefix : "";
        this.nextNumber = nextNumber;
        this.padding = padding;
    }

    public String format(int number) {
        return prefix + (padding > 0 ? String.format("%0" + padding + "d", number) : String.valueOf(number));
    }

    /** The number this sequence would give next, without consuming it. */
    public String peek() {
        return format(nextNumber);
    }

    public String take() {
        return format(nextNumber++);
    }
}
