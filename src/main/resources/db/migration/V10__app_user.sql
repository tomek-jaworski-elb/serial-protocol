-- Users move from memory into the database (entity.UserAccount). The table starts empty;
-- UserSeeder creates the two initial accounts from custom.server.credentials.* on the first
-- start only. Same version, timestamps and tombstone as every other table, so a deleted
-- username stays taken. Roles are a comma-separated list of SecurityRoles names ("ADMIN,USER").
-- The password is a DelegatingPasswordEncoder hash with its {id} prefix ("{bcrypt}$2a$10$...").

CREATE TABLE `app_user` (
    `app_user_uuid`                 UUID         NOT NULL,
    `app_user_username`             VARCHAR(64)  NOT NULL,
    `app_user_password`             VARCHAR(255) NOT NULL,
    `app_user_roles`                VARCHAR(100) NOT NULL,
    `app_user_enabled`              BIT          NOT NULL,
    `app_user_must_change_password` BIT          NOT NULL,
    `version`                       BIGINT       NOT NULL DEFAULT 0,
    `created_at`                    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`                    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `deleted_at`                    DATETIME(6)  NULL,
    PRIMARY KEY (`app_user_uuid`),
    CONSTRAINT `UK_app_user_username` UNIQUE (`app_user_username`)
) ENGINE=InnoDB;
