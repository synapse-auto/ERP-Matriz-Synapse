package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
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
 * <p>Roda em {@link ContextoDeServico}: nao ha usuario autenticado num job, e sem contexto as
 * politicas RLS negariam tudo — o publisher veria uma outbox vazia e as mensagens ficariam paradas
 * para sempre, sem erro nenhum. Falhar fechado protege leitura indevida, mas aqui produziria falha
 * silenciosa; declarar o contexto de servico resolve sem afrouxar politica.
 */
@Component
public class PublicadorDaOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDaOutbox.class);

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

    public PublicadorDaOutbox(
            Outbox outbox,
            MensagemRepositorio mensagens,
            CanalGateway canal,
            OutboxProperties propriedades,
            Clock relogio) {
        this.outbox = outbox;
        this.mensagens = mensagens;
        this.canal = canal;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /** Agendamento curto: e o que define a latencia percebida entre o clique e a saida. */
    @Scheduled(fixedDelayString = "${synapse.canal.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        ContextoDeServico.executarComo("publicador-outbox", this::rodada);
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

    /**
     * Alarme periodico. Rede de seguranca sem alarme some do radar ate a divida ficar cara — a mesma
     * licao da particao DEFAULT da V5.
     */
    @Scheduled(cron = "${synapse.canal.outbox.cron-alerta:0 */15 * * * *}")
    public void alertarSobreEsgotadas() {
        ContextoDeServico.executarComo("alerta-outbox", () -> {
            long esgotadas = contarEsgotadas();
            if (esgotadas > 0) {
                log.error(
                        "{} {} mensagem(ns) na outbox esgotaram as tentativas e nunca chegaram ao "
                                + "cliente. Inspecione: SELECT id, ultimo_erro, tentativas FROM "
                                + "outbox_evento WHERE esgotado_em IS NOT NULL;",
                        MARCADOR_ALARME,
                        esgotadas);
            }
        });
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotadas() {
        return outbox.quantidadeEsgotada();
    }
}
