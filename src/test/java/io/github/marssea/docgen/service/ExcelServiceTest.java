package io.github.marssea.docgen.service;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.sun.net.httpserver.HttpServer;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.PayloadTooLargeException;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import io.github.marssea.docgen.template.TemplateRepository;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import io.github.marssea.docgen.util.QrCodePayloadConverter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** ExcelService 单元测试 */
@DisplayName("ExcelService 测试")
class ExcelServiceTest {

    private static final byte[] TEST_PNG_BYTES =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @TempDir Path tempDir;

    private ExcelService excelService;
    private DocGenProperties properties;
    private ImagePayloadConverter imagePayloadConverter;
    private QrCodePayloadConverter qrCodePayloadConverter;
    private TemplateRepository templateRepository;

    @BeforeEach
    void setUp() {
        properties = new DocGenProperties();
        properties.setTemplatePath(tempDir.toString());
        imagePayloadConverter = new ImagePayloadConverter();
        qrCodePayloadConverter = new QrCodePayloadConverter();
        templateRepository = new TemplateRepository(properties);
        excelService =
                new ExcelService(
                        templateRepository,
                        imagePayloadConverter,
                        qrCodePayloadConverter,
                        properties);
    }

    @Nested
    @DisplayName("generateExcel 测试")
    class GenerateExcelTest {

        @Test
        @DisplayName("成功生成 Excel 文档")
        void shouldGenerateExcelDocument() {
            List<String> headers = Arrays.asList("姓名", "年龄", "城市");
            List<List<Object>> data =
                    Arrays.asList(
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
            List<List<Object>> data = Arrays.asList(Arrays.asList("A", "B"));

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
            List<List<Object>> data = Arrays.asList(Arrays.asList("Value1"));

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

        @Test
        @DisplayName("行数超限时应抛 PayloadTooLargeException")
        void shouldRejectTooManyRows() {
            properties.getLimits().setMaxListRows(2);

            List<String> headers = Arrays.asList("H1", "H2");
            List<List<Object>> data = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                data.add(Arrays.asList("a" + i, "b" + i));
            }

            PayloadTooLargeException exception =
                    assertThrows(
                            PayloadTooLargeException.class,
                            () -> excelService.generateExcel("S", headers, data));

            assertEquals(3, exception.getActual());
            assertEquals(2, exception.getLimit());
            assertEquals("data 行数", exception.getDimension());
        }
    }

    @Nested
    @DisplayName("fillTemplate 测试")
    class FillTemplateTest {

        @Test
        @DisplayName("模板不存在时应该抛出 TemplateNotFoundException")
        void shouldThrowExceptionWhenTemplateNotFound() {
            Map<String, Object> data = Map.of("title", "Test");

            TemplateNotFoundException exception =
                    assertThrows(
                            TemplateNotFoundException.class,
                            () -> excelService.fillTemplate("non-existent.xlsx", data, null));

            assertEquals("non-existent.xlsx", exception.getTemplateName());
        }

        @Test
        @DisplayName("非法模板名应该抛出 IllegalArgumentException")
        void shouldThrowExceptionForInvalidTemplateName() {
            Map<String, Object> data = Map.of("title", "Test");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> excelService.fillTemplate("../etc/passwd", data, null));

