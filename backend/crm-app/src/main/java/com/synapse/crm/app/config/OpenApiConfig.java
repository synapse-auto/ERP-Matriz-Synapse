package com.synapse.crm.app.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados, agrupamento e mecanismos de autenticacao exibidos na documentacao OpenAPI. */
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

        return new OpenAPI()
                .info(info)
                .components(componentes)
                .tags(List.of(
                        new Tag().name("Interno").description("Contrato autenticado por X-Synapse-Token."),
                        new Tag().name("Automação").description("Operações consumidas pela Automação."),
                        new Tag().name("Resumo por IA").description("Solicitação e ciclo do resumo por IA.")));
    }

    /**
     * Garante que o contrato gerado continue legivel mesmo quando um controller novo esquecer
     * metadados editoriais. O prefixo e a fonte da verdade para a fronteira de autenticacao:
     * nenhuma anotacao e usada para tornar uma rota interna publica.
     */
    @Bean
    OpenApiCustomizer gruposEHeadersDoContrato() {
        return openApi -> openApi.getPaths().forEach((caminho, item) -> item.readOperations().forEach(operacao -> {
            if (caminho.startsWith("/internal/v1/")) {
                adicionarTag(operacao, "Interno");
                adicionarTag(operacao, "Automação");
                if (caminho.startsWith("/internal/v1/ev05/")) {
                    adicionarTag(operacao, "Resumo por IA");
                }
                operacao.setSecurity(List.of(new SecurityRequirement().addList("synapseToken")));
            } else if (exigeAutenticacao(caminho)
                    && (operacao.getSecurity() == null || operacao.getSecurity().isEmpty())) {
                operacao.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
            }

            if (ehResumoPorIa(caminho)) {
                adicionarTag(operacao, "Resumo por IA");
            }

            Optional.ofNullable(operacao.getParameters()).orElseGet(List::of).stream()
                    .filter(parametro -> "Idempotency-Key".equalsIgnoreCase(parametro.getName()))
                    .forEach(parametro -> parametro.setDescription(
                            "Chave estável da operação. Repetições compatíveis devolvem a mesma resposta; "
                                    + "não reutilize a chave para outro recurso."));
        }));
    }

    /** Acrescenta os erros transversais da cadeia JWT sem repeti-los em cada operacao. */
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

    private static void adicionarTag(io.swagger.v3.oas.models.Operation operacao, String tag) {
        List<String> tags = operacao.getTags() == null ? new ArrayList<>() : new ArrayList<>(operacao.getTags());
        if (!tags.contains(tag)) {
            tags.add(tag);
            operacao.setTags(tags);
        }
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

    private static boolean ehResumoPorIa(String caminho) {
        return caminho.contains("/resumo-ia") || caminho.endsWith("/recursos-ia");
    }
}
