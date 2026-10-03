package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;

import java.util.List;
import java.util.UUID;

public interface StudentSubjectEnrollmentService {

    StudentSubjectEnrollmentResponse enrollSubject(UUID enrollmentId, CreateStudentSubjectEnrollmentRequest request);

    List<StudentSubjectEnrollmentResponse> batchEnrollSubjects(UUID enrollmentId, BatchAssignSubjectsRequest request);

    List<StudentSubjectEnrollment> autoEnrollCompulsorySubjects(StudentAcademicEnrollment enrollment);

    List<StudentSubjectEnrollmentResponse> getEnrolledSubjects(UUID enrollmentId);

    StudentSubjectEnrollmentResponse getSubjectEnrollmentById(UUID enrollmentId, UUID subjectEnrollmentId);

    StudentSubjectEnrollmentResponse updateSubjectEnrollment(UUID enrollmentId, UUID subjectEnrollmentId, UpdateStudentSubjectEnrollmentRequest request);

    void removeSubjectEnrollment(UUID enrollmentId, UUID subjectEnrollmentId);
}
