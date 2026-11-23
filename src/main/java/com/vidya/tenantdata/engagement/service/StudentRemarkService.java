package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkRow;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkUpdateRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarksRequest;
import com.vidya.tenantdata.engagement.entity.StudentDailyRemark;
import com.vidya.tenantdata.engagement.enums.RemarkCategory;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import com.vidya.tenantdata.engagement.repository.StudentDailyRemarkRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A teacher's daily remarks about students. Written for students the teacher teaches (STUDENT scope ALL: any);
 * changed or deleted by the author. Families see only remarks marked visible to guardians.
 */
@Service
@RequiredArgsConstructor
public class StudentRemarkService {

    private final StudentDailyRemarkRepository repository;
    private final SubjectRepository subjectRepository;
    private final EngagementAccess access;
    private final Clock clock;

    /** One or many students, one day. All or nothing. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<RemarkResponse> create(RemarksRequest r) {
        LocalDate date = r.remarkDate() != null ? r.remarkDate() : LocalDate.now(clock);
        if (date.isAfter(LocalDate.now(clock))) {
            throw new BusinessException("remarkDate is in the future");
        }
        Set<UUID> students = new LinkedHashSet<>();
        r.remarks().forEach(row -> students.add(row.studentId()));
        access.requireStudents(students, "write remarks");
        UUID teacher = access.ownStaffIds().stream().findFirst().orElse(null);
        List<StudentDailyRemark> rows = new ArrayList<>();
        for (RemarkRow row : r.remarks()) {
            StudentDailyRemark m = new StudentDailyRemark(row.studentId(), date);
            m.setTeacherId(teacher);
            m.setCreatedBy(access.actorId());
            set(m, row.subjectId(), row.category(), row.sentiment(), row.remark(), row.visibleToGuardians());
            rows.add(m);
        }
        return respond(repository.saveAll(rows));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public RemarkResponse update(UUID id, RemarkUpdateRequest r) {
        StudentDailyRemark m = remark(id);
        access.requireAuthorOrAll(m.getCreatedBy(), "change remarks");
        set(m, r.subjectId(), r.category(), r.sentiment(), r.remark(), r.visibleToGuardians());
        return respond(List.of(repository.save(m))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        StudentDailyRemark m = remark(id);
        access.requireAuthorOrAll(m.getCreatedBy(), "delete remarks");
        m.setDeletedAt(Instant.now(clock));
        m.setUpdatedBy(access.actorId());
        repository.save(m);
    }

    /**
     * Remarks of a student, or of every student enrolled in a section (controller checks access to either).
     * Staff-only remarks are included only for users who may write remarks.
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<RemarkResponse> list(UUID studentId, UUID sectionId, LocalDate from, LocalDate to, Integer limit) {
        if (studentId == null && sectionId == null) {
            throw new BusinessException("Give studentId or sectionId");
        }
        boolean guardianVisibleOnly = !access.hasAuthority(DailyActivityReportService.STAFF_REMARKS_AUTHORITY);
        return respond(repository.search(studentId, sectionId, from, to, guardianVisibleOnly, HomeworkService.limit(limit, 100, 500)));
    }

    private void set(StudentDailyRemark m, UUID subjectId, RemarkCategory category, RemarkSentiment sentiment, String remark,
                     Boolean visible) {
        if (subjectId != null) {
            subjectRepository.findByIdAndNotDeleted(subjectId).orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));
        }
        m.setSubjectId(subjectId);
        m.setCategory(category != null ? category : RemarkCategory.GENERAL);
        m.setSentiment(sentiment != null ? sentiment : RemarkSentiment.NEUTRAL);
        m.setRemark(remark.trim());
        m.setVisibleToGuardians(visible == null || visible);
        m.setUpdatedBy(access.actorId());
    }

    private List<RemarkResponse> respond(List<StudentDailyRemark> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        TeachingScope n = access.teaching();
        Map<UUID, String> students = n.names("SELECT s.id, COALESCE(NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), u.user_code) "
                + "FROM student s JOIN tenant_user u ON u.id = s.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id "
                + "WHERE s.id IN (:ids)", list.stream().map(StudentDailyRemark::getStudentId).toList());
        Map<UUID, String> teachers = n.staffNames(list.stream().map(StudentDailyRemark::getTeacherId).toList());
        Map<UUID, String> subjects = n.subjectNames(list.stream().map(StudentDailyRemark::getSubjectId).toList());
        List<RemarkResponse> out = new ArrayList<>();
        for (StudentDailyRemark m : list) {
            out.add(new RemarkResponse(m.getId(), m.getStudentId(), students.get(m.getStudentId()), m.getRemarkDate(), m.getTeacherId(),
                    teachers.get(m.getTeacherId()), m.getSubjectId(), subjects.get(m.getSubjectId()), m.getCategory(), m.getSentiment(),
                    m.getRemark(), m.isVisibleToGuardians(), m.getCreatedAt()));
        }
        return out;
    }

    private StudentDailyRemark remark(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("StudentDailyRemark", id));
    }
}
