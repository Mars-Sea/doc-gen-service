# GLOSSARY

本词表记录本项目的领域概念与模块命名，供架构评审与后续开发对齐用词。

## 模板（Template）

用户上传到模板目录的 `.docx` / `.xlsx` 文件，是文档生成的输入。模板名即文件名，含扩展名，如
`test-template.docx`。

## 模板名（TemplateName）

模板的名字，不可变值对象。只由静态工厂 `TemplateName.parse(raw, kind)` 产出，「合法」由此在类型上得到
保证：未通过校验的名字无法表达，读侧与写侧的接口都只接收本类型。

命名规则采用**字符白名单**（Unicode 字母、数字、下划线、点号、连字符），而非黑名单。白名单的正确形态是
「只能是什么」，黑名单的维护方式则是「想到下一个坏字符再补一次」——历史上 `..`、`/`、`\` 被逐一补上，
却漏掉了 Windows 盘符冒号 `C:evil.docx`。

中文模板名合法（`中文模板.docx`）。空格、控制字符、路径分隔符一律拒绝。

## 文档类型（TemplateKind）

模板对应的文档类型，决定哪些扩展名合法：

| 类型 | 合法扩展名 | 依据 |
|------|-----------|------|
| `WORD` | `.docx` | poi-tl 基于 OOXML；`.doc` 会在渲染阶段抛 `OLE2NotOfficeXmlFileException` |
| `EXCEL` | `.xlsx` | 上传入口与列表入口均只接收 `.xlsx` |

`.xls` 不放行：EasyExcel 4.0.1 技术上可走 `HSSFWorkbook` 路径，但上传接口硬编码只收 `.docx`/`.xlsx`、
列表也只列这两种，放行只会给出一个永远拿不到文件的死路。

## 模板读侧（TemplateRepository）

模板目录的读侧接口：`locate(name)` 定位文件、`read(name)` 读出字节、`list()` 列出目录内容。渲染模块
（Word / Excel）与模板控制器都穿过这一个接缝取模板。

持有模板目录配置。写侧需要算落盘目标时通过 `resolveForWrite(name)` 取路径——基路径的读法只有这一份。

## 模板写侧（TemplateWriter）

模板目录的写侧接口：`upload(file)` 上传、`delete(name)` 删除。与读侧分开是因为错误语义不同：读侧找不到
文件是 `TemplateNotFoundException`，写侧收到非法输入是 `IllegalArgumentException`。

上传侧与寻址侧规则刻意不同：上传侧先做字符清洗（把路径分隔符与 Windows 专用字符替换为下划线），再交给
`TemplateName` 判定。由此保证一条不变量——**凡是能落盘的名字，之后一定能在读侧被合法解析**。

## 渲染数据（Render Data）

调用方随请求传入的 `Map`，键对应模板占位符，值为填充内容。可包含**载荷**（见下）。

## 载荷遍历器（PayloadWalker）

从渲染数据根开始递归下行，在每个可能是载荷的位置把值交给调用方判定的模块。必须覆盖三类容器：嵌套
`Map`、`Iterable`、**数组**。数组是独立的一支——Java 中数组不实现 `Iterable`，漏掉它会让 `items[0].logo`
这类载荷保持 `Map` 原样，最终被渲染成 `Map.toString()`。

Word 与 Excel 共用同一份遍历，各自只在叶子上包装成自己的渲染对象：poi-tl 要 `PictureRenderData`，
EasyExcel 要 `WriteCellData`。

## 载荷转换结果（ConvertedImage）

载荷转换的统一出口：`byte[]` 字节 + `format` 格式 + `width`/`height` 展示尺寸。图片与二维码的字节来源
不同（URL 下载 / ZXing 本地生成），但这四项事实相同，各文档模块再按自己的渲染库包装。

## 生成的文档响应（GeneratedDocumentResponse）

把「文档字节 → 可下载的 HTTP 响应」收敛为一处：调用方交出字节与用户想要的文件名，扩展名 → MediaType
→ 文件名清洗 → RFC 5987 编码 → Content-Disposition 全部由它决定。收敛前 DocController 四个端点各自
内联了同一套逻辑，只差默认名与扩展名。

## 图片载荷（Image Payload）

渲染数据中的结构化对象，`type` 为 `image`：

```json
{ "type": "image", "url": "https://example.com/logo.png", "width": 300, "height": 200 }
```

## 二维码载荷（QR Code Payload）

渲染数据中的结构化对象，`type` 为 `qrcode`：

```json
{ "type": "qrcode", "content": "https://example.com/order/123", "width": 150, "height": 150 }
```

Word 与 Excel 均支持。二维码由 ZXing 在服务端本地生成，没有 URL，因此不能走图片那条 HTTP 下载链路，
需经 `ConvertedImage` 这一字节级出口。

### 列表数据（List Data）

Excel 模板填充中的行循环数据，模板用 `{.field}` 语法（带前导点）引用列表项字段：

```json
{ "items": [ { "no": 1, "name": "商品A" }, { "no": 2, "name": "商品B" } ] }
```

列表中的载荷与单值载荷走 EasyExcel 内部同一条 `fillImage` 链路，因此同样必须提前转换为
`WriteCellData`。
