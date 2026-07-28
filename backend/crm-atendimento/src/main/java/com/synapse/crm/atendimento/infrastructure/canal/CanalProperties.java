package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do canal da instancia.
 *
 * <p>{@code provedor} e o unico lugar do sistema que sabe qual adaptador esta ativo. Trocar de
 * provedor para um filho e mudar esta variavel de ambiente — nao ha {@code if} em lugar nenhum, e
 * nenhum caso de uso pergunta.
 *
 * @param provedor chave do adaptador; casa com {@code CanalGateway.provedor()}
 * @param urlBase raiz da API do provedor
 * @param webhookSecret segredo de validacao de assinatura do webhook. Sem default: se nao vier, o
 *     verificador recusa tudo, que e melhor que aceitar qualquer requisicao que chegue na rota
 * @param janelaTextoLivre 24h na Meta oficial; configuravel porque nao e lei da natureza e a Meta ja
 *     mudou regra de janela antes
 */
@ConfigurationProperties("synapse.canal.whatsapp")
public record CanalProperties(
        String provedor,
        String urlBase,
        String numeroPrincipal,
        String token,
        String webhookSecret,
        Duration janelaTextoLivre,
        Duration timeout) {

    public CanalProperties {
        provedor = (provedor == null || provedor.isBlank()) ? "meta-cloud" : provedor.trim();
        urlBase = (urlBase == null || urlBase.isBlank()) ? "https://graph.facebook.com/v21.0" : urlBase;
        janelaTextoLivre = janelaTextoLivre == null ? Duration.ofHours(24) : janelaTextoLivre;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    public boolean temSegredoDeWebhook() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
