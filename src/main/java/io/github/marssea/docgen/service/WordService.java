package io.github.marssea.docgen.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import com.deepoove.poi.render.compute.SpELRenderDataCompute;
import com.deepoove.poi.xwpf.NiceXWPFDocument;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import io.github.marssea.docgen.util.TemplateValidationUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

/**
 * Word 文档生成服务
 *
 * <p>使用 <a href="http://deepoove.com/poi-tl/">poi-tl</a> 库处理 Word 模板渲染。 该服务负责加载 Word
 * 模板文件，将传入的数据填充到模板中，并返回生成的文档二进制流。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>加载指定的 Word 模板文件
 *   <li>自动检测集合类型数据，绑定循环表格渲染策略
 *   <li>渲染模板并返回文档字节数组
 *   <li>批量生成多页文档
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
    private final ImagePayloadConverter imagePayloadConverter;

    /**
     * 根据模板和数据生成 Word 文档
     *
     * <p>该方法会自动检测数据中的集合类型字段（实现 {@link Iterable} 接口的对象）， 并为其绑定 {@link
     * LoopRowTableRenderPolicy}，从而支持表格行循环渲染。
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.docx）
     * @param data 渲染数据，Key 对应模板中的占位符，Value 为填充值
     * @return 生成的文档二进制流
     * @throws TemplateNotFoundException 当指定的模板文件不存在时抛出
     * @throws IOException 文件读取或写入异常
     */
    public byte[] generateWord(String templateName, Map<String, Object> data) throws IOException {
        // 安全校验：防止路径遍历攻击，验证扩展名
        TemplateValidationUtil.validateWordTemplateExtension(templateName);

        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new TemplateNotFoundException(
                    templateName, "Template not found at: " + templatePath);
        }

        log.info("Generating word document using template: {}", templatePath);

        // 预处理图片载荷：将结构化图片对象转换为 PictureRenderData
        Map<String, Object> processedData = preprocessImagePayloads(data);

        // 构建渲染配置
        Configure config = buildRenderConfig(processedData);

        // 编译模板并渲染数据
        try (XWPFTemplate template =
                        XWPFTemplate.compile(templateFile, config).render(processedData);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 将渲染结果写入内存流
            template.write(out);
            log.info("Word document generated successfully, size: {} bytes", out.size());
            return out.toByteArray();
        }
    }

    /**
     * 批量生成 Word 文档
     *
     * <p>使用同一模板渲染多条数据，每条数据生成一份完整模板实例，合并为单个文档。
     *
     * <p>合并策略：以第一个渲染实例作为主文档，后续实例通过 poi-tl 的 {@code NiceXWPFDocument.merge()} 逐个合并。 在此之前，先将主文档的 body
     * 元素序列化再反序列化，确保内部状态一致。 模板实例之间插入分页符。
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.docx）
     * @param dataList 数据列表，每条数据生成一份完整模板实例
     * @return 生成的文档二进制流
     * @throws TemplateNotFoundException 当指定的模板文件不存在时抛出
     * @throws IOException 文件读取或写入异常
     */
    public byte[] generateBatch(String templateName, List<Map<String, Object>> dataList)
            throws IOException {
        // 安全校验：防止路径遍历攻击，验证扩展名
        TemplateValidationUtil.validateWordTemplateExtension(templateName);

        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }

        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new TemplateNotFoundException(
                    templateName, "Template not found at: " + templatePath);
        }

        log.info(
                "Generating batch word document using template: {}, data count: {}",
                templatePath,
                dataList.size());

        try {
            // 渲染第一个模板实例作为主文档
            NiceXWPFDocument mainDoc = renderTemplateInstance(templateFile, dataList.get(0));
            log.debug("Rendered template instance 1 of {}", dataList.size());

            // 逐个合并后续实例
            for (int i = 1; i < dataList.size(); i++) {
                Map<String, Object> data = dataList.get(i);
                NiceXWPFDocument nextDoc = renderTemplateInstance(templateFile, data);
                log.debug("Rendered template instance {} of {}", i + 1, dataList.size());

                // 在主文档末尾添加分页符段落
                XWPFParagraph pageBreakPara = mainDoc.createParagraph();
                pageBreakPara.createRun().addBreak(BreakType.PAGE);

                // 使用 poi-tl 的 merge 合并下一个实例（merge 会关闭 nextDoc）
                mainDoc = mainDoc.merge(nextDoc);
            }

            // 输出最终文档
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                mainDoc.write(out);
                log.info(
                        "Batch word document generated successfully, template instances: {}, size:"
                                + " {} bytes",
                        dataList.size(),
                        out.size());
                return out.toByteArray();
            } finally {
                mainDoc.close();
            }
        } catch (IOException e) {
            throw e;
        } catch (InvalidImagePayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to merge batch word documents", e);
        }
    }

    /**
     * 渲染单个完整模板实例
     *
     * <p>先将 poi-tl 渲染结果写入内存，再重新加载为 {@link NiceXWPFDocument}，确保用于批量合并的文档状态完整、独立。
     *
     * @param templateFile 模板文件
     * @param data 渲染数据
     * @return 渲染后的完整模板实例
     * @throws IOException 文件读取或写入异常
     */
    private NiceXWPFDocument renderTemplateInstance(File templateFile, Map<String, Object> data)
            throws IOException {
        // 预处理图片载荷：将结构化图片对象转换为 PictureRenderData
        Map<String, Object> processedData = preprocessImagePayloads(data);

        // 构建渲染配置
        Configure config = buildRenderConfig(processedData);

        try (XWPFTemplate template =
                        XWPFTemplate.compile(templateFile, config).render(processedData);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            template.write(out);
            return new NiceXWPFDocument(new ByteArrayInputStream(out.toByteArray()));
        }
    }

    /**
     * 构建渲染配置
     *
     * <p>自动检测数据中的集合类型字段，为其绑定 {@link LoopRowTableRenderPolicy}， 使模板支持表格行循环渲染功能。
     *
     * <p>使用 SpEL 表达式引擎，可以优雅地处理 null 值（渲染为空字符串）。
     *
     * @param data 渲染数据
     * @return poi-tl 渲染配置对象
     */
    private Configure buildRenderConfig(Map<String, Object> data) {
        LoopRowTableRenderPolicy policy = new LoopRowTableRenderPolicy();
        var builder =
                Configure.builder()
                        .useSpringEL()
                        .setRenderDataComputeFactory(
                                model -> {
                                    var compute = new SpELRenderDataCompute(model, true);
                                    return el -> compute.compute(el.trim());
                                });

        if (data != null) {
            data.forEach(
                    (key, value) -> {
                        if (value instanceof Iterable) {
                            log.debug("Auto-binding LoopRowTableRenderPolicy for field: {}", key);
                            builder.bind(key, policy);
                        }
                    });
        }

        return builder.build();
    }

    /**
     * 预处理图片载荷
     *
     * <p>遍历数据 Map，检测结构化图片对象（包含 {@code type: "image"} 字段）， 将其转换为 poi-tl 的 {@link
     * PictureRenderData}。非图片字段保持不变。
     *
     * @param data 原始渲染数据
     * @return 预处理后的渲染数据（图片字段已转换为 PictureRenderData）
     */
    private Map<String, Object> preprocessImagePayloads(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Map<String, Object> processed = new HashMap<>();
        data.forEach(
                (key, value) -> {
                    if (imagePayloadConverter.isImagePayload(value)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) value;
                        log.debug("Converting image payload for field: {}", key);
                        processed.put(key, imagePayloadConverter.convert(key, payload));
                    } else {
                        processed.put(key, value);
                    }
                });
        return processed;
    }
}
