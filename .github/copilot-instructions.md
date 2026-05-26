# Copilot instructions for `serial-protocol`

## Build, test, and lint commands
- Full test suite: `.\mvnw.cmd clean test`
- Run one test class: `.\mvnw.cmd -Dtest=MapControllerTest test`
- Run one test method: `.\mvnw.cmd -Dtest=MapControllerTest#test_IndexEndpoint test`
- Build JAR (with tests): `.\mvnw.cmd clean package`
- Build JAR (skip tests, same behavior as `build-jar.bat`): `.\mvnw.cmd clean package -Dmaven.test.skip=true`
- Run locally from Maven: `.\mvnw.cmd spring-boot:run`
- Run packaged app: `java -jar .\target\serial-protocol-1.9.jar`
- Docker stack (app + MariaDB): `docker compose up --build` (or `.\start_docker.sh` in bash environments)

No dedicated lint command is configured in `pom.xml` (no Checkstyle/PMD/SpotBugs plugin execution).

## High-level architecture
- **Boot flow:** `SerialProtocolApplication` starts Spring, then explicitly calls `StartUp.start()` to open serial ports and (optionally) check external REST connectivity.
- **Serial ingest pipeline:** `SerialController` opens ports discovered by jSerialComm and filtered by `rs.comports`; `SerialPortListenerImpl` receives delimited frames (`rs.message_delimiter`), publishes raw payload to `/rs`, translates payload to `ModelTrackDTO`, and publishes JSON to `/json`.
- **Protocol translation:** `MessageTranslator` dispatches by message length (`27` for common models, `29` for Lady Marie). Low-level decoding is in `MessageCommon` and `MessageLadyMarie`, both implementing `SerialMessageTranslator`.
- **Realtime UI channeling:** `WebSocketConfiguration` wires four socket channels (`/rs`, `/json`, `/heartbeat`, `/session`) using `SessionType`. `WSSessionManager` stores sessions globally; `WebSocketPublisherImpl` fans out via the custom thread pool (`ThreadPoolExecutorConfig`).
- **UI surface:** Thymeleaf pages (`templates/`) are rendered by MVC controllers (`MapController`, `CustomDBController`, `PdfReportController`, `DbUtilsController`). Front-end scripts in `static/js/` consume WebSocket streams for terminal output, chart rendering, heartbeat time, and active session count. `MyErrorController` handles custom error pages; `WebExceptionHandler` provides global exception handling.
- **Persistence:** JPA/Hibernate with MariaDB in main profile (`application.properties`), H2 in tests (`src/test/resources/application.properties`). There are two domains: legacy `Student`/`Instructor` and extended training entities under `entity.custom` managed by `CustomDBController` and `service.db.custom`. Extended entities include `Courses`, `CourseType`, `CourseCounter`, and `Image`, plus person types (`Lecturer`, `Trainer`, `Technician`, `Participant`). `DatabaseBackupService` provides DB utility operations exposed via `DbUtilsController`.
- **PDF report generation:** `PdfReportController` exposes `/pdf/**` endpoints. Report rendering is handled by `PdfReportService` with layout (`PdfPageLayout`), header/footer (`PdfHeaderFooterEvent`), and color scheme (`PdfColorScheme`) helpers. Configuration properties use the `pdf.report.*` prefix (`PdfReportProperties`).

