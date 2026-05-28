# Doc-Gen-Service

[🇨🇳 中文文档](./README_CN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green.svg)](https://spring.io/projects/spring-boot)

A document generation microservice based on **Spring Boot**, using **poi-tl** for Word template rendering and **EasyExcel** for Excel operations. Provides RESTful APIs for external systems (e.g., Go services) to call, with Docker containerization support.

## ✨ Features

- 📄 **Word Document Generation** - Template-based dynamic document generation using poi-tl
- 📑 **Batch Word Generation** - Generate multi-page documents from multiple data records
- 📊 **Excel Generation** - Dynamic Excel creation with EasyExcel
- 📋 **Excel Template Fill** - Fill Excel templates with variables and list data
- 🔄 **Table Loop Rendering** - Automatic detection and rendering of collection data
- 📤 **Template Management** - Upload and list template files via API
- 🐳 **Docker Ready** - Multi-architecture support (amd64/arm64)
- 📚 **Go SDK** - Ready-to-use Go client library

## 📝 Changelog

### v0.0.5 (2026-05-08)
- ✨ **New**: Word document generation now supports image insertion via URL payloads (`{{@imageKey}}` template syntax)
- 📦 **Go SDK**: Added `Image()` helper function for constructing image payloads

### v0.0.4 (2026-01-08)
- 🐛 **Fixed**: Batch Word generation now correctly adds page breaks between documents
- ⚡ **Improved**: Using poi-tl native `NiceXWPFDocument.merge()` for better document format preservation

## 🚀 Quick Start

### Using Docker (Recommended)

```bash
# Build and run
mvn clean package -DskipTests
docker-compose up -d --build

# Access Swagger UI
open http://localhost:8081/swagger-ui.html
```

### Local Development

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/doc-gen-service-0.0.5.jar
```

## 📖 API Reference

### Generate Word Document

```http
POST /api/v1/doc/word
Content-Type: application/json
```

```json
{
  "templateName": "template.docx",
  "data": {
    "title": "My Report",
    "date": "2025-01-01"
  },
  "fileName": "output_report"
}
```

#### Image Insertion

Use `{{@imageKey}}` template syntax for images. Pass image payloads in the `data` map:

```json
{
  "templateName": "report-template.docx",
  "data": {
    "title": "Annual Report",
    "logo": {
      "type": "image",
      "url": "https://example.com/logo.png"
    }
  },
  "fileName": "annual_report"
}
```

**Image payload fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Must be `"image"` |
| `url` | string | Yes | Image URL (`http` or `https` only) |
| `format` | string | No | Image format: `png`, `jpg`, `jpeg`; inferred from URL suffix or response `Content-Type` when omitted |
| `width` | integer | No | Image width in pixels (positive); defaults to `300` |
| `height` | integer | No | Image height in pixels (positive); defaults to `200` |

### Batch Generate Word Document

Generate a single document with multiple pages from multiple data records.

```http
POST /api/v1/doc/word/batch
Content-Type: application/json
```

```json
{
  "templateName": "certificate.docx",
  "dataList": [
    {"name": "Alice", "award": "Gold"},
    {"name": "Bob", "award": "Silver"}
  ],
  "fileName": "certificates"
}
```

Each item in `dataList` supports image payloads:

```json
{
  "templateName": "badge-template.docx",
  "dataList": [
    {
      "name": "Alice",
      "badge": {
        "type": "image",
        "url": "https://example.com/gold-badge.png",
        "width": 80,
        "height": 80
      }
    },
    {
      "name": "Bob",
      "badge": {
        "type": "image",
        "url": "https://example.com/silver-badge.png",
        "width": 80,
        "height": 80
      }
    }
  ],
  "fileName": "badges"
}
```

### Generate Excel Document

```http
POST /api/v1/doc/excel
Content-Type: application/json
```

```json
{
  "sheetName": "Sheet1",
  "headers": ["Name", "Age", "City"],
  "data": [
    ["Alice", 25, "Beijing"],
    ["Bob", 30, "Shanghai"]
  ],
  "fileName": "employees"
}
```

### Fill Excel Template

Fill Excel templates with variables `{variable}`, image variables, and list data `{.field}`.

```http
POST /api/v1/doc/excel/fill
Content-Type: application/json
```

```json
{
  "templateName": "report-template.xlsx",
  "data": {
    "title": "Sales Report",
    "date": "2025-01-01",
    "logo": {"type": "image", "url": "https://example.com/logo.png", "format": "png", "width": 120, "height": 60}
  },
  "listData": {
    "items": [
      {"no": 1, "name": "Product A", "price": 100},
      {"no": 2, "name": "Product B", "price": 200}
    ]
  },
  "fileName": "sales_report"
}
```

### Template Management

```http
# Upload template
POST /api/v1/template/upload
Content-Type: multipart/form-data

# List templates
GET /api/v1/template/list

# Download template
GET /api/v1/template/download/{templateName}

# Delete template
DELETE /api/v1/template/{templateName}
```

## 🔧 Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `TEMPLATE_PATH` | `./templates` | Template files directory |
| `SERVER_PORT` | `8081` | Server port |

## 📦 Go SDK

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.5
```

```go
client := docgen.NewClient("http://localhost:8081")

// Generate Word document
doc, _ := client.GenerateWord("template.docx", data, "report")
os.WriteFile("report.docx", doc, 0644)

// Batch generate Word document
dataList := []map[string]any{
    {"name": "Alice", "award": "Gold"},
    {"name": "Bob", "award": "Silver"},
}
batchDoc, _ := client.BatchGenerateWord("certificate.docx", dataList, "certificates")

// Fill Excel template
filledExcel, _ := client.FillExcelTemplate("template.xlsx", data, listData, "output")
```

## 🐳 Multi-Architecture Docker Build

```bash
# Build for ARM64
docker buildx build --platform linux/arm64 -t doc-gen-service:arm64 --load .

# Build for AMD64
docker buildx build --platform linux/amd64 -t doc-gen-service:amd64 --load .
```

## 📋 Template Syntax

### Word (poi-tl)

| Syntax | Description | Example |
|--------|-------------|---------|
| `{{variable}}` | Text replacement | `{{title}}` |
| `{{@image}}` | Image insertion (URL payload) | `{{@logo}}` — pass `{"type":"image","url":"..."}` |
| `{{#table}}` | Table loop | `{{#items}}` |
| `{{?condition}}` | Conditional | `{{?showHeader}}` |

### Excel (EasyExcel)

| Syntax | Description | Example |
|--------|-------------|---------|
| `{variable}` | Single value or image payload in template fill | `{title}`, `{logo}` |
| `{.field}` | List row loop | `{.name}`, `{.price}` |

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Runtime |
| Spring Boot | 3.2.1 | Web Framework |
| poi-tl | 1.12.1 | Word Template Engine |
| EasyExcel | 4.0.1 | Excel Processing |
| SpringDoc | 2.3.0 | API Documentation |

## 📄 License

[MIT License](./LICENSE)

## 🔗 Links

- [poi-tl Documentation](http://deepoove.com/poi-tl/)
- [EasyExcel Documentation](https://easyexcel.opensource.alibaba.com/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Go SDK Documentation](./sdk/go/README.md)
