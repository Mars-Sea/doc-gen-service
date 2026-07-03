# Quality Guidelines

> Code quality standards for backend development.

---

## Formatting

All Java source files are formatted automatically by **Spotless** using **google-java-format** (AOSP style, 4-space indentation).

- **Apply formatting:** `mvn spotless:apply`
- **Check formatting:** `mvn spotless:check`
- The CI workflow runs `mvn spotless:check` on every PR — formatting violations block merge.
- A `PostToolUse` hook in `.claude/settings.json` auto-formats Java files after every `Write`/`Edit`.

---

## Forbidden Patterns

- **Raw `System.out.print`** — use `@Slf4j` + `log.info/debug/warn/error` instead.
- **Path traversal in template names** — always validate template names with `TemplateValidationUtil` before file operations.
- **Swallowing exceptions silently** — all exceptions must be logged or propagated.

---

## Required Patterns

- **Lombok** for boilerplate: `@Data`, `@Slf4j`, `@RequiredArgsConstructor` are standard.
- **Constructor injection** for Spring services (via `@RequiredArgsConstructor`).
- **Javadoc** in Chinese for public API classes and methods.

---

## Testing Requirements

- Run tests with: `mvn test`
- The CI workflow runs the full test suite on every PR.
- Tests use JUnit 5 (`@Test`, `@BeforeEach`, `@Nested`) with `@TempDir` for filesystem fixtures.
- Note: some existing `WordServiceTest` tests have pre-existing errors related to template rendering edge cases.

---

## Word Batch Template Merge Contract

### 1. Scope / Trigger

- Trigger: changes to `/api/v1/doc/word/batch` or `WordService.generateBatch`.
- Batch Word generation must treat each `dataList` entry as a complete rendered template instance, not as one physical page.
- This matters because a template may contain multiple pages; custom XML/body copying can drop the final page of non-final instances or corrupt Word relationships.

### 2. Signatures

- Controller: `POST /api/v1/doc/word/batch`
- Request DTO: `WordBatchRequest`
- Service: `byte[] WordService.generateBatch(String templateName, List<Map<String, Object>> dataList)`

### 3. Contracts

- `templateName`: required `.docx` template name; validate with `TemplateValidationUtil.validateWordTemplateExtension` before file access.
- `dataList`: required non-empty list; each item renders one complete template instance.
- Output: a single `.docx` byte array containing all rendered template instances in `dataList` order.
- Separator: insert a page break between rendered instances.
- Rendering behavior: preserve existing placeholder, spaced placeholder, table, table-loop, and image payload behavior.

### 4. Validation & Error Matrix

- Invalid template name/extension -> `IllegalArgumentException`.
- Missing template file -> `TemplateNotFoundException`.
- `dataList == null` or empty -> `IllegalArgumentException("Data list cannot be null or empty")`.
- Invalid image payload -> `InvalidImagePayloadException`; do not wrap it as `IOException`.
- Unexpected merge failure -> wrap in `IOException("Failed to merge batch word documents", cause)`.

### 5. Good/Base/Bad Cases

- Good: four-page template + three data records produces page markers `第一、第二、第三、第四` repeated three times in order.
- Base: single-page template + multiple data records still produces all records in order.
- Bad: output sequence `第一、第二、第三、第一` for a four-page template means the merge strategy replaced/dropped a non-final instance’s last page.

### 6. Tests Required

- Add or maintain a `WordServiceTest` regression for multi-page templates with assertion points:
  - 4-page template x 3 data records.
  - Assert all 12 page markers appear in exact order.
  - Assert no placeholder text remains for those markers.
- Keep focused verification with `mvn -Dtest=WordServiceTest test`.
- Run full `mvn test` and `mvn spotless:check` before finishing.

### 7. Wrong vs Correct

#### Wrong

```java
// Do not manually splice body XML or refresh POI body lists.
// This can drop final pages and fails to remap relationship IDs.
copyBodyElementsBeforeSectPr(sourceBody, targetBody);
refreshBodyElementLists(document);
```

#### Correct

