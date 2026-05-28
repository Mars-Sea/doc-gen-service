# Fix Word template placeholders with spaces

## Goal

Word document generation should tolerate common human-written poi-tl placeholders that include whitespace inside braces, such as `{{ age }}`, instead of failing when rendering templates prepared by users.

## What I already know

* User reported Word generation fails when a template contains placeholders like `{{ age }}` with spaces before/after the variable name.
* The service uses poi-tl 1.12.1 and Spring Boot Java 17.
* Word rendering is implemented in `src/main/java/io/github/marssea/docgen/service/WordService.java`.
* `generateWord` and `generateBatch` both call `preprocessImagePayloads`, then `buildRenderConfig`, then `XWPFTemplate.compile(...).render(...)`.
* `buildRenderConfig` currently uses `Configure.builder().useSpringEL()` and binds iterable keys to `LoopRowTableRenderPolicy`.
* Existing `WordServiceTest` creates in-memory `.docx` templates with Apache POI and tests normal rendering, batch rendering, and image payload validation.

## Assumptions (temporary)

* Whitespace normalization should apply to Word poi-tl placeholders only, not Excel template syntax.
* The intended data key remains the trimmed name (`age`) rather than requiring callers to send a key containing spaces.
* Existing image placeholders like `{{@imageKey}}` and table/list placeholders should keep working.

## Open Questions

* None.

## Requirements (evolving)

* Word generation accepts placeholders with leading/trailing whitespace inside the braces for normal text values, e.g. `{{ age }}` renders from data key `age`.
* Whitespace normalization is limited to trimming the placeholder variable/expression token at the tag boundary; do not rewrite arbitrary internal expression whitespace.
* The same behavior applies to batch Word generation.
* Existing no-space placeholders keep working.
* Existing image payload and table row loop behavior should not regress.

## Acceptance Criteria (evolving)

* [ ] A Word template containing `{{ age }}` renders successfully when data contains `age`.
* [ ] Batch generation also supports spaced placeholders.
* [ ] Existing Word service tests continue to pass, except any documented pre-existing failures.
* [ ] Maven tests/format checks are run or any inability to run them is reported.

## Definition of Done (team quality bar)

* Tests added/updated for the whitespace placeholder behavior.
* Spotless formatting applied/checkable.
* Relevant test suite run.
* No changes to public API contract unless explicitly agreed.

## Out of Scope (explicit)

* Excel placeholder behavior.
* Introducing a new template language.
* Changing request/response models.

## Technical Notes

* Inspected `WordService.java`, especially `generateWord`, `generateBatch`, `buildRenderConfig`, and `preprocessImagePayloads`.
* Inspected `WordServiceTest.java` for in-memory `.docx` fixture patterns.
* Backend quality spec requires Spotless/google-java-format, JUnit 5, `@TempDir`, no path traversal bypass, and Chinese Javadocs for public APIs.
