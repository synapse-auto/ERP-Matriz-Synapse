package com.synapse.crm.relatorios.application.cancelamentos;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.relatorios.domain.cancelamentos.NovoCancelamento;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

@Service
public class RegistrarCancelamentoUseCase {
    private final GravacaoDeCancelamentos gravacao;
    private final ValidacaoDoVinculoCancelamento vinculos;
    private final Clock relogio;
    private final ApplicationEventPublisher eventos;
    public RegistrarCancelamentoUseCase(GravacaoDeCancelamentos gravacao,
            ValidacaoDoVinculoCancelamento vinculos, Clock relogio, ApplicationEventPublisher eventos) {
        this.gravacao=gravacao; this.vinculos=vinculos; this.relogio=relogio; this.eventos=eventos;
    }
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(timeoutString = "${synapse.cancelamentos.timeout-segundos}")
    @Auditable(acao="REGISTRAR_CANCELAMENTO", entidadeTipo="CANCELAMENTO", atorTipo="AUTOMACAO")
    public GravacaoDeCancelamentos.Resultado executar(NovoCancelamento evento, String chave) {
        chave = NovoCancelamento.identificador(chave, 200);
        vinculos.validar(evento.leadId(), evento.atendimentoId());
        var resultado = gravacao.registrar(evento, chave, relogio.instant());
        if (!resultado.repetido()) eventos.publishEvent(new CancelamentoRegistrado());
        return resultado;
    }
}
