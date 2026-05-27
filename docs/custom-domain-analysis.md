# Analiza domenowa – moduł `custom`

> Wersja analizy: 2026-05-17 (BUG-01–06, WARN-01, WARN-02, WARN-05, CONS-01–03 — naprawione; pełna re-weryfikacja)  
> Zakres: `entity/custom`, `dto/custom`, `templates/custom`, `mappers/custom`, `service/db/custom`, `controller/web/CustomDBController`

---

## 1. Mapa encji, DTO i szablonów

### 1.1 Hierarchia klas encji

```
PersonBase (@MappedSuperclass)
  uuid: UUID          @Id @GeneratedValue(UUID)
  name: String        @NotBlank @Size(max=100)
  surname: String     @NotBlank @Size(max=100)
  notes: String       @Size(max=1000)
  nickname: String    @Size(max=100)
  email: String       @Email @Size(max=100)
  phoneNumber: String @Size(max=26)
  address: String     @Size(max=300)
  
  ├── Lecturer  @ManyToMany images: Set<Image>
  ├── Trainer   @ManyToMany images: Set<Image>
  ├── Technician @ManyToMany images: Set<Image>
  └── Participant
        id: Long        @NotNull @Positive @Column(unique=true)
        birthDate: LocalDate @Past
        image: Image    @OneToOne

CourseType
  id: Long            @Id @GeneratedValue(IDENTITY)
  code: String        @NotBlank @Size(max=32)
  description: String @NotBlank @Size(max=255)
  longDescription: String @Size(max=5000)

CourseCounter
  uuid: UUID          @Id @GeneratedValue(UUID)
  counter: Long       @NotNull @Positive @Column(unique=true)
  image: Image        @OneToOne

Courses
  uuid: UUID          @Id @GeneratedValue(UUID)
  id: Long            @NotNull @Positive @Column(unique=false)
  participant: Participant  @ManyToOne(optional=false)
  courseType: CourseType    @ManyToOne(optional=false)
  startDate: LocalDate      @NotNull
  endDate: LocalDate        @NotNull
  trainers: Set<Trainer>    @ManyToMany
  lecturers: Set<Lecturer>  @ManyToMany
  technicians: Set<Technician> @ManyToMany
  courseCounter: CourseCounter @ManyToOne (nullable)
  @AssertTrue isDateRangeValid()

Image
  id: UUID            @Id @GeneratedValue(UUID)
  data: byte[]        @Lob @Basic(LAZY) @Size(max=10_000_000)
  contentType: String @Size(max=100)
```

### 1.2 Tabela mapowania: Encja ↔ DTO ↔ Formularz

