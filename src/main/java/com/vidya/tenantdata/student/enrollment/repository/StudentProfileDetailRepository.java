package com.vidya.tenantdata.student.enrollment.repository;

import com.vidya.tenantdata.student.enrollment.entity.StudentProfileDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentProfileDetailRepository extends JpaRepository<StudentProfileDetail, UUID> {
}
