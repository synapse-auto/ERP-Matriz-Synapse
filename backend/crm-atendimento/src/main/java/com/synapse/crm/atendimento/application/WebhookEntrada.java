package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;

/**
 * Fila duravel de entrada: o que o provedor mandou, antes de o CRM entender.
 *
 * <p>O webhook grava aqui e devolve 200 na hora. Processar dentro do request seria a troca errada: o
 * provedor tem timeout curto e <b>reentrega</b> o que demora — uma tela lenta viraria mensagem
 * duplicada, e uma indisponibilidade momentanea do banco viraria mensagem perdida, porque o provedor
 * desiste depois de algumas tentativas.
 *
 * <p>Guardar o payload cru tambem e o que permite reprocessar depois de corrigir um bug de traducao.
 * O ACL descarta informacao de proposito; o que ele descartou nao volta.
 */
public interface WebhookEntrada {

    /**
     * Registra o payload. <b>Idempotente pela chave do POST</b>; a idempotência de cada mensagem
     * fica em {@code mensagem_recebida_idempotencia}, no processador.
     *
     * @return {@code true} se era novo; {@code false} se ja tinhamos — reentrega do provedor, que e
     *     rotina e nao erro
     */
    boolean registrarSeNovo(String idExterno, String provedor, String payloadCru, Instant recebidoEm);

    /** Pendentes reservados para este processador. */
    List<Pendente> reservarPendentes(int limite);

    void marcarProcessado(String idExterno, Instant quando);

    void reagendar(String idExterno, String erro);

    /**
     * Volta para a fila sem gastar tentativa. O provedor nao foi consultado (disjuntor aberto);
     * contar como falha esgotaria a linha enquanto a Meta estava saudavel.
     *
     * <p>{@code webhook_entrada} nao tem {@code proxima_tentativa_em} — essa coluna e da outbox, e
     * criar uma exigiria migration, que esta etapa nao faz. A linha permanece elegivel na proxima
     * rodada; o prazo absoluto a partir de {@code recebido_em} e a guarda contra fila eterna.
     */
    void adiar(String idExterno, String erro);

    /** Desistiu. A linha fica, com o erro, para alguem olhar. */
    void esgotar(String idExterno, Instant quando, String erro);

    long quantidadeEsgotada();

    record Pendente(String idExterno, String payloadCru, int tentativas, Instant recebidoEm) {}
}
