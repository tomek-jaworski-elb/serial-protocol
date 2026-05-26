# Plan implementacji: Raporty PDF dla modułu `custom`

> Wersja: 2026-05-17  
> Zakres: wszystkie 7 szablonów w `templates/custom/`, backend Spring Boot 4.x / Java 21

---

## 1. Cel

Umożliwić użytkownikowi zaznaczanie wierszy tabeli za pomocą checkboxów i generowanie raportu PDF zawierającego pełne dane wybranych rekordów (bez zdjęć). PDF otwierany w nowej karcie przeglądarki, gotowy do druku.

---

## 2. Analiza bibliotek PDF (backend)

### Kandydaci

| Biblioteka | Licencja | Opis | Zalety | Wady |
|---|---|---|---|---|
| **OpenPDF** (`com.github.librepdf:openpdf`) | LGPL 2.1 / MPL 2.0 | Fork iText 5.5.x – programowe tworzenie PDF | Prosta API, lekka (1 JAR ~1MB), brak zewnętrznych zależności, aktywnie utrzymywana, wolna dla projektów komercyjnych | Wymaga ręcznego budowania layoutu (no HTML rendering) |
| **iText 7 Community** (`com.itextpdf`) | AGPL 3.0 | Kompletny silnik PDF | Bardzo bogata API, pełna obsługa standardów PDF | Licencja AGPL – niezgodna z komercyjnym zastosowaniem bez zakupu licencji |
| **Apache PDFBox** (`org.apache.pdfbox`) | Apache 2.0 | Niskopoziomowe tworzenie/czytanie PDF | Wolna licencja, niezależna od iText | Bardzo verbose API, brak gotowych tabel/layoutów |
| **Openhtmltopdf** (`com.openhtmltopdf`) | Apache 2.0 | Renderowanie XHTML+CSS → PDF (backend: PDFBox) | HTML-driven layout – można reużyć styli | Wymaga valid XHTML; Bootstrap CSS nie jest w pełni obsługiwany przez renderer; skomplikowana konfiguracja fontów; większy zestaw JAR |
| **JasperReports** | LGPL | Pełny silnik raportowania | Zaawansowane szablony .jrxml | Ogromny overhead, Jasper Studio, overkill dla tego projektu |
| **Flying Saucer** (`org.xhtmlrenderer`) | LGPL | Renderowanie XHTML → PDF | Elegancki kod dla webapps | Stary projekt, ograniczone CSS3, wymaga iText backend |

### Rekomendacja: **OpenPDF**

**Uzasadnienie:**
- **Licencja LGPL 2.1** – całkowicie bezpłatna, kompatybilna z projektem komercyjnym i open-source
- **Prosta integracja** – jedna zależność Maven (`~1.5 MB`), zero konfiguracji Spring Boot
- **Stabilna, aktywna** – fork iText 5 utrzymywany przez LibrePDF (ostatnie wydanie 2024)
- **Czytelna API** – `Document`, `PdfWriter`, `PdfPTable`, `Paragraph`, `Phrase` – budowanie layoutu tabelarycznego jest naturalne
- **Nie wymaga renderowania HTML** – dane w PDF generowane programowo z DTOs, brak ryzyka niezgodności CSS
- Openhtmltopdf byłby lepszy gdybyśmy chcieli pixel-perfect HTML rendering – tutaj niepotrzebny bo raporty są prostą tabelą/listą pól

---

## 3. Architektura rozwiązania

### 3.1 Przepływ danych

```
Użytkownik zaznacza checkbox(y) → klik "Print Report"
    → JavaScript pobiera zaznaczone ID
    → hidden <form target="_blank"> wypełniany ID-ami → submit POST
    → PdfReportController obsługuje POST
    → PdfReportService generuje byte[] PDF z DTO
    → Response: application/pdf, Content-Disposition: inline
    → Przeglądarka otwiera PDF w nowej karcie (gotowe do druku Ctrl+P)
```

### 3.2 Endpointy REST

Wszystkie endpointy przyjmują `POST`, `Content-Type: application/x-www-form-urlencoded`, odpowiadają `application/pdf`.

| Szablon | Endpoint | Parametr | Typ ID |
|---|---|---|---|
| trainer-service.html | `POST /pdf/trainer` | `ids` (lista UUID) | UUID |
| lecturer-service.html | `POST /pdf/lecturer` | `ids` (lista UUID) | UUID |
| technician-service.html | `POST /pdf/technician` | `ids` (lista UUID) | UUID |
| participant-service.html | `POST /pdf/participant` | `ids` (lista UUID) | UUID |
| courses-service.html | `POST /pdf/courses` | `ids` (lista UUID) | UUID |
| course-type-service.html | `POST /pdf/course-type` | `ids` (lista Long) | Long |
| course-counter-service.html | `POST /pdf/course-counter` | `ids` (lista UUID) | UUID |

### 3.3 Nowe pliki Java

```
src/main/java/com/jaworski/serialprotocol/
├── configuration/
│   └── PdfReportProperties.java        ← @ConfigurationProperties(prefix="pdf.report")
├── controller/web/
│   └── PdfReportController.java        ← obsługuje POST /pdf/* endpointy
└── service/pdf/
    └── PdfReportService.java           ← generuje PDF byte[] z DTO
```

### 3.4 Zawartość PDF (bez zdjęć)

Każdy dokument PDF zawiera:
- **Nagłówek**: tytuł raportu, data generowania
- **Rekord** (jedna lub wiele sekcji, po jednej na zaznaczony wiersz):

