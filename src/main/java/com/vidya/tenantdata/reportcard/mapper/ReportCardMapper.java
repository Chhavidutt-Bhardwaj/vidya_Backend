package com.vidya.tenantdata.reportcard.mapper;

import com.vidya.tenantdata.reportcard.dto.ReportCardRemarkResponse;
import com.vidya.tenantdata.reportcard.dto.ReportCardSubjectResultResponse;
import com.vidya.tenantdata.reportcard.dto.StudentReportCardResponse;
import com.vidya.tenantdata.reportcard.entity.ReportCardRemark;
import com.vidya.tenantdata.reportcard.entity.ReportCardSubjectResult;
import com.vidya.tenantdata.reportcard.entity.StudentReportCard;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ReportCardMapper {

    public StudentReportCardResponse toResponse(StudentReportCard card) {
        if (card == null) return null;

        String studentName = null;
        String admissionNumber = null;
        if (card.getStudent() != null) {
            admissionNumber = card.getStudent().getAdmissionNumber();
            if (card.getStudent().getTenantUser() != null && card.getStudent().getTenantUser().getProfile() != null) {
                studentName = card.getStudent().getTenantUser().getProfile().getFullName();
            }
        }

        List<ReportCardSubjectResultResponse> subjectDtos = card.getSubjectResults() != null
                ? card.getSubjectResults().stream().map(this::toSubjectResultResponse).toList()
                : Collections.emptyList();

        List<ReportCardRemarkResponse> remarkDtos = card.getRemarks() != null
                ? card.getRemarks().stream().map(this::toRemarkResponse).toList()
                : Collections.emptyList();

        return StudentReportCardResponse.builder()
                .id(card.getId())
                .reportCardCode(card.getReportCardCode())
                .examTermId(card.getExamTerm() != null ? card.getExamTerm().getId() : null)
                .examTermName(card.getExamTerm() != null ? card.getExamTerm().getName() : null)
                .academicYearId(card.getAcademicYear() != null ? card.getAcademicYear().getId() : null)
                .academicYearName(card.getAcademicYear() != null ? card.getAcademicYear().getName() : null)
                .academicClassId(card.getAcademicClass() != null ? card.getAcademicClass().getId() : null)
                .academicClassName(card.getAcademicClass() != null ? card.getAcademicClass().getName() : null)
                .sectionId(card.getSection() != null ? card.getSection().getId() : null)
                .sectionName(card.getSection() != null ? card.getSection().getName() : null)
                .studentId(card.getStudent() != null ? card.getStudent().getId() : null)
                .studentName(studentName)
                .admissionNumber(admissionNumber)
                .totalMaxMarks(card.getTotalMaxMarks())
                .totalMarksObtained(card.getTotalMarksObtained())
                .percentage(card.getPercentage())
                .overallGrade(card.getOverallGrade())
                .cgpa(card.getCgpa())
                .sectionRank(card.getSectionRank())
                .classRank(card.getClassRank())
                .attendanceWorkingDays(card.getAttendanceWorkingDays())
                .attendancePresentDays(card.getAttendancePresentDays())
                .attendancePercentage(card.getAttendancePercentage())
                .resultStatus(card.getResultStatus())
                .status(card.getStatus())
                .publishedAt(card.getPublishedAt())
                .subjectResults(subjectDtos)
                .remarks(remarkDtos)
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    public ReportCardSubjectResultResponse toSubjectResultResponse(ReportCardSubjectResult result) {
        if (result == null) return null;

        return ReportCardSubjectResultResponse.builder()
                .id(result.getId())
                .subjectId(result.getSubject() != null ? result.getSubject().getId() : null)
                .subjectName(result.getSubjectName())
                .subjectCode(result.getSubjectCode())
                .maxMarks(result.getMaxMarks())
                .passMarks(result.getPassMarks())
                .theoryMarks(result.getTheoryMarks())
                .practicalMarks(result.getPracticalMarks())
                .marksObtained(result.getMarksObtained())
                .percentage(result.getPercentage())
                .grade(result.getGrade())
                .gradePoint(result.getGradePoint())
                .isPassing(result.isPassing())
                .isAbsent(result.isAbsent())
                .isExempt(result.isExempt())
                .build();
    }

    public ReportCardRemarkResponse toRemarkResponse(ReportCardRemark remark) {
        if (remark == null) return null;

        String authorName = null;
        if (remark.getAuthorStaff() != null && remark.getAuthorStaff().getTenantUser() != null
                && remark.getAuthorStaff().getTenantUser().getProfile() != null) {
            authorName = remark.getAuthorStaff().getTenantUser().getProfile().getFullName();
        }

        return ReportCardRemarkResponse.builder()
                .id(remark.getId())
                .authorStaffId(remark.getAuthorStaff() != null ? remark.getAuthorStaff().getId() : null)
                .authorStaffName(authorName)
                .authorRole(remark.getAuthorRole())
                .remark(remark.getRemark())
                .conductRating(remark.getConductRating())
                .createdAt(remark.getCreatedAt())
                .updatedAt(remark.getUpdatedAt())
                .build();
    }
}