```java
NiceXWPFDocument mainDoc = renderTemplateInstance(templateFile, dataList.get(0));
for (int i = 1; i < dataList.size(); i++) {
    NiceXWPFDocument nextDoc = renderTemplateInstance(templateFile, dataList.get(i));
    mainDoc.createParagraph().createRun().addBreak(BreakType.PAGE);
    mainDoc = mainDoc.merge(nextDoc);
}
```

Use poi-tl `NiceXWPFDocument.merge()` because it handles body XML concatenation, relationship ID remapping, and section property cleanup.

---

## Excel Template Fill Contract

### 1. Scope / Trigger

- Trigger: changes to `/api/v1/doc/excel/fill` or `ExcelService.fillTemplate`.
- This matters for large `listData` payloads because EasyExcel `forceNewRow(true)` disables streaming-style writes and keeps the workbook in memory.

### 2. Signatures

- Controller: `POST /api/v1/doc/excel/fill`
- Request DTO: `ExcelFillRequest`
- Service: `byte[] ExcelService.fillTemplate(String templateName, Map<String, Object> data, Map<String, List<Map<String, Object>>> listData)`

### 3. Contracts

- `templateName`: required `.xlsx` or `.xls` template name; validate with `TemplateValidationUtil.validateExcelTemplateExtension` before file access.
- Single-value placeholders: `{field}` are filled from `data`.
- List placeholders: support both unprefixed `{.field}` and named `{items.field}` placeholders.
- Fill order: fill list data before single-value data so rows inserted by list fill do not move already-filled scalar cells.
- Row insertion policy: inspect the template before filling. Use `forceNewRow(false)` only when the collection placeholder row is the final physical row on the sheet and no merged region extends below it. Use `forceNewRow(true)` when any physical template row, hidden row, merged region, footer, or second collection row exists below the collection row.
- Do not unconditionally set `forceNewRow(true)` for all list fills.

### 4. Validation & Error Matrix

- Invalid template name/extension -> `IllegalArgumentException`.
- Missing template file -> `TemplateNotFoundException`.
- Invalid image payload in `data` -> `InvalidImagePayloadException`; do not swallow it.
- Unexpected EasyExcel/POI failure -> wrap as `RuntimeException("Failed to fill Excel template: ...", cause)`.
- Large terminal list should not fail with EasyExcel close/IO wrapper errors caused by unnecessary in-memory row shifting.

### 5. Good/Base/Bad Cases

- Good: a template whose list row is the final physical row fills 2,500+ records without forcing row insertion.
- Good: a template with a summary/footer row, hidden row, blank preformatted row, merged region, or later collection row below the list preserves visible data by using `forceNewRow(true)`.
- Base: a template with only scalar placeholders still returns a valid workbook.
- Bad: always using `FillConfig.builder().forceNewRow(Boolean.TRUE)` for list fills; this makes large list fills slow and memory-heavy.

### 6. Tests Required

- Add or maintain `ExcelServiceTest` coverage for:
  - 2,500+ row terminal list fill, asserting the first and last generated rows.
  - List fill with a footer/summary row below it, asserting the footer is shifted and scalar-filled.
  - Named list placeholder `{items.field}`, asserting the `listData` key is honored via `FillWrapper`.
- Run focused verification with `mvn -Dtest=ExcelServiceTest test`.
- Run full `mvn test` and `mvn spotless:check` before finishing.

### 7. Wrong vs Correct

#### Wrong

```java
FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
excelWriter.fill(rows, fillConfig, writeSheet);
```

#### Correct

```java
ExcelTemplateAnalysis templateAnalysis = analyzeExcelTemplate(templateFile);
FillConfig fillConfig =
        FillConfig.builder().forceNewRow(templateAnalysis.requiresForceNewRow()).build();
excelWriter.fill(fillData, fillConfig, writeSheet);
```

---

## Code Review Checklist

- [ ] `mvn spotless:check` passes
- [ ] `mvn test` passes (existing pre-existing errors are known)
- [ ] New public methods have Javadoc
- [ ] No `System.out` / `System.err` usage
- [ ] Template file paths are validated before use
