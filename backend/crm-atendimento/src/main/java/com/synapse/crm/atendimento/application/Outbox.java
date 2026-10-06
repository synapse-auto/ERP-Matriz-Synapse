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
    default void enfileirarEnvio(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo) {
        enfileirarEnvio(
                mensagemId,
                enviadoEm,
                atendimentoId,
                leadId,
                telefoneDestino,
                credencialId,
                conteudo,
                null);
    }

    void enfileirarEnvio(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo,
            String contextoWamid);

    /**
     * Enfileira junto do destino os enderecos observados na mesma leitura do lead. Eles permitem
     * descartar o endereco retornado por uma resposta tardia se o usuario editou o contato antes
     * do aceite chegar.
     */
    default void enfileirarEnvio(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo,
            String contextoWamid,
            String telefoneCanonicoObservado,
            String telefoneProvedorObservado) {
        enfileirarEnvio(
                mensagemId,
                enviadoEm,
                atendimentoId,
                leadId,
                telefoneDestino,
                credencialId,
                conteudo,
                contextoWamid);
    }

    /** Enfileira envio originado por mensagem programada, preservando a origem no payload. */
    default void enfileirarEnvioProgramado(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo,
            UUID mensagemProgramadaId) {
        enfileirarEnvio(mensagemId, enviadoEm, atendimentoId, leadId, telefoneDestino, credencialId, conteudo);
    }

    /** Variante de mensagem programada que preserva o snapshot do contato. */
    default void enfileirarEnvioProgramado(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo,
            UUID mensagemProgramadaId,
            String telefoneCanonicoObservado,
            String telefoneProvedorObservado) {
        enfileirarEnvioProgramado(
                mensagemId,
                enviadoEm,
                atendimentoId,
                leadId,
                telefoneDestino,
                credencialId,
                conteudo,
                mensagemProgramadaId);
    }

    /**
     * Agenda o repasse do webhook cru para a Automacao na mesma transacao que reconhece a entrada.
     * Reentregas byte a byte identicas sao idempotentes.
     */
    void enfileirarRepasseWebhook(String payloadCru, String assinatura, Instant recebidoEm);

    /** Enfileira o pedido leve que dispara a execução assíncrona de resumo no n8n. */
    void enfileirarSolicitacaoResumoIa(
            UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm);

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

    /** Solicitações de resumo ainda não entregues ao n8n. */
    List<SolicitacaoResumoIaPendente> reservarSolicitacoesResumoIaPendentes(
            int limite, Instant agora, Instant reservaAte);

    /** Deu certo: marca publicado e sai da fila para sempre.
     *
     * @return {@code true} somente quando esta chamada ganhou a transição; {@code false} indica
     * que outro worker já publicou ou esgotou a mesma linha.
     */
    boolean marcarPublicado(UUID outboxId, Instant quando);

    /**
     * Grava que a linha esta prestes a ser entregue ao provedor (E209), numa transacao propria e
     * curta, imediatamente antes da chamada.
     *
     * <p>E esta marca que impede o reenvio: se o processo morrer depois da chamada e antes do
     * resultado, a linha volta a ser elegivel com a marca preenchida e a conciliacao a desvia para
     * conferencia em vez de enviar de novo.
     *
     * @return {@code false} quando a linha ja foi publicada ou esgotada; nesse caso nao se envia
     */
    boolean marcarDespachando(UUID outboxId, Instant quando);

    /**
     * Despachos cuja reserva expirou sem resultado registrado (E209): o provedor pode ou nao ter
     * aceitado, e nao ha como saber. A linha sai da fila de envio (fica esgotada, para conferencia) e
     * <b>nunca</b> e reenviada sozinha.
     *
     * @return as linhas desviadas, para quem chamou atualizar a mensagem e alarmar
     */
    List<EnvioPendente> conciliarDespachosSemResultado(int limite, Instant agora);

    /** Falhou, mas ainda ha esperanca: incrementa tentativas e agenda a proxima com backoff. */
    boolean reagendar(UUID outboxId, Instant proximaTentativa, String erro);

    /**
     * Desistiu.
     *
     * <p>A linha <b>nao</b> e apagada nem marcada como publicada: fica para inspecao, com o ultimo
     * erro. Descartar em silencio uma mensagem que o atendente escreveu e a pior falha deste modulo —
     * ninguem descobre, e para o cliente e como se a empresa nunca tivesse respondido.
     */
    boolean esgotar(UUID outboxId, Instant quando, String erro);

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
            int tentativas,
            String contextoWamid,
            String telefoneCanonicoObservado,
            String telefoneProvedorObservado,
            boolean possuiSnapshotDoContato) {

        /** Compatibilidade com payloads antigos que ainda nao guardam o snapshot do contato. */
        public EnvioPendente(
                UUID outboxId,
                UUID mensagemId,
                Instant enviadoEm,
                UUID atendimentoId,
                UUID leadId,
                String telefoneDestino,
                UUID credencialId,
                ConteudoDeEnvio conteudo,
                int tentativas,
                String contextoWamid) {
            this(
                    outboxId,
                    mensagemId,
                    enviadoEm,
                    atendimentoId,
                    leadId,
                    telefoneDestino,
                    credencialId,
                    conteudo,
                    tentativas,
                    contextoWamid,
                    null,
                    null,
                    false);
        }

        public EnvioPendente(
                UUID outboxId,
                UUID mensagemId,
                Instant enviadoEm,
                UUID atendimentoId,
                UUID leadId,
                String telefoneDestino,
                UUID credencialId,
                ConteudoDeEnvio conteudo,
                int tentativas) {
            this(
                    outboxId,
                    mensagemId,
                    enviadoEm,
                    atendimentoId,
                    leadId,
                    telefoneDestino,
                    credencialId,
                    conteudo,
                    tentativas,
                    null,
                    null,
                    null,
                    false);
        }
    }

    record RepasseWebhookPendente(
            UUID outboxId, String payloadCru, String assinatura, int tentativas) {}

    record SolicitacaoResumoIaPendente(
            UUID outboxId,
            UUID solicitacaoId,
            UUID leadId,
            UUID atendimentoId,
            Instant solicitadoEm,
            int tentativas) {}
}
