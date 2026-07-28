package com.synapse.crm.atendimento.application;

import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;

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
}
