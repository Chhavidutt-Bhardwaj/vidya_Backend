package com.vidya.tenantdata.academic.mapper;

import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.student.entity.Student;

public final class AcademicMapper {

    private AcademicMapper() {}

    // ── AcademicYear ──────────────────────────────────────────────────────────

    public static AcademicYear toEntity(CreateAcademicYearRequest request) {
        return new AcademicYear(
                request.getCode(),
                request.getName(),
                request.getStartDate(),
                request.getEndDate(),
                request.isCurrent(),
                request.isCurrent() ? AcademicYearStatus.ACTIVE : AcademicYearStatus.DRAFT
        );
    }

    public static AcademicYearResponse toResponse(AcademicYear year) {
        if (year == null) return null;
        return AcademicYearResponse.builder()
                .id(year.getId())
                .code(year.getCode())
                .name(year.getName())
                .startDate(year.getStartDate())
                .endDate(year.getEndDate())
                .isCurrent(year.isCurrent())
                .status(year.getStatus())
                .createdAt(year.getCreatedAt())
                .updatedAt(year.getUpdatedAt())
                .version(year.getVersion())
                .build();
    }

    // ── AcademicClass ─────────────────────────────────────────────────────────

    public static AcademicClass toEntity(CreateAcademicClassRequest request) {
        return new AcademicClass(
                request.getCode(),
                request.getName(),
                request.getDisplayName(),
                request.getSortOrder()
        );
    }

    public static AcademicClassResponse toResponse(AcademicClass cls) {
        if (cls == null) return null;
        return AcademicClassResponse.builder()
                .id(cls.getId())
                .code(cls.getCode())
                .name(cls.getName())
                .displayName(cls.getDisplayName())
                .sortOrder(cls.getSortOrder())
                .status(cls.getStatus())
                .createdAt(cls.getCreatedAt())
                .updatedAt(cls.getUpdatedAt())
                .version(cls.getVersion())
                .build();
    }

    // ── Section ───────────────────────────────────────────────────────────────

    public static Section toEntity(CreateSectionRequest request, AcademicYear year, AcademicClass cls) {
        return new Section(
                year,
                cls,
                request.getCode(),
                request.getName(),
                request.getCapacity()
        );
    }

    public static SectionResponse toResponse(Section section, long activeCount) {
        if (section == null) return null;
        return SectionResponse.builder()
                .id(section.getId())
                .academicYearId(section.getAcademicYear() != null ? section.getAcademicYear().getId() : null)
                .academicYearCode(section.getAcademicYear() != null ? section.getAcademicYear().getCode() : null)
                .academicClassId(section.getAcademicClass() != null ? section.getAcademicClass().getId() : null)
                .academicClassCode(section.getAcademicClass() != null ? section.getAcademicClass().getCode() : null)
                .academicClassName(section.getAcademicClass() != null ? section.getAcademicClass().getName() : null)
                .code(section.getCode())
                .name(section.getName())
                .capacity(section.getCapacity())
                .activeStudentCount(activeCount)
                .status(section.getStatus())
                .createdAt(section.getCreatedAt())
                .updatedAt(section.getUpdatedAt())
                .version(section.getVersion())
                .build();
    }

    // ── StudentAcademicAssignment ─────────────────────────────────────────────

    public static StudentAcademicAssignmentResponse toResponse(StudentAcademicAssignment assignment) {
        if (assignment == null) return null;

        String studentCode = null;
        String admissionNumber = null;
        String studentName = null;
        if (assignment.getStudent() != null) {
            Student s = assignment.getStudent();
            studentCode = s.getStudentCode();
            admissionNumber = s.getAdmissionNumber();
            if (s.getTenantUser() != null && s.getTenantUser().getProfile() != null) {
                studentName = s.getTenantUser().getProfile().getFullName();
            }
        }

        StudentAcademicAssignmentResponse.AcademicYearSummary yearSummary = null;
        if (assignment.getAcademicYear() != null) {
            yearSummary = StudentAcademicAssignmentResponse.AcademicYearSummary.builder()
                    .id(assignment.getAcademicYear().getId())
                    .code(assignment.getAcademicYear().getCode())
                    .name(assignment.getAcademicYear().getName())
                    .build();
        }

        StudentAcademicAssignmentResponse.AcademicClassSummary classSummary = null;
        if (assignment.getAcademicClass() != null) {
            classSummary = StudentAcademicAssignmentResponse.AcademicClassSummary.builder()
                    .id(assignment.getAcademicClass().getId())
                    .code(assignment.getAcademicClass().getCode())
                    .name(assignment.getAcademicClass().getName())
                    .build();
        }

        StudentAcademicAssignmentResponse.SectionSummary sectionSummary = null;
        if (assignment.getSection() != null) {
            sectionSummary = StudentAcademicAssignmentResponse.SectionSummary.builder()
                    .id(assignment.getSection().getId())
                    .code(assignment.getSection().getCode())
                    .name(assignment.getSection().getName())
                    .build();
        }

        return StudentAcademicAssignmentResponse.builder()
                .id(assignment.getId())
                .studentId(assignment.getStudent() != null ? assignment.getStudent().getId() : null)
                .studentCode(studentCode)
                .admissionNumber(admissionNumber)
                .studentName(studentName)
                .academicYear(yearSummary)
                .academicClass(classSummary)
                .section(sectionSummary)
                .rollNumber(assignment.getRollNumber())
                .status(assignment.getStatus())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .version(assignment.getVersion())
                .build();
    }
}
