package com.vidya.tenantdata.reportcard.entity;

import com.vidya.tenantdata.subject.entity.Subject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_card_subject_result")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCardSubjectResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_card_id", nullable = false)
    private StudentReportCard reportCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "subject_code", nullable = false, length = 50)
    private String subjectCode;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "pass_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "theory_marks", precision = 6, scale = 2)
    private BigDecimal theoryMarks;

    @Column(name = "practical_marks", precision = 6, scale = 2)
    private BigDecimal practicalMarks;

    @Column(name = "marks_obtained", precision = 6, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "grade_point", precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "is_passing", nullable = false)
    private boolean isPassing = true;

    @Column(name = "is_absent", nullable = false)
    private boolean isAbsent = false;

    @Column(name = "is_exempt", nullable = false)
    private boolean isExempt = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReportCardSubjectResult(StudentReportCard reportCard, Subject subject, String subjectName,
                                   String subjectCode, BigDecimal maxMarks, BigDecimal passMarks,
                                   BigDecimal theoryMarks, BigDecimal practicalMarks, BigDecimal marksObtained,
                                   BigDecimal percentage, String grade, BigDecimal gradePoint,
                                   boolean isPassing, boolean isAbsent, boolean isExempt) {
        this.reportCard = reportCard;
        this.subject = subject;
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
        this.maxMarks = maxMarks;
        this.passMarks = passMarks;
        this.theoryMarks = theoryMarks;
        this.practicalMarks = practicalMarks;
        this.marksObtained = marksObtained;
        this.percentage = percentage;
        this.grade = grade;
        this.gradePoint = gradePoint;
        this.isPassing = isPassing;
        this.isAbsent = isAbsent;
        this.isExempt = isExempt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