| Encja | Pola w PDF |
|---|---|
| **Trainer** | UUID, Name, Surname, Nickname, Email, Phone, Address, Notes |
| **Lecturer** | UUID, Name, Surname, Nickname, Email, Phone, Address, Notes |
| **Technician** | UUID, Name, Surname, Nickname, Email, Phone, Address, Notes |
| **Participant** | ID (Long), UUID, Name, Surname, Birth Date, Nickname, Email, Phone, Address, Notes |
| **Courses** | Course ID, UUID, Participant (name+surname), Course Type (code + description + long description), Start Date, End Date, Counter, Trainers (lista), Lecturers (lista), Technicians (lista) |
| **CourseType** | ID, Code, Description, Long Description |
| **CourseCounter** | UUID, Counter ID |

---

## 4. Zmiany w szablonach HTML (frontend)

### 4.1 Checkbox w tabeli

Dla każdej tabeli:
1. W `<thead>` dodać nową pierwszą kolumnę z "Select All" checkboxem:
   ```html
   <th scope="col">
     <input type="checkbox" id="selectAllCheckbox" class="form-check-input">
   </th>
   ```
2. W każdym `<tr>` dodać pierwszą kolumnę z checkboxem wiersza:
   ```html
   <td>
     <input type="checkbox" class="form-check-input row-select-checkbox"
            th:value="${trainer.id}">
   </td>
   ```
   Wartość (`value`) = ID rekordu (UUID lub Long zależnie od encji)

### 4.2 Przycisk "Print Report"

Umiejscowienie: w istniejącym pasku nagłówkowym `d-flex justify-content-between`, po prawej stronie obok przycisku "Add":

```html
<div class="d-flex justify-content-between align-items-center my-3">
    <h4 class="mb-0">Trainer</h4>
    <div class="d-flex align-items-center gap-2">
        <span class="badge bg-secondary" ...>0</span>
        <!-- NOWY przycisk PDF -->
        <button id="printReportBtn" type="button" class="btn btn-outline-secondary btn-sm" disabled>
            <i class="bi bi-printer"></i> Print Report
        </button>
        <button id="showAddFormBtn" type="button" class="btn btn-primary btn-sm">Add</button>
    </div>
</div>
```

Przycisk jest `disabled` gdy żaden checkbox nie jest zaznaczony; aktywuje się automatycznie.

### 4.3 Ukryty formularz PDF

Na dole strony (przed `<footer>`):
```html
<form id="pdfReportForm" method="post" th:action="@{/pdf/trainer}" target="_blank">
    <input type="hidden" id="pdfIds" name="ids" value="">
</form>
```

### 4.4 JavaScript

```javascript
const checkboxes = document.querySelectorAll('.row-select-checkbox');
const selectAllCheckbox = document.getElementById('selectAllCheckbox');
const printReportBtn = document.getElementById('printReportBtn');
const pdfReportForm = document.getElementById('pdfReportForm');
const pdfIds = document.getElementById('pdfIds');

// Select All
selectAllCheckbox.addEventListener('change', () => {
    checkboxes.forEach(cb => cb.checked = selectAllCheckbox.checked);
    updatePrintButton();
});

checkboxes.forEach(cb => cb.addEventListener('change', updatePrintButton));

function updatePrintButton() {
    const selected = [...checkboxes].filter(cb => cb.checked);
    printReportBtn.disabled = selected.length === 0;
}

printReportBtn.addEventListener('click', () => {
    const selected = [...checkboxes].filter(cb => cb.checked).map(cb => cb.value);
    if (selected.length === 0) return;
    pdfIds.value = selected.join(',');
    pdfReportForm.submit();
});
```

---

## 5. Bezpieczeństwo

Nowe endpointy `/pdf/**` muszą być chronione przez Spring Security (tak jak istniejące `/api/**`).  
Dodać w `SecurityConfig`:
```java
.requestMatchers("/pdf/**").authenticated()
```

---

## 6. Zależność Maven

Dodać do `pom.xml`:
```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>3.0.4</version>
</dependency>
```
(wersja 3.0.4 – najnowsza stabilna; Java 17+ baseline; poprawki fontów/glyph layout i aktualne zależności; API wstecznie kompatybilne; LGPL)

---

## 7. Plan prac (Fazy)

### Faza 1: Zależność i konfiguracja
- [ ] Dodać `openpdf` do `pom.xml`
- [ ] Dodać `/pdf/**` do `SecurityConfig`

### Faza 2: Backend – serwis PDF
- [ ] Stworzyć `PdfReportService.java`:
  - metoda `generateTrainersPdf(List<TrainerDTO>)`
  - metoda `generateLecturersPdf(List<LecturerDTO>)`
  - metoda `generateTechniciansPdf(List<TechnicianDTO>)`
  - metoda `generateParticipantsPdf(List<ParticipantDTO>)`
  - metoda `generateCoursesPdf(List<CoursesDTO>, ...)`
  - metoda `generateCourseTypesPdf(List<CourseTypeDTO>)`
  - metoda `generateCourseCountersPdf(List<CourseCounterDTO>)`
  - prywatna metoda pomocnicza `buildDocument()` dla nagłówka/stopki

### Faza 3: Backend – kontroler PDF
- [ ] Stworzyć `PdfReportController.java` z endpointami:
  - `POST /pdf/trainer`
  - `POST /pdf/lecturer`
  - `POST /pdf/technician`
  - `POST /pdf/participant`
  - `POST /pdf/courses`
  - `POST /pdf/course-type`
  - `POST /pdf/course-counter`
- [ ] Każdy endpoint parsuje `ids` (comma-separated), pobiera DTO przez serwisy, wywołuje `PdfReportService`, zwraca `ResponseEntity<byte[]>` z `Content-Type: application/pdf`

