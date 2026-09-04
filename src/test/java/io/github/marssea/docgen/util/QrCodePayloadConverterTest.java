package io.github.marssea.docgen.util;

import static org.junit.jupiter.api.Assertions.*;

import com.deepoove.poi.data.PictureRenderData;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import io.github.marssea.docgen.exception.InvalidImagePayloadException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QrCodePayloadConverter 单元测试 */
@DisplayName("QrCodePayloadConverter 测试")
class QrCodePayloadConverterTest {

    private final QrCodePayloadConverter converter = new QrCodePayloadConverter();

    @Test
    @DisplayName("type 为 qrcode 的 Map 应识别为二维码载荷")
    void shouldRecognizeQrCodePayload() {
        assertTrue(converter.isQrCodePayload(Map.of("type", "qrcode", "content", "hello")));
        assertFalse(converter.isQrCodePayload(Map.of("type", "image", "url", "https://a.com")));
        assertFalse(converter.isQrCodePayload("plain string"));
        assertFalse(converter.isQrCodePayload(null));
    }

    @Test
    @DisplayName("convert 应返回非空的 PictureRenderData")
    void shouldConvertPayloadToPictureRenderData() {
        Map<String, Object> payload =
                Map.of("type", "qrcode", "content", "https://example.com/order/123");

        PictureRenderData renderData = converter.convert("qr", payload);

        assertNotNull(renderData);
    }

    @Test
    @DisplayName("生成的二维码 PNG 应可解码还原出原始内容")
    void shouldGenerateDecodableQrCode() throws Exception {
        String content = "https://example.com/order/123?count=10";

        byte[] png = converter.generateQrCodePng("qr", content, 150, 150);

        assertEquals(content, decodeQrCode(png));
    }

    @Test
    @DisplayName("中文内容应可正确编码并解码")
    void shouldSupportChineseContent() throws Exception {
        String content = "订单编号：A-2026-0001，收货人：张三";

        byte[] png = converter.generateQrCodePng("qr", content, 200, 200);

        assertEquals(content, decodeQrCode(png));
    }

    @Test
    @DisplayName("缺少 content 应抛出 InvalidImagePayloadException")
    void shouldThrowExceptionForMissingContent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "qrcode");

        InvalidImagePayloadException ex =
                assertThrows(
                        InvalidImagePayloadException.class, () -> converter.convert("qr", payload));
        assertTrue(ex.getMessage().contains("content"));
    }

    @Test
    @DisplayName("空白 content 应抛出 InvalidImagePayloadException")
    void shouldThrowExceptionForEmptyContent() {
        Map<String, Object> payload = Map.of("type", "qrcode", "content", "");

        InvalidImagePayloadException ex =
                assertThrows(
                        InvalidImagePayloadException.class, () -> converter.convert("qr", payload));
        assertTrue(ex.getMessage().contains("must not be empty"));
    }

    @Test
    @DisplayName("超长内容应抛出 InvalidImagePayloadException")
    void shouldThrowExceptionForOverlongContent() {
        Map<String, Object> payload = Map.of("type", "qrcode", "content", "a".repeat(2001));

        InvalidImagePayloadException ex =
                assertThrows(
                        InvalidImagePayloadException.class, () -> converter.convert("qr", payload));
        assertTrue(ex.getMessage().contains("maximum length"));
    }

    @Test
    @DisplayName("尺寸越界应抛出 InvalidImagePayloadException")
    void shouldThrowExceptionForOutOfRangeSize() {
        Map<String, Object> tooSmall = Map.of("type", "qrcode", "content", "hello", "width", 10);
        Map<String, Object> tooLarge = Map.of("type", "qrcode", "content", "hello", "height", 5000);

        assertThrows(InvalidImagePayloadException.class, () -> converter.convert("qr", tooSmall));
        assertThrows(InvalidImagePayloadException.class, () -> converter.convert("qr", tooLarge));
    }

    @Test
    @DisplayName("非数字尺寸应抛出 InvalidImagePayloadException")
    void shouldThrowExceptionForNonNumericSize() {
        Map<String, Object> payload =
                Map.of("type", "qrcode", "content", "hello", "width", "large");

        InvalidImagePayloadException ex =
                assertThrows(
                        InvalidImagePayloadException.class, () -> converter.convert("qr", payload));
        assertTrue(ex.getMessage().contains("valid integer"));
    }

    /** 解码二维码 PNG，验证生成内容的正确性 */
    private String decodeQrCode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "PNG 应可解析为图像");
        BinaryBitmap bitmap =
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap).getText();
    }
}
