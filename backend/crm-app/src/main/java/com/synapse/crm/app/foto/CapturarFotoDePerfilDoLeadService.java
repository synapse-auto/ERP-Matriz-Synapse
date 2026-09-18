package com.synapse.crm.app.foto;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.CanalGateway.MidiaRecebida;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.lead.foto.AtualizarFotoDoLeadUseCase;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Enriquece leads com a foto do contato sem bloquear o webhook ou a abertura da conversa.
 *
 * <p>A consulta do lead acontece numa transação curta; a chamada ao provedor fica fora dela e num
 * executor próprio. O resultado cru da UZAPI já foi convertido em {@link MidiaRecebida} no ACL.
 */
@Service
public class CapturarFotoDePerfilDoLeadService {

    private static final Logger log = LoggerFactory.getLogger(CapturarFotoDePerfilDoLeadService.class);

    private final LeadRepositorio leads;
    private final AtualizarFotoDoLeadUseCase atualizarFoto;
    private final List<CanalGateway> canais;
    private final CanalProperties canal;
    private final FotoDePerfilProperties propriedades;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final ConcurrentHashMap<UUID, Instant> cacheDeConsulta = new ConcurrentHashMap<>();

    public CapturarFotoDePerfilDoLeadService(
            LeadRepositorio leads,
            AtualizarFotoDoLeadUseCase atualizarFoto,
            List<CanalGateway> canais,
            CanalProperties canal,
            FotoDePerfilProperties propriedades,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            Clock relogio) {
        this.leads = leads;
        this.atualizarFoto = atualizarFoto;
        this.canais = canais;
        this.canal = canal;
        this.propriedades = propriedades;
        this.transacao = new TransactionTemplate(transactionManager);
        this.relogio = relogio;
    }

    /** Executa uma tentativa best-effort; falha de foto nunca falha a mensagem recebida. */
    public void executar(UUID leadId) {
        if (!propriedades.habilitado() || leadId == null) {
            return;
        }
        Instant agora = relogio.instant();
        if (!podeConsultar(leadId, agora)) {
            return;
        }

        Lead lead = ContextoDeServico.buscarComo(
                "consultar-lead-para-foto",
                () -> transacao.execute(status -> leads.porId(leadId).orElse(null)));
        if (lead == null || lead.telefone() == null || lead.telefone().isBlank()) {
            marcarConsultado(leadId, agora);
            return;
        }

        CanalGateway gateway = canais.stream()
                .filter(item -> item.provedor().equals(canal.provedor()))
                .findFirst()
                .orElse(null);
        if (gateway == null) {
            marcarConsultado(leadId, agora);
            return;
        }

        try {
            Optional<MidiaRecebida> foto = gateway.buscarFotoDePerfil(lead.telefone());
            if (foto.isPresent()) {
                ContextoDeServico.buscarComo(
                        "salvar-foto-de-perfil",
                        () -> atualizarFoto.executar(leadId, foto.get().conteudo()));
            }
            // Ausência, 404 e resposta inválida são estados normais: cache negativo evita uma
            // chamada externa por cada mensagem de contato sem foto.
            marcarConsultado(leadId, agora);
        } catch (ProvedorTemporariamenteIndisponivelException temporaria) {
            // Não grava cache em erro temporário: a próxima mensagem poderá tentar novamente,
            // sem polling e sem transformar indisponibilidade do provedor em erro do chat.
            cacheDeConsulta.remove(leadId);
            log.warn(
                    "Foto de perfil temporariamente indisponível; leadId={}, tipoErro={}",
                    leadId,
                    temporaria.getClass().getSimpleName());
        } catch (RuntimeException erro) {
            marcarConsultado(leadId, agora);
            log.warn(
                    "Foto de perfil ignorada; leadId={}, tipoErro={}",
                    leadId,
                    erro.getClass().getSimpleName());
        }
    }

    private boolean podeConsultar(UUID leadId, Instant agora) {
        Instant proxima = cacheDeConsulta.get(leadId);
        Instant novaConsulta = agora.plus(propriedades.cacheTtl());
        if (proxima == null) {
            return cacheDeConsulta.putIfAbsent(leadId, novaConsulta) == null;
        }
        if (proxima.isAfter(agora)) {
            return false;
        }
        return cacheDeConsulta.replace(leadId, proxima, novaConsulta);
    }

    private void marcarConsultado(UUID leadId, Instant agora) {
        cacheDeConsulta.put(leadId, agora.plus(propriedades.cacheTtl()));
    }
}
