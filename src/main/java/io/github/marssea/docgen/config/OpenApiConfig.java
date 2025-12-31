package io.github.marssea.docgen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docGenOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Doc Gen Service API")
                        .description("文档生成服务 API 文档")
                        .version("v1.0.0"));
    }
}