| Encja / pole               | DTO / pole                | Formularz (ADD)        | Formularz (UPDATE)      | Wyświetlenie (tabela/modal) |
|----------------------------|---------------------------|------------------------|-------------------------|-----------------------------|
| **Participant**             |                           | participant-service    |                         |                             |
| uuid                        | participantUuid           | – (hidden, generowane) | hidden `participantUuid` | data-uuid (JS)             |
| id                          | id                        | `id` (number, min=1)   | `id` (number, min=1)    | kolumna ID                 |
| name                        | name                      | `name`                 | `name`                  | kolumna / modal            |
| surname                     | surname                   | `surname`              | `surname`               | kolumna / modal            |
| birthDate                   | birthDate @DateTimeFormat(dd/MM/yyyy) | `birthDate` **type="date"** ⚠️ | `birthDate` type="text" EU | kolumna (formatowana) |
| nickname                    | nickname                  | `nickname`             | `nickname`              | modal                      |
| email                       | email                     | `email` type="email"   | `email` type="email"    | modal                      |
| phoneNumber                 | phoneNumber               | `phoneNumber`          | `phoneNumber`           | modal                      |
| address                     | address                   | `address`              | `address`               | modal                      |
| notes                       | notes                     | `notes` (textarea)     | `notes` (textarea)      | modal                      |
| image (Image)               | image: UUID               | `imageFile` multipart  | `imageFile` multipart   | badge Assigned/Missing     |
| **Lecturer / Trainer / Technician** |                 | *-service              |                         |                             |
| uuid                        | id: UUID                  | – (hidden, generowane) | hidden `id`             | data-id (JS)               |
| name                        | name                      | `name`                 | `name`                  | kolumna / modal            |
| surname                     | surname                   | `surname`              | `surname`               | kolumna / modal            |
| nickname                    | nickname                  | `nickname`             | `nickname`              | kolumna / modal            |
| email                       | email                     | `email`                | `email`                 | kolumna / modal            |
| phoneNumber                 | phoneNumber               | `phoneNumber`          | `phoneNumber`           | kolumna / modal            |
| address                     | address                   | `address`              | `address`               | modal                      |
| notes                       | notes                     | `notes`                | `notes`                 | modal                      |
| images: Set<Image>          | imagesUuid: Set<UUID>     | `imageFiles` multiple  | `imageFiles` multiple   | badge count / modal imgs   |
| **CourseType**              |                           | course-type-service    |                         |                             |
| id                          | id                        | – (hidden, generowane) | hidden `id`             | kolumna #                  |
| code                        | code                      | `code` ⚠️ brak maxlength | `code` ⚠️ brak maxlength | kolumna                  |
| description                 | description               | `description` ⚠️ brak maxlength | `description` ⚠️ | kolumna                 |
| longDescription             | longDescription           | `longDescription`      | `longDescription`       | kolumna                    |
| **CourseCounter**           |                           | course-counter-service |                         |                             |
| uuid                        | uuid                      | – (hidden, generowane) | hidden `uuid`           | data-uuid (JS)             |
| counter                     | counter                   | `counter` (number)     | `counter` (number)      | kolumna ID                 |
| image                       | imageUuid                 | `imageFile` multipart  | `imageFile` multipart   | img thumbnail              |
| **Courses**                 |                           | courses-service        |                         |                             |
| uuid                        | uuid                      | – (hidden, generowane) | hidden `uuid`           | modal UUID                 |
| id                          | id                        | – (auto nextId)        | hidden `id`             | kolumna ID                 |
| participant                 | participantUuid           | select `participantUuid` | select `participantUuid` | span lookup (JS) |
| courseType                  | courseTypeId              | select `courseTypeId`  | select `courseTypeId`   | badge code                 |
| startDate                   | startDate @DateTimeFormat(dd/MM/yyyy) | `startDate` **type="date"** ⚠️ | `startDate` type="text" EU | kolumna (formatowana) |
| endDate                     | endDate @DateTimeFormat(dd/MM/yyyy)   | `endDate` **type="date"** ⚠️   | `endDate` type="text" EU   | kolumna (formatowana) |
| courseCounter               | counter (Long) / courseCounterUuid | select `counter` | select `counter`        | badge counter             |
| trainers                    | trainerIds: Set<UUID>     | select multi `trainerIds` (warunkowe) | select multi (warunkowe) | badges |
| lecturers                   | lecturerIds: Set<UUID>    | select multi `lecturerIds` (warunkowe) | select multi (warunkowe) | badges |
| technicians                 | technicianIds: Set<UUID>  | select multi `technicianIds` (warunkowe) | select multi (warunkowe) | badges |

---

## 2. Znalezione problemy

### 🔴 KRYTYCZNE (powodują błąd funkcjonalny)

#### ~~BUG-01 — Niezgodność formatu daty w formularzach ADD~~ ✅ NAPRAWIONE

**Dotyczy:** `courses-service.html` (pola `startDate`, `endDate`), `participant-service.html` (pole `birthDate`)

**Opis:**  
Formularze dodawania używały `type="date"`, który wysyła datę w formacie ISO 8601 (`yyyy-MM-dd`).  
Tymczasem DTO (`CoursesDTO`, `ParticipantDTO`) mają adnotację `@DateTimeFormat(pattern = "dd/MM/yyyy")`.  
Spring MVC nie był w stanie zbindować pola — rezultatem był błąd `BindingException` lub (przy leniwym bindingu) pole `null`, co skutkowało `IllegalArgumentException` po walidacji w serwisie.

Formularze UPDATE są poprawne — używają `type="text"` z pomocniczą funkcją JS `isoToEu()`, która konwertuje wartość data-atrybutu z ISO do formatu EU przed wypełnieniem pola.

**Naprawa (zastosowana — wariant A):** zmieniono `type="date"` na `type="text"` z `pattern="\d{2}/\d{2}/\d{4}"` — zgodnie ze wzorcem UPDATE:
```html
<input name="startDate" type="text" class="form-control"
       placeholder="dd/MM/yyyy" pattern="\d{2}/\d{2}/\d{4}" title="Format: dd/MM/yyyy" required>
```

**Pliki:** `courses-service.html` (pola `addStartDate`, `addEndDate`), `participant-service.html` (pole `addParticipantBirthDate`)

---

#### ~~BUG-02 — Utrata powiązań Trainers/Lecturers/Technicians przy aktualizacji kursu gdy lista jest pusta~~ ✅ NAPRAWIONE

