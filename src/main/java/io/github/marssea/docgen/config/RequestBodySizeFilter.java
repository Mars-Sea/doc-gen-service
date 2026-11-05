package io.github.marssea.docgen.config;

import io.github.marssea.docgen.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求体体积上限过滤器
 *
 * <p>Spring Boot 的 {@code spring.servlet.multipart.max-request-size} 只作用于 multipart 请求，Tomcat 的
 * {@code maxPostSize} 只作用于 {@code application/x-www-form-urlencoded}。而本服务的文档生成端点收的是 {@code
 * application/json}——也就是说这条路径此前没有任何体积上限，请求体可以一直大到把堆写满。
 *
 * <p>这里在读取时计数而不是只看 {@code Content-Length}：分块传输（chunked）的请求没有该头，只看头会漏。 一旦超限，立即抛 {@link
 * PayloadTooLargeException} 并停止读取，后续字节不再进入内存。
 *
 * <p>过滤范围限于 POST 请求：GET 没有请求体，其余方法本服务未开放带体的端点。限额取 {@code docgen.limits.max-request-bytes}，为 {@code
 * -1} 时本过滤器直接放行（不包装请求，避免无谓开销）。
 *
 * @author Mars-Sea
 * @since 0.0.8
 */
public class RequestBodySizeFilter extends OncePerRequestFilter {

    /** 读取时使用的字符集，与 Spring MVC 的 JSON 默认值一致 */
    private static final Charset BODY_CHARSET = StandardCharsets.UTF_8;

    private final DocGenProperties properties;

    /**
     * 构造函数
     *
     * @param properties 配置，读取 {@code docgen.limits.max-request-bytes}
     */
    public RequestBodySizeFilter(DocGenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = properties.getLimits().getMaxRequestBodyBytes();
        if (limit <= 0 || !"POST".equalsIgnoreCase(request.getMethod())) {
            // 未启用限额或不是带体的方法，保持原始请求，不做任何包装
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new SizeLimitedRequest(request, limit), response);
    }

    /**
     * 包装请求，把请求体读取改为带计数上限的读取
     *
     * <p>覆盖 {@code getInputStream()} 与 {@code getReader()} 两条读取入口：Jackson 走前者，
     * 表单类转换器走后者，只包一条会留出绕过路径。
     */
    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long limit;

        private SizeLimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedInputStream(super.getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(
                    new InputStreamReader(
                            getInputStream(), resolveCharset(getCharacterEncoding())));
        }

        private static Charset resolveCharset(String encoding) {
            if (encoding == null || encoding.isBlank()) {
                return BODY_CHARSET;
            }
            try {
                return Charset.forName(encoding);
            } catch (RuntimeException e) {
                // 非法字符集名不应让请求失败，退回默认字符集
                return BODY_CHARSET;
            }
        }
    }

    /**
     * 带计数上限的 Servlet 输入流
     *
     * <p>必须继承 {@link jakarta.servlet.ServletInputStream} 而非普通 {@link InputStream}：Jackson 通过 {@code
     * getInputStream()} 取流，该方法返回类型是 {@code ServletInputStream}，包装成普通流无法返回。
     *
     * <p>超限时抛 {@link PayloadTooLargeException} 而不是截断返回：截断会让 JSON 解析报出一个与真实原因 无关的语法错误，掩盖「请求太大」这个事实。
     */
    private static final class SizeLimitedInputStream extends jakarta.servlet.ServletInputStream {

        private final InputStream delegate;

        private final long limit;

        private long consumed;

        private SizeLimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            // 本服务不使用异步 Servlet IO，同步读取路径不会调用此方法
            throw new UnsupportedOperationException("setReadListener is not supported");
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = delegate.skip(n);
            if (skipped > 0) {
                count(skipped);
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void count(long bytes) {
            consumed += bytes;
            if (consumed > limit) {
                throw new PayloadTooLargeException("请求体", consumed, limit);
            }
        }
    }
}
