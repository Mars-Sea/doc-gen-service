package io.github.marssea.docgen.service;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelService 单元测试
 */
@DisplayName("ExcelService 测试")
class ExcelServiceTest {

    @TempDir
    Path tempDir;

    private ExcelService excelService;
    private DocGenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        excelService = new ExcelService(properties);
    }

    @Nested
    @DisplayName("generateExcel 测试")
    class GenerateExcelTest {

        @Test
        @DisplayName("成功生成 Excel 文档")
        void shouldGenerateExcelDocument() {
            List<String> headers = Arrays.asList("姓名", "年龄", "城市");
            List<List<Object>> data = Arrays.asList(
                    Arrays.asList("张三", 25, "北京"),
                    Arrays.asList("李四", 30, "上海"),
                    Arrays.asList("王五", 28, "广州"));

            byte[] result = excelService.generateExcel("员工列表", headers, data);

            assertNotNull(result);
            assertTrue(result.length > 0);

            // 验证生成的是有效的 Excel 文档
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertNotNull(workbook);
                assertEquals(1, workbook.getNumberOfSheets());
                assertEquals("员工列表", workbook.getSheetAt(0).getSheetName());
            } catch (IOException e) {
                fail("生成的不是有效的 Excel 文档: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("sheetName 为 null 时应该使用默认值 Sheet1")
        void shouldUseDefaultSheetNameWhenNull() {
            List<String> headers = Arrays.asList("Column1", "Column2");
            List<List<Object>> data = Arrays.asList(
                    Arrays.asList("A", "B"));

            byte[] result = excelService.generateExcel(null, headers, data);

            assertNotNull(result);

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertEquals("Sheet1", workbook.getSheetAt(0).getSheetName());
            } catch (IOException e) {
                fail("生成的不是有效的 Excel 文档: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("sheetName 为空字符串时应该使用默认值 Sheet1")
        void shouldUseDefaultSheetNameWhenEmpty() {
            List<String> headers = Arrays.asList("Column1");
            List<List<Object>> data = Arrays.asList(
                    Arrays.asList("Value1"));

            byte[] result = excelService.generateExcel("", headers, data);

            assertNotNull(result);

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertEquals("Sheet1", workbook.getSheetAt(0).getSheetName());
            } catch (IOException e) {
                fail("生成的不是有效的 Excel 文档: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("空数据列表应该只生成表头")
        void shouldGenerateHeaderOnlyForEmptyData() {
            List<String> headers = Arrays.asList("Header1", "Header2", "Header3");
            List<List<Object>> data = new ArrayList<>();

            byte[] result = excelService.generateExcel("TestSheet", headers, data);

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Nested
    @DisplayName("fillTemplate 测试")
    class FillTemplateTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            Map<String, Object> data = Map.of("title", "Test");

            TemplateNotFoundException exception = assertThrows(
                    TemplateNotFoundException.class,
                    () -> excelService.fillTemplate("non-existent.xlsx", data, null));

            assertEquals("non-existent.xlsx", exception.getTemplateName());
        }

        @Test
        @DisplayName("非法模板名应该抛出 IllegalArgumentException")
        void shouldThrowExceptionForInvalidTemplateName() {
            Map<String, Object> data = Map.of("title", "Test");

            assertThrows(IllegalArgumentException.class,
                    () -> excelService.fillTemplate("../etc/passwd", data, null));

            assertThrows(IllegalArgumentException.class,
                    () -> excelService.fillTemplate("template.docx", data, null));
        }

        @Test
        @DisplayName("成功填充 Excel 模板")
        void shouldFillExcelTemplate() throws Exception {
            // 创建测试模板
            Path templatePath = tempDir.resolve("test-template.xlsx");
            createSimpleExcelTemplate(templatePath);

            Map<String, Object> data = new HashMap<>();
            data.put("title", "Test Report");
            data.put("date", "2026-01-07");

            byte[] result = excelService.fillTemplate("test-template.xlsx", data, null);

            assertNotNull(result);
            assertTrue(result.length > 0);

            // 验证生成的是有效的 Excel 文档
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertNotNull(workbook);
            }
        }

        @Test
        @DisplayName("data 和 listData 都为 null 时应该返回原模板")
        void shouldReturnOriginalTemplateWhenDataIsNull() throws Exception {
            Path templatePath = tempDir.resolve("empty-data.xlsx");
            createSimpleExcelTemplate(templatePath);

            byte[] result = excelService.fillTemplate("empty-data.xlsx", null, null);

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    /**
     * 创建简单的 Excel 模板用于测试
     */
    private void createSimpleExcelTemplate(Path path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path.toFile());
                ExcelWriter excelWriter = EasyExcel.write(out).build()) {

            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();

            // 写入一些初始数据作为模板结构
            List<List<Object>> data = new ArrayList<>();
            data.add(Arrays.asList("Title", "{title}"));
            data.add(Arrays.asList("Date", "{date}"));

            excelWriter.write(data, writeSheet);
        }
    }
}