### Faza 4: Frontend – szablony HTML (7 szablonów)
- [ ] `trainer-service.html`
- [ ] `lecturer-service.html`
- [ ] `technician-service.html`
- [ ] `participant-service.html`
- [ ] `courses-service.html`
- [ ] `course-type-service.html`
- [ ] `course-counter-service.html`

Dla każdego szablonu:
1. Dodać kolumnę checkbox w `<thead>` i `<tbody>`
2. Dodać przycisk "Print Report" w pasku nagłówkowym
3. Dodać ukryty `<form id="pdfReportForm">` przed `<footer>`
4. Dodać JavaScript obsługujący checkboxy i submit formularza

### Faza 5: Testy jednostkowe i integracyjne
- [ ] Stworzyć `PdfReportPropertiesTest.java` — weryfikacja bindowania właściwości
- [ ] Stworzyć `PdfReportServiceTest.java` — testy generowania PDF (czysty unit test)
- [ ] Stworzyć `PdfReportControllerTest.java` — testy MockMvc (integracyjne)
- [ ] Uruchomić pełen zestaw testów (`.\mvnw.cmd clean test`)
- [ ] Ręcznie przetestować generowanie PDF w przeglądarce

### Faza 6: Dokumentacja
- [ ] Zaktualizować `docs/custom-domain-analysis.md` o informację o funkcji PDF

---

## 8. Uwagi implementacyjne — (patrz niżej sekcja 8)

## 9. Plan testów jednostkowych — (patrz niżej sekcja 9)

---

## 10. Analiza ryzyka — błędy krytyczne i ważne

> Data analizy: 2026-05-17  
> Znalezione przez: przegląd planu + weryfikację kodu projektu

---

### 🔴 BŁĘDY KRYTYCZNE

---

#### KRYT-01: CSRF wyłączony — plan zawiera błędną informację

**Sekcja:** 8.3 Pozostałe uwagi  
**Problem:** Plan stwierdza: *"CSRF – ukryty formularz POST musi zawierać `th:action` (Thymeleaf automatycznie dodaje CSRF token)"*  
Tymczasem `SecurityConfig.java` zawiera:
```java
.csrf(AbstractHttpConfigurer::disable)
```
CSRF jest **całkowicie wyłączony** w projekcie. Thymeleaf nie dodaje żadnych tokenów. Informacja jest myląca i może powodować niepotrzebne debugowanie.

**Korekta:** Usunąć wzmiankę o CSRF z uwag implementacyjnych. Formularz POST zadziała bez żadnych dodatkowych tokenów.

---

#### KRYT-02: Niezgodność poziomów bezpieczeństwa — `/pdf/**` vs strony custom

**Sekcja:** 5. Bezpieczeństwo  
**Problem:** Plan proponuje `.requestMatchers("/pdf/**").authenticated()`. Jednak analiza `SecurityConfig.java` pokazuje, że istniejące strony custom (`/trainer-service`, `/participant-service`, `/courses-service` itd.) **nie są wymienione** w chronionych routach i domyślnie kwalifikują się jako `permitAll()`.

Efekt: użytkownik może otworzyć stronę trainers bez logowania, zaznaczyć checkboxy, kliknąć "Print Report" → dostać **HTTP 401**, bo `/pdf/trainer` wymaga auth.

**Korekta:** Decyzja do podjęcia (udokumentować):
- **Opcja A:** Dodać strony custom do SecurityConfig (`/trainer-service`, `/participant-service` itd.) jako `authenticated()` — spójna ochrona całego modułu
- **Opcja B:** Pozostawić `/pdf/**` bez ochrony — spójne z poziomem stron (ale PDF z danymi jest publicznie dostępny)

Zalecana: **Opcja A** — chronić cały moduł custom łącznie z PDF endpointami.

---

#### KRYT-03: `@Configuration` + `@ConfigurationProperties` — błędna adnotacja

**Sekcja:** 8.3 `PdfReportProperties.java`  
**Problem:** Przykład kodu używa `@Configuration @ConfigurationProperties(prefix = "pdf.report")`. `@Configuration` jest adnotacją dla klas tworzących beany przez metody `@Bean` — nie dla klas wiążących właściwości. Kombinacja może powodować problemy z proxy CGLIB w Spring.

Dodatkowo: w projekcie **nie ma** `@EnableConfigurationProperties` ani `@ConfigurationPropertiesScan` — bez tego `@ConfigurationProperties` bez `@Component` nie jest rejestrowane przez Spring Boot.

**Korekta:**
```java
// ✅ Prawidłowo:
@Component                                         // ← rejestruje bean
@ConfigurationProperties(prefix = "pdf.report")   // ← wiąże właściwości
@Validated
public class PdfReportProperties { ... }
```

---

#### KRYT-04: `@Valid` brakuje — kaskadowa walidacja nie działa

**Sekcja:** 8.3 `PdfReportProperties.java`  
**Problem:** Bean Validation nie przechodzi automatycznie do zagnieżdżonych obiektów. `@Min`/`@Max` na polach `SizeConfig` NIE ZADZIAŁAJĄ bez `@Valid` na polach-obiektach:

```java
// ❌ Bez @Valid — walidacja SizeConfig jest ignorowana:
private FontConfig font = new FontConfig();

public static class FontConfig {
    private SizeConfig size = new SizeConfig();  // ← @Min/@Max na polach SizeConfig pominięte!
}
```

```java
// ✅ Z @Valid — walidacja kaskadowa:
@Valid
private FontConfig font = new FontConfig();

public static class FontConfig {
    @Valid
    private SizeConfig size = new SizeConfig();

    public static class SizeConfig {
        @Min(6) @Max(24) private int regular = 10;
        // ...
    }
}
```

