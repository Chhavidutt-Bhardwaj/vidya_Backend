package com.vidya.tenantdata.assignment.service;

import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for ClassSubjectAssignment business operations.
 */
public interface ClassSubjectAssignmentService {

    ClassSubjectAssignmentDetailResponse createAssignment(CreateClassSubjectAssignmentRequest request);

    ClassSubjectAssignmentDetailResponse getAssignment(UUID id);

    ClassSubjectAssignmentDetailResponse getAssignmentByCode(String assignmentCode);

    Page<ClassSubjectAssignmentResponse> searchAssignments(
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            UUID subjectId,
            ClassSubjectAssignmentStatus status,
            Boolean isCompulsory,
            Pageable pageable
    );

    List<ClassSubjectAssignmentResponse> getActiveAssignmentsByClassAndSection(UUID academicYearId, UUID academicClassId, UUID sectionId);

    ClassSubjectAssignmentDetailResponse updateAssignment(UUID id, UpdateClassSubjectAssignmentRequest request);

    ClassSubjectAssignmentDetailResponse updateStatus(UUID id, UpdateClassSubjectAssignmentStatusRequest request);

    void deleteAssignment(UUID id);
}
