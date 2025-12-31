package io.github.marssea.docgen.service;

import io.github.marssea.docgen.config.DocGenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * 模板文件管理服务
 * <p>
 * 提供模板文件的上传和列表查询功能。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final DocGenProperties properties;

    /**
     * 获取模板目录路径
     *
     * @return 模板目录 Path 对象
     */
    private Path getTemplateDir() {
        return Paths.get(properties.getTemplatePath());
    }

    /**
     * 上传模板文件
     * <p>
     * 将上传的文件保存到模板目录。如果文件已存在，将会覆盖。
     *
     * @param file 上传的模板文件
     * @return 保存后的文件名
     * @throws IOException              文件写入异常
     * @throws IllegalArgumentException 文件名无效或文件类型不支持
     */
    public String uploadTemplate(MultipartFile file) throws IOException {
        // 校验文件
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 校验文件类型（只允许 .docx 和 .xlsx）
        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .docx 和 .xlsx 格式的模板文件");
        }

        // 清理文件名中的非法字符
        String safeFilename = originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // 确保模板目录存在
        Path templateDir = getTemplateDir();
        if (!Files.exists(templateDir)) {
            Files.createDirectories(templateDir);
        }

        // 保存文件
        Path targetPath = templateDir.resolve(safeFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Template uploaded successfully: {}", safeFilename);
        return safeFilename;
    }

    /**
     * 获取所有模板文件列表
     * <p>
     * 列出模板目录下所有的 .docx 和 .xlsx 文件。
     *
     * @return 模板文件名列表
     * @throws IOException 目录读取异常
     */
    public List<String> listTemplates() throws IOException {
        Path templateDir = getTemplateDir();

        // 如果目录不存在，返回空列表
        if (!Files.exists(templateDir)) {
            log.warn("Template directory does not exist: {}", templateDir);
            return List.of();
        }

        // 列出所有 .docx 和 .xlsx 文件
        try (Stream<Path> paths = Files.list(templateDir)) {
            List<String> templates = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".docx") || lower.endsWith(".xlsx");
                    })
                    .sorted()
                    .toList();

            log.info("Found {} templates in directory: {}", templates.size(), templateDir);
            return templates;
        }
    }

    /**
     * 检查模板文件是否存在
     *
     * @param templateName 模板文件名
     * @return 是否存在
     */
    public boolean templateExists(String templateName) {
        Path templatePath = getTemplateDir().resolve(templateName);
        return Files.exists(templatePath) && Files.isRegularFile(templatePath);
    }

    /**
     * 删除模板文件
     * <p>
     * 从模板目录中删除指定的模板文件。
     *
     * @param templateName 模板文件名
     * @return 是否删除成功
     * @throws IOException              文件删除异常
     * @throws IllegalArgumentException 文件名无效
     */
    public boolean deleteTemplate(String templateName) throws IOException {
        // 校验文件名
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("模板文件名不能为空");
        }

        // 防止路径遍历攻击
        if (templateName.contains("..") || templateName.contains("/") || templateName.contains("\\")) {
            throw new IllegalArgumentException("非法的文件名");
        }

        Path templatePath = getTemplateDir().resolve(templateName);

        // 检查文件是否存在
        if (!Files.exists(templatePath)) {
            log.warn("Template not found for deletion: {}", templateName);
            return false;
        }

        // 检查是否为普通文件
        if (!Files.isRegularFile(templatePath)) {
            throw new IllegalArgumentException("只能删除文件，不能删除目录");
        }

        // 删除文件
        Files.delete(templatePath);
        log.info("Template deleted successfully: {}", templateName);
        return true;
    }

    /**
     * 下载模板文件
     * <p>
     * 读取模板文件内容并返回字节数组。
     *
     * @param templateName 模板文件名
     * @return 文件内容字节数组
     * @throws IOException              文件读取异常
     * @throws IllegalArgumentException 文件名无效或文件不存在
     */
    public byte[] downloadTemplate(String templateName) throws IOException {
        // 校验文件名
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("模板文件名不能为空");
        }

        // 防止路径遍历攻击
        if (templateName.contains("..") || templateName.contains("/") || templateName.contains("\\")) {
            throw new IllegalArgumentException("非法的文件名");
        }

        Path templatePath = getTemplateDir().resolve(templateName);

        // 检查文件是否存在
        if (!Files.exists(templatePath) || !Files.isRegularFile(templatePath)) {
            throw new IllegalArgumentException("模板文件不存在: " + templateName);
        }

        log.info("Template downloaded: {}", templateName);
        return Files.readAllBytes(templatePath);
    }
}
