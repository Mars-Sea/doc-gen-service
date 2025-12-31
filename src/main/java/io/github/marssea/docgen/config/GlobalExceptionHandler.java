package io.github.marssea.docgen.config;

import io.github.marssea.docgen.exception.TemplateNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 统一处理应用程序中抛出的异常，将其转换为标准的 HTTP 响应格式。
 * 不同类型的异常会映射到不同的 HTTP 状态码，便于客户端识别错误类型。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理模板未找到异常
     * <p>
     * 当用户请求的模板文件不存在时返回 404 Not Found
     *
     * @param e 模板未找到异常
     * @return HTTP 404 响应
     */
    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(TemplateNotFoundException e) {
        log.warn("Template not found: {}", e.getTemplateName());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", e.getMessage());
    }

    /**
     * 处理参数校验异常
     * <p>
     * 当请求参数不满足 @Valid 校验规则时返回 400 Bad Request
     *
     * @param e 参数校验异常
     * @return HTTP 400 响应，包含具体的校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getMessage());

        // 提取第一个校验错误信息
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errorMessage);
    }

    /**
     * 处理 IO 异常
     * <p>
     * 当文件读写操作失败时返回 500 Internal Server Error
     *
     * @param e IO 异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException e) {
        log.error("IO error occurred: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR",
                "File operation failed: " + e.getMessage());
    }

    /**
     * 处理所有未捕获的异常
     * <p>
     * 作为兜底处理器，捕获所有其他类型的异常并返回 500 Internal Server Error
     *
     * @param e 未处理的异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error: " + e.getMessage());
    }

    /**
     * 构建标准错误响应
     *
     * @param status  HTTP 状态码
     * @param code    错误代码
     * @param message 错误消息
     * @return 标准格式的错误响应
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
