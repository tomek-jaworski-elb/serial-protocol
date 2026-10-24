-- Photos nothing points at any more. Until stage 3 the upload was committed before the
-- record that owned it, and a failed save relied on a compensating delete that could itself
-- fail; the rows it missed are still here. From now on an upload and its owner commit
-- together, so this is a one-off. Run as a SELECT COUNT(*) on a copy first if in doubt.

DELETE FROM `image`
WHERE NOT EXISTS (SELECT 1 FROM `participants`     p WHERE p.`image_uuid` = `image`.`image_uuid`)
  AND NOT EXISTS (SELECT 1 FROM `course_counter`   c WHERE c.`image_uuid` = `image`.`image_uuid`)
  AND NOT EXISTS (SELECT 1 FROM `trainer_image`    t WHERE t.`image_uuid` = `image`.`image_uuid`)
  AND NOT EXISTS (SELECT 1 FROM `lecturer_image`   l WHERE l.`image_uuid` = `image`.`image_uuid`)
  AND NOT EXISTS (SELECT 1 FROM `technician_image` n WHERE n.`image_uuid` = `image`.`image_uuid`);
