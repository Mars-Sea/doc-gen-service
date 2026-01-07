package io.github.marssea.docgen.service;

import io.github.marssea.docgen.config.DocGenProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateService 单元测试
 */
@DisplayName("TemplateService 测试")
class TemplateServiceTest {

    @TempDir
    Path tempDir;

    private TemplateService templateService;
    private DocGenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        templateService = new TemplateService(properties);
    }

    @Nested
    @DisplayName("uploadTemplate 测试")
    class UploadTemplateTest {

        @Test
        @DisplayName("成功上传 .docx 文件")
        void shouldUploadDocxFile() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "test content".getBytes());

            String result = templateService.uploadTemplate(file);

            assertEquals("test-template.docx", result);
            assertTrue(Files.exists(tempDir.resolve("test-template.docx")));
        }

        @Test
        @DisplayName("成功上传 .xlsx 文件")
        void shouldUploadXlsxFile() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "test content".getBytes());

            String result = templateService.uploadTemplate(file);

            assertEquals("test-data.xlsx", result);
            assertTrue(Files.exists(tempDir.resolve("test-data.xlsx")));
        }

        @Test
        @DisplayName("空文件应该抛出异常")
        void shouldThrowExceptionForEmptyFile() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "empty.docx",
                    "application/octet-stream",
                    new byte[0]);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> templateService.uploadTemplate(file));

            assertEquals("上传的文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("null 文件应该抛出异常")
        void shouldThrowExceptionForNullFile() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> templateService.uploadTemplate(null));

            assertEquals("上传的文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("不支持的文件类型应该抛出异常")
        void shouldThrowExceptionForUnsupportedFileType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    "application/pdf",
                    "test content".getBytes());

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> templateService.uploadTemplate(file));

            assertEquals("仅支持 .docx 和 .xlsx 格式的模板文件", exception.getMessage());
        }

        @Test
        @DisplayName("文件名中的非法字符应该被替换")
        void shouldSanitizeFileName() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test:file<name>.docx",
                    "application/octet-stream",
                    "test content".getBytes());

            String result = templateService.uploadTemplate(file);

            assertEquals("test_file_name_.docx", result);
        }
    }

    @Nested
    @DisplayName("listTemplates 测试")
    class ListTemplatesTest {

        @Test
        @DisplayName("应该返回所有模板文件")
        void shouldReturnAllTemplates() throws IOException {
            // 创建测试文件
            Files.createFile(tempDir.resolve("template1.docx"));
            Files.createFile(tempDir.resolve("template2.xlsx"));
            Files.createFile(tempDir.resolve("template3.docx"));

            List<String> templates = templateService.listTemplates();

            assertEquals(3, templates.size());
            assertTrue(templates.contains("template1.docx"));
            assertTrue(templates.contains("template2.xlsx"));
            assertTrue(templates.contains("template3.docx"));
        }

        @Test
        @DisplayName("应该过滤非模板文件")
        void shouldFilterNonTemplateFiles() throws IOException {
            Files.createFile(tempDir.resolve("template.docx"));
            Files.createFile(tempDir.resolve("readme.txt"));
            Files.createFile(tempDir.resolve("data.pdf"));

            List<String> templates = templateService.listTemplates();

            assertEquals(1, templates.size());
            assertTrue(templates.contains("template.docx"));
        }

        @Test
        @DisplayName("空目录应该返回空列表")
        void shouldReturnEmptyListForEmptyDirectory() throws IOException {
            List<String> templates = templateService.listTemplates();

            assertNotNull(templates);
            assertTrue(templates.isEmpty());
        }

        @Test
        @DisplayName("目录不存在时应该返回空列表")
        void shouldReturnEmptyListWhenDirectoryNotExist() throws IOException {
            properties.setTemplatePath(tempDir.resolve("non-existent").toString());
            templateService = new TemplateService(properties);

            List<String> templates = templateService.listTemplates();

            assertNotNull(templates);
            assertTrue(templates.isEmpty());
        }
    }

    @Nested
    @DisplayName("deleteTemplate 测试")
    class DeleteTemplateTest {

        @Test
        @DisplayName("成功删除模板文件")
        void shouldDeleteTemplate() throws IOException {
            Path templatePath = tempDir.resolve("to-delete.docx");
            Files.createFile(templatePath);
            assertTrue(Files.exists(templatePath));

            boolean result = templateService.deleteTemplate("to-delete.docx");

            assertTrue(result);
            assertFalse(Files.exists(templatePath));
        }

        @Test
        @DisplayName("文件不存在时应该返回 false")
        void shouldReturnFalseWhenFileNotExist() throws IOException {
            boolean result = templateService.deleteTemplate("non-existent.docx");

            assertFalse(result);
        }

        @Test
        @DisplayName("空文件名应该抛出异常")
        void shouldThrowExceptionForEmptyFileName() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> templateService.deleteTemplate(""));

            assertEquals("模板文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("路径遍历攻击应该被拒绝")
        void shouldRejectPathTraversal() {
            assertThrows(IllegalArgumentException.class,
                    () -> templateService.deleteTemplate("../etc/passwd"));

            assertThrows(IllegalArgumentException.class,
                    () -> templateService.deleteTemplate("sub/file.docx"));
        }
    }

    @Nested
    @DisplayName("downloadTemplate 测试")
    class DownloadTemplateTest {

        @Test
        @DisplayName("成功下载模板文件")
        void shouldDownloadTemplate() throws IOException {
            byte[] content = "template content".getBytes();
            Files.write(tempDir.resolve("download.docx"), content);

            byte[] result = templateService.downloadTemplate("download.docx");

            assertArrayEquals(content, result);
        }

        @Test
        @DisplayName("文件不存在时应该抛出异常")
        void shouldThrowExceptionWhenFileNotExist() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> templateService.downloadTemplate("non-existent.docx"));

            assertTrue(exception.getMessage().contains("模板文件不存在"));
        }

        @Test
        @DisplayName("路径遍历攻击应该被拒绝")
        void shouldRejectPathTraversal() {
            assertThrows(IllegalArgumentException.class,
                    () -> templateService.downloadTemplate("../etc/passwd"));

            assertThrows(IllegalArgumentException.class,
                    () -> templateService.downloadTemplate("sub\\file.docx"));
        }
    }

    @Nested
    @DisplayName("templateExists 测试")
    class TemplateExistsTest {

        @Test
        @DisplayName("文件存在时应该返回 true")
        void shouldReturnTrueWhenFileExists() throws IOException {
            Files.createFile(tempDir.resolve("exists.docx"));

            assertTrue(templateService.templateExists("exists.docx"));
        }

        @Test
        @DisplayName("文件不存在时应该返回 false")
        void shouldReturnFalseWhenFileNotExist() {
            assertFalse(templateService.templateExists("not-exists.docx"));
        }
    }
}