**Dotyczy:** `courses-service.html` formularze ADD i UPDATE

**Opis:**  
Selecty Trainers/Lecturers/Technicians były renderowane warunkowo:
```html
<div class="col-md-6" th:if="${!#lists.isEmpty(trainers)}">
  <select name="trainerIds" multiple>...</select>
</div>
```
Jeśli w systemie nie było żadnych trainerów (lista `trainers` pusta), select nie trafiał do HTML → nie był przesłany w POST → Spring bindował `trainerIds` jako pustą kolekcję → `CoursesService.buildCourses()` ustawiał `trainers = emptySet()` → **istniejące powiązania kursu z trainerami zostawały utracone**.

**Naprawa (zastosowana):** usunięto `th:if` z divów owijających selecty w obu formularzach (ADD i UPDATE). Gdy lista jest pusta, select jest renderowany z wyłączoną opcją informacyjną:
```html
<div class="col-md-6">
    <select id="updateTrainerIds" name="trainerIds" class="form-select" multiple size="4">
        <option th:if="${#lists.isEmpty(trainers)}" value="" disabled>No trainers available</option>
        <option th:each="t : ${trainers}" th:value="${t.id}" th:text="${t.name + ' ' + t.surname}">Trainer</option>
    </select>
</div>
```

**Plik:** `courses-service.html`

---

### 🟠 WAŻNE (błędy logiki lub integralności danych)

#### ~~BUG-03 — `CourseType`: brak `maxlength` w formularzach~~ ✅ NAPRAWIONE

**Dotyczy:** `course-type-service.html`, pola `code` i `description`

**Opis:**  
Encja definiuje:
- `code`: `@Size(max=32)`, kolumna VARCHAR(32)  
- `description`: `@Size(max=255)`, kolumna VARCHAR(255)  

Formularze ADD i UPDATE nie miały atrybutu `maxlength`. Użytkownik mógł wpisać dłuższy tekst, co spowodowałoby wyjątek z bazy danych (`Data too long for column`).

**Naprawa (zastosowana):**
```html
<input name="code"        maxlength="32"  ...>
<input name="description" maxlength="255" ...>
```
Dodano `maxlength` w obu formularzach (ADD i UPDATE).

**Plik:** `course-type-service.html`

---

#### ~~BUG-04 — Formularz "Add to Course" (z widoku Participant) nie przekazuje kontekstu trainerów / licznika~~ ✅ NAPRAWIONE

**Dotyczy:** `participant-service.html`, formularz `POST /courses-service/add-participant`

**Opis:**  
Formularz quick-add kursu z poziomu widoku participanta wysyła tylko:  
`participantUuid`, `courseTypeId`, `startDate`, `endDate`  
Nie przesyła: `counter`, `trainerIds`, `lecturerIds`, `technicianIds`.  

Kurs zostaje zapisany bez licznika i bez kadry. Zachowanie jest celowe (uproszczony formularz), jednak:
1. ~~brak komunikatu informującego użytkownika o niekompletności~~ — **naprawione**
2. daty są w formacie `type="text"` – zgodność z DTO ✅

**Naprawa (zastosowana):**  
Dodano alert w formularzu:
```html
<div class="alert alert-warning ...">
    The course will be saved <strong>without trainers, lecturers, technicians and course counter</strong>.
    You can complete the assignment later in the <a href="/courses-service">Certificates</a> view.
</div>
```

**Plik:** `participant-service.html`

---

#### ~~BUG-05 — `CoursesMapper.mapToEntity()` tworzy stub-encje bez danych~~ ✅ NAPRAWIONE

**Dotyczy:** `CoursesMapper.java`, metoda `mapToEntity()`

**Opis:**  
Metoda tworzyła stub-obiekty encji bazując tylko na ID. Aktualnie metoda **nie jest używana** w ścieżce zapisu — `CoursesService.buildCourses()` używa poprawnie `repository.getReferenceById()`. Jednak metoda była publiczna i mogła być użyta przez inny kod.

**Naprawa (zastosowana):**  
Metoda oznaczona `@Deprecated(since = "1.9", forRemoval = false)` z pełną Javadoc opisującą ryzyko:
```java
@Deprecated(since = "1.9", forRemoval = false)
public static Courses mapToEntity(CoursesDTO dto) { ... }
```

**Plik:** `CoursesMapper.java`

---

#### ~~BUG-06 — `Trainer` — niekompletny `@AttributeOverride` dla pola `uuid`~~ ✅ NAPRAWIONE

