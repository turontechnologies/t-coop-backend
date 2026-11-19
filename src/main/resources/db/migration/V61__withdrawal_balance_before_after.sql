-- A withdrawal remembers the member's savings just before it was paid and just after, so the
-- member can see "you had ₦X, now you have ₦Y". The balance is the one the money came out of:
-- that savings type, or all their savings. Filled in here for withdrawals already approved.
ALTER TABLE savings_requests ADD balance_before DECIMAL(18,2) NULL;
ALTER TABLE savings_requests ADD balance_after DECIMAL(18,2) NULL;
GO

UPDATE r SET
    balance_after = b.after_amount,
    balance_before = b.after_amount + r.amount
FROM savings_requests r
CROSS APPLY (
    SELECT TOP 1 id, created_at FROM savings_records
    WHERE member_id = r.member_id
      AND transaction_id LIKE 'wd-' + LOWER(LEFT(CAST(r.id AS NVARCHAR(36)), 8)) + '%'
    ORDER BY created_at
) w
CROSS APPLY (
    SELECT ISNULL(SUM(s.amount), 0) AS after_amount FROM savings_records s
    WHERE s.member_id = r.member_id
      AND s.status = 'Success'
      AND s.created_at <= w.created_at
      AND (r.savings_type_id IS NULL OR s.savings_type_id = r.savings_type_id)
) b
WHERE r.status = 'Approved' AND r.request_type = 'Withdrawal';
