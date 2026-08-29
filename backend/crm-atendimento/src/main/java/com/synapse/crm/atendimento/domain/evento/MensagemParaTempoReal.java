package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;

/**
 * Uma mensagem, pronta para a tela — nao para o banco.
 *
 * <p>Deliberadamente separado de {@link EventoDeAtendimento}: aquele e o registro dos <em>fatos</em>
 * do modulo (consumido por timeline e auditoria, que precisam de pouco — quem, quando, o id). Este
 * carrega o que a tela precisa para renderizar sem uma segunda ida ao banco — remetente, conteudo,
 * status — porque o WebSocket existe para a conversa <em>parecer</em> instantanea, e um evento que so
 * avisa "algo mudou, va buscar" nao entrega isso.
 *
 * <p>Publicado pelos mesmos casos de uso que ja publicam {@code EventoDeAtendimento}, no mesmo
 * instante, com o {@code Mensagem} que eles ja tem em mao — sem consulta extra.
 */
public record MensagemParaTempoReal(
        UUID atendimentoId,
        UUID leadId,
        UUID mensagemId,
        String remetenteTipo,
        UUID remetenteId,
        String tipo,
        String conteudo,
        String midiaUrl,
        String midiaMetadados,
        String opcoes,
        String statusEntrega,
        Instant enviadoEm,
        CitacaoDeMensagem citacao) {

    public MensagemParaTempoReal(
            UUID atendimentoId,
            UUID leadId,
            UUID mensagemId,
            String remetenteTipo,
            UUID remetenteId,
            String tipo,
            String conteudo,
            String midiaUrl,
            String midiaMetadados,
            String opcoes,
            String statusEntrega,
            Instant enviadoEm) {
        this(
                atendimentoId,
                leadId,
                mensagemId,
                remetenteTipo,
                remetenteId,
                tipo,
                conteudo,
                midiaUrl,
                midiaMetadados,
                opcoes,
                statusEntrega,
                enviadoEm,
                null);
    }
}
