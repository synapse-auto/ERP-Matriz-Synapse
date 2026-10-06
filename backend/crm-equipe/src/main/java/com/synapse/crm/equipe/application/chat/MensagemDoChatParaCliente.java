package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

/**
 * O que uma mensagem do Chat Interno tem de encaminhável para um cliente, já lido do banco e sem
 * nada do contexto interno (reações, citação, editada, remetente).
 *
 * <p>{@code midiaReferencia} é a referência opaca do storage; nunca vai ao navegador. Para texto,
 * {@code texto} é o corpo; para mídia, {@code legenda} é o texto que acompanha o arquivo.
 */
public record MensagemDoChatParaCliente(
        UUID conversaId,
        UUID mensagemId,
        String tipo,
        String texto,
        String midiaReferencia,
        String nomeArquivo,
        String mimetype,
        Long tamanhoBytes,
        String legenda) {

    public boolean ehMidia() {
        return midiaReferencia != null;
    }
}
