package io.github.marssea.docgen.template;

import io.github.marssea.docgen.config.DocGenProperties;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模板读侧
 *
 * <p>把「从模板目录中找到一个模板、读出它的字节、列出目录里有什么」收敛为一处。 渲染模块（Word / Excel）与模板控制器都穿过这一个接缝取模板， 不再各自拼接 {@code
 * templatePath} 与各自判断文件是否存在。
 *
 * <p>持有模板目录配置：写侧需要算落盘目标时通过 {@link #resolveForWrite(TemplateName)} 取路径， 避免基路径的读法被复制成两份。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRepository {

    private static final String[] TEMPLATE_EXTENSIONS = {".docx", ".xlsx"};

    private final DocGenProperties properties;

    /**
     * 定位模板文件
     *
     * @param name 已校验的模板名
     * @return 模板目录内的文件对象，调用方可直接交给渲染库（poi-tl 与 EasyExcel 均接受 File）
     * @throws TemplateNotFoundException 模板文件不存在
     */
    public File locate(TemplateName name) {
        File file = resolveForWrite(name);
        if (!file.exists() || !file.isFile()) {
            log.warn("Template not found: {}", name.value());
            throw new TemplateNotFoundException(name.value());
        }
        return file;
    }

    /**
     * 解析写侧落盘目标路径
     *
     * <p>与 {@link #locate(TemplateName)} 同一套解析规则，不校验文件是否已存在：写侧允许覆盖同名模板。
     *
     * @param name 已校验的模板名
     * @return 目标文件对象
     */
    public File resolveForWrite(TemplateName name) {
        Path templateDir = Paths.get(properties.getTemplatePath());
        return templateDir.resolve(name.value()).toFile();
    }

    /**
     * 读出模板字节
     *
     * @param name 已校验的模板名
     * @return 模板文件内容
     * @throws TemplateNotFoundException 模板文件不存在
     * @throws IOException 文件读取失败
     */
    public byte[] read(TemplateName name) throws IOException {
        File file = locate(name);
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 列出模板目录下的模板文件及其属性
     *
     * <p>除文件名外一并返回大小与最后修改时间：管理页面要显示这两项， 而调用方自行 stat 一遍等于把模板目录的位置与读法复制出去。
     *
     * @return 模板信息列表，按名称排序；目录不存在时返回空列表
     * @throws IOException 目录读取或属性读取失败
     */
    public List<TemplateInfo> list() throws IOException {
        Path templateDir = Paths.get(properties.getTemplatePath());
        if (!Files.exists(templateDir)) {
            log.warn("Template directory does not exist: {}", templateDir);
            return List.of();
        }
        try (Stream<Path> paths = Files.list(templateDir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(TemplateRepository::hasTemplateExtension)
                    .sorted()
                    .map(name -> toInfo(templateDir.resolve(name), name))
                    .toList();
        }
    }

    /**
     * 读出单个模板文件的属性
     *
     * <p>读取失败不中止整个列表：一个文件属性读不出来（例如被占用）不代表其余模板不可用， 此时大小回退为 0、时间回退为 epoch。
     */
    private static TemplateInfo toInfo(Path path, String name) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return TemplateInfo.of(
                    name, attributes.size(), attributes.lastModifiedTime().toInstant());
        } catch (IOException e) {
            log.warn("Failed to read attributes of template: {}", name, e);
            return TemplateInfo.of(name, 0L, Instant.EPOCH);
        }
    }

    private static boolean hasTemplateExtension(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String extension : TEMPLATE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
