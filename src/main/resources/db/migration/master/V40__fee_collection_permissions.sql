-- =====================================================================================
-- V40: Fee collection (tenant V29: ledgers, payments, receipts, concession requests, reminders).
--      See tenantdata/fee/FEES.md.
--
-- No new permission codes; the existing ones get their meaning:
--   FEE_READ       ledgers, receipts, payments day book, defaulters, summary (school-wide lists need FEE_SCOPE_ALL;
--                  parents read their own children's ledger and receipts)
--   FEE_MANAGE     generate invoices, add / cancel charges, collect payments, cheque clearance, request concessions,
--                  send reminders
--   FEE_APPROVE    approve / reject / revoke concession requests, reverse payments
--   FEE_SCOPE_ALL  work on every student's fees
--
-- Until now school owners, school admins and principals could read fees only for their OWN records (no
-- FEE_SCOPE_ALL) and could not collect. They oversee the school's money, so they get school-wide scope;
-- owners and admins can also collect and approve. Accounts collects but does not approve (maker-checker):
-- a principal, owner or admin approves concessions and reversals.
-- =====================================================================================

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('SCHOOL_OWNER', 'FEE_SCOPE_ALL'), ('SCHOOL_OWNER', 'FEE_MANAGE'), ('SCHOOL_OWNER', 'FEE_APPROVE'),
    ('SCHOOL_ADMIN', 'FEE_SCOPE_ALL'), ('SCHOOL_ADMIN', 'FEE_MANAGE'), ('SCHOOL_ADMIN', 'FEE_APPROVE'),
    ('PRINCIPAL',    'FEE_SCOPE_ALL')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
