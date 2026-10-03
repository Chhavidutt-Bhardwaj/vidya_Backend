package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.UpdateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StudentEnrollmentService {

    EnrollmentDetailResponse createEnrollment(UUID studentId, CreateEnrollmentRequest request);

    StudentAcademicEnrollment createEnrollmentEntity(Student student, CreateEnrollmentRequest request);

    EnrollmentDetailResponse getEnrollmentById(UUID enrollmentId);

    EnrollmentDetailResponse getEnrollmentByCode(String code);

    List<EnrollmentResponse> getEnrollmentsByStudentId(UUID studentId);

    CurrentEnrollmentResponse getCurrentEnrollment(UUID studentId);

    EnrollmentDetailResponse updateEnrollment(UUID enrollmentId, UpdateEnrollmentRequest request);

    EnrollmentDetailResponse withdrawStudent(UUID enrollmentId, WithdrawEnrollmentRequest request);

    EnrollmentDetailResponse transferStudent(UUID enrollmentId, TransferEnrollmentRequest request);

    Page<EnrollmentResponse> searchEnrollments(
            UUID studentId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            StudentEnrollmentStatus status,
            String enrollmentCode,
            Pageable pageable
    );

    Page<ClassSectionStudentResponse> getClassSectionStudents(
            UUID classId,
            UUID sectionId,
            UUID academicYearId,
            StudentEnrollmentStatus status,
            String searchQuery,
            Pageable pageable
    );
}
