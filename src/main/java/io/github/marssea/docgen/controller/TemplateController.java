package io.github.marssea.docgen.controller;

import io.github.marssea.docgen.template.TemplateInfo;
import io.github.marssea.docgen.template.TemplateName;
import io.github.marssea.docgen.template.TemplateName.TemplateKind;
import io.github.marssea.docgen.template.TemplateRepository;
import io.github.marssea.docgen.template.TemplateWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模板管理 API 控制器
 *
 * <p>提供模板文件的上传和列表查询接口。
 *
 * <p>接口路径: /api/v1/template
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "Template Management", description = "模板文件管理接口")
@RestController
@RequestMapping("/api/v1/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateRepository templateRepository;
    private final TemplateWriter templateWriter;

    /**
     * 上传模板文件
     *
     * <p>将模板文件上传到服务器的模板目录。 支持 .docx 和 .xlsx 格式。如果文件名已存在，将会覆盖。
     *
     * @param file 上传的模板文件
     * @return 上传结果，包含保存后的文件名
     * @throws IOException 文件处理异常
     */
    @Operation(
            summary = "上传模板文件",
            description = "上传 Word (.docx) 或 Excel (.xlsx) 模板文件到服务器。如果文件已存在将会覆盖。")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "上传成功",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "请求参数无效（文件为空或格式不支持）",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "服务器内部错误",
                        content = @Content(schema = @Schema(implementation = Map.class)))
            })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTemplate(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info(
                "Received template upload request, originalFilename: {}, size: {} bytes",
                file.getOriginalFilename(),
                file.getSize());

        String savedFilename = templateWriter.upload(file);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "模板上传成功", "fileName", savedFilename));
    }

    /**
     * 获取模板文件列表
     *
     * <p>返回服务器上所有可用的模板及其属性。每项含 {@code name}（文件名）、{@code size}（字节数） 与 {@code lastModified}（ISO-8601
     * UTC 时间，如 {@code 2026-10-09T08:54:16Z}）。
     *
     * @return 模板信息列表
     * @throws IOException 目录读取异常
     */
    @Operation(
            summary = "获取模板列表",
            description =
                    "获取服务器上所有可用的模板（包括 .docx 和 .xlsx 文件）。返回文件名、字节大小与最后修改时间（ISO-8601" + " UTC）。")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "获取成功",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "服务器内部错误",
                        content = @Content(schema = @Schema(implementation = Map.class)))
            })
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTemplates() throws IOException {
        log.info("Received template list request");

        List<TemplateInfo> templates = templateRepository.list();

        List<Map<String, Object>> items =
                templates.stream().map(TemplateController::toItem).toList();

        return ResponseEntity.ok(
                Map.of("success", true, "count", items.size(), "templates", items));
    }

    /**
     * 把一条模板信息摊平成响应元素
     *
     * <p>为什么在这里摊平而不是直接序列化 {@link TemplateInfo}：控制器其余端点返回的都是 {@code Map}，混用两套出参风格会让「响应长什么样」分散到两处； 且
     * {@code Instant} 由 Jackson 序列化时受全局配置影响，显式转换成字符串可锁定 ISO-8601 UTC 这一种格式。
     */
    private static Map<String, Object> toItem(TemplateInfo info) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", info.name());
        item.put("size", info.size());
        item.put("lastModified", info.lastModified().toString());
        return item;
    }

    /**
     * 删除模板文件
     *
     * <p>从服务器上删除指定的模板文件。
     *
     * @param templateName 要删除的模板文件名
     * @return 删除结果
     * @throws IOException 文件删除异常
     */
    @Operation(summary = "删除模板文件", description = "从服务器上删除指定的模板文件。删除后不可恢复。")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "删除成功或文件不存在",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "请求参数无效",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "服务器内部错误",
                        content = @Content(schema = @Schema(implementation = Map.class)))
            })
    @DeleteMapping("/{templateName}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable String templateName)
            throws IOException {

        log.info("Received template delete request, templateName: {}", templateName);

        boolean deleted = templateWriter.delete(parseAnyKind(templateName));

        if (deleted) {
            return ResponseEntity.ok(
                    Map.of("success", true, "message", "模板删除成功", "fileName", templateName));
        } else {
            return ResponseEntity.ok(
                    Map.of("success", false, "message", "模板文件不存在", "fileName", templateName));
        }
    }

    /**
     * 下载模板文件
     *
     * <p>返回模板文件的二进制内容。
     *
     * @param templateName 模板文件名
     * @return 模板文件二进制流
     * @throws IOException 文件读取异常
     */
    @Operation(summary = "下载模板文件", description = "下载指定的模板文件。返回文件二进制流。")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "下载成功",
                        content = @Content(mediaType = "application/octet-stream")),
                @ApiResponse(
                        responseCode = "400",
                        description = "文件名无效或文件不存在",
                        content = @Content(schema = @Schema(implementation = Map.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "服务器内部错误",
                        content = @Content(schema = @Schema(implementation = Map.class)))
            })
    @GetMapping("/download/{templateName}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String templateName)
            throws IOException {

        log.info("Received template download request, templateName: {}", templateName);

        byte[] content = templateRepository.read(parseAnyKind(templateName));

        // 根据文件扩展名设置 Content-Type
        String contentType =
                templateName.toLowerCase().endsWith(".xlsx")
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + templateName + "\"")
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 解析删除 / 下载入口的模板名
     *
     * <p>这两个端点对 Word 与 Excel 模板一视同仁，故按扩展名选择对应的合法类型， 命名规则仍由{@link TemplateName} 单份判定。
     */
    private static TemplateName parseAnyKind(String templateName) {
        TemplateKind kind =
                templateName.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                        ? TemplateKind.EXCEL
                        : TemplateKind.WORD;
        return TemplateName.parse(templateName, kind);
    }
}
