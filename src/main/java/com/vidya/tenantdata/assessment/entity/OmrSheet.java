package com.vidya.tenantdata.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** One scanned sheet: the student it belongs to, the marked answers and the score. */
@Entity
@Table(name = "omr_sheet")
@Getter
@Setter
@NoArgsConstructor
public class OmrSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_no")
    private int rowNo;

    @Column(name = "roll_number")
    private String rollNumber;

    @Column(name = "student_code")
    private String studentCode;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "set_label")
    private String setLabel;

    @Column(name = "raw_answers", nullable = false, columnDefinition = "text")
    private String rawAnswers;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "correct")
    private int correct;

    @Column(name = "wrong")
    private int wrong;

    @Column(name = "blank")
    private int blank;

    @Column(name = "invalid")
    private int invalid;

    @Column(name = "error")
    private String error;
}
