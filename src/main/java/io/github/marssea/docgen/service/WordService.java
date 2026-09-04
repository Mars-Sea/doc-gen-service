package io.github.marssea.docgen.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import com.deepoove.poi.render.compute.SpELRenderDataCompute;
import com.deepoove.poi.template.ElementTemplate;
import com.deepoove.poi.template.run.RunTemplate;
import com.deepoove.poi.xwpf.NiceXWPFDocument;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import io.github.marssea.docgen.util.QrCodePayloadConverter;
import io.github.marssea.docgen.util.TemplateValidationUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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
 *   <li>自动识别模板中的表格循环结构，绑定循环表格渲染策略
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
    private final QrCodePayloadConverter qrCodePayloadConverter;

    /**
     * 表格循环数据行占位标签（poi-tl {@code LoopRowTableRenderPolicy} 默认 {@code [field]} 语法）
     *
     * <p>要求方括号内是合法的变量名（首字符为 Unicode 字母或下划线，后续为字母、数字、下划线、点号）， 且整个单元格内容就是该标签（配合 {@link
     * #isLoopDataRow(XWPFTableRow)} 使用）。 旧正则 {@code \[[^\[\]{}]+\]} 会匹配 {@code [选填]}、{@code
     * [是/否]}、{@code [1]} 等纯文本方括号，导致普通表格行被误判为循环数据行而被静默删除。
     */
    private static final Pattern LOOP_ROW_TAG =
            Pattern.compile("\\[\\s*[\\p{L}_][\\p{L}\\p{N}_.]*\\s*\\]");

    /**
     * 模板占位标签，如 {@code {{items}}}、{@code {{items.name}}}。
     *
     * <p>首字符允许任意 Unicode 字母或下划线（poi-tl 默认标签语法支持中文），后续允许字母、数字、下划线与点号； 不含 {@code @ # ? / ^}
     * 等前缀标识符，避免匹配到图片、表格、区块对等标签。
     */
    private static final Pattern TEMPLATE_TAG =
            Pattern.compile("\\{\\{\\s*([\\p{L}_][\\p{L}\\p{N}_.]*)\\s*\\}\\}");

    /**
     * 根据模板和数据生成 Word 文档
     *
     * <p>该方法会根据模板中的表格结构识别循环字段，并仅对这些字段绑定 {@link LoopRowTableRenderPolicy}，从而支持表格行循环渲染。
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

        // 识别模板中的表格循环字段，缺失/为空时自动按空列表处理（用于删除循环行及其占位标签）
        Set<String> loopFields = detectLoopTableFields(templateFile);
        ensureLoopData(processedData, loopFields);

        // 构建渲染配置
        Configure config = buildRenderConfig(processedData, loopFields);

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

        NiceXWPFDocument mainDoc = null;
        try {
            // 识别模板中的表格循环字段（渲染每个实例共用，避免重复解析模板）
            Set<String> loopFields = detectLoopTableFields(templateFile);

            // 渲染第一个模板实例作为主文档
            mainDoc = renderTemplateInstance(templateFile, dataList.get(0), loopFields);
            log.debug("Rendered template instance 1 of {}", dataList.size());

            // 逐个合并后续实例
            for (int i = 1; i < dataList.size(); i++) {
                // 在主文档末尾添加分页符段落
                XWPFParagraph pageBreakPara = mainDoc.createParagraph();
                pageBreakPara.createRun().addBreak(BreakType.PAGE);

                Map<String, Object> data = dataList.get(i);
                NiceXWPFDocument nextDoc = renderTemplateInstance(templateFile, data, loopFields);
                log.debug("Rendered template instance {} of {}", i + 1, dataList.size());

                // merge 内部通过 generate(true) 会关闭旧的主文档与 nextDoc，并返回全新文档
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
            }
        } catch (IOException | RuntimeException e) {
            // 保持客户端错误的语义：InvalidImagePayloadException / IllegalArgumentException 等
            // 运行时异常应原样抛出，由全局异常处理器映射为 4xx，避免被包装成 IOException 误报 500
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to merge batch word documents", e);
        } finally {
            // 无论成功或中途异常，都确保当前主文档被关闭，避免资源泄漏
            closeQuietly(mainDoc);
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
    private NiceXWPFDocument renderTemplateInstance(
            File templateFile, Map<String, Object> data, Set<String> loopFields)
            throws IOException {
        // 预处理图片载荷：将结构化图片对象转换为 PictureRenderData
        Map<String, Object> processedData = preprocessImagePayloads(data);

        // 缺失/为空的表格循环字段自动按空列表处理（用于删除循环行及其占位标签）
        ensureLoopData(processedData, loopFields);

        // 构建渲染配置
        Configure config = buildRenderConfig(processedData, loopFields);

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
     * <p>仅为<b>模板中确实存在表格循环结构</b>的字段绑定 {@link LoopRowTableRenderPolicy}，
     * 使模板支持表格行循环渲染功能。不再依据数据类型（{@code Iterable}）绑定，避免非表格场景渲染失败。
     *
     * <p>使用 SpEL 表达式引擎，可以优雅地处理 null 值（渲染为空字符串）。
     *
     * @param data 渲染数据
     * @param loopFields 模板中识别出的表格循环字段名集合
     * @return poi-tl 渲染配置对象
     */
    private Configure buildRenderConfig(Map<String, Object> data, Set<String> loopFields) {
        // 使用扩展策略：循环渲染后清理残留的触发行（poi-tl 默认只清空其文本，行仍在）
        LoopRowTableRenderPolicy policy = new LoopRowTableCleanupPolicy();
        var builder =
                Configure.builder()
                        .useSpringEL()
                        .setRenderDataComputeFactory(
                                model -> {
                                    // 非严格模式（false）：当标签表达式无法计算（如变量缺失、对 null 级联取值）时
                                    // 返回 null 并渲染为空字符串，而非抛出异常中断整篇文档的生成。
                                    var compute = new SpELRenderDataCompute(model, false);
                                    return el -> compute.compute(el.trim());
                                });

        // 仅绑定模板中识别出的表格循环字段。
        // 旧实现会额外把数据中所有 Iterable 值无条件绑定循环策略；而 poi-tl 按「标签名」查找自定义策略、
        // 不校验标签是否位于表格内，导致段落中的 {{tags}} 这类非表格列表字段抛出 RenderException → 500。
        // 表格循环字段：即便数据缺失/为 null，也绑定循环策略，从而删除循环行及其占位标签。
        // 仅当该字段不是明确的标量（存在且非 Iterable）时才绑定，避免误伤模板中的普通单元格变量。
        Set<String> boundFields = new HashSet<>();
        if (loopFields != null) {
            loopFields.forEach(
                    field -> {
                        boolean scalar =
                                data != null
                                        && data.containsKey(field)
                                        && data.get(field) != null
                                        && !(data.get(field) instanceof Iterable);
                        if (!scalar) {
                            boundFields.add(field);
                        }
                    });
        }
        boundFields.forEach(
                field -> {
                    log.debug("Binding LoopRowTableRenderPolicy for field: {}", field);
                    builder.bind(field, policy);
                });

        return builder.build();
    }

    /**
     * 预处理图片与二维码载荷
     *
     * <p>遍历数据（含嵌套 Map 与集合），检测结构化图片对象（包含 {@code type: "image"} 字段） 与二维码对象（包含 {@code type: "qrcode"}
     * 字段），分别将其转换为 poi-tl 的 {@code PictureRenderData}。其余字段保持不变。
     *
     * @param data 原始渲染数据
     * @return 预处理后的渲染数据（图片/二维码字段已转换为 PictureRenderData）
     */
    private Map<String, Object> preprocessImagePayloads(Map<String, Object> data) {
        // data 为 null 时返回空 Map（而非 null），避免 null 模型进入 poi-tl render()，
        // 同时保证后续 ensureLoopData 能正常为循环字段补入空列表。
        Map<String, Object> processed = new HashMap<>();
        if (data == null) {
            return processed;
        }

        data.forEach((key, value) -> processed.put(key, convertImagePayload(key, value)));
        return processed;
    }

    /**
     * 递归转换值中的图片与二维码载荷
     *
     * <p>除顶层字段外，还需递归进入嵌套 Map 与集合内部：表格循环行内的图片载荷（如 {@code items[0].logo}）位于集合元素之中，若只扫描顶层，会被 poi-tl
     * 原样渲染成 {@code Map.toString()} 形式的字符串。
     *
     * @param field 字段路径（用于日志与异常提示）
     * @param value 待转换的值
     * @return 转换后的值；非图片/二维码载荷且无可递归结构时原样返回
     */
    private Object convertImagePayload(String field, Object value) {
        if (qrCodePayloadConverter.isQrCodePayload(value)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) value;
            log.debug("Converting qr code payload for field: {}", field);
            return qrCodePayloadConverter.convert(field, payload);
        }
        if (imagePayloadConverter.isImagePayload(value)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) value;
            log.debug("Converting image payload for field: {}", field);
            return imagePayloadConverter.convert(field, payload);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> converted = new HashMap<>();
            nestedMap.forEach(
                    (key, nested) ->
                            converted.put(
                                    String.valueOf(key),
                                    convertImagePayload(field + "." + key, nested)));
            return converted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> converted = new ArrayList<>();
            int index = 0;
            for (Object element : iterable) {
                converted.add(convertImagePayload(field + "[" + index + "]", element));
                index++;
            }
            return converted;
        }
        return value;
    }

    /**
     * 识别模板中的表格循环字段。
     *
     * <p>poi-tl 的 {@link LoopRowTableRenderPolicy} 使用默认的 {@code [field]} 语法标记循环数据行： 循环触发标签（默认
     * {@code {{items}}}）位于某一行单元格中，其下一行即为数据模板行（含 {@code [field]} 占位标签）。 据此遍历模板所有表格：仅当某行的下一行是「整单元格为
     * {@code [field]}」的循环数据行，且本行存在「整单元格为 {@code {{key}}}」的触发标签时，才将 {@code key} 视为循环字段。
     *
     * @param templateFile 模板文件
     * @return 识别到的表格循环字段名集合
     */
    private Set<String> detectLoopTableFields(File templateFile) {
        Set<String> loopFields = new HashSet<>();
        try (XWPFDocument document =
                new XWPFDocument(Files.newInputStream(templateFile.toPath()))) {
            // 递归收集所有表格（含单元格内的嵌套表格），避免嵌套循环表被漏检
            List<XWPFTable> tables = new ArrayList<>();
            collectTables(document.getTables(), tables);
            for (XWPFTable table : tables) {
                List<XWPFTableRow> rows = table.getRows();
                for (int i = 0; i < rows.size() - 1; i++) {
                    // 下一行必须是「整单元格就是一个 [field]」的循环数据模板行；
                    // 否则它可能只是含 [选填]、[备注] 之类方括号文本的普通内容行，
                    // 误判会导致该行被循环策略静默删除。
                    if (!isLoopDataRow(rows.get(i + 1))) {
                        continue;
                    }
                    loopFields.addAll(triggerLoopFields(rows.get(i)));
                }
            }
        } catch (Exception e) {
            // POI 打开非 OOXML 或损坏模板时抛出的 OpenXML4JException 属于运行时异常，
            // 仅捕获 IOException 无法降级。此处统一降级为空集合，保证模板解析失败不阻断主流程。
            log.warn("Failed to inspect template for table loop fields, fallback to empty set", e);
        }
        return loopFields;
    }

    /**
     * 判断是否为循环数据模板行
     *
     * <p>要求至少一个单元格的**全部内容**就是一个 {@code [field]} 占位标签。 循环数据行是纯模板行，而普通内容行的方括号通常只是文字的一部分（如 {@code
     * 备注[选填]}）。
     */
    private boolean isLoopDataRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            if (LOOP_ROW_TAG.matcher(cellText(cell).trim()).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取出循环触发行中的循环字段名
     *
     * <p>只认「整单元格内容就是一个 {@code {{tag}}}」的单元格（poi-tl 循环触发行的约定写法）。 形如 {@code 客户 {{customerName}}}
     * 的单元格是普通变量，不是循环触发标签。
     */
    private Set<String> triggerLoopFields(XWPFTableRow row) {
        Set<String> fields = new HashSet<>();
        for (XWPFTableCell cell : row.getTableCells()) {
            Matcher matcher = TEMPLATE_TAG.matcher(cellText(cell).trim());
            if (matcher.matches()) {
                fields.add(leadingSegment(matcher.group(1)));
            }
        }
        return fields;
    }

    /** 递归收集表格及其单元格内嵌套的所有表格 */
    private void collectTables(List<XWPFTable> source, List<XWPFTable> accumulator) {
        for (XWPFTable table : source) {
            accumulator.add(table);
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    List<XWPFTable> nested = cell.getTables();
                    if (nested != null && !nested.isEmpty()) {
                        collectTables(nested, accumulator);
                    }
                }
            }
        }
    }

    /**
     * 为缺失或为 null 的表格循环字段补入空列表。
     *
     * <p>空列表在 {@link LoopRowTableRenderPolicy} 中会触发删除循环数据行，从而移除 {@code [field]} 等占位标签； 循环触发行本身由
     * {@link LoopRowTableCleanupPolicy} 一并清理，避免残留空行。
     *
     * @param data 渲染数据
     * @param loopFields 表格循环字段名集合
     */
    private void ensureLoopData(Map<String, Object> data, Set<String> loopFields) {
        if (data == null || loopFields == null || loopFields.isEmpty()) {
            return;
        }
        for (String field : loopFields) {
            if (!data.containsKey(field) || data.get(field) == null) {
                data.put(field, new ArrayList<>());
            }
        }
    }

    /** 拼接单元格内所有段落的文本（不含单元格内嵌套表格的文本），用于标签识别 */
    private static String cellText(XWPFTableCell cell) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            text.append(paragraph.getText());
        }
        return text.toString();
    }

    /** 拼接整行所有单元格的文本，用于标签识别 */
    private static String rowText(XWPFTableRow row) {
        StringBuilder text = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            text.append(cellText(cell));
        }
        return text.toString();
    }

    /**
     * 表格循环策略扩展：渲染后移除残留的循环触发行
     *
     * <p>poi-tl 的 {@link LoopRowTableRenderPolicy} 在渲染时只做两件事：清空触发行中 {@code {{tag}}} 的 文本（{@code
     * run.setText("", 0)}）、删除数据模板行（{@code table.removeRow(templateRowIndex)}）。 <b>含 {@code {{tag}}}
     * 的触发行本身会被保留</b>，最终在文档中表现为一行空白行。
     *
     * <p>本实现在父类渲染完成后，若该触发行已无任何可见文本（说明它只是纯标记行），则将其一并删除。 触发行若还有其他内容（例如静态标题），则保留，避免误删用户内容。
     */
    private static final class LoopRowTableCleanupPolicy extends LoopRowTableRenderPolicy {

        @Override
        public void render(ElementTemplate eleTemplate, Object data, XWPFTemplate template) {
            XWPFTableCell tagCell = tagCellOf(eleTemplate);
            XWPFTableRow triggerRow = tagCell == null ? null : tagCell.getTableRow();
            XWPFTable table = triggerRow == null ? null : triggerRow.getTable();

            super.render(eleTemplate, data, template);

            if (table == null || triggerRow == null) {
                return;
            }
            // 父类渲染过程中可能插入/删除了行，删除前重新定位触发行
            int triggerRowIndex = table.getRows().indexOf(triggerRow);
            if (triggerRowIndex < 0) {
                return;
            }
            if (rowText(triggerRow).trim().isEmpty()) {
                log.debug("Removing empty loop trigger row at index {}", triggerRowIndex);
                table.removeRow(triggerRowIndex);
            }
        }

        /** 取出循环标签所在的单元格，无法定位时返回 null */
        private XWPFTableCell tagCellOf(ElementTemplate eleTemplate) {
            if (!(eleTemplate instanceof RunTemplate runTemplate)) {
                return null;
            }
            XWPFRun run = runTemplate.getRun();
            if (run == null || !(run.getParent() instanceof XWPFParagraph paragraph)) {
                return null;
            }
            return paragraph.getBody() instanceof XWPFTableCell cell ? cell : null;
        }
    }

    /** 取表达式首个路径段（如 {@code items}、{@code items.name} {@code ->} {@code items}） */
    private String leadingSegment(String expression) {
        int dot = expression.indexOf('.');
        int bracket = expression.indexOf('[');
        int separator = dot < 0 ? bracket : (bracket < 0 ? dot : Math.min(dot, bracket));
        return separator < 0 ? expression : expression.substring(0, separator);
    }

    /** 安静地关闭文档，忽略关闭过程中的异常（仅用于资源清理） */
    private void closeQuietly(NiceXWPFDocument doc) {
        if (doc == null) {
            return;
        }
        try {
            doc.close();
        } catch (IOException e) {
            log.warn("Failed to close word document: {}", e.getMessage());
        }
    }
}
