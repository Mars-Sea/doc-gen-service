package io.github.marssea.docgen.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 生成的文档响应
 *
 * <p>把「文档字节 → 可下载的 HTTP 响应」收敛为一处：调用方只需要交出字节与用户想要的文件名， 扩展名 → MediaType → 文件名清洗 → RFC 5987 编码 →
 * Content-Disposition 全部由本模块决定。
 *
 * <p>收敛前，DocController 四个端点各自内联了同一套逻辑（默认名、清洗正则、URLEncoder、Content-Disposition 双写、MediaType、空字节校验），
 * 彼此只差默认名与扩展名。那种形态下，任何一次头语义调整都要在四处同步，漏一处的代价是某个端点 的中文文件名下载后乱码。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
public final class GeneratedDocumentResponse {

    private static final String WORD_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 需要替换为下划线的字符
     *
     * <p>Windows 文件名保留字与路径分隔符。与上传侧 {@code TemplateWriter} 的清洗规则同源，但服务对象不同： 这里是响应给调用方的下载
     * 名，那边是落盘前的模板名。
     */
    private static final String ILLEGAL_FILENAME_CHARS = "[\\\\/:*?\"<>|]";

    private final byte[] content;
    private final DocumentType type;
    private final String downloadName;

    private GeneratedDocumentResponse(byte[] content, DocumentType type, String downloadName) {
        this.content = content;
        this.type = type;
        this.downloadName = downloadName;
    }

    /**
     * 构造文档响应
     *
     * @param content 文档字节
     * @param type 文档类型，决定扩展名与 MediaType
     * @param requestedName 调用方请求的文件名，可为空；为空时用该文档类型的默认名
     * @return 响应构造器
     * @throws IllegalStateException 内容为空
     */
    public static GeneratedDocumentResponse of(
            byte[] content, DocumentType type, String requestedName) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("生成的文档内容为空");
        }
        String name =
                requestedName == null || requestedName.isBlank() ? type.defaultName : requestedName;
        return new GeneratedDocumentResponse(content, type, sanitize(name));
    }

    /** 转为可直接返回给调用方的响应实体 */
    public ResponseEntity<byte[]> toResponseEntity() {
        String encoded = encode(downloadName + "." + type.extension);
        return ResponseEntity.ok()
                .contentType(Objects.requireNonNull(MediaType.parseMediaType(type.contentType)))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 按 RFC 5987 编码文件名
     *
     * <p>中文文件名必须经百分号编码才能安全进入 filename* 段；同时 {@code +} 要还原为 {@code %20}， 否则 URLEncoder 会把空格编码成加号，
     * 浏览器下载下来文件名里的空格变成加号。
     */
    private static String encode(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String sanitize(String fileName) {
        return fileName.replaceAll(ILLEGAL_FILENAME_CHARS, "_");
    }

    /** 生成的文档类型，决定默认名、扩展名与 MediaType */
    public enum DocumentType {
        /** Word 文档 */
        WORD("generated", "docx", WORD_CONTENT_TYPE),
        /** 批量生成的 Word 文档 */
        WORD_BATCH("batch_generated", "docx", WORD_CONTENT_TYPE),
        /** Excel 文档 */
        EXCEL("generated", "xlsx", EXCEL_CONTENT_TYPE),
        /** 基于模板填充的 Excel 文档 */
        EXCEL_FILL("filled", "xlsx", EXCEL_CONTENT_TYPE);

        private final String defaultName;
        private final String extension;
        private final String contentType;

        DocumentType(String defaultName, String extension, String contentType) {
            this.defaultName = defaultName;
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    /** 供日志使用：清洗后的文件名（不含扩展名） */
    public String downloadName() {
        return downloadName;
    }

    /** 供日志使用：文档字节数 */
    public int size() {
        return content.length;
    }
}