            assertThrows(
                    IllegalArgumentException.class,
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
        @DisplayName("模板填充应支持图片载荷")
        void shouldFillExcelTemplateWithImagePayload() throws Exception {
            Path templatePath = tempDir.resolve("image-template.xlsx");
            createImageExcelTemplate(templatePath);

            HttpServer server = startImageServer();
            try {
                Map<String, Object> imagePayload = new HashMap<>();
                imagePayload.put("type", "image");
                imagePayload.put(
                        "url", "http://localhost:" + server.getAddress().getPort() + "/logo.png");
                imagePayload.put("format", "png");
                imagePayload.put("width", 80);
                imagePayload.put("height", 40);

                Map<String, Object> data = new HashMap<>();
                data.put("title", "Image Report");
                data.put("logo", imagePayload);

                byte[] result = excelService.fillTemplate("image-template.xlsx", data, null);

                try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                    assertEquals(
                            "Image Report",
                            workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
                    List<XSSFPictureData> pictures = workbook.getAllPictures();
                    assertEquals(1, pictures.size());
                    assertArrayEquals(TEST_PNG_BYTES, pictures.get(0).getData());
                }
            } finally {
                server.stop(0);
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

    /** 创建简单的 Excel 模板用于测试 */
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

    /** 创建包含图片占位符的 Excel 模板用于测试 */
    @Test
    @DisplayName("模板填充的列表数据应支持图片载荷")
    void shouldFillExcelTemplateWithImagePayloadInsideListData() throws Exception {
        Path templatePath = tempDir.resolve("list-image-template.xlsx");
        createListImageExcelTemplate(templatePath);

        HttpServer server = startImageServer();
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", "Item " + i);
                Map<String, Object> logo = new HashMap<>();
                logo.put("type", "image");
                logo.put("url", "http://localhost:" + server.getAddress().getPort() + "/logo.png");
                logo.put("format", "png");
                logo.put("width", 80);
                logo.put("height", 40);
                row.put("logo", logo);
                rows.add(row);
            }

            byte[] result =
                    excelService.fillTemplate(
                            "list-image-template.xlsx", null, Map.of("items", rows));

            // 列表填充与单值填充在 EasyExcel 内部走同一条 fillImage 链路，
            // 若 listData 分支未做载荷转换，这里会是 0 张图
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {}
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("模板填充的列表数据应支持二维码载荷")
    void shouldFillExcelTemplateWithQrCodePayloadInsideListData() throws Exception {
        Path templatePath = tempDir.resolve("list-qr-template.xlsx");
        createListImageExcelTemplate(templatePath);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", "Item " + i);
            row.put("logo", Map.of("type", "qrcode", "content", "https://example.com/order/" + i));
            rows.add(row);
        }

        byte[] result =
                excelService.fillTemplate("list-qr-template.xlsx", null, Map.of("items", rows));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(2, workbook.getAllPictures().size());
        }
    }

    @Test
    @DisplayName("相同图片在多行重复出现时只下载一次")
    void shouldDownloadRepeatedImageOnlyOnce() throws Exception {
        Path templatePath = tempDir.resolve("list-image-template.xlsx");
        createListImageExcelTemplate(templatePath);

        int[] requestCount = {0};
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/logo.png",
                exchange -> {
                    requestCount[0]++;
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, TEST_PNG_BYTES.length);
                    exchange.getResponseBody().write(TEST_PNG_BYTES);
                    exchange.close();
                });
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/logo.png";
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", "Item " + i);
                row.put("logo", Map.of("type", "image", "url", url, "format", "png"));
                rows.add(row);
            }

            excelService.fillTemplate("list-image-template.xlsx", null, Map.of("items", rows));

            // 五行的 logo 是同一个 URL，缓存命中后只应发生一次 HTTP 请求
            assertEquals(1, requestCount[0], "同一 URL 的图片应只下载一次");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("不同 URL 指向相同字节时堆内只保留一份图片")
    void shouldDeduplicateIdenticalImageBytesFromDifferentUrls() throws Exception {
        Path templatePath = tempDir.resolve("list-image-template.xlsx");
        createListImageExcelTemplate(templatePath);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        // 两个路径返回完全相同的字节，模拟 CDN 多域名指向同一资源
        for (String path : List.of("/a.png", "/b.png")) {
            server.createContext(
                    path,
                    exchange -> {
                        exchange.getResponseHeaders().add("Content-Type", "image/png");
                        exchange.sendResponseHeaders(200, TEST_PNG_BYTES.length);
                        exchange.getResponseBody().write(TEST_PNG_BYTES);
                        exchange.close();
                    });
        }
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String path : List.of("/a.png", "/b.png")) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", path);
                row.put("logo", Map.of("type", "image", "url", base + path, "format", "png"));
                rows.add(row);
            }