---

#### KRYT-05: NullPointerException w fallback fontu

**Sekcja:** 8.3 `loadBaseFont()`  
**Problem:** Gdy plik fontu nie istnieje w classpath, fallback również może zwrócić `null`:
```java
try (InputStream fallback = getClass().getResourceAsStream("/fonts/DejaVuSans" + variant + ".ttf")) {
    byte[] data = fallback.readAllBytes();  // ← NullPointerException jeśli DejaVuSans.ttf też nie istnieje!
```
Jeśli deweloper zapomni dodać pliki TTF do `src/main/resources/fonts/`, aplikacja padnie z NPE przy pierwszym żądaniu PDF.

**Korekta:**
```java
if (fallback == null) {
    throw new IllegalStateException(
        "Default fallback font DejaVuSans" + variant + ".ttf not found in classpath:/fonts/. " +
        "Ensure DejaVuSans.ttf and DejaVuSans-Bold.ttf are in src/main/resources/fonts/"
    );
}
```

---

#### KRYT-06: `PdfFontSet` — klasa nieistniejąca, typ zwracany błędny

**Sekcja:** 8.3 `buildFonts()`  
**Problem:** Przykład kodu:
```java
private Font buildFonts() throws DocumentException, IOException {
    // ...
    return new PdfFontSet(  // ← PdfFontSet NIE ISTNIEJE w OpenPDF!
        new Font(regular, s.getRegular()),
        new Font(bold, s.getHeading()),
        ...
    );
}
```
`PdfFontSet` to klasa którą trzeba **stworzyć samodzielnie** — nie ma jej w OpenPDF API. Typ zwracany `Font` jest też błędny (metoda ma zwracać zestaw fontów, nie jeden font).

**Korekta:** Stworzyć wewnętrzną klasę pomocniczą lub record:
```java
// W PdfReportService:
private record FontSet(Font regular, Font heading, Font title, Font small) {}

private FontSet buildFonts() throws DocumentException, IOException {
    BaseFont regular = loadBaseFont("");
    BaseFont bold    = loadBaseFont("-Bold");
    PdfReportProperties.FontConfig.SizeConfig s = props.getFont().getSize();
    return new FontSet(
        new Font(regular, s.getRegular()),
        new Font(bold,    s.getHeading()),
        new Font(bold,    s.getTitle()),
        new Font(regular, s.getSmall())
    );
}
```

---

#### KRYT-07: Parsowanie `ids` — Spring MVC nie rozkłada comma-separated automatycznie

**Sekcja:** 4.3 JavaScript + 7 Faza 3  
**Problem:** JavaScript wysyła:
```javascript
pdfIds.value = selected.join(',');  // → "uuid1,uuid2,uuid3" jako JEDEN string
pdfReportForm.submit();
```
Żądanie HTTP: `ids=uuid1%2Cuuid2%2Cuuid3` — jeden parametr z przecinkami.

Spring MVC `@RequestParam List<UUID> ids` **nie parsuje** comma-separated stringa — wymagałoby to wielu osobnych parametrów `ids=uuid1&ids=uuid2`. Wynik: `ids` będzie listą z jednym elementem `"uuid1,uuid2,uuid3"`, co spowoduje `IllegalArgumentException` przy próbie konwersji na UUID.

**Korekta:** Kontroler powinien przyjmować `String ids` i parsować ręcznie:
```java
@PostMapping("/pdf/trainer")
public ResponseEntity<byte[]> trainerPdf(
        @RequestParam(required = false, defaultValue = "") String ids) {
    List<UUID> idList = Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(UUID::fromString)  // ← patrz WAŻN-03 — potrzebny try/catch
        .toList();
    ...
}
```

---

### 🟡 BŁĘDY WAŻNE

---

#### WAŻN-01: Brak cache fontów — plik TTF czytany przy każdym PDF

**Sekcja:** 8.3 `loadBaseFont()`  
**Problem:** Metoda `loadBaseFont()` czyta ~750KB z classpath przy każdym wywołaniu `buildFonts()`, czyli przy każdym żądaniu PDF. Przy intensywnym użyciu to zbędne I/O.

**Korekta:** Cache fontów jako pola serwisu, inicjalizowane `@PostConstruct`:
```java
private FontSet cachedFonts;

@PostConstruct
private void initFonts() {
    cachedFonts = buildFonts();  // ← jednorazowe wczytanie przy starcie
}
```

---

#### WAŻN-02: `generateCoursesPdf` — nieokreślone parametry uzupełniające

**Sekcja:** 7 Faza 2  
**Problem:** `metoda generateCoursesPdf(List<CoursesDTO>, ...)` — trzy kropki bez wyjaśnienia. `CoursesDTO` przechowuje wyłącznie ID relacji (`participantUuid: UUID`, `courseTypeId: Long`, `trainerIds: Set<UUID>`). PDF musi zawierać **nazwy** (Participant name, CourseType code/description, Trainer names). Brak definicji jak te dane będą dostarczone.

**Korekta:** Dwie opcje:
- **Opcja A (zalecana):** `PdfReportService` ma autowired serwisy i samodzielnie rozwiązuje relacje
- **Opcja B:** Serwis PDF przyjmuje gotowe mapy: `Map<UUID, ParticipantDTO>`, `Map<Long, CourseTypeDTO>`, itd.

---

#### WAŻN-03: Brak obsługi `IllegalArgumentException` — UUID.fromString

**Sekcja:** 7 Faza 3  
**Problem:** Gdy `ids` zawiera niepoprawny UUID (np. `"abc-xyz"`), `UUID.fromString()` rzuca `IllegalArgumentException` → HTTP 500 Internal Server Error zamiast odpowiedzi 400 Bad Request.

