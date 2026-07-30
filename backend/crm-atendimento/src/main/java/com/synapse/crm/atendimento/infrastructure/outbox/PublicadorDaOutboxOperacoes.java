package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Drena a outbox: pega o que esta pendente, entrega ao provedor, marca o resultado.
 *
 * <p>E aqui — e so aqui — que uma chamada externa acontece no fluxo de envio. A transacao que grava a
 * mensagem termina sem nunca falar com a rede; este job, depois e fora do caminho do usuario, e que
 * conversa com o WhatsApp. E o que permite o atendente receber resposta da tela em milissegundos
 * mesmo com o provedor lento.
 *
 * <p>Bean separado de {@link PublicadorDaOutbox} de proposito: quem tem o {@code @Scheduled} e o
 * outro, e ele chama {@link #rodada()} atraves desta instancia injetada — uma chamada externa de
 * verdade, que passa pelo proxy do Spring. Se o metodo transacional vivesse na mesma classe do
 * {@code @Scheduled} e fosse chamado por {@code this::rodada}, a chamada pularia o proxy CGLIB e o
 * {@code @Transactional} simplesmente nao valeria (o bug corrigido na E07b).
 *
 * <p>Roda em {@link ContextoDeServico}, publicado por {@link PublicadorDaOutbox} antes de chamar:
 * nao ha usuario autenticado num job, e sem contexto as politicas RLS negariam tudo — o publisher
 * veria uma outbox vazia e as mensagens ficariam paradas para sempre, sem erro nenhum. Falhar
 * fechado protege leitura indevida, mas aqui produziria falha silenciosa; declarar o contexto de
 * servico resolve sem afrouxar politica.
 */
@Component
public class PublicadorDaOutboxOperacoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDaOutboxOperacoes.class);

    /**
     * Marcador do alarme de esgotamento. Fixo e greppavel — e o que alguem digita no agregador de log
     * quando um cliente diz que nao recebeu resposta.
     */
    public static final String MARCADOR_ALARME = "[ALERTA_OUTBOX_ESGOTADA]";

    private final Outbox outbox;
    private final MensagemRepositorio mensagens;
    private final CanalGateway canal;
    private final OutboxProperties propriedades;
    private final Clock relogio;
    private final ApplicationEventPublisher eventos;

    public PublicadorDaOutboxOperacoes(
            Outbox outbox,
            MensagemRepositorio mensagens,
            CanalGateway canal,
            OutboxProperties propriedades,
            Clock relogio,
            ApplicationEventPublisher eventos) {
        this.outbox = outbox;
        this.mensagens = mensagens;
        this.canal = canal;
        this.propriedades = propriedades;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    /**
     * Uma rodada, numa transacao so.
     *
     * <p>A transacao segura os {@code FOR UPDATE SKIP LOCKED} da reserva ate o fim: enquanto ela
     * estiver aberta, nenhuma outra instancia pega as mesmas linhas. E e a mesma transacao que
     * atualiza {@code mensagem.status_entrega}, entao nao ha estado em que a outbox diz "publicado" e
     * a mensagem continua {@code PENDENTE} na tela do atendente.
     */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public int rodada() {
        Instant agora = Instant.now(relogio);
        List<Outbox.EnvioPendente> pendentes = outbox.reservarPendentes(propriedades.lote(), agora);

        for (Outbox.EnvioPendente pendente : pendentes) {
            entregar(pendente, agora);
        }
        return pendentes.size();
    }

    private void entregar(Outbox.EnvioPendente pendente, Instant agora) {
        ResultadoDeEnvio resultado = enviarSemDeixarEscapar(pendente);

        switch (resultado) {
            case ResultadoDeEnvio.Aceito aceito -> {
                outbox.marcarPublicado(pendente.outboxId(), agora);
                mensagens.atualizarStatusEntrega(
                        pendente.mensagemId(), pendente.enviadoEm(), StatusEntrega.ENVIADO);
                eventos.publishEvent(new MudancaDeStatusDeEntrega(
                        pendente.mensagemId(),
                        pendente.atendimentoId(),
                        pendente.leadId(),
                        StatusEntrega.ENVIADO.name(),
                        agora));
                log.debug(
                        "Mensagem {} aceita pelo provedor {} como {}.",
                        pendente.mensagemId(),
                        canal.provedor(),
                        aceito.idExterno());
            }
            case ResultadoDeEnvio.Recusado recusado -> {
                int tentativasFeitas = pendente.tentativas() + 1;
                boolean desiste =
                        recusado.permanente() || tentativasFeitas >= propriedades.maximoDeTentativas();

                if (desiste) {
                    esgotar(pendente, agora, recusado, tentativasFeitas);
                } else {
                    // Ainda ha esperanca: volta para a fila com espera maior.
                    outbox.reagendar(
                            pendente.outboxId(),
                            agora.plus(propriedades.esperaApos(pendente.tentativas())),
                            recusado.motivo());
                }
            }
        }
    }

    private void esgotar(
            Outbox.EnvioPendente pendente,
            Instant agora,
            ResultadoDeEnvio.Recusado recusado,
            int tentativasFeitas) {

        // A linha NAO e apagada: fica com o ultimo erro, para alguem olhar.
        outbox.esgotar(pendente.outboxId(), agora, recusado.motivo());
        mensagens.atualizarStatusEntrega(
                pendente.mensagemId(), pendente.enviadoEm(), StatusEntrega.FALHOU);
        // A transicao que mais importa chegar na tela: um tique de "enviado" que
        // nunca devia ter aparecido e pior que um alerta.
        eventos.publishEvent(new MudancaDeStatusDeEntrega(
                pendente.mensagemId(),
                pendente.atendimentoId(),
                pendente.leadId(),
                StatusEntrega.FALHOU.name(),
                agora));

        log.error(
                "{} mensagem {} do lead {} nao foi entregue ao provedor {} apos {} tentativa(s). "
                        + "Motivo: {} ({}). A linha {} da outbox fica para inspecao e NAO sera "
                        + "reenviada sozinha.",
                MARCADOR_ALARME,
                pendente.mensagemId(),
                pendente.leadId(),
                canal.provedor(),
                tentativasFeitas,
                recusado.motivo(),
                recusado.permanente() ? "recusa permanente" : "limite de tentativas",
                pendente.outboxId());
    }

    /**
     * O gateway devolve {@link ResultadoDeEnvio}; se ainda assim escapar excecao — bug de adaptador,
     * breaker sem fallback, {@code NullPointerException} —, ela vira recusa temporaria em vez de
     * derrubar a rodada inteira. Uma linha problematica nao pode impedir as outras de sair.
     */
    private ResultadoDeEnvio enviarSemDeixarEscapar(Outbox.EnvioPendente pendente) {
        try {
            return canal.enviar(new CanalGateway.Envio(
                    pendente.mensagemId(),
                    pendente.telefoneDestino(),
                    pendente.conteudo(),
                    pendente.credencialId()));
        } catch (RuntimeException e) {
            log.warn("Falha nao tratada ao enviar a mensagem {}.", pendente.mensagemId(), e);
            return ResultadoDeEnvio.Recusado.temporario(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotadas() {
        return outbox.quantidadeEsgotada();
    }
}
