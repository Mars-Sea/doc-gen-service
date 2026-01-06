# Doc-Gen-Service Go SDK

[🇬🇧 English](#doc-gen-service-go-sdk) | [🇨🇳 中文](#中文文档)

[![Go Reference](https://pkg.go.dev/badge/github.com/Mars-Sea/doc-gen-service/sdk/go.svg)](https://pkg.go.dev/github.com/Mars-Sea/doc-gen-service/sdk/go)
[![Go Version](https://img.shields.io/badge/Go-1.20+-blue.svg)](https://go.dev/)

Go client library for Doc-Gen-Service API.

## Installation

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.3
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

### Word Document Generation

| Method | Returns | Description |
|--------|---------|-------------|
| `GenerateWord(template, data, fileName)` | `[]byte, error` | Generate Word document |
| `SaveWord(template, data, outputPath)` | `error` | Generate and save to file |
| `BatchGenerateWord(template, dataList, fileName)` | `[]byte, error` | Generate multi-page Word from list |
| `SaveBatchWord(template, dataList, outputPath)` | `error` | Batch generate and save |

### Excel Document Generation

| Method | Returns | Description |
|--------|---------|-------------|
| `GenerateExcel(sheetName, headers, data, fileName)` | `[]byte, error` | Generate Excel dynamically |
| `SaveExcel(sheetName, headers, data, outputPath)` | `error` | Generate Excel and save |
| `FillExcelTemplate(template, data, listData, fileName)` | `[]byte, error` | Fill Excel template |
| `SaveFilledExcel(template, data, listData, outputPath)` | `error` | Fill template and save |

### Template Management

| Method | Returns | Description |
|--------|---------|-------------|
| `UploadTemplate(filePath)` | `*UploadResponse, error` | Upload from file path |
| `UploadTemplateFromBytes(data, filename)` | `*UploadResponse, error` | Upload from bytes |
| `ListTemplates()` | `[]string, error` | Get template names |
| `DownloadTemplate(templateName)` | `[]byte, error` | Download template content |
| `DeleteTemplate(templateName)` | `*DeleteResponse, error` | Delete template |

## Examples

### Batch Generate Word

```go
dataList := []map[string]any{
    {"name": "Alice", "award": "Gold"},
    {"name": "Bob", "award": "Silver"},
}
doc, _ := client.BatchGenerateWord("certificate.docx", dataList, "certificates")
os.WriteFile("certificates.docx", doc, 0644)
```

### Fill Excel Template

```go
data := map[string]any{"title": "Report", "date": "2025-01-01"}
listData := map[string][]map[string]any{
    "items": {
        {"no": 1, "name": "Product A", "price": 100},
        {"no": 2, "name": "Product B", "price": 200},
    },
}
doc, _ := client.FillExcelTemplate("template.xlsx", data, listData, "output")
os.WriteFile("output.xlsx", doc, 0644)
```

### Error Handling

```go
doc, err := client.GenerateWord("template.docx", data, "")
if err != nil {
    if apiErr, ok := err.(*docgen.ErrorResponse); ok {
        fmt.Printf("API Error [%s]: %s\n", apiErr.Code, apiErr.Message)
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
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.3
```

## 快速开始

```go
client := docgen.NewClient("http://localhost:8081")

// 生成单个 Word 文档
data := map[string]any{"title": "报告", "date": "2025-01-01"}
doc, _ := client.GenerateWord("template.docx", data, "报告")

// 批量生成 Word 文档
dataList := []map[string]any{
    {"name": "张三", "award": "一等奖"},
    {"name": "李四", "award": "二等奖"},
}
batchDoc, _ := client.BatchGenerateWord("certificate.docx", dataList, "证书")

// 填充 Excel 模板
listData := map[string][]map[string]any{
    "items": {{"name": "商品A", "price": 100}},
}
excelDoc, _ := client.FillExcelTemplate("template.xlsx", data, listData, "output")
```

## 主要方法

| 方法 | 说明 |
|------|------|
| `Health()` / `IsHealthy()` | 健康检查 |
| `GenerateWord()` / `SaveWord()` | 生成 Word 文档 |
| `BatchGenerateWord()` / `SaveBatchWord()` | 批量生成 Word 文档 |
| `GenerateExcel()` / `SaveExcel()` | 动态生成 Excel |
| `FillExcelTemplate()` / `SaveFilledExcel()` | 填充 Excel 模板 |
| `UploadTemplate()` / `ListTemplates()` / `DeleteTemplate()` | 模板管理 |

## License

MIT
