package io.github.marssea.docgen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Excel 文档生成请求体 DTO
 * <p>
 * 定义客户端请求生成 Excel 文档时需要传递的参数结构。
 * 支持动态表头和二维数据填充。
 *
 * <h3>请求示例:</h3>
 * 
 * <pre>
 * {
 *   "sheetName": "员工列表",
 *   "headers": ["姓名", "年龄", "城市"],
 *   "data": [
 *     ["张三", 25, "北京"],
 *     ["李四", 30, "上海"]
 *   ],
 *   "fileName": "employees"
 * }
 * </pre>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Data
@Schema(description = "Excel 文档生成请求参数")
public class ExcelGenRequest {

    /**
     * 工作表名称（可选）
     * <p>
     * 如不指定，默认使用 "Sheet1"
     */
    @Schema(description = "工作表名称", example = "Sheet1", defaultValue = "Sheet1")
    private String sheetName;

    /**
     * 表头列名列表
     * <p>
     * 定义 Excel 第一行的列标题
     */
    @NotEmpty(message = "表头列表不能为空")
    @Schema(description = "表头列名列表", example = "[\"姓名\", \"年龄\", \"城市\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> headers;

    /**
     * 数据行列表
     * <p>
     * 二维列表，每个内层列表代表一行数据，
     * 列顺序需与 headers 对应
     */
    @NotEmpty(message = "数据列表不能为空")
    @Schema(description = "数据行（二维数组，每行对应 headers 顺序）", example = "[[\"张三\", 25, \"北京\"], [\"李四\", 30, \"上海\"]]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<List<Object>> data;

    /**
     * 自定义输出文件名（可选）
     * <p>
     * 不含扩展名，系统会自动添加 .xlsx 后缀。
     * 如不指定，则默认使用 "generated"。
     */
    @Schema(description = "自定义输出文件名（不含扩展名）", example = "report_2024")
    private String fileName;
}
