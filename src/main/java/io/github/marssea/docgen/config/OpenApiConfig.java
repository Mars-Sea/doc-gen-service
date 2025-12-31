package io.github.marssea.docgen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置类
 * <p>
 * 配置 API 文档的基本信息，包括标题、描述、版本、联系人和许可证信息。
 * 配置完成后可通过 /swagger-ui.html 访问 API 文档界面。
 *
 * @author Mars-Sea
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 文档信息
     *
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI docGenOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Doc Gen Service API")
                        .description("文档生成服务 API - 基于模板动态生成 Word/Excel 文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Mars-Sea")
                                .url("https://github.com/Mars-Sea")
                                .email(""))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
