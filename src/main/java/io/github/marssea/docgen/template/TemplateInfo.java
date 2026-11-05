package io.github.marssea.docgen.template;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 一个模板文件的目录信息
 *
 * <p>把「模板名 + 大小 + 最后修改时间」收敛为一个不可变值对象，作为列表接口的返回元素。 此前列表只返回文件名字符串，管理页面想显示大小与修改时间时无从取得，
 * 而把三个字段散着返回会让「一条模板信息」这个概念继续缺席。
 *
 * <p>由 {@link TemplateRepository} 的读侧产出，不提供 public 构造器： 实例即「目录里确实存在这个文件、且这些属性是从该文件读出来的」。
 *
 * <p>时间统一为 {@link Instant}（序列化为 ISO-8601 UTC，如 {@code 2026-10-09T08:54:16Z}）。 不带服务器本地时区偏移：UTC
 * 是唯一无需调用方猜测时区基准的写法， 客户端按本地时区展示即可，跨时区读到的也是同一个时刻。
 *
 * @author Mars-Sea
 * @since 0.0.6
 */
public final class TemplateInfo {

    private final String name;
    private final long size;
    private final Instant lastModified;

    private TemplateInfo(String name, long size, Instant lastModified) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
    }

    /**
     * 由目录项构造
     *
     * <p>时间统一截断到秒：文件系统给出的是纳秒精度（{@code 2026-10-09T09:31:52.161164400Z}），
     * 而「模板什么时候改的」这一问法只到秒为止，保留尾数只会让每次列表响应都不同、无法比对。
     *
     * @param name 模板文件名，不含目录路径
     * @param size 文件大小，单位字节
     * @param lastModified 最后修改时间，UTC 时刻
     */
    public static TemplateInfo of(String name, long size, Instant lastModified) {
        return new TemplateInfo(name, size, lastModified.truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 模板文件名
     *
     * @return 不含目录路径的文件名，可直接用于下载与删除接口
     */
    public String name() {
        return name;
    }

    /**
     * 文件大小
     *
     * <p>只提供字节数，不提供 {@code "20 KB"} 这类文本：同一事实两处表达易不一致， 单位换算属展示层职责，交给各客户端按自己的语言与习惯格式化。
     *
     * @return 字节数
     */
    public long size() {
        return size;
    }

    /**
     * 最后修改时间
     *
     * @return UTC 时刻
     */
    public Instant lastModified() {
        return lastModified;
    }
}
