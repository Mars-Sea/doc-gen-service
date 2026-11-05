package io.github.marssea.docgen.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.marssea.docgen.template.TemplateInfo;
import io.github.marssea.docgen.template.TemplateRepository;
import io.github.marssea.docgen.template.TemplateWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** TemplateController 单元测试 */
@DisplayName("TemplateController 测试")
@WebMvcTest(TemplateController.class)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TemplateWriter templateWriter;

    @MockBean private TemplateRepository templateRepository;

    @Nested
    @DisplayName("POST /api/v1/template/upload 测试")
    class UploadTemplateTest {

        @Test
        @DisplayName("成功上传 Word 模板")
        void shouldUploadWordTemplate() throws Exception {
            when(templateWriter.upload(any())).thenReturn("test-template.docx");

            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test-template.docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "word content".getBytes());

            mockMvc.perform(multipart("/api/v1/template/upload").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.fileName").value("test-template.docx"));
        }

        @Test
        @DisplayName("成功上传 Excel 模板")
        void shouldUploadExcelTemplate() throws Exception {
            when(templateWriter.upload(any())).thenReturn("test-template.xlsx");

            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test-template.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "excel content".getBytes());

            mockMvc.perform(multipart("/api/v1/template/upload").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.fileName").value("test-template.xlsx"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/template/list 测试")
    class ListTemplatesTest {

        @Test
        @DisplayName("成功获取模板列表")
        void shouldReturnTemplateList() throws Exception {
            List<TemplateInfo> templates =
                    List.of(
                            TemplateInfo.of(
                                    "report.docx", 2048L, Instant.parse("2026-10-09T08:54:16Z")),
                            TemplateInfo.of(
                                    "data.xlsx", 1024L, Instant.parse("2026-10-08T03:00:00Z")),
                            TemplateInfo.of(
                                    "certificate.docx", 0L, Instant.parse("2026-10-07T23:59:59Z")));
            when(templateRepository.list()).thenReturn(templates);

            mockMvc.perform(get("/api/v1/template/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.count").value(3))
                    .andExpect(jsonPath("$.templates[0].name").value("report.docx"))
                    .andExpect(jsonPath("$.templates[0].size").value(2048))
                    .andExpect(
                            jsonPath("$.templates[0].lastModified").value("2026-10-09T08:54:16Z"))
                    .andExpect(jsonPath("$.templates[1].name").value("data.xlsx"))
                    .andExpect(jsonPath("$.templates[1].size").value(1024))
                    .andExpect(jsonPath("$.templates[2].name").value("certificate.docx"))
                    .andExpect(jsonPath("$.templates[2].size").value(0));
        }

        @Test
        @DisplayName("时间截断到秒，不带纳秒尾数")
        void shouldTruncateTimeToSeconds() throws Exception {
            // 文件系统给出纳秒精度，保留尾数会让每次列表响应都不同、无法比对
            when(templateRepository.list())
                    .thenReturn(
                            List.of(
                                    TemplateInfo.of(
                                            "report.docx",
                                            1L,
                                            Instant.parse("2026-10-09T08:54:16.161164400Z"))));

            mockMvc.perform(get("/api/v1/template/list"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.templates[0].lastModified").value("2026-10-09T08:54:16Z"));
        }

        @Test
        @DisplayName("时间是 ISO-8601 UTC，不带服务器本地时区偏移")
        void shouldSerializeTimeAsUtc() throws Exception {
            when(templateRepository.list())
                    .thenReturn(
                            List.of(
                                    TemplateInfo.of(
                                            "report.docx",
                                            1L,
                                            Instant.parse("2026-10-09T16:54:16+08:00"))));

            // 同一时刻用带偏移的方式构造，序列化后必须是 UTC 的 Z 形式，否则跨时区调用方会读错
            mockMvc.perform(get("/api/v1/template/list"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.templates[0].lastModified").value("2026-10-09T08:54:16Z"));
        }

        @Test
        @DisplayName("空模板目录时返回空列表")
        void shouldReturnEmptyListWhenNoTemplates() throws Exception {
            when(templateRepository.list()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/template/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.count").value(0))
                    .andExpect(jsonPath("$.templates").isEmpty());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/template/{templateName} 测试")
    class DeleteTemplateTest {

        @Test
        @DisplayName("成功删除模板文件")
        void shouldDeleteExistingTemplate() throws Exception {
            when(templateWriter.delete(any())).thenReturn(true);

            mockMvc.perform(delete("/api/v1/template/old-template.docx"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("模板删除成功"));
        }

        @Test
        @DisplayName("删除不存在的模板返回不存在提示")
        void shouldReturnNotFoundWhenTemplateDoesNotExist() throws Exception {
            when(templateWriter.delete(any())).thenReturn(false);

            mockMvc.perform(delete("/api/v1/template/missing.docx"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("模板文件不存在"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/template/download/{templateName} 测试")
    class DownloadTemplateTest {

        @Test
        @DisplayName("成功下载 Word 模板")
        void shouldDownloadWordTemplate() throws Exception {
            byte[] content = "word template content".getBytes();
            when(templateRepository.read(any())).thenReturn(content);

            mockMvc.perform(get("/api/v1/template/download/report.docx"))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .andExpect(
                            header().string(
                                            "Content-Disposition",
                                            org.hamcrest.Matchers.containsString("report.docx")))
                    .andExpect(content().bytes(content));
        }

        @Test
        @DisplayName("成功下载 Excel 模板")
        void shouldDownloadExcelTemplate() throws Exception {
            byte[] content = "excel template content".getBytes();
            when(templateRepository.read(any())).thenReturn(content);

            mockMvc.perform(get("/api/v1/template/download/data.xlsx"))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(
                            header().string(
                                            "Content-Disposition",
                                            org.hamcrest.Matchers.containsString("data.xlsx")))
                    .andExpect(content().bytes(content));
        }
    }
}
