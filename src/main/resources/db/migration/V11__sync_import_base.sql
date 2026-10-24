-- USB-stick synchronisation (docs/sync-spec.md): which node made the last imported file and when
-- that file was exported. A row from that node not touched since then was already merged here,
-- so an edit made here afterwards is not a collision with it. Restore clears both columns.

ALTER TABLE `sync_node` ADD COLUMN `last_import_node`        UUID        NULL;
ALTER TABLE `sync_node` ADD COLUMN `last_import_exported_at` DATETIME(6) NULL;
