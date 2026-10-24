-- The legacy Student/Instructor tables stay in the product, so they get the same version,
-- timestamps and tombstone as the custom domain (entity.Auditable, @SQLDelete + @SQLRestriction):
-- without them a synchronisation could neither order nor delete their rows.

ALTER TABLE `student`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `instructor`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;
