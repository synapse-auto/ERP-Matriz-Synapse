package com.synapse.crm.atendimento.application.encaminhamentodochat;

/** Por que uma mensagem do Chat Interno não pode ir a este cliente agora. */
public enum MotivoDeBloqueio {
    /** O atendimento de destino já foi finalizado; o envio não abre outro por conta própria. */
    ATENDIMENTO_FINALIZADO,
    /** Provedor oficial: texto livre só dentro de 24h da última mensagem do cliente. */
    FORA_DA_JANELA,
    /** O tipo ou o formato do arquivo não é aceito pelo envio ao cliente. */
    TIPO_NAO_SUPORTADO,
    /** O arquivo passa do limite configurado para o envio ao cliente. */
    ARQUIVO_ACIMA_DO_LIMITE,
    /** O chat não guardou o tamanho do arquivo, então o limite não pode ser conferido. */
    ARQUIVO_SEM_TAMANHO
}
