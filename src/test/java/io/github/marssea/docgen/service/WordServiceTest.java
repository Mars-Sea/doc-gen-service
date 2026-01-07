package io.github.marssea.docgen.service;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WordService 单元测试
 */
@DisplayName("WordService 测试")
class WordServiceTest {

    @TempDir
    Path tempDir;

    private WordService wordService;
    private DocGenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        wordService = new WordService(properties);
    }

    @Nested
    @DisplayName("generateWord 测试")
    class GenerateWordTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            Map<String, Object> data = Map.of("title", "Test");

            TemplateNotFoundException exception = assertThrows(
                    TemplateNotFoundException.class,
                    () -> wordService.generateWord("non-existent.docx", data));

            assertEquals("non-existent.docx", exception.getTemplateName());
        }

        @Test
        @DisplayName("非法模板名应该抛出 IllegalArgumentException")
        void shouldThrowExceptionForInvalidTemplateName() {
            Map<String, Object> data = Map.of("title", "Test");

            assertThrows(IllegalArgumentException.class,
                    () -> wordService.generateWord("../etc/passwd", data));

            assertThrows(IllegalArgumentException.class,
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
        @DisplayName("data 为空 Map 时应该正常生成文档")
        void shouldGenerateWordDocumentWithEmptyData() throws Exception {
            Path templatePath = tempDir.resolve("simple.docx");
            createSimpleWordTemplate(templatePath);

            // poi-tl 需要非 null 的数据，使用空 Map
            byte[] result = wordService.generateWord("simple.docx", new HashMap<>());

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Nested
    @DisplayName("generateBatch 测试")
    class GenerateBatchTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            List<Map<String, Object>> dataList = List.of(
                    Map.of("name", "Test1"),
                    Map.of("name", "Test2"));

            TemplateNotFoundException exception = assertThrows(
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
        @DisplayName("空数据列表应该返回空文档")
        void shouldReturnEmptyDocumentForEmptyDataList() throws Exception {
            Path templatePath = tempDir.resolve("empty-batch.docx");
            createSimpleWordTemplate(templatePath);

            List<Map<String, Object>> dataList = new ArrayList<>();

            byte[] result = wordService.generateBatch("empty-batch.docx", dataList);

            assertNotNull(result);
            // 空数据列表生成的文档长度为 0
            assertEquals(0, result.length);
        }
    }

    /**
     * 创建简单的 Word 模板用于测试
     */
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
}
