# Offline synchronisation — Specification

Specification for moving the certificates database between installations that share no network,
agreed 2026-10-24 and to be implemented over the seven stages listed at the end. It builds on the
database repair (Flyway, `Auditable`, tombstones, `IdAllocator`, `ImageBlob`) described in
`.claude/rules/domain-persistence.md`.

## Decisions this rests on

| Question | Decision (user, 2026-10-24) |
|---|---|
| Who writes? | **One installation at a time.** The others only browse and print until the data reaches them. Which one that is, is an agreement between people, not a state the application passes around. |
| How does the data travel? | A file on a USB stick, between **any** two installations. No LAN, no Internet. |
| What is in the file? | **The whole database**, not a delta: every row of every synchronised table, tombstones included, the `id_sequence` rows and the image blobs. |
| An edit on an installation that is not the active one? | **Not blocked.** The next import detects it, reports the exact place (table, record, field), explains the problem and offers a resolution. |
| Mode of an installation | **Active by default**, set by the `sync.mode` property at every start (user, 2026-10-24). A browse-only computer is configured as `READ_ONLY`; nothing in a sync file changes a node's mode. |
| Safety net on import? | Before any import the application writes a backup of the current state (`.bak`, same format) to a directory set by a property; the newest 20 are kept (property). |
| Business numbers across installations? | Unchanged: one continuous sequence, carried in the file. The single-writer rule makes per-node ranges unnecessary. |
| Application version | The same on every installation; a file from a different schema version is refused. |
| Who operates it? | **Every logged-in user** (role `USER`) exports, imports and backs up on their own computer; only an admin restores a backup or issues a new node id (user, 2026-10-24). |
| Resolving "edited on both" (C2) | **Whole record**: the report shows both versions field by field and the operator keeps one side. No per-field merge in the first version (user, 2026-10-24). |
| Size | The production database with photos is **under 200 MB** today (user, 2026-10-24), so the file fits in memory; the exporter still streams because it is no harder, the importer may read the ZIP from the work directory entry by entry. |

The rejected alternatives, for the record: SymmetricDS (AGPL since Sep 2026, built for concurrent
writers we do not have), a delta file (needs every node to remember what it sent to whom, which a
stick travelling between arbitrary machines breaks), and wipe-and-load as the sync mechanism
(loses whatever the receiver typed; it survives only as the explicit *restore* of a backup).

## Vocabulary

- **Node**: one installation with its own database. Identified by a UUID generated on first
  start and a human name (`sync.node-name`, default: host name).
- **Active** / **read-only**: the node's *mode*, read from `sync.mode` at start-up (default
  `ACTIVE`). It is a property of the installation, nothing in the data; which active node is the
  one editing at the moment is an agreement between the operators, which is why the import must
  detect violations.
- **Sync file**: `<node-name>-<yyyyMMdd-HHmm>.sync`, a ZIP, full snapshot. A **backup** is the same
  format with the `.bak` extension, written by the application itself.
- **Origin**: the node that made the last change to a row (`origin_node` column).
- **Collision**: a row (or a unique value) whose local and file versions cannot both be kept
  without a human decision.

## File format (a contract: `SyncFormatTest` pins it)

```
manifest.json
tables/participants.jsonl        one JSON object per row, every column, column names as in SQL
tables/courses.jsonl
tables/...                       all tables listed below, including empty ones
blobs/<image uuid>.bin           image_blob.image_data
thumbs/<image uuid>.bin          image_blob.image_thumb_data (absent when the thumb is null)
```

`manifest.json`:

```json
{
  "format": 1,
  "schemaVersion": "10",            // highest applied Flyway version
  "appVersion": "3.0",
  "node": {"id": "…uuid…", "name": "biuro-1"},
  "exportedAt": "2026-10-24T15:32:10Z",
  "mode": "ACTIVE",                 // the exporter's mode at export time, informational
  "backup": false,                  // true in a .bak
  "rows": {"participants": 412, "courses": 1203, "...": 0},
  "blobs": 388
}
```

