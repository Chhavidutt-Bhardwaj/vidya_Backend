package com.vidya.tenantdata.student.mapper;

import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;

import java.util.Collections;
import java.util.List;

public final class EnrollmentMapper {

    private EnrollmentMapper() {}

    public static EnrollmentResponse toResponse(StudentAcademicEnrollment entity) {
        if (entity == null) {
            return null;
        }
        Student s = entity.getStudent();
        TenantUser u = s != null ? s.getTenantUser() : null;
        TenantUserProfile p = u != null ? u.getProfile() : null;

        String studentName = null;
        if (p != null) {
            if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
                studentName = p.getDisplayName();
            } else {
                studentName = (p.getFirstName() != null ? p.getFirstName() : "") +
                        (p.getLastName() != null && !p.getLastName().isBlank() ? " " + p.getLastName() : "");
                studentName = studentName.trim();
            }
        }

        return EnrollmentResponse.builder()
                .id(entity.getId())
                .enrollmentCode(entity.getEnrollmentCode())
                .studentId(s != null ? s.getId() : null)
                .studentCode(s != null ? s.getStudentCode() : null)
                .admissionNumber(s != null ? s.getAdmissionNumber() : null)
                .studentName(studentName)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .rollNumber(entity.getRollNumber())
                .enrollmentStatus(entity.getEnrollmentStatus())
                .admissionDate(entity.getAdmissionDate() != null ? entity.getAdmissionDate() : (s != null ? s.getAdmissionDate() : null))
                .enrollmentDate(entity.getEnrollmentDate())
                .withdrawalDate(entity.getWithdrawalDate())
                .withdrawalReason(entity.getWithdrawalReason())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }

    public static EnrollmentDetailResponse toDetailResponse(StudentAcademicEnrollment entity, List<StudentSubjectEnrollment> enrolledSubjects) {
        if (entity == null) {
            return null;
        }
        List<StudentSubjectEnrollmentResponse> subjectResponses = enrolledSubjects != null
                ? enrolledSubjects.stream().map(StudentSubjectEnrollmentMapper::toResponse).toList()
                : Collections.emptyList();

        return EnrollmentDetailResponse.builder()
                .id(entity.getId())
                .enrollmentCode(entity.getEnrollmentCode())
                .student(entity.getStudent() != null ? StudentMapper.toResponse(entity.getStudent()) : null)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .rollNumber(entity.getRollNumber())
                .enrollmentStatus(entity.getEnrollmentStatus())
                .admissionDate(entity.getAdmissionDate() != null ? entity.getAdmissionDate() : (entity.getStudent() != null ? entity.getStudent().getAdmissionDate() : null))
                .enrollmentDate(entity.getEnrollmentDate())
                .withdrawalDate(entity.getWithdrawalDate())
                .withdrawalReason(entity.getWithdrawalReason())
                .remarks(entity.getRemarks())
                .enrolledSubjects(subjectResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }

    public static CurrentEnrollmentResponse toCurrentResponse(StudentAcademicEnrollment entity, List<StudentSubjectEnrollment> enrolledSubjects) {
        if (entity == null) {
            return null;
        }
        List<StudentSubjectEnrollmentResponse> subjectResponses = enrolledSubjects != null
                ? enrolledSubjects.stream().map(StudentSubjectEnrollmentMapper::toResponse).toList()
                : Collections.emptyList();

        return CurrentEnrollmentResponse.builder()
                .enrollmentId(entity.getId())
                .enrollmentCode(entity.getEnrollmentCode())
                .student(entity.getStudent() != null ? StudentMapper.toResponse(entity.getStudent()) : null)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .rollNumber(entity.getRollNumber())
                .enrollmentStatus(entity.getEnrollmentStatus())
                .enrollmentDate(entity.getEnrollmentDate())
                .subjects(subjectResponses)
                .build();
    }

    public static ClassSectionStudentResponse toClassSectionStudentResponse(StudentAcademicEnrollment entity) {
        if (entity == null) {
            return null;
        }
        Student s = entity.getStudent();
        TenantUser u = s != null ? s.getTenantUser() : null;
        TenantUserProfile p = u != null ? u.getProfile() : null;

        String fullName = null;
        String firstName = null;
        String lastName = null;
        String gender = null;
        java.time.LocalDate dob = null;
        String phone = null;

        if (p != null) {
            firstName = p.getFirstName();
            lastName = p.getLastName();
            gender = p.getGender();
            dob = p.getDateOfBirth();
            fullName = (firstName != null ? firstName : "") +
                    (lastName != null && !lastName.isBlank() ? " " + lastName : "");
            fullName = fullName.trim();
        }
        if (u != null) {
            phone = u.getPhone();
        }

        return ClassSectionStudentResponse.builder()
                .enrollmentId(entity.getId())
                .enrollmentCode(entity.getEnrollmentCode())
                .studentId(s != null ? s.getId() : null)
                .studentCode(s != null ? s.getStudentCode() : null)
                .admissionNumber(s != null ? s.getAdmissionNumber() : null)
                .firstName(firstName)
                .lastName(lastName)
                .fullName(fullName)
                .gender(gender)
                .dateOfBirth(dob)
                .contactPhone(phone)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .rollNumber(entity.getRollNumber())
                .enrollmentStatus(entity.getEnrollmentStatus())
                .enrollmentDate(entity.getEnrollmentDate())
                .build();
    }
}
