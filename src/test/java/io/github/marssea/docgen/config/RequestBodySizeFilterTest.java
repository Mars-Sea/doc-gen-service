package io.github.marssea.docgen.config;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.exception.PayloadTooLargeException;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/** RequestBodySizeFilter 单元测试 */
@DisplayName("请求体体积上限过滤器测试")
class RequestBodySizeFilterTest {

    private static final int LIMIT = 32;

    private DocGenProperties propertiesWithLimit(long limit) {
        DocGenProperties properties = new DocGenProperties();
        properties.getLimits().setMaxRequestBodyBytes(limit);
        return properties;
    }

    private MockHttpServletRequest postRequest(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        new MockServletContext(), "POST", "/api/v1/doc/excel/fill");
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }

    @Nested
    @DisplayName("限额未启用")
    class DisabledTest {

        @Test
        @DisplayName("限额为 -1 时超大体也能正常读取")
        void shouldPassThroughWhenLimitDisabled() throws Exception {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(-1));
            String body = "x".repeat(LIMIT * 10);
            MockHttpServletRequest request = postRequest(body);

            boolean[] invoked = {false};
            filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {
                        invoked[0] = true;
                        // 限额关闭时应返回原始请求，不做包装
                        assertSame(request, req);
                    });

            assertTrue(invoked[0]);
        }

        @Test
        @DisplayName("非 POST 请求不受限额影响")
        void shouldIgnoreNonPostRequests() throws Exception {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            new MockServletContext(), "GET", "/api/v1/template/list");

            boolean[] invoked = {false};
            filter.doFilter(
                    request, new MockHttpServletResponse(), (req, res) -> invoked[0] = true);

            assertTrue(invoked[0]);
        }
    }

    @Nested
    @DisplayName("限额已启用")
    class EnabledTest {

        @Test
        @DisplayName("体积未超限时正常读取全部内容")
        void shouldReadBodyWithinLimit() throws Exception {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
            String body = "{\"title\":\"ok\"}";
            MockHttpServletRequest request = postRequest(body);

            byte[][] captured = new byte[1][];
            filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> captured[0] = req.getInputStream().readAllBytes());

            assertArrayEquals(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), captured[0]);
        }

        @Test
        @DisplayName("体积超限时抛 PayloadTooLargeException")
        void shouldRejectBodyExceedingLimit() {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
            String body = "x".repeat(LIMIT + 1);
            MockHttpServletRequest request = postRequest(body);

            assertThrows(
                    PayloadTooLargeException.class,
                    () ->
                            filter.doFilter(
                                    request,
                                    new MockHttpServletResponse(),
                                    (req, res) -> req.getInputStream().readAllBytes()));
        }

        @Test
        @DisplayName("超限异常携带实测值与上限值")
        void shouldReportActualAndLimit() {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
            String body = "x".repeat(LIMIT + 10);
            MockHttpServletRequest request = postRequest(body);

            PayloadTooLargeException exception =
                    assertThrows(
                            PayloadTooLargeException.class,
                            () ->
                                    filter.doFilter(
                                            request,
                                            new MockHttpServletResponse(),
                                            (req, res) -> req.getInputStream().readAllBytes()));

            assertEquals(LIMIT, exception.getLimit());
            assertTrue(exception.getActual() > LIMIT);
            assertEquals("请求体", exception.getDimension());
        }

        @Test
        @DisplayName("getReader() 同样受限额约束")
        void shouldLimitReaderPath() {
            RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
            String body = "x".repeat(LIMIT + 1);
            MockHttpServletRequest request = postRequest(body);

            // 只包 getInputStream() 会留出绕过路径：表单类转换器走 getReader()
            assertThrows(
                    PayloadTooLargeException.class,
                    () ->
                            filter.doFilter(
                                    request,
                                    new MockHttpServletResponse(),
                                    (req, res) -> {
                                        char[] buffer = new char[LIMIT * 4];
                                        req.getReader().read(buffer);
                                    }));
        }
    }

    @Test
    @DisplayName("包装后的流仍是 ServletInputStream，Jackson 能正常取用")
    void shouldExposeServletInputStream() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
        MockHttpServletRequest request = postRequest("{}");

        ServletInputStream[] captured = new ServletInputStream[1];
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> captured[0] = req.getInputStream());

        assertNotNull(captured[0]);
        assertTrue(captured[0].isReady());
        // 同步读取路径下 Jackson 只调 read()，不会走异步的 ReadListener
        assertFalse(captured[0].isFinished());
    }

    @Test
    @DisplayName("字符集非法时退回 UTF-8 而不是让请求失败")
    void shouldFallbackToUtf8OnInvalidCharset() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(propertiesWithLimit(LIMIT));
        MockHttpServletRequest request = postRequest("{}");
        request.setCharacterEncoding("not-a-charset");

        String[] captured = new String[1];
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> captured[0] = req.getReader().readLine());

        assertEquals("{}", captured[0]);
    }
}
