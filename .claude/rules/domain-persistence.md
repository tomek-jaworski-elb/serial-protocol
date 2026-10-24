---
paths:
  - "src/main/java/**/{entity,repository,dto,mappers,rest,authorization,exception}/**"
  - "src/main/java/**/service/db/**"
  - "src/main/java/**/service/pdf/**"
  - "src/main/java/**/configuration/TomcatServerConfiguration.java"
  - "src/main/resources/db/**"
  - "src/main/resources/templates/custom/**"
  - "src/main/resources/application*.properties"
  - "src/test/resources/application*.properties"
  - "src/test/java/**/{dto,mappers,rest,service}/**"
  - "docs/custom-domain-analysis.md"
  - "docs/pdf-report-plan.md"
---

# Persistence, custom domain, images, REST, security and PDF

Loaded when you touch entities, repositories, DTOs, mappers, the custom-domain services, Flyway migrations, the custom templates or the application properties. The schema, soft-delete, IdAllocator and image-blob invariants are in `CLAUDE.md`.

## Persistence
- **Main profile**: MariaDB (`jdbc:mariadb://${DB_HOST_IP:mariadb}:3306/certificates`), credentials via env vars `DB_USER`/`DB_PASSWORD`. `spring.jpa.open-in-view=false`.
- **Test profile**: H2 in-memory (`src/test/resources/application.properties`), `server.port=8081`, `ddl-auto=create`, `spring.flyway.enabled=false`, `open-in-view=false` (same as production, so the suite can catch `LazyInitializationException`). Never hardcode prod DB assumptions in tests. Test auth: `user:user`.
- **Schema migration is Flyway** (`flyway-core` + `flyway-mysql`, scripts in `src/main/resources/db/migration`). `V1__baseline.sql` is the schema as Hibernate generates it for MariaDB 11.8 from the mappings; `V2__relax_not_null_columns.sql` carries the `ALTER TABLE … MODIFY COLUMN` statements that `SchemaMigrationRunner` used to issue on every start. Production runs `ddl-auto=validate` with `spring.flyway.baseline-on-migrate=true` and `baseline-version=1`: an installation built by the old `ddl-auto=update` is marked as version 1 without executing V1 and then receives V2 and later; a fresh database runs everything. The H2 test profile keeps `ddl-auto=create` with Flyway disabled; `SchemaMigrationTest` (`@Testcontainers`, `mariadb:11.8`, skipped without Docker) applies every script to an empty MariaDB and lets Hibernate validate the result. Every mapping change needs a new `V<n>__*.sql`; never edit an applied script. Before the first deployment with `validate`, start the app once against a copy of the production database: the baseline describes the mapped schema, and an installation grown by `ddl-auto=update` may differ in details (old columns, type widths) that need a corrective migration.
- Two domains coexist: legacy `Student`/`Instructor` (`service.db`, REST) and the custom training domain (`entity.custom`). The legacy one stays by decision: its tables carry the same audit columns and tombstones (`V9__legacy_audit_columns.sql`), its services are transactional, and its `int` keys are assigned by the sending system (unique by origin). `StudentRepository.findLatest` is JPQL so tombstones stay hidden.

## REST API
`RestStudent` under `/api/v1`: `POST /students`, `POST /instructors`, `GET /instructors` (JSON in/out). Secured by the `/api/**` URL rule only; the malformed class-level `@PreAuthorize` is gone (method security was never enabled). Each POST stores its batch atomically through `StudentService.saveNew` (skips known ids) / `InstructorService.saveNew` (skips known surname+name) and returns the stored ids; a failing row now fails the whole batch instead of being logged and skipped.

## Security
In-memory users built from `custom.server.credentials.*` properties (regular + admin). Protected paths: `/name-service`, `/instructor-service`, `/api/**`, `/admin/**`, `/pdf/**`, and all custom domain CRUD endpoints (`/trainer-service/**`, `/lecturer-service/**`, `/technician-service/**`, `/participant-service/**`, `/courses-service/**`, `/course-type-service/**`, `/course-counter-service/**`).

HSTS header (`max-age=31536000; includeSubDomains; preload`) is set in `SecurityConfig`. SSL via PKCS12, port 443.

HTTP→HTTPS redirect: configurable via `server.http.redirect.enabled`, `server.http.ports`, `server.port`. `TomcatServerConfiguration` validates ports at startup — throws `IllegalStateException` on collision.

## Custom domain module (`entity.custom`)
Full reference: [`docs/custom-domain-analysis.md`](docs/custom-domain-analysis.md)

