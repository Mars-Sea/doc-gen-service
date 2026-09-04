package io.github.marssea.docgen.util;

import com.deepoove.poi.data.PictureRenderData;
import com.deepoove.poi.data.PictureType;
import com.deepoove.poi.data.Pictures;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 二维码载荷转换工具
 *
 * <p>将请求数据中的结构化二维码对象转换为 poi-tl 的 {@link PictureRenderData}。 基于 ZXing 在服务端本地生成 PNG
 * 二维码图片，纯内存计算，无网络请求。
 *
 * <p>二维码载荷格式示例：
 *
 * <pre>
 * {
 *   "type": "qrcode",
 *   "content": "https://example.com/order/123",
 *   "width": 150,
 *   "height": 150
 * }
 * </pre>
 *
 * <p>其中 {@code content} 为二维码编码内容（必填），{@code width} / {@code height} 为输出图片尺寸（可选，默认 150 像素）。
 *
 * @author Mars-Sea
 * @since 0.0.7
 */
@Slf4j
@Component
public class QrCodePayloadConverter {

    /** 载荷类型标识字段 */
    private static final String FIELD_TYPE = "type";

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_WIDTH = "width";
    private static final String FIELD_HEIGHT = "height";

    /** 二维码载荷类型标识 */
    private static final String QRCODE_TYPE = "qrcode";

    /** 默认图片宽度（像素） */
    private static final int DEFAULT_WIDTH = 150;

    /** 默认图片高度（像素） */
    private static final int DEFAULT_HEIGHT = 150;

    /** 允许的最小图片边长（像素），过小会导致二维码无法扫描 */
    private static final int MIN_SIZE_PX = 50;

    /** 允许的最大图片边长（像素），防止恶意超大尺寸请求拖垮渲染 */
    private static final int MAX_SIZE_PX = 2000;

    /** 二维码内容的最大字符长度（UTF-8 编码，QR 码理论容量约 2953 字节） */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 二维码纠错级别：M 级（可纠正约 15% 数据），扫描成功率与容量的均衡默认值 */
    private static final ErrorCorrectionLevel ERROR_CORRECTION_LEVEL = ErrorCorrectionLevel.M;

    /** 二维码静区（四周白边）宽度，单位为模块数，标准推荐至少 4 */
    private static final int QUIET_ZONE_MODULES = 4;

    /**
     * 判断给定的值是否为二维码载荷
     *
     * <p>通过检查是否为 Map 且包含 {@code type: "qrcode"} 字段来判断。
     *
     * @param value 待检查的值
     * @return 如果是二维码载荷则返回 true
     */
    public boolean isQrCodePayload(Object value) {
        if (!(value instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return QRCODE_TYPE.equals(map.get(FIELD_TYPE));
    }

    /**
     * 将二维码载荷转换为 poi-tl 的 {@link PictureRenderData}
     *
     * @param fieldName 数据 Map 中的字段名（用于错误消息）
     * @param payload 二维码载荷 Map
     * @return 生成的二维码图片渲染数据
     */
    public PictureRenderData convert(String fieldName, Map<String, Object> payload) {
        String content = extractContent(fieldName, payload);
        int width =
                toSizeOrDefault(fieldName, FIELD_WIDTH, payload.get(FIELD_WIDTH), DEFAULT_WIDTH);
        int height =
                toSizeOrDefault(fieldName, FIELD_HEIGHT, payload.get(FIELD_HEIGHT), DEFAULT_HEIGHT);

        byte[] png = generateQrCodePng(fieldName, content, width, height);
        return Pictures.ofBytes(png, PictureType.PNG).size(width, height).create();
    }

    /** 提取并校验必填的二维码内容 */
    private String extractContent(String fieldName, Map<String, Object> payload) {
        Object raw = payload.get(FIELD_CONTENT);
        if (raw == null) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "QR code payload for '"
                            + fieldName
                            + "' is missing required field: "
                            + FIELD_CONTENT);
        }
        String content = raw.toString();
        if (content.isEmpty()) {
            throw new InvalidImagePayloadException(
                    fieldName, "QR code content for '" + fieldName + "' must not be empty");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "QR code content for '"
                            + fieldName
                            + "' exceeds maximum length of "
                            + MAX_CONTENT_LENGTH
                            + " characters, got: "
                            + content.length());
        }
        return content;
    }

    /**
     * 将值转换为合法的图片边长，缺省时使用默认值
     *
     * <p>要求为正整数且在允许范围内（{@link #MIN_SIZE_PX} ~ {@link #MAX_SIZE_PX}）。
     */
    private int toSizeOrDefault(String fieldName, String field, Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int intValue;
            if (value instanceof Number number) {
                intValue = number.intValue();
            } else {
                intValue = Integer.parseInt(value.toString());
            }
            if (intValue < MIN_SIZE_PX || intValue > MAX_SIZE_PX) {
                throw new InvalidImagePayloadException(
                        fieldName,
                        "QR code "
                                + field
                                + " for '"
                                + fieldName
                                + "' must be between "
                                + MIN_SIZE_PX
                                + " and "
                                + MAX_SIZE_PX
                                + " pixels, got: "
                                + intValue);
            }
            return intValue;
        } catch (NumberFormatException e) {
            throw new InvalidImagePayloadException(
                    fieldName,
                    "QR code "
                            + field
                            + " for '"
                            + fieldName
                            + "' must be a valid integer, got: "
                            + value);
        }
    }

    /**
     * 使用 ZXing 生成二维码 PNG 字节
     *
     * <p>内容以 UTF-8 编码（支持中文），纠错级别 M，四周保留标准静区白边。 包级可见以便单元测试直接验证生成内容的可解码性。
     *
     * @param fieldName 数据 Map 中的字段名（用于错误消息）
     * @param content 二维码内容
     * @param width 输出图片宽度（像素）
     * @param height 输出图片高度（像素）
     * @return PNG 格式的二维码图片字节
     */
    byte[] generateQrCodePng(String fieldName, String content, int width, int height) {
        try {
            BitMatrix matrix =
                    new QRCodeWriter()
                            .encode(
                                    content,
                                    BarcodeFormat.QR_CODE,
                                    width,
                                    height,
                                    Map.of(
                                            EncodeHintType.CHARACTER_SET,
                                            "UTF-8",
                                            EncodeHintType.ERROR_CORRECTION,
                                            ERROR_CORRECTION_LEVEL,
                                            EncodeHintType.MARGIN,
                                            QUIET_ZONE_MODULES));
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "png", out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            // ZXing 编码失败（如内容超出 QR 码容量）统一转换为 4xx 语义的载荷异常
            log.warn("Failed to generate QR code for field: {}", fieldName, e);
            throw new InvalidImagePayloadException(
                    fieldName,
                    "Failed to generate QR code for '" + fieldName + "': " + e.getMessage(),
                    e);
        }
    }
}
