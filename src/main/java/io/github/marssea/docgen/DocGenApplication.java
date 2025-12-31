package io.github.marssea.docgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Doc-Gen-Service 应用程序入口
 * <p>
 * 基于 Spring Boot 的文档生成微服务，提供以下功能：
 * <ul>
 * <li>Word 文档模板渲染（使用 poi-tl）</li>
 * <li>支持表格行循环、条件判断等高级模板语法</li>
 * <li>RESTful API 接口，便于与其他服务集成</li>
 * <li>Docker 容器化部署支持</li>
 * </ul>
 * <p>
 * 项目地址: <a href="https://github.com/Mars-Sea/doc-gen-service">GitHub</a>
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@SpringBootApplication
public class DocGenApplication {

    /**
     * 应用程序主入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DocGenApplication.class, args);
    }

}
