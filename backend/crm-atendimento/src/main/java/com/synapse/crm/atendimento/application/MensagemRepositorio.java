package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

/**
 * Porta do caminho critico de escrita de mensagem.
 *
 * <p>A escrita e o caminho critico do produto inteiro. A implementacao grava pelo pool do chat, sem
 * nada que possa falhar ou demorar junto: validacao de midia, resumo por IA e notificacao sao reacoes
 * {@code AFTER_COMMIT} ou vao para fila.
 *
 */
public interface MensagemRepositorio {

    /** Grava a mensagem. O {@code enviadoEm} do agregado escolhe a particao. */
    Mensagem registrar(Mensagem mensagem);

    /**
     * Move a mensagem no ciclo de entrega.
     *
     * <p>Quem chama e o publisher da outbox, depois de o provedor responder: {@code PENDENTE} vira
     * {@code ENVIADO} quando aceita, {@code FALHOU} quando esgota. O atendente nunca ve tique de
     * enviado numa mensagem que nao saiu.
     *
     * @param enviadoEm chave de particao da mensagem; sem ela o banco varre todas as particoes
     */
    void atualizarStatusEntrega(UUID mensagemId, Instant enviadoEm, StatusEntrega status);

    /**
     * Avanca o ciclo de entrega a partir do {@code wamid} que o provedor mandou em {@code statuses[]}.
     *
     * <p>So grava se o novo status for posterior ao atual — a Meta entrega fora de ordem. Junta
     * {@code atendimento} de proposito: sem contexto de servico a RLS esconde a linha e o UPDATE
     * vira no-op, o mesmo padrao da V50. Devolve vazio quando o wamid nao e nosso, quando o status
     * nao avanca, ou quando a RLS negou a escrita.
     */
    Optional<StatusDeEntregaAplicado> aplicarStatusDoProvedor(
            String wamid, StatusEntrega novo, Integer codigoErro, String tituloErro);

    record StatusDeEntregaAplicado(
            UUID mensagemId, UUID atendimentoId, UUID leadId, StatusEntrega status) {}
}