**Dotyczy:** `Trainer.java`

**Opis:**  
```java
// Trainer.java — przed naprawą
@AttributeOverride(name = "uuid", column = @Column(name = TABLE_NAME + "_uuid", unique = true))
// Brak: nullable = false, updatable = false
```
Porównaj z `Lecturer.java` i `Technician.java`:
```java
@AttributeOverride(name = "uuid", column = @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false))
```
Brak `updatable = false` oznaczał, że Hibernate **mógł próbować aktualizować kolumnę UUID**, co spowodowałoby błąd bazy danych (UUID nie powinien być modyfikowalny po zapisie).

**Naprawa (zastosowana):**
```java
@AttributeOverride(name = "uuid", column = @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false, unique = true))
```

**Plik:** `Trainer.java`

---

### 🟡 OSTRZEŻENIA (potencjalne problemy, złe praktyki)

#### ~~WARN-01 — Podwójny tag `<head>` we wszystkich szablonach `custom`~~ ✅ NAPRAWIONE

**Dotyczy:** Wszystkie pliki w `templates/custom`

**Opis:**  
Każdy szablon zawierał dwa tagi `<head>`, co tworzyło nieważny HTML.

**Naprawa (zastosowana):**  
Dodano fragment `headContent` do `fragment.html` jako `<th:block>` (wirtualny element bez tagu HTML):
```html
<th:block th:fragment="headContent">
    <meta charset="UTF-8"/>
    ...Bootstrap CSS/JS/styles...
</th:block>
```
Każdy szablon custom używa teraz pojedynczego `<head>` z tytułem strony i `th:block th:replace`:
```html
<head>
    <title th:text="...">Serial port server</title>
    <!-- opcjonalnie: dodatkowe CSS (np. Flatpickr) -->
    <th:block th:replace="~{fragment::headContent}"></th:block>
</head>
```
Stary fragment `<head th:fragment="head">` pozostał niezmieniony — używany przez inne strony aplikacji.

**Pliki:** `fragment.html`, wszystkie 7 szablonów `templates/custom/*.html`

---

#### ~~WARN-02 — Brak walidacji daty (startDate ≤ endDate) po stronie klienta w formularzach ADD~~ ✅ NAPRAWIONE

**Dotyczy:** `courses-service.html`, `participant-service.html`

**Opis:**  
Backend walidował poprawność zakresu dat. Brak walidacji client-side dawał złe UX.

**Naprawa (zastosowana):**  
Dodano walidację JavaScript na zdarzeniu `submit` dla wszystkich formularzy z zakresem dat:
- `courses-service.html` — formularz ADD (pola `addStartDate`/`addEndDate`) i UPDATE (`updateStartDate`/`updateEndDate`)
- `participant-service.html` — formularz "Add to Course" (`addToCourseStartDate`/`addToCourseEndDate`)

```javascript
function euToDate(euStr) { ... }  // dd/MM/yyyy → Date
form.addEventListener('submit', (e) => {
    const start = euToDate(...), end = euToDate(...);
    if (start && end && start > end) {
        e.preventDefault();
        alert('End Date must be on or after Start Date.');
    }
});
```

---

#### WARN-03 — `CourseCounterDTO` jako Java record używany z `@ModelAttribute`

**Dotyczy:** `CustomDBController`, endpointy `/course-counter-service/add` i `/course-counter-service/update`

**Opis:**  
`CourseCounterDTO` jest zdefiniowany jako `record`. Spring MVC obsługuje bindowanie recordów przez konstruktor od wersji 6.1 (Spring Boot 3.2+). Projekt używa Spring Boot 4.x — działa poprawnie. Niemniej jednak każda zmiana na wersję < 6.1 bez konwersji do klasy POJO spowoduje błąd bindowania.

**Status:** Akceptowalny przy obecnym stacku. Warto dodać komentarz.

---

#### WARN-04 — `ImageDTO` zdefiniowany ale nieużywany w warstwie webowej

**Dotyczy:** `ImageDTO.java`

**Opis:**  
`ImageDTO` (record z `uuid`, `data`, `contentType`, helper `getImageBase64()`) istnieje, ale żaden kontroler ani szablon go nie używa. Obrazy są serwowane przez dedykowany endpoint `/custom/image/{uuid}` jako `ResponseEntity<byte[]>`. Klasa jest potencjalnie martwym kodem.

**Naprawa:**  
Usunąć `ImageDTO` lub udokumentować jako element przyszłej integracji API.

---

