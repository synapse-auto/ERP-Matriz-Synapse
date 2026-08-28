package com.synapse.crm.atendimento.domain.canal;

/**
 * Um modelo de mensagem do provedor, ja traduzido para vocabulario do CRM.
 *
 * <p>Nome, idioma e categoria existem porque a Meta os exige para enviar fora da janela de 24h —
 * nao porque o dominio goste de detalhe de API. O payload cru ({@code components}, {@code
 * parameter_format}) nao atravessa esta fronteira.
 */
public record TemplateDoCanal(
        String nome,
        String idioma,
        Categoria categoria,
        Status status,
        String corpo,
        int quantidadeDeParametros) {

    public enum Categoria {
        UTILIDADE,
        MARKETING,
        AUTENTICACAO
    }

    public enum Status {
        APROVADO,
        PENDENTE,
        REJEITADO,
        PAUSADO,
        DESCONHECIDO
    }
}
