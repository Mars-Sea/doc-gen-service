package io.github.marssea.docgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档生成配置类
 * <p>
 * 用于读取 application.yml 中以 docgen 开头的配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "docgen")
public class DocGenProperties {
    /**
     * 模板文件存放的根目录路径
     * 默认为 ./templates，可以通过 Docker 挂载卷进行覆盖
     */
    private String templatePath;
}
