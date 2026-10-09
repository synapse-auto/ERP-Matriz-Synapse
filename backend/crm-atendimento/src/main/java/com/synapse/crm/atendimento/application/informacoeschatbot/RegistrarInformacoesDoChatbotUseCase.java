package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoes;
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
 */
@Service
public class RegistrarInformacoesDoChatbotUseCase {

    /** Teto da coluna {@code conteudo} (CHECK da V101): configurar acima disso so falharia em runtime. */
    static final int TETO_ABSOLUTO_DE_CARACTERES = 20_000;

    private final AtendimentoRepositorio atendimentos;
    private final InformacoesDoChatbotRepositorio informacoes;
    private final HabilitacaoDasInformacoesDoChatbot habilitacao;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final int tamanhoMaximo;

    public RegistrarInformacoesDoChatbotUseCase(
            AtendimentoRepositorio atendimentos,
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
        this.informacoes = informacoes;
        this.habilitacao = habilitacao;
        this.eventos = eventos;
        this.relogio = relogio;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    /** Primeira barreira: instancia que nao habilitou o recurso nao reserva chave nem grava nada. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public void exigirHabilitada() {
        if (!habilitacao.habilitada()) {
            throw new InformacoesDoChatbotDesabilitadasException();
        }
    }

    /** Valida e normaliza o texto; o valor devolvido e o que entra no hash da Idempotency-Key. */
    @PreAuthorize("hasRole('SERVICO')")
    public String normalizar(String bruto) {
        return ConteudoDasInformacoes.de(bruto, tamanhoMaximo).texto();
    }

    /**
     * O atendimento existe e esta com um humano.
     *
     * @throws RecursoDeAtendimentoIndisponivelException atendimento inexistente
     * @throws InformacoesDoChatbotRecusadasException ainda com a IA ou ja finalizado
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Atendimento validarDestino(UUID atendimentoId) {
        Atendimento atendimento = atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("Atendimento", atendimentoId));
        if (atendimento.status() == StatusAtendimento.FINALIZADO) {
            throw new InformacoesDoChatbotRecusadasException(
                    atendimentoId, InformacoesDoChatbotRecusadasException.Motivo.ATENDIMENTO_FINALIZADO);
        }
        if (atendimento.status() != StatusAtendimento.EM_ATENDIMENTO) {
            throw new InformacoesDoChatbotRecusadasException(
                    atendimentoId, InformacoesDoChatbotRecusadasException.Motivo.ATENDIMENTO_NAO_TRANSFERIDO);
        }
        return atendimento;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID atendimentoId, String chaveIdempotencia, String conteudoNormalizado) {
        Atendimento atendimento = validarDestino(atendimentoId);
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now(relogio);
        informacoes.inserir(id, atendimentoId, chaveIdempotencia, conteudoNormalizado, agora);
        eventos.publishEvent(new InformacoesDoChatbotParaTempoReal(atendimentoId, atendimento.leadId(), id, agora));
        return new Resultado(id, atendimentoId, agora);
    }

    public record Resultado(UUID id, UUID atendimentoId, Instant registradoEm) {}
}
