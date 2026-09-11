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
 * @param webhookVerifyToken token escolhido pela instancia para o desafio {@code GET} de cadastro
 *     do webhook; nao e o App Secret
 * @param webhookSecret segredo usado na autenticacao do {@code POST}. Na Meta e o App Secret do
 *     HMAC; na Uzapi/Autotic e comparado ao parametro {@code secret} da query. Sem default: se nao
 *     vier, o verificador recusa tudo, que e melhor que aceitar qualquer requisicao que chegue na
 *     rota
 * @param janelaTextoLivre 24h na Meta oficial; configuravel porque nao e lei da natureza e a Meta ja
 *     mudou regra de janela antes
 * @param contaNegocio WABA ID usado para administrar templates. A Graph API nao oferece uma
 *     resolucao reversa suportada a partir do Phone Number ID; vazio deixa somente a administracao
 *     de templates indisponivel, sem afetar envio e recebimento.
 * @param usuarioApi {@code {username}} da rota da Uzapi/Autotic (E152), especifico deste provedor.
 *     Vazio e ignorado por qualquer outro adaptador.
 * @param versaoApi {@code {version}} da rota da Uzapi/Autotic (E152). Mesma logica de
 *     {@code usuarioApi}: vazio nao afeta provedores que nao usam esse segmento de URL.
 */
@ConfigurationProperties("synapse.canal.whatsapp")
public record CanalProperties(
        String provedor,
        String urlBase,
        String numeroPrincipal,
        String token,
        String webhookVerifyToken,
        String webhookSecret,
        Duration janelaTextoLivre,
        Duration timeout,
        String contaNegocio,
        String usuarioApi,
        String versaoApi) {

    public CanalProperties {
        provedor = (provedor == null || provedor.isBlank()) ? "meta-cloud" : provedor.trim();
        urlBase = (urlBase == null || urlBase.isBlank()) ? "https://graph.facebook.com/v21.0" : urlBase;
        janelaTextoLivre = janelaTextoLivre == null ? Duration.ofHours(24) : janelaTextoLivre;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        contaNegocio = (contaNegocio == null || contaNegocio.isBlank()) ? "" : contaNegocio.trim();
        usuarioApi = (usuarioApi == null || usuarioApi.isBlank()) ? "" : usuarioApi.trim();
        versaoApi = (versaoApi == null || versaoApi.isBlank()) ? "" : versaoApi.trim();
    }

    public boolean temSegredoDeWebhook() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean temTokenDeVerificacao() {
        return webhookVerifyToken != null && !webhookVerifyToken.isBlank();
    }

    public boolean temContaNegocio() {
        return contaNegocio != null && !contaNegocio.isBlank();
    }
}
