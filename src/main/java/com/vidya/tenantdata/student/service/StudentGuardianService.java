package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentGuardianRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentGuardian;

import java.util.List;
import java.util.UUID;

public interface StudentGuardianService {

    StudentGuardianResponse createGuardian(UUID studentId, CreateStudentGuardianRequest request);

    StudentGuardian createGuardianEntity(Student student, CreateStudentGuardianRequest request);

    List<StudentGuardianResponse> getGuardiansByStudentId(UUID studentId);

    StudentGuardianResponse getGuardianById(UUID studentId, UUID guardianId);

    StudentGuardianResponse updateGuardian(UUID studentId, UUID guardianId, UpdateStudentGuardianRequest request);

    void deleteGuardian(UUID studentId, UUID guardianId);
}
