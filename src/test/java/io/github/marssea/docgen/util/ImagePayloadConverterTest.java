package io.github.marssea.docgen.util;

import static org.junit.jupiter.api.Assertions.*;

import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ImagePayloadConverter 单元测试 */
@DisplayName("ImagePayloadConverter 测试")
class ImagePayloadConverterTest {

    private ImagePayloadConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ImagePayloadConverter();
    }

    @Nested
    @DisplayName("isImagePayload 测试")
    class IsImagePayloadTest {

        @Test
        @DisplayName("应正确识别图片载荷")
        void shouldIdentifyImagePayload() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");

            assertTrue(converter.isImagePayload(payload));
        }

        @Test
        @DisplayName("非 Map 类型应返回 false")
        void shouldReturnFalseForNonMap() {
            assertFalse(converter.isImagePayload("string value"));
            assertFalse(converter.isImagePayload(42));
            assertFalse(converter.isImagePayload(null));
        }

        @Test
        @DisplayName("Map 中无 type 字段应返回 false")
        void shouldReturnFalseWhenTypeMissing() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("url", "https://example.com/logo.png");

            assertFalse(converter.isImagePayload(payload));
        }

        @Test
        @DisplayName("type 不是 image 时应返回 false")
        void shouldReturnFalseWhenTypeIsNotImage() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "text");

            assertFalse(converter.isImagePayload(payload));
        }
    }

    @Nested
    @DisplayName("convert 测试")
    class ConvertTest {

        @Test
        @DisplayName("缺少 url 字段时应抛出异常")
        void shouldThrowWhenUrlMissing() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("url"));
        }

        @Test
        @DisplayName("只传 url 时应使用默认长宽并从 URL 推断格式")
        void shouldAcceptMinimalPayloadWithDefaults() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://invalid.nonexistent.domain/logo.png");

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("Failed to download"));
            assertFalse(ex.getMessage().contains("format"));
            assertFalse(ex.getMessage().contains("width"));
            assertFalse(ex.getMessage().contains("height"));
        }

        @Test
        @DisplayName("缺少 width 和 height 时应使用默认长宽")
        void shouldUseDefaultWidthAndHeightWhenMissing() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://invalid.nonexistent.domain/logo.png");
            payload.put("format", "png");

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("Failed to download"));
            assertFalse(ex.getMessage().contains("width"));
            assertFalse(ex.getMessage().contains("height"));
        }

        @Test
        @DisplayName("URL 使用非 http/https 协议时应抛出异常")
        void shouldThrowForNonHttpProtocol() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "ftp://example.com/logo.png");
            payload.put("format", "png");
            payload.put("width", 120);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("http or https"));
        }

        @Test
        @DisplayName("URL 使用 file 协议时应抛出异常")
        void shouldThrowForFileProtocol() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "file:///etc/passwd");
            payload.put("format", "png");
            payload.put("width", 120);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("http or https"));
        }

        @Test
        @DisplayName("不支持的显式图片格式应抛出异常")
        void shouldThrowForUnsupportedFormat() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.gif");
            payload.put("format", "gif");
            payload.put("width", 120);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("not supported"));
        }

        @Test
        @DisplayName("不支持的 URL 图片格式应抛出异常")
        void shouldThrowForUnsupportedUrlFormat() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://invalid.nonexistent.domain/logo.gif");

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("not supported"));
        }

        @Test
        @DisplayName("width 为 0 时应抛出异常")
        void shouldThrowForZeroWidth() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");
            payload.put("format", "png");
            payload.put("width", 0);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("positive integer"));
        }

        @Test
        @DisplayName("height 为负数时应抛出异常")
        void shouldThrowForNegativeHeight() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");
            payload.put("format", "png");
            payload.put("width", 120);
            payload.put("height", -10);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("positive integer"));
        }

        @Test
        @DisplayName("width 为非数字时应抛出异常")
        void shouldThrowForNonNumericWidth() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");
            payload.put("format", "png");
            payload.put("width", "abc");
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("valid integer"));
        }

        @Test
        @DisplayName("格式校验应不区分大小写")
        void shouldAcceptFormatCaseInsensitive() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");
            payload.put("format", "PNG");
            payload.put("width", 120);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertFalse(
                    ex.getMessage().contains("not supported"), "Format should be case-insensitive");
        }

        @Test
        @DisplayName("宽度和高度支持 Number 类型（如 Double）")
        void shouldAcceptNumericWidthAndHeight() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://example.com/logo.png");
            payload.put("format", "png");
            payload.put("width", 120.0);
            payload.put("height", 60.5);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertFalse(
                    ex.getMessage().contains("valid integer"),
                    "Should accept Number types for width/height");
        }

        @Test
        @DisplayName("图片下载失败时应抛出明确异常")
        void shouldThrowWhenDownloadFails() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "image");
            payload.put("url", "https://invalid.nonexistent.domain/image.png");
            payload.put("format", "png");
            payload.put("width", 120);
            payload.put("height", 60);

            InvalidImagePayloadException ex =
                    assertThrows(
                            InvalidImagePayloadException.class,
                            () -> converter.convert("logo", payload));
            assertTrue(ex.getMessage().contains("Failed to download"));
        }
    }
}
