package com.synapse.crm.atendimento.infrastructure.canal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Nenhum nome de header do fornecedor e presumido. OFF e a ausencia de configuracao. */
@ConfigurationProperties("synapse.canal.z-api.webhook")
public record ZApiWebhookProperties(
        boolean habilitado, Modo autenticacao, String segredo, String headerNome,
        int limiteCorpoBytes, Namespace namespaceStatus, boolean repasseAutomacaoHabilitado) {
    public enum Modo { DESATIVADO, SEGREDO_URL, HEADER_NATIVO }
    public enum Namespace { NAO_CONFIRMADO, WHATSAPP, PROCESSAMENTO }

    public ZApiWebhookProperties {
        autenticacao = autenticacao == null ? Modo.DESATIVADO : autenticacao;
        namespaceStatus = namespaceStatus == null ? Namespace.NAO_CONFIRMADO : namespaceStatus;
        headerNome = headerNome == null ? "" : headerNome;
    }

    public void validar() {
        if (!habilitado) return;
        new AutenticacaoWebhookZApi(true, segredo);
        if (autenticacao == Modo.DESATIVADO || limiteCorpoBytes <= 0 || limiteCorpoBytes == Integer.MAX_VALUE
                || (autenticacao == Modo.HEADER_NATIVO && !headerNome.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) {
            throw new IllegalArgumentException("Z_API_WEBHOOK_CONFIGURACAO_INVALIDA");
        }
    }

    public String namespaceConfirmado() {
        return namespaceStatus == Namespace.NAO_CONFIRMADO ? null : namespaceStatus.name();
    }

    @Override public String toString() {
        return "ZApiWebhookProperties[habilitado=" + habilitado + ", autenticacao=" + autenticacao + ", segredo=REDACTED]";
    }
}