## Key codebase conventions
- **WebSocket endpoint contract is shared between Java and JS.** Keep `SessionType` values and JS constants aligned (`/json`, `/rs`, `/heartbeat`, `/session`) when changing endpoints.
- **Serial frame completeness is delimiter-driven.** End-of-message depends on `rs.message_delimiter`; default `13,10` is used both in app and docker config.
- **Tracking page depends on log format, not DB.** `TrackService` reads `logs/<tracking>.log` and parses lines using `LogPatternMatcher` regexes that expect `MessageTranslator` log format (`Translated message: ModelTrackDTO(...)`). Changing log text in translator breaks tracks parsing.
- **Model identity mapping is centralized.** Ship IDs and names are defined in `Models` (WARTA=1, BLUE_LADY=2, DORCHERTER_LADY=3, CHERRY_LADY=4, KOLOBRZEG=5, LADY_MARIE=6); serial prefixes (`w1`, `b2`, etc.) are mapped in `MessageTranslator.MODEL_MAP`; JS color mapping mirrors IDs in `static/js/chart-script.js`.
- **Security credentials are property-driven in-memory users.** `SecurityConfig` builds users from `custom.server.credentials.*` properties and protects `/name-service`, `/instructor-service`, `/api/**`, `/admin/**`, `/db-utils/**`, `/pdf/**`, and all custom domain CRUD endpoints (`/trainer-service/**`, `/lecturer-service/**`, `/technician-service/**`, `/participant-service/**`, `/courses-service/**`, `/course-type-service/**`, `/course-counter-service/**`); tests commonly authenticate with `user:user`.
- **Custom domain entities use UUID primary keys plus business IDs.** In `entity.custom`, UUID is DB identity while numeric IDs (e.g., `Courses.id`, `Participant.id`) are managed in services (`nextId()` patterns). Preserve both when adding write paths.
- **Person-like entities share a common base class.** `Lecturer`, `Trainer`, `Technician`, and `Participant` extend `PersonBase` (`@MappedSuperclass`) which holds `uuid` (`@Id`), `name`, and `surname`. Each subclass overrides column names via `@AttributeOverride` to preserve prefixed DB column naming (e.g., `lecturer_name`). Email and nickname remain in subclasses.
- **DTO UUID field naming convention.** The UUID primary-key field in person-like DTOs is named `id` for `LecturerDTO`, `TrainerDTO`, and `TechnicianDTO`. For `ParticipantDTO` the field is named `participantUuid` (to avoid collision with the existing `Long id` business identifier). Always use `getParticipantUuid()` / `setParticipantUuid()` when mapping participant identity in services, mappers, controllers, and templates.
- **Tests assume test-specific Spring properties.** Test runtime uses H2 and `server.port=8081` from `src/test/resources/application.properties`; avoid hardcoding prod DB assumptions in tests.
- **Custom domain entities include supporting types.** Beyond person entities and `Courses`, the custom domain includes `CourseType` (course classification), `CourseCounter` (sequence/counter management), and `Image` (binary image storage). Each has corresponding services, DTOs, and Thymeleaf templates.
- **Schema migration is handled programmatically.** `SchemaMigrationRunner` in the configuration package runs schema adjustments at startup alongside Hibernate's `ddl-auto=update`.
- **PDF report configuration is externalized.** Font names, sizes (regular/small/heading/title), and `max-records` limit are driven by `pdf.report.*` properties bound via `PdfReportProperties`.
- **File upload is supported.** Multipart upload limits are configured (`spring.servlet.multipart.max-file-size=10MB`), used by image upload features in the custom domain.

## Custom domain module — detailed reference

> Full domain analysis with entity hierarchy, bug history, and flow diagrams: [`docs/custom-domain-analysis.md`](../docs/custom-domain-analysis.md)

### Entity hierarchy

```
PersonBase (@MappedSuperclass)
  uuid: UUID          @Id @GeneratedValue(UUID)
  name, surname       @NotBlank @Size(max=100)
  notes               @Size(max=1000)
  nickname            @Size(max=100)
  email               @Email @Size(max=100)
  phoneNumber         @Size(max=26)
  address             @Size(max=300)

  ├── Lecturer   @ManyToMany images: Set<Image>
  ├── Trainer    @ManyToMany images: Set<Image>
  ├── Technician @ManyToMany images: Set<Image>
  └── Participant
        id: Long          @NotNull @Positive @Column(unique=true)
        birthDate: LocalDate @Past
        image: Image      @OneToOne

CourseType
  id: Long              @Id @GeneratedValue(IDENTITY)
  code: String          @NotBlank @Size(max=32) @Column(unique=true)
  description: String   @NotBlank @Size(max=255)
  longDescription       @Size(max=5000)

CourseCounter
  uuid: UUID            @Id @GeneratedValue(UUID)
  counter: Long         @NotNull @Positive @Column(unique=true)
  image: Image          @OneToOne

Courses
  uuid: UUID            @Id @GeneratedValue(UUID)
  id: Long              @NotNull @Positive
  participant           @ManyToOne(optional=false)
  courseType            @ManyToOne(optional=false)
  startDate, endDate    @NotNull (with @AssertTrue isDateRangeValid)
  trainers, lecturers, technicians  @ManyToMany
  courseCounter         @ManyToOne (nullable)

Image
  id: UUID              @Id @GeneratedValue(UUID)
  data: byte[]          @Lob @Basic(LAZY) @Size(max=10_000_000)
  contentType: String   @Size(max=100)
```

