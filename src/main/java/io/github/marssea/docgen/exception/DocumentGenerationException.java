package io.github.marssea.docgen.exception;

/**
 * 文档生成资源不足异常
 *
 * <p>用于把 {@link OutOfMemoryError} 转成可被全局异常处理器捕获的业务异常。{@code OutOfMemoryError} 是 {@link Error}，不属于
 * {@code Exception} 体系，Spring 的 {@code @ExceptionHandler(Exception.class)} 兜底
 * 处理器接不住它，只能在生成入口就地捕获再转换。
 *
 * <p>捕获 {@code OutOfMemoryError} 后进程并非必然可用：堆满时 GC 通常能回收出空间，服务可继续处理后续 请求；但也可能进入持续 OOM
 * 的半死状态，那种情况由容器的重启策略兜底。因此这里只负责「让当前请求 得到一个明确的中文答复」，不声称进程已恢复。
 *
 * <p>该异常会被全局异常处理器捕获并返回 HTTP 503 (SERVICE_UNAVAILABLE)。
 *
 * @author Mars-Sea
 * @since 0.0.8
 */
public class DocumentGenerationException extends RuntimeException {

    /**
     * 构造函数
     *
     * @param message 错误消息
     * @param cause 原始错误（通常是 {@link OutOfMemoryError}）
     */
    public DocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
