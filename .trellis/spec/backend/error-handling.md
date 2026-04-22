# Error Handling

> How errors are handled in this project.

---

## Overview

Errors are handled via `GlobalExceptionHandler` (`@RestControllerAdvice`), which converts all exceptions into a standard JSON error response with an HTTP status code.

---

## Error Types

Custom exception:

- **`TemplateNotFoundException`** — template file referenced in the request does not exist on disk.

Standard exceptions used throughout:

- **`IllegalArgumentException`** — invalid input (empty data list, bad template extension, etc.)
- **`IOException`** — file read/write failures

---

## Error Handling Patterns

### Service Layer

Services throw checked/unchecked exceptions directly. Do **not** catch and swallow:

```java
if (!templateFile.exists()) {
    throw new TemplateNotFoundException(templateName, "...");
}
```

### Controller / Global Handler

`GlobalExceptionHandler` maps exceptions to HTTP responses:

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `TemplateNotFoundException` | 422 Unprocessable Entity | `TEMPLATE_NOT_FOUND` |
| `MethodArgumentNotValidException` | 400 Bad Request | `VALIDATION_ERROR` |
| `IllegalArgumentException` | 400 Bad Request | `INVALID_ARGUMENT` |
| `IOException` | 500 Internal Server Error | `IO_ERROR` |
| All others (`Exception`) | 500 Internal Server Error | `INTERNAL_ERROR` |

> 422 is used for `TemplateNotFoundException` (not 404) because the API endpoint exists — only the referenced template resource is missing.

### Response Format

```json
{
  "status": 422,
  "code": "TEMPLATE_NOT_FOUND",
  "message": "Template not found at: ./templates/missing.docx"
}
```

### Logging

- `warn` for client errors (4xx) — `TemplateNotFoundException`, validation errors, illegal arguments
- `error` for server errors (5xx) — IO errors, unexpected exceptions (include stack trace)

---

## Common Mistakes

- Returning 404 for missing templates — the project uses 422 instead (API path exists, resource missing).
- Catching `IOException` inside services and returning `null` or empty arrays — let it propagate to the global handler.