**Korekta:** `try/catch` w kontrolerze lub wspólny `@ExceptionHandler`.

---

#### WAŻN-04: "Select All" nie jest desynchronizowany przy ręcznym odznaczaniu

**Sekcja:** 4.4 JavaScript  
**Problem:** Po zaznaczeniu "Select All" i ręcznym odznaczeniu jednego checkboxa — checkbox "Select All" pozostaje zaznaczony (wizualna niespójność).

**Korekta:** Dodać synchronizację "Select All" przy każdej zmianie wiersza:
```javascript
function updatePrintButton() {
    const selected = [...checkboxes].filter(cb => cb.checked);
    printReportBtn.disabled = selected.length === 0;
    // Synchronizuj "Select All"
    selectAllCheckbox.indeterminate = selected.length > 0 && selected.length < checkboxes.length;
    selectAllCheckbox.checked = selected.length === checkboxes.length && checkboxes.length > 0;
}
```
Użycie `indeterminate` (stan pośredni) daje lepszy UX (standardowy wzorzec tabel z multi-select).

---

#### WAŻN-05: Brak limitu rekordów — ryzyko bardzo dużego PDF

**Sekcja:** 3.1 Przepływ danych  
**Problem:** Użytkownik może zaznaczyć WSZYSTKIE rekordy (np. 1000 trainerów) → kontroler spróbuje wygenerować ogromny PDF → potencjalny OOM lub timeout.

**Korekta:** Dodać limit po stronie frontendu (wyłączyć "Print Report" jeśli > N zaznaczonych) i walidację po stronie backendowej:
```java
if (idList.size() > 100) {
    return ResponseEntity.badRequest().build();  // lub limit z properties
}
```
Wartość limitu jako osobna właściwość: `pdf.report.max-records=100`.

---

#### WAŻN-06: Błędna kolejność sekcji w dokumencie

**Problem:** Sekcja "9. Plan testów" (linia ~257) pojawia się **przed** sekcją "8. Uwagi implementacyjne" (linia ~514). Numeracja jest niezgodna z kolejnością w dokumencie.

**Korekta:** Przestawić sekcje lub zmienić numerację na zgodną z kolejnością.

---

### ✅ Matryca błędów — podsumowanie

| ID | Opis | Priorytet | Sekcja |
|---|---|---|---|
| KRYT-01 | CSRF wyłączony — błędna informacja w planie | 🔴 Krytyczny | 8.3 |
| KRYT-02 | Niezgodność security `/pdf/**` vs strony custom | 🔴 Krytyczny | 5 |
| KRYT-03 | `@Configuration` zamiast `@Component` w Properties | 🔴 Krytyczny | 8.3 |
| KRYT-04 | Brak `@Valid` — walidacja zagnieżdżona nie działa | 🔴 Krytyczny | 8.3 |
| KRYT-05 | NPE w fallback fontu gdy plik TTF nie istnieje | 🔴 Krytyczny | 8.3 |
| KRYT-06 | `PdfFontSet` nie istnieje w OpenPDF, typ zwracany błędny | 🔴 Krytyczny | 8.3 |
| KRYT-07 | `ids` comma-separated — Spring MVC nie parsuje `List<UUID>` | 🔴 Krytyczny | 4.3 + 7 |
| WAŻN-01 | Brak cache fontów — I/O przy każdym żądaniu | 🟡 Ważny | 8.3 |
| WAŻN-02 | `generateCoursesPdf` — nieokreślone parametry relacji | 🟡 Ważny | 7 |
| WAŻN-03 | Brak obsługi UUID.fromString exception | 🟡 Ważny | 7 |
| WAŻN-04 | "Select All" brak `indeterminate` stanu pośredniego | 🟡 Ważny | 4.4 |
| WAŻN-05 | Brak limitu rekordów — ryzyko OOM | 🟡 Ważny | 3.1 |
| WAŻN-06 | Błędna kolejność sekcji 8/9 w dokumencie | 🟡 Ważny | — |


---

## 9. Plan testów jednostkowych (backend)

### 9.1 Dodatkowa zależność testowa

Aby weryfikować **zawartość** wygenerowanego PDF (tekst, kodowanie Unicode) bez dodawania biblioteki produkcyjnej, dodajemy Apache PDFBox wyłącznie jako zależność testową:

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
    <scope>test</scope>
</dependency>
```

PDFBox (Apache 2.0 License) służy w testach jako **reader** — ekstrahuje tekst z PDF i pozwala sprawdzić konkretne pola. Nie trafia do produkcyjnego JAR.

---

### 9.2 Klasy testowe — przegląd

| Klasa testowa | Typ | Adnotacja |
|---|---|---|
| `PdfReportPropertiesTest` | Unit (Spring slice) | `@SpringBootTest` |
| `PdfReportServiceTest` | Unit (bez Spring) | `@ExtendWith(MockitoExtension.class)` |
| `PdfReportControllerTest` | Integracyjny | `@SpringBootTest` + `@AutoConfigureMockMvc` |

---

### 9.3 `PdfReportPropertiesTest`

**Lokalizacja:** `src/test/java/.../configuration/PdfReportPropertiesTest.java`  
**Cel:** Weryfikacja bindowania `@ConfigurationProperties(prefix = "pdf.report")` i walidacji

```
Przypadki testowe:
─────────────────────────────────────────────────────────────────────
shouldUseDefaultValues
  │ Given: brak wpisów pdf.report.* w application.properties testu
  │ Then:  font.name = "DejaVuSans"
  │        font.size.regular = 10, small = 8, heading = 13, title = 16
  
