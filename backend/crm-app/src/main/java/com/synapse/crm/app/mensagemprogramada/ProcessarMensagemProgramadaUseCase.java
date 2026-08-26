package com.synapse.crm.app.mensagemprogramada;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.core.application.mensagemprogramada.MensagemProgramadaRepositorio;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Operações transacionais do job de mensagens programadas. */
@Service
public class ProcessarMensagemProgramadaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarMensagemProgramadaUseCase.class);

    private final MensagemProgramadaRepositorio mensagens;
    private final EnviarMensagemUseCase enviar;
    private final Clock relogio;

    public ProcessarMensagemProgramadaUseCase(
            MensagemProgramadaRepositorio mensagens, EnviarMensagemUseCase enviar, Clock relogio) {
        this.mensagens = mensagens;
        this.enviar = enviar;
        this.relogio = relogio;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<UUID> idsVencidos(int limite) {
        return mensagens.idsVencidos(Instant.now(relogio), limite);
    }

    /** Reserva por atualização condicional e materializa a mensagem/outbox na mesma transação. */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void processar(UUID id) {
        Instant agora = Instant.now(relogio);
        MensagemProgramada programada = mensagens.reservarVencida(id, agora).orElse(null);
        if (programada == null) {
            return;
        }
        try {
            enviar.executarComoServico(
                    programada.leadId(),
                    programada.atendenteId(),
                    new ConteudoDeEnvio.MensagemLivre(programada.conteudo()),
                    programada.id());
        } catch (RuntimeException erro) {
            // A transação faz rollback e mantém AGENDADA; a outbox já possui o retry/backoff para a
            // entrega posterior. O aviso evita que uma falha de dados/estado desapareça em silêncio.
            log.warn("Falha ao processar mensagem programada {}; ela permanece AGENDADA", id, erro);
            throw erro;
        }
    }
}