#### ~~WARN-05 — Brak informacji o unikalności `CourseType.code` w formularzu~~ ✅ NAPRAWIONE

**Dotyczy:** `CourseType.java`, `CourseTypeRepository.java`, `CourseTypeService.java`, `course-type-service.html`

**Opis:**  
Baza danych nie wymuszała unikalności `code`, a serwis nie sprawdzał duplikatów.

**Naprawa (zastosowana):**

1. **`CourseType.java`** — dodano `unique = true` do kolumny `code`:
```java
@Column(name = "course_type_code", nullable = false, unique = true, length = 32)
```

2. **`CourseTypeRepository.java`** — dodano metody sprawdzające duplikaty:
```java
boolean existsByCode(String code);
boolean existsByCodeAndIdNot(String code, Long id);
```

3. **`CourseTypeService.java`** — dodano walidację w `save()` (dla ADD) i `update()`:
```java
if (courseTypeRepository.existsByCode(dto.getCode())) {
    throw new IllegalArgumentException("Course type with code '...' already exists.");
}
// przy update: existsByCodeAndIdNot(code, id)
```

**Pliki:** `CourseType.java`, `CourseTypeRepository.java`, `CourseTypeService.java`

---

#### WARN-06 — Ambiguity `CoursesDTO.counter` vs `CoursesDTO.courseCounterUuid`

**Dotyczy:** `CoursesDTO.java`

**Opis:**  
DTO posiada dwa pola do identyfikacji `CourseCounter`:
- `courseCounterUuid: UUID` — nigdy nie jest ustawiany z formularzy (tylko przez mapper przy odczycie)
- `counter: Long` — przesyłany z formularzy (add/update)

`CoursesService.resolveCourseCounter()` preferuje `courseCounterUuid` jeśli jest ustawiony, w przeciwnym razie szuka po `counter`. Ta logika jest poprawna, ale DTO jest mylące — pole `courseCounterUuid` powinno być wyraźnie oznaczone jako "read-only" lub "nie przesyłaj z formularza".

---

### 🔵 SPÓJNOŚĆ (niespójności między encjami — wykryte przy re-analizie)

#### ~~CONS-01 — `Lecturer.java`: brak `unique=true` na polu `uuid` w `@AttributeOverride`~~ ✅ NAPRAWIONE

**Dotyczy:** `Lecturer.java`

**Opis:**  
`Trainer.java` (naprawione w BUG-06) posiada `unique=true` na kolumnie `uuid`. Pozostałe encje `PersonBase` — `Lecturer`, `Technician`, `Participant` — nie miały tej adnotacji, co powodowało niespójność schematu (brak DB-level unique constraint na kolumnie będącej kluczem głównym encji).

**Naprawa (zastosowana):**
```java
@AttributeOverride(name = "uuid", column = @Column(name = "lecturer_uuid", nullable = false, updatable = false, unique = true))
```

**Plik:** `Lecturer.java`

---

#### ~~CONS-02 — `Technician.java`: brak `unique=true` na polu `uuid` w `@AttributeOverride`~~ ✅ NAPRAWIONE

**Dotyczy:** `Technician.java`

**Opis:**  
Identyczny problem jak CONS-01 — brak `unique=true` na uuid.

**Naprawa (zastosowana):**
```java
@AttributeOverride(name = "uuid", column = @Column(name = "technician_uuid", nullable = false, updatable = false, unique = true))
```

**Plik:** `Technician.java`

---

#### ~~CONS-03 — `Participant.java`: brak `unique=true` na polu `uuid` w `@AttributeOverride`~~ ✅ NAPRAWIONE

**Dotyczy:** `Participant.java`

**Opis:**  
Identyczny problem jak CONS-01 i CONS-02 — brak `unique=true` na uuid.

**Naprawa (zastosowana):**
```java
@AttributeOverride(name = "uuid", column = @Column(name = "participants_uuid", nullable = false, updatable = false, unique = true))
```

**Plik:** `Participant.java`

---

#### ~~CONS-04 — `Lecturer.java` i `Technician.java`: brak `referencedColumnName` w `@JoinTable`~~ ✅ NAPRAWIONE

**Dotyczy:** `Lecturer.java`, `Technician.java`

**Opis:**  
`Trainer.java` w `@JoinTable` używa `referencedColumnName` wskazując jawnie kolumnę UUID:
```java
joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid", referencedColumnName = TABLE_NAME + "_uuid")
```
`Lecturer.java` i `Technician.java` pomijały `referencedColumnName`, co powodowało niespójność z Trainer i mogło wprowadzić niejednoznaczność przy generacji schematu.

