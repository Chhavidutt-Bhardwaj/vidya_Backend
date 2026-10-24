package com.vidya.tenantdata.examination.mapper;

import com.vidya.tenantdata.examination.dto.StudentMarkResponse;
import com.vidya.tenantdata.examination.entity.StudentMarkEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StudentMarkMapper {

    public StudentMarkResponse toResponse(StudentMarkEntry entry) {
        if (entry == null) return null;

        BigDecimal maxMarks = entry.getExamSubjectConfig() != null ? entry.getExamSubjectConfig().getMaxMarks() : null;
        BigDecimal passMarks = entry.getExamSubjectConfig() != null ? entry.getExamSubjectConfig().getPassMarks() : null;

        BigDecimal percentage = null;
        boolean isPassing = false;

        if (entry.getMarksObtained() != null && maxMarks != null && maxMarks.compareTo(BigDecimal.ZERO) > 0) {
            percentage = entry.getMarksObtained()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(maxMarks, 2, RoundingMode.HALF_UP);

            if (passMarks != null) {
                isPassing = entry.getMarksObtained().compareTo(passMarks) >= 0;
            }
        }

        String studentName = null;
        String admissionNumber = null;
        if (entry.getStudent() != null) {
            admissionNumber = entry.getStudent().getAdmissionNumber();
            if (entry.getStudent().getTenantUser() != null && entry.getStudent().getTenantUser().getProfile() != null) {
                studentName = entry.getStudent().getTenantUser().getProfile().getFullName();
            }
        }

        return StudentMarkResponse.builder()
                .id(entry.getId())
                .entryCode(entry.getEntryCode())
                .examSubjectConfigId(entry.getExamSubjectConfig() != null ? entry.getExamSubjectConfig().getId() : null)
                .subjectName(entry.getExamSubjectConfig() != null && entry.getExamSubjectConfig().getSubject() != null
                        ? entry.getExamSubjectConfig().getSubject().getName() : null)
                .subjectCode(entry.getExamSubjectConfig() != null && entry.getExamSubjectConfig().getSubject() != null
                        ? entry.getExamSubjectConfig().getSubject().getSubjectCode() : null)
                .studentId(entry.getStudent() != null ? entry.getStudent().getId() : null)
                .studentName(studentName)
                .admissionNumber(admissionNumber)
                .marksObtained(entry.getMarksObtained())
                .maxMarks(maxMarks)
                .theoryMarksObtained(entry.getTheoryMarksObtained())
                .practicalMarksObtained(entry.getPracticalMarksObtained())
                .percentage(percentage)
                .grade(entry.getGrade())
                .gradePoint(entry.getGradePoint())
                .isAbsent(entry.isAbsent())
                .isExempt(entry.isExempt())
                .isPassing(isPassing)
                .remarks(entry.getRemarks())
                .evaluatedByStaffId(entry.getEvaluatedByStaff() != null ? entry.getEvaluatedByStaff().getId() : null)
                .evaluatedByStaffName(entry.getEvaluatedByStaff() != null && entry.getEvaluatedByStaff().getTenantUser() != null
                        && entry.getEvaluatedByStaff().getTenantUser().getProfile() != null
                        ? entry.getEvaluatedByStaff().getTenantUser().getProfile().getFullName() : null)
                .status(entry.getStatus())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
