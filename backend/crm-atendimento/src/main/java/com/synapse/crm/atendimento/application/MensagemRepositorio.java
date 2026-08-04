package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

/**
 * Porta de escrita e leitura de mensagem.
 *
 * <p>A escrita e o caminho critico do produto inteiro. A implementacao grava pelo pool do chat, sem
 * nada que possa falhar ou demorar junto: validacao de midia, resumo por IA e notificacao sao reacoes
 * {@code AFTER_COMMIT} ou vao para fila.
 *
 * <p>A leitura e paginada por construcao — nao existe "todas as mensagens deste atendimento". Uma
 * conversa de meses tem milhares de linhas e a tela mostra as ultimas.
 */
public interface MensagemRepositorio {

    /** Grava a mensagem. O {@code enviadoEm} do agregado escolhe a particao. */
    Mensagem registrar(Mensagem mensagem);

    /** As mais recentes primeiro, no maximo {@code limite}. */
    List<Mensagem> ultimasDoAtendimento(UUID atendimentoId, int limite);

    /** Pagina para tras por uma chave estavel, sem deslocamento por mensagens novas. */
    List<Mensagem> anteriores(
            UUID atendimentoId, Instant cursorEnviadoEm, UUID cursorId, int limite);

    /**
     * Tudo que chegou <b>depois</b> de {@code desde}, mais antiga primeiro — a reconciliacao que o
     * cliente pede ao reconectar o WebSocket (E06). Sem limite de proposito: a janela e curta (o
     * tempo que o socket ficou fora), nunca a conversa inteira.
     */
    List<Mensagem> desde(UUID atendimentoId, Instant desde);

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
}
