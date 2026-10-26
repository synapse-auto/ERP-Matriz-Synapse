package com.synapse.crm.app.mensagemprogramada;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.ContaDeTesteSomenteLeituraException;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.origem.OrigemDaMensagem;
import com.synapse.crm.atendimento.application.origem.RegistroDeOrigemDaMensagem;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.core.application.mensagemprogramada.MensagemProgramadaRepositorio;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.equipe.application.usuario.AuditarBloqueioContaTesteUseCase;
import com.synapse.crm.equipe.application.usuario.ClassificacaoDeTesteRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Operações transacionais do job de mensagens programadas. */
@Service
public class ProcessarMensagemProgramadaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarMensagemProgramadaUseCase.class);

    private final MensagemProgramadaRepositorio mensagens;
    private final EnviarMensagemUseCase enviar;
    private final RegistroDeOrigemDaMensagem origens;
    private final Clock relogio;
    private final ClassificacaoDeTesteRepositorio classificacao;
    private final AuditarBloqueioContaTesteUseCase auditoria;
    @Value("${synapse.canal.transicao.saidas-suspensas:false}") private boolean saidasSuspensas;

    public ProcessarMensagemProgramadaUseCase(
            MensagemProgramadaRepositorio mensagens,
            EnviarMensagemUseCase enviar,
            RegistroDeOrigemDaMensagem origens,
            Clock relogio,ClassificacaoDeTesteRepositorio classificacao,AuditarBloqueioContaTesteUseCase auditoria) {
        this.mensagens = mensagens;
        this.enviar = enviar;
        this.origens = origens;
        this.relogio = relogio;
        this.classificacao=classificacao; this.auditoria=auditoria;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<UUID> idsVencidos(int limite) {
        if(saidasSuspensas) return List.of();
        return mensagens.idsVencidos(Instant.now(relogio), limite);
    }

    /** Reserva por atualização condicional e materializa a mensagem/outbox na mesma transação. */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void processar(UUID id) {
        if(saidasSuspensas) return;
        Instant agora = Instant.now(relogio);
        MensagemProgramada programada = mensagens.reservarVencida(id, agora).orElse(null);
        if (programada == null) {
            return;
        }
        try {
            if (classificacao.contaDeTeste(programada.atendenteId())) {
                auditoria.executar(programada.atendenteId(),"MENSAGEM_PROGRAMADA");
                throw new ContaDeTesteSomenteLeituraException();
            }
            EnviarMensagemUseCase.Resultado enviado = enviar.executarComoServico(
                    programada.leadId(),
                    programada.atendenteId(),
                    new ConteudoDeEnvio.MensagemLivre(programada.conteudo()),
                    programada.id());
            origens.registrar(
                    "job de mensagens programadas",
                    enviado.mensagem().id(),
                    enviado.atendimento().id(),
                    enviado.atendimento().leadId(),
                    enviado.mensagem().enviadoEm(),
                    OrigemDaMensagem.programada(programada.id()));
        } catch (RuntimeException erro) {
            // A transação faz rollback e mantém AGENDADA; a outbox já possui o retry/backoff para a
            // entrega posterior. O aviso evita que uma falha de dados/estado desapareça em silêncio.
            log.warn("Falha ao processar mensagem programada {}; ela permanece AGENDADA", id, erro);
            throw erro;
        }
    }
}
