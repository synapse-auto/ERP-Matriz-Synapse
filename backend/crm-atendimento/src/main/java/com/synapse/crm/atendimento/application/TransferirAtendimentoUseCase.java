package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Reatribui o atendimento — para outro atendente ou de volta para a IA.
 *
 * <p>O lead acompanha. Deixar o atendimento com um dono e o lead com outro produziria a pior forma de
 * inconsistencia deste sistema: a conversa aparece para um atendente e a comissao conta para outro.
 *
 * <p>Roda no pool do chat pelo mesmo motivo dos casos de uso de mensagem — atendimento e lead mudam
 * juntos ou nao mudam.
 */
@Service
public class TransferirAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final AtendenteParaTransferenciaRepositorio destinos;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final UsuarioContext usuarios;

    public TransferirAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            AtendenteParaTransferenciaRepositorio destinos,
            ApplicationEventPublisher eventos,
            Clock relogio,
            UsuarioContext usuarios) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.destinos = destinos;
        this.eventos = eventos;
        this.relogio = relogio;
        this.usuarios = usuarios;
    }

    /**
     * @param paraAtendenteId {@code null} devolve a conversa para a IA
     * @param quemPediu autor da transferencia, para a timeline dizer quem moveu o lead
     */
    @PreAuthorize("hasAnyRole('ATENDENTE','GESTOR','SUBGESTOR','ADMINISTRADOR')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId, UUID paraAtendenteId, UUID quemPediu) {
        return transferir(atendimentoId, paraAtendenteId, quemPediu, OrigemEvento.USUARIO, false);
    }

    /** Mesma transferencia, com identidade tecnica e somente a partir do estado da IA. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executarPelaAutomacao(UUID atendimentoId, UUID paraAtendenteId) {
        if (paraAtendenteId == null) {
            throw new IllegalArgumentException("transferencia da Automacao exige atendente destino");
        }
        return transferir(atendimentoId, paraAtendenteId, null, OrigemEvento.AUTOMACAO, true);
    }

    /** A Automação pode devolver uma conversa humana para a IA, sem fabricar um usuário. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento devolverParaIaPelaAutomacao(UUID atendimentoId) {
        return transferir(atendimentoId, null, null, OrigemEvento.AUTOMACAO, false);
    }

    /** Devolve uma conversa por comando do sistema, sem fabricar um usuario para a auditoria. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento devolverParaIaPeloSistema(UUID atendimentoId) {
        return transferir(atendimentoId, null, null, OrigemEvento.SISTEMA, false);
    }

    private Atendimento transferir(
            UUID atendimentoId,
            UUID paraAtendenteId,
            UUID atorId,
            OrigemEvento atorTipo,
            boolean exigirOrigemIa) {
        Instant agora = Instant.now(relogio);

        Atendimento antes = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);

        if (exigirOrigemIa && antes.status() != StatusAtendimento.EM_IA) {
            throw new TransferenciaDaAutomacaoInvalidaException(atendimentoId);
        }

        if (paraAtendenteId == null && antes.status() == StatusAtendimento.EM_IA) {
            return antes;
        }

        recusarDistribuicaoDePotencial(antes, paraAtendenteId, atorId, atorTipo);

        if (paraAtendenteId != null) {
            destinos.exigirAtendenteAtivo(paraAtendenteId);
        }

        Atendimento depois =
                paraAtendenteId == null ? antes.devolverParaIa() : antes.transferirPara(paraAtendenteId);
        // A leitura acima usou o papel real (404 se nao enxerga). A gravacao de troca de dono
        // precisa de SERVICO: a RLS recusa o UPDATE cuja linha nova o atendente ja nao enxerga.
        atendimentos.elevarRlsParaEscritaDeNovoDono();
        atendimentos.salvar(depois);

        if (paraAtendenteId == null) {
            // Volta para o grupo "Potenciais": sem dono, visivel a todos os atendentes.
            leads.marcarStatus(antes.leadId(), StatusBasicoLead.IA);
        } else {
            leads.transferirPara(antes.leadId(), paraAtendenteId);
        }

        eventos.publishEvent(new EventoDeAtendimento.AtendimentoTransferido(
                antes.leadId(),
                leads.nomeParaTempoReal(antes.leadId()).orElse(""),
                antes.id(),
                antes.atendenteId(),
                paraAtendenteId,
                atorId,
                atorTipo,
                agora));

        return depois;
    }

    /**
     * A RLS deixa qualquer atendente ler o grupo {@code EM_IA}. Sem esta recusa, ele entregaria um
     * Potencial a um colega escolhido a dedo. Transferir a conversa que já é dele para um colega
     * ativo continua permitido; devolver para a IA ou assumir para si também.
     */
    private void recusarDistribuicaoDePotencial(
            Atendimento antes, UUID paraAtendenteId, UUID atorId, OrigemEvento atorTipo) {
        if (atorTipo != OrigemEvento.USUARIO || paraAtendenteId == null || atorId == null) {
            return;
        }
        if (paraAtendenteId.equals(atorId) || antes.status() != StatusAtendimento.EM_IA) {
            return;
        }
        if (usuarios.atual().enxergaTodosOsLeads()) {
            return;
        }
        throw new TransferenciaDePotencialProibidaException();
    }
}
