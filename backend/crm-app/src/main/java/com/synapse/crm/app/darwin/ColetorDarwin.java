package com.synapse.crm.app.darwin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.darwin.*;
import com.synapse.crm.core.application.lead.EnriquecerCadastroDoLeadUseCase;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.evento.ColetaCadastralDoLeadAtualizada;
import com.synapse.crm.core.domain.evento.DadosCadastraisDoLeadAlterados;
import com.synapse.crm.core.domain.lead.Cpf;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadConsultado;
import com.synapse.crm.core.infrastructure.darwin.DarwinProperties;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** HTTP exclusivamente no pool dedicado, fora de transação. Cache/lease sobrevivem a deploys. */
@Service
public class ColetorDarwin implements ColetaDarwin {
    private final DarwinGateway darwin;
    private final DarwinProperties config;
    private final ColetaDarwinProperties coleta;
    private final LeadRepositorio leads;
    private final ColetaDarwinRepositorio cache;
    private final EnriquecerCadastroDoLeadUseCase aplicar;
    private final Executor executor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;
    private final java.util.Set<UUID> locais=ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> alteradosDuranteConsulta=ConcurrentHashMap.newKeySet();

    public ColetorDarwin(DarwinGateway darwin, DarwinProperties config, ColetaDarwinProperties coleta,
            LeadRepositorio leads, ColetaDarwinRepositorio cache, EnriquecerCadastroDoLeadUseCase aplicar,
            @Qualifier("coletaDarwinExecutor") Executor executor, org.springframework.transaction.PlatformTransactionManager tm,
            Clock clock, ApplicationEventPublisher eventos) {
        this.darwin=darwin; this.config=config; this.coleta=coleta; this.leads=leads; this.cache=cache;
        this.aplicar=aplicar; this.executor=executor; this.tx=new TransactionTemplate(tm); this.clock=clock; this.eventos=eventos;
    }
    @EventListener public void aoConsultar(LeadConsultado evento) { solicitar(evento.leadId(),false); }
    @TransactionalEventListener public void aoAlterar(DadosCadastraisDoLeadAlterados evento) {
        if (locais.contains(evento.leadId())) alteradosDuranteConsulta.add(evento.leadId());
        else solicitar(evento.leadId(),false);
    }
    @TransactionalEventListener public void aoReceber(EventoDeAtendimento.MensagemRecebida evento) {
        if (evento.abriuAtendimento()) solicitar(evento.leadId(),false);
    }
    @Override public void solicitar(UUID id, boolean manual) {
        if (!darwin.habilitado() || !coleta.habilitada() || !locais.add(id)) return;
        try {
            executor.execute(()-> {
                try { ContextoDeServico.executarComo("coleta-darwin",()->executar(id,manual)); }
                finally {
                    locais.remove(id);
                    if (alteradosDuranteConsulta.remove(id)) solicitar(id,false);
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException cheio) {
            locais.remove(id);
            if (manual) throw new FalhaDarwin(FalhaDarwin.Codigo.OCUPADO);
        }
    }
    @Override public Situacao situacao(UUID id) {
        if (!darwin.habilitado()) return new Situacao(darwin.estadoIntegracao().name());
        if (!coleta.habilitada()) return new Situacao("DESABILITADA");
        return ContextoDeServico.buscarComo("estado-coleta-darwin",()->tx.execute(s->new Situacao(cache.estado(id))));
    }
    private record Reserva(Lead lead, UUID token) {}
    private void executar(UUID id, boolean manual) {
        var reserva=tx.execute(s->{
            var lead=leads.porId(id).orElse(null);
            if (lead==null) return null;
            var agora=clock.instant();
            return cache.reservar(id,hash(config.url()+"\n"+lead.cpf()),agora,
                    agora.plus(config.timeoutConsulta()).plus(config.timeoutConexao()),manual)
                    .map(token->new Reserva(lead,token)).orElse(null);
        });
        if (reserva==null) return;
        String resultado;
        try {
            String cpf=Cpf.validoOuNulo(reserva.lead().cpf());
            if (cpf==null) resultado="IDENTIFICACAO_PENDENTE";
            else {
                var resposta=darwin.consultar(cpf); // Nenhuma transação/conexão de banco fica aberta aqui.
                if (resposta.limiteAtingido() || resposta.candidatos().size()>1) resultado="REVISAO_NECESSARIA";
                else if (resposta.candidatos().isEmpty()) resultado="NAO_ENCONTRADO";
                else {
                    aplicar.executar(id,reserva.token(),reserva.lead(),resposta.candidatos().getFirst(),
                            clock.instant().plus(coleta.cache()),clock.instant().plus(coleta.intervaloErro()));
                    return;
                }
            }
        } catch (FalhaDarwin erro) { resultado=erro.codigo().name(); }
        catch (RuntimeException erro) { resultado="INDISPONIVEL"; }
        String estado=resultado;
        tx.executeWithoutResult(s->{
            if (cache.concluir(id,reserva.token(),estado,clock.instant().plus(coleta.intervaloErro()),clock.instant().plus(coleta.intervaloErro())))
                eventos.publishEvent(new ColetaCadastralDoLeadAtualizada(id));
        });
    }
    private static String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException erro) { throw new IllegalStateException(erro); }
    }
}