### CRUD endpoints

| Entity | CREATE | READ | UPDATE | DELETE |
|---|---|---|---|---|
| Participant | `POST /participant-service/add` | `GET /participant-service` | `POST /participant-service/update` | `POST /participant-service/delete/{uuid}` |
| Lecturer | `POST /lecturer-service/add` | `GET /lecturer-service` | `POST /lecturer-service/update` | `POST /lecturer-service/delete/{id}` |
| Trainer | `POST /trainer-service/add` | `GET /trainer-service` | `POST /trainer-service/update` | `POST /trainer-service/delete/{id}` |
| Technician | `POST /technician-service/add` | `GET /technician-service` | `POST /technician-service/update` | `POST /technician-service/delete/{id}` |
| CourseType | `POST /course-type-service/add` | `GET /course-type-service` | `POST /course-type-service/update` | `POST /course-type-service/delete/{id}` |
| CourseCounter | `POST /course-counter-service/add` | `GET /course-counter-service` | `POST /course-counter-service/update` | `POST /course-counter-service/delete/{uuid}` |
| Courses | `POST /courses-service/add` | `GET /courses-service` | `POST /courses-service/update` | `POST /courses-service/delete/{uuid}` |
| Image | upload via parent entity | `GET /custom/image/{uuid}` | replace via parent update | cascade with parent |

Quick-add course from Participant view: `POST /courses-service/add-participant` (submits only `participantUuid`, `courseTypeId`, `startDate`, `endDate`; trainers/lecturers/technicians/counter are omitted).

### Key custom domain conventions
- **Date format is `dd/MM/yyyy` (EU).** All date DTOs use `@DateTimeFormat(pattern = "dd/MM/yyyy")`. HTML forms use `type="text"` with Flatpickr date picker — never `type="date"` (ISO format mismatch). JS helper `isoToEu()` converts data-attribute dates for UPDATE forms.
- **`@InitBinder` converts empty strings to `null`.** `CustomDBController.initBinder()` registers `StringTrimmerEditor(true)`, essential for correct `@Email`/`@Size` validation on optional fields.
- **Image upload uses `MultipartFile`.** Single-image entities (`Participant`, `CourseCounter`) use `uploadSingleImage()`; multi-image entities (`Lecturer`, `Trainer`, `Technician`) use `uploadImages()`. Images are served as `ResponseEntity<byte[]>` via `GET /custom/image/{uuid}`.
- **`nextId()` uses `COALESCE(MAX(id), 0) + 1`.** `CoursesRepository.findMaxCoursesId()` and `ParticipantRepository.findMaxParticipantId()` are safe for empty tables (return 0 → nextId = 1).
- **Participant deletion is guarded.** `ParticipantService.deleteByUuid()` checks `coursesRepository.existsByParticipant_Uuid()` and throws `IllegalStateException` if courses exist. Error message is displayed via flash attribute.
- **`CourseType.code` has unique constraint.** Both DB-level (`unique=true`) and service-level (`existsByCode()` / `existsByCodeAndIdNot()`) validation prevent duplicates.
- **`CoursesMapper.mapToEntity()` is deprecated.** Marked `@Deprecated(since = "1.9")` — use `CoursesService.buildCourses()` with `repository.getReferenceById()` instead.
- **`CourseCounterDTO` is a Java `record`.** Binding works via constructor (requires Spring 6.1+ / Boot 3.2+). Current stack (Boot 4.x) supports this.
- **`CoursesDTO` has dual CourseCounter fields.** `courseCounterUuid` (read-only, set by mapper) and `counter` (Long, sent from forms). `CoursesService.resolveCourseCounter()` prefers UUID if set, otherwise looks up by counter value.
- **All `@AttributeOverride` for UUID fields include `nullable=false, updatable=false, unique=true`.** This applies to `Lecturer`, `Trainer`, `Technician`, and `Participant`. All `@JoinTable` mappings include explicit `referencedColumnName`.

### Known open items (non-blocking)
- `WARN-03`: `CourseCounterDTO` record binding requires Spring 6.1+ comment
- `WARN-04`: `ImageDTO` exists but is unused in the web layer (potential dead code)
- `WARN-06`: `CoursesDTO.courseCounterUuid` should be documented as read-only
- `INFO-03`: Exception handling in controller is not uniform (`@ExceptionHandler` could unify)