Synchronised tables, in foreign-key order (import inserts in this order, deletes in reverse):
`course_type`, `image`, `image_blob` (as files, see above), `participants`, `course_counter`,
`trainer`, `lecturer`, `technician`, `trainer_image`, `lecturer_image`, `technician_image`,
`courses`, `courses_trainers`, `courses_lecturers`, `courses_technicians`, `student`, `instructor`,
`id_sequence`. Not synchronised: `flyway_schema_history`, `sync_node`.

Rows are exported and imported **through JDBC, never through Hibernate**: the file carries
`version`, `created_at`, `updated_at`, `deleted_at` and `origin_node` verbatim, and an entity save
would overwrite all of them. Values are written as JSON strings for UUID, timestamps (ISO-8601
UTC with six fraction digits, `2026-01-02T03:04:05.123456Z`) and dates, Base64 strings for the
in-row binary columns of the legacy tables (`student_photo`, `instructor_photo1..4`), numbers
for the rest, `null` for NULL. The encoding follows the JDBC column type, not the Java class the
driver returns, so H2 and MariaDB write the same text; a UUID column is told apart by its type
name because H2 reports it as BINARY(16). Join-table rows have no audit
columns; they belong to their owning row (a course's crew, a person's photo set) and are replaced
together with it.

Streaming on both sides: the exporter writes the ZIP entry by entry straight into the HTTP
response or the backup file; the importer reads the staged file entry by entry. Nothing holds all
blobs in memory.

## Schema changes (`V10__sync.sql`)

```
sync_node        node_id uuid PK, node_name varchar(100),
                 last_export_at, last_import_at, last_import_file varchar(255), last_sync_at
<every synchronised row table>   origin_node uuid NULL      (10 tables: 8 custom, student, instructor)
```

- The migration inserts the single `sync_node` row with a random id (MariaDB `UUID()`).
  `SyncNodeService` also creates the row on `ApplicationReadyEvent` when it is missing, as
  `IdAllocator.ensureSequences` does, because the H2 test profile runs without Flyway. The mode
  is not stored: it comes from `sync.mode` on every start.
- **Never clone a database volume to set up a second computer**: the copy carries the same
  `node_id`, and two nodes with one id cannot tell their changes apart. A new installation starts
  empty and receives its data by importing a sync file. `/sync` offers "new node id" (admin) for the
  case where it happened anyway.
- Stamping needs the node id inside an entity callback. The listener is a Spring bean
  (`@EntityListeners(OriginStamper.class)` on `Auditable`); Boot registers Hibernate's
  `SpringBeanContainer`, so the listener is constructor-injected with `NodeIdentity`.
- `origin_node` is stamped by `Auditable` (`@PrePersist` / `@PreUpdate`) from a `NodeIdentity`
  bean. A soft delete goes through `@SQLDelete` and therefore keeps the previous origin; "deleted
  here" is read from `deleted_at` against `last_sync_at` instead. Rows written before V10 keep
  `NULL` and are treated as "origin unknown, not changed here" until they are next edited.
- `last_sync_at` is the later of the last export and the last import. Everything with
  `updated_at` or `deleted_at` after it, or created after it, is a **local change since the last
  exchange** (export or import).

## Modes

| | Active (`sync.mode=ACTIVE`, the default) | Read-only (`sync.mode=READ_ONLY`) |
|---|---|---|
| Forms | as today | work, but the page shows a persistent warning bar ("This computer is configured read-only; changes made here will be reported as collisions on the next import") and every successful save adds a warning toast |
| Export | allowed | allowed, with a warning on the page: it is the only way accidental edits reach the active computer, where they surface as collisions; the manifest carries `mode=READ_ONLY` and the report on the other side says so |
| Import | allowed | allowed |

