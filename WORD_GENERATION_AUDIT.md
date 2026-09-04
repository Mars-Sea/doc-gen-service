# Word 文档生成模块 — 代码审计报告

> 审计对象：`doc-gen-service`（Spring Boot 3.2.1 / poi-tl 1.12.1）
> 审计范围：Word 文档生成的全部功能模块与调用链路
> 审计日期：2026-09-02
> 验证方式：**静态分析 + 可运行复现程序实测**（所有标注「已实测」的结论均由复现程序验证，非推测）

---

## 一、模块边界与调用链路

### 1.1 涉及文件

| 模块 | 文件 | 职责 |
|---|---|---|
| 接口层 | `controller/DocController.java` | `/api/v1/doc/word`、`/api/v1/doc/word/batch` |
| 服务层 | `service/WordService.java` | **核心**：模板加载、循环表识别、渲染配置、批量合并 |
| 图片处理 | `util/ImagePayloadConverter.java` | 图片载荷校验、下载、转 `PictureRenderData` |
| 安全校验 | `util/TemplateValidationUtil.java` | 模板名路径遍历防护、扩展名校验 |
| 异常 | `exception/*.java`、`config/GlobalExceptionHandler.java` | 异常到 HTTP 状态码映射 |
| 模型 | `model/WordGenRequest.java`、`model/WordBatchRequest.java` | 请求 DTO |
| 配置 | `config/DocGenProperties.java` | 模板根目录 |
| 辅助 | `config/TemplateInitializer.java`（仅 dev profile） | 生成演示模板 |

### 1.2 调用链路

**单文档生成 `POST /api/v1/doc/word`**

```
DocController.generateWord (L80)
└─ WordService.generateWord (L87)
   ├─ TemplateValidationUtil.validateWordTemplateExtension (L89)  ← 扩展名/路径遍历校验
   ├─ Paths.get(templatePath, templateName) + exists() (L92-100)   ← 模板定位
   ├─ preprocessImagePayloads (L105 → L305)                        ← 【缺陷 7】仅顶层扫描
   │  └─ ImagePayloadConverter.isImagePayload / convert
   │     └─ downloadImage (L248)                                   ← 【缺陷 2】SSRF
   │     └─ resolveFormat (L302)                                   ← 【缺陷 8】信任 Content-Type
   ├─ detectLoopTableFields (L108 → L337)                          ← 【缺陷 3/5】正则误判 + 异常捕获失效
   │  └─ collectTables / rowText / leadingSegment
   ├─ ensureLoopData (L109 → L386)                                 ← 缺失字段补空列表
   ├─ buildRenderConfig (L112 → L250)                              ← 【缺陷 1】无条件绑定循环策略
   └─ XWPFTemplate.compile().render().write() (L115-123)
      └─ LoopRowTableRenderPolicy.render()                         ← 【缺陷 6】残留空行
```

**批量生成 `POST /api/v1/doc/word/batch`**

```
DocController.batchGenerateWord (L154)
└─ WordService.generateBatch (L140)
   ├─ detectLoopTableFields (L168)              ← 仅解析一次（合理）
   ├─ renderTemplateInstance(dataList[0]) (L171)
   └─ for i = 1..n-1 (L175-186)
      ├─ mainDoc.createParagraph().addBreak(PAGE)  ← 手动分页符
      ├─ renderTemplateInstance(dataList[i])       ← 每个实例独立渲染
      └─ mainDoc = mainDoc.merge(nextDoc)          ← 【缺陷 9】O(n²) 全量重建
```

### 1.3 关键机制核实（poi-tl 源码级）

为避免误判，以下机制均核对了 poi-tl 1.12.1 源码：

| 机制 | 核实结论 |
|---|---|
| `LoopRowTableRenderPolicy` 循环行定位 | `getTemplateRowIndex()` 返回「标签所在行 +1」，即 `{{items}}` 在触发行、`[field]` 在其**下一行**。项目 `TemplateInitializer` 的模板结构**符合**此约定 ✓ |
| 自定义策略作用范围 | `RunTemplate.findPolicy()` = `config.getCustomPolicy(tagName)` —— **按标签名匹配，与标签是否在表格内无关**。这是缺陷 1 的根源 |
| `merge()` 资源语义 | `XmlXWPFDocumentMerge.merge()` 末尾 `return source.generate(true)`，会关闭旧主文档与待合并文档并返回全新文档。项目注释 ✓ 正确 |
| `merge()` 开销 | `generate(true)` = 整篇文档 `write()` 到内存 + `close()` + 重新解析。缺陷 9 的根源 |