            // 两个不同 URL、相同字节：填充应正常完成（去重不能影响渲染结果）
            byte[] result =
                    excelService.fillTemplate(
                            "list-image-template.xlsx", null, Map.of("items", rows));

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertEquals(2, workbook.getAllPictures().size());
            }
        } finally {
            server.stop(0);
        }
    }

    @Nested
    @DisplayName("限额校验测试")
    class LimitsTest {

        @Test
        @DisplayName("未配置限额时不应拦下任何数据量")
        void shouldAllowAnySizeWhenLimitsDisabled() throws Exception {
            Path templatePath = tempDir.resolve("list-image-template.xlsx");
            createListImageExcelTemplate(templatePath);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rows.add(Map.of("name", "Item " + i));
            }

            // 默认 limits 全部为 -1，200 行应正常通过
            byte[] result =
                    excelService.fillTemplate(
                            "list-image-template.xlsx", null, Map.of("items", rows));
            assertTrue(result.length > 0);
        }

        @Test
        @DisplayName("列表行数超限时应抛 PayloadTooLargeException")
        void shouldRejectTooManyListRows() throws Exception {
            Path templatePath = tempDir.resolve("list-image-template.xlsx");
            createListImageExcelTemplate(templatePath);
            properties.getLimits().setMaxListRows(3);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                rows.add(Map.of("name", "Item " + i));
            }

            PayloadTooLargeException exception =
                    assertThrows(
                            PayloadTooLargeException.class,
                            () ->
                                    excelService.fillTemplate(
                                            "list-image-template.xlsx",
                                            null,
                                            Map.of("items", rows)));

            assertEquals(4, exception.getActual());
            assertEquals(3, exception.getLimit());
            assertTrue(exception.getDimension().contains("items"));
        }

        @Test
        @DisplayName("单值字段数超限时应抛 PayloadTooLargeException")
        void shouldRejectTooManyDataFields() throws Exception {
            Path templatePath = tempDir.resolve("image-template.xlsx");
            createImageExcelTemplate(templatePath);
            properties.getLimits().setMaxDataFields(1);

            Map<String, Object> data = new HashMap<>();
            data.put("title", "T");
            data.put("date", "D");

            assertThrows(
                    PayloadTooLargeException.class,
                    () -> excelService.fillTemplate("image-template.xlsx", data, null));
        }

        @Test
        @DisplayName("图片张数超限时应抛 PayloadTooLargeException")
        void shouldRejectTooManyImages() throws Exception {
            Path templatePath = tempDir.resolve("list-image-template.xlsx");
            createListImageExcelTemplate(templatePath);
            properties.getLimits().setMaxImageCount(1);

            HttpServer server = startImageServer();
            try {
                String url = "http://localhost:" + server.getAddress().getPort() + "/logo.png";
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", "Item " + i);
                    row.put("logo", Map.of("type", "image", "url", url, "format", "png"));
                    rows.add(row);
                }

                PayloadTooLargeException exception =
                        assertThrows(
                                PayloadTooLargeException.class,
                                () ->
                                        excelService.fillTemplate(
                                                "list-image-template.xlsx",
                                                null,
                                                Map.of("items", rows)));

                assertEquals(2, exception.getActual());
            } finally {
                server.stop(0);
            }
        }
    }

    /** 创建含 {title} / {logo} 单值填充单元格的 Excel 模板 */
    @Nested
    @DisplayName("forceNewRow 参数测试")
    class ForceNewRowTest {

        @Test
        @DisplayName("不传 forceNewRow 时按 true 处理（走共享字符串表，产出可正常打开）")
        void shouldDefaultToTrueWhenNull() throws Exception {
            Path templatePath = tempDir.resolve("list-image-template.xlsx");
            createListImageExcelTemplate(templatePath);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                rows.add(Map.of("name", "Item " + i));
            }

            byte[] result =
                    excelService.fillTemplate(
                            "list-image-template.xlsx",
                            null,
                            Map.of("items", rows),
                            null,
                            null,
                            null);

            assertTrue(result.length > 0);
        }

        @Test
        @DisplayName("forceNewRow=false 与 =true 都能产出有效文档")
        void shouldProduceValidDocumentInBothModes() throws Exception {
            Path templatePath = tempDir.resolve("list-image-template.xlsx");
            createListImageExcelTemplate(templatePath);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                rows.add(Map.of("name", "Item " + i));
            }

            byte[] withoutForce =
                    excelService.fillTemplate(
                            "list-image-template.xlsx",
                            null,
                            Map.of("items", rows),
                            null,
                            null,
                            false);
            byte[] withForce =
                    excelService.fillTemplate(
                            "list-image-template.xlsx",
                            null,
                            Map.of("items", rows),
                            null,
                            null,
                            true);

            for (byte[] bytes : List.of(withoutForce, withForce)) {
                try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    assertNotNull(workbook.getSheetAt(0));
                }
            }
        }
    }

    private void createImageExcelTemplate(Path path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path.toFile());
                ExcelWriter excelWriter = EasyExcel.write(out).build()) {

            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();

            List<List<Object>> data = new ArrayList<>();
            data.add(Arrays.asList("Title", "{title}"));
            data.add(Arrays.asList("Logo", "{logo}"));

            excelWriter.write(data, writeSheet);
        }
    }

    /** 创建含 {.name} / {.logo} 列表填充行的 Excel 模板 */
    private void createListImageExcelTemplate(Path path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path.toFile());
                ExcelWriter excelWriter = EasyExcel.write(out).build()) {

            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();

            List<List<Object>> data = new ArrayList<>();
            data.add(Arrays.asList("Name", "{.name}"));
            data.add(Arrays.asList("Logo", "{.logo}"));

            excelWriter.write(data, writeSheet);
        }
    }

    /** 启动测试图片 HTTP 服务 */
    private HttpServer startImageServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/logo.png",
                exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, TEST_PNG_BYTES.length);
                    exchange.getResponseBody().write(TEST_PNG_BYTES);
                    exchange.close();
                });
        server.start();
        return server;
    }
}
