package io.github.marssea.docgen.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Servlet 过滤器注册
 *
 * <p>请求体体积上限必须在任何会读取请求体的组件之前生效：Jackson 反序列化发生在 Spring MVC 的 {@code DispatcherServlet}
 * 内，若过滤器排在它之后，超量请求体已经被读进内存，限额就失去了意义。 因此注册顺序取 {@link Ordered#HIGHEST_PRECEDENCE}。
 *
 * @author Mars-Sea
 * @since 0.0.8
 */
@Configuration
public class FilterConfig {

    /**
     * 注册请求体体积上限过滤器
     *
     * @param properties 配置，提供 {@code docgen.limits.max-request-bytes}
     * @return 过滤器注册对象
     */
    @Bean
    public FilterRegistrationBean<RequestBodySizeFilter> requestBodySizeFilter(
            DocGenProperties properties) {
        FilterRegistrationBean<RequestBodySizeFilter> registration =
                new FilterRegistrationBean<>(new RequestBodySizeFilter(properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("requestBodySizeFilter");
        return registration;
    }
}
