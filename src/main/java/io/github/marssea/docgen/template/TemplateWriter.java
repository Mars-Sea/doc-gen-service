package io.github.marssea.docgen.template;

import io.github.marssea.docgen.template.TemplateName.TemplateKind;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模板写侧
 *
 * <p>把「上传模板文件、删除模板文件」收敛为一处。 与 {@link TemplateRepository} 分开：读侧与写侧的接口面向不同调用方，错误语义也不同 —— 读侧找不到文件是
 * {@link io.github.marssea.docgen.exception.TemplateNotFoundException}， 写侧收到非法输入是 {@link
 * IllegalArgumentException}。
 *
 * <p>命名规则不在这里实现：写侧拿到上传文件名后先做字符清洗，再交给{@link TemplateName} 判定合法性， 保证「凡是能落盘的名字，之后一定能在读侧被合法解析」这条闭环。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateWriter {

    /**
     * 上传文件名的非法字符集合
     *
     * <p>上传侧与寻址侧规则刻意不同：上传的人是手里拿着文件的人，名字常带空格与括号， 因为一个空格就拒绝整个文件不合理；这里的职责是把名字收敛到 {@link TemplateName}
     * 的白名单之内，再由白名单做最终判定。
     */
    private static final String ILLEGAL_FILENAME_CHARS = "[\\\\/:*?\"<>|]";

    private final TemplateRepository repository;

    /**
     * 上传模板文件
     *
     * <p>同名模板会被覆盖。清洗后的名字若仍不合法（例如用户上传的是 {@code test.pdf}）则整体拒绝。
     *
     * @param file 上传的模板文件
     * @return 落盘后的文件名
     * @throws IOException 文件写入失败
     * @throws IllegalArgumentException 文件为空、文件名为空或文件类型不受支持
     */
    public String upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String safeFilename = sanitize(originalFilename);
        TemplateName name = TemplateName.parse(safeFilename, detectKind(safeFilename));

        Path targetPath = repository.resolveForWrite(name).toPath();
        Path templateDir = targetPath.getParent();
        if (templateDir != null && !Files.exists(templateDir)) {
            Files.createDirectories(templateDir);
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Template uploaded successfully: {}", name.value());
        return name.value();
    }

    /**
     * 删除模板文件
     *
     * @param name 已校验的模板名
     * @return 删除成功返回 true；文件本就不存在返回 false
     * @throws IOException 文件删除失败
     */
    public boolean delete(TemplateName name) throws IOException {
        File file = repository.resolveForWrite(name);
        if (!Files.exists(file.toPath())) {
            log.warn("Template not found for deletion: {}", name.value());
            return false;
        }
        if (!Files.isRegularFile(file.toPath())) {
            throw new IllegalArgumentException("只能删除文件，不能删除目录");
        }
        Files.delete(file.toPath());
        log.info("Template deleted successfully: {}", name.value());
        return true;
    }

    /** 判断上传文件的类型，据此决定允许的扩展名 */
    private TemplateKind detectKind(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return TemplateKind.WORD;
        }
        if (lower.endsWith(".xlsx")) {
            return TemplateKind.EXCEL;
        }
        throw new IllegalArgumentException("仅支持 .docx 和 .xlsx 格式的模板文件");
    }

    /** 把文件名中的路径分隔符与 Windows 专用字符替换为下划线 */
    private String sanitize(String originalFilename) {
        return originalFilename.replaceAll(ILLEGAL_FILENAME_CHARS, "_");
    }
}
