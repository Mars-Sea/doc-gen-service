# AGENTS.md

This file provides guidance to AI agents (Claude Code, Codex, etc.) when working with code in this repository.

## Project

Java 17 Spring Boot 3.2.1 microservice for document generation (Word/Excel). Uses Maven.

## Build & Test

- **Build:** `mvn clean package`
- **Test:** `mvn test`
- **Format:** `mvn spotless:apply`
- **Check format:** `mvn spotless:check`
- **Skip tests:** `mvn clean package -DskipTests` (used for Docker builds)
- **Run locally:** `java -jar target/doc-gen-service-0.0.6.jar`
- **Run with Docker:** `mvn clean package -DskipTests && docker-compose up -d --build`

## Tech Stack

- **Word generation:** poi-tl 1.12.1 (template-based)
- **Excel generation:** EasyExcel 4.0.1
- **Boilerplate reduction:** Lombok (`@Data`, `@Slf4j`, etc.)
- **API docs:** SpringDoc OpenAPI 2.3.0 at `/swagger-ui.html`
- **Code coverage:** JaCoCo 0.8.11 (report generated during `mvn test`)
- **Formatting:** Spotless 2.43.0 with google-java-format (AOSP style)

## Configuration

- `TEMPLATE_PATH` — template files directory (default: `./templates`)
- `SERVER_PORT` — server port (default: `8081`)

## Notes

- The previous Trellis-managed instructions under `.trellis/` were removed; this file is the canonical agent guidance for the repo.
- `com/` and `sdk/` at the repository root are local vendored directories, not part of the Maven build.
