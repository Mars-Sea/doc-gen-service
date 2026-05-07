package io.github.marssea.docgen.exception;

/**
 * 无效图片载荷异常
 *
 * <p>当请求数据中包含无效的图片载荷时抛出此异常。 包括以下场景：
 *
 * <ul>
 *   <li>URL 协议不是 http 或 https
 *   <li>不支持的图片格式（仅支持 png、jpg、jpeg）
 *   <li>缺少必填字段（url、format、width、height）
 *   <li>URL 格式无效
 *   <li>图片下载失败
 * </ul>
 *
 * <p>该异常会被全局异常处理器捕获并返回 HTTP 400 (BAD_REQUEST) 响应。
 *
 * @author Mars-Sea
 * @since 0.0.5
 */
public class InvalidImagePayloadException extends RuntimeException {

    /** 发生错误的字段名（可选） */
    private final String fieldName;

    /**
     * 构造函数
     *
     * @param fieldName 发生错误的字段名
     * @param message 错误消息
     */
    public InvalidImagePayloadException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    /**
     * 构造函数（带原因）
     *
     * @param fieldName 发生错误的字段名
     * @param message 错误消息
     * @param cause 原始异常
     */
    public InvalidImagePayloadException(String fieldName, String message, Throwable cause) {
        super(message, cause);
        this.fieldName = fieldName;
    }

    /**
     * 获取发生错误的字段名
     *
     * @return 字段名，可能为 null
     */
    public String getFieldName() {
        return fieldName;
    }
}
