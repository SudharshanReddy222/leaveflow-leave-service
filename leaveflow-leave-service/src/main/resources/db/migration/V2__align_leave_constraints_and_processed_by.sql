ALTER TABLE leave_requests RENAME COLUMN approved_by TO processed_by;
ALTER TABLE leave_requests
    RENAME CONSTRAINT ck_leave_requests_approved_by TO ck_leave_requests_processed_by;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM leave_balances
        WHERE total_days < 0
           OR used_days < 0
           OR available_days < 0
           OR used_days > total_days
           OR available_days <> total_days - used_days
    ) THEN
        RAISE EXCEPTION 'Cannot add leave balance constraints: existing rows violate the new rules';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM leave_requests
        WHERE end_date < start_date
           OR number_of_days <= 0
           OR (processed_by IS NOT NULL AND processed_by <= 0)
    ) THEN
        RAISE EXCEPTION 'Cannot add leave request constraints: existing rows violate the new rules';
    END IF;
END;
$$;

ALTER TABLE leave_balances
    DROP CONSTRAINT ck_leave_balances_days_nonnegative,
    DROP CONSTRAINT ck_leave_balances_days_consistent;

ALTER TABLE leave_balances
    ADD CONSTRAINT ck_leave_balances_total_days_nonnegative CHECK (total_days >= 0),
    ADD CONSTRAINT ck_leave_balances_used_days_nonnegative CHECK (used_days >= 0),
    ADD CONSTRAINT ck_leave_balances_available_days_nonnegative CHECK (available_days >= 0),
    ADD CONSTRAINT ck_leave_balances_used_days_within_total CHECK (used_days <= total_days),
    ADD CONSTRAINT ck_leave_balances_available_days_consistent
        CHECK (available_days = total_days - used_days);

ALTER TABLE leave_requests
    DROP CONSTRAINT ck_leave_requests_number_of_days;

ALTER TABLE leave_requests
    ADD CONSTRAINT ck_leave_requests_number_of_days CHECK (number_of_days > 0);
