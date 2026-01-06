package io.github.marssea.docgen.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * </ul>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Service
public class ExcelService {

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
