-- One row per human-visible business number; IdAllocator reads it FOR UPDATE inside the
-- saving transaction. Seeded from what is in use, after V4 renumbered the courses.

CREATE TABLE `id_sequence` (
    `id_sequence_name` VARCHAR(32) NOT NULL,
    `id_sequence_next` BIGINT      NOT NULL,
    PRIMARY KEY (`id_sequence_name`)
) ENGINE=InnoDB;

INSERT INTO `id_sequence` (`id_sequence_name`, `id_sequence_next`)
SELECT 'participant',    COALESCE(MAX(`participants_id`), 0) + 1 FROM `participants`;
INSERT INTO `id_sequence` (`id_sequence_name`, `id_sequence_next`)
SELECT 'courses',        COALESCE(MAX(`courses_id`), 0) + 1 FROM `courses`;
INSERT INTO `id_sequence` (`id_sequence_name`, `id_sequence_next`)
SELECT 'course_counter', COALESCE(MAX(`course_counter_counter`), 0) + 1 FROM `course_counter`;
