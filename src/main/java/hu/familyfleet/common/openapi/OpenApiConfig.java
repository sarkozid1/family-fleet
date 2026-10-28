package hu.familyfleet.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Az OpenAPI-leírás fejléce. A végpontokat a springdoc a controllerekből és a DTO-k
 * validációs annotációiból (pl. {@code @Size(max = 100)} → {@code maxLength: 100}) állítja elő.
 * Swagger UI: /swagger-ui.html, géppel olvasható leírás: /v3/api-docs.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI familyFleetOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build == null ? "dev" : build.getVersion();
        return new OpenAPI().info(new Info()
                .title("Family Fleet API")
                .version(version)
                .description("""
                        Családi járműnyilvántartó: háztartások, családtagok, járművek, biztosítások és \
                        határidők (műszaki vizsga, KGFB-évforduló, gépjárműadó, matrica). \
                        A hibák RFC 9457 `application/problem+json` formában érkeznek."""));
    }
}
