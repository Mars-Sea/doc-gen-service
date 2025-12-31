# Doc-Gen-Service Go SDK

用于调用文档生成服务 API 的 Go 客户端库。

## 安装

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go/docgen
```

## 快速开始

```go
package main

import (
    "log"
    "os"
    
    "github.com/Mars-Sea/doc-gen-service/sdk/go/docgen"
)

func main() {
    // 创建客户端
    client := docgen.NewClient("http://localhost:8081")
    
    // 准备数据
    data := map[string]any{
        "title": "My Report",
        "date":  "2025-01-01",
    }
    
    // 生成文档
    doc, err := client.GenerateWord("test-template.docx", data, "my_report")
    if err != nil {
        log.Fatal(err)
    }
    
    // 保存文件
    os.WriteFile("my_report.docx", doc, 0644)
}
```

## API

### NewClient(baseURL string) *Client

创建客户端，默认超时 30 秒。

### NewClientWithTimeout(baseURL string, timeout time.Duration) *Client

创建带自定义超时的客户端。

### client.GenerateWord(templateName, data, fileName) ([]byte, error)

生成 Word 文档，返回字节数组。

| 参数 | 类型 | 说明 |
|------|------|------|
| templateName | string | 模板文件名（需包含 .docx） |
| data | map[string]any | 渲染数据 |
| fileName | string | 输出文件名（不含扩展名，可选） |

### client.SaveWord(templateName, data, outputPath) error

生成并保存 Word 文档到指定路径。

## 错误处理

```go
doc, err := client.GenerateWord("template.docx", data, "")
if err != nil {
    if apiErr, ok := err.(*docgen.ErrorResponse); ok {
        // API 返回的错误
        fmt.Printf("Code: %s, Message: %s\n", apiErr.Code, apiErr.Message)
    } else {
        // 网络或其他错误
        log.Fatal(err)
    }
}
```

## 表格循环示例

```go
data := map[string]any{
    "month": "January 2025",
    "goods": []map[string]any{
        {"name": "Product A", "price": 299.99},
        {"name": "Product B", "price": 49.99},
    },
}

doc, err := client.GenerateWord("loop-table-template.docx", data, "report")
```
