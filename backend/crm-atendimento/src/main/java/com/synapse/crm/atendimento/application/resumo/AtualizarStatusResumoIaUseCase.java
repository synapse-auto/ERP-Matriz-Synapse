package com.synapse.crm.atendimento.application.resumo;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.ResumoIaParaTempoReal;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Recebe do n8n somente a evolução do ciclo; o texto continua no endpoint de escrita EV-05. */
@Service
public class AtualizarStatusResumoIaUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final SolicitacaoResumoIaRepositorio solicitacoes;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AtualizarStatusResumoIaUseCase(
            AtendimentoRepositorio atendimentos,
            SolicitacaoResumoIaRepositorio solicitacoes,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.solicitacoes = solicitacoes;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public SolicitacaoResumoIaRepositorio.Solicitacao executar(
            UUID leadId,
            UUID solicitacaoId,
            UUID atendimentoId,
            SolicitacaoResumoIaRepositorio.Status status,
            String erroCodigo,
            String erroMensagem) {
        var solicitacao = solicitacoes.porId(solicitacaoId)
                .orElseThrow(() -> new ResumoIaSolicitacaoNaoEncontradaException(solicitacaoId));
        if (!solicitacao.leadId().equals(leadId) || !solicitacao.atendimentoId().equals(atendimentoId)) {
            throw new ResumoIaCicloObsoletoException(solicitacaoId);
        }
        validarErro(status, erroCodigo, erroMensagem);
        // Repetição do mesmo estado é o replay idempotente do webhook; não depende de o
        // atendimento ainda estar aberto, pois a conclusão pode ter sido seguida de finalização.
        if (solicitacao.status() == status) return solicitacao;
        var atendimento = atendimentos.porId(atendimentoId)
                .filter(item -> item.leadId().equals(leadId) && item.status() == StatusAtendimento.EM_ATENDIMENTO)
                .orElseThrow(() -> new ResumoIaCicloObsoletoException(solicitacaoId));
        if (!transicaoPermitida(solicitacao.status(), status)) {
            throw new ResumoIaCicloObsoletoException(solicitacaoId);
        }
        Instant agora = Instant.now(relogio);
        String codigo = status == SolicitacaoResumoIaRepositorio.Status.FALHOU ? normalizarCodigo(erroCodigo) : null;
        String mensagem = status == SolicitacaoResumoIaRepositorio.Status.FALHOU ? normalizarMensagem(erroMensagem) : null;
        if (!solicitacoes.atualizarStatus(
                solicitacaoId, leadId, atendimentoId, status, codigo, mensagem, agora)) {
            throw new ResumoIaCicloObsoletoException(solicitacaoId);
        }
        eventos.publishEvent(new ResumoIaParaTempoReal(
                atendimento.id(), leadId, solicitacaoId, status.name(), codigo, agora));
        return solicitacoes.porId(solicitacaoId).orElseThrow();
    }

    private static boolean transicaoPermitida(
            SolicitacaoResumoIaRepositorio.Status anterior,
            SolicitacaoResumoIaRepositorio.Status novo) {
        return (anterior == SolicitacaoResumoIaRepositorio.Status.PENDENTE
                        && (novo == SolicitacaoResumoIaRepositorio.Status.PROCESSANDO
                                || novo == SolicitacaoResumoIaRepositorio.Status.CONCLUIDO
                                || novo == SolicitacaoResumoIaRepositorio.Status.FALHOU))
                || (anterior == SolicitacaoResumoIaRepositorio.Status.PROCESSANDO
                        && (novo == SolicitacaoResumoIaRepositorio.Status.CONCLUIDO
                                || novo == SolicitacaoResumoIaRepositorio.Status.FALHOU));
    }

    private static void validarErro(
            SolicitacaoResumoIaRepositorio.Status status, String erroCodigo, String erroMensagem) {
        if (status != SolicitacaoResumoIaRepositorio.Status.FALHOU
                && (erroCodigo != null || erroMensagem != null)) {
            throw new IllegalArgumentException("erro só pode ser informado em FALHOU");
        }
        if (status == SolicitacaoResumoIaRepositorio.Status.FALHOU && (erroCodigo == null || erroCodigo.isBlank())) {
            throw new IllegalArgumentException("FALHOU exige erroCodigo");
        }
        normalizarCodigo(erroCodigo);
        normalizarMensagem(erroMensagem);
    }

    private static String normalizarCodigo(String valor) {
        if (valor == null) return null;
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (!normalizado.matches("[A-Z0-9_]{1,80}")) throw new IllegalArgumentException("erroCodigo inválido");
        return normalizado;
    }

    private static String normalizarMensagem(String valor) {
        if (valor == null) return null;
        String normalizado = valor.trim().replaceAll("\\s+", " ");
        if (normalizado.length() > 500 || normalizado.isBlank()) throw new IllegalArgumentException("erroMensagem inválida");
        String minusculo = normalizado.toLowerCase(Locale.ROOT);
        if (minusculo.contains("token") || minusculo.contains("authorization") || minusculo.contains("payload")
                || minusculo.contains("telefone") || minusculo.contains("phone")
                || minusculo.contains("http://") || minusculo.contains("https://")) {
            throw new IllegalArgumentException("erroMensagem contém dado sensível");
        }
        return normalizado;
    }

    public static class ResumoIaSolicitacaoNaoEncontradaException extends RuntimeException {
        public ResumoIaSolicitacaoNaoEncontradaException(UUID id) { super("solicitacao de resumo não encontrada: " + id); }
    }

    public static class ResumoIaCicloObsoletoException extends RuntimeException {
        public ResumoIaCicloObsoletoException(UUID id) { super("ciclo de resumo obsoleto: " + id); }
    }
}
