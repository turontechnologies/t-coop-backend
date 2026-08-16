-- A co-operative's admin now logs in with the co-operative's own ID (the co-op IS the
-- admin account), instead of a separately generated AD-XXXX membership ID. This retires
-- every existing admin row whose id doesn't already match its cooperative_id -- including
-- the V2-seeded 'AD-0001' -- reassigning its id to its cooperative_id and carrying its full
-- profile plus every foreign-key reference across to the new id.

DECLARE @oldId NVARCHAR(20);
DECLARE @newId NVARCHAR(20);
DECLARE @email NVARCHAR(320);

DECLARE admin_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT id, cooperative_id, email
    FROM members
    WHERE role = 'admin'
      AND cooperative_id IS NOT NULL
      AND id <> cooperative_id
      AND NOT EXISTS (SELECT 1 FROM members m2 WHERE m2.id = members.cooperative_id);

OPEN admin_cursor;
FETCH NEXT FROM admin_cursor INTO @oldId, @newId, @email;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- free the UNIQUE(email) slot before the row exists twice under two different ids
    UPDATE members SET email = CONCAT('migrating-', id, '@placeholder.invalid') WHERE id = @oldId;

    INSERT INTO members (
        id, cooperative_id, role, password_hash, first_name, last_name, other_name,
        email, phone, gender, nin, home_address, country, state, city,
        facebook, twitter, guarantor, bank_code, account_number, account_name,
        avatar_url, status, created_at, updated_at
    )
    SELECT
        @newId, cooperative_id, role, password_hash, first_name, last_name, other_name,
        @email, phone, gender, nin, home_address, country, state, city,
        facebook, twitter, guarantor, bank_code, account_number, account_name,
        avatar_url, status, created_at, updated_at
    FROM members WHERE id = @oldId;

    UPDATE savings_type_approvers SET member_id = @newId WHERE member_id = @oldId;
    UPDATE loan_types SET approver1_id = @newId WHERE approver1_id = @oldId;
    UPDATE loan_types SET approver2_id = @newId WHERE approver2_id = @oldId;
    UPDATE savings_records SET member_id = @newId WHERE member_id = @oldId;
    UPDATE savings_requests SET member_id = @newId WHERE member_id = @oldId;
    UPDATE loan_records SET member_id = @newId WHERE member_id = @oldId;
    UPDATE loan_records SET guarantor_id = @newId WHERE guarantor_id = @oldId;
    UPDATE notices SET created_by_id = @newId WHERE created_by_id = @oldId;
    UPDATE notice_replies SET author_id = @newId WHERE author_id = @oldId;
    UPDATE notice_read_receipts SET member_id = @newId WHERE member_id = @oldId;
    UPDATE audit_log SET actor_id = @newId WHERE actor_id = @oldId;

    DELETE FROM members WHERE id = @oldId;

    FETCH NEXT FROM admin_cursor INTO @oldId, @newId, @email;
END

CLOSE admin_cursor;
DEALLOCATE admin_cursor;
