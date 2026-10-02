package io.github.marssea.docgen.template;

import java.util.Locale;

/**
 * 模板文件的名字
 *
 * <p>把「什么名字能用、什么格式配什么文档」收敛为唯一一份规则，用不可变值对象表达： 只有 {@link #parse(String, TemplateKind)} 成功返回的实例才是合法名字。
 *
 * <p>以此保证三条性质：
 *
 * <ul>
 *   <li>未校验的名字在类型上无法表达 —— 读侧与写侧的接口都只接收本类型
 *   <li>Windows 盘符冒号 {@code C:evil.docx} 这类黑名单漏掉的写法被白名单自然拒绝
 *   <li>Word 与 Excel 的合法格式由各自库的真实能力决定（见各类常量），不再出现<br>
 *       「上传入口拒 .xls、生成入口放 .xls」的分叉
 * </ul>
 *
 * <p>字符规则采用白名单而非黑名单：黑名单的维护方式是「想到下一个坏字符再补一次」， 而正确形态本就是「只能是什么」。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
public final class TemplateName {

    /**
     * 允许的文件名字符
     *
     * <p>要求首字符与后续字符都是 Unicode 字母、数字、下划线、点号或连字符。 中文模板名由此保持合法（{@code 中文模板.docx}），而 {@code :} 、{@code
     * /}、{@code \}、控制字符、空格全部落选。
     */
    private static final String NAME_PATTERN = "[\\p{L}\\p{N}_.-]+";

    /** Word 模板唯一合法扩展名：poi-tl 基于 OOXML，仅支持 .docx */
    private static final String WORD_EXTENSION = "docx";

    /** Excel 模板唯一合法扩展名 */
    private static final String EXCEL_EXTENSION = "xlsx";

    private final String value;

    private TemplateName(String value) {
        this.value = value;
    }

    /**
     * 解析并校验模板名
     *
     * @param raw 调用方传入的原始名字，需包含扩展名
     * @param kind 目标文档类型，决定哪些扩展名合法
     * @return 校验通过的模板名
     * @throws IllegalArgumentException 名字为空、含非法字符，或扩展名与该文档类型不匹配
     */
    public static TemplateName parse(String raw, TemplateKind kind) {
        requireNotBlank(raw);
        requireMatchesPattern(raw);
        requireExpectedExtension(raw, kind);
        return new TemplateName(raw);
    }

    /**
     * 取得原始名字
     *
     * @return 调用方传入的名字原样返回，不含目录路径
     */
    public String value() {
        return value;
    }

    private static void requireNotBlank(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("模板文件名不能为空");
        }
    }

    private static void requireMatchesPattern(String raw) {
        if (!raw.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("模板文件名包含非法字符（仅允许字母、数字、下划线、点号、连字符）: " + raw);
        }
    }

    private static void requireExpectedExtension(String raw, TemplateKind kind) {
        String actual = extensionOf(raw);
        if (actual == null) {
            throw new IllegalArgumentException("模板文件名缺少扩展名: " + raw);
        }
        String expected = kind == TemplateKind.WORD ? WORD_EXTENSION : EXCEL_EXTENSION;
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    labelOf(kind) + " 模板必须是 ." + expected + " 格式: " + raw);
        }
    }

    /** 取小写扩展名，不含点号；无扩展名时返回 null */
    private static String extensionOf(String raw) {
        int dot = raw.lastIndexOf('.');
        if (dot < 0 || dot == raw.length() - 1) {
            return null;
        }
        return raw.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String labelOf(TemplateKind kind) {
        return kind == TemplateKind.WORD ? "Word" : "Excel";
    }

    /** 模板对应的文档类型，决定合法的扩展名 */
    public enum TemplateKind {
        /** Word 文档，合法扩展名 {@code .docx} */
        WORD,
        /** Excel 文档，合法扩展名 {@code .xlsx} */
        EXCEL
    }
}
