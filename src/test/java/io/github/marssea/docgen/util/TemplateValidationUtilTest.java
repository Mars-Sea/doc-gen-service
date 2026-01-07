package io.github.marssea.docgen.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateValidationUtil 单元测试
 */
@DisplayName("TemplateValidationUtil 测试")
class TemplateValidationUtilTest {

    @Nested
    @DisplayName("validateTemplateName 测试")
    class ValidateTemplateNameTest {

        @Test
        @DisplayName("null 文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName(null));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("空字符串文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName(""));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("空白字符串文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameIsBlank() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName("   "));
            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = { "../etc/passwd", "..\\system32", "test/../secret.docx" })
        @DisplayName("包含 '..' 路径遍历字符的文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameContainsParentDir(String templateName) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName(templateName));
            assertEquals("模板文件名包含非法字符", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = { "/etc/passwd", "path/to/file.docx", "sub/template.xlsx" })
        @DisplayName("包含 '/' 路径字符的文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameContainsForwardSlash(String templateName) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName(templateName));
            assertEquals("模板文件名包含非法字符", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = { "C:\\Windows\\system32", "path\\to\\file.docx" })
        @DisplayName("包含 '\\' 路径字符的文件名应该抛出异常")
        void shouldThrowExceptionWhenTemplateNameContainsBackSlash(String templateName) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateTemplateName(templateName));
            assertEquals("模板文件名包含非法字符", exception.getMessage());
        }

        @Test
        @DisplayName("有效的文件名应该通过验证")
        void shouldPassValidationForValidTemplateName() {
            assertDoesNotThrow(() -> TemplateValidationUtil.validateTemplateName("valid-template.docx"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateTemplateName("report_2024.xlsx"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateTemplateName("中文模板.docx"));
        }
    }

    @Nested
    @DisplayName("validateWordTemplateExtension 测试")
    class ValidateWordTemplateExtensionTest {

        @Test
        @DisplayName(".docx 扩展名应该通过验证")
        void shouldPassValidationForDocxExtension() {
            assertDoesNotThrow(() -> TemplateValidationUtil.validateWordTemplateExtension("template.docx"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateWordTemplateExtension("TEMPLATE.DOCX"));
        }

        @Test
        @DisplayName(".doc 扩展名应该通过验证")
        void shouldPassValidationForDocExtension() {
            assertDoesNotThrow(() -> TemplateValidationUtil.validateWordTemplateExtension("template.doc"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateWordTemplateExtension("TEMPLATE.DOC"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "template.xlsx", "template.pdf", "template.txt", "template" })
        @DisplayName("非 Word 扩展名应该抛出异常")
        void shouldThrowExceptionForNonWordExtension(String templateName) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateWordTemplateExtension(templateName));
            assertEquals("Word 模板必须是 .docx 或 .doc 格式", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("validateExcelTemplateExtension 测试")
    class ValidateExcelTemplateExtensionTest {

        @Test
        @DisplayName(".xlsx 扩展名应该通过验证")
        void shouldPassValidationForXlsxExtension() {
            assertDoesNotThrow(() -> TemplateValidationUtil.validateExcelTemplateExtension("template.xlsx"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateExcelTemplateExtension("TEMPLATE.XLSX"));
        }

        @Test
        @DisplayName(".xls 扩展名应该通过验证")
        void shouldPassValidationForXlsExtension() {
            assertDoesNotThrow(() -> TemplateValidationUtil.validateExcelTemplateExtension("template.xls"));
            assertDoesNotThrow(() -> TemplateValidationUtil.validateExcelTemplateExtension("TEMPLATE.XLS"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "template.docx", "template.pdf", "template.csv", "template" })
        @DisplayName("非 Excel 扩展名应该抛出异常")
        void shouldThrowExceptionForNonExcelExtension(String templateName) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateValidationUtil.validateExcelTemplateExtension(templateName));
            assertEquals("Excel 模板必须是 .xlsx 或 .xls 格式", exception.getMessage());
        }
    }
}
