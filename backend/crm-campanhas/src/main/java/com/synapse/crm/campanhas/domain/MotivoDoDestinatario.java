package com.synapse.crm.campanhas.domain;

/**
 * Por que um destinatario nao foi enviado ou falhou. O codigo (nome do enum) e o que o banco guarda e a
 * tela traduz via catalogo de textos; nenhum texto de interface mora aqui.
 */
public enum MotivoDoDestinatario {
    // Exclusoes do publico, decididas por regra antes de qualquer envio.
    TELEFONE_INVALIDO,
    SEM_NOME_UTILIZAVEL,
    OPT_OUT,
    JA_RECEBEU,
    ATENDIMENTO_ATIVO,
    RECEBEU_PROATIVA_RECENTE,

    // Decididas na hora de enviar, pela politica proativa (E219) e pelo estado do lead.
    COOLDOWN,
    TETO_DIARIO_POR_LEAD,
    TIPO_PROATIVO_DESLIGADO,
    AUTOMACAO_PROATIVA_DESLIGADA,
    OCORRENCIA_JA_REGISTRADA,
    LEAD_INDISPONIVEL,
    ATENDIMENTO_ABERTO_NO_ENVIO,
    OPT_OUT_NO_ENVIO,

    // Falhas depois de enfileirar.
    ENVIO_NAO_CONFIRMADO,
    FALHA_NO_PROVEDOR,
    ENFILEIRADO_SEM_CONFIRMACAO;

    /** Os que o assistente mostra como "excluidos do publico", na ordem em que a regra e aplicada. */
    public boolean exclusaoDoPublico() {
        return ordinal() <= RECEBEU_PROATIVA_RECENTE.ordinal();
    }
}
