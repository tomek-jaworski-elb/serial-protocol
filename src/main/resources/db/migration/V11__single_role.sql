-- Roles became a hierarchy (ADMIN > USER > OPERATOR) with one stored role per account:
-- an administrator is stored as ADMIN alone instead of the list "ADMIN,USER".
UPDATE `app_user` SET `app_user_roles` = 'ADMIN' WHERE `app_user_roles` = 'ADMIN,USER';
