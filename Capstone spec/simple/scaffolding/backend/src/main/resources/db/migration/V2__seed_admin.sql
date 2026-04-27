-- A placeholder admin row. The SUBJECT here will not match any real Google
-- account, so logging in will not grant you ADMIN by default.
--
-- To make YOUR Google account the admin: after your first login, run
--   UPDATE BANK_USERS SET ROLE = 'ADMIN' WHERE EMAIL = 'you@example.com';

INSERT INTO BANK_USERS (USER_ID, SUBJECT, EMAIL, DISPLAY_NAME, ROLE)
VALUES ('usr_seed_admin', 'google-sub-placeholder', 'admin@example.com', 'Demo Admin', 'ADMIN');
