package io.github.marssea.docgen.payload;

/**
 * 载荷转换的结果
 *
 * <p>图片载荷与二维码载荷最终都要落到「字节 + 格式 + 展示尺寸」这一组事实上，差别只在字节从哪来： 图片从 URL 下载，二维码在服务端用 ZXing 本地生成。
 *
 * <p>两种载荷转换器（{@code ImagePayloadConverter} / {@code QrCodePayloadConverter}）都把本类型作为出口：
 * 各文档模块再按自己的渲染库包装 —— poi-tl 要 {@code PictureRenderData}，EasyExcel 要 {@code WriteCellData}。
 * 本类型因此是两侧共用的那一段，不依赖任何一个渲染库。
 *
 * @param bytes 图片字节（PNG / JPEG）
 * @param format 图片格式，取 {@code png} / {@code jpg}
 * @param width 展示宽度（像素）
 * @param height 展示高度（像素）
 * @author Mars-Sea
 * @since 0.0.6
 */
public record ConvertedImage(byte[] bytes, String format, int width, int height) {}
