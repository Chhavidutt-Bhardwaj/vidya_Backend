package com.vidya.tenantdata.subject.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CurriculumStatus — State Machine Tests")
class CurriculumStatusTest {

    @Test
    @DisplayName("DRAFT can transition to ACTIVE, DELETED")
    void draft_ValidTransitions() {
        assertThat(CurriculumStatus.DRAFT.canTransitionTo(CurriculumStatus.ACTIVE)).isTrue();
        assertThat(CurriculumStatus.DRAFT.canTransitionTo(CurriculumStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("DRAFT cannot transition directly to ARCHIVED")
    void draft_InvalidTransitions() {
        assertThat(CurriculumStatus.DRAFT.canTransitionTo(CurriculumStatus.ARCHIVED)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE can transition to ARCHIVED, DELETED")
    void active_ValidTransitions() {
        assertThat(CurriculumStatus.ACTIVE.canTransitionTo(CurriculumStatus.ARCHIVED)).isTrue();
        assertThat(CurriculumStatus.ACTIVE.canTransitionTo(CurriculumStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("ACTIVE cannot transition to DRAFT")
    void active_CannotTransitionToDraft() {
        assertThat(CurriculumStatus.ACTIVE.canTransitionTo(CurriculumStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("ARCHIVED can only transition to DELETED")
    void archived_ValidTransitions() {
        assertThat(CurriculumStatus.ARCHIVED.canTransitionTo(CurriculumStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("ARCHIVED cannot transition back to DRAFT or ACTIVE")
    void archived_CannotReactivate() {
        assertThat(CurriculumStatus.ARCHIVED.canTransitionTo(CurriculumStatus.DRAFT)).isFalse();
        assertThat(CurriculumStatus.ARCHIVED.canTransitionTo(CurriculumStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("DELETED is terminal — no valid transitions")
    void deleted_IsTerminal() {
        for (CurriculumStatus target : CurriculumStatus.values()) {
            if (target == CurriculumStatus.DELETED) continue;
            assertThat(CurriculumStatus.DELETED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("Same status transition is always valid (idempotent)")
    void sameStatus_IsIdempotent() {
        for (CurriculumStatus status : CurriculumStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    @DisplayName("validateTransitionTo throws InvalidStateTransitionException for invalid transition")
    void validateTransitionTo_ThrowsOnInvalid() {
        assertThatThrownBy(() -> CurriculumStatus.DELETED.validateTransitionTo(CurriculumStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> CurriculumStatus.ARCHIVED.validateTransitionTo(CurriculumStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
