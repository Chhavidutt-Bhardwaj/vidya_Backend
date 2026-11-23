-- Dashboard action items for online fees can be dismissed too:
--   FEE_DEBIT_ERROR     auto-debit the gateway refused (no bounce charge; collect another way)
--   FEE_UNALLOCATED     online payment with money left over after all dues (refund it)
--   FEE_MANDATE_STALLED mandate still waiting for the bank / payer after 3 days
ALTER TABLE dashboard_action_dismissal DROP CONSTRAINT chk_dashboard_action_dismissal_type;
ALTER TABLE dashboard_action_dismissal ADD CONSTRAINT chk_dashboard_action_dismissal_type CHECK (item_type IN (
    'LESSON_PLAN_REVIEW', 'MARKS_LOCK', 'REPORT_CARD_PUBLISH', 'REEVALUATION_REQUEST', 'FEE_CONCESSION',
    'FEE_DEBIT_ERROR', 'FEE_UNALLOCATED', 'FEE_MANDATE_STALLED'
));
