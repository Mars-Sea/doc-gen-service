package io.github.marssea.docgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档生成配置类
 *
 * <p>用于读取 application.yml 中以 docgen 开头的配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "docgen")
public class DocGenProperties {
    /** 模板文件存放的根目录路径 默认为 ./templates，可以通过 Docker 挂载卷进行覆盖 */
    private String templatePath;

    /** 渲染载荷限额，缺省全部不限制 */
    private Limits limits = new Limits();

    /**
     * 渲染载荷限额
     *
     * <p>Excel 模板填充是纯内存操作（EasyExcel 的 fill 基于 {@code XSSFWorkbook}，行对象全部驻留堆），
     * 请求数据的每个维度都是堆占用的乘数。这些值不是业务规则，而是把「请求能否在有限堆内完成」前置成 可判定条件——不设限额时，超量请求会一路做到 {@code
     * OutOfMemoryError} 才暴露，那时整个进程都已受 影响，而不仅仅是一个请求。
     *
     * <p>取 {@code -1} 表示该项不限制，也是默认值：限额的合适数值取决于部署机的堆大小与真实业务量， 只能由部署方按环境给出，代码里硬编码一个数值等于替部署方猜。
     */
    @Data
    public static class Limits {
        /** 单次请求 listData 各行数之和的上限，-1 不限制 */
        private int maxListRows = -1;

        /** 单次请求单值 data 字段数（含嵌套展开）的上限，-1 不限制 */
        private int maxDataFields = -1;

        /** 单次请求图片 / 二维码载荷张数的上限，-1 不限制 */
        private int maxImageCount = -1;

        /** 请求体字节数上限，-1 不限制 */
        private long maxRequestBodyBytes = -1;
    }
}
