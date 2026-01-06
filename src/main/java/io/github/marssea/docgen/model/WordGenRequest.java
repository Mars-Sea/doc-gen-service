package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Word 文档生成请求体 DTO
 * <p>
 * 定义客户端请求生成 Word 文档时需要传递的参数结构。
 * 该类用于接收来自调用方（如 Go 服务）的 JSON 数据。
 *
 * <h3>请求示例:</h3>
 * 
 * <pre>
 * {
 *   "templateName": "report-template.docx",
 *   "data": {
 *     "title": "Monthly Report",
 *     "date": "2025-01-01",
 *     "items": [
 *       {"name": "Item 1", "price": 100},
 *       {"name": "Item 2", "price": 200}
 *     ]
 *   },
 *   "fileName": "monthly_report_2025"
 * }
 * </pre>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Data
@Schema(description = "Word 文档生成请求参数")
public class WordGenRequest {

    /**
     * 模板文件名称
     * <p>
     * 需包含扩展名（如 template.docx）。
     * 该文件必须存在于配置的 templatePath 目录下。
     */
    @NotBlank(message = "模板文件名不能为空")
    @Schema(description = "模板文件名（需包含扩展名）", example = "test-template.docx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 填充到模板中的动态数据
     * <p>
     * Key 对应模板中的占位符标签（如 {{title}}），
     * Value 为具体的填充值，支持以下类型：
     * <ul>
     * <li>简单类型：String, Number, Boolean</li>
     * <li>集合类型：List, Set（用于表格行循环）</li>
     * <li>嵌套对象：Map（用于复杂结构）</li>
     * </ul>
     */
    @Schema(description = "模板渲染数据（键值对）", example = "{\"title\": \"My Title\", \"date\": \"2023-01-01\"}")
    private Map<String, Object> data;

    /**
     * 自定义输出文件名（可选）
     * <p>
     * 不含扩展名，系统会自动添加 .docx 后缀。
     * 支持中文等非 ASCII 字符。
     * 如不指定，则默认使用 "generated"。
     */
    @Schema(description = "自定义输出文件名（不含扩展名，支持中文）", example = "report_2024")
    private String fileName;
}
