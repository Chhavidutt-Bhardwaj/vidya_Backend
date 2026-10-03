package com.vidya.tenantdata.assignment.service;

import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.ReplaceClassTeacherRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for ClassTeacherAssignment business operations.
 */
public interface ClassTeacherAssignmentService {

    /** Create a new class teacher assignment for a section. */
    ClassTeacherAssignmentDetailResponse createAssignment(CreateClassTeacherAssignmentRequest request);

    /** Retrieve an assignment by UUID. */
    ClassTeacherAssignmentDetailResponse getAssignment(UUID id);

    /** Retrieve an assignment by business code. */
    ClassTeacherAssignmentDetailResponse getAssignmentByCode(String assignmentCode);

    /** Get the current ACTIVE class teacher for the given section and academic year. */
    ClassTeacherAssignmentDetailResponse getCurrentClassTeacher(UUID sectionId, UUID academicYearId);

    /** Paginated search with optional filters. */
    Page<ClassTeacherAssignmentResponse> searchAssignments(
            UUID staffId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            ClassTeacherAssignmentStatus status,
            Pageable pageable);

    /** Get all active class teacher assignments for a staff member. */
    List<ClassTeacherAssignmentResponse> getActiveAssignmentsByStaff(UUID staffId, UUID academicYearId);

    /** Get all assignments (history) for a given section and academic year. */
    List<ClassTeacherAssignmentResponse> getAssignmentsBySection(UUID sectionId, UUID academicYearId);

    /** Get all assignments for a given academic year. */
    List<ClassTeacherAssignmentResponse> getAssignmentsByAcademicYear(UUID academicYearId);

    /** Update mutable fields (startDate, endDate, notes). */
    ClassTeacherAssignmentDetailResponse updateAssignment(UUID id, UpdateClassTeacherAssignmentRequest request);

    /** Transition assignment to a new lifecycle status. */
    ClassTeacherAssignmentDetailResponse updateStatus(UUID id, UpdateClassTeacherAssignmentStatusRequest request);

    /**
     * Replace the current active class teacher for a section with a new teacher.
     * The existing assignment is COMPLETED and a new ACTIVE assignment is created transactionally.
     *
     * @param existingAssignmentId the UUID of the existing ACTIVE assignment to complete
     * @param request contains newStaffId, effectiveDate, notes
     * @return the newly created assignment
     */
    ClassTeacherAssignmentDetailResponse replaceClassTeacher(UUID existingAssignmentId, ReplaceClassTeacherRequest request);

    /** Soft-delete the assignment. */
    void deleteAssignment(UUID id);
}
