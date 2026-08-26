package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;

/**
 * Transactional Outbox do canal de saida.
 *
 * <p>A regra que justifica tudo aqui: <b>a intencao de enviar e gravada na mesma transacao que grava
 * a mensagem</b>. Ou as duas coisas acontecem, ou nenhuma. Nao existe estado em que a conversa mostre
 * uma mensagem que ninguem jamais tentou enviar, nem envio de mensagem que nao esta na conversa.
 *
 * <p>A alternativa que a E04 usava — reagir com {@code AFTER_COMMIT} e chamar o provedor dali — falha
 * de um jeito particularmente ruim: se o processo morre entre o commit e o listener, a mensagem esta
 * gravada, o atendente a ve na tela, e ela nunca foi enviada. Nada no sistema sabe disso.
 *
 * <p>Por isso o envio nunca acontece dentro da transacao: chamada de rede no caminho critico
 * significaria a aba Atendimentos esperando o WhatsApp responder.
 */
public interface Outbox {

    /**
     * Enfileira um envio. Roda na transacao de quem chamou — de proposito.
     *
     * @param enviadoEm chave de particao da mensagem, guardada para o publisher achar a linha depois
     */
    void enfileirarEnvio(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo);

    /**
     * Agenda o repasse do webhook cru para a Automacao na mesma transacao que reconhece a entrada.
     * Reentregas byte a byte identicas sao idempotentes.
     */
    void enfileirarRepasseWebhook(String payloadCru, String assinatura, Instant recebidoEm);

    /**
     * Reserva de forma persistida os pendentes cuja hora de tentar ja chegou.
     *
     * <p>A reserva e gravada antes de qualquer chamada externa. Enquanto o instante de expiracao
     * nao chega, outra instancia nao pode selecionar a linha; se o processo morrer, a linha volta
     * para a fila depois desse instante.
     */
    List<EnvioPendente> reservarPendentes(int limite, Instant agora, Instant reservaAte);

    /** Repasses de webhook cuja proxima tentativa ja chegou. */
    List<RepasseWebhookPendente> reservarRepassesWebhookPendentes(int limite, Instant agora);

    /** Deu certo: marca publicado e sai da fila para sempre. */
    void marcarPublicado(UUID outboxId, Instant quando);

    /** Falhou, mas ainda ha esperanca: incrementa tentativas e agenda a proxima com backoff. */
    void reagendar(UUID outboxId, Instant proximaTentativa, String erro);

    /**
     * Desistiu.
     *
     * <p>A linha <b>nao</b> e apagada nem marcada como publicada: fica para inspecao, com o ultimo
     * erro. Descartar em silencio uma mensagem que o atendente escreveu e a pior falha deste modulo —
     * ninguem descobre, e para o cliente e como se a empresa nunca tivesse respondido.
     */
    void esgotar(UUID outboxId, Instant quando, String erro);

    /** Quantas desistiram. Espera-se zero; qualquer valor acima disso e alarme. */
    long quantidadeEsgotada();

    /** Quantos repasses para a Automacao esgotaram as tentativas. */
    long quantidadeRepassesWebhookEsgotados();

    /**
     * Uma linha da outbox pronta para o publisher.
     *
     * @param mensagemId e {@code enviadoEm} identificam a mensagem na tabela particionada
     */
    record EnvioPendente(
            UUID outboxId,
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo,
            int tentativas) {}

    record RepasseWebhookPendente(
            UUID outboxId, String payloadCru, String assinatura, int tentativas) {}
}
