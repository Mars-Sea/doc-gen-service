package io.github.marssea.docgen.util;

import com.deepoove.poi.data.PictureRenderData;
import com.deepoove.poi.data.PictureType;
import com.deepoove.poi.data.Pictures;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图片载荷转换工具
 *
 * <p>将请求数据中的结构化图片对象转换为 poi-tl 的 {@link PictureRenderData}。 支持 URL 图片输入，自动校验协议和图片格式。
 *
 * <p>图片载荷格式示例：
 *
 * <pre>
 * {
 *   "type": "image",
 *   "url": "https://example.com/logo.png"
 * }
 * </pre>
 *
 * @author Mars-Sea
 * @since 0.0.5
 */
@Slf4j
@Component
public class ImagePayloadConverter {

    /** 图片载荷标识字段 */
    private static final String FIELD_TYPE = "type";

    private static final String FIELD_URL = "url";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_WIDTH = "width";
    private static final String FIELD_HEIGHT = "height";

    /** 图片载荷类型标识 */
    private static final String IMAGE_TYPE = "image";

    /** 支持的图片格式 */
    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpg", "jpeg");

    /** 支持的 URL 协议 */
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("http", "https");

    /** 默认图片宽度 */
    private static final int DEFAULT_WIDTH = 300;

    /** 默认图片高度 */
    private static final int DEFAULT_HEIGHT = 200;

    /** 网络连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** 网络读取超时时间（毫秒） */
    private static final int READ_TIMEOUT_MS = 30_000;

    /** 最大图片大小（10MB） */
    private static final int MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * 判断给定的值是否为图片载荷
     *
     * <p>通过检查是否为 Map 且包含 {@code type: "image"} 字段来判断。
     *
     * @param value 待检查的值
     * @return 如果是图片载荷则返回 true
     */
    public boolean isImagePayload(Object value) {
        if (!(value instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return IMAGE_TYPE.equals(map.get(FIELD_TYPE));
    }

    /**
     * 将图片载荷转换为 poi-tl 的 PictureRenderData
     *
     * <p>执行以下校验：
     *
     * <ul>
     *   <li>URL 协议必须为 http 或 https
     *   <li>图片格式必须为 png、jpg 或 jpeg
     *   <li>宽度和高度必须为正整数，未传时默认 300 x 200
     *   <li>图片下载成功且大小不超过 10MB
     * </ul>
     *
     * @param fieldName 数据 Map 中的字段名（用于错误消息）
     * @param payload 图片载荷 Map
     * @return poi-tl 图片渲染数据
     * @throws InvalidImagePayloadException 当载荷校验失败或图片下载失败时抛出
     */
    public PictureRenderData convert(String fieldName, Map<String, Object> payload) {
        validateRequiredFields(fieldName, payload);

        String url = payload.get(FIELD_URL).toString();
        String explicitFormat = getOptionalFormat(payload.get(FIELD_FORMAT));
        int width =
                toPositiveIntOrDefault(
                        fieldName, FIELD_WIDTH, payload.get(FIELD_WIDTH), DEFAULT_WIDTH);
        int height =
                toPositiveIntOrDefault(
                        fieldName, FIELD_HEIGHT, payload.get(FIELD_HEIGHT), DEFAULT_HEIGHT);

        validateUrl(fieldName, url);
        validateExplicitFormat(fieldName, explicitFormat);
        validateInferredUrlFormat(fieldName, url, explicitFormat);

        return downloadAndConvert(fieldName, url, explicitFormat, width, height);
    }

    /** 校验必填字段 */
    private void validateRequiredFields(String fieldName, Map<String, Object> payload) {
        if (!payload.containsKey(FIELD_URL) || payload.get(FIELD_URL) == null) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Image payload for '"
                            + fieldName
                            + "' is missing required field: "
                            + FIELD_URL);
        }
    }