shouldBindCustomFontName
  │ Given: @TestPropertySource("pdf.report.font.name=DejaVuSans-Condensed")
  │ Then:  font.name = "DejaVuSans-Condensed"

shouldBindCustomFontSizes
  │ Given: pdf.report.font.size.regular=12, heading=15, title=20, small=9
  │ Then:  wszystkie wartości są odpowiednio przepisane

shouldFailValidation_whenRegularSizeTooSmall
  │ Given: pdf.report.font.size.regular=5 (poniżej @Min(6))
  │ Then:  rzuca ConstraintViolationException przy starcie kontekstu
  
shouldFailValidation_whenTitleSizeTooLarge
  │ Given: pdf.report.font.size.title=37 (powyżej @Max(36))
  │ Then:  rzuca ConstraintViolationException
─────────────────────────────────────────────────────────────────────
```

---

### 9.4 `PdfReportServiceTest`

**Lokalizacja:** `src/test/java/.../service/pdf/PdfReportServiceTest.java`  
**Cel:** Weryfikacja generowania PDF — format, zawartość, Unicode, null-safety  
**Styl:** Czysty unit test, `PdfReportProperties` tworzony programowo (bez Mockito)

#### Metoda pomocnicza wspólna dla wszystkich testów:
```java
private String extractPdfText(byte[] pdfBytes) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
        return new PDFTextStripper().getText(doc);
    }
}

private void assertValidPdf(byte[] bytes) {
    assertNotNull(bytes);
    assertTrue(bytes.length > 100);
    // Sygnatura PDF: %PDF-
    assertEquals('%', (char) bytes[0]);
    assertEquals('P', (char) bytes[1]);
    assertEquals('D', (char) bytes[2]);
    assertEquals('F', (char) bytes[3]);
}
```

#### Przypadki testowe:

```
─── Trainer ─────────────────────────────────────────────────────────
shouldGenerateValidPdf_forTrainers
  │ Given: lista 1 TrainerDTO (name="Jan", surname="Kowalski")
  │ Then:  byte[] != null, zaczyna się od "%PDF-", rozmiar > 100B

shouldContainTrainerName_inPdf
  │ Given: TrainerDTO(name="Władysław", surname="Żółkiewski")
  │ Then:  extractPdfText() zawiera "Władysław" i "Żółkiewski"

shouldContainPolishCharacters_inTrainerPdf
  │ Given: TrainerDTO z polami zawierającymi "Zażółć gęślą jaźń"
  │ Then:  PDF text zawiera "Zażółć gęślą jaźń" (bez zastępowania znaków)

shouldContainGermanCharacters_inTrainerPdf
  │ Given: TrainerDTO(notes="Ärger über Größe, Müller")
  │ Then:  PDF text zawiera "Ärger über Größe, Müller"

shouldReplaceNullFields_withEmDash_inTrainerPdf
  │ Given: TrainerDTO(email=null, phoneNumber=null, address=null)
  │ Then:  PDF text zawiera "—" (em-dash) w miejscu pustych pól

shouldGeneratePdf_withMultipleTrainers
  │ Given: lista 3 TrainerDTO z różnymi imionami
  │ Then:  extractPdfText() zawiera nazwisko każdego z 3 trainerów

─── Lecturer / Technician ───────────────────────────────────────────
shouldGenerateValidPdf_forLecturers
shouldContainLecturerName_inPdf
shouldGenerateValidPdf_forTechnicians
shouldContainTechnicianName_inPdf
  (analogiczne do Trainer — wspólna PersonBase)

─── Participant ─────────────────────────────────────────────────────
shouldGenerateValidPdf_forParticipants
  │ Given: ParticipantDTO(id=1L, name="Anna", surname="Nowak",
  │         birthDate=LocalDate.of(1990,5,15))
  │ Then:  byte[] valid PDF

shouldContainParticipantBirthDate_inPdf
  │ Given: ParticipantDTO(birthDate=LocalDate.of(1990, 5, 15))
  │ Then:  PDF text zawiera "15/05/1990" (format dd/MM/yyyy)

shouldContainParticipantId_inPdf
  │ Given: ParticipantDTO(id=42L)
  │ Then:  PDF text zawiera "42"

─── Courses ─────────────────────────────────────────────────────────
shouldGenerateValidPdf_forCourses
  │ Given: CoursesDTO z startDate, endDate, courseTypeId
  │ Then:  valid PDF

shouldContainCourseDates_inPdf
  │ Given: startDate=2026-01-10, endDate=2026-01-20
  │ Then:  PDF text zawiera "10/01/2026" i "20/01/2026"

shouldContainCourseTypeCode_inPdf
  │ Given: CoursesDTO z mapą courseTypeId→CourseTypeDTO(code="BASIC")
  │ Then:  PDF text zawiera "BASIC"

─── CourseType ──────────────────────────────────────────────────────
shouldGenerateValidPdf_forCourseTypes
shouldContainCourseTypeCode_andDescription_inPdf
  │ Given: CourseTypeDTO(code="NW-1", description="Nawigacja",
  │         longDescription="Szkolenie z nawigacji morskiej")
  │ Then:  PDF text zawiera "NW-1", "Nawigacja", "Szkolenie z nawigacji morskiej"

─── CourseCounter ───────────────────────────────────────────────────
shouldGenerateValidPdf_forCourseCounters

─── Font fallback ───────────────────────────────────────────────────
shouldFallbackToDejaVuSans_whenUnknownFontName
  │ Given: PdfReportProperties z font.name="NonExistentFont"
  │ Then:  PDF generowany poprawnie (bez wyjątku), rozmiar > 100B
  │        (fallback na DejaVuSans)

