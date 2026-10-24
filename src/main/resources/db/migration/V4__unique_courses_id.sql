-- courses_id was declared unique in the mappings' intent but never in the schema, and
-- MAX(id)+1 computed at page render could hand the same number to two forms. Renumber any
-- duplicates (the first row of each number keeps it, the others move past the current
-- maximum) and then make the column unique for good.

UPDATE `courses` c
JOIN (
    SELECT d.`courses_uuid`,
           m.`max_id` + ROW_NUMBER() OVER (ORDER BY d.`courses_uuid`) AS `new_id`
    FROM (
        SELECT `courses_uuid`,
               ROW_NUMBER() OVER (PARTITION BY `courses_id` ORDER BY `courses_uuid`) AS `rn`
        FROM `courses`
    ) d
    CROSS JOIN (SELECT COALESCE(MAX(`courses_id`), 0) AS `max_id` FROM `courses`) m
    WHERE d.`rn` > 1
) r ON r.`courses_uuid` = c.`courses_uuid`
SET c.`courses_id` = r.`new_id`;

ALTER TABLE `courses` ADD CONSTRAINT `UK_courses_id` UNIQUE (`courses_id`);
