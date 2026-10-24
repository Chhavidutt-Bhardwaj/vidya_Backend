package com.vidya.tenantdata.reportcard.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import com.vidya.tenantdata.reportcard.enums.ResultStatus;
import com.vidya.tenantdata.student.entity.Student;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "student_report_card")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentReportCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_card_code", nullable = false, unique = true, length = 50, updatable = false)
    private String reportCardCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_term_id", nullable = false)
    private ExamTerm examTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "total_max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMaxMarks = BigDecimal.ZERO;

    @Column(name = "total_marks_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarksObtained = BigDecimal.ZERO;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(name = "overall_grade", length = 10)
    private String overallGrade;

    @Column(name = "cgpa", precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "section_rank")
    private Integer sectionRank;

    @Column(name = "class_rank")
    private Integer classRank;

    @Column(name = "attendance_working_days", nullable = false)
    private int attendanceWorkingDays = 0;

    @Column(name = "attendance_present_days", nullable = false)
    private int attendancePresentDays = 0;

    @Column(name = "attendance_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal attendancePercentage = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 30)
    private ResultStatus resultStatus = ResultStatus.PASSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportCardStatus status = ReportCardStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "reportCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportCardSubjectResult> subjectResults = new ArrayList<>();

    @OneToMany(mappedBy = "reportCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportCardRemark> remarks = new ArrayList<>();

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

    public StudentReportCard(String reportCardCode, ExamTerm examTerm, AcademicYear academicYear,
                             AcademicClass academicClass, Section section, Student student) {
        this.reportCardCode = reportCardCode;
        this.examTerm = examTerm;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.student = student;
        this.status = ReportCardStatus.DRAFT;
    }

    public void addSubjectResult(ReportCardSubjectResult result) {
        subjectResults.add(result);
        result.setReportCard(this);
    }

    public void addRemark(ReportCardRemark remark) {
        remarks.add(remark);
        remark.setReportCard(this);
    }

    public void clearSubjectResults() {
        subjectResults.clear();
    }

    public void publish() {
        this.status = ReportCardStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void revoke() {
        this.status = ReportCardStatus.REVOKED;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
