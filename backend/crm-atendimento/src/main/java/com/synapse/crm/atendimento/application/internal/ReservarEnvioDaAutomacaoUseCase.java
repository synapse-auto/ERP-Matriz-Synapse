package com.synapse.crm.atendimento.application.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.ReservaDeEnvioDaAutomacaoRepositorio;
import com.synapse.crm.atendimento.application.ReservaDeEnvioDaAutomacaoRepositorio.Reserva;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Autoriza no maximo UM envio da Automacao por chave (docs/50).
 *
 * O n8n chama antes de falar com o provedor e so envia quando {@code novaReserva=true}. A chave
 * precisa ser estavel entre reexecucoes do mesmo evento (ex.: X-Synapse-Evento-Id + passo do fluxo):
 * uma chave nova a cada retry anularia a protecao.
 */
@Service
public class ReservarEnvioDaAutomacaoUseCase {

    public static final int TAMANHO_MAXIMO_DA_CHAVE = 200;
    public static final int MAXIMO_DE_PENDENTES = 100;

    private final AtendimentoRepositorio atendimentos;
    private final ReservaDeEnvioDaAutomacaoRepositorio reservas;
    private final Clock relogio;

    public ReservarEnvioDaAutomacaoUseCase(
            AtendimentoRepositorio atendimentos, ReservaDeEnvioDaAutomacaoRepositorio reservas, Clock relogio) {
        this.atendimentos = atendimentos;
        this.reservas = reservas;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResultadoDaReserva reservar(UUID atendimentoId, String chave) {
        exigirChaveValida(chave);
        Atendimento atendimento = atendimentos.porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        if (!atendimento.status().estaAberto()) {
            throw new AtendimentoFinalizadoException(atendimentoId);
        }
        // INSERT ... ON CONFLICT DO NOTHING: com duas entregas simultaneas, a segunda espera a
        // primeira commitar e entao ve a linha — exatamente uma recebe novaReserva=true.
        boolean nova = reservas.reservar(chave, atendimentoId, Instant.now(relogio));
        Reserva reserva = reservas.buscar(chave)
                .orElseThrow(() -> new IllegalStateException("reserva de envio sumiu na mesma transacao"));
        if (!reserva.atendimentoId().equals(atendimentoId)) {
            throw new ChaveDeOutroAtendimentoException(chave);
        }
        return new ResultadoDaReserva(reserva, nova);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<Reserva> pendentesAntesDe(Instant limite) {
        return reservas.pendentesAntesDe(limite, MAXIMO_DE_PENDENTES);
    }

    public static void exigirChaveValida(String chave) {
        if (chave == null || chave.isBlank() || chave.length() > TAMANHO_MAXIMO_DA_CHAVE) {
            throw new ChaveDeEnvioInvalidaException();
        }
    }

    public record ResultadoDaReserva(Reserva reserva, boolean novaReserva) {}

    public static final class ChaveDeEnvioInvalidaException extends RuntimeException {
        ChaveDeEnvioInvalidaException() {
            super("chave de envio e obrigatoria e tem no maximo " + TAMANHO_MAXIMO_DA_CHAVE + " caracteres");
        }
    }

    public static final class ChaveDeOutroAtendimentoException extends RuntimeException {
        public ChaveDeOutroAtendimentoException(String chave) {
            super("a chave de envio " + chave + " ja pertence a outro atendimento");
        }
    }

    public static final class AtendimentoFinalizadoException extends RuntimeException {
        AtendimentoFinalizadoException(UUID atendimentoId) {
            super("atendimento " + atendimentoId + " finalizado: a Automacao nao envia nele");
        }
    }
}
