package com.synapse.crm.app.config;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracao da instancia (bloco {@code synapse:}), conforme
 * {@code docs/07-base-pai-multitenancy.md} secao 4.
 *
 * <p>A mesma imagem de container roda em qualquer filho; o que varia e o que e injetado por
 * variavel de ambiente. Por isso nenhum valor aqui pode ser constante no codigo.
 *
 * <p>Sobre o nome {@code tenant}: identifica a <em>instancia</em> (deploy e banco proprios) para
 * log, metrica e alerta. Nao existe e nao deve existir discriminador de tenant em tabela — o
 * isolamento entre clientes e fisico, nao logico.
 */
@Validated
@ConfigurationProperties(prefix = "synapse")
public record SynapseProperties(
        @NotNull @Valid Tenant tenant,
        @NotNull @Valid Canal canal,
        @NotNull @Valid Automacao automacao,
        @NotNull @Valid Alertas alertas,
        @NotNull Map<String, Boolean> features) {

    /** Identidade da instancia, usada em logs estruturados e labels de metrica. */
    public record Tenant(
            @NotBlank String codigo, @NotBlank String nomeExibicao, @NotBlank String timezone) {}

    /** Credenciais de canal. Vem do ambiente; o banco guarda apenas referencia, nunca o token. */
    public record Canal(@NotNull @Valid Whatsapp whatsapp) {}

    public record Whatsapp(
            String provedor, String numeroPrincipal, String token, String webhookSecret) {}

    /** Contrato com a Automacao: muda no maximo a URL e o token de filho para filho. */
    public record Automacao(String urlBase, String tokenPermanente) {}

    /** Destino do aviso quando a funcao critica degrada (ver doc 07, secao 7). */
    public record Alertas(String webhookGrupo) {}

    /**
     * Consulta uma feature flag de nivel 3 (arquivo de configuracao da instancia).
     *
     * <p>O mapa e aberto de proposito: ligar um modulo para um filho e uma linha de YAML, nao uma
     * alteracao de codigo. As flags de nivel 2, editaveis sem deploy, vem do banco a partir da
     * etapa E08 e tem precedencia sobre estas.
     */
    public boolean featureHabilitada(String nome) {
        return features.getOrDefault(nome, Boolean.FALSE);
    }
}
