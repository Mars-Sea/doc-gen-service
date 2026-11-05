package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Excel 模板填充请求体 DTO
 *
 * <p>定义客户端请求基于模板生成 Excel 文档时需要传递的参数结构。 支持单值变量替换和列表数据循环填充。
 *
 * <h3>模板语法:</h3>
 *
 * <ul>
 *   <li>{variable} - 单值替换，如 {title}
 *   <li>{.field} - 列表行循环，如 {.name}, {.price}
 * </ul>
 *
 * <h3>请求示例:</h3>
 *
 * <pre>
 * {
 *   "templateName": "report-template.xlsx",
 *   "data": {
 *     "title": "销售报告",
 *     "date": "2025-01-06"
 *   },
 *   "listData": {
 *     "items": [
 *       {"no": 1, "name": "商品A", "price": 100},
 *       {"no": 2, "name": "商品B", "price": 200}
 *     ]
 *   },
 *   "fileName": "sales_report"
 * }
 * </pre>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Data
@Schema(description = "Excel 模板填充请求参数")
public class ExcelFillRequest {

    /**
     * 模板文件名称
     *
     * <p>需包含扩展名（如 template.xlsx）。 该文件必须存在于配置的 templatePath 目录下。
     */
    @NotBlank(message = "模板文件名不能为空")
    @Schema(
            description = "模板文件名（需包含扩展名）",
            example = "report-template.xlsx",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 单值变量数据
     *
     * <p>Key 对应模板中的占位符标签（如 {title}）， Value 为具体的填充值。
     */
    @Schema(
            description = "单值变量数据（对应模板中的 {variable} 语法）",
            example = "{\"title\": \"销售报告\", \"date\": \"2025-01-06\"}")
    private Map<String, Object> data;

    /**
     * 列表数据（用于循环填充）
     *
     * <p>Key 为列表名称，Value 为数据列表。 模板中使用 {.field} 语法引用列表项的字段。
     */
    @Schema(
            description = "列表数据（对应模板中的 {.field} 语法，用于行循环）",
            example = "{\"items\": [{\"no\": 1, \"name\": \"商品A\", \"price\": 100}]}")
    private Map<String, List<Map<String, Object>>> listData;

    /**
     * 自定义输出文件名（可选）
     *
     * <p>不含扩展名，系统会自动添加 .xlsx 后缀。 如不指定，则默认使用 "filled"。
     */
    @Schema(description = "自定义输出文件名（不含扩展名）", example = "report_2024")
    private String fileName;

    /**
     * 指定填充的工作表名称（可选）
     *
     * <p>当模板包含多个 sheet 时，可通过名称指定要填充的 sheet。 若不指定则默认填充第一个 sheet。 与 sheetNo 同时指定时，sheetNo 优先。
     */
    @Schema(description = "指定填充的工作表名称（可选，与 sheetNo 同时指定时 sheetNo 优先）", example = "数据表")
    private String sheetName;

    /**
     * 指定填充的工作表索引（可选，从 0 开始）
     *
     * <p>当模板包含多个 sheet 时，可通过索引指定要填充的 sheet。 若不指定则默认填充第一个 sheet（索引 0）。
     */
    @Schema(description = "指定填充的工作表索引（从 0 开始，可选）", example = "0")
    private Integer sheetNo;

    /**
     * 列表填充时是否强制新建行（可选，默认 true）
     *
     * <p><b>默认 true 的理由：</b>xlsx 解包后 {@code xl/worksheets/sheet1.xml} 超过 100 MB 时， Excel
     * 打开会提示「部分内容有问题，是否恢复」，恢复过程会丢掉读不下去的行。true 走共享字符串表，两个 XML 都在上限内；false 全部内联，同样数据量下 sheet1.xml
     * 更大（实测 67,000 行：70.3 MB vs 91.3 MB）。
     *
     * <p><b>代价（实测 67,000 行 × 25 列）：</b>true 耗时 26.6 秒；false 耗时 4.1 秒。 选快的那条，代价是可能拿到一个打不开的文件。
     *
     * <p><b>何时可传 false：</b>数据量小（约 3 万行以内，视单元格文本长度而定），且模板里列表占位行<b>下方没有</b> 静态内容——false
     * 是原位扩展，下方内容不会被下推，可能被覆盖。
     */
    @Schema(
            description =
                    "列表填充是否强制新建行（默认 true）。true 走共享字符串表、产出文件可正常打开；"
                            + "false 更快但 sheet1.xml 更大，大数据量下可能超出 Excel 的 100MB 单记录上限"
                            + "导致文件需要修复。数据量小且列表位于模板末尾时可传 false",
            example = "true",
            defaultValue = "true")
    private Boolean forceNewRow;
}
