package io.github.marssea.docgen.config;

import io.github.marssea.docgen.exception.DocumentGenerationException;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import io.github.marssea.docgen.exception.PayloadTooLargeException;
import io.github.marssea.docgen.exception.TemplateNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * <p>统一处理应用程序中抛出的异常，将其转换为标准的 HTTP 响应格式。 不同类型的异常会映射到不同的 HTTP 状态码，便于客户端识别错误类型。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理模板未找到异常
     *
     * <p>当用户请求的模板文件不存在时返回 422 Unprocessable Entity 使用 422 而非 404，因为 API 路径存在，只是引用的模板资源不存在
     *
     * @param e 模板未找到异常
     * @return HTTP 422 响应
     */
    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(TemplateNotFoundException e) {
        log.warn("Template not found: {}", e.getTemplateName());
        return buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY, "TEMPLATE_NOT_FOUND", e.getMessage());
    }

    /**
     * 处理参数校验异常
     *
     * <p>当请求参数不满足 @Valid 校验规则时返回 400 Bad Request
     *
     * @param e 参数校验异常
     * @return HTTP 400 响应，包含具体的校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getMessage());

        // 提取第一个校验错误信息
        String errorMessage =
                e.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .orElse("Validation failed");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errorMessage);
    }

    /**
     * 处理非法参数异常
     *
     * <p>当请求参数不合法时返回 400 Bad Request（如路径遍历、非法文件类型等）
     *
     * @param e 非法参数异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }

    /**
     * 处理无效图片载荷异常
     *
     * <p>当请求数据中的图片载荷校验失败时返回 400 Bad Request 包括 URL 协议错误、格式不支持、必填字段缺失、图片下载失败等场景
     *
     * @param e 无效图片载荷异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(InvalidImagePayloadException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidImagePayload(
            InvalidImagePayloadException e) {
        log.warn("Invalid image payload: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_PAYLOAD", e.getMessage());
    }

    /**
     * 处理渲染载荷超限异常
     *
     * <p>当请求数据超出 {@code docgen.limits} 配置的限额时返回 413 Payload Too Large。 用 413 而非 400：请求本身合法，
     * 只是量太大，这个区分让调用方知道「减少数据量」比「修正参数」更可能是解法。
     *
     * @param e 载荷超限异常
     * @return HTTP 413 响应
     */
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handlePayloadTooLarge(PayloadTooLargeException e) {
        log.warn(
                "Payload too large: dimension={}, actual={}, limit={}",
                e.getDimension(),
                e.getActual(),
                e.getLimit());
        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", e.getMessage());
    }

    /**
     * 处理文档生成资源不足异常
     *
     * <p>当生成过程抛出 {@code OutOfMemoryError} 并被就地转为本异常时返回 503 Service Unavailable。 用 503 而非 500：
     * 这是服务侧的容量问题，重试或降低数据量后可能成功，而不是请求本身的错误。
     *
     * @param e 文档生成资源不足异常
     * @return HTTP 503 响应
     */
    @ExceptionHandler(DocumentGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentGeneration(
            DocumentGenerationException e) {
        log.error("Document generation exhausted resources: {}", e.getMessage(), e.getCause());
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE, "RESOURCE_EXHAUSTED", e.getMessage());
    }

    /**
     * 处理 IO 异常
     *
     * <p>当文件读写操作失败时返回 500 Internal Server Error
     *
     * @param e IO 异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException e) {
        log.error("IO error occurred: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "IO_ERROR",
                "File operation failed: " + e.getMessage());
    }

    /**
     * 处理所有未捕获的异常
     *
     * <p>作为兜底处理器，捕获所有其他类型的异常并返回 500 Internal Server Error。
     *
     * <p>响应体不透传 {@code e.getMessage()}：底层异常消息常带服务器路径、SQL 片段、上游英文错误码，
     * 对调用方无意义且属于信息泄露。真实原因已记入日志，响应只给一个可读的概括。
     *
     * @param e 未处理的异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误，请稍后重试或联系管理员（详情见服务端日志）");
    }

    /**
     * 构建标准错误响应
     *
     * @param status HTTP 状态码
     * @param code 错误代码
     * @param message 错误消息
     * @return 标准格式的错误响应
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(body);
    }
}
