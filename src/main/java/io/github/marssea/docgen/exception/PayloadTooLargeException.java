package io.github.marssea.docgen.exception;

/**
 * 渲染载荷超出限额异常
 *
 * <p>Excel 模板填充会把整份工作簿读进堆（EasyExcel 的模板填充基于 {@code XSSFWorkbook}，SXSSF 的流式窗口 对 {@code fill}
 * 不生效），因此请求数据的行数、字段数、图片张数都是堆占用的直接乘数。限额不是业务规则， 而是把「请求能否在有限堆内完成」这一事实前置为可判定的条件，避免等到 {@code
 * OutOfMemoryError} 才发现。
 *
 * <p>限额由 {@code docgen.limits.*} 配置，取 {@code -1} 表示该项不限制。该异常会被全局异常处理器捕获并 返回 HTTP 413
 * (PAYLOAD_TOO_LARGE)。
 *
 * @author Mars-Sea
 * @since 0.0.8
 */
public class PayloadTooLargeException extends RuntimeException {

    /** 触发限额的维度名称，如 {@code listData.items} */
    private final String dimension;

    /** 实测值 */
    private final long actual;

    /** 配置的上限值 */
    private final long limit;

    /**
     * 构造函数
     *
     * @param dimension 触发限额的维度名称（用于提示调用方是哪一项超了）
     * @param actual 实测值
     * @param limit 配置的上限值
     */
    public PayloadTooLargeException(String dimension, long actual, long limit) {
        super(
                "请求数据超出限额："
                        + dimension
                        + " 实测 "
                        + actual
                        + "，上限 "
                        + limit
                        + "（如需调整请修改 docgen.limits 配置）");
        this.dimension = dimension;
        this.actual = actual;
        this.limit = limit;
    }

    /**
     * 获取触发限额的维度名称
     *
     * @return 维度名称
     */
    public String getDimension() {
        return dimension;
    }

    /**
     * 获取实测值
     *
     * @return 实测值
     */
    public long getActual() {
        return actual;
    }

    /**
     * 获取上限值
     *
     * @return 上限值
     */
    public long getLimit() {
        return limit;
    }
}
