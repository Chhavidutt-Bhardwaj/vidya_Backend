package com.vidya.tenantdata.examination.service;

import com.vidya.tenantdata.examination.dto.BulkStudentMarksRequest;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksResponse;
import com.vidya.tenantdata.examination.dto.StudentMarkResponse;
import com.vidya.tenantdata.examination.dto.SubmitStudentMarkRequest;
import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StudentMarkService {

    StudentMarkResponse submitStudentMark(UUID configId, SubmitStudentMarkRequest request, UUID evaluatorStaffId);

    BulkStudentMarksResponse submitBulkMarks(BulkStudentMarksRequest request, UUID evaluatorStaffId);

    StudentMarkResponse getMarkById(UUID id);

    Page<StudentMarkResponse> searchMarks(UUID configId, MarkEntryStatus status, Pageable pageable);

    List<StudentMarkResponse> getMarksByStudent(UUID studentId);

    void lockMarks(UUID configId);
}
