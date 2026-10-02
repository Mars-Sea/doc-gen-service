package io.github.marssea.docgen.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.marssea.docgen.controller.GeneratedDocumentResponse.DocumentType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * GeneratedDocumentResponse 单元测试
 *
 * <p>直接锁定收敛后的头语义：扩展名→MediaType、默认名、字符清洗、RFC 5987 中文编码、空内容拒绝。 DocControllerTest 只验证端点接线，不重述这些 规则。
 */
@DisplayName("GeneratedDocumentResponse 测试")
class GeneratedDocumentResponseTest {

    private static final byte[] CONTENT = new byte[] {1, 2, 3};

    @Nested
    @DisplayName("Content-Type 测试")
    class ContentTypeTest {

        @Test
        @DisplayName("Word 与批量 Word 应为 wordprocessingml.document")
        void shouldUseWordContentType() {
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    contentTypeOf(DocumentType.WORD));
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    contentTypeOf(DocumentType.WORD_BATCH));
        }

        @Test
        @DisplayName("Excel 与填充 Excel 应为 spreadsheetml.sheet")
        void shouldUseExcelContentType() {
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    contentTypeOf(DocumentType.EXCEL));
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    contentTypeOf(DocumentType.EXCEL_FILL));
        }
    }

    @Nested
    @DisplayName("扩展名测试")
    class ExtensionTest {

        @Test
        @DisplayName("Word 类型拼 .docx，Excel 类型拼 .xlsx")
        void shouldUseCorrectExtension() {
            assertTrue(dispositionOf(DocumentType.WORD, "report").contains("report.docx"));
            assertTrue(dispositionOf(DocumentType.WORD_BATCH, "report").contains("report.docx"));
            assertTrue(dispositionOf(DocumentType.EXCEL, "report").contains("report.xlsx"));
            assertTrue(dispositionOf(DocumentType.EXCEL_FILL, "report").contains("report.xlsx"));
        }
    }

    @Nested
    @DisplayName("默认文件名测试")
    class DefaultNameTest {

        @Test
        @DisplayName("文件名为空时按文档类型取默认名")
        void shouldUseDefaultNameWhenRequestedNameIsBlank() {
            assertTrue(dispositionOf(DocumentType.WORD, null).contains("generated.docx"));
            assertTrue(dispositionOf(DocumentType.WORD_BATCH, "").contains("batch_generated.docx"));
            assertTrue(dispositionOf(DocumentType.EXCEL, "   ").contains("generated.xlsx"));
            assertTrue(dispositionOf(DocumentType.EXCEL_FILL, null).contains("filled.xlsx"));
        }
    }

    @Nested
    @DisplayName("文件名清洗测试")
    class SanitizeTest {

        @Test
        @DisplayName("路径分隔符与 Windows 保留字应被替换为下划线")
        void shouldReplaceIllegalCharacters() {
            assertTrue(
                    dispositionOf(DocumentType.WORD, "report/2025:test")
                            .contains("report_2025_test.docx"));
        }

        @Test
        @DisplayName("反斜杠与竖线应被替换")
        void shouldReplaceBackSlashAndPipe() {
            assertTrue(dispositionOf(DocumentType.WORD, "a\\b|c").contains("a_b_c.docx"));
        }
    }

    @Nested
    @DisplayName("中文文件名编码测试")
    class ChineseNameEncodingTest {

        @Test
        @DisplayName("中文文件名应经百分号编码写入 filename 与 filename* 两段")
        void shouldEncodeChineseFileNameInBothSegments() {
            String expected =
                    URLEncoder.encode("报表.docx", StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = dispositionOf(DocumentType.WORD, "报表");

            assertTrue(disposition.contains("filename=\"" + expected + "\""), disposition);
            assertTrue(disposition.contains("filename*=UTF-8''" + expected), disposition);
            assertFalse(disposition.contains("报表"), disposition);
        }

        @Test
        @DisplayName("空格应编码为 %20 而非 +")
        void shouldEncodeSpaceAsPercent20() {
            String disposition = dispositionOf(DocumentType.EXCEL, "my report");

            assertTrue(disposition.contains("my%20report.xlsx"), disposition);
            assertFalse(disposition.contains("+"), disposition);
        }
    }

    @Nested
    @DisplayName("内容长度测试")
    class ContentLengthTest {

        @Test
        @DisplayName("contentLength 应为文档字节数")
        void shouldSetContentLength() {
            assertEquals(
                    3,
                    GeneratedDocumentResponse.of(CONTENT, DocumentType.WORD, "x")
                            .toResponseEntity()
                            .getHeaders()
                            .getContentLength());
        }

        @Test
        @DisplayName("响应体应为原始字节")
        void shouldCarryOriginalBytes() {
            assertArrayEquals(
                    CONTENT,
                    GeneratedDocumentResponse.of(CONTENT, DocumentType.WORD, "x")
                            .toResponseEntity()
                            .getBody());
        }
    }

    @Nested
    @DisplayName("内容校验测试")
    class ContentValidationTest {

        @Test
        @DisplayName("内容为 null 时应抛异常")
        void shouldThrowWhenContentIsNull() {
            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () -> GeneratedDocumentResponse.of(null, DocumentType.WORD, "x"));
            assertEquals("生成的文档内容为空", exception.getMessage());
        }

        @Test
        @DisplayName("内容为空数组时应抛异常")
        void shouldThrowWhenContentIsEmpty() {
            assertThrows(
                    IllegalStateException.class,
                    () -> GeneratedDocumentResponse.of(new byte[0], DocumentType.WORD, "x"));
        }
    }

    private static String contentTypeOf(DocumentType type) {
        return GeneratedDocumentResponse.of(CONTENT, type, "x")
                .toResponseEntity()
                .getHeaders()
                .getContentType()
                .toString();
    }

    private static String dispositionOf(DocumentType type, String name) {
        return GeneratedDocumentResponse.of(CONTENT, type, name)
                .toResponseEntity()
                .getHeaders()
                .getFirst("Content-Disposition");
    }
}
