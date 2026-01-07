package io.github.marssea.docgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marssea.docgen.model.ExcelGenRequest;
import io.github.marssea.docgen.model.WordGenRequest;
import io.github.marssea.docgen.model.WordBatchRequest;
import io.github.marssea.docgen.service.ExcelService;
import io.github.marssea.docgen.service.WordService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DocController 集成测试
 */
@DisplayName("DocController 测试")
@WebMvcTest(DocController.class)
class DocControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WordService wordService;

    @MockBean
    private ExcelService excelService;

    @Nested
    @DisplayName("POST /api/v1/doc/word 测试")
    class GenerateWordTest {

        @Test
        @DisplayName("成功生成 Word 文档")
        void shouldGenerateWordDocument() throws Exception {
            byte[] mockResult = "mock word document content".getBytes();
            when(wordService.generateWord(anyString(), any())).thenReturn(mockResult);

            WordGenRequest request = new WordGenRequest();
            request.setTemplateName("test-template.docx");
            request.setData(Map.of("title", "Test Title"));
            request.setFileName("output");

            mockMvc.perform(post("/api/v1/doc/word")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("attachment")))
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        }

        @Test
        @DisplayName("templateName 为空时应该返回 400")
        void shouldReturn400WhenTemplateNameIsBlank() throws Exception {
            WordGenRequest request = new WordGenRequest();
            request.setTemplateName("");
            request.setData(Map.of("title", "Test"));

            mockMvc.perform(post("/api/v1/doc/word")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("fileName 为 null 时应该使用默认文件名")
        void shouldUseDefaultFileNameWhenNull() throws Exception {
            byte[] mockResult = "mock word document".getBytes();
            when(wordService.generateWord(anyString(), any())).thenReturn(mockResult);

            WordGenRequest request = new WordGenRequest();
            request.setTemplateName("test-template.docx");
            request.setData(Map.of("title", "Test"));
            // fileName is null

            mockMvc.perform(post("/api/v1/doc/word")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("generated.docx")));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/doc/word/batch 测试")
    class BatchGenerateWordTest {

        @Test
        @DisplayName("成功批量生成 Word 文档")
        void shouldBatchGenerateWordDocument() throws Exception {
            byte[] mockResult = "mock batch word document".getBytes();
            when(wordService.generateBatch(anyString(), anyList())).thenReturn(mockResult);

            WordBatchRequest request = new WordBatchRequest();
            request.setTemplateName("batch-template.docx");
            request.setDataList(Arrays.asList(
                    Map.of("name", "Page 1"),
                    Map.of("name", "Page 2")));
            request.setFileName("batch_output");

            mockMvc.perform(post("/api/v1/doc/word/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("batch_output.docx")));
        }

        @Test
        @DisplayName("dataList 为空时应该返回 400")
        void shouldReturn400WhenDataListIsEmpty() throws Exception {
            WordBatchRequest request = new WordBatchRequest();
            request.setTemplateName("batch-template.docx");
            request.setDataList(new ArrayList<>());

            mockMvc.perform(post("/api/v1/doc/word/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/doc/excel 测试")
    class GenerateExcelTest {

        @Test
        @DisplayName("成功生成 Excel 文档")
        void shouldGenerateExcelDocument() throws Exception {
            byte[] mockResult = "mock excel document".getBytes();
            when(excelService.generateExcel(anyString(), anyList(), anyList())).thenReturn(mockResult);

            ExcelGenRequest request = new ExcelGenRequest();
            request.setSheetName("TestSheet");
            request.setHeaders(Arrays.asList("Name", "Age"));
            request.setData(Arrays.asList(
                    Arrays.asList("John", 25),
                    Arrays.asList("Jane", 30)));
            request.setFileName("excel_output");

            mockMvc.perform(post("/api/v1/doc/excel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }

        @Test
        @DisplayName("headers 为空时应该返回 400")
        void shouldReturn400WhenHeadersIsEmpty() throws Exception {
            ExcelGenRequest request = new ExcelGenRequest();
            request.setSheetName("TestSheet");
            request.setHeaders(new ArrayList<>());
            request.setData(Arrays.asList(Arrays.asList("A", "B")));

            mockMvc.perform(post("/api/v1/doc/excel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("data 为空时应该返回 400")
        void shouldReturn400WhenDataIsEmpty() throws Exception {
            ExcelGenRequest request = new ExcelGenRequest();
            request.setSheetName("TestSheet");
            request.setHeaders(Arrays.asList("Col1", "Col2"));
            request.setData(new ArrayList<>());

            mockMvc.perform(post("/api/v1/doc/excel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
