# Soft delete, trash and optimistic locking

The seven records of the custom domain — `Participant`, `Lecturer`, `Trainer`, `Technician`, `CourseType`,
`CourseCounter`, `Courses` — are never removed by a user. Delete marks the row; it disappears from every
page a user sees; an administrator finds it in the trash and can restore it or purge it for good. Edits are
protected against overwriting someone else's change (`@Version`). Backups carry deleted rows and restore them
as deleted. `Image` has neither: a photo follows its owner and is removed only when nothing uses it.

## How a delete works

- `SoftDeletable` is the shared `@MappedSuperclass`: `deleted_at`, `deleted_by`, `version`. A row is active
  while `deleted_at IS NULL`. `PersonBase` extends it, so do `CourseType`, `CourseCounter` and `Courses`. Its
  fields stay out of `equals`/`hashCode`: entities live in `Set`s.
- `@FilterDef(name = "activeOnly", autoEnabled = true, applyToLoadByKey = true)` on that class is switched on
  for every session and also applies to lookups by id. `findAll`, `findById`, `existsById`, derived queries,
  JPQL, `count()` and the "referenced by courses" guards never see a deleted row. Bulk JPQL
  (`deleteAllInBatch`) honours the filter too.
- The real delete is `SoftDeleteSupport.softDelete(entity, repository)`: `markDeleted(currentUser())`,
  `saveAndFlush`, then **detach**. The session answers `find` from its cache before the filter can get in the
  way; without the detach, a record deleted and read again in the same transaction would still be returned.
  `currentUser()` is the signed-in name (truncated to 100) or `"system"`.
- Each entity also declares `@SQLDelete` (`UPDATE … SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'system',
  version = version + 1 WHERE pk = ? AND version = ?`), a safety net for a stray `repository.delete()`. It
  covers the row only: Hibernate clears the join tables the entity owns before running it. No production path
  calls `delete` on these entities.
- `SoftDeleteSupport.withDeleted(Supplier)` / `runWithDeleted(ThrowingRunnable)` switch the filter off for the
  rest of the current transaction's work and back on afterwards. They need an active transaction (outside one
  every repository call gets its own session), and they are re-entrant: an inner call leaves the switch alone.
  **Entities loaded under them stay in the session cache**; detach them before handing control to code that
  assumes it only sees active rows (`DeletedRecordsService.forget` is the pattern).

## Rules on the user side

- Guards: a participant, course type, counter, trainer, lecturer or technician cannot be deleted while an
  *active* course uses it ("This trainer is used in existing courses and cannot be deleted."). A deleted
  course blocks nothing. The seven delete handlers show that reason; the confirm dialog says an administrator
  can restore the record.
- `nextId()` (participant, course) and `nextCounter()` run under `withDeleted`, so an id or counter of a
  deleted row is never handed out again.
