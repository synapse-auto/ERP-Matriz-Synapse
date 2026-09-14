package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Encerra o atendimento e leva o lead junto para {@code FINALIZADO}.
 *
 * <p>Finalizar duas vezes falha, em vez de virar no-op: o agregado recusa, e a recusa importa porque
 * um segundo encerramento publicaria um segundo evento e a timeline do lead contaria uma historia que
 * nao aconteceu.
 *
 * <p><b>Avaliacao (contrato EV-08 §1.1 e §1.5):</b> a pesquisa de satisfacao e enfileirada na
 * finalizacao individual e na solicitada pela Automacao, mas nunca em lote — tres cliques de lote
 * virariam dezenas de conversas abertas com o cliente. A origem nao vem de parametro HTTP nem de
 * heuristica: quem chama ja sabe qual e, porque lote, individual e Automacao entram por metodos
 * publicos distintos.
 */
@Service
public class FinalizarAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final SolicitacaoDeAvaliacao avaliacao;

    public FinalizarAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio,
            SolicitacaoDeAvaliacao avaliacao) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
        this.avaliacao = avaliacao;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executar(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou, Origem.INDIVIDUAL);
    }

    /** Entrada exclusiva do caso de uso de lote; nao exposta como parametro HTTP. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executarEmLote(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou, Origem.LOTE);
    }

    /**
     * Entrada exclusiva da Automacao. O ator e tipado no evento como AUTOMACAO, sem fabricar um
     * UUID de usuario; a transicao e a mesma do botao humano e continua dentro desta transacao.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executarPelaAutomacao(UUID atendimentoId) {
        return finalizar(atendimentoId, null, Origem.AUTOMACAO);
    }

    /**
     * Finaliza somente se o atendimento ainda estiver humano e inativo no corte informado.
     *
     * <p>A leitura é refeita sob os mesmos locks da finalização antes de aplicar a transição. Isso
     * torna a decisão idempotente entre rodadas do scheduler: uma mensagem que chegou depois da
     * seleção não será encerrada por uma fotografia antiga.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public java.util.Optional<Atendimento> executarPelaAutomacaoSeInativo(
            UUID atendimentoId, Instant corte) {
        Atendimento aberto = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        if (aberto.status() != StatusAtendimento.EM_ATENDIMENTO) {
            return java.util.Optional.empty();
        }
        Instant ultimaInteracao = atendimentos.ultimaMensagemEm(atendimentoId).orElse(aberto.iniciadoEm());
        if (!ultimaInteracao.isBefore(corte)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(finalizar(aberto, null, Origem.AUTOMACAO));
    }

    /**
     * Valida a existência e o estado antes da reserva de idempotência do comando interno.
     *
     * <p>A reserva possui FK para {@code atendimento}; por isso o comando valida antes de inserir
     * uma chave nova. O lock é mantido na transação do comando e também impede que outra
     * finalização atravesse a validação antes da aplicação do efeito.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public void validarPelaAutomacao(UUID atendimentoId) {
        Atendimento atendimento = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        if (!atendimento.estaAberto()) {
            throw new AtendimentoJaFinalizadoException(atendimentoId, "finalizacao");
        }
    }

    private Atendimento finalizar(UUID atendimentoId, UUID quemFinalizou, Origem origem) {
        Atendimento aberto = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        return finalizar(aberto, quemFinalizou, origem);
    }

    private Atendimento finalizar(Atendimento aberto, UUID quemFinalizou, Origem origem) {
        Instant agora = Instant.now(relogio);

        // Mesma parede da transferencia (E107): UPDATE que tira a linha da visibilidade de quem
        // executa e recusado mesmo com WITH CHECK (TRUE). EM_IA + atendente_id nulo deixa de
        // passar no USING depois de virar FINALIZADO. A leitura e o 404 ja rodaram com o papel
        // real; daqui so gravamos o que ja foi autorizado.
        if (aberto.status() == StatusAtendimento.EM_IA) {
            atendimentos.elevarRlsParaEscritaDeNovoDono();
        }

        Atendimento finalizado = atendimentos.salvar(aberto.finalizar(agora));
        leads.marcarStatus(aberto.leadId(), StatusBasicoLead.FINALIZADO);
        if (origem == Origem.INDIVIDUAL || origem == Origem.AUTOMACAO) {
            avaliacao.preparar(finalizado);
        }

        if (origem == Origem.AUTOMACAO) {
            eventos.publishEvent(new EventoDeAtendimento.AtendimentoFinalizadoPelaAutomacao(
                    aberto.leadId(), aberto.id(), agora));
        } else {
            eventos.publishEvent(new EventoDeAtendimento.AtendimentoFinalizado(
                    aberto.leadId(), aberto.id(), quemFinalizou, agora));
        }
        EventosCanonicosDeAtendimento.publicar(
                atendimentos,
                eventos,
                EventoCanonicoDeAtendimento.Tipo.ATENDIMENTO_FINALIZADO,
                aberto.id(),
                aberto.leadId(),
                agora);

        return finalizado;
    }

    private enum Origem { INDIVIDUAL, LOTE, AUTOMACAO }
}
