package com.vidya.tenantdata.assignment.service;

import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for TeacherSubjectAssignment business operations.
 */
public interface TeacherSubjectAssignmentService {

    TeacherAssignmentDetailResponse createAssignment(CreateTeacherAssignmentRequest request);

    TeacherAssignmentDetailResponse getAssignment(UUID id);

    TeacherAssignmentDetailResponse getAssignmentByCode(String assignmentCode);

    Page<TeacherAssignmentResponse> searchAssignments(
            UUID teacherId,
            UUID subjectId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            TeacherAssignmentStatus status,
            Boolean isPrimary,
            Pageable pageable
    );

    List<TeacherAssignmentResponse> getActiveAssignmentsByTeacher(UUID teacherId, UUID academicYearId);

    List<TeacherAssignmentResponse> getActiveAssignmentsByClassAndSection(UUID academicYearId, UUID academicClassId, UUID sectionId);

    TeacherAssignmentDetailResponse updateAssignment(UUID id, UpdateTeacherAssignmentRequest request);

    TeacherAssignmentDetailResponse updateStatus(UUID id, UpdateTeacherAssignmentStatusRequest request);

    void deleteAssignment(UUID id);
}
