package io.github.marssea.docgen.controller;

import io.github.marssea.docgen.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 模板管理 API 控制器
 * <p>
 * 提供模板文件的上传和列表查询接口。
 * <p>
 * 接口路径: /api/v1/template
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

    private final TemplateService templateService;

    /**
     * 上传模板文件
     * <p>
     * 将模板文件上传到服务器的模板目录。
     * 支持 .docx 和 .xlsx 格式。如果文件名已存在，将会覆盖。
     *
     * @param file 上传的模板文件
     * @return 上传结果，包含保存后的文件名
     * @throws IOException 文件处理异常
     */
    @Operation(summary = "上传模板文件", description = "上传 Word (.docx) 或 Excel (.xlsx) 模板文件到服务器。如果文件已存在将会覆盖。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上传成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "请求参数无效（文件为空或格式不支持）", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTemplate(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Received template upload request, originalFilename: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        String savedFilename = templateService.uploadTemplate(file);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "模板上传成功",
                "fileName", savedFilename));
    }

    /**
     * 获取模板文件列表
     * <p>
     * 返回服务器上所有可用的模板文件名列表。
     *
     * @return 模板文件名列表
     * @throws IOException 目录读取异常
     */
    @Operation(summary = "获取模板列表", description = "获取服务器上所有可用的模板文件名列表（包括 .docx 和 .xlsx 文件）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTemplates() throws IOException {
        log.info("Received template list request");

        List<String> templates = templateService.listTemplates();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", templates.size(),
                "templates", templates));
    }
}
