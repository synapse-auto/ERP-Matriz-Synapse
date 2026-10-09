package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoParaAlteracao;
import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoes;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Registra, no historico do atendimento, o que o chatbot coletou antes de transferi-lo a um humano.
 *
 * <p>So grava um snapshot interno: nao toca em responsavel, participantes, lead, resumo da ficha nem
 * outbox de envio, e nao dispara transferencia. A reserva de Idempotency-Key e a ordem das validacoes
 * ficam em {@code ComandosAutomacaoUseCase}, como nos demais comandos do n8n; este caso de uso e o
 * efeito.
 *
 * <p>O destino e sempre o atendimento identificado pelo chamador. Nunca se resolve por lead, nome ou
 * telefone — e e isso que impede um callback atrasado de cair na conversa errada.
 *
 * <p><b>Atomicidade.</b> {@link #validarDestino} e uma pre-checagem barata e sem lock, so para recusar
 * cedo e antes da reserva da chave. A decisao que vale e a de {@link #executar}: ela toma o lock do
 * lead e do atendimento na mesma ordem de finalizar e transferir
 * ({@link AtendimentoParaAlteracao}) e so entao confere o estado e grava. Uma finalizacao ou
 * devolucao para a IA concorrente termina antes (e o card e recusado) ou depois (e o card ja esta
 * gravado) — nunca no meio, num destino que ja ficou obsoleto.
 *
 * <p>Os metodos de validacao nao marcam a transacao como rollback-only ao recusar: o chamador ainda
 * precisa poder devolver o replay de uma corrida resolvida por outra requisicao.
 */
@Service
public class RegistrarInformacoesDoChatbotUseCase {

    /** Teto da coluna {@code conteudo} (CHECK da V101): configurar acima disso so falharia em runtime. */
    static final int TETO_ABSOLUTO_DE_CARACTERES = 20_000;

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final InformacoesDoChatbotRepositorio informacoes;
    private final HabilitacaoDasInformacoesDoChatbot habilitacao;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final int tamanhoMaximo;

    public RegistrarInformacoesDoChatbotUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            InformacoesDoChatbotRepositorio informacoes,
            HabilitacaoDasInformacoesDoChatbot habilitacao,
            ApplicationEventPublisher eventos,
            Clock relogio,
            @Value("${synapse.automacao.informacoes-chatbot-tamanho-maximo:4000}") int tamanhoMaximo) {
        if (tamanhoMaximo < 1 || tamanhoMaximo > TETO_ABSOLUTO_DE_CARACTERES) {
            throw new IllegalStateException(
                    "synapse.automacao.informacoes-chatbot-tamanho-maximo deve estar entre 1 e "
                            + TETO_ABSOLUTO_DE_CARACTERES + ", mas e " + tamanhoMaximo);
        }
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.informacoes = informacoes;
        this.habilitacao = habilitacao;
        this.eventos = eventos;
        this.relogio = relogio;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    /** Operacao NOVA numa instancia que nao habilitou o recurso: nao reserva chave nem grava nada. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            readOnly = true,
            noRollbackFor = InformacoesDoChatbotDesabilitadasException.class)
    public void exigirHabilitada() {
        if (!habilitacao.habilitada()) {
            throw new InformacoesDoChatbotDesabilitadasException();
        }
    }

    /**
     * So normaliza, sem validar: e o que entra no hash da Idempotency-Key, e o hash de um replay nao
     * pode depender da configuracao de hoje.
     */
    @PreAuthorize("hasRole('SERVICO')")
    public String normalizarParaIdempotencia(String bruto) {
        return ConteudoDasInformacoes.normalizar(bruto);
    }

    /** Valida o texto contra o limite ATUAL da instancia: vale para operacao nova, nunca para replay. */
    @PreAuthorize("hasRole('SERVICO')")
    public void validarConteudo(String conteudoBruto) {
        ConteudoDasInformacoes.de(conteudoBruto, tamanhoMaximo);
    }

    /**
     * Pre-checagem sem lock: o atendimento existe e esta com um humano. Nao substitui a conferencia
     * sob lock de {@link #executar}.
     *
     * @throws RecursoDeAtendimentoIndisponivelException atendimento inexistente
     * @throws InformacoesDoChatbotRecusadasException ainda com a IA ou ja finalizado
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            readOnly = true,
            noRollbackFor = {
                InformacoesDoChatbotRecusadasException.class,
                RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento validarDestino(UUID atendimentoId) {
        Atendimento atendimento = atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("Atendimento", atendimentoId));
        exigirComHumano(atendimento);
        return atendimento;
    }

    /** Confere o estado SOB LOCK e grava. O conteudo ja chega normalizado e validado. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID atendimentoId, String chaveIdempotencia, String conteudoNormalizado) {
        Atendimento atendimento = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        exigirComHumano(atendimento);
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now(relogio);
        informacoes.inserir(id, atendimentoId, chaveIdempotencia, conteudoNormalizado, agora);
        eventos.publishEvent(new InformacoesDoChatbotParaTempoReal(atendimentoId, atendimento.leadId(), id, agora));
        return new Resultado(id, atendimentoId, agora);
    }

    private static void exigirComHumano(Atendimento atendimento) {
        if (atendimento.status() == StatusAtendimento.FINALIZADO) {
            throw new InformacoesDoChatbotRecusadasException(
                    atendimento.id(), InformacoesDoChatbotRecusadasException.Motivo.ATENDIMENTO_FINALIZADO);
        }
        if (atendimento.status() != StatusAtendimento.EM_ATENDIMENTO) {
            throw new InformacoesDoChatbotRecusadasException(
                    atendimento.id(), InformacoesDoChatbotRecusadasException.Motivo.ATENDIMENTO_NAO_TRANSFERIDO);
        }
    }

    public record Resultado(UUID id, UUID atendimentoId, Instant registradoEm) {}
}
