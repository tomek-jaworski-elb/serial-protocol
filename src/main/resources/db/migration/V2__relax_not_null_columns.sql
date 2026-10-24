-- Moved from SchemaMigrationRunner, which used to run these on every start and swallow the
-- errors. Older installations created these columns NOT NULL; the mappings have been
-- nullable for a long time. MODIFY COLUMN is idempotent, so a fresh database (where V1
-- already created them nullable) runs it as a no-op.

ALTER TABLE `course_type` MODIFY COLUMN `course_type_long_description` VARCHAR(5000) NULL;

ALTER TABLE `lecturer` MODIFY COLUMN `lecturer_email`        VARCHAR(100)  NULL;
ALTER TABLE `lecturer` MODIFY COLUMN `lecturer_nickname`     VARCHAR(100)  NULL;
ALTER TABLE `lecturer` MODIFY COLUMN `lecturer_notes`        VARCHAR(1000) NULL;
ALTER TABLE `lecturer` MODIFY COLUMN `lecturer_phone_number` VARCHAR(26)   NULL;
ALTER TABLE `lecturer` MODIFY COLUMN `lecturer_address`      VARCHAR(300)  NULL;

ALTER TABLE `trainer` MODIFY COLUMN `trainer_email`        VARCHAR(100)  NULL;
ALTER TABLE `trainer` MODIFY COLUMN `trainer_nickname`     VARCHAR(100)  NULL;
ALTER TABLE `trainer` MODIFY COLUMN `trainer_notes`        VARCHAR(1000) NULL;
ALTER TABLE `trainer` MODIFY COLUMN `trainer_phone_number` VARCHAR(26)   NULL;
ALTER TABLE `trainer` MODIFY COLUMN `trainer_address`      VARCHAR(300)  NULL;

ALTER TABLE `technician` MODIFY COLUMN `technician_email`        VARCHAR(100)  NULL;
ALTER TABLE `technician` MODIFY COLUMN `technician_nickname`     VARCHAR(100)  NULL;
ALTER TABLE `technician` MODIFY COLUMN `technician_notes`        VARCHAR(1000) NULL;
ALTER TABLE `technician` MODIFY COLUMN `technician_phone_number` VARCHAR(26)   NULL;
ALTER TABLE `technician` MODIFY COLUMN `technician_address`      VARCHAR(300)  NULL;

ALTER TABLE `participants` MODIFY COLUMN `participants_birth_date`   DATE          NULL;
ALTER TABLE `participants` MODIFY COLUMN `participants_email`        VARCHAR(100)  NULL;
ALTER TABLE `participants` MODIFY COLUMN `participants_nickname`     VARCHAR(100)  NULL;
ALTER TABLE `participants` MODIFY COLUMN `participants_notes`        VARCHAR(1000) NULL;
ALTER TABLE `participants` MODIFY COLUMN `participants_phone_number` VARCHAR(26)   NULL;
ALTER TABLE `participants` MODIFY COLUMN `participants_address`      VARCHAR(300)  NULL;
