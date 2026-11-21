package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.security.OwnRecords;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Row rules shared by homework, class activities, announcements and remarks. Staff with STUDENT scope ALL
 * work on the whole school; everyone else only on the sections, classes and students they teach.
 */
@Component
@RequiredArgsConstructor
public class EngagementAccess {

    static final String DOMAIN = "STUDENT";

    private final DataAccessService dataAccess;
    private final TeachingScope teaching;
    private final AcademicYearRepository yearRepository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;

    /** Year, class and (optional) section, checked to belong together. */
    public record Place(AcademicYear year, AcademicClass clazz, Section section) {
    }

    public boolean all() {
        return dataAccess.hasFullScope(auth(), DOMAIN);
    }

    public UUID actorId() {
        return teaching.actorId();
    }

    public TeachingScope teaching() {
        return teaching;
    }

    public OwnRecords own() {
        return dataAccess.ownRecords(auth());
    }

    public Set<UUID> ownStaffIds() {
        return teaching.ownStaffIds();
    }

    public boolean hasAuthority(String authority) {
        Authentication a = auth();
        return a != null && a.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    /** The user may post for the section, or (no section) for the whole class. */
    public void requireClassOrSection(UUID classId, UUID sectionId, String forWhat) {
        boolean ok = sectionId != null ? dataAccess.canAccessSection(auth(), sectionId, DOMAIN) : dataAccess.canAccessClass(auth(), classId, DOMAIN);
        if (!ok) {
            throw new AccessDeniedException("You can " + forWhat + " only for the classes and sections you teach");
        }
    }

    public void requireStudents(Collection<UUID> studentIds, String forWhat) {
        if (!dataAccess.canAccessStudents(auth(), studentIds, DOMAIN)) {
            throw new AccessDeniedException("You can " + forWhat + " only for students you teach");
        }
    }

    /** Whoever created the record, or a user with full scope. */
    public void requireAuthorOrAll(UUID createdBy, String forWhat) {
        if (!all() && (createdBy == null || !createdBy.equals(actorId()))) {
            throw new AccessDeniedException("You can " + forWhat + " only what you created");
        }
    }

    public Place place(UUID yearId, UUID classId, UUID sectionId) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(yearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearId));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        AcademicClass clazz = classRepository.findByIdAndNotDeleted(classId).orElseThrow(() -> new ResourceNotFoundException("AcademicClass", classId));
        Section section = null;
        if (sectionId != null) {
            section = sectionRepository.findByIdAndNotDeleted(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
            if (!section.getAcademicYear().getId().equals(year.getId()) || !section.getAcademicClass().getId().equals(clazz.getId())) {
                throw new BusinessException("The section is not a section of " + clazz.getName() + " in " + year.getCode());
            }
        }
        return new Place(year, clazz, section);
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
