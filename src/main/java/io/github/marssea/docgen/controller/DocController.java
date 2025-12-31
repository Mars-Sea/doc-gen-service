package io.github.marssea.docgen.controller;

import io.github.marssea.docgen.model.DocGenRequest;
import io.github.marssea.docgen.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 文档生成 API 控制器
 * <p>
 * 暴露 HTTP 接口供外部系统调用
 */
@Tag(name = "Document Generation", description = "文档生成相关接口")
@RestController
@RequestMapping("/api/v1/doc")
@RequiredArgsConstructor
public class DocController {

    private final WordService wordService;

    /**
     * Word 文档生成接口
     *
     * @param request 包含模板名称和数据的请求体
     * @return 生成的 docx 文件流
     * @throws IOException 处理异常
     */
    @Operation(summary = "生成 Word 文档", description = "根据模板名称和数据生成 Word 文档。")
    @PostMapping("/word")
    public ResponseEntity<byte[]> generateWord(@RequestBody DocGenRequest request) throws IOException {
        byte[] bytes = wordService.generateWord(request.getTemplateName(), request.getData());

        // 获取自定义文件名，如果未指定则使用默认值
        String fileName = request.getFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "generated";
        }

        // 返回文件流，并设置 Content-Disposition 头部，方便浏览器或客户端下载
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName + ".docx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
