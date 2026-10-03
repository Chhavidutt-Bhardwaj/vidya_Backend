package com.vidya.tenantdata.subject.service;

import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for managing Subject mappings within a Curriculum.
 */
public interface CurriculumSubjectService {

    CurriculumSubjectResponse addSubjectToCurriculum(UUID curriculumId, CreateCurriculumSubjectRequest request);

    CurriculumSubjectResponse getCurriculumSubject(UUID curriculumId, UUID curriculumSubjectId);

    Page<CurriculumSubjectResponse> listCurriculumSubjects(UUID curriculumId, UUID academicClassId,
                                                           SubjectType subjectType, Boolean isCompulsory,
                                                           Pageable pageable);

    CurriculumSubjectResponse updateCurriculumSubject(UUID curriculumId, UUID curriculumSubjectId,
                                                      UpdateCurriculumSubjectRequest request);

    void removeSubjectFromCurriculum(UUID curriculumId, UUID curriculumSubjectId);
}
