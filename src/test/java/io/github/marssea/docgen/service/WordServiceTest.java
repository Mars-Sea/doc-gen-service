package io.github.marssea.docgen.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** WordService 单元测试 */
@DisplayName("WordService 测试")
class WordServiceTest {

    @TempDir Path tempDir;

    private WordService wordService;
    private DocGenProperties properties;
    private ImagePayloadConverter imagePayloadConverter;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        imagePayloadConverter = new ImagePayloadConverter();
        wordService = new WordService(properties, imagePayloadConverter);
    }

    @Nested
    @DisplayName("generateWord 测试")
    class GenerateWordTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            Map<String, Object> data = Map.of("title", "Test");

            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () -> wordService.generateWord("non-existent.docx", data));

            assertEquals("non-existent.docx", exception.getTemplateName());
        }

        @Test
        @DisplayName("非法模板名应该抛出 IllegalArgumentException")
        void shouldThrowExceptionForInvalidTemplateName() {
            Map<String, Object> data = Map.of("title", "Test");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> wordService.generateWord("../etc/passwd", data));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> wordService.generateWord("template.xlsx", data));
        }

        @Test
        @DisplayName("成功生成 Word 文档")
        void shouldGenerateWordDocument() throws Exception {
            // 创建测试模板
            Path templatePath = tempDir.resolve("test-template.docx");
            createSimpleWordTemplate(templatePath);

            // 生成文档
            Map<String, Object> data = new HashMap<>();
            data.put("title", "Test Title");
            data.put("content", "Test Content");

            byte[] result = wordService.generateWord("test-template.docx", data);

            assertNotNull(result);
            assertTrue(result.length > 0);

            // 验证生成的文档是有效的 Word 文档
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                assertNotNull(doc);
            }
        }

        @Test
        @DisplayName("占位符首尾有空格时应正常渲染")
        void shouldGenerateWordDocumentWithSpacedPlaceholders() throws Exception {
            Path templatePath = tempDir.resolve("spaced-template.docx");
            createSpacedPlaceholderWordTemplate(templatePath);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "Alice");
            data.put("age", 18);

            byte[] result = wordService.generateWord("spaced-template.docx", data);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                assertEquals("Name: Alice", doc.getParagraphs().get(0).getText());
                assertEquals("Age: 18", doc.getParagraphs().get(1).getText());
            }
        }

        @Test
        @DisplayName("data 为空 Map 时应该正常生成文档")
        void shouldGenerateWordDocumentWithEmptyData() throws Exception {
            Path templatePath = tempDir.resolve("simple.docx");
            createPlainWordTemplate(templatePath);

            // 无占位符的模板，空数据也能正常渲染
            byte[] result = wordService.generateWord("simple.docx", new HashMap<>());

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Nested
    @DisplayName("generateWord 图片载荷测试")
    class GenerateWordImagePayloadTest {

        @Test
        @DisplayName("无效图片载荷应抛出 InvalidImagePayloadException")
        void shouldThrowExceptionForInvalidImagePayload() throws Exception {
            Path templatePath = tempDir.resolve("image-template.docx");
            createSimpleWordTemplate(templatePath);

            // 使用不支持的协议
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "ftp://example.com/logo.png");
            imagePayload.put("format", "png");
            imagePayload.put("width", 120);
            imagePayload.put("height", 60);
            data.put("logo", imagePayload);
            data.put("title", "Test");

            assertThrows(
                    InvalidImagePayloadException.class,
                    () -> wordService.generateWord("image-template.docx", data));
        }

        @Test
        @DisplayName("不支持的图片格式应抛出 InvalidImagePayloadException")
        void shouldThrowExceptionForUnsupportedFormat() throws Exception {
            Path templatePath = tempDir.resolve("image-format-template.docx");
            createSimpleWordTemplate(templatePath);

            Map<String, Object> data = new HashMap<>();
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "https://example.com/logo.gif");
            imagePayload.put("format", "gif");
            imagePayload.put("width", 120);
            imagePayload.put("height", 60);
            data.put("logo", imagePayload);
            data.put("title", "Test");

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> wordService.generateWord("image-format-template.docx", data));
            assertTrue(ex.getMessage().contains("not supported"));
        }

        @Test
        @DisplayName("缺少必填字段的图片载荷应抛出异常")
        void shouldThrowExceptionForIncompleteImagePayload() throws Exception {
            Path templatePath = tempDir.resolve("image-incomplete-template.docx");
            createSimpleWordTemplate(templatePath);

            Map<String, Object> data = new HashMap<>();
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "https://example.com/logo.png");
            // 缺少 format, width, height
            data.put("logo", imagePayload);
            data.put("title", "Test");

            assertThrows(
                    InvalidImagePayloadException.class,
                    () -> wordService.generateWord("image-incomplete-template.docx", data));
        }

        @Test
        @DisplayName("包含图片载荷的普通文本字段应正常处理")
        void shouldHandleMixedDataWithImageAndText() throws Exception {
            Path templatePath = tempDir.resolve("mixed-template.docx");
            createSimpleWordTemplate(templatePath);

            // 混合数据：包含普通文本和无效图片载荷
            Map<String, Object> data = new HashMap<>();
            data.put("title", "Normal Title");
            data.put("content", "Normal Content");

            // 添加一个无效图片载荷（格式错误）
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "ftp://example.com/logo.png");
            imagePayload.put("format", "png");
            imagePayload.put("width", 120);
            imagePayload.put("height", 60);
            data.put("logo", imagePayload);

            // 应该抛出异常，因为 logo 字段校验失败
            assertThrows(
                    InvalidImagePayloadException.class,
                    () -> wordService.generateWord("mixed-template.docx", data));
        }
    }

    @Nested
    @DisplayName("generateBatch 测试")
    class GenerateBatchTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            List<Map<String, Object>> dataList =
                    List.of(Map.of("name", "Test1"), Map.of("name", "Test2"));

            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () -> wordService.generateBatch("non-existent.docx", dataList));

            assertEquals("non-existent.docx", exception.getTemplateName());
        }

        @Test
        @DisplayName("成功批量生成 Word 文档")
        void shouldGenerateBatchDocument() throws Exception {
            Path templatePath = tempDir.resolve("batch-template.docx");
            createSimpleWordTemplate(templatePath);

            List<Map<String, Object>> dataList = new ArrayList<>();
            dataList.add(Map.of("title", "Page 1", "content", "Content 1"));
            dataList.add(Map.of("title", "Page 2", "content", "Content 2"));
            dataList.add(Map.of("title", "Page 3", "content", "Content 3"));

            byte[] result = wordService.generateBatch("batch-template.docx", dataList);

            assertNotNull(result);
            assertTrue(result.length > 0);

            // 验证生成的是有效的 Word 文档
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                assertNotNull(doc);
            }
        }

        @Test
        @DisplayName("批量生成应支持多页模板且保持数据顺序")
        void shouldGenerateBatchDocumentWithMultiPageTemplateInOrder() throws Exception {
            Path templatePath = tempDir.resolve("multi-page-batch-template.docx");
            createMultiPageWordTemplate(templatePath);

            List<Map<String, Object>> dataList =
                    List.of(
                            Map.of("name", "Alice", "summary", "First summary"),
                            Map.of("name", "Bob", "summary", "Second summary"));

            byte[] result = wordService.generateBatch("multi-page-batch-template.docx", dataList);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                List<String> paragraphs =
                        doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();

                assertParagraphOrder(
                        paragraphs,
                        "Page1 Name: Alice",
                        "Page2 Summary: First summary",
                        "Page1 Name: Bob",
                        "Page2 Summary: Second summary");
            }
        }

        @Test
        @DisplayName("批量生成 4 页模板 x 3 条数据应保持 12 个标记的正确顺序")
        void shouldGenerateBatchDocumentWith4PageTemplateAnd3Records() throws Exception {
            Path templatePath = tempDir.resolve("four-page-template.docx");
            createFourPageWordTemplate(templatePath);

            List<Map<String, Object>> dataList =
                    List.of(Map.of("id", "R1"), Map.of("id", "R2"), Map.of("id", "R3"));

            byte[] result = wordService.generateBatch("four-page-template.docx", dataList);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                List<String> paragraphs =
                        doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();

                // 4 pages x 3 records = 12 markers in exact order
                assertParagraphOrder(
                        paragraphs,
                        // Record 1
                        "第一页 R1",
                        "第二页 R1",
                        "第三页 R1",
                        "第四页 R1",
                        // Record 2
                        "第一页 R2",
                        "第二页 R2",
                        "第三页 R2",
                        "第四页 R2",
                        // Record 3
                        "第一页 R3",
                        "第二页 R3",
                        "第三页 R3",
                        "第四页 R3");
            }
        }

        @Test
        @DisplayName("批量生成多页模板应支持普通表格")
        void shouldGenerateBatchDocumentWithMultiPageTemplateAndTable() throws Exception {
            Path templatePath = tempDir.resolve("multi-page-table-template.docx");
            createMultiPageTableTemplate(templatePath);

            List<Map<String, Object>> dataList =
                    List.of(
                            Map.of("name", "Alice", "item", "A-1", "amount", 10),
                            Map.of("name", "Bob", "item", "B-1", "amount", 30));

            byte[] result = wordService.generateBatch("multi-page-table-template.docx", dataList);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                String tableText =
                        doc.getTables().stream()
                                .flatMap(table -> table.getRows().stream())
                                .flatMap(row -> row.getTableCells().stream())
                                .map(cell -> cell.getText())
                                .toList()
                                .toString();
                assertTrue(tableText.contains("A-1"));
                assertTrue(tableText.contains("10"));
                assertTrue(tableText.contains("B-1"));
                assertTrue(tableText.contains("30"));
            }
        }

        @Test
        @DisplayName("批量生成应支持首尾有空格的占位符")
        void shouldGenerateBatchDocumentWithSpacedPlaceholders() throws Exception {
            Path templatePath = tempDir.resolve("batch-spaced-template.docx");
            createSpacedPlaceholderWordTemplate(templatePath);

            List<Map<String, Object>> dataList =
                    List.of(Map.of("name", "Alice", "age", 18), Map.of("name", "Bob", "age", 20));

            byte[] result = wordService.generateBatch("batch-spaced-template.docx", dataList);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                String text =
                        doc.getParagraphs().stream()
                                .map(XWPFParagraph::getText)
                                .toList()
                                .toString();
                assertTrue(text.contains("Name: Alice"));
                assertTrue(text.contains("Age: 18"));
                assertTrue(text.contains("Name: Bob"));
                assertTrue(text.contains("Age: 20"));
            }
        }

        @Test
        @DisplayName("空数据列表应该抛出 IllegalArgumentException")
        void shouldThrowExceptionForEmptyDataList() throws Exception {
            Path templatePath = tempDir.resolve("empty-batch.docx");
            createSimpleWordTemplate(templatePath);

            List<Map<String, Object>> dataList = new ArrayList<>();

            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> wordService.generateBatch("empty-batch.docx", dataList));

            assertEquals("Data list cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("批量生成中包含无效图片载荷应抛出异常")
        void shouldThrowExceptionForInvalidImagePayloadInBatch() throws Exception {
            Path templatePath = tempDir.resolve("batch-image-template.docx");
            createSimpleWordTemplate(templatePath);

            List<Map<String, Object>> dataList = new ArrayList<>();
            dataList.add(Map.of("title", "Page 1", "content", "Content 1"));

            // 第二条数据包含无效图片载荷
            Map<String, Object> page2 = new HashMap<>();
            page2.put("title", "Page 2");
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "ftp://example.com/logo.png");
            imagePayload.put("format", "png");
            imagePayload.put("width", 120);
            imagePayload.put("height", 60);
            page2.put("logo", imagePayload);
            dataList.add(page2);

            assertThrows(
                    InvalidImagePayloadException.class,
                    () -> wordService.generateBatch("batch-image-template.docx", dataList));
        }

        @Test
        @DisplayName("批量生成中不支持的图片格式应抛出明确异常")
        void shouldThrowExceptionForUnsupportedFormatInBatch() throws Exception {
            Path templatePath = tempDir.resolve("batch-format-template.docx");
            createSimpleWordTemplate(templatePath);

            Map<String, Object> page1 = new HashMap<>();
            page1.put("title", "Page 1");
            Map<String, Object> imagePayload = new HashMap<>();
            imagePayload.put("type", "image");
            imagePayload.put("url", "https://example.com/logo.bmp");
            imagePayload.put("format", "bmp");
            imagePayload.put("width", 100);
            imagePayload.put("height", 100);
            page1.put("logo", imagePayload);

            List<Map<String, Object>> dataList = List.of(page1);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () ->
                                    wordService.generateBatch(
                                            "batch-format-template.docx", dataList));
            assertTrue(ex.getMessage().contains("not supported"));
        }
    }

    /** 创建简单的 Word 模板用于测试 */
    private void createSimpleWordTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Title: {{title}}");

            XWPFParagraph contentParagraph = document.createParagraph();
            XWPFRun contentRun = contentParagraph.createRun();
            contentRun.setText("Content: {{content}}");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }

    /** 创建包含首尾空格占位符的 Word 模板用于测试 */
    private void createSpacedPlaceholderWordTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph nameParagraph = document.createParagraph();
            XWPFRun nameRun = nameParagraph.createRun();
            nameRun.setText("Name: {{ name }}");

            XWPFParagraph ageParagraph = document.createParagraph();
            XWPFRun ageRun = ageParagraph.createRun();
            ageRun.setText("Age: {{  age  }}");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }

    /** 创建多页 Word 模板用于批量生成测试 */
    private void createMultiPageWordTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph firstPage = document.createParagraph();
            XWPFRun firstRun = firstPage.createRun();
            firstRun.setText("Page1 Name: {{name}}");
            firstRun.addBreak(BreakType.PAGE);

            XWPFParagraph secondPage = document.createParagraph();
            XWPFRun secondRun = secondPage.createRun();
            secondRun.setText("Page2 Summary: {{summary}}");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }

    /** 创建包含普通表格的多页 Word 模板用于批量生成测试 */
    private void createMultiPageTableTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph firstPage = document.createParagraph();
            XWPFRun firstRun = firstPage.createRun();
            firstRun.setText("姓名: {{name}}");
            firstRun.addBreak(BreakType.PAGE);

            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("项目");
            table.getRow(0).getCell(1).setText("金额");
            table.getRow(1).getCell(0).setText("{{item}}");
            table.getRow(1).getCell(1).setText("{{amount}}");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }

    private void assertParagraphOrder(List<String> paragraphs, String... expectedTexts) {
        String fullText = String.join("\n", paragraphs);
        int currentIndex = -1;
        for (String expected : expectedTexts) {
            int foundIndex = fullText.indexOf(expected);
            assertTrue(foundIndex > currentIndex, "Expected text in order: " + expected);
            currentIndex = foundIndex;
        }
    }

    /** 创建无占位符的 Word 模板用于测试 */
    private void createPlainWordTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Plain document without placeholders");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }

    /** 创建 4 页 Word 模板用于批量合并回归测试 */
    private void createFourPageWordTemplate(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Page 1
            XWPFParagraph p1 = document.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("第一页 {{id}}");
            r1.addBreak(BreakType.PAGE);

            // Page 2
            XWPFParagraph p2 = document.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("第二页 {{id}}");
            r2.addBreak(BreakType.PAGE);

            // Page 3
            XWPFParagraph p3 = document.createParagraph();
            XWPFRun r3 = p3.createRun();
            r3.setText("第三页 {{id}}");
            r3.addBreak(BreakType.PAGE);

            // Page 4
            XWPFParagraph p4 = document.createParagraph();
            XWPFRun r4 = p4.createRun();
            r4.setText("第四页 {{id}}");

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                document.write(out);
            }
        }
    }
}
