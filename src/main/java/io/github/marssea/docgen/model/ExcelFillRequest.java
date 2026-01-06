package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Excel 模板填充请求体 DTO
 * <p>
 * 定义客户端请求基于模板生成 Excel 文档时需要传递的参数结构。
 * 支持单值变量替换和列表数据循环填充。
 *
 * <h3>模板语法:</h3>
 * <ul>
 * <li>{variable} - 单值替换，如 {title}</li>
 * <li>{.field} - 列表行循环，如 {.name}, {.price}</li>
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
     * <p>
     * 需包含扩展名（如 template.xlsx）。
     * 该文件必须存在于配置的 templatePath 目录下。
     */
    @NotBlank(message = "模板文件名不能为空")
    @Schema(description = "模板文件名（需包含扩展名）", example = "report-template.xlsx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 单值变量数据
     * <p>
     * Key 对应模板中的占位符标签（如 {title}），
     * Value 为具体的填充值。
     */
    @Schema(description = "单值变量数据（对应模板中的 {variable} 语法）", example = "{\"title\": \"销售报告\", \"date\": \"2025-01-06\"}")
    private Map<String, Object> data;

    /**
     * 列表数据（用于循环填充）
     * <p>
     * Key 为列表名称，Value 为数据列表。
     * 模板中使用 {.field} 语法引用列表项的字段。
     */
    @Schema(description = "列表数据（对应模板中的 {.field} 语法，用于行循环）", example = "{\"items\": [{\"no\": 1, \"name\": \"商品A\", \"price\": 100}]}")
    private Map<String, List<Map<String, Object>>> listData;

    /**
     * 自定义输出文件名（可选）
     * <p>
     * 不含扩展名，系统会自动添加 .xlsx 后缀。
     * 如不指定，则默认使用 "filled"。
     */
    @Schema(description = "自定义输出文件名（不含扩展名）", example = "report_2024")
    private String fileName;
}
