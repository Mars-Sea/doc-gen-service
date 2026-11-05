package io.github.marssea.docgen.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.ImageData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.DocumentGenerationException;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import io.github.marssea.docgen.exception.PayloadTooLargeException;
import io.github.marssea.docgen.payload.ConvertedImage;
import io.github.marssea.docgen.payload.PayloadWalker;
import io.github.marssea.docgen.template.TemplateName;
import io.github.marssea.docgen.template.TemplateName.TemplateKind;
import io.github.marssea.docgen.template.TemplateRepository;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import io.github.marssea.docgen.util.QrCodePayloadConverter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Excel 文档生成服务
 *
 * <p>使用 <a href="https://easyexcel.opensource.alibaba.com/">EasyExcel</a> 库处理 Excel 生成。
 * 该服务支持动态表头和数据填充，无需预定义实体类。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>动态配置表头列名
 *   <li>填充二维数据到工作表
 *   <li>自动调整列宽
 *   <li>基于模板的数据填充
 * </ul>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final TemplateRepository templateRepository;
    private final ImagePayloadConverter imagePayloadConverter;
    private final QrCodePayloadConverter qrCodePayloadConverter;
    private final DocGenProperties properties;

    /**
     * 根据表头和数据生成 Excel 文档
     *
     * <p>使用 EasyExcel 动态生成 Excel 文件，无需预定义数据模型类。
     *
     * @param sheetName 工作表名称，为空时默认使用 "Sheet1"
     * @param headers 表头列名列表
     * @param data 二维数据列表，每行数据的列顺序需与 headers 对应
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] generateExcel(String sheetName, List<String> headers, List<List<Object>> data) {
        // 设置默认工作表名称
        if (sheetName == null || sheetName.isBlank()) {
            sheetName = "Sheet1";
        }

        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Headers cannot be null or empty");
        }

        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        // 生成路径也受行数限额管：它走 SXSSF 流式写、成本比模板填充低一个量级，
        // 但请求体本身与 Jackson 反序列化出来的二维 List 仍是纯内存占用，不限额同样能堆满
        int maxListRows = properties.getLimits().getMaxListRows();
        if (maxListRows > 0 && data.size() > maxListRows) {
            throw new PayloadTooLargeException("data 行数", data.size(), maxListRows);
        }

        log.info(
                "Generating Excel document, sheet: {}, columns: {}, rows: {}",
                sheetName,
                headers.size(),
                data.size());

        // 构建表头（EasyExcel 需要 List<List<String>> 格式）
        List<List<String>> headList = buildHead(headers);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 使用 EasyExcel 写入数据
            EasyExcel.write(out)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .head(headList)
                    .doWrite(data);

            log.info("Excel document generated successfully, size: {} bytes", out.size());
            return out.toByteArray();
        } catch (OutOfMemoryError e) {
            log.error("Excel generation ran out of heap, sheet: {}", sheetName, e);
            throw new DocumentGenerationException("文档生成所需内存超出服务可用上限，请减少数据量后重试（Java heap space）", e);
        } catch (Exception e) {
            log.error("Failed to generate Excel document", e);
            throw new RuntimeException("Failed to generate Excel document: " + e.getMessage(), e);
        }
    }

    /**
     * 基于模板填充 Excel 文档
     *
     * <p>使用 EasyExcel 模板填充功能，支持：
     *
     * <ul>
     *   <li>单值变量替换: {variable}
     *   <li>列表数据循环: {.field}
     * </ul>
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.xlsx）
     * @param data 单值变量数据
     * @param listData 列表数据（用于循环填充）
     * @param sheetNo 工作表索引（从 0 开始），为 null 时使用默认值
     * @param sheetName 工作表名称，为 null/空时使用默认值；当 sheetNo 不为 null 时 sheetNo 优先
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] fillTemplate(
            String templateName,
            Map<String, Object> data,
            Map<String, List<Map<String, Object>>> listData,
            Integer sheetNo,
            String sheetName) {
        return fillTemplate(templateName, data, listData, sheetNo, sheetName, null);
    }

    /**
     * 基于模板填充 Excel 文档（可指定是否强制新建行）
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.xlsx）
     * @param data 单值变量数据
     * @param listData 列表数据（用于循环填充）
     * @param sheetNo 工作表索引（从 0 开始）
     * @param sheetName 工作表名称
     * @param forceNewRow 列表填充时是否强制新建行；null 时按 {@code true} 处理。详见 {@link #buildFillConfig(Boolean)}
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] fillTemplate(
            String templateName,
            Map<String, Object> data,
            Map<String, List<Map<String, Object>>> listData,
            Integer sheetNo,
            String sheetName,
            Boolean forceNewRow) {
        // 安全校验：命名规则与存在性检查统一走 TemplateRepository，Excel 侧不再自行拼接模板路径
        File templateFile = templateRepository.locate(parseTemplateName(templateName));

        log.info(
                "Filling Excel template: {}, data keys: {}, list keys: {}",
                templateFile.getPath(),
                data != null ? data.keySet() : "null",
                listData != null ? listData.keySet() : "null");

        // 限额必须在开始填充之前判：EasyExcel 的模板填充是纯内存操作，一旦进入填充流程再发现超量，
        // 堆已经被占住了，拒绝请求也救不回来。
        checkLimits(data, listData);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 缓存作用域即本次请求：同一张图在多少行重复出现，只下载一次、堆里只留一份字节
            ImageCache imageCache = new ImageCache();

            // 使用模板创建 ExcelWriter
            try (ExcelWriter excelWriter =
                    EasyExcel.write(out).withTemplate(templateFile).build()) {

                WriteSheet writeSheet = buildWriteSheet(sheetNo, sheetName);

                FillConfig fillConfig = buildFillConfig(forceNewRow);

                // 先填充列表数据（因为列表会改变行数）
                if (listData != null && !listData.isEmpty()) {
                    for (Map.Entry<String, List<Map<String, Object>>> entry : listData.entrySet()) {
                        log.debug(
                                "Filling list data for key: {}, rows: {}",
                                entry.getKey(),
                                entry.getValue().size());
                        excelWriter.fill(
                                preprocessListPayloads(entry.getValue(), imageCache),
                                fillConfig,
                                writeSheet);
                    }
                }

                // 再填充单值数据
                if (data != null && !data.isEmpty()) {
                    excelWriter.fill(preprocessImagePayloads(data, imageCache), writeSheet);
                }
            }

            log.info("Excel template filled successfully, size: {} bytes", out.size());
            return out.toByteArray();
        } catch (OutOfMemoryError e) {
            // OutOfMemoryError 是 Error，Spring 的 @ExceptionHandler(Exception.class) 兜底接不住，
            // 不在此处转成业务异常，调用方只会拿到连接断开，日志里也看不出是「数据太大」
            log.error("Excel template fill ran out of heap, template: {}", templateName, e);
            throw new DocumentGenerationException("文档生成所需内存超出服务可用上限，请减少数据量后重试（Java heap space）", e);
        } catch (Exception e) {
            log.error("Failed to fill Excel template", e);
            throw new RuntimeException("Failed to fill Excel template: " + e.getMessage(), e);
        }
    }

    /**
     * 校验渲染载荷是否超出配置的限额
     *
     * <p>三项各自独立：行数决定工作表要建多少个行对象，字段数决定单值填充的单元格数，图片张数决定 {@code addPicture}
     * 要往包里写多少份字节——任一维度失控都足以把堆吃满，所以分别判定而不是合并成一个 「总重量」。
     *
     * <p>全部取 {@code -1} 时本方法立即返回，不遍历数据。
     */
    private void checkLimits(
            Map<String, Object> data, Map<String, List<Map<String, Object>>> listData) {
        DocGenProperties.Limits limits = properties.getLimits();

        int maxListRows = limits.getMaxListRows();
        if (maxListRows > 0 && listData != null && !listData.isEmpty()) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : listData.entrySet()) {
                List<Map<String, Object>> rows = entry.getValue();
                if (rows != null && rows.size() > maxListRows) {
                    throw new PayloadTooLargeException(
                            "listData." + entry.getKey() + " 行数", rows.size(), maxListRows);
                }
            }
        }

        int maxDataFields = limits.getMaxDataFields();
        if (maxDataFields > 0 && data != null) {
            int fieldCount = countFields(data);
            if (fieldCount > maxDataFields) {
                throw new PayloadTooLargeException("data 字段数", fieldCount, maxDataFields);
            }
        }

        int maxImageCount = limits.getMaxImageCount();
        if (maxImageCount > 0) {
            int imageCount = countImages(data, listData);
            if (imageCount > maxImageCount) {
                throw new PayloadTooLargeException("图片载荷数", imageCount, maxImageCount);
            }
        }
    }

    /**
     * 统计单值数据展开后的字段数
     *
     * <p>按叶子节点计数而非顶层 key：一个 {@code {items: [...]}} 顶层只算一个 key，实际却要填几百个单元格， 只数顶层等于没数。
     */
    private int countFields(Map<String, Object> data) {
        int[] counter = {0};
        PayloadWalker.walk(
                data,
                (field, value) -> {
                    if (!(value instanceof Map)
                            && !(value instanceof Iterable)
                            && !(value instanceof Object[])) {
                        counter[0]++;
                    }
                    // 恒返回原值：本遍历只用于计数，不做任何替换
                    return value;
                });
        return counter[0];
    }

    /**
     * 统计请求中的图片与二维码载荷张数
     *
     * <p>单值与列表两侧都要算：列表侧图片逐行插入，是最容易失控的一侧。
     */
    private int countImages(
            Map<String, Object> data, Map<String, List<Map<String, Object>>> listData) {
        int[] counter = {0};
        PayloadWalker.PayloadHandler counterHandler =
                (field, value) -> {
                    if (imagePayloadConverter.isImagePayload(value)
                            || qrCodePayloadConverter.isQrCodePayload(value)) {
                        counter[0]++;
                    }
                    return value;
                };
        if (data != null) {
            PayloadWalker.walk(data, counterHandler);
        }
        if (listData != null) {
            for (List<Map<String, Object>> rows : listData.values()) {
                if (rows == null) {
                    continue;
                }
                for (Map<String, Object> row : rows) {
                    if (row != null) {
                        PayloadWalker.walk(row, counterHandler);
                    }
                }
            }
        }
        return counter[0];
    }

    /**
     * 基于模板填充 Excel 文档（默认填充第一个 sheet）
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.xlsx）
     * @param data 单值变量数据
     * @param listData 列表数据（用于循环填充）
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] fillTemplate(
            String templateName,
            Map<String, Object> data,
            Map<String, List<Map<String, Object>>> listData) {
        return fillTemplate(templateName, data, listData, null, null);
    }

    /**
     * 构建列表填充配置
     *
     * <p>{@code forceNewRow} 默认为 {@code true}。它同时影响内存、耗时、产出体积，三项结论不一致，
     * 因此选默认值要看的不是「哪个快」，而是「哪个能交出可打开的文件」。
     *
     * <p><b>关键事实：Excel / POI 对单个 XML 记录有 100 MB 硬上限。</b>xlsx 解包后 {@code xl/worksheets/sheet1.xml}
     * 超过这个尺寸，Excel 打开时就会提示「部分内容有问题，是否恢复」， 恢复过程会丢掉它读不下去的行，表现为「中间一段数据只剩最后一列」。
     *
     * <p>两种模式写出的 XML 形态不同，导致体积差很多（实测 25 列、单元格文本 6 字符、 {@code forceNewRow=false}）：
     *
     * <table>
     *   <tr><th>行数</th><th>sheet1.xml</th><th>能否正常打开</th></tr>
     *   <tr><td>60,000</td><td>81.7 MB</td><td>能</td></tr>
     *   <tr><td>67,000</td><td>91.3 MB</td><td>能</td></tr>
     *   <tr><td>70,000</td><td>95.5 MB</td><td><b>不能</b></td></tr>
     *   <tr><td>75,000</td><td>102.3 MB</td><td><b>不能</b></td></tr>
     * </table>
     *
     * <p>而 {@code true} 走共享字符串表（{@code sharedStrings.xml}），同样 67,000 行拆成 {@code sheet1.xml} 70.3 MB
     * + {@code sharedStrings.xml} 39.0 MB，两个都在上限内。
     *
     * <p>代价与收益对照（67,000 行 × 25 列）：
     *
     * <table>
     *   <tr><th>forceNewRow</th><th>耗时</th><th>文件</th><th>sheet1.xml</th><th>可打开</th></tr>
     *   <tr><td>true</td><td>26.6 秒</td><td>9.2 MB</td><td>70.3 MB</td><td>是</td></tr>
     *   <tr><td>false</td><td>4.1 秒</td><td>5.1 MB</td><td>91.3 MB</td><td>临界</td></tr>
     * </table>
     *
     * <p>{@code true} 慢 6.5 倍，但产出稳定可打开；{@code false} 快，但 XML 体积更大，越接近上限
     * 越容易产出「需要修复」的文件。大数据量下选快的那条，代价是拿到一个打不开的文件。
     *
     * <p><b>何时可以传 false：</b>数据量小（约 3 万行以内，视单元格文本长度而定），且模板里列表占位行 <b>下方没有</b>静态内容。{@code false}
     * 是原位扩展，下方内容不会被下推，可能被覆盖。
     *
     * <p>本方法不做「行数 × forceNewRow」的组合校验：安全行数取决于单元格文本长度、列数与部署机堆大小， 服务端算不出一个对所有环境都正确的阈值。超量时由 {@code
     * catch (OutOfMemoryError)} 转成 503， 或由调用方打开文件时发现需要修复。
     */
    private FillConfig buildFillConfig(Boolean forceNewRow) {
        // 未传时取 true：这是「产出能被 Excel 正常打开」的默认值，不是「最快」的默认值
        return FillConfig.builder()
                .forceNewRow(forceNewRow == null ? Boolean.TRUE : forceNewRow)
                .build();
    }

    /**
     * 解析调用方传入的模板名
     *
     * <p>命名规则（合法字符、必须为 .xlsx）由 {@link TemplateName} 统一判定，此处只声明本模块要的是 Excel 模板。
     */
    private static TemplateName parseTemplateName(String templateName) {
        return TemplateName.parse(templateName, TemplateKind.EXCEL);
    }

    /**
     * 构建 WriteSheet 实例
     *
     * <p>优先使用 sheetNo，其次使用 sheetName，都为空时默认使用索引 0。
     */
    private WriteSheet buildWriteSheet(Integer sheetNo, String sheetName) {
        if (sheetNo != null) {
            return EasyExcel.writerSheet(sheetNo, sheetName).build();
        } else if (sheetName != null && !sheetName.isBlank()) {
            return EasyExcel.writerSheet(sheetName).build();
        } else {
            return EasyExcel.writerSheet().build();
        }
    }

    /**
     * 预处理 Excel 模板载荷
     *
     * <p>遍历由 {@link PayloadWalker} 负责，本模块只在叶子上判定载荷并包装成 EasyExcel 的 {@code WriteCellData}。 与 Word
     * 侧共用同一份遍历 —— 那些「循环行内的图片载荷不生效」的差异，来自这里原先只扫描顶层、且 listData 分支完全不预处理，而非遍历器本身。
     *
     * @param data 原始渲染数据
     * @param cache 本次请求的图片缓存，同一张图只下载、只保留一份字节
     */
    private Map<String, Object> preprocessImagePayloads(
            Map<String, Object> data, ImageCache cache) {
        return PayloadWalker.walk(
                data,
                (field, value) -> {
                    if (imagePayloadConverter.isImagePayload(value)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) value;
                        log.debug("Converting Excel image payload for field: {}", field);
                        return toExcelImageCell(
                                field, cache.resolveImage(field, payload, imagePayloadConverter));
                    }
                    if (qrCodePayloadConverter.isQrCodePayload(value)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) value;
                        log.debug("Converting Excel qrcode payload for field: {}", field);
                        return toExcelImageCell(
                                field, cache.resolveQrCode(field, payload, qrCodePayloadConverter));
                    }
                    // 未认出时原样返回，遍历器据此继续向下
                    return value;
                });
    }

    /**
     * 预处理 Excel 列表载荷
     *
     * <p>列表填充与单值填充在 EasyExcel 内部走同一条 {@code doFill} / {@code converterAndSet} / {@code fillImage}
     * 链路（{@code AbstractExcelWriteExecutor.convert} 判 {@code originalFieldClass ==
     * WriteCellData.class} 后原样返回）， 因此列表里的图片载荷同样必须 提前转成 {@code WriteCellData}，否则 {@code Map}
     * 原样传入会因找不到匹配 converter 而失败。
     *
     * <p>注意：异常提示里的字段路径是 {@code logo} 而非 {@code items[0].logo}——{@link PayloadWalker} 对 {@code List}
     * 元素会拼上 {@code [下标]}，但本方法逐行调用 {@code walk}，每行都从字段名重新起头，
     * 行号因此不会进入路径。要定位到具体一行，需要看该行数据本身或调用方传入的行标识。
     *
     * @param rows 原始行数据
     * @param cache 本次请求的图片缓存
     */
    private List<Map<String, Object>> preprocessListPayloads(
            List<Map<String, Object>> rows, ImageCache cache) {
        if (rows == null) {
            return null;
        }
        List<Map<String, Object>> processed = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            processed.add(preprocessImagePayloads(row, cache));
        }
        return processed;
    }

    /**
     * 将通用图片结果转换为 EasyExcel 图片单元格数据
     *
     * <p>入参取 {@link ConvertedImage} 而非载荷 {@code Map}：图片与二维码的字节来源不同（URL 下载 / ZXing 本地生成）， 但落到
     * EasyExcel 的包装动作完全相同。
     */
    private WriteCellData<Void> toExcelImageCell(String fieldName, ConvertedImage image) {
        WriteCellData<Void> cellData = new WriteCellData<>(CellDataTypeEnum.EMPTY);
        ImageData imageData = new ImageData();
        imageData.setImage(image.bytes());
        imageData.setImageType(toExcelImageType(fieldName, image.format()));
        imageData.setRelativeLastRowIndex(0);
        imageData.setRelativeLastColumnIndex(0);
        imageData.setRight(Math.max(0, image.width()));
        imageData.setBottom(Math.max(0, image.height()));
        cellData.setImageDataList(List.of(imageData));
        return cellData;
    }

    /** 将图片格式映射为 EasyExcel 图片类型 */
    private ImageData.ImageType toExcelImageType(String fieldName, String format) {
        return switch (format) {
            case "png" -> ImageData.ImageType.PICTURE_TYPE_PNG;
            case "jpg", "jpeg" -> ImageData.ImageType.PICTURE_TYPE_JPEG;
            default -> throw new InvalidImagePayloadException(
                    fieldName,
                    "Unsupported image format: " + format + ". Supported: png, jpg, jpeg");
        };
    }

    /**
     * 构建 EasyExcel 所需的表头格式
     *
     * <p>EasyExcel 的动态表头需要 List&lt;List&lt;String&gt;&gt; 格式， 每个内层 List 代表一列的多级表头（本实现仅支持单级）。
     *
     * @param headers 表头列名列表
     * @return EasyExcel 格式的表头
     */
    private List<List<String>> buildHead(List<String> headers) {
        List<List<String>> headList = new ArrayList<>();
        for (String header : headers) {
            headList.add(Collections.singletonList(header));
        }
        return headList;
    }
}
