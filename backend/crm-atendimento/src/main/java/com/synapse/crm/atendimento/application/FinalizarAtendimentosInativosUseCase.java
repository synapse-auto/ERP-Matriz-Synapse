package com.synapse.crm.atendimento.application;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;

/**
 * Coordena uma rodada limitada de finalização automática.
 *
 * <p>A seleção e cada alteração ficam em transações diferentes: uma linha lenta ou concorrente não
 * segura a conexão da varredura, e a falha de um atendimento não desfaz os demais. Cada ID é
 * revalidado por {@link FinalizarAtendimentoUseCase} sob lock antes da transição.
 */
@Service
public class FinalizarAtendimentosInativosUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizarAtendimentosInativosUseCase.class);

    private final ListarAtendimentosInativosUseCase listar;
    private final FinalizarAtendimentoUseCase finalizar;

    public FinalizarAtendimentosInativosUseCase(
            ListarAtendimentosInativosUseCase listar, FinalizarAtendimentoUseCase finalizar) {
        this.listar = listar;
        this.finalizar = finalizar;
    }

    @PreAuthorize("hasRole('SERVICO')")
    public Resultado executar(Instant agora, Duration inatividade, int limite) {
        if (inatividade.isNegative() || inatividade.isZero()) {
            throw new IllegalArgumentException("inatividade deve ser positiva");
        }
        Instant corte = agora.minus(inatividade);
        var ids = listar.executar(corte, limite);
        int finalizados = 0;
        int ignorados = 0;
        int falhas = 0;
        for (var id : ids) {
            try {
                if (finalizar.executarPelaAutomacaoSeInativo(id, corte).isPresent()) {
                    finalizados++;
                } else {
                    ignorados++;
                }
            } catch (AtendimentoJaFinalizadoException | RecursoDeAtendimentoIndisponivelException erro) {
                // Outra mensagem, uma finalização concorrente ou RLS pode retirar o candidato entre
                // a seleção e a transação individual; a próxima rodada fará uma nova leitura.
                ignorados++;
            } catch (RuntimeException erro) {
                falhas++;
                log.warn("Falha ao finalizar atendimento inativo {} nesta rodada", id, erro);
            }
        }
        return new Resultado(ids.size(), finalizados, ignorados, falhas, corte);
    }

    public record Resultado(
            int candidatos, int finalizados, int ignorados, int falhas, Instant corte) {}
}
