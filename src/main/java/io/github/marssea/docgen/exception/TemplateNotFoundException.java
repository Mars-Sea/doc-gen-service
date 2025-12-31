package io.github.marssea.docgen.exception;

/**
 * 模板文件未找到异常
 * <p>
 * 当请求的模板文件在模板目录中不存在时抛出此异常。
 * 该异常会被全局异常处理器捕获并返回 HTTP 404 响应。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
public class TemplateNotFoundException extends RuntimeException {

    /**
     * 模板文件名
     */
    private final String templateName;

    /**
     * 构造函数
     *
     * @param templateName 未找到的模板文件名
     */
    public TemplateNotFoundException(String templateName) {
        super("Template not found: " + templateName);
        this.templateName = templateName;
    }

    /**
     * 构造函数（带自定义消息）
     *
     * @param templateName 未找到的模板文件名
     * @param message      自定义错误消息
     */
    public TemplateNotFoundException(String templateName, String message) {
        super(message);
        this.templateName = templateName;
    }

    /**
     * 获取模板文件名
     *
     * @return 模板文件名
     */
    public String getTemplateName() {
        return templateName;
    }
}
