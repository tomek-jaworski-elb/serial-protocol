-- course_type was the last table keyed by a database-generated number (AUTO_INCREMENT),
-- which two databases cannot generate independently. It moves to a UUID key like every
-- other table; courses follows it through a new foreign-key column. The old FK name is the
-- one Hibernate derived (see V1__baseline.sql); an installation created by a different
-- Hibernate line may carry another name — check SHOW CREATE TABLE courses on a copy first.

ALTER TABLE `course_type` ADD COLUMN `course_type_uuid` UUID NULL;
UPDATE `course_type` SET `course_type_uuid` = UUID();
ALTER TABLE `course_type` MODIFY COLUMN `course_type_uuid` UUID NOT NULL;

ALTER TABLE `courses` ADD COLUMN `courses_course_type_uuid` UUID NULL;
UPDATE `courses` c
JOIN `course_type` t ON t.`course_type_id` = c.`courses_course_type_id`
SET c.`courses_course_type_uuid` = t.`course_type_uuid`;

ALTER TABLE `courses` DROP FOREIGN KEY `FK4gv924oowpglsih2wlrjaftdb`;
ALTER TABLE `courses` DROP COLUMN `courses_course_type_id`;
ALTER TABLE `courses` MODIFY COLUMN `courses_course_type_uuid` UUID NOT NULL;

ALTER TABLE `course_type` MODIFY COLUMN `course_type_id` BIGINT NOT NULL;
ALTER TABLE `course_type` DROP PRIMARY KEY;
ALTER TABLE `course_type` DROP COLUMN `course_type_id`;
ALTER TABLE `course_type` ADD PRIMARY KEY (`course_type_uuid`);

ALTER TABLE `courses` ADD CONSTRAINT `FK_courses_course_type`
    FOREIGN KEY (`courses_course_type_uuid`) REFERENCES `course_type` (`course_type_uuid`);
