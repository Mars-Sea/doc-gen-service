package io.github.marssea.docgen.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Word 文档生成服务
 * <p>
 * 使用 <a href="http://deepoove.com/poi-tl/">poi-tl</a> 库处理 Word 模板渲染。
 * 该服务负责加载 Word 模板文件，将传入的数据填充到模板中，并返回生成的文档二进制流。
 * <p>
 * 主要功能：
 * <ul>
 * <li>加载指定的 Word 模板文件</li>
 * <li>自动检测集合类型数据，绑定循环表格渲染策略</li>
 * <li>渲染模板并返回文档字节数组</li>
 * <li>批量生成多页文档</li>
 * </ul>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordService {

    private final DocGenProperties properties;

    /**
     * 根据模板和数据生成 Word 文档
     * <p>
     * 该方法会自动检测数据中的集合类型字段（实现 {@link Iterable} 接口的对象），
     * 并为其绑定 {@link LoopRowTableRenderPolicy}，从而支持表格行循环渲染。
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.docx）
     * @param data         渲染数据，Key 对应模板中的占位符，Value 为填充值
     * @return 生成的文档二进制流
     * @throws TemplateNotFoundException 当指定的模板文件不存在时抛出
     * @throws IOException               文件读取或写入异常
     */
    public byte[] generateWord(String templateName, Map<String, Object> data) throws IOException {
        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new TemplateNotFoundException(templateName, "Template not found at: " + templatePath);
        }

        log.info("Generating word document using template: {}", templatePath);

        // 构建渲染配置
        Configure config = buildRenderConfig(data);

        // 编译模板并渲染数据
        try (XWPFTemplate template = XWPFTemplate.compile(templateFile, config).render(data);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 将渲染结果写入内存流
            template.write(out);
            log.info("Word document generated successfully, size: {} bytes", out.size());
            return out.toByteArray();
        }
    }

    /**
     * 批量生成 Word 文档
     * <p>
     * 使用同一模板渲染多条数据，每条数据生成一页，合并为单个文档。
     * 页面之间自动添加分页符。
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.docx）
     * @param dataList     数据列表，每条数据生成一页
     * @return 生成的文档二进制流
     * @throws TemplateNotFoundException 当指定的模板文件不存在时抛出
     * @throws IOException               文件读取或写入异常
     */
    public byte[] generateBatch(String templateName, List<Map<String, Object>> dataList) throws IOException {
        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new TemplateNotFoundException(templateName, "Template not found at: " + templatePath);
        }

        log.info("Generating batch word document using template: {}, data count: {}",
                templatePath, dataList.size());

        // 创建主文档
        XWPFDocument mainDoc = null;

        try {
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> data = dataList.get(i);

                // 构建渲染配置
                Configure config = buildRenderConfig(data);

                // 渲染当前数据
                try (XWPFTemplate template = XWPFTemplate.compile(templateFile, config).render(data);
                        ByteArrayOutputStream tempOut = new ByteArrayOutputStream()) {

                    template.write(tempOut);

                    if (mainDoc == null) {
                        // 第一页：直接使用渲染结果作为主文档
                        mainDoc = new XWPFDocument(new ByteArrayInputStream(tempOut.toByteArray()));
                    } else {
                        // 后续页：添加分页符，然后合并内容
                        XWPFParagraph breakPara = mainDoc.createParagraph();
                        breakPara.createRun().addBreak(BreakType.PAGE);

                        // 读取渲染后的文档
                        try (XWPFDocument pageDoc = new XWPFDocument(new ByteArrayInputStream(tempOut.toByteArray()))) {
                            // 合并文档内容
                            appendDocument(mainDoc, pageDoc);
                        }
                    }
                }

                log.debug("Rendered page {} of {}", i + 1, dataList.size());
            }

            // 输出最终文档
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (mainDoc != null) {
                    mainDoc.write(out);
                }
                log.info("Batch word document generated successfully, pages: {}, size: {} bytes",
                        dataList.size(), out.size());
                return out.toByteArray();
            }
        } finally {
            if (mainDoc != null) {
                mainDoc.close();
            }
        }
    }

    /**
     * 将源文档的内容追加到目标文档
     *
     * @param target 目标文档
     * @param source 源文档
     */
    private void appendDocument(XWPFDocument target, XWPFDocument source) {

        // 复制段落
        source.getParagraphs().forEach(para -> {
            XWPFParagraph newPara = target.createParagraph();
            newPara.getCTP().set(para.getCTP().copy());
        });

        // 复制表格
        source.getTables().forEach(table -> {
            target.createTable();
            int pos = target.getTables().size() - 1;
            target.getTables().get(pos).getCTTbl().set(table.getCTTbl().copy());
        });
    }

    /**
     * 构建渲染配置
     * <p>
     * 自动检测数据中的集合类型字段，为其绑定 {@link LoopRowTableRenderPolicy}，
     * 使模板支持表格行循环渲染功能。
     *
     * @param data 渲染数据
     * @return poi-tl 渲染配置对象
     */
    private Configure buildRenderConfig(Map<String, Object> data) {
        LoopRowTableRenderPolicy policy = new LoopRowTableRenderPolicy();
        var builder = Configure.builder();

        if (data != null) {
            data.forEach((key, value) -> {
                if (value instanceof Iterable) {
                    log.debug("Auto-binding LoopRowTableRenderPolicy for field: {}", key);
                    builder.bind(key, policy);
                }
            });
        }

        return builder.build();
    }
}
