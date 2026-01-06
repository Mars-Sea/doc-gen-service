package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量 Word 文档生成请求体 DTO
 * <p>
 * 定义客户端请求批量生成 Word 文档时需要传递的参数结构。
 * 使用同一模板渲染多条数据，每条数据生成一页，合并为单个文档。
 *
 * <h3>请求示例:</h3>
 * 
 * <pre>
 * {
 *   "templateName": "certificate.docx",
 *   "dataList": [
 *     {"name": "张三", "award": "一等奖"},
 *     {"name": "李四", "award": "二等奖"},
 *     {"name": "王五", "award": "三等奖"}
 *   ],
 *   "fileName": "批量证书"
 * }
 * </pre>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Data
@Schema(description = "批量 Word 文档生成请求参数")
public class WordBatchRequest {

    /**
     * 模板文件名称
     * <p>
     * 需包含扩展名（如 certificate.docx）。
     * 该文件必须存在于配置的 templatePath 目录下。
     */
    @NotBlank(message = "模板文件名不能为空")
    @Schema(description = "模板文件名（需包含扩展名）", example = "certificate.docx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 数据列表
     * <p>
     * 每个 Map 对应一页的渲染数据，Key 对应模板中的占位符。
     * 列表中有多少条数据，生成的文档就有多少页。
     */
    @NotEmpty(message = "数据列表不能为空")
    @Schema(description = "数据列表，每条数据生成一页", example = "[{\"name\": \"张三\", \"award\": \"一等奖\"}, {\"name\": \"李四\", \"award\": \"二等奖\"}]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Map<String, Object>> dataList;

    /**
     * 自定义输出文件名（可选）
     * <p>
     * 不含扩展名，系统会自动添加 .docx 后缀。
     * 如不指定，则默认使用 "batch_generated"。
     */
    @Schema(description = "自定义输出文件名（不含扩展名）", example = "batch_certificates")
    private String fileName;
}