The mode is a `SyncProperties` value, exposed to every template by a `@ControllerAdvice` model
attribute so `fragment.html` can render the bar, and shown on `/sync`. Changing it means
changing the property (or the environment variable) and restarting; there is no switch in the UI.

## Export

`GET /sync` shows node id, name, mode, last export, last import and the backup list.
`POST /sync/export` streams the file as a download and sets `last_export_at` and
`last_sync_at` **after** the stream completed without error. The export reads inside one `REPEATABLE READ`
transaction so the file is a consistent snapshot even if someone saves during the download.

## Import

Two steps, so nothing is written before a human has seen the report.

1. `POST /sync/import` uploads the file to `sync.work-dir` under a random token, runs the
   **analysis** and shows `/sync/import/{token}`: the manifest, counts, and the collision
   report. Staged files older than `sync.work-ttl` (default 2 h) are removed.
2. The report page lists every collision with a resolution to choose; `POST
   /sync/import/{token}/apply` refuses while any blocking collision has no resolution, then
   writes the backup, applies everything in one transaction, updates `sync_node` and shows a
   summary.

### Analysis

For every synchronised table, rows are matched by primary key. `self` is the local node id,
`since` is `sync_node.last_sync_at` (a node that never exchanged data has `NULL`, read as "every
local row counts as changed here"). Two facts drive the decision:

- **changed here** = the local row's `updated_at` or `deleted_at` is after `since`, or it was
  created after `since`;
- **changed there** = the file row's `origin_node` is not `self` and the file row differs from
  the local one. A file row whose origin *is* `self` is this node's own earlier state coming
  back (we exported it, nobody touched it, the stick returns), never a competing change.

`version` orders two states of one row; `updated_at` only breaks ties and is shown to people,
because the computers' clocks are not synchronised.

| Local | File | Outcome |
|---|---|---|
| absent | present | **insert** |
| present | absent | created locally after the file was made. On an active node: keep (notice "not in file"). On a read-only node: **collision C1 "created on a read-only computer"** |
| equal (same `version`, `updated_at`, `deleted_at`, `origin_node`) | — | nothing |
| not changed here, changed there | — | **update** (file wins; if the file's `version` is lower it is a stale stick: keep, notice "file is older than this computer for this row") |
| changed here, not changed there (file origin is `self`, or file row older than what we exported) | — | keep, no notice |
| changed here **and** changed there, live on both sides | — | **collision C2 "edited on both computers"** |
| changed here and there, deleted on one side, edited on the other | — | **collision C3 "deleted on one, edited on the other"** |
| deleted on both sides | — | nothing (keep the earlier `deleted_at`) |
| rows with `origin_node = NULL` (written before V10) | — | treated as "not changed here" and, in the file, as "changed there" only if they differ |

Then, over the merged result, before anything is written:

| Check | Collision |
|---|---|
| two different records (local vs file) with the same `participants_id`, `courses_id` or `course_counter_counter` | **C4 "number used twice"** |
| two different `course_type` rows with the same `code` (case-insensitive) | **C5 "course type code used twice"** |
| a file row referencing a participant, course type, counter, trainer, lecturer, technician or image that exists on neither side, or only as a tombstone when the referencing row is live | **C6 "dangling reference"** |
| file `schemaVersion` ≠ local | refused before analysis (the local value is `Flyway.info().current()`, read through an `ObjectProvider<Flyway>` because the H2 profile has no Flyway bean; tests pass the version explicitly) |
| file made by this very node | refused ("this is our own export"; a backup is restored on the restore page, not imported) |
| `id_sequence` | no collision: import takes `MAX(file.next, local.next, highest number in use + 1)` per sequence |
| image blobs | no collision: a blob is written only when `image_blob` has no row for that id; bytes never change after upload |

C1–C6 are *blocking*; notices are listed but need no decision.

### The report

One entry per collision:

- **where**: table in the user's words ("Participant"), the record as a person would name it
  (name, surname and number; course by number, participant and type; type by code), the field(s)
  that differ;
- **both values**, each with its `updated_at` and origin node name ("changed on *biuro-2* on
  24/10/2026 15:02"); for C3 which side deleted and when;
- **what went wrong**, one sentence per type, e.g. C2: "Both computers edited this record after the
  last exchange of data; only one installation should be editing at a time." C4: "Participant number 118
  was given to Anna Nowak on *biuro-1* and to Jan Kowalski on *biuro-2*; numbers are unique and are
  printed on certificates."
- **resolutions** (radio buttons): C1 keep local / discard local; C2 take file / keep local
  (whole record, no per-field merge); C3 keep deleted / keep edited; C4 keep
  file's number and renumber local to the next free / keep local's and renumber file's; C5 same
  with codes; C6 skip the referencing row / keep the referenced tombstone alive again.

Entries are grouped by table, blocking ones first. The same report is written as
`<file>.report.json` next to the backup for the record.

### Apply

In one JDBC transaction, in the table order above: inserts and updates of live rows, tombstone
updates, join-table rows replaced per owning row, blobs streamed in, sequences adjusted, and the
resolutions' renumberings last. `version` is written as in the file, so later optimistic checks
on every node keep working. The transaction also updates `sync_node`
(`last_import_at`, `last_import_file`, `last_sync_at`). Any failure rolls everything back;
the backup written just before stays. Thumbnails arrive with the file; `ThumbnailBackfill` is
triggered once afterwards for rows that came without one.

## Backups

- `SyncBackupService.writeBackup(reason)` runs the exporter into
  `${sync.backup-dir}/<node-name>-<yyyyMMdd-HHmmss>-<reason>.bak` (`reason` ∈ `before-import`,
  `before-restore`, `manual`), then deletes the oldest files beyond `sync.backup-keep`
  (`0` keeps every file). The exporter writes to a `.part` name and renames when complete, so a
  crash never leaves a half file that looks restorable; a second backup within the same second
  gets a `-2`, `-3` suffix instead of overwriting. Only `.bak` files in the directory count.
- Called before every import apply and every restore; also from a "Back up now" button.
- `/sync` lists the backups with size and date and offers **download** to everyone and
  **restore** to admins.
- **Restore** (`POST /admin/sync/restore/{file}`, `ROLE_ADMIN`) is *replace* mode: its own `before-restore`
  backup, then every synchronised table is emptied in reverse order and loaded from the file
  inside one transaction. It never runs the collision analysis; it answers "the last import was a
  mistake". The node keeps its id.

## Properties

```
sync.mode=${SYNC_MODE:ACTIVE}                         # ACTIVE | READ_ONLY, read at start
sync.node-name=${SYNC_NODE_NAME:}                      # empty = InetAddress.getLocalHost().getHostName()
sync.backup-dir=${SYNC_BACKUP_DIR:./sync-backups}     # a volume in docker-compose
sync.backup-keep=${SYNC_BACKUP_KEEP:20}               # 0 = never delete
sync.work-dir=${SYNC_WORK_DIR:${java.io.tmpdir}/serial-protocol-sync}
sync.work-ttl=2h
sync.max-file-size=2GB
spring.servlet.multipart.max-file-size=${sync.max-file-size}      # was 10MB
spring.servlet.multipart.max-request-size=${sync.max-file-size}
```

Raising the multipart limit moves the 10 MB per-photo cap into code (large parts go to the
servlet temp directory above `spring.servlet.multipart.file-size-threshold`, so memory is
unaffected):
`MultipartImages` rejects a part larger than `images.max-upload-size` (new, default 10MB) with
the same message the container used to produce.

## Security and UI

- `/sync/**` (status, export, import, apply, back up now, backup download) is open to every
  authenticated user, like the database pages. `/admin/sync/**` (restore, new node id) is
  `ROLE_ADMIN` through the existing `/admin/**` rule; the page renders those two controls inside
  `sec:authorize="hasRole('ADMIN')"`.
- One page, `templates/sync.html`, on the shared layout: status card, export form, import form,
  backup table. The report is `templates/sync-report.html`. A "Sync" entry joins the database
  dropdown in `fragment.html` for every user.
- The read-only bar is part of `fragment.html` and therefore on every page.

## Testing

- `SyncCollisionTest`: the analysis table above as unit tests on in-memory row pairs, one test per
  cell and per collision type.
- `SyncRoundTripTest` (H2): export → import into an empty database → identical rows, sequences
  and blobs; then edit both sides and import again → the expected report.
- `SyncFormatTest`: a golden `src/test/resources/golden/sync/` file is readable and the exporter
  reproduces its manifest and row shapes (the contract).
- `SyncMariaDbTest` (Testcontainers, skipped without Docker): the round trip on MariaDB, because
  UUID and timestamp types come back through JDBC differently from H2.
- `SyncPageTest`: the page as `user`, the mode bar, restore refused for `user` and allowed for
  `admin`, the two-step import.
- `ListPagesLoadNoImageBlobsTest` keeps guarding that none of this makes a list load blobs.

## Invariants to add to `CLAUDE.md` once implemented

- Sync and backup files are written and read through JDBC, never through entities; audit columns
  travel verbatim.
- The sync file format is a contract pinned by `SyncFormatTest`; a change bumps `format` in the
  manifest and the importer keeps reading the previous one.
- `sync_node` and `flyway_schema_history` are never exported.
- Import never writes before the report was shown and every blocking collision has a resolution.

## Implementation plan

One commit per stage, as for the repair; a stage starts on request.

| Stage | Scope | Done when |
|---|---|---|
| 1 | This specification; `sync.*` properties with defaults (`sync.mode` default `ACTIVE`); `NodeIdentity` + `SyncNodeService`; `V10__sync.sql`; `origin_node` stamping in `Auditable`; mode from the property shown as the bar in `fragment.html`; `/sync` page with status; nav entry | `SchemaMigrationTest` green; a test pins that a save stamps `origin_node` and that the bar shows with `sync.mode=READ_ONLY` |
| 2 | Exporter (JDBC, streaming ZIP, manifest, blobs); download endpoint; `SyncBackupService` with retention; "Back up now"; golden file | `SyncFormatTest`, backup retention test |
| 3 | Reader + analysis (`SyncAnalyzer`, `Collision` types C1–C6, notices); staging of uploads; report page with resolutions; per-photo size cap in code and raised multipart limit | `SyncCollisionTest` covers every row of the analysis table; report renders with the user-facing wording |
| 4 | Apply: one-transaction import with resolutions, renumbering, sequences, blobs, `sync_node` update, report JSON next to the backup | `SyncRoundTripTest` (H2) |
| 5 | Restore from a backup (replace mode), backup list with download | restore round trip on H2; the restore never runs analysis |
| 6 | `SyncMariaDbTest` on Testcontainers; large-file behaviour (streaming, memory); docker-compose volume for the backup dir; `.gitignore` | round trip on MariaDB 11.8 with a 3-digit number of blobs |
| 7 | Docs: this file cross-referenced, `.claude/rules/domain-persistence.md` and `CLAUDE.md` invariants, operator how-to (export, import, restore) in `docs/sync-howto.md` | mirrored blocks in `AGENTS.md` updated in the same commit |

Settled in stage 3:

- a tombstone that reappears live in the file (someone restored it elsewhere) is a C3 when it
  was deleted here after `since` and an update otherwise (`SyncCollisionTest`);
- "changed here" also requires `origin_node` to be set: a row written before V10 is never
  "changed here", so a node's first import after the upgrade cannot collide on history;
- the report page is `sync-report.html` under `/sync/import/{token}`; the upload is staged under
  `sync.work-dir` (`SyncStaging`, token in the URL, purged after `sync.work-ttl`) and analysed
  again on every view. The analysis is `SyncAnalyzer`, a pure class over `SyncRow` maps, so the
  decision table is tested without a database.
