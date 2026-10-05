package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ContagemPorAtendente;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consulta previa: quantos atendimentos o filtro alcanca, por atendente, antes de qualquer confirmacao. */
@Service
public class PreverFinalizacaoEmMassaUseCase {

    private final ValidadorDeFiltroDeFinalizacao validador;
    private final FinalizacaoEmMassaRepositorio repositorio;
    private final ParametrosDeFinalizacaoEmMassa parametros;

    public PreverFinalizacaoEmMassaUseCase(
            ValidadorDeFiltroDeFinalizacao validador,
            FinalizacaoEmMassaRepositorio repositorio,
            ParametrosDeFinalizacaoEmMassa parametros) {
        this.validador = validador;
        this.repositorio = repositorio;
        this.parametros = parametros;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.finalizar_lote')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Previa executar(PedidoDeFinalizacao pedido) {
        FiltroDeFinalizacao filtro = validador.montar(pedido);
        validador.exigirAtendentesAcessiveis(filtro);
        List<ContagemPorAtendente> porAtendente = repositorio.contarElegiveis(filtro);
        long total = porAtendente.stream().mapToLong(ContagemPorAtendente::quantidade).sum();
        int limite = parametros.limitePorOperacao();
        return new Previa(
                total,
                porAtendente,
                filtro.periodo().inicio(),
                filtro.periodo().fim(),
                filtro.periodo().fuso().getId(),
                limite,
                total > limite);
    }

    public record Previa(
            long total,
            List<ContagemPorAtendente> porAtendente,
            Instant periodoInicio,
            Instant periodoFim,
            String fuso,
            int limite,
            boolean excedeLimite) {}
}
