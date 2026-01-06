# Doc-Gen-Service Go SDK

[🇬🇧 English](#doc-gen-service-go-sdk) | [🇨🇳 中文](#中文文档)

[![Go Reference](https://pkg.go.dev/badge/github.com/Mars-Sea/doc-gen-service/sdk/go.svg)](https://pkg.go.dev/github.com/Mars-Sea/doc-gen-service/sdk/go)
[![Go Version](https://img.shields.io/badge/Go-1.20+-blue.svg)](https://go.dev/)

Go client library for Doc-Gen-Service API.

## Installation

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.2
```

## Quick Start

```go
package main

import (
    "log"
    "os"
    
    "github.com/Mars-Sea/doc-gen-service/sdk/go/docgen"
)

func main() {
    client := docgen.NewClient("http://localhost:8081")
    
    // Check health
    if !client.IsHealthy() {
        log.Fatal("Service unavailable")
    }
    
    // Generate document
    data := map[string]any{
        "title": "My Report",
        "date":  "2025-01-01",
    }
    doc, _ := client.GenerateWord("template.docx", data, "report")
    os.WriteFile("report.docx", doc, 0644)
}
```

## API Reference

### Client

| Method | Description |
|--------|-------------|
| `NewClient(baseURL)` | Create client (30s timeout) |
| `NewClientWithTimeout(baseURL, timeout)` | Create client with custom timeout |

### Health Check

| Method | Returns | Description |
|--------|---------|-------------|
| `Health()` | `*HealthResponse, error` | Get health status details |
| `IsHealthy()` | `bool` | Quick health check |

### Document Generation

| Method | Returns | Description |
|--------|---------|-------------|
| `GenerateWord(template, data, fileName)` | `[]byte, error` | Generate Word document |
| `SaveWord(template, data, outputPath)` | `error` | Generate and save to file |
| `GenerateExcel(sheetName, headers, data, fileName)` | `[]byte, error` | Generate Excel document |
| `SaveExcel(sheetName, headers, data, outputPath)` | `error` | Generate Excel and save to file |

### Template Management

| Method | Returns | Description |
|--------|---------|-------------|
| `UploadTemplate(filePath)` | `*UploadResponse, error` | Upload from file path |
| `UploadTemplateFromBytes(data, filename)` | `*UploadResponse, error` | Upload from bytes |
| `ListTemplates()` | `[]string, error` | Get template names |
| `ListTemplatesWithDetails()` | `*ListTemplatesResponse, error` | Get templates with count |
| `DownloadTemplate(templateName)` | `[]byte, error` | Download template content |
| `SaveTemplate(templateName, outputPath)` | `error` | Download and save to file |
| `DeleteTemplate(templateName)` | `*DeleteResponse, error` | Delete template |

## Examples

### Health Check

```go
if client.IsHealthy() {
    fmt.Println("Service is UP")
}
```

### Generate Document

```go
data := map[string]any{
    "title": "Report",
    "items": []map[string]any{
        {"name": "Item A", "price": 100},
        {"name": "Item B", "price": 200},
    },
}
doc, _ := client.GenerateWord("template.docx", data, "output")
os.WriteFile("output.docx", doc, 0644)
```

### Generate Excel

```go
headers := []string{"Name", "Age", "City"}
data := [][]any{
    {"Alice", 25, "Beijing"},
    {"Bob", 30, "Shanghai"},
}
doc, _ := client.GenerateExcel("Sheet1", headers, data, "employees")
os.WriteFile("employees.xlsx", doc, 0644)

// Or save directly
client.SaveExcel("Sheet1", headers, data, "employees.xlsx")
```

### Template Management

```go
// Upload
result, _ := client.UploadTemplate("./template.docx")
fmt.Println(result.FileName)

// List
templates, _ := client.ListTemplates()
for _, t := range templates {
    fmt.Println(t)
}

// Download
content, _ := client.DownloadTemplate("template.docx")
os.WriteFile("local.docx", content, 0644)
// Or use SaveTemplate
client.SaveTemplate("template.docx", "./local.docx")

// Delete
client.DeleteTemplate("old-template.docx")
```

### Error Handling

```go
doc, err := client.GenerateWord("template.docx", data, "")
if err != nil {
    if apiErr, ok := err.(*docgen.ErrorResponse); ok {
        fmt.Printf("API Error: %s\n", apiErr.Message)
    } else {
        log.Fatal(err)
    }
}
```

---

# 中文文档

用于调用文档生成服务 API 的 Go 客户端库。

## 安装

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.2
```

## 快速开始

```go
client := docgen.NewClient("http://localhost:8081")

// 检查服务健康
if !client.IsHealthy() {
    log.Fatal("服务不可用")
}

// 生成文档
data := map[string]any{"title": "报告", "date": "2025-01-01"}
doc, _ := client.GenerateWord("template.docx", data, "报告")
os.WriteFile("报告.docx", doc, 0644)
```

## 主要方法

| 方法 | 说明 |
|------|------|
| `Health()` / `IsHealthy()` | 健康检查 |
| `GenerateWord()` / `SaveWord()` | 生成 Word 文档 |
| `GenerateExcel()` / `SaveExcel()` | 生成 Excel 文档 |
| `UploadTemplate()` | 上传模板 |
| `ListTemplates()` | 获取模板列表 |
| `DeleteTemplate()` | 删除模板 |

## License

MIT