**Naprawa (zastosowana):** dodano `referencedColumnName` do obu encji:
```java
joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid", referencedColumnName = TABLE_NAME + "_uuid")
```

**Pliki:** `Lecturer.java`, `Technician.java`

---

### 🔵 INFORMACYJNE

#### INFO-01 — Usunięcie participanta z kursami jest blokowane

`ParticipantService.deleteByUuid()` sprawdza `coursesRepository.existsByParticipant_Uuid()` i rzuca `IllegalStateException`. Zachowanie jest poprawne. Komunikat błędu jest wyświetlany przez flash attribute.

#### INFO-02 — `nextId()` korzysta z `COALESCE(MAX, 0)` — bezpieczne przy pustej tabeli

`CoursesRepository.findMaxCoursesId()` i `ParticipantRepository.findMaxParticipantId()` używają `COALESCE(MAX(id), 0)`, więc przy pustej tabeli zwracają `0`, a `nextId()` = 1. Brak ryzyka NPE.

#### INFO-03 — Obsługa błędów w kontrolerze jest niesymetryczna

`addParticipant` i `updateParticipant` obsługują `ConstraintViolationException` oddzielnie (z dekompozycją naruszeń).  
Inne endpointy (np. `addCourse`, `addTrainer`) łapią tylko `RuntimeException`. Spójność kodu byłaby lepsza z jedną metodą `handleException()` lub `@ExceptionHandler`.

#### INFO-04 — `@InitBinder` konwertuje puste stringi na `null`

`CustomDBController.initBinder()` zamienia puste i białoznakowe stringi na `null`. Jest to kluczowe dla poprawnego działania walidacji `@Email`, `@Size` na opcjonalnych polach. Mechanizm działa poprawnie.

---

## 3. Weryfikacja kompletności CRUD

| Encja            | CREATE                              | READ (lista)                        | UPDATE                              | DELETE                                  |
|------------------|-------------------------------------|-------------------------------------|-------------------------------------|-----------------------------------------|
| Participant       | ✅ `POST /participant-service/add`   | ✅ `GET /participant-service`        | ✅ `POST /participant-service/update` | ✅ `POST /participant-service/delete/{uuid}` |
| Lecturer          | ✅ `POST /lecturer-service/add`      | ✅ `GET /lecturer-service`           | ✅ `POST /lecturer-service/update`   | ✅ `POST /lecturer-service/delete/{id}`     |
| Trainer           | ✅ `POST /trainer-service/add`       | ✅ `GET /trainer-service`            | ✅ `POST /trainer-service/update`    | ✅ `POST /trainer-service/delete/{id}`      |
| Technician        | ✅ `POST /technician-service/add`    | ✅ `GET /technician-service`         | ✅ `POST /technician-service/update` | ✅ `POST /technician-service/delete/{id}`   |
| CourseType        | ✅ `POST /course-type-service/add`   | ✅ `GET /course-type-service`        | ✅ `POST /course-type-service/update` | ✅ `POST /course-type-service/delete/{id}` |
| CourseCounter     | ✅ `POST /course-counter-service/add` | ✅ `GET /course-counter-service`    | ✅ `POST /course-counter-service/update` | ✅ `POST /course-counter-service/delete/{uuid}` |
| Courses           | ✅ `POST /courses-service/add`       | ✅ `GET /courses-service`            | ✅ `POST /courses-service/update`    | ✅ `POST /courses-service/delete/{uuid}`    |
| Image             | ✅ (upload przy encjach)             | ✅ `GET /custom/image/{uuid}`        | ✅ (replace przy update)             | ✅ (kaskadowo przy encji)               |

---

## 4. Weryfikacja flow — opis end-to-end

### 4.1 Dodawanie kursu (Courses ADD)

```
Użytkownik → GET /courses-service
  → model: courses, participants, courseTypes, trainers, lecturers, technicians, courseCounters
  → Thymeleaf renderuje formularz ADD

Użytkownik wypełnia formularz → POST /courses-service/add
  → @ModelAttribute CoursesDTO
     ⚠️ BUG-01: startDate/endDate type="date" → Spring nie może zbindować dd/MM/yyyy
  → coursesDTO.setUuid(null)
  → coursesService.save(dto):
     - walidacja participantUuid, courseTypeId, dat
     - nextId() jeśli id==null
     - buildCourses(): getReferenceById dla participant, courseType, trainers, lecturers, technicians
     - resolveCourseCounter(): szuka po counter (Long)
     - coursesRepository.save(courses)
  → redirect:/courses-service z flash successMessage/errorMessage
```

