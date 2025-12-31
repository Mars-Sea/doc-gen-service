package io.github.marssea.docgen.service;

import com.deepoove.poi.XWPFTemplate;
import io.github.marssea.docgen.config.DocGenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Word 文档生成服务
 * <p>
 * 使用 poi-tl 库处理 Word 模板渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordService {

    private final DocGenProperties properties;

    /**
     * 根据模板和数据生成 Word 文档
     *
     * @param templateName 模板文件名
     * @param data         渲染数据
     * @return 生成的文档二进制流
     * @throws IOException 文件读取或写入异常
     */
    public byte[] generateWord(String templateName, Map<String, Object> data) throws IOException {
        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new RuntimeException("Template not found: " + templatePath);
        }

        log.info("Generating word document using template: {}", templatePath);

        // 自动检测 Collection 类型的字段，为它们绑定 LoopRowTableRenderPolicy
        LoopRowTableRenderPolicy policy = new LoopRowTableRenderPolicy();
        var builder = Configure.builder();

        if (data != null) {
            data.forEach((key, value) -> {
                if (value instanceof Iterable) {
                    log.info("Auto-binding LoopRowTableRenderPolicy for field: {}", key);
                    builder.bind(key, policy);
                }
            });
        }

        Configure config = builder.build();

        // 编译模板并渲染数据
        try (XWPFTemplate template = XWPFTemplate.compile(templateFile, config).render(data);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 将渲染结果写入内存流
            template.write(out);
            return out.toByteArray();
        }
    }
}
