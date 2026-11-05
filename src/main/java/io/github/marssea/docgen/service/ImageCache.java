package io.github.marssea.docgen.service;

import io.github.marssea.docgen.payload.ConvertedImage;
import io.github.marssea.docgen.util.ImagePayloadConverter;
import io.github.marssea.docgen.util.QrCodePayloadConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 单次 Excel 填充请求的图片缓存
 *
 * <p>作用域是一次 {@code fillTemplate} 调用，不跨请求共享：图片字节与请求数据同生共死，缓存越过请求边界 就变成没人回收的常驻内存，与本类要解决的问题正好相反。
 *
 * <p>两级键，省的是两笔不同的开销：
 *
 * <ul>
 *   <li><b>URL 键</b>：同一张图在多行重复出现时只下载一次。省的是网络往返与下载途中的临时缓冲。
 *   <li><b>内容哈希键</b>：不同 URL 指向同一份字节（CDN 多域名、带签名参数的同一资源）时，堆里只留一份 {@code byte[]}，后续单元格复用同一个数组引用。
 * </ul>
 *
 * <p>必须说清本类<b>不能</b>做到的事：EasyExcel 的 {@code AbstractExcelWriteExecutor.fillImage} 内部无条件 调 {@code
 * workbook.addPicture(bytes, type)}，POI 每次都会新建一个 {@code PackagePart} 并写出字节。这一步在 库内部，外部无法拦截，所以最终
 * xlsx 里同一张图仍会存多份。本类减少的是服务端堆里的副本数与下载次数， 不是产出文件的体积。
 *
 * <p>二维码不按内容哈希合并：二维码的字节由内容和尺寸共同决定，缓存键用「内容 + 尺寸」即可，无需再算哈希。
 *
 * @author Mars-Sea
 * @since 0.0.8
 */
@Slf4j
final class ImageCache {

    /** 图片载荷中标识资源位置的字段 */
    private static final String FIELD_URL = "url";

    /** 二维码键前缀 */
    private static final String QR_PREFIX = "qr:";

    /** URL → 转换结果 */
    private final Map<String, ConvertedImage> byUrl = new HashMap<>();

    /** 内容哈希 → 转换结果，仅用于保证堆里只有一份同内容字节 */
    private final Map<String, ConvertedImage> byDigest = new HashMap<>();

    /** 二维码「内容 + 尺寸」→ 转换结果 */
    private final Map<String, ConvertedImage> byQrKey = new HashMap<>();

    /**
     * 解析图片载荷，命中缓存时不重复下载
     *
     * @param field 字段路径，用于日志与异常提示
     * @param payload 图片载荷
     * @param converter 下载与校验的执行者
     * @return 图片结果，同一 URL 或同一内容只返回同一实例
     */
    ConvertedImage resolveImage(
            String field, Map<String, Object> payload, ImagePayloadConverter converter) {
        Object rawUrl = payload.get(FIELD_URL);
        String url = rawUrl == null ? null : rawUrl.toString();
        if (url != null) {
            ConvertedImage cached = byUrl.get(url);
            if (cached != null) {
                log.debug("Reusing cached image for field: {}, url: {}", field, url);
                return cached;
            }
        }

        ConvertedImage downloaded = converter.convertToImage(field, payload);
        if (url != null) {
            byUrl.put(url, downloaded);
        }

        // 不同 URL 可能是同一份字节：按内容哈希再收敛一次，堆里只留一份数组
        String digest = sha256(downloaded.bytes());
        ConvertedImage sameContent = byDigest.get(digest);
        if (sameContent != null) {
            return sameContent;
        }
        byDigest.put(digest, downloaded);
        return downloaded;
    }

    /**
     * 解析二维码载荷，命中缓存时不重复生成
     *
     * @param field 字段路径，用于日志与异常提示
     * @param payload 二维码载荷
     * @param converter 生成与校验的执行者
     * @return 二维码图片结果，同内容同尺寸只返回同一实例
     */
    ConvertedImage resolveQrCode(
            String field, Map<String, Object> payload, QrCodePayloadConverter converter) {
        // 直接复用转换器产出的结果做键：内容与尺寸的解析、校验逻辑都在转换器里，
        // 本类若自行再解析一遍，两处规则迟早会漂移
        String key = QR_PREFIX + payload;
        ConvertedImage cached = byQrKey.get(key);
        if (cached != null) {
            log.debug("Reusing cached qrcode for field: {}", field);
            return cached;
        }
        ConvertedImage generated = converter.convertToImage(field, payload);
        byQrKey.put(key, generated);
        return generated;
    }

    /**
     * 计算内容哈希
     *
     * <p>SHA-256 而非 {@code Arrays.hashCode}：后者 32 位且碰撞概率对「图片字节」这种高度相似输入不够低，
     * 碰撞会导致两张不同的图被当成同一张，静默出错图。
     */
    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现的算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
