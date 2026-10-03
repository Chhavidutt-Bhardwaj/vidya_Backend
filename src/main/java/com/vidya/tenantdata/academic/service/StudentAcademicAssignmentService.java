package com.vidya.tenantdata.academic.service;

import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.TransferStudentAssignmentRequest;
import com.vidya.tenantdata.academic.dto.UpdateStudentAssignmentRequest;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StudentAcademicAssignmentService {

    StudentAcademicAssignmentResponse assignStudent(UUID studentId, AssignStudentRequest request);

    List<StudentAcademicAssignmentResponse> getStudentAssignments(UUID studentId);

    StudentAcademicAssignmentResponse getCurrentAssignment(UUID studentId);

    StudentAcademicAssignmentResponse getAssignmentById(UUID studentId, UUID assignmentId);

    StudentAcademicAssignmentResponse updateAssignment(UUID studentId, UUID assignmentId, UpdateStudentAssignmentRequest request);

    StudentAcademicAssignmentResponse transferStudent(UUID studentId, UUID assignmentId, TransferStudentAssignmentRequest request);

    Page<StudentAcademicAssignmentResponse> searchAssignments(
            UUID studentId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            StudentAssignmentStatus status,
            Pageable pageable
    );
}
