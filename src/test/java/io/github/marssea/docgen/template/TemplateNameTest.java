package io.github.marssea.docgen.template;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.template.TemplateName.TemplateKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TemplateName 单元测试 锁住「什么名字能用、什么格式配什么文档」这唯一一份规则 */
@DisplayName("TemplateName 测试")
class TemplateNameTest {

    @Nested
    @DisplayName("空名测试")
    class BlankNameTest {

        @Test
        @DisplayName("null 应该抛出异常")
        void shouldThrowExceptionWhenNameIsNull() {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> TemplateName.parse(null, TemplateKind.WORD));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("空字符串应该抛出异常")
        void shouldThrowExceptionWhenNameIsEmpty() {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> TemplateName.parse("", TemplateKind.WORD));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("纯空白应该抛出异常")
        void shouldThrowExceptionWhenNameIsBlank() {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> TemplateName.parse("   ", TemplateKind.WORD));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("非法字符测试")
    class IllegalCharacterTest {

        @ParameterizedTest
        @ValueSource(strings = {"../etc/passwd", "..\\system32", "test/../secret.docx"})
        @DisplayName("路径遍历字符应该被拒绝")
        void shouldRejectParentDirectoryReference(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.WORD));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/etc/passwd", "path/to/file.docx", "sub/template.xlsx"})
        @DisplayName("斜杠应该被拒绝")
        void shouldRejectForwardSlash(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.WORD));
        }

        @ParameterizedTest
        @ValueSource(strings = {"C:\\Windows\\system32", "path\\to\\file.docx"})
        @DisplayName("反斜杠应该被拒绝")
        void shouldRejectBackSlash(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.WORD));
        }

        @Test
        @DisplayName("Windows 盘符冒号应该被拒绝（黑名单曾漏掉这一项）")
        void shouldRejectDriveLetterColon() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse("C:evil.docx", TemplateKind.WORD));
        }

        @ParameterizedTest
        @ValueSource(strings = {"模板 名.docx", "报表.docx "})
        @DisplayName("空白字符应该被拒绝")
        void shouldRejectWhitespaceInName(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.WORD));
        }

        @Test
        @DisplayName("控制字符应该被拒绝")
        void shouldRejectControlCharacter() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse("report\n\rname.docx", TemplateKind.WORD));
        }

        @Test
        @DisplayName("缺少扩展名应该被拒绝")
        void shouldRejectNameWithoutExtension() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse("template", TemplateKind.WORD));
        }
    }

    @Nested
    @DisplayName("合法名字测试")
    class ValidNameTest {

        @Test
        @DisplayName("合法名字应该通过校验")
        void shouldPassValidationForValidTemplateName() {
            assertEquals(
                    "valid-template.docx",
                    TemplateName.parse("valid-template.docx", TemplateKind.WORD).value());
            assertEquals(
                    "report_2024.xlsx",
                    TemplateName.parse("report_2024.xlsx", TemplateKind.EXCEL).value());
            assertEquals("中文模板.docx", TemplateName.parse("中文模板.docx", TemplateKind.WORD).value());
        }

        @Test
        @DisplayName("点号与连字符组合的名字应该通过校验")
        void shouldPassValidationForDottedName() {
            assertEquals(
                    "my.report-2024.docx",
                    TemplateName.parse("my.report-2024.docx", TemplateKind.WORD).value());
        }
    }

    @Nested
    @DisplayName("Word 扩展名测试")
    class WordExtensionTest {

        @Test
        @DisplayName(".docx 应该通过校验，且大小写不敏感")
        void shouldPassValidationForDocxExtension() {
            assertDoesNotThrow(() -> TemplateName.parse("template.docx", TemplateKind.WORD));
            assertDoesNotThrow(() -> TemplateName.parse("TEMPLATE.DOCX", TemplateKind.WORD));
        }

        @ParameterizedTest
        @ValueSource(strings = {"template.doc", "TEMPLATE.DOC"})
        @DisplayName(".doc 应该被拒绝（poi-tl 仅支持 OOXML 的 .docx）")
        void shouldRejectDocExtension(String templateName) {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> TemplateName.parse(templateName, TemplateKind.WORD));
            assertEquals("Word 模板必须是 .docx 格式: " + templateName, exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "template.xlsx",
                    "template.pdf",
                    "template.txt",
                    "template",
                    "template.doc",
                    "TEMPLATE.DOC"
                })
        @DisplayName("非 .docx 扩展名应该被拒绝")
        void shouldThrowExceptionForNonWordExtension(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.WORD));
        }

        @ParameterizedTest
        @ValueSource(strings = {"template.xls", "TEMPLATE.XLS"})
        @DisplayName(".xls 应该被拒绝（上传入口与列表入口均不接收，放行只会给出死路）")
        void shouldRejectXlsExtension(String templateName) {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> TemplateName.parse(templateName, TemplateKind.EXCEL));
            assertEquals("Excel 模板必须是 .xlsx 格式: " + templateName, exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Excel 扩展名测试")
    class ExcelExtensionTest {

        @Test
        @DisplayName(".xlsx 应该通过校验，且大小写不敏感")
        void shouldPassValidationForXlsxExtension() {
            assertDoesNotThrow(() -> TemplateName.parse("template.xlsx", TemplateKind.EXCEL));
            assertDoesNotThrow(() -> TemplateName.parse("TEMPLATE.XLSX", TemplateKind.EXCEL));
        }

        @ParameterizedTest
        @ValueSource(strings = {"template.docx", "template.pdf", "template.csv", "template"})
        @DisplayName("非 .xlsx 扩展名应该被拒绝")
        void shouldThrowExceptionForNonExcelExtension(String templateName) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse(templateName, TemplateKind.EXCEL));
        }

        @Test
        @DisplayName("Word 名字不得用于 Excel 类型")
        void shouldRejectWordNameForExcelKind() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateName.parse("template.docx", TemplateKind.EXCEL));
        }
    }
}