---

## 二、问题总览

| # | 问题 | 类型 | 严重程度 | 状态 |
|---|---|---|---|---|
| 1 | 集合类型数据被无条件绑定表格循环策略，导致非表格场景 500 | 逻辑缺陷 | **高** | 已实测 |
| 2 | 图片下载无主机白名单，存在 SSRF | 安全漏洞 | **高** | 已实测 |
| 3 | 循环字段误判，合法表格行被静默删除 | 逻辑缺陷 | **高** | 已实测 |
| 4 | `.doc` 扩展名放行，报 500 而非 400 | 边界处理 | 中 | 已实测 |
| 5 | `detectLoopTableFields` 的 `catch (IOException)` 形同虚设 | 异常处理 | 中 | 已实测 |
| 6 | 循环数据缺失时残留空行，与注释承诺不符 | 内容准确性 | 中 | 已实测 |
| 7 | 嵌套图片载荷不生效（仅顶层扫描） | 功能缺陷 | 中 | 已实测 |
| 8 | 图片格式依据响应头而非魔术字节 | 数据格式 | 中 | 已实测 |
| 9 | 批量生成 O(n²) 时间与内存，无条数上限 | 性能/稳定性 | 中 | 已实测 |
| 10 | `merge` 失败时 `nextDoc` 资源泄漏 | 资源泄漏 | 中低 | 静态 |
| 11 | 每实例间可能产生多余空段落 | 排版 | 低 | 静态 |
| 12 | 页眉/页脚/文本框内表格未扫描 | 覆盖遗漏 | 低 | 静态 |
| 13 | `toLowerCase()` 未指定 Locale | 国际化 | 低 | 静态 |
| 14 | `exists()` 未校验 `isFile()` | 边界处理 | 低 | 静态 |
| 15 | 每次请求重复解析模板，无缓存 | 性能 | 低 | 静态 |
| 16 | `new URL()` 已废弃（编译告警） | 代码质量 | 低 | 已实测 |
| 17 | 文件名清理不彻底 | 边界处理 | 低 | 静态 |
| 18 | `templatePath` 为 null 时 NPE | 边界处理 | 低 | 静态 |

---

## 三、问题详情

### 【缺陷 1】集合类型数据被无条件绑定表格循环策略 → 非表格场景 500 ★高

**位置**：`WordService.buildRenderConfig()` L264-271

```java
data.forEach((key, value) -> {
    if (value instanceof Iterable) {
        boundFields.add(key);      // ← 只要值是集合，就绑定 LoopRowTableRenderPolicy
    }
});
```

**根本原因**：poi-tl 的 `RunTemplate.findPolicy()` 按**标签名**查找自定义策略，不校验标签是否位于表格内。而 `LoopRowTableRenderPolicy.render()` L81-84 强制要求标签在表格中：

```java
if (!TableTools.isInsideTable(run)) {
    throw new IllegalStateException("The template tag " + source + " must be inside a table");
}
```

**实测（T1）**：模板为普通段落「标题: {{title}} / 标签: {{tags}}」，数据 `tags = ["A","B","C"]`

```
!! 抛出异常: com.deepoove.poi.exception.RenderException
!! message : HackLoopTable for {{tags}}error: The template tag {{tags}} must be inside a table
-> HTTP 映射: 500 (INTERNAL_ERROR - 未捕获兜底)
```

**实测（T6）**：`{{items}}` 位于表格**最后一行** + List 数据（即模板缺少 `[field]` 数据行）

```
!! 抛出异常: com.deepoove.poi.exception.RenderException
!! message : HackLoopTable for {{items}}error: Cannot invoke "XWPFTableRow.getCtRow()" because "templateRow" is null
-> HTTP 映射: 500
```

**触发场景**：
- 模板中「标签: {{tags}}」这类**行内展示列表**的段落（非常常见）
- 模板中 `{{items}}` 位于表格最后一行
- 只要数据中该 key 是 List/Set，无论模板结构如何都会触发

