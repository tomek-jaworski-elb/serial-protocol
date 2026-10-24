-- USB-stick synchronisation (docs/sync-spec.md). One row names this installation; every
-- synchronised row table records which installation wrote the row last (entity.custom.Auditable,
-- stamped by OriginStamper). Existing rows keep NULL: "origin unknown, not changed here".

CREATE TABLE `sync_node` (
    `node_id`          UUID         NOT NULL,
    `node_name`        VARCHAR(100) NOT NULL,
    `last_export_at`   DATETIME(6)  NULL,
    `last_import_at`   DATETIME(6)  NULL,
    `last_import_file` VARCHAR(255) NULL,
    `last_sync_at`     DATETIME(6)  NULL,
    PRIMARY KEY (`node_id`)
) ENGINE=InnoDB;

-- The name is replaced from sync.node-name (or the host name) at every start.
INSERT INTO `sync_node` (`node_id`, `node_name`) VALUES (UUID(), 'node');

ALTER TABLE `course_type`    ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `image`          ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `participants`   ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `course_counter` ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `trainer`        ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `lecturer`       ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `technician`     ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `courses`        ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `student`        ADD COLUMN `origin_node` UUID NULL;
ALTER TABLE `instructor`     ADD COLUMN `origin_node` UUID NULL;
