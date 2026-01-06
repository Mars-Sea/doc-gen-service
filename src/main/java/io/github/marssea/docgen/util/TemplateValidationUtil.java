package io.github.marssea.docgen.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 模板路径安全验证工具类
 * <p>
 * 提供模板文件名和扩展名的安全校验功能，
 * 防止路径遍历攻击和非法文件类型攻击。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
public final class TemplateValidationUtil {

    private TemplateValidationUtil() {
        // 工具类不允许实例化
    }

    /**
     * 验证模板文件名的安全性
     * <p>
     * 检查文件名是否包含路径遍历字符，防止访问模板目录外的文件。
     *
     * @param templateName 模板文件名
     * @throws IllegalArgumentException 当文件名包含非法字符时抛出
     */
    public static void validateTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("模板文件名不能为空");
        }

        // 检查路径遍历字符
        if (templateName.contains("..") || templateName.contains("/") || templateName.contains("\\")) {
            throw new IllegalArgumentException("模板文件名包含非法字符");
        }

        // 检查是否只包含文件名（无路径组件）
        Path path = Paths.get(templateName);
        if (path.getNameCount() != 1 || !path.getFileName().toString().equals(templateName)) {
            throw new IllegalArgumentException("模板文件名包含非法路径组件");
        }
    }

    /**
     * 验证 Word 模板扩展名
     * <p>
     * 支持 .docx (推荐) 和 .doc (旧版) 格式
     * 注意：poi-tl 仅支持 .docx 格式，.doc 文件可能会导致运行时错误
     *
     * @param templateName 模板文件名
     * @throws IllegalArgumentException 当扩展名不是 Word 格式时抛出
     */
    public static void validateWordTemplateExtension(String templateName) {
        validateTemplateName(templateName);
        String lowerName = templateName.toLowerCase();
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".doc")) {
            throw new IllegalArgumentException("Word 模板必须是 .docx 或 .doc 格式");
        }
    }

    /**
     * 验证 Excel 模板扩展名
     * <p>
     * 支持 .xlsx (推荐) 和 .xls (旧版) 格式
     * 注意：EasyExcel 主要支持 .xlsx 格式，.xls 文件可能功能受限
     *
     * @param templateName 模板文件名
     * @throws IllegalArgumentException 当扩展名不是 Excel 格式时抛出
     */
    public static void validateExcelTemplateExtension(String templateName) {
        validateTemplateName(templateName);
        String lowerName = templateName.toLowerCase();
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException("Excel 模板必须是 .xlsx 或 .xls 格式");
        }
    }
}
