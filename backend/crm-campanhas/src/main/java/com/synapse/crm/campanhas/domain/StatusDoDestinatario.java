package com.synapse.crm.campanhas.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Onde um contato esta no funil de uma campanha.
 *
 * <p>Nao existe RESERVADO persistido: a reserva da politica proativa (E219), a mensagem, o evento da
 * outbox e a mudanca para ENFILEIRADO acontecem na mesma transacao. O que o banco mostra e sempre um
 * estado inteiro, nunca "reservei e morri no meio".
 *
 * <p>O funil e acumulado: quem foi LIDO tambem foi ENVIADO e ENTREGUE. {@link #niveisCruzados} diz quais
 * contadores incrementar quando o status avanca, inclusive quando a Meta pula um degrau.
 */
public enum StatusDoDestinatario {
    PENDENTE(0),
    ENFILEIRADO(1),
    ENVIADO(2),
    ENTREGUE(3),
    LIDO(4),
    /** O envio falhou ou nao pode ser confirmado. O motivo vive em {@link MotivoDoDestinatario}. */
    FALHA(-1),
    /** Excluido do envio por regra (sem telefone, opt-out, atendimento ativo...). Nunca sera enviado. */
    IGNORADO(-1);

    private final int nivel;

    StatusDoDestinatario(int nivel) {
        this.nivel = nivel;
    }

    /** Posicao no funil de entrega; negativo para FALHA e IGNORADO, que ficam fora dele. */
    public int nivel() {
        return nivel;
    }

    public boolean noFunil() {
        return nivel >= 0;
    }

    /** O destinatario ja saiu da fila: nao e mais candidato a envio. */
    public boolean aindaPodeSerEnviado() {
        return this == PENDENTE;
    }

    /** Uma falha pode chegar depois do aceite (a Meta reporta {@code failed} assincrono), mas nunca depois de entregue. */
    public boolean aceitaFalha() {
        return this == ENFILEIRADO || this == ENVIADO;
    }

    /** O novo status e posterior no funil de entrega? Entrega e fora de ordem, e o retrocesso e ignorado. */
    public boolean ehAnteriorA(StatusDoDestinatario novo) {
        return noFunil() && novo.noFunil() && nivel < novo.nivel;
    }

    /** Degraus do funil que passam a contar ao ir de {@code atual} para {@code novo}, em ordem. */
    public static List<StatusDoDestinatario> niveisCruzados(StatusDoDestinatario atual, StatusDoDestinatario novo) {
        List<StatusDoDestinatario> cruzados = new ArrayList<>();
        if (!atual.ehAnteriorA(novo)) {
            return cruzados;
        }
        for (StatusDoDestinatario candidato : values()) {
            if (candidato.noFunil() && candidato.nivel > atual.nivel && candidato.nivel <= novo.nivel
                    && candidato != PENDENTE) {
                cruzados.add(candidato);
            }
        }
        return cruzados;
    }
}