Entity hierarchy:
```
Auditable (@MappedSuperclass) — version (@Version), createdAt, updatedAt, deletedAt; every entity below extends it and carries @SQLRestriction("deleted_at is null") + an @SQLDelete UPDATE (never Hibernate's @SoftDelete)
PersonBase (@MappedSuperclass, extends Auditable) — uuid (@Id), name, surname, notes, nickname, email, phoneNumber, address
  ├── Lecturer   @ManyToMany images + primaryImageUuid
  ├── Trainer    @ManyToMany images + primaryImageUuid
  ├── Technician @ManyToMany images + primaryImageUuid
  └── Participant — id: Long (business key, unique), birthDate, image @OneToOne
CourseType  — id: UUID (@Id, column course_type_uuid), code (unique), description
CourseCounter — uuid (@Id), counter (unique), image @OneToOne
Courses     — uuid (@Id), id: Long, participant @ManyToOne, courseType @ManyToOne, date range, trainers/lecturers/technicians @ManyToMany
Image       — id: UUID, contentType, thumb metadata (no bytes)
ImageBlob   — id = Image id (assigned), data + thumbData @Lob; not Auditable, never loaded by a list
```

CRUD shape is the same for every entity: `GET /<x>-service`, `POST /<x>-service/add`, `POST /<x>-service/update`, `POST /<x>-service/delete/{key}`. The key is `{uuid}` for participant, course-counter and courses and `{id}` for trainer, lecturer, technician and course-type. `POST /courses-service/add-participant` is the quick-add from the participant view: it submits only `participantUuid`, `courseTypeId`, `startDate` and `endDate`, so trainers, lecturers, technicians and counter stay empty.

