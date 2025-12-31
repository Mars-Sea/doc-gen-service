package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 文档生成请求体 DTO
 * <p>
 * 接收来自调用方（如 Go 服务）的 JSON 数据
 */
@Data
@Schema(description = "文档生成请求参数")
public class DocGenRequest {
    /**
     * 模板文件名称 (需包含扩展名, 如 template.docx)
     * 该文件必须存在于配置的 templatePath 目录下
     */
    @Schema(description = "模板文件名", example = "test-template.docx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 填充到模板中的动态数据
     * Key 对应模板中的标签，Value 为具体的值或对象
     */
    @Schema(description = "模板渲染数据(键值对)", example = "{\"title\": \"My Title\", \"date\": \"2023-01-01\"}")
    private Map<String, Object> data;

    /**
     * 自定义输出文件名 (可选，不含扩展名)
     * 如不指定，则默认使用 "generated"
     */
    @Schema(description = "自定义输出文件名(不含扩展名)", example = "report_2024")
    private String fileName;
}
