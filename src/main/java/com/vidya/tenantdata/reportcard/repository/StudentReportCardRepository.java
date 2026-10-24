package com.vidya.tenantdata.reportcard.repository;

import com.vidya.tenantdata.reportcard.entity.StudentReportCard;
import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentReportCardRepository extends JpaRepository<StudentReportCard, UUID> {

    @Query("SELECT r FROM StudentReportCard r WHERE r.id = :id AND r.deletedAt IS NULL")
    Optional<StudentReportCard> findActiveById(@Param("id") UUID id);

    @Query("SELECT r FROM StudentReportCard r WHERE r.reportCardCode = :code AND r.deletedAt IS NULL")
    Optional<StudentReportCard> findActiveByCode(@Param("code") String code);

    @Query("SELECT r FROM StudentReportCard r WHERE r.examTerm.id = :termId AND r.student.id = :studentId AND r.deletedAt IS NULL")
    Optional<StudentReportCard> findByTermAndStudent(@Param("termId") UUID termId, @Param("studentId") UUID studentId);

    @Query("SELECT r FROM StudentReportCard r WHERE r.student.id = :studentId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<StudentReportCard> findByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT r FROM StudentReportCard r WHERE r.examTerm.id = :termId AND r.academicClass.id = :classId AND r.deletedAt IS NULL")
    List<StudentReportCard> findByTermAndClass(@Param("termId") UUID termId, @Param("classId") UUID classId);

    @Query("SELECT r FROM StudentReportCard r WHERE r.examTerm.id = :termId AND r.academicClass.id = :classId AND r.section.id = :sectionId AND r.deletedAt IS NULL")
    List<StudentReportCard> findByTermAndClassAndSection(@Param("termId") UUID termId,
                                                         @Param("classId") UUID classId,
                                                         @Param("sectionId") UUID sectionId);

    @Query("SELECT r FROM StudentReportCard r WHERE r.deletedAt IS NULL " +
           "AND (:termId IS NULL OR r.examTerm.id = :termId) " +
           "AND (:classId IS NULL OR r.academicClass.id = :classId) " +
           "AND (:sectionId IS NULL OR r.section.id = :sectionId) " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<StudentReportCard> searchReportCards(@Param("termId") UUID termId,
                                              @Param("classId") UUID classId,
                                              @Param("sectionId") UUID sectionId,
                                              @Param("status") ReportCardStatus status,
                                              Pageable pageable);
}
