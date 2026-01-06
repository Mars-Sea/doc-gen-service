package io.github.marssea.docgen.controller;

import io.github.marssea.docgen.model.DocGenRequest;
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
                        @ApiResponse(responseCode = "404", description = "模板文件不存在", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        @PostMapping("/word")
        public ResponseEntity<byte[]> generateWord(@Valid @RequestBody DocGenRequest request) throws IOException {
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
                                .contentType(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
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
                                .contentType(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + fileName + ".xlsx\"; filename*=UTF-8''"
                                                                + encodedFileName)
                                .contentLength(bytes.length)
                                .body(bytes);
        }
}
