package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.AdmissionResponse;
import com.vidya.tenantdata.student.dto.CreateAdmissionRequest;

public interface StudentAdmissionService {

    AdmissionResponse admitStudent(CreateAdmissionRequest request);
}
