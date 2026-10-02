package io.github.marssea.docgen.payload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染数据的遍历器
 *
 * <p>从数据根开始递归下行，在每个「可能是载荷」的位置把值交给调用方判定，并用调用方返回的结果替换原值。
 *
 * <p>遍历必须覆盖三类容器：嵌套 {@link Map}、{@link Iterable}（{@code List} / {@code Set} 等）与数组。 数组是独立的一支 ——Java
 * 中数组不实现 {@link Iterable}，漏掉它会让 {@code items[0].logo} 这类载荷保持 {@code Map} 原样， 最终被 poi-tl 渲染成 {@code
 * Map.toString()}，与「只扫描顶层」是同一种静默失败。
 *
 * <p>本模块不认识任何具体载荷：图片、二维码、将来新增的别的类型，都只由调用方在 {@link PayloadHandler} 里判定。 因此 Word 与 Excel
 * 可共用同一份遍历，各自只在叶子上包装成自己的渲染对象（poi-tl 的 {@code PictureRenderData} / EasyExcel 的 {@code
 * WriteCellData}）。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
public final class PayloadWalker {

    private PayloadWalker() {
        // 工具类不允许实例化
    }

    /**
     * 遍历数据并替换载荷
     *
     * @param data 原始渲染数据，可为 null（返回空 Map）
     * @param handler 载荷处理器
     * @return 替换后的新数据；原 Map 不被修改
     */
    public static Map<String, Object> walk(Map<String, Object> data, PayloadHandler handler) {
        // null 数据要转成空 Map 而非原样返回 null：poi-tl 的 render() 不接受 null 模型，
        // 且调用方后续还要为缺失的表格循环字段补空列表。
        Map<String, Object> processed = new HashMap<>();
        if (data == null) {
            return processed;
        }
        data.forEach((key, value) -> processed.put(key, walkValue(key, value, handler)));
        return processed;
    }

    private static Object walkValue(String field, Object value, PayloadHandler handler) {
        Object converted = handler.convert(field, value);
        if (converted != value) {
            // 处理器认出了这个值并给出了替代物，无需再向下遍历
            return converted;
        }
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> convertedMap = new HashMap<>();
            nestedMap.forEach(
                    (key, nested) ->
                            convertedMap.put(
                                    String.valueOf(key),
                                    walkValue(field + "." + key, nested, handler)));
            return convertedMap;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> convertedElements = new ArrayList<>();
            int index = 0;
            for (Object element : iterable) {
                convertedElements.add(walkValue(field + "[" + index + "]", element, handler));
                index++;
            }
            return convertedElements;
        }
        // 数组不实现 Iterable，是独立的一支
        if (value instanceof Object[] array) {
            List<Object> convertedElements = new ArrayList<>();
            int index = 0;
            for (Object element : array) {
                convertedElements.add(walkValue(field + "[" + index + "]", element, handler));
                index++;
            }
            return convertedElements;
        }
        return value;
    }

    /**
     * 载荷处理器
     *
     * <p>按调用方所在文档模块的能力判定期望支持的载荷类型。 约定：认出载荷时返回替代值；未认出时<b>原样返回传入的 value</b> （而不是 null），遍历器据此判断是否继续向下。
     */
    public interface PayloadHandler {
        /**
         * 尝试把某个值转换为渲染库可用的对象
         *
         * @param field 字段路径，如 {@code items[0].logo}，用于日志与异常提示
         * @param value 待判定的值
         * @return 已转换的值；不是本处理器支持的载荷时原样返回 {@code value}
         */
        Object convert(String field, Object value);
    }
}
