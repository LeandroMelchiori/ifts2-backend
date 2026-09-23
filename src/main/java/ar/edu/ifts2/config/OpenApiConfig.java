package ar.edu.ifts2.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI institutionalApi() {
        return new OpenAPI()
                .info(new Info().title("IFTS N. 2 - API institucional")
                        .version("v1")
                        .description("CMS institucional: autenticacion, usuarios, noticias, eventos, documentos, carreras, autoridades, enlaces e institucion."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