Key conventions:
- UUID is DB identity everywhere; numeric `id` on `Courses`/`Participant` and `counter` on `CourseCounter` are human-visible business numbers. A `null` number on save is reserved by `IdAllocator.next(sequence, highestInUse)` from the `id_sequence` row of that sequence (`SELECT … FOR UPDATE`, seeded by `V5__id_sequences.sql`, created on first use where the migration has not run), stepping over any higher number typed by hand. `nextId()` / `nextCounter()` are `IdAllocator.peek` and feed only the forms' placeholders. `courses_id` is unique (`V4__unique_courses_id.sql` renumbered duplicates first) and is never changed by an update.
- Person-like DTOs use `id` for UUID in `LecturerDTO`/`TrainerDTO`/`TechnicianDTO`; `ParticipantDTO` uses `participantUuid` (avoids collision with `Long id`).
- All `@AttributeOverride` for UUID includes `nullable=false, updatable=false, unique=true`.
- Date format is `dd/MM/yyyy` (EU). HTML forms use Flatpickr on `type="text"` — never `type="date"`.
- `@InitBinder` in `CustomDBController` registers an anonymous `PropertyEditorSupport` for `String.class` only, turning blank input into `null` — required for optional field validation. (It is *not* `StringTrimmerEditor`; that class appears nowhere in the code. Non-`String` types such as `UUID` go through Spring's default converters.)
- **Validation runs on the DTOs, before any service is called**: every `CustomDBController` POST handler binds `@Valid @ModelAttribute` plus `BindingResult` and, on errors, redirects with a flash `errorMessage` of `field message` pairs (`validationMessage(...)`). The constraints on `dto.custom` mirror the entity columns (`@NotBlank`/`@Size` on person fields, `@Email`, `@Past birthDate`, `@Positive` numbers, `@NotNull` course references and dates, `CoursesDTO.isDateRange()` as `@AssertTrue`). The flush-time `ConstraintViolationException` handling stays as a safety net only. `CustomDBControllerValidationTest` pins the messages.
- `CoursesMapper.mapToEntity()` is deprecated; use `CoursesService.buildCourses()` with `repository.getReferenceById()`.
- `CoursesDTO` has dual counter fields: `courseCounterUuid` (read-only, set by mapper) and `counter` (Long, from forms). `resolveCourseCounter()` prefers UUID.
- Participant deletion is guarded: throws `IllegalStateException` if linked courses exist.
- **Deletes are tombstones** (`V7__audit_columns.sql`): `deleteById` becomes `UPDATE … SET deleted_at`, rows never leave the table, and `count()`/`existsById`/`findById` no longer see them. Soft-deleted images keep their blobs; a purge is a separate, later decision. **Join-table rows are hard-deleted**: soft-deleting a course removes its `courses_trainers`/`courses_lecturers`/`courses_technicians` rows, and dropping a photo from a person removes the `*_image` row. Chosen deliberately (Hibernate's `@SoftDelete` tombstoned them and then collided on re-add); a future merge therefore treats a course's crew and a person's photo set as part of the owning row, versioned by it, not as rows of their own. Updates are load-and-copy everywhere (`ParticipantService.updateByUuid`, `CoursesService.applyTo`, `CourseTypeService.update`), never a merge of a detached DTO-built entity, so the version check and a "not found" for a deleted record both work. Every DTO carries `version`; the templates post it from `data-version` into a hidden input, and the controllers turn `OptimisticLockingFailureException` into a toast.
- `CourseType.code` is unique at DB level and pre-checked in `CourseTypeService` through the native, case-insensitive `countWithCode()` / `countWithCodeExcludingId()` (native so a tombstoned type's code still counts as taken).
- `CourseCounterDTO` is a Java `record` (Spring 6.1+ constructor binding required).

## Images and avatars
- Uploads are capped at 10 MB (`spring.servlet.multipart.max-file-size` and `max-request-size`). The controllers only read the multipart parts (`MultipartImages` → `UploadedImage`); every service has an upload-aware overload (`save(dto, uploads)`, `update(dto, uploads, removals)` for people, `save(dto, upload)` / `update(dto, upload, removeImage)` for participant and counter) that stores the bytes and the record in one transaction. People are limited to `ImageService.MAX_IMAGES_PER_PERSON` (6) photos, checked by `ImageService.assertWithinLimit`, whose `ImageLimitExceededException` is the only message the toasts show verbatim. The request carries the images to *remove*, never the ones to keep (a broken editor then removes nothing). `V3__delete_orphan_images.sql` removed the orphans the old upload-then-save order had left behind.
- One endpoint serves everything: `GET /custom/image/{uuid}`, optionally `?size=thumb` — the *only* accepted value, anything else is a 400 so a single URL cannot spawn unbounded resize work. `ThumbnailGenerator.MAX_EDGE=160`.
- ETags derive from the uuid alone (`"{uuid}-orig"` / `"{uuid}-thumb-{ThumbnailGenerator.VERSION}"`) because image bytes never change — replacing a photo creates a new `Image` row. Bump `ThumbnailGenerator.VERSION` when thumbnail rendering changes; that invalidates thumbs only, not originals. A 304 still verifies existence, otherwise deleted photos would stay cached forever. A bump does not touch the thumbnails on GET: `ThumbnailBackfill` rebuilds every stale row once after start-up (`images.thumbnail.backfill-on-start`, default `true`), and until it has run a stale row is scaled per request.
- `Cache-Control: private, no-cache` is set explicitly and survives Spring Security's `no-store` writer, which skips whenever the header is already present.
- **Reading never touches blobs.** The bytes of a photo are a separate row, `ImageBlob` (`image_blob`, `V8__image_blob_table.sql`), keyed by the `Image` uuid with no JPA association between them; `Image` carries only content type and thumbnail metadata. `ImageService.getOriginal` / `getThumbnail` / `refreshThumbnail` are the only readers of `ImageBlobRepository`, so `resolveImages`, `deleteRemovedImages` and every list are metadata-only. `Trainer`/`Lecturer`/`Technician` services fetch image ids for a whole page with one `findImageLinks(...)` query (`repository.custom.ImageLink`) and the mappers take those ids as a parameter instead of calling `getImages()`; `Participant.image`, `CourseCounter.image` and the to-one associations of `Courses` are LAZY and the mappers only read ids off the proxies. `hibernate.default_batch_fetch_size=50` turns the remaining lazy loads into one IN query per page. `CoursesService.findByParticipantUuids` / `findByCourseCounterUuids` replace the old one-query-per-row loops. `ListPagesLoadNoImageBlobsTest` asserts zero `ImageBlob` loads on every list page and a row-independent statement count.
- Multi-image people (`Lecturer`/`Trainer`/`Technician`) carry a `primaryImageUuid` pointer; `dto.custom.PrimaryImage` is the single source of truth for resolving it, shared by services (on write) and DTOs (on render). Sort candidate UUIDs **lexicographically by `toString()`**, never `UUID.compareTo` (it compares both halves as signed longs). An absent pointer in a request means "leave it alone", and fallback prefers pre-existing photos so a newly uploaded one never silently becomes the avatar.

## USB-stick synchronisation (`service/db/sync`)
Spec: [`docs/sync-spec.md`](docs/sync-spec.md). A sync file is a ZIP (`manifest.json`, `tables/<name>.jsonl`, `blobs/`, `thumbs/`) written by `SyncExporter` **through JDBC**, never through entities, so `version`, the audit columns and `origin_node` travel verbatim. `SyncTables.TABLES` is the FK order and the row order per table; `SyncValues` decides the JSON encoding by JDBC type. `src/test/resources/golden/sync/` is the format contract: `SyncFormatTest` exports `SyncFixture` and compares every entry byte for byte, so a format change edits the golden files first. Regenerate them only with `SyncGoldenWriter` (`-Dgolden.write=true`) and review the diff. `SyncBackupService` keeps `.bak` files (same format, `backup=true`) under `sync.backup-dir`, newest `sync.backup-keep`. `SyncNodeService` caches the node id for `OriginStamper` and refreshes it on every `current()`.

## PDF reports
`PdfReportController` (`/pdf/**`, one POST per entity type) → `PdfReportService` with `PdfPageLayout`, `PdfHeaderFooterEvent`, `PdfColorScheme`. Configuration prefix: `pdf.report.*` (`PdfReportProperties`). Max records: `pdf.report.max-records=100`. Fonts are bundled in `src/main/resources/fonts` (DejaVuSans — required for Polish glyphs). Design notes and library comparison: [`docs/pdf-report-plan.md`](docs/pdf-report-plan.md).
