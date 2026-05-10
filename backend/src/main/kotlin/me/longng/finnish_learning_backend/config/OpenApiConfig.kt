package me.longng.finnish_learning_backend.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Finnish Learning App API")
                    .description("API documentation for the Finnish Learning App")
                    .version("1.0.0")
            )
    }
}