─── Custom font sizes ───────────────────────────────────────────────
shouldRespectCustomFontSizes_inGeneratedPdf
  │ Given: PdfReportProperties z size.regular=14, size.title=24
  │ Then:  PDF generowany poprawnie (bez wyjątku)
  │        (weryfikacja rozmiaru czcionki bezpośrednio w PDF jest
  │         poza zakresem — test sprawdza brak wyjątku + valid PDF)

─── Edge cases ──────────────────────────────────────────────────────
shouldGenerateEmptyReportPdf_whenListIsEmpty
  │ Given: pusta lista trainerów
  │ Then:  valid PDF (z informacją "No records selected" lub nagłówkiem)
─────────────────────────────────────────────────────────────────────
```

**Łącznie:** ~20 przypadków testowych

---

### 9.5 `PdfReportControllerTest`

**Lokalizacja:** `src/test/java/.../controller/web/PdfReportControllerTest.java`  
**Cel:** Weryfikacja endpointów HTTP — Content-Type, status HTTP, autoryzacja  
**Styl:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`

> Wzorzec identyczny z istniejącymi `MapControllerTest` i `DbUtilsControllerTest`

```
─── Kontrola dostępu (bez uwierzytelnienia → 401) ───────────────────
shouldReturn401_whenUnauthenticated_postTrainer
shouldReturn401_whenUnauthenticated_postLecturer
shouldReturn401_whenUnauthenticated_postTechnician
shouldReturn401_whenUnauthenticated_postParticipant
shouldReturn401_whenUnauthenticated_postCourses
shouldReturn401_whenUnauthenticated_postCourseType
shouldReturn401_whenUnauthenticated_postCourseCounter

─── Content-Type (z uwierzytelnieniem) ─────────────────────────────
shouldReturnApplicationPdf_forTrainerEndpoint
  │ Given: POST /pdf/trainer, ids = "" (pusty), auth = user:user
  │ Then:  HTTP 200, Content-Type = application/pdf

shouldReturnApplicationPdf_forLecturerEndpoint
shouldReturnApplicationPdf_forTechnicianEndpoint
shouldReturnApplicationPdf_forParticipantEndpoint
shouldReturnApplicationPdf_forCoursesEndpoint
shouldReturnApplicationPdf_forCourseTypeEndpoint
shouldReturnApplicationPdf_forCourseCounterEndpoint

─── Response body ───────────────────────────────────────────────────
shouldReturnNonEmptyPdfBytes_forTrainerWithValidId
  │ Given: POST /pdf/trainer, ids = "<uuid istniejącego trainera>"
  │        (trainer wcześniej zapisany przez @Autowired TrainerService)
  │ Then:  response body zaczyna się od "%PDF-"

shouldReturnValidPdf_forUnknownIds
  │ Given: POST /pdf/trainer, ids = "00000000-0000-0000-0000-000000000000"
  │ Then:  HTTP 200, valid PDF (pusta lista → pusty raport)

shouldReturnValidPdf_forEmptyIds
  │ Given: POST /pdf/trainer, ids = ""
  │ Then:  HTTP 200, valid PDF

─── Content-Disposition ────────────────────────────────────────────
shouldIncludeContentDisposition_withFilename
  │ Given: POST /pdf/trainer, auth user:user
  │ Then:  header Content-Disposition zawiera "filename="
  │        i "trainers-" (prefiks nazwy pliku)
─────────────────────────────────────────────────────────────────────
```

**Łącznie:** ~19 przypadków testowych

---

### 9.6 Podsumowanie testów

| Klasa | Typ | Przypadki | Adnotacja |
|---|---|---|---|
| `PdfReportPropertiesTest` | Unit (Spring slice) | 5 | `@SpringBootTest` |
| `PdfReportServiceTest` | Unit (pure) | ~20 | `@ExtendWith(MockitoExtension.class)` |
| `PdfReportControllerTest` | Integracyjny | ~19 | `@SpringBootTest` + MockMvc |
| **Razem** | | **~44** | |

### 9.7 Kolejność implementacji testów (TDD-like)

1. `PdfReportPropertiesTest` — najprostszy, definiuje kontrakt konfiguracji
2. `PdfReportServiceTest` — testuje logikę generowania (można pisać równolegle z serwisem)
3. `PdfReportControllerTest` — ostatni, po serwisie i kontrolerze


---

## 8. Uwagi implementacyjne

### 8.1 Pakiety OpenPDF 3.x — WAŻNA ZMIANA

OpenPDF 3.0.0+ zmienił nazwy wszystkich pakietów:

| Stary (1.x / 2.x) | Nowy (3.x) |
|---|---|
| `com.lowagie.text.*` | `org.openpdf.text.*` |
| `com.lowagie.text.pdf.*` | `org.openpdf.text.pdf.*` |

**Wszystkie importy w kodzie projektu muszą używać `org.openpdf.*`.**

### 8.2 Obsługa Unicode (polskie, niemieckie, angielskie znaki)

**Problem:** Wbudowane fonty PDF (Helvetica, Times Roman, Courier) obsługują wyłącznie Latin-1 (ISO-8859-1). Znaki spoza tego zakresu (ą, ę, ś, ó, ź, ż, ć, ń, ł, ü, ö, ä, ß, č, š itd.) są zastępowane przez `?` lub `□`.

**Rozwiązanie: Osadzenie fontu TrueType z kodowaniem `IDENTITY_H` (Unicode)**