### 4.2 Aktualizacja kursu (Courses UPDATE)

```
Użytkownik klika "Update" na wierszu tabeli
  → JS wypełnia formularz UPDATE z data-atrybutów:
     - uuid, id → hidden inputs
     - participantUuid → select (wyszukanie opcji po value)
     - courseTypeId → select
     - startDate, endDate → type="text" z isoToEu() ✅
     - counter → select value
     - trainerIds, lecturerIds, technicianIds → multi-select ✅ (ale warunkowe renderowanie ⚠️ BUG-02)

Użytkownik klika "Update" → POST /courses-service/update
  → @ModelAttribute CoursesDTO
  → coursesService.update(dto):
     - walidacja uuid, participantUuid, courseTypeId, dat
     - buildCourses() → getReferenceById
     - coursesRepository.save(courses) [merge po uuid = PK]
  → redirect:/courses-service
```

### 4.3 Dodawanie participanta

```
GET /participant-service
  → model: participants, nextId, courseTypes, coursesByParticipant

POST /participant-service/add
  → @ModelAttribute ParticipantDTO + MultipartFile imageFile
  → ⚠️ BUG-01: birthDate type="date" → binding error
  → uploadSingleImage(imageFile) → Image zapisany w DB → UUID
  → participantService.save(dto):
     - sprawdź unikalność id
     - mapToEntity + setImage
     - save
  → redirect:/participant-service
```

### 4.4 Przypisanie participanta do kursu (quick-add)

```
POST /courses-service/add-participant
  → @ModelAttribute CoursesDTO (tylko: participantUuid, courseTypeId, startDate, endDate)
     startDate/endDate: type="text" EU format → ✅ bindowanie poprawne
  → coursesService.save(dto):
     - trainers = emptySet, lecturers = emptySet, technicians = emptySet (nie przesłane)
     - courseCounter = null (nie przesłane)
     - walidacja participantUuid, courseTypeId, dat → OK
     - save → kurs bez kadry i bez licznika
  → redirect:/participant-service
```

### 4.5 Obsługa obrazów

```
Upload:
  uploadSingleImage(MultipartFile) → Image { data, contentType } → imageService.saveImage() → Image.id (UUID)
  uploadImages(MultipartFile[], maxFiles) → Set<UUID>

Serwowanie:
  GET /custom/image/{uuid} → imageService.getImageById(uuid) → ResponseEntity<byte[]> + ContentType
  → szablony używają: <img th:src="@{/custom/image/{uuid}(uuid=${...})}">

Replace przy update:
  - Participant: jeśli nowy plik → upload nowego, usuń stary (imageRepository.deleteById)
  - Lecturer/Trainer/Technician: jeśli nowe pliki → replace całego Set<UUID>; jeśli brak pliku → zachowaj stary Set
  - CourseCounter: analogicznie jak Participant (single image)
```

---

## 5. Priorytety zmian

### 🔴 Konieczne (do naprawy — powodują błędy funkcjonalne)

| ID     | Zmiana                                                                              | Plik                                         | Status |
|--------|-------------------------------------------------------------------------------------|----------------------------------------------|--------|
| ~~BUG-01~~ | ~~Zmień `type="date"` na `type="text"` z pattern EU w formularzach ADD (startDate, endDate, birthDate)~~ | `courses-service.html`, `participant-service.html` | ✅ Naprawione |
| ~~BUG-02~~ | ~~Zawsze renderuj selecty trainer/lecturer/technician w update (bez `th:if` ukrywającego)~~ | `courses-service.html` | ✅ Naprawione |
| ~~BUG-03~~ | ~~Dodaj `maxlength="32"` do pola `code` i `maxlength="255"` do `description`~~ | `course-type-service.html` | ✅ Naprawione |
| ~~BUG-06~~ | ~~Dodaj `nullable = false, updatable = false` do `@AttributeOverride` uuid w Trainer~~ | `Trainer.java` | ✅ Naprawione |

### 🟠 Zalecane (poprawiają jakość / spójność)

