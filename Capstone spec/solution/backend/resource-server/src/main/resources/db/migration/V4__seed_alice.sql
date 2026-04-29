-- Seed row for the mock-auth demo customer 'alice'.
-- Spring Authorization Server sets sub = username, so subject = 'alice'.
-- JwtAuthConverter finds this row on login and assigns ROLE_CUSTOMER.
--
-- This block is safe to run even if alice already logged in once:
--   - If the BANK_USERS row already exists (from a prior login), we skip the INSERT.
--   - Accounts are inserted only if they do not already exist, linked to
--     whichever USER_ID owns subject='alice'.

DECLARE
    v_user_id BANK_USERS.USER_ID%TYPE;
BEGIN
    -- Ensure alice has a BANK_USERS row (insert only if absent)
    BEGIN
        INSERT INTO BANK_USERS (USER_ID, SUBJECT, EMAIL, DISPLAY_NAME, ROLE)
        VALUES ('usr_seed_alice', 'alice', 'alice@mock.local', 'Alice Demo', 'CUSTOMER');
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN NULL; -- already exists, skip
    END;

    -- Resolve the actual USER_ID for subject='alice' (may differ from usr_seed_alice)
    SELECT USER_ID INTO v_user_id FROM BANK_USERS WHERE SUBJECT = 'alice';

    -- Insert checking account if not already present
    BEGIN
        INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
        VALUES ('acc_alice_checking', v_user_id, 'CHECKING', 'USD', 3200.00);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN NULL;
    END;

    -- Insert savings account if not already present
    BEGIN
        INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
        VALUES ('acc_alice_savings', v_user_id, 'SAVINGS', 'USD', 8750.00);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN NULL;
    END;
END;
/
