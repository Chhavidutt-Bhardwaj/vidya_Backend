-- =====================================================================================
-- V28: Overview dashboard — action items a user has dismissed.
--
-- "Dismiss" hides a pending item (lesson plan to review, marks to lock, ...) from one user's
-- dashboard only. It does not approve or reject the item; it stays pending in its own module.
-- user_id is the master platform user id of the person who dismissed it; item_key is the id of the
-- pending record (lesson plan, exam paper, re-evaluation request) or "termId:classId" for report cards.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS dashboard_action_dismissal (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    item_type     VARCHAR(40)  NOT NULL,
    item_key      VARCHAR(100) NOT NULL,
    dismissed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dashboard_action_dismissal PRIMARY KEY (id),
    CONSTRAINT uq_dashboard_action_dismissal UNIQUE (user_id, item_type, item_key),
    CONSTRAINT chk_dashboard_action_dismissal_type CHECK (item_type IN (
        'LESSON_PLAN_REVIEW', 'MARKS_LOCK', 'REPORT_CARD_PUBLISH', 'REEVALUATION_REQUEST'
    ))
);

COMMENT ON TABLE dashboard_action_dismissal IS 'Dashboard action items hidden by one user; the item itself stays pending';
