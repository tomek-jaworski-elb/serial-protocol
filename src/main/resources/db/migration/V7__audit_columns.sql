-- Version, timestamps and tombstone for every synchronisable table (entity.custom.Auditable,
-- @SQLDelete + @SQLRestriction on each entity). Existing rows start at version 0 with "now" as both timestamps;
-- deleted_at stays NULL for a live row. Deletes are UPDATEs from here on, so a row never
-- disappears from the table and a later merge can learn that it was removed and when.

ALTER TABLE `lecturer`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `trainer`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `technician`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `participants`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `courses`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `course_type`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `course_counter`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

ALTER TABLE `image`
    ADD COLUMN `version`    BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN `deleted_at` DATETIME(6) NULL;
