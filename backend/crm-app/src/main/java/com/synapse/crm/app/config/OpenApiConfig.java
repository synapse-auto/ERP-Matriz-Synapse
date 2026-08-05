package com.synapse.crm.app.config;

import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados e mecanismos de autenticacao exibidos na documentacao OpenAPI. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi(
            OpenApiProperties propriedades,
            @Value("${spring.application.name}") String nomeDaAplicacao) {
        Info info = new Info()
                .title(propriedades.title())
                .description(propriedades.description())
                .version(propriedades.version())
                .extensions(Map.of("x-application-name", nomeDaAplicacao));

        Components componentes = new Components()
                .addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .name("Authorization")
                                .description("JWT de usuario. A interface envia o cabecalho Bearer <token>.")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                .addSecuritySchemes(
                        "synapseToken",
                        new SecurityScheme()
                                .name("X-Synapse-Token")
                                .description("Token permanente do contrato interno da Automacao.")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER))
                .addSecuritySchemes(
                        "metaWebhookSignature",
                        new SecurityScheme()
                                .name("X-Hub-Signature-256")
                                .description("Assinatura HMAC SHA-256 calculada pelo provedor sobre o corpo bruto.")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER));

        return new OpenAPI().info(info).components(componentes);
    }

    /** Acrescenta os erros transversais da cadeia JWT sem repeti-los em 61 operacoes. */
    @Bean
    OpenApiCustomizer respostasDeSeguranca() {
        return openApi -> openApi.getPaths().forEach((caminho, item) -> {
            item.readOperations().forEach(operacao -> {
                if (exigeAutenticacao(caminho)) {
                    operacao.getResponses()
                            .addApiResponse(
                                    "401",
                                    new ApiResponse().description(
                                                    "Credencial ausente, inválida ou expirada."));
                    operacao.getResponses()
                            .addApiResponse(
                                    "403",
                                    new ApiResponse().description(
                                                    "Credencial válida sem permissão para a operação."));
                }
                // swagger-core reaproveita o schema de sucesso em respostas de erro que so
                // declaram codigo e descricao. Remover esse conteudo evita documentar um DTO
                // de sucesso como corpo de 400/401/403/404.
                operacao.getResponses().forEach((codigo, resposta) -> {
                    if (!codigo.startsWith("2")) {
                        resposta.setContent(null);
                    }
                });
            });
        });
    }

    private static boolean exigeAutenticacao(String caminho) {
        if (caminho.startsWith("/internal/v1/")) {
            return true;
        }
        if (!caminho.startsWith("/api/v1/")) {
            return false;
        }
        return !caminho.startsWith("/api/v1/auth/")
                && !caminho.equals("/api/v1/config/tema")
                && !caminho.equals("/api/v1/config/textos");
    }
}
