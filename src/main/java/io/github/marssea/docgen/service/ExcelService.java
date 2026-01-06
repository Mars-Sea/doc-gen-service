package io.github.marssea.docgen.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import io.github.marssea.docgen.util.TemplateValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Excel 文档生成服务
 * <p>
 * 使用 <a href="https://easyexcel.opensource.alibaba.com/">EasyExcel</a> 库处理
 * Excel 生成。
 * 该服务支持动态表头和数据填充，无需预定义实体类。
 * <p>
 * 主要功能：
 * <ul>
 * <li>动态配置表头列名</li>
 * <li>填充二维数据到工作表</li>
 * <li>自动调整列宽</li>
 * <li>基于模板的数据填充</li>
 * </ul>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final DocGenProperties properties;

    /**
     * 根据表头和数据生成 Excel 文档
     * <p>
     * 使用 EasyExcel 动态生成 Excel 文件，无需预定义数据模型类。
     *
     * @param sheetName 工作表名称，为空时默认使用 "Sheet1"
     * @param headers   表头列名列表
     * @param data      二维数据列表，每行数据的列顺序需与 headers 对应
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] generateExcel(String sheetName, List<String> headers, List<List<Object>> data) {
        // 设置默认工作表名称
        if (sheetName == null || sheetName.isBlank()) {
            sheetName = "Sheet1";
        }

        log.info("Generating Excel document, sheet: {}, columns: {}, rows: {}",
                sheetName, headers.size(), data.size());

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
        } catch (Exception e) {
            log.error("Failed to generate Excel document", e);
            throw new RuntimeException("Failed to generate Excel document: " + e.getMessage(), e);
        }
    }

    /**
     * 基于模板填充 Excel 文档
     * <p>
     * 使用 EasyExcel 模板填充功能，支持：
     * <ul>
     * <li>单值变量替换: {variable}</li>
     * <li>列表数据循环: {.field}</li>
     * </ul>
     *
     * @param templateName 模板文件名（需包含扩展名，如 template.xlsx）
     * @param data         单值变量数据
     * @param listData     列表数据（用于循环填充）
     * @return 生成的 Excel 文档二进制流
     */
    public byte[] fillTemplate(String templateName,
            Map<String, Object> data,
            Map<String, List<Map<String, Object>>> listData) {
        // 安全校验：防止路径遍历攻击，验证扩展名
        TemplateValidationUtil.validateExcelTemplateExtension(templateName);

        // 构建模板文件的完整路径
        Path templatePath = Paths.get(properties.getTemplatePath(), templateName);
        File templateFile = templatePath.toFile();

        // 校验模板文件是否存在
        if (!templateFile.exists()) {
            log.error("Template file not found at: {}", templatePath);
            throw new TemplateNotFoundException(templateName, "Template not found at: " + templatePath);
        }

        log.info("Filling Excel template: {}, data keys: {}, list keys: {}",
                templatePath,
                data != null ? data.keySet() : "null",
                listData != null ? listData.keySet() : "null");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 使用模板创建 ExcelWriter
            try (ExcelWriter excelWriter = EasyExcel.write(out)
                    .withTemplate(templateFile)
                    .build()) {

                WriteSheet writeSheet = EasyExcel.writerSheet().build();

                // 配置列表填充：自动换行
                FillConfig fillConfig = FillConfig.builder()
                        .forceNewRow(Boolean.TRUE)
                        .build();

                // 先填充列表数据（因为列表会改变行数）
                if (listData != null && !listData.isEmpty()) {
                    for (Map.Entry<String, List<Map<String, Object>>> entry : listData.entrySet()) {
                        log.debug("Filling list data for key: {}, rows: {}",
                                entry.getKey(), entry.getValue().size());
                        excelWriter.fill(entry.getValue(), fillConfig, writeSheet);
                    }
                }

                // 再填充单值数据
                if (data != null && !data.isEmpty()) {
                    excelWriter.fill(data, writeSheet);
                }
            }

            log.info("Excel template filled successfully, size: {} bytes", out.size());
            return out.toByteArray();
        } catch (TemplateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fill Excel template", e);
            throw new RuntimeException("Failed to fill Excel template: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 EasyExcel 所需的表头格式
     * <p>
     * EasyExcel 的动态表头需要 List&lt;List&lt;String&gt;&gt; 格式，
     * 每个内层 List 代表一列的多级表头（本实现仅支持单级）。
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
