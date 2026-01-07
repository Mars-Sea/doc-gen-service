package io.github.marssea.docgen.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 模板初始化器
 * 用于在应用启动时生成测试用的 Word 模板文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateInitializer implements CommandLineRunner {

    private final DocGenProperties properties;

    @Override
    public void run(String... args) throws Exception {
        Path templateDir = Paths.get(properties.getTemplatePath());
        if (!Files.exists(templateDir)) {
            Files.createDirectories(templateDir);
        }

        // 简单模板
        createSimpleTemplate(templateDir);

        // 单表格循环模板
        createLoopTableTemplate(templateDir);

        // 多表格循环模板
        createMultiTableTemplate(templateDir);
    }

    private void createSimpleTemplate(Path templateDir) throws Exception {
        Path templatePath = templateDir.resolve("test-template.docx");
        if (Files.exists(templatePath)) {
            log.info("Test template already exists at: {}", templatePath);
            return;
        }

        log.info("Creating test template at: {}", templatePath);
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Document Title: {{title}}");
            titleRun.setBold(true);
            titleRun.setFontSize(20);

            XWPFParagraph dateParagraph = document.createParagraph();
            XWPFRun dateRun = dateParagraph.createRun();
            dateRun.setText("Date: {{date}}");
            dateRun.setItalic(true);

            XWPFParagraph contentParagraph = document.createParagraph();
            XWPFRun contentRun = contentParagraph.createRun();
            contentRun.setText("Content: {{content}}");

            try (FileOutputStream out = new FileOutputStream(templatePath.toFile())) {
                document.write(out);
            }
        }
        log.info("Test template created successfully.");
    }

    private void createLoopTableTemplate(Path templateDir) throws Exception {
        Path loopTemplatePath = templateDir.resolve("loop-table-template.docx");
        if (Files.exists(loopTemplatePath)) {
            log.info("Loop table template already exists at: {}", loopTemplatePath);
            return;
        }

        log.info("Creating loop table template at: {}", loopTemplatePath);
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph p = document.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("Monthly Report for {{month}}");
            r.setBold(true);
            r.setFontSize(16);

            createLoopTable(document, "goods", new String[] { "Product Name", "Category", "Price" },
                    new String[] { "name", "category", "price" });

            try (FileOutputStream out = new FileOutputStream(loopTemplatePath.toFile())) {
                document.write(out);
            }
        }
        log.info("Loop table template created successfully.");
    }

    private void createMultiTableTemplate(Path templateDir) throws Exception {
        Path multiTemplatePath = templateDir.resolve("multi-table-template.docx");
        if (Files.exists(multiTemplatePath)) {
            log.info("Multi-table template already exists at: {}", multiTemplatePath);
            return;
        }

        log.info("Creating multi-table template at: {}", multiTemplatePath);
        try (XWPFDocument document = new XWPFDocument()) {
            // 标题
            XWPFParagraph title = document.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Monthly Report - {{month}}");
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            // 第一个表格：商品列表
            XWPFParagraph p1 = document.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Product List:");
            r1.setBold(true);

            createLoopTable(document, "products",
                    new String[] { "Product Name", "Category", "Price" },
                    new String[] { "name", "category", "price" });

            // 分隔段落
            document.createParagraph();

            // 第二个表格：员工列表
            XWPFParagraph p2 = document.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("Employee List:");
            r2.setBold(true);

            createLoopTable(document, "employees",
                    new String[] { "Name", "Department", "Score" },
                    new String[] { "name", "dept", "score" });

            // 分隔段落
            document.createParagraph();

            // 第三个表格：订单列表
            XWPFParagraph p3 = document.createParagraph();
            XWPFRun r3 = p3.createRun();
            r3.setText("Order List:");
            r3.setBold(true);

            createLoopTable(document, "orders",
                    new String[] { "Order ID", "Customer", "Amount" },
                    new String[] { "orderId", "customer", "amount" });

            try (FileOutputStream out = new FileOutputStream(multiTemplatePath.toFile())) {
                document.write(out);
            }
        }
        log.info("Multi-table template created successfully.");
    }

    /**
     * 创建循环表格的通用方法
     * 
     * @param document  Word文档
     * @param fieldName 循环字段名（如 goods, employees）
     * @param headers   表头数组
     * @param fields    字段名数组（使用方括号语法）
     */
    private void createLoopTable(XWPFDocument document, String fieldName,
            String[] headers, String[] fields) {
        org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable();
        table.setWidth("100%");

        // Row 0: 表头行
        org.apache.poi.xwpf.usermodel.XWPFTableRow header = table.getRow(0);
        header.getCell(0).setText(headers[0]);
        for (int i = 1; i < headers.length; i++) {
            header.addNewTableCell().setText(headers[i]);
        }

        // Row 1: 占位行 - {{fieldName}} 必须放在循环行的上一行
        org.apache.poi.xwpf.usermodel.XWPFTableRow placeholderRow = table.createRow();
        while (placeholderRow.getTableCells().size() < headers.length) {
            placeholderRow.addNewTableCell();
        }
        XWPFParagraph pPlaceholder = placeholderRow.getCell(0).getParagraphs().get(0);
        while (!pPlaceholder.getRuns().isEmpty())
            pPlaceholder.removeRun(0);
        XWPFRun rPlaceholder = pPlaceholder.createRun();
        rPlaceholder.setText("{{" + fieldName + "}}");
        for (int i = 1; i < headers.length; i++) {
            placeholderRow.getCell(i).setText("");
        }

        // Row 2: 循环行 - 使用方括号 [] 语法
        org.apache.poi.xwpf.usermodel.XWPFTableRow dataRow = table.createRow();
        while (dataRow.getTableCells().size() < headers.length) {
            dataRow.addNewTableCell();
        }

        for (int i = 0; i < fields.length; i++) {
            XWPFParagraph pField = dataRow.getCell(i).getParagraphs().get(0);
            while (!pField.getRuns().isEmpty())
                pField.removeRun(0);
            XWPFRun rField = pField.createRun();
            rField.setText("[" + fields[i] + "]");
        }
    }
}
