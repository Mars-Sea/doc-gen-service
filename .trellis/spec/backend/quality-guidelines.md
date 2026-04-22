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

## Code Review Checklist

- [ ] `mvn spotless:check` passes
- [ ] `mvn test` passes (existing pre-existing errors are known)
- [ ] New public methods have Javadoc
- [ ] No `System.out` / `System.err` usage
- [ ] Template file paths are validated before use