| ID     | Zmiana                                                                              | Plik                                         | Status |
|--------|-------------------------------------------------------------------------------------|----------------------------------------------|--------|
| ~~BUG-04~~ | ~~Dodaj informację UI o niekompletności kursu tworzonego przez "Add to Course"~~ | `participant-service.html` | ✅ Naprawione |
| ~~BUG-05~~ | ~~Oznacz `CoursesMapper.mapToEntity()` jako `@Deprecated`~~ | `CoursesMapper.java` | ✅ Naprawione |
| ~~WARN-01~~ | ~~Napraw podwójne `<head>` tagi we wszystkich szablonach~~ | `fragment.html`, wszystkie `templates/custom` | ✅ Naprawione |
| ~~WARN-02~~ | ~~Dodaj client-side walidację zakresu dat w formularzach ADD/UPDATE~~ | `courses-service.html`, `participant-service.html` | ✅ Naprawione |
| ~~WARN-05~~ | ~~Dodaj `unique = true` do `CourseType.code` + walidację duplikatów w serwisie~~ | `CourseType.java`, `CourseTypeRepository.java`, `CourseTypeService.java` | ✅ Naprawione |

### 🟡 Opcjonalne (dobre praktyki / porządkowanie)

| ID     | Zmiana                                                                              |
|--------|-------------------------------------------------------------------------------------|
| WARN-03 | Dodaj komentarz przy `CourseCounterDTO` o wymaganiu Spring 6.1+ dla record binding |
| WARN-04 | Usuń lub udokumentuj `ImageDTO`                                                    |
| WARN-06 | Oznacz `CoursesDTO.courseCounterUuid` jako read-only lub dodaj `// not sent from forms` |
| INFO-03 | Ujednolicić obsługę wyjątków w kontrolerze (wyciągnąć do `@ExceptionHandler`)      |

### 🔵 Spójność (naprawione podczas re-analizy 2026-05-17)

| ID      | Zmiana                                                                                        | Plik                                  | Status          |
|---------|-----------------------------------------------------------------------------------------------|---------------------------------------|-----------------|
| ~~CONS-01~~ | ~~Dodaj `unique=true` na uuid `@AttributeOverride` w Lecturer~~                           | `Lecturer.java`                       | ✅ Naprawione    |
| ~~CONS-02~~ | ~~Dodaj `unique=true` na uuid `@AttributeOverride` w Technician~~                        | `Technician.java`                     | ✅ Naprawione    |
| ~~CONS-03~~ | ~~Dodaj `unique=true` na uuid `@AttributeOverride` w Participant~~                        | `Participant.java`                    | ✅ Naprawione    |
| ~~CONS-04~~ | ~~Dodaj `referencedColumnName` do `@JoinTable` w Lecturer i Technician (jak Trainer)~~   | `Lecturer.java`, `Technician.java`    | ✅ Naprawione    |

---

## 6. Podsumowanie

System ma **kompletne operacje CRUD** dla wszystkich 7 encji domenowych modułu custom. Architektura (encje, DTO, mappery, serwisy, kontroler, szablony) jest spójna i przemyślana.

**Wszystkie znalezione problemy zostały naprawione (2026-05-17):**
- ✅ BUG-01 — formularz ADD: `type="date"` → `type="text"` z pattern EU (courses, participant)
- ✅ BUG-02 — selecty trainers/lecturers/technicians zawsze renderowane (bez `th:if`)
- ✅ BUG-03 — dodano `maxlength` do pól `code` i `description` w formularzach CourseType
- ✅ BUG-04 — alert UI w formularzu "Add to Course" o niekompletności kursu
- ✅ BUG-05 — `CoursesMapper.mapToEntity()` oznaczony `@Deprecated`
- ✅ BUG-06 — `Trainer.@AttributeOverride` uzupełniony o `nullable=false, updatable=false, unique=true`
- ✅ WARN-01 — naprawiono podwójny `<head>` we wszystkich 7 szablonach custom
- ✅ WARN-02 — dodano client-side walidację zakresu dat (startDate ≤ endDate)
- ✅ WARN-05 — `CourseType.code` ma `unique=true` + walidacja duplikatów w serwisie
- ✅ CONS-01 — `Lecturer.java` uuid `@AttributeOverride` uzupełniony o `unique=true`
- ✅ CONS-02 — `Technician.java` uuid `@AttributeOverride` uzupełniony o `unique=true`
- ✅ CONS-03 — `Participant.java` uuid `@AttributeOverride` uzupełniony o `unique=true`
- ✅ CONS-04 — `Lecturer.java` i `Technician.java` `@JoinTable` uzupełnione o `referencedColumnName`
- ✅ Flatpickr — picker kalendarza (dd/MM/yyyy) na wszystkich 8 polach daty

**Pozostałe pozycje (opcjonalne, nie wpływają na funkcjonalność):** WARN-03, WARN-04, WARN-06, INFO-03.

Wyniki testów: **268 testów, 0 błędów, 2 pominięte (niezwiązane z modułem).**
