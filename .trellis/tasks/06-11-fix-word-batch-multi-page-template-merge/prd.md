# Fix Word Batch Multi-Page Template Merge

## Goal

Fix `/api/v1/doc/word/batch` so batch Word generation works correctly when the source template itself contains multiple pages. The current implementation assumes each data item produces one page, but in practice each data item should render one complete template instance; when the template has multiple pages and the request contains multiple data items, merged output can become disordered or visually corrupted.

## What I already know

* User observed that when the Word template has multiple pages and `/word/batch` receives multiple data records, some generated data appears错乱 / mixed up.
* The endpoint is `POST /api/v1/doc/word/batch` in `src/main/java/io/github/marssea/docgen/controller/DocController.java`.
* Controller only validates/logs/passes through; the risky behavior is in `WordService.generateBatch`.
* `WordService.generateBatch` currently renders each data item with poi-tl and merges rendered `NiceXWPFDocument` instances with `mainDoc.merge(currentDoc)`.
* Current code comments and request DTO say “每条数据生成一页”, which is inaccurate for multi-page templates; the more accurate behavior is “每条数据生成一份模板实例”.
* Existing tests cover simple single-page batch templates and spaced placeholders, but not multi-page templates.
* Project is Java 17 / Spring Boot 3.2.1 / Maven; Word generation uses poi-tl 1.12.1 and Apache POI via poi-tl.

## Assumptions (temporary)

* For batch generation, each entry in `dataList` should render an independent copy of the full template, not a single logical page.
* The output should preserve the order of `dataList`.
* A page break should separate rendered template instances unless the template already controls its final break/section in a way that makes this unsafe.
* We should prefer a surgical fix using existing dependencies over introducing a new document manipulation library.

## Open Questions

* None.

## Requirements (evolving)

* `/word/batch` must support templates containing multiple pages.
* Each item in `dataList` must render as one full template instance.
* Rendered instances must appear in the same order as `dataList`.
* Existing single-page batch behavior must remain compatible.
* Existing image payload and table loop support must continue working.
* API/DTO documentation should no longer claim every data item always generates exactly one page.
* MVP scope is common production template support: paragraphs, explicit page breaks, ordinary tables, table loops, and image payload/relationship handling where feasible without replacing the Word generation stack.

## Acceptance Criteria (evolving)

* [ ] Add a regression test for a multi-page Word template with at least two data records.
* [ ] Generated output contains each data record’s values in deterministic order across full template instances.
* [ ] Existing WordService tests continue to pass.
* [ ] `/word/batch` docs/comments describe each data item as a full template instance instead of exactly one page.
* [ ] `mvn test` passes, or any failure is reported with exact output.

## Definition of Done (team quality bar)

* Tests added/updated (unit/integration where appropriate)
* Lint / typecheck / CI green
* Docs/notes updated if behavior changes
* Rollout/rollback considered if risky

## Out of Scope (explicit)

* Changing request/response API shape.
* Adding a separate batch ZIP export mode.
* Replacing poi-tl/EasyExcel stack.
* Guaranteeing perfect preservation for every advanced Word feature unless included in MVP scope below.

## Technical Notes

* `src/main/java/io/github/marssea/docgen/service/WordService.java` — core generation logic.
* `src/main/java/io/github/marssea/docgen/controller/DocController.java` — `/word/batch` endpoint delegates to service.
* `src/main/java/io/github/marssea/docgen/model/WordBatchRequest.java` — DTO docs currently state one data item equals one page.
* `src/test/java/io/github/marssea/docgen/service/WordServiceTest.java` — existing batch tests use simple templates only.
* `pom.xml` — dependencies include `com.deepoove:poi-tl:1.12.1`; no extra Word-processing library currently present.

## Expansion Sweep

### Future evolution

* Batch mode may later need output modes: one merged `.docx`, one `.zip` with one docx per data item, or PDF conversion.
* It may be useful to make separator behavior configurable later, but not necessary for the current bug fix.

### Related scenarios

* Single document `/word` should remain unchanged.
* Table row loop and image placeholder handling should remain consistent between `/word` and `/word/batch`.

### Failure & edge cases

* Multi-page template with explicit page breaks should not duplicate/mix data.
* Templates with headers/footers/images/tables may rely on relationships and section properties; merge strategy must avoid obvious relationship collisions.
* Empty/null data list behavior should remain as-is.

## Candidate MVP Scope Options

**Option 1: Text/table/page-break regression only (minimal)**

* Support and test multi-page templates made of paragraphs, page breaks, and tables.
* Lower implementation/test cost, but may not catch header/footer/image relationship issues.

**Option 2: Common production template support (recommended)**

* Support and test multi-page templates with paragraphs, explicit page breaks, tables, and at least one relationship-bearing element already covered by the code path where feasible.
* Better confidence for real templates while staying surgical.

**Option 3: Exhaustive advanced Word layout support**

* Attempt to validate headers/footers, sections, images, numbering, bookmarks, and complex relationships.
* Highest confidence but larger scope and likely more brittle tests.