    /** 校验 URL 协议 */
    private void validateUrl(String fieldName, String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidImagePayloadException(
                    fieldName, "Image URL for '" + fieldName + "' is not a valid URL: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !SUPPORTED_PROTOCOLS.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Image URL for '"
                            + fieldName
                            + "' must use http or https protocol, got: "
                            + scheme);
        }
    }

    /** 校验显式图片格式 */
    private void validateExplicitFormat(String fieldName, String format) {
        if (format != null) {
            validateFormat(fieldName, format);
        }
    }

    /** 校验可从 URL 识别出的图片格式 */
    private void validateInferredUrlFormat(String fieldName, String url, String explicitFormat) {
        if (explicitFormat == null) {
            String formatFromUrl = inferFormatFromUrl(url);
            if (formatFromUrl != null) {
                validateFormat(fieldName, formatFromUrl);
            }
        }
    }

    /** 校验图片格式 */
    private void validateFormat(String fieldName, String format) {
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Image format for '"
                            + fieldName
                            + "' is not supported: "
                            + format
                            + ". Supported formats: png, jpg, jpeg");
        }
    }

    /** 获取可选图片格式 */
    private String getOptionalFormat(Object value) {
        if (value == null) {
            return null;
        }
        String format = value.toString().trim();
        if (format.isEmpty()) {
            return null;
        }
        return format.toLowerCase(Locale.ROOT);
    }

    /** 将值转换为正整数，缺省时使用默认值 */
    private int toPositiveIntOrDefault(
            String fieldName, String field, Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int intValue;
            if (value instanceof Number) {
                intValue = ((Number) value).intValue();
            } else {
                intValue = Integer.parseInt(value.toString());
            }
            if (intValue <= 0) {
                throw new InvalidImagePayloadException(
                        fieldName,
                        "Image "
                                + field
                                + " for '"
                                + fieldName
                                + "' must be a positive integer, got: "
                                + intValue);
            }
            return intValue;
        } catch (NumberFormatException e) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Image "
                            + field
                            + " for '"
                            + fieldName
                            + "' must be a valid integer, got: "
                            + value);
        }
    }

    /**
     * 下载图片并转换为 PictureRenderData
     *
     * <p>使用自定义 HTTP 连接（带超时设置），因为 poi-tl 的 {@code Pictures.ofUrl()} 不支持自定义超时。
     */
    private PictureRenderData downloadAndConvert(
            String fieldName, String url, String explicitFormat, int width, int height) {
        DownloadedImage image = downloadImage(fieldName, url);
        String format = resolveFormat(fieldName, url, explicitFormat, image.contentType());

        PictureType pictureType = mapFormatToPictureType(fieldName, format);

        return Pictures.ofBytes(image.bytes(), pictureType).size(width, height).create();
    }

    /** 下载图片，带超时设置 */
    private DownloadedImage downloadImage(String fieldName, String url) {
        HttpURLConnection connection = null;
        try {
            URL imageUrl = new URL(url);
            connection = (HttpURLConnection) imageUrl.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "doc-gen-service/0.0.5");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new InvalidImagePayloadException(
                        fieldName,
                        "Failed to download image for '" + fieldName + "': HTTP " + responseCode);
            }

            try (InputStream in = connection.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_IMAGE_SIZE_BYTES) {
                        throw new InvalidImagePayloadException(
                                fieldName,
                                "Image for '" + fieldName + "' exceeds maximum size of 10MB");
                    }
                    out.write(buffer, 0, bytesRead);
                }
                byte[] result = out.toByteArray();
                if (result.length == 0) {
                    throw new InvalidImagePayloadException(
                            fieldName, "Downloaded image for '" + fieldName + "' is empty");
                }
                return new DownloadedImage(result, connection.getContentType());
            }
        } catch (InvalidImagePayloadException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Failed to download image for '" + fieldName + "': " + e.getMessage(),
                    e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 解析图片格式 */
    private String resolveFormat(
            String fieldName, String url, String explicitFormat, String contentType) {
        if (explicitFormat != null) {
            return explicitFormat;
        }

        String formatFromUrl = inferFormatFromUrl(url);
        if (formatFromUrl != null) {
            validateFormat(fieldName, formatFromUrl);
            return formatFromUrl;
        }

        String formatFromContentType = inferFormatFromContentType(contentType);
        if (formatFromContentType != null) {
            return formatFromContentType;
        }

        throw new InvalidImagePayloadException(
                fieldName,
                "Image format for '"
                        + fieldName
                        + "' cannot be inferred from URL or Content-Type. Supported formats: png,"
                        + " jpg, jpeg");
    }

    /** 从 URL 路径推断图片格式 */
    private String inferFormatFromUrl(String url) {
        String path = URI.create(url).getPath();
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return null;
        }
        return path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /** 从 Content-Type 推断图片格式 */
    private String inferFormatFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/png")) {
            return "png";
        }
        if (normalized.startsWith("image/jpeg") || normalized.startsWith("image/jpg")) {
            return "jpg";
        }
        return null;
    }

    /** 将格式字符串映射到 PictureType */
    private PictureType mapFormatToPictureType(String fieldName, String format) {
        return switch (format) {
            case "png" -> PictureType.PNG;
            case "jpg", "jpeg" -> PictureType.JPEG;
            default -> throw new InvalidImagePayloadException(
                    fieldName,
                    "Unsupported image format: " + format + ". Supported: png, jpg, jpeg");
        };
    }

    private record DownloadedImage(byte[] bytes, String contentType) {}
}
