-- ADMIN manages the whole hospital; an appointed DOCTOR manages one department.
-- Former MANAGER accounts stay for audit history but cannot sign in or refresh a session.
-- An ADMIN decides who to reactivate; nobody gains authority from this migration.
UPDATE account
SET is_active = FALSE, role = 'ADMIN', credential_version = credential_version + 1
WHERE role = 'MANAGER';

UPDATE staff SET job_title = 'ADMINISTRATIVE' WHERE job_title = 'MANAGER';

-- schedule_management_scope_audit stays: it records who held which grant and why.
DROP TABLE schedule_management_scope_command_receipt;
DROP TABLE schedule_management_scope_department;
DROP TABLE schedule_management_scope;
