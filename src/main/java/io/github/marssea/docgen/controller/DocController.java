package io.github.marssea.docgen.controller;

import io.github.marssea.docgen.model.WordBatchRequest;
import io.github.marssea.docgen.model.WordGenRequest;
import io.github.marssea.docgen.model.ExcelFillRequest;
import io.github.marssea.docgen.model.ExcelGenRequest;
import io.github.marssea.docgen.service.ExcelService;
import io.github.marssea.docgen.service.WordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 文档生成 API 控制器
 * <p>
 * 提供 RESTful API 接口，支持根据模板和数据动态生成 Word 和 Excel 文档。
 * 生成的文档以二进制流形式返回，可直接被客户端下载。
 * <p>
 * 接口路径: /api/v1/doc
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "Document Generation", description = "文档生成相关接口")
@RestController
@RequestMapping("/api/v1/doc")
@RequiredArgsConstructor
public class DocController {

        private final WordService wordService;
        private final ExcelService excelService;

        /**
         * 生成 Word 文档
         * <p>
         * 根据指定的模板名称和渲染数据，生成 Word 文档并返回文件流。
         * 支持自定义输出文件名，包括中文文件名。
         *
         * @param request 包含模板名称、渲染数据和可选文件名的请求体
         * @return 生成的 .docx 文件二进制流
         * @throws IOException 文件处理异常
         */
        @Operation(summary = "生成 Word 文档", description = "根据模板名称和数据生成 Word 文档。支持自定义输出文件名（包括中文）。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "文档生成成功", content = @Content(mediaType = "application/octet-stream")),
                        @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "422", description = "模板文件不存在", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        @PostMapping("/word")
        public ResponseEntity<byte[]> generateWord(@Valid @RequestBody WordGenRequest request) throws IOException {
                log.info("Received word generation request, template: {}, fileName: {}",
                                request.getTemplateName(), request.getFileName());

                byte[] bytes = wordService.generateWord(request.getTemplateName(), request.getData());

                if (bytes == null || bytes.length == 0) {
                        throw new IllegalStateException("Generated word document is empty");
                }

                String fileName = request.getFileName();
                if (fileName == null || fileName.isBlank()) {
                        fileName = "generated";
                }

                // 清理非法字符
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

                String encodedFileName = URLEncoder
                                .encode(fileName + ".docx", StandardCharsets.UTF_8)
                                .replace("+", "%20");

                log.info("Word document generated successfully, size: {} bytes, fileName: {}",
                                bytes.length, fileName);

                return ResponseEntity.ok()
                                .contentType(Objects.requireNonNull(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + fileName + ".docx\"; filename*=UTF-8''"
                                                                + encodedFileName)
                                .contentLength(bytes.length)
                                .body(bytes);
        }

        /**
         * 批量生成 Word 文档
         * <p>
         * 使用同一模板渲染多条数据，每条数据生成一页，合并为单个文档。
         *
         * @param request 包含模板名称、数据列表和可选文件名的请求体
         * @return 生成的 .docx 文件二进制流
         * @throws IOException 文件处理异常
         */
        @Operation(summary = "批量生成 Word 文档", description = "使用同一模板渲染多条数据，每条数据生成一页，合并为单个文档。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "文档生成成功", content = @Content(mediaType = "application/octet-stream")),
                        @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "422", description = "模板文件不存在", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        @PostMapping("/word/batch")
        public ResponseEntity<byte[]> batchGenerateWord(@Valid @RequestBody WordBatchRequest request)
                        throws IOException {
                log.info("Received batch word generation request, template: {}, dataCount: {}, fileName: {}",
                                request.getTemplateName(),
                                request.getDataList() != null ? request.getDataList().size() : 0,
                                request.getFileName());

                byte[] bytes = wordService.generateBatch(request.getTemplateName(), request.getDataList());

                if (bytes == null || bytes.length == 0) {
                        throw new IllegalStateException("Generated batch word document is empty");
                }

                String fileName = request.getFileName();
                if (fileName == null || fileName.isBlank()) {
                        fileName = "batch_generated";
                }

                // 清理非法字符
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

                String encodedFileName = URLEncoder
                                .encode(fileName + ".docx", StandardCharsets.UTF_8)
                                .replace("+", "%20");

                log.info("Batch word document generated successfully, pages: {}, size: {} bytes, fileName: {}",
                                request.getDataList().size(), bytes.length, fileName);

                return ResponseEntity.ok()
                                .contentType(Objects.requireNonNull(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + fileName + ".docx\"; filename*=UTF-8''"
                                                                + encodedFileName)
                                .contentLength(bytes.length)
                                .body(bytes);
        }

        /**
         * 生成 Excel 文档
         * <p>
         * 根据指定的表头和数据，动态生成 Excel 文档并返回文件流。
         * 支持自定义输出文件名，包括中文文件名。
         *
         * @param request 包含表头、数据和可选文件名的请求体
         * @return 生成的 .xlsx 文件二进制流
         */
        @Operation(summary = "生成 Excel 文档", description = "根据表头和数据动态生成 Excel 文档。支持自定义输出文件名（包括中文）。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "文档生成成功", content = @Content(mediaType = "application/octet-stream")),
                        @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        @PostMapping("/excel")
        public ResponseEntity<byte[]> generateExcel(@Valid @RequestBody ExcelGenRequest request) {
                log.info("Received excel generation request, sheetName: {}, columns: {}, rows: {}, fileName: {}",
                                request.getSheetName(),
                                request.getHeaders() != null ? request.getHeaders().size() : 0,
                                request.getData() != null ? request.getData().size() : 0,
                                request.getFileName());

                byte[] bytes = excelService.generateExcel(
                                request.getSheetName(),
                                request.getHeaders(),
                                request.getData());

                if (bytes == null || bytes.length == 0) {
                        throw new IllegalStateException("Generated excel document is empty");
                }

                String fileName = request.getFileName();
                if (fileName == null || fileName.isBlank()) {
                        fileName = "generated";
                }

                // 清理非法字符
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

                String encodedFileName = URLEncoder
                                .encode(fileName + ".xlsx", StandardCharsets.UTF_8)
                                .replace("+", "%20");

                log.info("Excel document generated successfully, size: {} bytes, fileName: {}",
                                bytes.length, fileName);

                return ResponseEntity.ok()
                                .contentType(Objects.requireNonNull(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + fileName + ".xlsx\"; filename*=UTF-8''"
                                                                + encodedFileName)
                                .contentLength(bytes.length)
                                .body(bytes);
        }

        /**
         * 基于模板填充 Excel 文档
         * <p>
         * 根据指定的模板和数据，填充生成 Excel 文档并返回文件流。
         * 支持单值变量替换和列表数据循环填充。
         *
         * @param request 包含模板名称、单值数据、列表数据和可选文件名的请求体
         * @return 生成的 .xlsx 文件二进制流
         */
        @Operation(summary = "基于模板填充 Excel 文档", description = "根据 Excel 模板和数据生成文档。支持 {variable} 单值替换和 {.field} 列表循环。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "文档生成成功", content = @Content(mediaType = "application/octet-stream")),
                        @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "422", description = "模板文件不存在", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        @PostMapping("/excel/fill")
        public ResponseEntity<byte[]> fillExcelTemplate(@Valid @RequestBody ExcelFillRequest request) {
                log.info("Received excel fill request, template: {}, fileName: {}",
                                request.getTemplateName(), request.getFileName());

                byte[] bytes = excelService.fillTemplate(
                                request.getTemplateName(),
                                request.getData(),
                                request.getListData());

                if (bytes == null || bytes.length == 0) {
                        throw new IllegalStateException("Filled excel document is empty");
                }

                String fileName = request.getFileName();
                if (fileName == null || fileName.isBlank()) {
                        fileName = "filled";
                }

                // 清理非法字符
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

                String encodedFileName = URLEncoder
                                .encode(fileName + ".xlsx", StandardCharsets.UTF_8)
                                .replace("+", "%20");

                log.info("Excel template filled successfully, size: {} bytes, fileName: {}",
                                bytes.length, fileName);

                return ResponseEntity.ok()
                                .contentType(Objects.requireNonNull(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + fileName + ".xlsx\"; filename*=UTF-8''"
                                                                + encodedFileName)
                                .contentLength(bytes.length)
                                .body(bytes);
        }
}