- Unique values (`participants_id`, `course_type_code`, `course_counter_counter`) are checked twice on create
  and edit: among active rows ("already exists") and among deleted ones ("… is used by a deleted record. Ask
  an administrator to restore or purge it."). The database keeps them unique across deleted rows as well.
- A course may only point at active rows. `CoursesService.activeParent` loads each parent with `em.find`
  (a deleted one is simply not found: "The selected participant does not exist or has been deleted.") and then
  `em.lock(parent, OPTIMISTIC)`: a parent deleted while the course is being saved makes the commit fail
  instead of leaving a course whose parent the list can no longer load. `getReferenceById` would do neither.
  `lock()` comes after `find()` on purpose — a lock mode passed to `find()` is applied to everything loaded
  with the parent, and a photo has no version to lock.
- Editing a record someone deleted meanwhile is a plain "not found".
- Owners' photos stay with a deleted record (needed for restore). Replacing a photo on edit still removes the
  old one.

## The trash (`/admin/deleted/{entity}`)

`AdminDeletedController` + `admin-deleted.html` + `DeletedRecordsService`, under the existing `/admin/**`
rule (`ROLE_ADMIN`). `DeletedKind` is the vocabulary: path segment (`participants`, `lecturers`, `trainers`,
`technicians`, `course-types`, `course-counters`, `courses`), labels, entity class, id attribute, key type
(course types are the only kind addressed by a `Long`). Menu entry "Deleted records", admins only.

- **List**: JPQL `where e.deletedAt is not null`, newest first, paginated, read-only. Rows are mapped inside
  the filter-off block, because a deleted course's parents load with it.
- **Restore**: refused while a parent of a course is still in the trash; the message names them the way their
  own tab does ("Restore participant Kowalski Jan, course type T-1 first."). Nothing else can be blocked:
  unique values are held by the deleted row itself.
- **Purge**: refused while any course, deleted or not, still points at the record ("A course still uses this
  record (deleted courses count). Purge or restore that course first."). Otherwise photo sets and a course's
  staff links are cleared and flushed, the row goes with a JPQL bulk delete (bypasses `@SQLDelete`, binds the
  key through Hibernate, so the same statement runs on H2 and MariaDB), then every photo the record owned is
  removed unless something — deleted owners included — still uses it. Single-record only, no retention.
- Every hidden row the service loaded is detached before it returns, on success and on refusal.
- Audit: `INFO` on restore, `WARN` on purge, with the user.

## Optimistic locking

- `version` (`@Version`, `BIGINT DEFAULT 0` so `ddl-auto=update` fills old rows) lives on `SoftDeletable`.
- Every DTO carries `version`; the seven edit forms send it in a hidden field filled from `data-version` on the
  row's Update button; the mappers copy it both ways. Create carries none.
- `VersionGuard.check(type, id, current, submitted)` runs in all seven update paths after the entity is
  loaded. No version → `IllegalArgumentException` ("The form did not say which version…"). A different one →
  Spring's `ObjectOptimisticLockingFailureException`, the same type Hibernate's own flush-time check throws,
  so both land in one `WebExceptionHandler` method: flash "This record was changed by someone else in the
  meantime. Reload the page and try again." and a redirect to the page the form came from (`Referer`). The
  nine handlers that save an edited or newly linked record **rethrow** that exception; a new handler must too.
- Update paths `saveAndFlush`, so the DTO they return carries the new version and validation fires at once.
- Delete, restore and purge bump the version and never check it.

## Backup

- Schema `1.1`. Every soft-deletable element is a `BackupRow<T>`: the form DTO unwrapped plus `deletedAt`
  and `deletedBy` — the 1.0 shape with two extra properties, so `READABLE_SCHEMA_VERSIONS` is `{1.0, 1.1}`
  and a 1.0 file restores with every row active. `version` is `@JsonIgnore`d; restored rows start at 0.
- The writer runs under `runWithDeleted`. Restore sets the deletion state on each persisted entity and needs
  no filter handling (`persist`/`getReference` never load). `clearAllTables()` is plain native `DELETE`s,
  children first, join tables explicitly — `deleteAll()` would only mark rows and `deleteAllInBatch()` would
  skip the marked ones.
- `@AssignedOrGeneratedUuid` on the four uuid ids (`PersonBase`, `CourseCounter`, `Courses`, `Image`) keeps a
  uuid the application assigned and generates one otherwise. `@GeneratedValue(strategy = UUID)` and
  Hibernate's `@UuidGenerator` both treat an entity that already carries an id as detached, which made every
  restore of a non-empty backup fail. A row that already exists under the uuid still fails on the primary key.

## Deployment

1. Take a backup through `/db-utils` before the first start of this version. The schema gains `deleted_at`,
   `deleted_by` and `version` on seven tables (`ddl-auto=update`, all nullable or defaulted, no
   `SchemaMigrationRunner` entry). An older build started against the new schema would hard-delete rows this
   one only hides, and would not know the 1.1 backup format.
2. `markDeleted` stamps JVM time, `@SQLDelete` database time. If the app and MariaDB containers run in
   different zones the two can differ by the offset; display-only, and the second path is the one nothing
   uses.

### Rollback

Going back to the previous build is a data decision first and a deployment second. The previous build has no
filter, so every row this version only hides becomes an active record again the moment it starts, and its
`DELETE` removes rows for good.

1. Decide the trash: an administrator restores what should come back and purges the rest on
   `/admin/deleted/{entity}` (there is no "empty trash"; purge each record). Whatever is still in the trash
   reappears in the lists after the rollback.
2. Keep the 1.0 backup from deployment step 1. A backup taken by this version is schema 1.1, which the
   previous build does not read.
3. Start the previous build. `ddl-auto=update` never drops a column, so `deleted_at`, `deleted_by` and
   `version` stay; the old entities do not map them, rows the old build inserts get `NULL`, `NULL` and the
   default `0`, and a later upgrade picks them up as active. Nothing in the schema has to be reverted.

### MariaDB smoke checklist (H2 covers the tests; this is the manual pass)

Run `docker compose up --build`, sign in as a user, then as admin.

1. Start on an existing database: the log shows the three new columns being added and no
   `SchemaMigrationRunner` errors; every list page still loads.
2. Delete a trainer without courses → it vanishes from the list; `/admin/deleted/trainers` shows it with
   your user name and a timestamp.
3. Try to delete a participant that has an active course → the reason appears as the error toast.
4. Restore the trainer → it is back; its photos are still there.
5. Delete a course, then its participant; restore the course → refused, naming the participant; restore the
   participant, then the course → both back.
6. Purge: delete and purge a course type that no course uses → gone from the trash; purge a participant that a
   deleted course still points at → refused.
7. Create a course type with the code of a deleted one → "used by a deleted record".
8. Open the same record in two tabs, save in both → the second gets "changed by someone else".
9. `/db-utils`: backup, delete something, restore the backup → the deleted record is back in the trash, not in
   the list; the DB Utils page shows schema 1.1.
10. Restore a backup taken before this version (schema 1.0) → it loads, all rows active.

## Testing notes

- Tests run on H2. Anything that checks visibility after a delete needs either a fresh transaction
  (`TransactionTemplate` with `Propagation.NOT_SUPPORTED` on the class, as `SoftDeleteFoundationTest` does)
  or relies on `softDelete`'s detach.
- A commit-time check (the locked parents of a course) only fires when a transaction actually commits, never
  inside a rolled-back `@DataJpaTest` transaction.
- `DeletedRecordsServiceTest`, `SoftDeleteUserPathTest`, `OptimisticLockingTest`, `StaleFormTest`,
  `AdminDeletedControllerTest` and `BackupRoundTripTest` are the executable specification of this document.
