# Logging Guidelines

> How logging is done in this project.

---

## Overview

Uses **Lombok `@Slf4j`** annotation for logging. No `System.out` / `System.err` usage allowed.

---

## Log Levels

| Level | When to Use | Example |
|-------|-------------|---------|
| `debug` | Internal details useful during development | Template path resolved, data map keys |
| `info` | Normal operations worth recording | Document generated successfully |
| `warn` | Client errors (4xx) — bad input, missing resources | Template not found, invalid image payload |
| `error` | Server errors (5xx) — unexpected failures | IO error, unhandled exception (include stack trace) |

---

## Structured Logging

Use SLF4J parameterized messages (not string concatenation):

```java
// ✅ Correct
log.warn("Template not found: {}", templateName);
log.error("IO error occurred: {}", e.getMessage(), e);  // last arg = stack trace

// ❌ Wrong
log.warn("Template not found: " + templateName);  // string concat, always evaluates
System.out.println("Debug: " + result);            // forbidden
```

---

## What to Log

- **warn**: All 4xx responses — template not found, validation failures, illegal arguments, invalid image payloads
- **error**: All 5xx responses — IO errors, unhandled exceptions (always include the exception as last argument for stack trace)
- **debug**: Internal state useful during development (template path resolution, data transformations)

---

## What NOT to Log

- **PII / secrets** — never log API keys, passwords, tokens
- **Full request bodies** at `info` level — may contain sensitive data
- **Sensitive file contents** — template content, generated document bytes
