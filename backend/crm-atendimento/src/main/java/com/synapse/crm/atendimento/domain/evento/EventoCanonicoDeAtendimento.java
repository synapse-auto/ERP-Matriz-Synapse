package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sinal versionado de que o estado canonico de um atendimento precisa ser reconciliado.
 *
 * <p>O payload e deliberadamente minimo: nao transporta nome, telefone, texto, midia, token nem
 * qualquer outro dado da conversa. O navegador usa estes identificadores e a versao apenas para
 * buscar o snapshot REST autorizado. Assim, WebSocket fora de ordem nao vira fonte de verdade.
 */
public record EventoCanonicoDeAtendimento(
        UUID eventoId,
        int versaoContrato,
        Tipo tipo,
        UUID atendimentoId,
        UUID leadId,
        long versao,
        Instant ocorridoEm) {

    public static final int VERSAO_CONTRATO_ATUAL = 1;

    public EventoCanonicoDeAtendimento {
        Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        Objects.requireNonNull(tipo, "tipo e obrigatorio");
        Objects.requireNonNull(atendimentoId, "atendimentoId e obrigatorio");
        Objects.requireNonNull(leadId, "leadId e obrigatorio");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        if (versaoContrato < 1) throw new IllegalArgumentException("versaoContrato deve ser positiva");
        if (versao < 1) throw new IllegalArgumentException("versao deve ser positiva");
    }

    public static EventoCanonicoDeAtendimento criar(
            Tipo tipo, UUID atendimentoId, UUID leadId, long versao, Instant ocorridoEm) {
        return new EventoCanonicoDeAtendimento(
                UUID.randomUUID(), VERSAO_CONTRATO_ATUAL, tipo, atendimentoId, leadId, versao, ocorridoEm);
    }

    public enum Tipo {
        ATENDIMENTO_INICIADO,
        MENSAGEM_RECEBIDA,
        MENSAGEM_ENVIADA,
        MENSAGEM_ENVIADA_AUTOMACAO,
        ATENDIMENTO_TRANSFERIDO,
        ATENDIMENTO_DEVOLVIDO_IA,
        ATENDIMENTO_FINALIZADO,
        PEDIDO_ENTRADA_SOLICITADO,
        CONVITE_ATENDIMENTO_CRIADO,
        PEDIDO_ENTRADA_APROVADO,
        PEDIDO_ENTRADA_RECUSADO,
        PARTICIPANTE_ENTROU,
        PARTICIPANTE_SAIU
    }
}