#### Wybrany font: DejaVu Sans
- **Pokrycie:** cały Latin Extended (PL, DE, EN, FR, CS, SK, HU, ...), cyrylica, greka, i wiele innych
- **Licencja:** DejaVu License (SIL Open Font License) — wolna do bundlowania w projekcie
- **Pliki potrzebne:**
  - `DejaVuSans.ttf` — tekst regularny (~750 KB)
  - `DejaVuSans-Bold.ttf` — nagłówki pogrubione (~750 KB)
- **Źródło:** https://dejavu-fonts.github.io/ lub Maven Central: `com.ibm.icu:icu4j` nie wymagane — fonty pobierane ręcznie

#### Lokalizacja w projekcie
```
src/main/resources/
└── fonts/
    ├── DejaVuSans.ttf
    └── DejaVuSans-Bold.ttf
```

### 8.3 Konfiguracja czcionek przez application.properties

#### Nowa klasa: `PdfReportProperties.java`

```java
@Configuration
@ConfigurationProperties(prefix = "pdf.report")
@Validated
public class PdfReportProperties {

    private FontConfig font = new FontConfig();

    public FontConfig getFont() { return font; }
    public void setFont(FontConfig font) { this.font = font; }

    public static class FontConfig {

        /**
         * Nazwa rodziny fontu. Musi odpowiadać plikom w classpath:/fonts/:
         *   DejaVuSans          → /fonts/DejaVuSans.ttf + /fonts/DejaVuSans-Bold.ttf
         *   DejaVuSans-Condensed → /fonts/DejaVuSans-Condensed.ttf + ...
         * Domyślnie: DejaVuSans
         */
        private String name = "DejaVuSans";

        private SizeConfig size = new SizeConfig();

        // getters + setters

        public static class SizeConfig {
            /** Rozmiar tekstu podstawowego (wartości pól) */
            @Min(6) @Max(24)
            private int regular = 10;

            /** Rozmiar etykiet i opisów pomocniczych */
            @Min(6) @Max(18)
            private int small = 8;

            /** Rozmiar nagłówków sekcji (nazwa encji) */
            @Min(8) @Max(30)
            private int heading = 13;

            /** Rozmiar tytułu dokumentu */
            @Min(10) @Max(36)
            private int title = 16;

            // getters + setters
        }
    }
}
```

#### Wpisy w `application.properties`

```properties
# ─── PDF Report ──────────────────────────────────────────────────
# Rodzina fontu: nazwa pliku TTF bez rozszerzenia z classpath:/fonts/
# Dostępne wbudowane: DejaVuSans, DejaVuSans-Condensed
pdf.report.font.name=DejaVuSans

# Rozmiary czcionek (punkty)
pdf.report.font.size.regular=10
pdf.report.font.size.small=8
pdf.report.font.size.heading=13
pdf.report.font.size.title=16
```

#### Logika ładowania fontu w `PdfReportService`

```java
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final PdfReportProperties props;

    private BaseFont loadBaseFont(String variant) throws DocumentException, IOException {
        // variant: "" dla regular, "-Bold" dla pogrubionego
        String fileName = props.getFont().getName() + variant + ".ttf";
        try (InputStream is = getClass().getResourceAsStream("/fonts/" + fileName)) {
            if (is == null) {
                LOG.warn("Font file not found: /fonts/{}, falling back to DejaVuSans{}.ttf", fileName, variant);
                try (InputStream fallback = getClass().getResourceAsStream("/fonts/DejaVuSans" + variant + ".ttf")) {
                    byte[] data = fallback.readAllBytes();
                    return BaseFont.createFont("DejaVuSans" + variant + ".ttf",
                            BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, data, null);
                }
            }
            byte[] data = is.readAllBytes();
            return BaseFont.createFont(fileName,
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, data, null);
        }
    }

    private Font buildFonts() throws DocumentException, IOException {
        BaseFont regular = loadBaseFont("");
        BaseFont bold    = loadBaseFont("-Bold");
        PdfReportProperties.FontConfig.SizeConfig s = props.getFont().getSize();

        // Zwraca zestaw fontów gotowych do użycia
        return new PdfFontSet(
            new Font(regular, s.getRegular()),
            new Font(bold,    s.getHeading()),
            new Font(bold,    s.getTitle()),
            new Font(regular, s.getSmall())
        );
    }
}
```

#### Testowanie pokrycia znaków
PDF musi poprawnie renderować m.in.:
- **Polskie:** `Zażółć gęślą jaźń. ĄĆĘŁŃÓŚŹŻ ąćęłńóśźż`
- **Niemieckie:** `Ärger über Größe. Müller, Öffentlichkeit, Straße`
- **Angielskie:** `The quick brown fox jumps over the lazy dog`
- Cyfry, znaki specjalne: `0–9 !@#$%^&*()`

### 8.3 Pozostałe uwagi

- **Wielojęzyczność**: Etykiety pól w PDF w języku angielskim (zgodnie z istniejącym UI).
- **Puste pola**: wyświetlać `—` (myślnik em) jeśli wartość jest `null`.
- **Strony PDF**: każdy rekord zaczyna nową sekcję z separatorem; wiele rekordów w jednym pliku.
- **Nazwa pliku**: `Content-Disposition: inline; filename="trainers-report-20260517.pdf"` – przeglądarka wyświetli PDF z możliwością drukowania (Ctrl+P).
- **courses PDF**: wymaga danych uzupełniających (Participant name, CourseType code/description, Trainer/Lecturer/Technician names) – serwis PDF musi przyjmować dodatkowe listy do rozwiązania relacji.
- **Bezpieczeństwo**: CSRF – ukryty formularz POST musi zawierać `th:action` (Thymeleaf automatycznie dodaje CSRF token przy `th:action`).
- **Rozmiar JAR**: Dwa pliki TTF dodają ~1.5 MB do finalnego JAR — akceptowalne.
