# 升级版本到 0.0.5 — Word 图片支持

## Context
Word 生成已支持图片载荷（URL 图片插入），需要将版本号从 0.0.4 升级到 0.0.5，并在 Go SDK 中补充图片相关的辅助类型和示例。

## 变更范围

### 1. 版本号更新 (0.0.4 → 0.0.5)
- `pom.xml` line 13
- `README.md`
- `README_CN.md`
- `sdk/go/README.md`
- `CLAUDE.md`

### 2. Go SDK 增强
- `sdk/go/docgen/client.go` — 添加 `ImagePayload` 辅助构造函数 `Image(url string) map[string]any`，返回符合服务端约定的图片载荷 map
- `sdk/go/example/main.go` — 添加图片插入示例

### 3. 不涉及
- 服务端 Java 代码（图片功能已完整实现）
- Go SDK 结构性改动（Data 已是 `map[string]any`，天然兼容）