**严重程度**：**高**
- 这是**本服务引入的回归**：若不绑定策略，poi-tl 默认会把 List 渲染为 `[A, B, C]`，完全可用
- 客户端收到 500 且错误信息为底层 poi-tl 内部异常，无法定位
- 无异常兜底，`RenderException extends RuntimeException` → `GlobalExceptionHandler.handleException` → 500

**修复建议**：

```java
// 只对「确实在表格循环结构中」的字段绑定策略
Set<String> boundFields = new HashSet<>(loopFields);   // 仅用模板实际检测到的循环字段
// 删除基于 data 值类型的无条件绑定，或增加前置校验：
//   - 该字段必须出现在模板检测出的 loopFields 中
```

---

### 【缺陷 2】图片下载无主机白名单 → SSRF ★高（安全）

**位置**：`ImagePayloadConverter.downloadImage()` L248-299

**问题**：仅校验 URL **协议**（`SUPPORTED_PROTOCOLS = {http, https}`，L64），**完全不校验目标主机**。协议白名单本身实现正确（实测 `file://`、`jar://`、`ftp://` 均被拦截 ✓），但 `http://127.0.0.1`、`http://10.0.0.1`、`http://169.254.169.254` 全部放行。

**实测（场景 A）**：本地启动模拟内网服务 `127.0.0.1:18099`，请求 `http://127.0.0.1:18099/fake.png`

```
请求成功，未被任何主机白名单拦截
下载字节数 = 45
实际内容   = <html><body>Internal Admin Page</body></html>
推断格式   = png
-> 内网/本机资源可被任意读取，且内容未做校验
```

**触发场景**：
- 攻击者构造 `{"type":"image","url":"http://169.254.169.254/latest/meta-data/..."}` 读取云主机元数据/临时凭证
- 扫描内网 `10.0.0.0/8`、`192.168.0.0/16` 的服务指纹
- 访问内网 Redis/ES/管理后台等无认证服务
- `setInstanceFollowRedirects(true)`（L257）会跟随 30x 跳转，进一步绕过粗粒度 URL 过滤

**严重程度**：**高**
- 服务若部署在云上或内网，可被用作内网探测跳板
- 响应内容虽不直接回显，但可通过「下载成功/失败」「耗时差异」进行盲注式内网探测
- 且响应体被当作图片嵌入文档，可用于内容外带

**修复建议**：
- 建立目标主机**白名单**（配置项，默认仅允许业务域名）
- 至少禁止 `127.0.0.0/8`、`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`169.254.0.0/16`、`::1` 等
- 禁用自动重定向，或每跳重新校验目标地址
- 校验响应 `Content-Type` 与文件魔术字节（见缺陷 8）

---

### 【缺陷 3】循环字段误判 → 合法表格行被静默删除 ★高

**位置**：`WordService` L64 正则 + `detectLoopTableFields()` L346-355 + `ensureLoopData()` L386

```java
private static final Pattern LOOP_ROW_TAG = Pattern.compile("\\[[^\\[\\]{}]+\\]");
```

该正则匹配**任意**方括号包裹的内容，包括完全非循环语义的 `[选填]`、`[备注]`、`[1]`、`[是/否]`。

结合判定逻辑（L346-355）：**若第 i 行含 `{{tag}}` 且第 i+1 行含任意 `[...]`，则 `tag` 被认定为循环字段**。随后 `ensureLoopData()` 会在数据缺失时补入空列表（L390-394），导致 poi-tl 执行 `table.removeRow()` 删除第 i+1 行。

**实测（T4）**：模板 2 行

| 行 | 内容 |
|---|---|
| 0 | `客户 {{customerName}}` ǀ `等级 A` |
| 1 | `备注[选填]` ǀ `如有疑问请联系` |

数据 **不含** `customerName`（缺失）：

```
输出表格行数 = 1 (模板为 2 行)
    [客户 ][等级 A]
