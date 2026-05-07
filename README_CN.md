# Doc-Gen-Service

[🇬🇧 English](./README.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green.svg)](https://spring.io/projects/spring-boot)

基于 **Spring Boot** 的文档生成微服务，使用 **poi-tl** 渲染 Word 模板，**EasyExcel** 处理 Excel 文档。提供 RESTful API 供外部系统（如 Go 服务）调用，支持 Docker 容器化部署。

## ✨ 功能特性

- 📄 **Word 文档生成** - 基于模板的动态文档生成
- 📑 **批量 Word 生成** - 多条数据生成单个多页文档
- 📊 **Excel 动态生成** - 根据表头和数据动态创建 Excel
- 📋 **Excel 模板填充** - 支持变量替换和列表循环填充
- 🔄 **表格循环渲染** - 自动检测并渲染集合数据
- 📤 **模板管理** - 通过 API 上传和查询模板文件
- 🐳 **Docker 支持** - 多架构镜像 (amd64/arm64)
- 📚 **Go SDK** - 开箱即用的 Go 客户端库

## 📝 更新日志

### v0.0.4 (2026-01-08)
- 🐛 **修复**: 批量生成 Word 文档时正确添加分页符
- ⚡ **优化**: 使用 poi-tl 原生 `NiceXWPFDocument.merge()` 方法，更好地保留文档格式

## 🚀 快速开始

### 使用 Docker（推荐）

```bash
# 构建并运行
mvn clean package -DskipTests
docker-compose up -d --build

# 访问 Swagger UI
open http://localhost:8081/swagger-ui.html
```

### 本地开发

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/doc-gen-service-0.0.4.jar
```

## 📖 API 接口

### 生成 Word 文档

```http
POST /api/v1/doc/word
Content-Type: application/json
```

```json
{
  "templateName": "template.docx",
  "data": {"title": "我的报告", "date": "2025-01-01"},
  "fileName": "输出报告"
}
```

#### 图片插入

使用 `{{@imageKey}}` 模板语法插入图片。在 `data` 中传入图片载荷：

```json
{
  "templateName": "report-template.docx",
  "data": {
    "title": "年度报告",
    "logo": {
      "type": "image",
      "url": "https://example.com/logo.png"
    }
  },
  "fileName": "年度报告"
}
```

**图片载荷字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | 必须为 `"image"` |
| `url` | string | 是 | 图片 URL（仅支持 `http` 或 `https`） |
| `format` | string | 否 | 图片格式：`png`、`jpg`、`jpeg`；未传时从 URL 后缀或响应 `Content-Type` 推断 |
| `width` | integer | 否 | 图片宽度（像素，正整数），默认 `300` |
| `height` | integer | 否 | 图片高度（像素，正整数），默认 `200` |

### 批量生成 Word 文档

使用同一模板渲染多条数据，每条数据生成一页，合并为单个文档。

```http
POST /api/v1/doc/word/batch
Content-Type: application/json
```

```json
{
  "templateName": "certificate.docx",
  "dataList": [
    {"name": "张三", "award": "一等奖"},
    {"name": "李四", "award": "二等奖"}
  ],
  "fileName": "批量证书"
}
```

每条数据支持图片载荷：

```json
{
  "templateName": "badge-template.docx",
  "dataList": [
    {
      "name": "张三",
      "badge": {
        "type": "image",
        "url": "https://example.com/gold-badge.png",
        "width": 80,
        "height": 80
      }
    },
    {
      "name": "李四",
      "badge": {
        "type": "image",
        "url": "https://example.com/silver-badge.png",
        "width": 80,
        "height": 80
      }
    }
  ],
  "fileName": "徽章"
}
```

### 生成 Excel 文档

```http
POST /api/v1/doc/excel
Content-Type: application/json
```

```json
{
  "sheetName": "Sheet1",
  "headers": ["姓名", "年龄", "城市"],
  "data": [
    ["张三", 25, "北京"],
    ["李四", 30, "上海"]
  ],
  "fileName": "员工列表"
}
```

### 填充 Excel 模板

支持单值变量 `{variable}` 和列表循环 `{.field}` 语法。

```http
POST /api/v1/doc/excel/fill
Content-Type: application/json
```

```json
{
  "templateName": "report-template.xlsx",
  "data": {"title": "销售报告", "date": "2025-01-01"},
  "listData": {
    "items": [
      {"no": 1, "name": "商品A", "price": 100},
      {"no": 2, "name": "商品B", "price": 200}
    ]
  },
  "fileName": "销售报告"
}
```

### 模板管理

```http
# 上传模板
POST /api/v1/template/upload
Content-Type: multipart/form-data

# 获取模板列表
GET /api/v1/template/list

# 下载模板
GET /api/v1/template/download/{templateName}

# 删除模板
DELETE /api/v1/template/{templateName}
```

## 🔧 配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `TEMPLATE_PATH` | `./templates` | 模板文件目录 |
| `SERVER_PORT` | `8081` | 服务端口 |

## 📦 Go SDK

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.4
```

```go
client := docgen.NewClient("http://localhost:8081")

// 生成 Word 文档
doc, _ := client.GenerateWord("template.docx", data, "报告")
os.WriteFile("报告.docx", doc, 0644)

// 批量生成 Word 文档
dataList := []map[string]any{
    {"name": "张三", "award": "一等奖"},
    {"name": "李四", "award": "二等奖"},
}
batchDoc, _ := client.BatchGenerateWord("certificate.docx", dataList, "证书")

// 填充 Excel 模板
filledExcel, _ := client.FillExcelTemplate("template.xlsx", data, listData, "output")
```

## 🐳 多架构 Docker 构建

```bash
# 构建 ARM64 镜像
docker buildx build --platform linux/arm64 -t doc-gen-service:arm64 --load .

# 构建 AMD64 镜像
docker buildx build --platform linux/amd64 -t doc-gen-service:amd64 --load .
```

## 📋 模板语法

### Word (poi-tl)

| 语法 | 说明 | 示例 |
|------|------|------|
| `{{variable}}` | 文本替换 | `{{title}}` |
| `{{@image}}` | 图片插入（URL 载荷） | `{{@logo}}` — 传入 `{"type":"image","url":"..."}` |
| `{{#table}}` | 表格循环 | `{{#items}}` |
| `{{?condition}}` | 条件判断 | `{{?showHeader}}` |

### Excel (EasyExcel)

| 语法 | 说明 | 示例 |
|------|------|------|
| `{variable}` | 单值替换 | `{title}` |
| `{.field}` | 列表行循环 | `{.name}`, `{.price}` |

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.1 | Web 框架 |
| poi-tl | 1.12.1 | Word 模板引擎 |
| EasyExcel | 4.0.1 | Excel 处理 |
| SpringDoc | 2.3.0 | API 文档 |

## 📄 开源协议

[MIT License](./LICENSE)

## 🔗 相关链接

- [poi-tl 官方文档](http://deepoove.com/poi-tl/)
- [EasyExcel 官方文档](https://easyexcel.opensource.alibaba.com/)
- [Spring Boot 官网](https://spring.io/projects/spring-boot)
- [Go SDK 文档](./sdk/go/README.md)
