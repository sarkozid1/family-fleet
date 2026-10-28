package hu.familyfleet.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Az OpenAPI-leírás fejléce. A végpontokat a springdoc a controllerekből és a DTO-k
 * validációs annotációiból (pl. {@code @Size(max = 100)} → {@code maxLength: 100}) állítja elő.
 * Swagger UI: /swagger-ui.html, géppel olvasható leírás: /v3/api-docs.
 *
 * <p>A "bearerAuth" séma miatt a Swagger UI-ban megjelenik az Authorize gomb: a
 * {@code /api/auth/login} által adott tokent oda beillesztve a többi végpont is kipróbálható.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI familyFleetOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build == null ? "dev" : build.getVersion();
        return new OpenAPI()
                .info(new Info().title("Family Fleet API").version(version).description("""
                                Családi járműnyilvántartó: háztartások, családtagok, járművek, biztosítások és \
                                határidők (műszaki vizsga, KGFB-évforduló, gépjárműadó, matrica). \
                                Hitelesítés: POST /api/auth/login, majd Authorization: Bearer <token>. \
                                A hibák RFC 9457 `application/problem+json` formában érkeznek."""))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
