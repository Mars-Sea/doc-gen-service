# Directory Structure

> How backend code is organized in this project.

---

## Overview

Spring Boot project using standard Maven layout. Business logic is in service layer, REST endpoints in controller layer, shared utilities in util package.

---

## Directory Layout

```
src/main/java/io/github/marssea/docgen/
├── config/                   # Spring configuration
│   ├── DocGenProperties.java       # Template directory config
│   ├── GlobalExceptionHandler.java # Unified error handling
│   ├── OpenApiConfig.java          # Swagger/OpenAPI config
│   └── TemplateInitializer.java    # Template file init on startup
├── controller/               # REST API endpoints
│   ├── DocController.java          # Word/Excel generation endpoints
│   └── TemplateController.java     # Template CRUD endpoints
├── exception/                # Custom exceptions
│   ├── InvalidImagePayloadException.java
│   └── TemplateNotFoundException.java
├── model/                    # Request DTOs
│   ├── ExcelFillRequest.java
│   ├── ExcelGenRequest.java
│   ├── WordBatchRequest.java
│   └── WordGenRequest.java
├── service/                  # Business logic
│   ├── ExcelService.java
│   ├── TemplateService.java
│   └── WordService.java
├── util/                     # Shared utilities
│   ├── ImagePayloadConverter.java
│   └── TemplateValidationUtil.java
└── DocGenApplication.java    # Entry point

sdk/go/                       # Go client SDK
├── docgen/                   # Library package (client, file, template)
└── example/                  # Usage examples

templates/                    # Pre-built template files (.docx, .xlsx)
```

---

## Module Organization

- **New endpoints** → add to existing controller or create new `@RestController` in `controller/`
- **New services** → add `@Service` class in `service/`, inject via `@RequiredArgsConstructor`
- **New request models** → add `@Data` DTO in `model/`
- **New exceptions** → add in `exception/`, register handler in `GlobalExceptionHandler`
- **New utilities** → add in `util/`

---

## Naming Conventions

- Classes: PascalCase (`WordService`, `TemplateNotFoundException`)
- Packages: lowercase singular (`controller`, `service`, `model`, `exception`, `util`)
- Request DTOs: `<Feature>Request` (`WordGenRequest`, `ExcelFillRequest`)
- Services: `<Feature>Service` (`WordService`, `ExcelService`)

---

## Examples

- `WordService` — good example of service pattern (Lombok, constructor injection, validation, error propagation)
- `GlobalExceptionHandler` — good example of config pattern (`@RestControllerAdvice`)
- `TemplateValidationUtil` — good example of utility pattern (static methods, no state)
