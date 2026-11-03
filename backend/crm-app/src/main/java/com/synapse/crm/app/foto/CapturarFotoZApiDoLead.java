package com.synapse.crm.app.foto;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.atendimento.infrastructure.canal.ZApiFotoProperties;
import com.synapse.crm.atendimento.infrastructure.canal.ZApiProperties;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.lead.foto.AtualizarFotoDoLeadUseCase;
import com.synapse.crm.core.application.lead.foto.CapturaDeFotoRepositorio;
import com.synapse.crm.core.domain.lead.FotoDoLead;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.LeadConsultado;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Enriquecimento por atividade; não agenda varreduras nem executa HTTP na thread do chat. */
@Service
public class CapturarFotoZApiDoLead {
    private static final Logger log = LoggerFactory.getLogger(CapturarFotoZApiDoLead.class);
    private final ZApiFotoProperties foto;
    private final ZApiProperties api;
    private final CanalProperties canal;
    private final CanalGateway gateway;
    private final LeadRepositorio leads;
    private final CapturaDeFotoRepositorio cache;
    private final AtualizarFotoDoLeadUseCase atualizar;
    private final Executor executor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AvisadorDeFotoDoLead avisador;
    private final java.util.Set<UUID> locais = ConcurrentHashMap.newKeySet();

    public CapturarFotoZApiDoLead(ZApiFotoProperties foto, ZApiProperties api, CanalProperties canal,
            CanalGateway gateway, LeadRepositorio leads, CapturaDeFotoRepositorio cache, AtualizarFotoDoLeadUseCase atualizar,
            @Qualifier("fotoDePerfilExecutor") Executor executor, org.springframework.transaction.PlatformTransactionManager tm,
            Clock clock, AvisadorDeFotoDoLead avisador) {
        this.foto=foto; this.api=api; this.canal=canal; this.gateway=gateway; this.leads=leads;
        this.cache=cache; this.atualizar=atualizar; this.executor=executor; this.tx=new TransactionTemplate(tm);
        this.clock=clock; this.avisador=avisador;
    }

    @EventListener public void aoConsultar(LeadConsultado evento) { solicitar(evento.leadId()); }
    @TransactionalEventListener public void aoReceber(EventoDeAtendimento.MensagemRecebida evento) { solicitar(evento.leadId()); }

    public void solicitar(UUID leadId) {
        if (!foto.habilitado() || !api.configurada() || !"z-api".equals(canal.provedor())
                || !Objects.equals(api.instanciaId(), canal.numeroPrincipal()) || !locais.add(leadId)) return;
        try {
            executor.execute(() -> {
                try { ContextoDeServico.executarComo("capturar-foto-z-api", () -> executar(leadId)); }
                catch (RuntimeException falha) { log.warn("Z_API_FOTO_FALHOU etapa=PROCESSAMENTO"); }
                finally { locais.remove(leadId); }
            });
        } catch (RuntimeException filaCheia) { locais.remove(leadId); }
    }

    private void executar(UUID id) {
        var agora = clock.instant();
        var consulta = tx.execute(s -> {
            if (!cache.credencialAtiva(api.credencialId(), api.instanciaId())) return null;
            Lead lead = leads.porId(id).orElse(null);
            if (lead == null || lead.telefone() == null || !lead.telefone().matches("[1-9][0-9]{7,14}")
                    || (lead.fotoUrl()!=null && !lead.fotoUrl().isBlank())) return null;
            var atual = leads.fotoDoLead(id).orElse(null);
            var reserva = cache.reservar(id, api.instanciaId(), api.credencialId(),
                    telefoneHash(lead.telefone()),
                    agora, agora.plus(foto.reserva())).orElse(null);
            return reserva == null || atual == null ? null : new Consulta(lead.telefone(), atual, reserva);
        });
        if (consulta == null) return;
        if (consulta.atual().existe() && !Objects.equals(consulta.atual().referencia(), consulta.reserva().referenciaGerada())) {
            tx.executeWithoutResult(s -> cache.concluir(consulta.reserva(), consulta.reserva().referenciaGerada(),
                    clock.instant().plus(foto.cacheTtl()), "PRESERVADA"));
            return;
        }
        try {
            var recebida = gateway.buscarFotoDePerfil(consulta.telefone()); // FORA de qualquer transação.
            boolean mudou = Boolean.TRUE.equals(tx.execute(s -> {
                if (!cache.credencialAtiva(api.credencialId(), api.instanciaId())) return false;
                // A atualização da lease e da foto comitam juntas. Token vencido não pode gravar.
                if (!cache.concluir(consulta.reserva(), consulta.reserva().referenciaGerada(),
                        clock.instant().plus(recebida.isPresent()?foto.cacheTtl():foto.cacheNegativo()),
                        recebida.isPresent()?"FOTO":"AUSENTE")) return false;
                if (recebida.isEmpty()) return false; // Não apagar foto válida por ausência/privacidade.
                var resultado = atualizar.executarAutomaticamente(id, recebida.get().conteudo(), consulta.atual(), consulta.telefone());
                // O native CAS atualiza fora do persistence context; referência é relida pelo repositório.
                var nova = leads.fotoDoLead(id).orElseThrow();
                cache.concluir(consulta.reserva(), nova.referencia(), clock.instant().plus(foto.cacheTtl()), "FOTO");
                return resultado == com.synapse.crm.core.application.lead.foto.ResultadoDaFotoDoLead.ATUALIZADA;
            }));
            if (mudou) avisador.publicar(); // Best effort, após commit, no executor de foto.
        } catch (RuntimeException falha) {
            tx.executeWithoutResult(s -> cache.concluir(consulta.reserva(), consulta.reserva().referenciaGerada(),
                    clock.instant().plus(foto.intervaloErro()), "ERRO"));
            log.warn("Z_API_FOTO_FALHOU etapa=COLETA_OU_PERSISTENCIA");
        }
    }
    private record Consulta(String telefone, FotoDoLead atual, CapturaDeFotoRepositorio.Reserva reserva) {}

    private static String telefoneHash(String telefone) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(telefone.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel");
        }
    }
}
