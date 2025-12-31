# Doc-Gen-Service

[🇬🇧 English](./README.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green.svg)](https://spring.io/projects/spring-boot)

基于 **Spring Boot** 的文档生成微服务，使用 **poi-tl** 渲染 Word 模板。提供 RESTful API 供外部系统（如 Go 服务）调用，支持 Docker 容器化部署。

## ✨ 功能特性

- 📄 **Word 文档生成** - 基于模板的动态文档生成
- 📊 **Excel 支持** - 集成 EasyExcel 处理电子表格
- 🔄 **表格循环渲染** - 自动检测并渲染集合数据
- 📤 **模板管理** - 通过 API 上传和查询模板文件
- 🐳 **Docker 支持** - 多架构镜像 (amd64/arm64)
- 📚 **Go SDK** - 开箱即用的 Go 客户端库

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
java -jar target/doc-gen-service-0.0.1-SNAPSHOT.jar
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
  "data": {
    "title": "我的报告",
    "date": "2025-01-01"
  },
  "fileName": "输出报告"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `templateName` | string | ✅ | 模板文件名 |
| `data` | object | ✅ | 渲染数据 |
| `fileName` | string | ❌ | 输出文件名（支持中文） |

### 模板管理

```http
# 上传模板
POST /api/v1/template/upload
Content-Type: multipart/form-data

# 获取模板列表
GET /api/v1/template/list
```

## 🔧 配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `TEMPLATE_PATH` | `./templates` | 模板文件目录 |
| `SERVER_PORT` | `8081` | 服务端口 |

## 📦 Go SDK

```bash
go get github.com/Mars-Sea/doc-gen-service/sdk/go@v0.0.1
```

```go
client := docgen.NewClient("http://localhost:8081")

// 生成文档
doc, _ := client.GenerateWord("template.docx", data, "报告")
os.WriteFile("报告.docx", doc, 0644)

// 获取模板列表
templates, _ := client.ListTemplates()

// 上传模板
result, _ := client.UploadTemplate("/path/to/template.docx")
```

## 🐳 多架构 Docker 构建

```bash
# 构建 ARM64 镜像
docker buildx build --platform linux/arm64 -t doc-gen-service:arm64 --load .

# 构建 AMD64 镜像
docker buildx build --platform linux/amd64 -t doc-gen-service:amd64 --load .
```

## 📋 模板语法 (poi-tl)

| 语法 | 说明 | 示例 |
|------|------|------|
| `{{variable}}` | 文本替换 | `{{title}}` |
| `{{@image}}` | 图片插入 | `{{@logo}}` |
| `{{#table}}` | 表格循环 | `{{#items}}` |
| `{{?condition}}` | 条件判断 | `{{?showHeader}}` |

### 表格循环示例

**模板文件:**
| 名称 | 价格 |
|------|------|
| {{goods}} | |
| [name] | [price] |

**请求数据:**
```json
{
  "goods": [
    {"name": "商品A", "price": 100},
    {"name": "商品B", "price": 200}
  ]
}
```

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.1 | Web 框架 |
| poi-tl | 1.12.1 | Word 模板引擎 |
| EasyExcel | 4.0.1 | Excel 处理 |
| SpringDoc | 2.3.0 | API 文档 |

## ❓ 常见问题

### 多架构构建失败

```bash
# 安装 QEMU 模拟器
docker run --privileged --rm tonistiigi/binfmt --install all
```

### 模板文件找不到

确保模板文件放置在 `TEMPLATE_PATH` 配置的目录下。

### Docker 无法访问模板

检查 volume 挂载路径是否正确：
```bash
docker run -v /绝对路径/templates:/app/templates ...
```

## 📄 开源协议

[MIT License](./LICENSE)

## 🔗 相关链接

- [poi-tl 官方文档](http://deepoove.com/poi-tl/)
- [Spring Boot 官网](https://spring.io/projects/spring-boot)
- [Go SDK 文档](./sdk/go/README.md)