-> 整个「备注[选填] / 如有疑问请联系」行被删除
```

**触发条件**（三者同时满足即触发）：
1. 表格某行含 `{{tag}}`
2. 其**下一行**含任意 `[...]` 文本（哪怕只是 `[选填]`、`[1]`）
3. 该 `tag` 在请求数据中**缺失或为 null**

**严重程度**：**高**
- **静默内容丢失**：不报错、不告警，用户拿到的是少了一行的文档
- 合同、报表、证书类模板中「备注」「说明」行极易命中
- 若该 `tag` 有标量数据（String/Number），`buildRenderConfig` L277-281 的 `scalar` 判断会规避；**但字段缺失时防护失效**

**修复建议**：
- 收紧 `LOOP_ROW_TAG`，要求方括号内为合法标识符：`\[\s*[\p{L}_][\p{L}\p{N}_]*\s*\]`
- 增加**双重校验**：触发行的 `{{tag}}` 与数据行的 `[field]` 之间建立字段关联（例如循环行至少一个 `[field]` 能通过 `data.get(tag)` 的样例元素解析）
- 更稳妥：不在数据缺失时自动补空列表，改为保留原始标签由调用方感知

---

### 【缺陷 4】`.doc` 扩展名放行 → 500 而非 400

**位置**：`TemplateValidationUtil.validateWordTemplateExtension()` L58

```java
if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".doc")) {
    throw new IllegalArgumentException("Word 模板必须是 .docx 或 .doc 格式");
}
```

方法**自身注释已承认**「poi-tl 仅支持 .docx 格式，.doc 文件可能会导致运行时错误」，却仍然放行。

**实测（T2）**：

```
!! 扩展名校验阶段是否放行? 是 -> 进入渲染阶段
!! 异常: org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
!! message: The supplied data appears to be in the OLE2 Format...
-> HTTP 映射: 500 (INTERNAL_ERROR - 未捕获兜底)
```

**严重程度**：**中**
- 客户端错误被上报为服务端错误，监控告警失真、排查困难
- 应当返回 400 并明确提示「仅支持 .docx」

**修复建议**：移除 `.doc` 放行，仅保留 `.docx`；或保留但立刻给出明确 400 提示。

---

### 【缺陷 5】`detectLoopTableFields` 的 `catch (IOException)` 形同虚设

**位置**：`WordService.detectLoopTableFields()` L357-359

```java
} catch (IOException e) {
    log.warn("Failed to inspect template for table loop fields: {}", e.getMessage());
}
```

**已通过 `javap` 核实异常层级**：

```
public class OLE2NotOfficeXmlFileException extends NotOfficeXmlFileException
public final class InvalidFormatException extends OpenXML4JException
```

`OpenXML4JException` **不是 `IOException` 的子类**（为 RuntimeException）。而 POI 打开模板失败时抛出的正是这一类异常。

**后果**：设计意图是「模板解析失败时降级为空集合，不影响主流程」，但**最常见的失败模式（模板损坏、非 OOXML 文件）根本不会被捕获**，异常直接穿透到 `generateWord`，最终由 `GlobalExceptionHandler.handleException` 兜底为 500。

**实测佐证**：缺陷 4 中 `.doc` 场景的异常正是从此处直接抛出，未被降级。

**严重程度**：**中** — 容错代码失效，且因只记录 `getMessage()` 无堆栈，即使命中也难以诊断。

**修复建议**：改为捕获 `Exception`（或显式捕获 `OpenXML4JException` + `IOException`），并打印完整堆栈。

---

### 【缺陷 6】循环数据缺失时残留空行（与注释承诺不符）

**位置**：`WordService` L107、L381 注释 vs poi-tl 实际行为

代码注释声称：
> 「缺失/为空时自动按空列表处理（用于**删除循环行及其占位标签**）」
> 「空列表在 `LoopRowTableRenderPolicy` 中会触发**删除循环数据行，从而移除 `[field]` 等占位标签**」

**实测（T3）**：模板 3 行（表头 / `{{items}}` 触发行 / `[name][price]` 数据行），数据**不含** `items`

```
输出表格行数 = 2 (模板为 3 行)
    [名称][价格]
    [][]            ← 残留空行
