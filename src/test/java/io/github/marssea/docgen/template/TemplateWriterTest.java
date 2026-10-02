package io.github.marssea.docgen.template;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.template.TemplateName.TemplateKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** TemplateWriter 单元测试 覆盖 upload / delete 两条写路径 */
@DisplayName("TemplateWriter 测试")
class TemplateWriterTest {

    @TempDir Path tempDir;

    private TemplateWriter writer;
    private TemplateRepository repository;

    @BeforeEach
    void setUp() {
        DocGenProperties properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        repository = new TemplateRepository(properties);
        writer = new TemplateWriter(repository);
    }

    @Nested
    @DisplayName("upload 测试")
    class UploadTest {

        @Test
        @DisplayName("成功上传 .docx 文件")
        void shouldUploadDocxFile() throws IOException {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test-template.docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "test content".getBytes());

            String result = writer.upload(file);

            assertEquals("test-template.docx", result);
            assertTrue(Files.exists(tempDir.resolve("test-template.docx")));
        }

        @Test
        @DisplayName("成功上传 .xlsx 文件")
        void shouldUploadXlsxFile() throws IOException {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test-data.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "test content".getBytes());

            String result = writer.upload(file);

            assertEquals("test-data.xlsx", result);
            assertTrue(Files.exists(tempDir.resolve("test-data.xlsx")));
        }

        @Test
        @DisplayName("空文件应该抛出异常")
        void shouldThrowExceptionForEmptyFile() {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "empty.docx", "application/octet-stream", new byte[0]);

            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> writer.upload(file));

            assertEquals("上传的文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("null 文件应该抛出异常")
        void shouldThrowExceptionForNullFile() {
            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> writer.upload(null));

            assertEquals("上传的文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("不支持的文件类型应该抛出异常")
        void shouldThrowExceptionForUnsupportedFileType() {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "test.pdf", "application/pdf", "test content".getBytes());

            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> writer.upload(file));

            assertEquals("仅支持 .docx 和 .xlsx 格式的模板文件", exception.getMessage());
        }

        @Test
        @DisplayName("文件名中的非法字符应该被替换")
        void shouldSanitizeFileName() throws IOException {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test:file<name>.docx",
                            "application/octet-stream",
                            "test content".getBytes());

            String result = writer.upload(file);

            assertEquals("test_file_name_.docx", result);
        }

        @Test
        @DisplayName("同目录不存在时应该自动创建")
        void shouldCreateTemplateDirectoryWhenAbsent() throws IOException {
            DocGenProperties properties = new DocGenProperties();
            properties.setTemplatePath(tempDir.resolve("nested/dir").toString());
            TemplateWriter freshWriter = new TemplateWriter(new TemplateRepository(properties));

            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "nested.docx",
                            "application/octet-stream",
                            "test content".getBytes());

            assertEquals("nested.docx", freshWriter.upload(file));
            assertTrue(Files.exists(tempDir.resolve("nested/dir/nested.docx")));
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTest {

        @Test
        @DisplayName("成功删除模板文件")
        void shouldDeleteTemplate() throws IOException {
            Path templatePath = tempDir.resolve("to-delete.docx");
            Files.createFile(templatePath);
            assertTrue(Files.exists(templatePath));

            boolean result = writer.delete(TemplateName.parse("to-delete.docx", TemplateKind.WORD));

            assertTrue(result);
            assertFalse(Files.exists(templatePath));
        }

        @Test
        @DisplayName("文件不存在时应该返回 false")
        void shouldReturnFalseWhenFileNotExist() throws IOException {
            boolean result =
                    writer.delete(TemplateName.parse("non-existent.docx", TemplateKind.WORD));

            assertFalse(result);
        }

        @Test
        @DisplayName("目标是目录时应该拒绝删除")
        void shouldRejectDirectoryDeletion() throws IOException {
            Files.createDirectory(tempDir.resolve("sub.docx"));

            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> writer.delete(TemplateName.parse("sub.docx", TemplateKind.WORD)));

            assertEquals("只能删除文件，不能删除目录", exception.getMessage());
        }
    }
}
