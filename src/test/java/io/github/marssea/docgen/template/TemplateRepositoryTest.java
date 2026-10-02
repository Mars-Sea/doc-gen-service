package io.github.marssea.docgen.template;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TemplateRepository 单元测试 覆盖 locate / read / list 三条读路径 */
@DisplayName("TemplateRepository 测试")
class TemplateRepositoryTest {

    @TempDir Path tempDir;

    private TemplateRepository repository;
    private DocGenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        repository = new TemplateRepository(properties);
    }

    @Nested
    @DisplayName("locate 测试")
    class LocateTest {

        @Test
        @DisplayName("模板存在时应该返回文件对象")
        void shouldReturnFileWhenTemplateExists() throws IOException {
            Files.createFile(tempDir.resolve("report.docx"));

            File file =
                    repository.locate(
                            TemplateName.parse("report.docx", TemplateName.TemplateKind.WORD));

            assertTrue(file.exists());
            assertEquals(tempDir.resolve("report.docx").toString(), file.getPath());
        }

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () ->
                                    repository.locate(
                                            TemplateName.parse(
                                                    "missing.docx",
                                                    TemplateName.TemplateKind.WORD)));

            assertEquals("missing.docx", exception.getTemplateName());
        }

        @Test
        @DisplayName("异常消息应该包含模板名但不得包含服务器绝对路径")
        void exceptionMessageShouldNotLeakAbsolutePath() {
            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () ->
                                    repository.locate(
                                            TemplateName.parse(
                                                    "missing.docx",
                                                    TemplateName.TemplateKind.WORD)));

            assertTrue(exception.getMessage().contains("missing.docx"));
            assertFalse(exception.getMessage().contains(tempDir.toString()));
        }

        @Test
        @DisplayName("目标是目录时应该视为不存在")
        void shouldThrowExceptionWhenTargetIsDirectory() throws IOException {
            Files.createDirectory(tempDir.resolve("sub.docx"));

            assertThrows(
                    TemplateNotFoundException.class,
                    () ->
                            repository.locate(
                                    TemplateName.parse(
                                            "sub.docx", TemplateName.TemplateKind.WORD)));
        }
    }

    @Nested
    @DisplayName("read 测试")
    class ReadTest {

        @Test
        @DisplayName("成功读取模板内容")
        void shouldReadTemplateContent() throws IOException {
            byte[] content = "template content".getBytes();
            Files.write(tempDir.resolve("report.docx"), content);

            byte[] result =
                    repository.read(
                            TemplateName.parse("report.docx", TemplateName.TemplateKind.WORD));

            assertArrayEquals(content, result);
        }

        @Test
        @DisplayName("文件不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenFileNotExist() {
            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () ->
                                    repository.read(
                                            TemplateName.parse(
                                                    "non-existent.docx",
                                                    TemplateName.TemplateKind.WORD)));

            assertEquals("non-existent.docx", exception.getTemplateName());
        }
    }

    @Nested
    @DisplayName("list 测试")
    class ListTest {

        @Test
        @DisplayName("应该返回所有模板文件")
        void shouldReturnAllTemplates() throws IOException {
            Files.createFile(tempDir.resolve("template1.docx"));
            Files.createFile(tempDir.resolve("template2.xlsx"));
            Files.createFile(tempDir.resolve("template3.docx"));

            List<String> templates = repository.list();

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

            List<String> templates = repository.list();

            assertEquals(1, templates.size());
            assertTrue(templates.contains("template.docx"));
        }

        @Test
        @DisplayName("空目录应该返回空列表")
        void shouldReturnEmptyListForEmptyDirectory() throws IOException {
            List<String> templates = repository.list();

            assertNotNull(templates);
            assertTrue(templates.isEmpty());
        }

        @Test
        @DisplayName("目录不存在时应该返回空列表")
        void shouldReturnEmptyListWhenDirectoryNotExist() throws IOException {
            properties.setTemplatePath(tempDir.resolve("non-existent").toString());
            repository = new TemplateRepository(properties);

            List<String> templates = repository.list();

            assertNotNull(templates);
            assertTrue(templates.isEmpty());
        }
    }

    @Nested
    @DisplayName("与 TemplateWriter 的闭环测试")
    class UploadToLocateTest {

        @Test
        @DisplayName("上传落盘后的名字一定能被读侧解析")
        void uploadedTemplateShouldBeLocatable() throws IOException {
            TemplateWriter writer = new TemplateWriter(repository);
            org.springframework.mock.web.MockMultipartFile file =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file",
                            "report:final<v1>.docx",
                            "application/octet-stream",
                            "x".getBytes());

            String savedName = writer.upload(file);

            File located =
                    repository.locate(
                            TemplateName.parse(savedName, TemplateName.TemplateKind.WORD));
            assertTrue(located.exists());
        }
    }
}