```

**原因**：poi-tl `LoopRowTableRenderPolicy` L87 仅 `run.setText("", 0)` 清空触发行文本，L139 `table.removeRow(templateRowIndex)` 只删除 `[field]` 数据行。**含 `{{items}}` 的触发行会被保留为空行**。

**严重程度**：**中**
- 输出文档残留空白行，排版异常
- 与代码注释及预期行为不符，后续维护者易被误导

**修复建议**：扩展 `LoopRowTableRenderPolicy`，重写 `afterloop()` 回调，在数据为空时同时移除触发行：

```java
new LoopRowTableRenderPolicy() {
    @Override protected void afterloop(XWPFTable table, Object data) {
        if (data == null || (data instanceof Iterable<?> it && !it.iterator().hasNext())) {
            // 同时移除 {{tag}} 触发行
        }
    }
}
```

---

### 【缺陷 7】嵌套图片载荷不生效（仅顶层扫描）

**位置**：`WordService.preprocessImagePayloads()` L305-325

只遍历 `data` 的**顶层** value，不递归进入 List/Map 内部。

**实测（T5）**：循环数据项内含图片载荷

```json
{"items": [{"name": "苹果", "logo": {"type":"image","url":"https://example.com/a.png"}}]}
```

```
输出行: [[商品][图片], [][], [苹果][{type=image, url=https://example.com/a.png}]]
                                          ↑ 图片载荷原样打印为 Map.toString()
```

**触发场景**：图片位于表格循环行内（`[logo]`）、或任意嵌套结构中。

**严重程度**：**中**
- DTO 文档（`WordGenRequest` L58、L68）宣称支持图片载荷，但**表格行内图片完全不可用**
- 未转换时不会报错，只是渲染成难看的 Map 字符串，问题隐蔽

**修复建议**：递归遍历 `data`（至少进入 `List<Map>` 与嵌套 `Map`），对所有层级的图片载荷统一转换。

---

### 【缺陷 8】图片格式依据响应头而非魔术字节

**位置**：`ImagePayloadConverter.resolveFormat()` L302-324 + `inferFormatFromContentType()` L340-352

格式判定优先级：`显式 format` → `URL 后缀` → `响应 Content-Type`。**始终不校验真实字节**。

**实测（场景 B）**：响应头 `Content-Type: image/png`，实际返回 JPEG 魔术字节

```
通过校验，推断格式 = png
真实文件头 = FF D8 FF E0 (FF D8 FF = JPEG)
-> 格式取自响应头而非文件魔术字节，二者不一致时嵌入错误类型图片
```

**实测（场景 A 佐证）**：返回 45 字节 HTML 文本 + `Content-Type: image/png`，**同样通过校验**并被当作 PNG 嵌入。

**严重程度**：**中**
- 源站 Content-Type 配置错误或返回错误页（如 200 + HTML 错误页）时，会生成**图片损坏的 docx**
- 文件能正常下载，用户打开 Word 后才发现图片无法显示

**修复建议**：下载后读取前若干字节做魔术字节嗅探（`89 50 4E 47` = PNG，`FF D8 FF` = JPEG），与推断格式交叉校验，不一致则以实际字节为准或直接拒绝。

---

### 【缺陷 9】批量生成 O(n²) 时间与内存，无条数上限

**位置**：`WordService.generateBatch()` L175-186

每次 `mainDoc.merge(nextDoc)` 内部调用 `generate(true)`：将**已累积的整篇文档**序列化为字节 → 关闭 → 重新解析。第 i 轮处理的数据量为 i×模板大小。

**实测（含 JIT 预热，`-Xmx1g`）**：

| 条数 | 耗时(ms) | 输出(KB) | 峰值堆(MB) |
|---|---|---|---|
| 50 | 2,118 | 3.3 | 22.3 |
| 100 | 2,979 | 3.8 | 83.6 |
| 200 | 6,050 | 4.8 | 76.9 |
| 400 | 15,858 | 6.8 | 16.8 |
| 800 | 51,073 | 10.8 | 261.3 |

耗时比值：100→200 为 2.0×，200→400 为 2.6×，400→800 为 3.2× —— **比值持续增大，呈超线性（趋近 O(n²)）**。

**触发场景**：批量导出几百到几千条记录（证书、对账单、成绩单等典型批量场景）。

**严重程度**：**中**
- 800 条已需 51 秒，远超常见网关超时（30s）
- 峰值堆随 n 增长，容器默认堆（通常 512MB~1GB）下数千条即 OOM
- 无 `dataList.size()` 上限校验，单次请求即可打垮实例

**修复建议**：
- 改用 poi-tl 的批量合并重载 `merge(List<NiceXWPFDocument>, XWPFRun)`，**一次性**合并所有实例（内部只做一次 `generate`），将复杂度降至 O(n)
- 或分批合并（如每 50 个实例合并一次）
- 增加 `dataList` 条数上限校验（如 1000），超出返回 400
- 大批量场景改为异步任务 + 结果下载

---

### 其余静态分析发现（低危）

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| 10 | `generateBatch` L181-185 | `XmlXWPFDocumentMerge.createMergeableStrings` 在 `createMergeableString` 抛异常时不执行 `next.close()`；`finally` 仅关闭 `mainDoc` → `nextDoc` 的 OPCPackage 句柄泄漏 | 在循环中收集待关闭文档，`finally` 统一关闭 |
| 11 | `generateBatch` L177-178 | 手动创建分页符段落，而 `merge()` 内部还会 `createParagraph().createRun()` 建锚点段落，实例间可能多出空段落 | 改用 `merge` 后处理，或复用 `merge(List, run)` 重载 |
| 12 | `detectLoopTableFields` L343 | 仅遍历 `document.getTables()`，**不扫描页眉、页脚、文本框、正文内容控件（SDT）**中的表格 → 这些位置的循环标签缺失数据时不会被清理 | 扩展至 `getHeaderList()`/`getFooterList()` 及 SDT 递归 |
| 13 | `TemplateValidationUtil` L57/73 | `toLowerCase()` 未指定 Locale，土耳其语环境下 `I`→`ı` 可能导致匹配失败；而 `ImagePayloadConverter` 已正确使用 `Locale.ROOT`，不一致 | 统一改为 `toLowerCase(Locale.ROOT)` |
| 14 | `WordService` L96 | `templateFile.exists()` 未校验 `isFile()`，若存在名为 `x.docx` 的**目录**会通过校验后 500 | 改为 `!isFile()` 时抛 `TemplateNotFoundException` |
| 15 | `generateWord` L108 | 每次请求都全量解析一遍 docx 做循环字段识别；模板是静态资源 | 按「文件绝对路径 + lastModified + size」缓存检测结果 |
| 16 | `ImagePayloadConverter` L251 | `new URL(url)` 已废弃，编译时 javac 产生 deprecation 告警（Java 20+） | 改为 `URI.create(url).toURL()` |
| 17 | `DocController` L99/L174 | 文件名仅替换 `\/:*?"<>|`，未处理 Windows 保留名（CON/PRN/NUL）、长度上限、首尾空格。*注：因后续 `URLEncoder.encode`，换行注入风险已被中和 ✓* | 补充保留名与长度校验 |
| 18 | `WordService` L92 | `properties.getTemplatePath()` 为 null 时 `Paths.get(null, name)` → NPE → 500 | 增加 null/空串校验与明确报错 |
| 19 | `WordService` L253 | `Configure.builder().useSpringEL()` 实际等价于 `useSpringEL(true)`（严格模式），随后被 L255 的 `setRenderDataComputeFactory` 覆盖为**非严格**模式 —— 该调用是死代码且语义误导（读代码易误判为严格模式） | 删除 `useSpringEL()`，直接保留自定义 factory |

---

## 四、修复优先级建议

**第一优先（影响可用性与安全，建议立即修复）**
1. 缺陷 1 —— 循环策略绑定条件收窄（当前会导致合法请求 500）
2. 缺陷 2 —— SSRF 主机白名单（安全风险）
3. 缺陷 3 —— 收紧 `LOOP_ROW_TAG` 正则（静默内容丢失）

**第二优先（正确性与健壮性）**
4. 缺陷 7 —— 递归处理嵌套图片载荷
5. 缺陷 8 —— 图片格式魔术字节校验
6. 缺陷 6 —— 循环行清理补全（含触发行）
7. 缺陷 4/5 —— 错误码语义与异常捕获

**第三优先（性能与工程化）**
8. 缺陷 9 —— 批量合并改为一次性 merge + 条数上限
9. 缺陷 10/15/16 —— 资源泄漏、缓存、废弃 API

---

## 五、附：复现程序说明

本次审计编写了 3 个可独立运行的验证程序（位于 `C:\tmp\wordrepro\`），可重复执行以回归验证：

| 程序 | 覆盖 |
|---|---|
| `Repro.java` | T1 非表格列表字段、T2 `.doc` 扩展名、T3 循环缺失残留空行、T4 循环误判删行、T5 嵌套图片、T6 循环标签末行、T7 批量性能 |
| `Repro2.java` | 批量生成性能精确测量（JIT 预热 + 内存采样） |
| `Repro3.java` | SSRF 可达性、格式信任、协议白名单有效性 |

编译产物基于 `src/main/java` 全量重新编译，classpath 取自本地 Maven 仓库，无需 Maven 即可运行。
