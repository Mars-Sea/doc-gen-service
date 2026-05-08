# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Java 17 Spring Boot 3.2.1 microservice for document generation (Word/Excel). Uses Maven.

## Build & Test

- **Build:** `mvn clean package`
- **Test:** `mvn test`
- **Format:** `mvn spotless:apply`
- **Check format:** `mvn spotless:check`
- **Skip tests:** `mvn clean package -DskipTests` (used for Docker builds)
- **Run locally:** `java -jar target/doc-gen-service-0.0.5.jar`
- **Run with Docker:** `mvn clean package -DskipTests && docker-compose up -d --build`

## Tech Stack

- **Word generation:** poi-tl 1.12.1 (template-based)
- **Excel generation:** EasyExcel 4.0.1
- **Boilerplate reduction:** Lombok (`@Data`, `@Slf4j`, etc.)
- **API docs:** SpringDoc OpenAPI 2.3.0 at `/swagger-ui.html`

## Configuration

- `TEMPLATE_PATH` — template files directory (default: `./templates`)
- `SERVER_PORT` — server port (default: `8081`)

## Project Workflow

This repo uses the Trellis development workflow. Active tasks and specs live under `.trellis/`. Use the existing Trellis skills (`/trellis:continue`, `/trellis:finish-work`, etc.) to navigate the workflow.
