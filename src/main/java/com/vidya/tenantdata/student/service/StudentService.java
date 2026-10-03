package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentProfileResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service handling Student business operations in the tenant schema.
 */
public interface StudentService {

    StudentDetailResponse createStudent(CreateStudentRequest request);

    StudentDetailResponse getStudentById(UUID id);

    StudentDetailResponse getStudentByStudentCode(String studentCode);

    StudentDetailResponse getStudentByAdmissionNumber(String admissionNumber);

    StudentDetailResponse getStudentByTenantUserId(UUID tenantUserId);

    StudentDetailResponse updateStudent(UUID id, UpdateStudentRequest request);

    StudentDetailResponse updateStudentStatus(UUID id, UpdateStudentStatusRequest request);

    void deleteStudent(UUID id);

    Page<StudentResponse> searchStudents(StudentStatus status, String admissionNumber, String studentCode, String searchQuery, Pageable pageable);

    StudentProfileResponse getStudentProfile(UUID id);
}
